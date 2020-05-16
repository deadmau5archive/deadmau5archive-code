<?php

$isAuth = false;

$key = " --- REMOVED ---";
if (php_sapi_name() === 'cli' || (isset($_GET['key']) && $_GET['key'] == $key)) {
	$isAuth = true;
}


$dbuser = "username goes here";
$dbpass = "password goes here";
$dbhost = "localhost";
$dbname = "mixer_save";
$dbtable = "vods";

$dldir = "/media/hdd/proj/mixer/dl/";

$vlc_watch_link = 'vlc://file://///RASPI/streamrec/mixer/dl/';

function sectotime($t) {
	if ($t >= 3600) return floor($t/3600).':'.sprintf('%02d:%02d',($t/60%60), $t%60);
	else return floor($t/60%60).':'.sprintf('%02d', $t%60);
}

if ($isAuth) {
	
	if (isset($_GET['channel']) && !empty($_GET['channel']) && is_string($_GET['channel'])) {
		$whereChannel = " WHERE channel='".mysqli_real_escape_string($db, $_GET['channel'])."'";
	} else {
		$whereChannel = "";
	}
	
	$db = mysqli_connect($dbhost, $dbuser, $dbpass, $dbname);
	mysqli_select_db($db, $dbname);
	$res = mysqli_query($db, "SELECT * FROM ".$dbtable.$whereChannel." ORDER BY run DESC");
	
	$videolist = [];
	while ($row = mysqli_fetch_assoc($res)) {
		$videolist[$row['contentId']] = $row;
		$videolist[$row['contentId']]['json'] = json_decode($row['json'], true);
		$videolist[$row['contentId']]['data'] = json_decode($row['data'], true);
	}
	
	if (isset($_GET['h'])) { // -h human readable
		echo '<!doctype html>';
		echo '<html lang="en"><head>';
		echo '<meta charset="utf-8">';
		echo '<title>mixerdlinfo</title>';
		echo '<link rel="icon" href="mixerdlinfo.png">';
		echo '</head><body>';
	
		echo 'n='.count($videolist)."<br>";
		echo '<table border="1">';
		echo '<tr>';
		echo '<th>id</th>';
		echo '<th>n</th>';
		echo '<th>channel</th>';
		echo '<th>contentId</th>';
		echo '<th>shareableId</th>';
		echo '<th>title</th>';
		//echo '<th>category</th>';
		echo '<th>length</th>';
		echo '<th>uploadDate</th>';
		echo '<th>last run</th>';
		echo '<th>size (head)</th>';
		//echo '<th>newest ts file idx</th>';
		//echo '<th>num ts files</th>';
		echo '<th>file dl info</th>';
		echo '<th>status</th>';
		echo '<th>errors</th>';
		echo '<th>vlc://</th>';
		echo '</tr>';
		$i = count($videolist)-1;
		foreach ($videolist as $contentId => $vod) {
			$ch = $vod['channel'];
			echo '<tr>';
			echo '<td>'.$i.'</td>';
			echo '<td>#'.($i+1).'</td>';
			echo '<td>'.$ch.'</td>';
			echo '<td>'.$contentId.'</td>';
			echo '<td>'.$vod['json']['shareableId'].'</td>';
			echo '<td>'.$vod['json']['title'].'</td>';
			// category? (not in json)
			echo '<td style="text-align: right;">'.sectotime($vod['json']['durationInSeconds']).'</td>';
			echo '<td>'.date('Y-m-d H:i:s', strtotime($vod['json']['uploadDate'])).'</td>';
			echo '<td>'.date('Y-m-d H:i:s', $vod['run']).'</td>';
			//echo '<td>'.(isset($vod['data']['newestIdx']) ? $vod['data']['newestIdx'] : '--').'</td>';
			//echo '<td>'.(isset($vod['data']['num_ts_files']) ? $vod['data']['num_ts_files'] : '--').'</td>';
			$contentLength = "n/a";
			
			$mp4_fcache = false;
			$mp4_fexist = false;
			$mp4_fsizeb = null;
			$mp4_fsize = "n/a";
			if (isset($vod['data']['actualSize'])) {
				$mp4_fcache = true;
				$mp4_fexist = true;
				$mp4_fsizeb = trim($vod['data']['actualSize']);
			} elseif (isset($vod['data']['contentLength']) || isset($vod['data']['downloadCompleteMp4'])) {
				$fpmp4 = $dldir.$ch.'/vid/'.$contentId.'/source.mp4';
				$mp4_fexist = file_exists($fpmp4);
				if ($mp4_fexist) {
					//$mp4_fsize = explode("\t",shell_exec('du -sh '.$fpmp4))[0];
					$mp4_fsizeb = trim(explode("\t",shell_exec('du -sb '.$fpmp4))[0]);
					/*$vod['data']['actualSize'] = $mp4_fsizeb;
					mysqli_query($db, "UPDATE ".$dbtable." SET data='".
					mysqli_real_escape_string($db, json_encode($vod['data'])).
					"' WHERE contentId='".$contentId."'");*/
				}
			}
			
			if ($mp4_fsizeb !== null) {
				//$divideBy = 1000000000;
				$divideBy = 1024*1024*1024;
				$actualSize_qr = gmp_div_qr($mp4_fsizeb, strval($divideBy));
				$actualSizeGb = intval($actualSize_qr[0]) + intval($actualSize_qr[1])/$divideBy;
				$mp4_fsize = round($actualSizeGb, 3)."G";
			}
			
			
			if (isset($vod['data']['contentLength'])) {
				$mp4ContentLength = trim($vod['data']['contentLength']);
				//$divideBy = 1000000000;
				$divideBy = 1024*1024*1024;
				$contentLength_qr = gmp_div_qr($mp4ContentLength, strval($divideBy));
				$contentLengthGb = intval($contentLength_qr[0]) + intval($contentLength_qr[1])/$divideBy;
				$contentLength = round($contentLengthGb, 3)."G";
				
				//$fpmp4 = $dldir.$ch.'/vid/'.$contentId.'/source.mp4';
				//$actualFileSize = trim(explode("\t",shell_exec('du -sb '.$fpmp4))[0]);
				if ($mp4_fsizeb !== null && $mp4_fsizeb != $mp4ContentLength) {
					$contentLength .= ' <b style="color:red;" title="remote='.$mp4ContentLength.' / local='.$actualFileSize.'">[!]</b>';
				}
				
				//$contentLength .= " cl=".$mp4ContentLength;
				//$contentLength .= " sz=".$actualFileSize;
			}
			echo '<td>'.$contentLength.'</td>';
			$dlstate = '--';
			if (isset($vod['data']['num_ts_files'])) {
				$dlstate = '[TS] '.
					(isset($vod['data']['newestIdx']) ? $vod['data']['newestIdx'] : '--').
					' / '.
					(isset($vod['data']['num_ts_files']) ? $vod['data']['num_ts_files'] : '--');
			} else {
				$dlstate = '[MP4] '.
					($mp4_fcache?"":"* ").
					'exist='.($mp4_fexist ? 'y':'n').' '.
					($mp4_fexist ? ('size='.$mp4_fsize):'');
			}
			echo '<td>'.$dlstate.'</td>';
			$status = "processing";
			if (isset($vod['data']['downloadComplete']) && $vod['data']['downloadComplete']===true) $status = '<b>done (ts)</b>';
			if (isset($vod['data']['downloadCompleteMp4']) && $vod['data']['downloadCompleteMp4']===true) $status = '<b>done (mp4)</b>';
			echo '<td>'.$status.'</td>';
			echo '<td>'.(isset($vod['data']['errors']) ? 
				('<span style="color:#f00;">'.implode('<br>', $vod['data']['errors']).'</span>') 
				:'<span style="color:#0a0;">OK</span>').'</td>';
			echo '<td><a href="'.$vlc_watch_link.$ch.'/vid/'.$contentId.'/source.mp4">Play</a></td>';
			echo '</tr>';
			$i--;
		}
		echo '</table>';
		echo '</body></html>';
	} else {
		echo json_encode($videolist);
	}
	
} else {
	echo 'Not allowed. <a href="index.php">Home</a>';
}

?>
