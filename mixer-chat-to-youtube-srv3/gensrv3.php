<?php


$chatfile = "source.json"; //source.json file of a given stream with the chat log
$stylesfile = "style_mixer.json";
$mixerlevelssfile = "levels.json"; //get this file from https://mixer.com/api/v1/ascension/levels
$streamerTimezone = "America/Toronto";

$maxlines = 10;
$maxcols = 50;
$level_stepsize = 10;
$level_stepnum = 10;

/*
events:
[0] => RecordingStarted
[1] => ChatMessage
[2] => SkillAttribution
[3] => RecordingEnded

sub badge: ★
mod badge: 🔧

normal background: #212C3D
sticker background: #29374A

*/

$styles = json_decode(file_get_contents($stylesfile), true);
if ($styles === null) die("failed to read styles.json");
//print_r($styles);exit;
$levels = json_decode(file_get_contents($mixerlevelssfile), true); 
$lvlcolors = [];
foreach ($levels['levels'] as $lvl) $lvlcolors[$lvl['level']] = $lvl['color'];
$lvlcolors[0] = $lvlcolors[1];
for ($i = 0; $i<$level_stepnum*$level_stepsize; $i+=$level_stepsize) {
	$styles["ascension_".$i] = [
		"extends" => ["ascension_base"],
		"bc" => strtoupper($lvlcolors[$i]),
	];
}

$chat = [];
$tStart = 0;

$handle = fopen($chatfile, "r");
if ($handle) {
	while (($line = fgets($handle)) !== false) {
		$m = [];
		
		$msg = json_decode($line, true);
		$evnt = $msg["content"]["event"];
		
		$msg['date'] = substr($msg['date'], 0, 23)."Z";
		$tMillis = 1000*strtotime($msg['date']) + intval(substr($msg['date'], 20, 23));
		
		$userinfo = [];
		if (!empty($msg['content']['data'])) {
			$data = $msg['content']['data'];
			
			$role = "normal";
			
			if (isset($data['user_roles'])) {
				$roles = $data['user_roles'];
				/*$col = "37aad5";
				if (in_array("Owner", $roles)) $col = "fff";
				elseif (in_array("Staff", $roles)) $col = "ecbf37";
				elseif (in_array("Founder", $roles)) $col = "e42d2d";
				elseif (in_array("Mod", $roles)) $col = "37ed3b";
				elseif (in_array("Pro", $roles)) $col = "e175ff";*/
				if (in_array("Owner", $roles)) $role = "owner";
				elseif (in_array("Staff", $roles)) $role = "staff";
				elseif (in_array("Founder", $roles)) $role = "founder";
				elseif (in_array("Mod", $roles)) $role = "mod";
				elseif (in_array("Pro", $roles)) $role = "pro";
			}
			
			if (isset($data['user_name'])) {
				$userinfo[] = [
					"s" => "user_".$role,
					"m" => $data['user_name']." ",
				];
			}
			
			if (isset($data['user_ascension_level'])) {
				$lvl_step = intval($data['user_ascension_level'] / $level_stepsize) * $level_stepsize;
				$userinfo[] = [
					"s" => "ascension_".$lvl_step,
					"m" => ' '.$data['user_ascension_level'].' ',
				];
				$userinfo[] = [
					"s" => "message",
					"m" => " ",
				];
			}
		}
		
		
		if ($evnt == "RecordingStarted") {
			$ts = new DateTime($msg['date']);
			$ts->setTimezone(new DateTimeZone($streamerTimezone));
			$m[] = [
				"s" => "system",
				"m" => "Chat log starts from ".$ts->format("F j, Y, g:i:s A T"),
			];
			$tStart = $tMillis;
		} else if ($evnt == "RecordingEnded") {
			$ts = new DateTime($msg['date']);
			$ts->setTimezone(new DateTimeZone($streamerTimezone));
			$m[] = [
				"s" => "system",
				"m" => "Chat log ends at ".$ts->format("F j, Y, g:i:s A T"),
			];
		} else if ($evnt == "ChatMessage") {
			$m = $userinfo;
			$data = $msg['content']['data'];
			foreach ($data['message']['message'] as $msgpart) {
				if ($msgpart['type'] == "text") {
					$m[] = [
						"s" => "message",
						"m" => $msgpart['text'],
					];
				} elseif ($msgpart['type'] == "tag") {
					$m[] = [
						"s" => "tag",
						"m" => $msgpart['text'],
					];
				} elseif ($msgpart['type'] == "emoticon") {
					$hasAltText = isset($msgpart['alt']['en']);
					$isEmoji = $hasAltText && (preg_match('/.*[\x{1F600}-\x{1F64F}].*/u', $msgpart['alt']['en']) 
						|| mb_strlen(trim($msgpart['alt']['en'])) == 1);
					$m[] = [
						"s" => $isEmoji ? "message" : "message_i",
						"m" => $hasAltText ? $msgpart['alt']['en'] : $msgpart['text'],
					];
				} elseif ($msgpart['type'] == "image") {
					$m[0]['s'] .= "_hl";
					if (isset($m[2]['s'])) $m[2]['s'] = "message_hl";
					$m[] = [
						"s" => "sticker_msg",
						"m" => "sticker: ",
					];
					$m[] = [
						"s" => "sticker_name",
						"m" => $msgpart['text'],
					];
				} else {
					//echo "unknow message type ".$msgpart['type']."\n";
					if (isset($msgpart['text'])) $m[] = [
						"s" => "message",
						"m" => $msgpart['text'],
					];
					else echo "no text representation found\n";
				}
			}
		} else if ($evnt == "SkillAttribution") {
			$m = $userinfo;
			$data = $msg['content']['data'];
			$m[0]['s'] .= "_hl";
			if (isset($m[2]['s'])) $m[2]['s'] = "message_hl";
			$m[] = [
				"s" => "skill_msg",
				"m" => "used ",
			];
			$m[] = [
				"s" => "skill_name",
				"m" => $data['skill']['skill_name'],
			];
		} else {
			echo "unknown event: ".$evnt."\n";
		}
		
		$chat[] = [
			"t" => $tMillis - $tStart,
			"s" => $m,
		];
	}
	fclose($handle);
} else {
	echo "error\n";
}


