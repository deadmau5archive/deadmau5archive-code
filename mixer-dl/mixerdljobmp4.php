<?php

set_time_limit(0);

require_once(__DIR__.'/conf.php');

$table = "vods"; // "vods_mp4";

function deleteDir($str) {
    if (is_file($str)) {
        return unlink($str);
    } elseif (is_dir($str)) {
        $scan = glob(rtrim($str,'/').'/*');
        foreach($scan as $path) {
            deleteDir($path);
        }
        return @rmdir($str);
    }
}

$db = mysqli_connect($cfg['db']['server'], $cfg['db']['user'], $cfg['db']['password'], "mixer_save");

if (!is_dir($cfg['dl_dir'])) {
	mkdir($cfg['dl_dir']);
}

function download($file_source, $file_target) {
    $rh = fopen($file_source, 'rb');
    $wh = fopen($file_target, 'w+b');
    if (!$rh || !$wh) {
        return false;
    }

	$written = 0;
    while (!feof($rh)) {
		$w = fwrite($wh, fread($rh, 4096));
        if ($w === FALSE) {
            return false;
        } else {
			$written += $w;
		}
        echo "\r[downloading] ".$written." bytes";
        flush();
    }

    fclose($rh);
    fclose($wh);

	echo "\n";
	
    return true;
}

