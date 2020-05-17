<?php

//this file will be dumed by uploader-java-v2:
// (you don't absolutely need it though, you essentially just need a list with all vod contentIds, or just a list of files you want to process)
$allvids = json_decode(file_get_contents("info.txt"), true); 

foreach ($allvids as $vid) {
	$contentId = $vid['contentId'];
	//for debugging a single vod:
	//if ($contentId != "0356e446-ef96-4418-96de-b88687de0a18") continue;
	//if ($contentId != "fe77e156-73eb-45cb-bbe1-f16bdc04f7f1") continue;
	//if ($contentId != "a41ea698-391b-4276-a1d0-01a5a76cf2c1") continue;
	
	echo "\n".$contentId."\n";

	$chatfile = "/path/to/mixer-dl/dl/deadmau5/meta/chatarchive/".$contentId."/chat.txt"; //loads in the source.json file of the respective vod (downloaded with mixer-dl)
	$stylesfile = "style_mixer.json";
	$mixerlevelssfile = "levels.json"; //get this file from https://mixer.com/api/v1/ascension/levels
	$streamerTimezone = "America/Toronto";

	$maxmessages = 10;
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
	
	sparks: ⚡
	embers: ⬢

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
	$startStr = null;
	$numEvents = 0;

	$polls = [];

	$handle = fopen($chatfile, "r");
	if ($handle) {
		while (($line = fgets($handle)) !== false) {
			$msg = json_decode($line, true);
			if (!isset($msg["content"])) continue;
			$evnt = $msg["content"]["event"];
			
			$numEvents++;
			$m = [];
			
			$msg['date'] = substr($msg['date'], 0, 23)."Z";
			$tMillis = 1000*strtotime($msg['date']) + intval(substr($msg['date'], 20, 23));
			
			$userinfo = [];
			$message = [];
			$hlMessage = false;
			
			if (!empty($msg['content']['data'])) {
				$data = $msg['content']['data'];
				
				$role = "normal";
				$isSub = false;
				
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
					
					if (in_array("Subscriber", $roles)) $isSub = true;
				}
				
				if (isset($data['user_name'])) {
					$userinfo[] = [
						"s" => "user_".$role."@",
						"m" => $data['user_name']." ",
						'nbr' => true,
					];
				}
				
				/*if ($role == "staff") {
					$userinfo[] = [
						"s" => "message@",
						"m" => "🔧 ",
					];
				}*/
				if ($isSub) {
					$userinfo[] = [
						"s" => "subbadge@",
						"m" => "★ ",
						'nbr' => true,
					];
				}
				
				
				if (isset($data['user_ascension_level'])) {
					$lvl_step = intval($data['user_ascension_level'] / $level_stepsize) * $level_stepsize;
					$userinfo[] = [
						"s" => "ascension_".$lvl_step,
						"m" => ' '.$data['user_ascension_level'].' ',
						'nbr' => true,
					];
					$userinfo[] = [
						"s" => "message@",
						"m" => " ",
					];
				}
				
				if (isset($data['message']['message']))
				foreach ($data['message']['message'] as $idx => $msgpart) {
					if ($msgpart['type'] == "text") {
						$message[] = [
							"s" => "message@",
							"m" => $idx==count($data['message']['message'])-1 ? rtrim($msgpart['text']) : $msgpart['text'],
						];
					} elseif ($msgpart['type'] == "tag") {
						$message[] = [
							"s" => "tag@",
							"m" => $msgpart['text'],
						];
					} elseif ($msgpart['type'] == "emoticon") {
						$hasAltText = isset($msgpart['alt']['en']);
						$isEmoji = $hasAltText && (preg_match('/.*[\x{1F600}-\x{1F64F}].*/u', $msgpart['alt']['en']) 
							|| mb_strlen(trim($msgpart['alt']['en'])) == 1);
						$message[] = [
							"s" => $isEmoji ? "message@" : "message_i@",
							"m" => $hasAltText ? $msgpart['alt']['en'] : $msgpart['text'],
						];
					} elseif ($msgpart['type'] == "image") {
						if ($idx > 0) echo "[warn] sticker not first msg part!\n";
						$hlMessage = true;
						$message[] = [
							"s" => "sticker_msg",
							"m" => "sticker: ",
							'nbr' => true,
						];
						$message[] = [
							"s" => "sticker_name",
							"m" => $msgpart['text'],
							'nbr' => true,
						];
						$skill = $data['message']['meta']['skill'];
						if ($skill['currency']=="Sparks") $currencyIcon = "⚡";
						elseif ($skill['currency']=="Embers") {
							$currencyIcon = "⬢";
							/*if (count($data['message']['message'])>1) {
								echo msectotime($tMillis-$tStart)." sticker has message\n";
								//print_r($msg);exit;
							}*/ //this is ok
						}
						else echo "sticker: unknown currency ".$skill['currency']."!\n";
						$message[] = [
							"s" => "sticker_msg",
							"m" => "  ".$currencyIcon." ".$skill['cost'].(count($data['message']['message'])>1?"\r\n":""),
							'nbr' => true,
						];
					} elseif ($msgpart['type'] == "link") {
						$message[] = [
							"s" => "tag@",
							"m" => $msgpart['text'], //TODO are links allowed in subtitles?
						];
					} elseif ($msgpart['type'] == "inaspacesuit") {
						// @mixer, why the fuck exactly is this a thing?
						$message[] = [
							"s" => "message_i@",
							"m" => $msgpart['text'],
						];
					} else {
						echo "unknown message type ".$msgpart['type']."\n";
						print_r($msgpart);exit;
						//  catch all:
						/*if (isset($msgpart['text'])) $message[] = [
							"s" => "message@",
							"m" => $msgpart['text'],
						];
						else echo "no text representation found\n";*/
					}
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
				$startStr = $ts->format("Y-m-d_H.i.s");
			} else if ($evnt == "RecordingEnded") {
				$ts = new DateTime($msg['date']);
				$ts->setTimezone(new DateTimeZone($streamerTimezone));
				$m[] = [
					"s" => "system",
					"m" => "Chat log ends at ".$ts->format("F j, Y, g:i:s A T"),
				];
			} else if ($evnt == "ChatMessage") {
				$m = array_merge($userinfo, $message);
			} else if ($evnt == "SkillAttribution") {
				$m = $userinfo;
				$data = $msg['content']['data'];
				$hlMessage = true;
				$m[] = [
					"s" => "skill_msg",
					"m" => "used ",
					'nbr' => true,
				];
				$m[] = [
					"s" => "skill_name",
					"m" => $data['skill']['skill_name'],
					'nbr' => true,
				];
				$skill = $data['skill'];
				if ($skill['currency']=="Sparks") $currencyIcon = "⚡";
				elseif ($skill['currency']=="Embers") $currencyIcon = "⬢";
				else echo "skill: unknown currency ".$skill['currency']."!\n";
				$skillHasMsg = isset($data['message']);
				$m[] = [
					"s" => "skill_msg",
					"m" => "  ".$currencyIcon." ".$skill['cost'].($skillHasMsg?"\r\n":""),
					'nbr' => true,
				];
				if ($skillHasMsg) {
					//echo msectotime($tMillis-$tStart)." skill has message\n"; //ok
					$m = array_merge($m, $message);
				} 
			} else if ($evnt == "PollStart") {
				//print_r($msg);exit;

				$poll = $msg['content']['data'];
				$pollUniqid = md5($poll['q'].$poll['endsAt']);
				
				$pollDurationInSec = round($poll['duration'] / 1000.0);
				
				if (!isset($polls[$pollUniqid])) {
					$polls[$pollUniqid] = [
						"poll" => $poll,
						"start" => $tMillis,
						"events" => [
							[
								"offset" => 0,
								"time_left" => $pollDurationInSec,
								"responses" => $poll['responsesByIndex'],
							]
						],
					];
					for ($i = 1; $i<$pollDurationInSec; $i++) {
						$polls[$pollUniqid]['events'][] = [
							"offset" => $i * 1000,
							"time_left" => $pollDurationInSec-$i,
						];
					}
				} else {
					$newOffset = $tMillis - $polls[$pollUniqid]['start'];
					$lastEvent = $polls[$pollUniqid]['events'][0];
					foreach ($polls[$pollUniqid]['events'] as $pollEvent) {
						if (isset($pollEvent['responses'])) {
							if ($pollEvent['offset'] < $newOffset) {
								if ($pollEvent['offset'] > $lastEvent['offset']) {
									$lastEvent = $pollEvent;
								}
							}
						}
					}
					if ($lastEvent['responses'] !== $poll['responsesByIndex']) {
						$polls[$pollUniqid]['events'][] = [
							"offset" => $newOffset,
							"responses" => $poll['responsesByIndex'],
						];
					}
				}
				continue;
			} else if ($evnt == "PollEnd") {
				//print_r($msg);exit;
				$poll = $msg['content']['data'];
				$pollUniqid = md5($poll['q'].$poll['endsAt']);
				
				$polls[$pollUniqid]['events'][] = [
					"offset" => $tMillis - $polls[$pollUniqid]['start'],
					"responses" => $poll['responsesByIndex'],
					"end" => true,
				];
				continue;
			} else if ($evnt == "UserUpdate") {
				//ignore 
				continue;
			} else if ($evnt == "ClearMessages") {
				$data = $msg['content']['data'];
				$m[] = [
					"s" => "system_b",
					"m" => $data['clearer']['user_name'],
				];
				$m[] = [
					"s" => "system",
					"m" => " cleared the chat!",
				];
			} else {
				echo "unknown event: ".$evnt."\n";
				print_r($msg);//exit;
				continue;
			}
			
			for ($i = 0; $i<count($m); $i++) 
				$m[$i]['s'] = str_replace("@", $hlMessage?"_hl":"", $m[$i]['s']);
			
			$chat[] = [
				"t" => $tMillis - $tStart,
				"s" => $m,
			];
		}
		fclose($handle);
	} else {
		echo "error\n";
	}
	
	foreach ($polls as $pollUniqid => $pollObj) 
		usort($polls[$pollUniqid]['events'], function($a, $b) { return $a['offset']-$b['offset']; });
	//print_r($polls);exit;


	$pens = [];
	$parsTimed = [];
	$pars = [];

	$style_pen_index = [];

	$pid = 1;
	foreach ($styles as $stylename => $styleattribs) {
		$attribs = getStyleAttribs($stylename);
		$xmlattribs = [];
		foreach ($attribs as $attribname => $attribval) $xmlattribs[] = $attribname.'="'.$attribval.'"';
		$pens[] = '<pen id="'.$pid.'" '.implode(" ", $xmlattribs).'/>';
		$style_pen_index[$stylename] = $pid;
		//for debugging and looking at xml:
		//$style_pen_index[$stylename] = $stylename;
		$pid++;
	}

	//chat
	for ($i = 0; $i<count($chat); $i++) {
		$t = $chat[$i]['t'];
		while ($i+1<count($chat) && $chat[$i+1]['t'] == $t) $i++;
		$h = max(0, $i-$maxmessages+1);
		$d = $i+1<count($chat) ? ($chat[$i+1]['t'] - $t) : 10*1000 /*duration of last msg*/;
		$p = '<p t="'.($t*1).'" d="'.($d*1).'" wp="1" ws="1">';
		for ($j = $h; $j<=$i; $j++) {
			$sfrag = $chat[$j]['s'];
			$col = 0;
			foreach ($sfrag as $s) {
				$str = "";
				$fst = true;
				//line break stuff:
				$nbr = (isset($s['nbr']) && $s['nbr']);
				$lines = explode("\n", $s['m']);
				foreach ($lines as $lnidx => $line) {
					if (!$fst) {
						$str .= "\r\n";
						$col = 0;
					}
					if ($lnidx > 0) $line = ltrim($line);
					if ($lnidx < count($lines)-1) $line = rtrim($line);
					$words = explode(" ", $line);
					foreach ($words as $word) {
						$wordFitsNoSpace = ($col + strlen($word) <= $maxcols) || $nbr;
						$wordFitsWithSpace = ($col + 1 + strlen($word) <= $maxcols) || $nbr;
						if ($col == 0 || ($fst && $wordFitsNoSpace)) {
							$sep = "";
							$col += strlen($word);
						} elseif ($wordFitsWithSpace) {
							$sep = " ";
							$col += 1 + strlen($word);
						} else {
							$sep = "\r\n";
							$col = strlen($word);
						}
						$str .= $sep . $word;
						$fst = false;
					}
				}
				$p .= '<s p="'.$style_pen_index[$s['s']].'">'.htmlspecialchars($str).'</s>';
			}
			if ($j<$i) $p .= "\r\n";
		}
		$p .= '</p>';
		$parsTimed []= ["t" => $t, "p" => $p];
	}
	
	//polls
	$sty = $style_pen_index;
	foreach ($polls as $pollUniqid => $poll) {
		$pStart = $poll['start'] - $tStart;
		$timeLeft = 0;
		$responses = [];
		for ($i = 0; $i<count($poll['events']); $i++) {
			$pEv = $poll['events'][$i];
			
			if (isset($pEv['time_left'])) $timeLeft = $pEv['time_left'];
			if (isset($pEv['responses'])) $responses = $pEv['responses'];
			$isEnd = isset($pEv['end']);
			
			$t = $pStart + $pEv['offset'];
			$d = $i+1<count($poll['events']) ? ($poll['events'][$i+1]['offset'] - $pEv['offset']) : 10*1000;
			$p = '<p t="'.$t.'" d="'.$d.'" wp="2" ws="1">';
			// xyz started a poll <timeleft>
			$p .= '<s p="'.$sty['poll_b'].'">'.$poll['poll']['author']['user_name'].'</s>';
			if (!$isEnd) {
				$p .= '<s p="'.$sty['poll'].'"> asks: </s>';
				$p .= '<s p="'.$sty['poll_hl'].'"> '.$timeLeft.' </s>'."\r\n";
			} else {
				$p .= '<s p="'.$sty['poll'].'"> asked: </s>'."\r\n";
			}
			// <poll question>
			$p .= '<s p="'.$sty['poll_i'].'">'.htmlspecialchars($poll['poll']['q']).'</s>'."\r\n";
			// • <answer> <num_votes>
			foreach ($poll['poll']['answers'] as $ansIdx => $ansTxt) {
				$p .= '<s p="'.$sty['poll'].'"> • '.htmlspecialchars($ansTxt).' </s>';
				$p .= '<s p="'.$sty['poll_hl'].'"> '.$responses[$ansIdx].' </s>'."\r\n";
			}
			// total votes: x
			$p .= '<s p="'.$sty['poll'].'">Total votes: '.array_sum($responses).'</s>';
			$p .= '</p>';
			$parsTimed []= ["t" => $t, "p" => $p];
		}
	}
	
	usort($parsTimed, function($a, $b) { return $a['t']-$b['t']; });
	foreach ($parsTimed as $par) $pars[] = $par['p'];

$srv3 = '<?xml version="1.0" encoding="UTF-8"?>
<timedtext format="3">
<head>
'.implode("\n", $pens).'
<ws id="0"/>
<ws id="1" ju="0"/>
<wp id="0"/>
<wp id="1" ap="6" ah="0" av="100"/>
<wp id="2" ap="0" ah="0" av="0"/>
</head>
<body>
'.implode("\n", $pars).'
</body>
</timedtext>
';

	echo $numEvents." events\n";
	if ($startStr == null) {
		echo "no start time!\n";
		$startStr = "unknown";
		continue;
	} 
	
	$fname = "srv3/".$startStr."_".$vid['shareableId'];
	//$fname = "srv3poll";
	file_put_contents(__DIR__ . "/".$fname.".xml", $srv3);

	//exit();
}

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

function msectotime($t) { return sectotime($t/1000); }
function sectotime($t) {
	if ($t >= 3600) return floor($t/3600).':'.sprintf('%02d:%02d',($t/60%60), $t%60);
	else return floor($t/60%60).':'.sprintf('%02d', $t%60);
}