$pens = [];
$pars = [];

$style_pen_index = [];

$pid = 1;
function getStyleAttribs($stylename) {
	global $styles;
	$attribs = [];
	if (isset($styles[$stylename]['extends'])) {
		foreach ($styles[$stylename]['extends'] as $extendstyle) {
			$extendattribs = getStyleAttribs($extendstyle);
			foreach ($extendattribs as $xatrn => $xatrv) $attribs[$xatrn] = $xatrv;
		}
	}
	foreach ($styles[$stylename] as $atrn => $atrv) {
		if ($atrn == "extends") continue;
		$attribs[$atrn] = $atrv;
	}
	return $attribs;
}
foreach ($styles as $stylename => $styleattribs) {
	$attribs = getStyleAttribs($stylename);
	$xmlattribs = [];
	foreach ($attribs as $attribname => $attribval) $xmlattribs[] = $attribname.'="'.$attribval.'"';
	$pens[] = '<pen id="'.$pid.'" '.implode(" ", $xmlattribs).'/>';
	$style_pen_index[$stylename] = $pid;
	$pid++;
}


for ($i = 0; $i<count($chat); $i++) {
	$t = $chat[$i]['t'];
	while ($i+1<count($chat) && $chat[$i+1]['t'] == $t) $i++;
	$h = max(0, $i-$maxlines+1);
	$d = $i+1<count($chat) ? ($chat[$i+1]['t'] - $t) : 10 /*duration of last msg*/;
	$p = '<p t="'.($t*1).'" d="'.($d*1).'" wp="1" ws="1">';
	for ($j = $h; $j<=$i; $j++) {
		$sfrag = $chat[$j]['s'];
		$col = 0;
		foreach ($sfrag as $s) {
			$str = "";
			$words = explode(" ", $s['m']);
			$fst = true;
			//line break stuff:
			foreach ($words as $word) {
				if ($col == 0 || ($fst && $col + strlen($word) <= $maxcols)) {
					$sep = "";
					$col += strlen($word);
				} elseif ($col + 1 + strlen($word) <= $maxcols) {
					$sep = " ";
					$col += 1 + strlen($word);
				} else {
					$sep = "\r\n";
					$col = strlen($word);
				}
				$str .= $sep . $word;
				$fst = false;
			}
			$p .= '<s p="'.$style_pen_index[$s['s']].'">'.htmlspecialchars($str).'</s>';
		}
		$p .= "\r\n";
	}
	$p .= '</p>';
	$pars []= $p;
}

$srv3 = '<?xml version="1.0" encoding="UTF-8"?>
<timedtext format="3">
<head>
'.implode("\n", $pens).'
<ws id="0"/>
<ws id="1" ju="0"/>
<wp id="0"/>
<wp id="1" ap="6" ah="0" av="100"/>
</head>
<body>
'.implode("\n", $pars).'
</body>
</timedtext>
';

file_put_contents(__DIR__ . "/srv3.xml", $srv3);