foreach ($cfg['channel_list'] as $ch) {
	
	$observed = [];
	
	$dir = $cfg['dl_dir'].$ch['name'].'/';
	$lastrun = 0; // for first run
	$videolist = [];
	if (!is_dir($dir)) mkdir($dir);
	if (!is_dir($dir."meta/")) mkdir($dir."meta/");
	if (!is_dir($dir."vid/")) mkdir($dir."vid/");
	
	$res = mysqli_query($db, "SELECT * FROM ".$table." WHERE channel='".$ch['name']."' ORDER BY run ASC");
	while ($row = mysqli_fetch_assoc($res)) {
		$videolist[$row['contentId']] = $row;
		$videolist[$row['contentId']]['json'] = json_decode($row['json'], true);
		$videolist[$row['contentId']]['data'] = json_decode($row['data'], true);
		$lastrun = $row['run'];
	}
	
	
	//get most recent past broadcasts:
	$url = 'https://mixer.com/api/v2/vods/channels/'.$ch['id'];
	$vids = json_decode(file_get_contents($url), true);
	foreach ($vids as $vid) {
		$time_create = strtotime($vid['uploadDate']);
		$contentId = $vid['contentId'];
		if (!isset($videolist[$contentId])) {
			echo "add new vod ".$contentId."\n";
			
			//insert to db
			mysqli_query($db, "INSERT INTO ".$table." (channel, contentId, json, run, data) VALUES ('".
				$ch['name']."','".
				mysqli_real_escape_string($db,$contentId)."','".
				mysqli_real_escape_string($db,json_encode($vid))."',".
				time().",'".
				"{}".
			"')");
			
			//add to processing array
			$videolist[$contentId] = [
				"data" => [],
				"json" => $vid,
			];
			
		} else {
			echo "existing vod ".$contentId."\n";
		}
	}
	
	foreach ($videolist as $contentId => $videodata) {
		
		$error = [];
		
		if (isset($videodata['data']['downloadCompleteMp4']) && $videodata['data']['downloadCompleteMp4'] == true) {
			echo "skipping ".$contentId." (mp4 download is complete)\n";
			continue;
		}
		
		if (isset($videodata['data']['downloadComplete']) && $videodata['data']['downloadComplete'] == true) {
			echo "skipping ".$contentId." (ts download is complete)\n";
			continue;
		}

		$contentLocators = $videodata['json']['contentLocators'];
		if (empty($contentLocators)) {
			$error[] = "contentLocators in vod json is empty";
			goto err;
		}
		$mp4url = null;
		foreach ($contentLocators as $ctl) {
			if ($ctl['locatorType'] == "Download") {
				$mp4url = substr($ctl['uri'], 0, strpos($ctl['uri'], ".mp4")+4);
			}
		}
		if (empty($mp4url)) {
			$error[] = "mp4 uri is empty";
			goto err;
		}
		
		echo "mp4 url: ".$mp4url."\n";
		$matches = [];
		preg_match('/^(.*\/)[^\/]+\.mp4$/', trim($mp4url), $matches);
		$tsRemoteDir = $matches[1];
		if (empty($tsRemoteDir)) {
			$error[] = "cannot get remote ts dir from mp4 url";
			goto err;
		}
		
		$context = stream_context_create(['http' => ['method' => 'HEAD']]);
		$mp4_headers = get_headers($mp4url, 1, $context);
		//print_r($mp4_headers);//exit;
		echo $mp4_headers['Content-Length']."\n";
		$mp4ContentLength = trim($mp4_headers['Content-Length']);
		$videodata['data']['contentLength'] = $mp4ContentLength;
		//$contentLength_qr = gmp_div_qr($mp4ContentLength, "1000000000");
		//$contentLengthGb = intval($contentLength_qr[0]) + intval($contentLength_qr[1])/1000000000;
		//echo "mp4 size: ".round($contentLengthGb, 3)." Gb\n";
		echo "mp4 size: ".$mp4ContentLength." bytes\n";
		
		
		if (!is_dir($dir."meta/".$contentId.'/')) mkdir($dir."meta/".$contentId.'/');
		if (!is_dir($dir."vid/".$contentId.'/')) mkdir($dir."vid/".$contentId.'/');
		
	
		//dl m3u8
		$file = $dir."meta/".$contentId.'/index.m3u8';
		$manifesturl = $tsRemoteDir."manifest.m3u8";
		$manifest = file_get_contents($manifesturl);
		if (empty($manifest)) {
			$error[] = "manifest file empty/download error";
		}
		$bytesWritten = file_put_contents($file, $manifest);
		if (empty($bytesWritten)) {
			$error[] = "failed to save manifest file";
		}
		
		//chatlog
		$file = $dir."meta/".$contentId.'/chat.txt';
		$chaturl = $tsRemoteDir."source.json";
		$chatlog = file_get_contents($chaturl);
		if (empty($chatlog)) {
			$error[] = "chatlog file empty/download error";
		}
		$bytesWritten = file_put_contents($file, $chatlog);
		if (empty($bytesWritten)) {
			$error[] = "failed to save chatlog file";
		}
		
		//thumb
		$file = $dir."meta/".$contentId.'/thumb.png';
		$thumburl = $tsRemoteDir."source.png";
		$thumbHandle = fopen($thumburl, 'r');
		if (empty($thumbHandle)) {
			$error[] = "thumbnail file download error";
		}
		$bytesWritten = file_put_contents($file, $thumbHandle);
		if (empty($bytesWritten)) {
			$error[] = "failed to save thumbnail file";
		}
	
		//mp4
		$file = $dir."vid/".$contentId.'/source.mp4';
		$dlresult = download($mp4url, $file);
		if (!$dlresult) {
			$error[] = "failed to save mp4 file";
			goto err;
		}
		
		//verify mp4 size
		$actualFileSize = trim(explode("\t",shell_exec('du -sb '.$file))[0]);
		if ($actualFileSize != $mp4ContentLength) {
			$error[] = "mp4 dl not completed (".$actualFileSize." / ".$mp4ContentLength.")";
			goto err; //i.e. do not set downloadCompleteMp4 to true
		}

		$videodata['data']['actualSize'] = $actualFileSize;
		$videodata['data']['downloadCompleteMp4'] = true;
	
		
		err:
		if (!empty($error)) {
			$prevErros = isset($videodata['data']['errors']) ? $videodata['data']['errors'] : [];
			$videodata['data']['errors'] = array_merge($prevErros, $error);
		}
		
		mysqli_query($db, "UPDATE ".$table." SET data='".
			mysqli_real_escape_string($db, json_encode($videodata['data'])).
			"' WHERE contentId='".$contentId."'");
		
	}
	
	echo "done!\n";
}

?>