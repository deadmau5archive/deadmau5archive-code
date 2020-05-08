<?php

require_once(__DIR__.'/conf.php');

$script = realpath(__DIR__) . '/'.$cfg['job_script_name'].'.php';
$logfile = realpath(__DIR__) . '/logs/'.date('Y-m-d_H.i.s').'.log';
//$logfile = '/dev/null';
$lsHandle = popen('ps -C php -f', 'r');
$psList = fread($lsHandle, 8192);

pclose($lsHandle);
if (strpos($psList, $cfg['job_proc_name']) === false) { //if not running
	pclose(popen('php "' . $script . '" > "'.$logfile.'" 2>&1 &', 'r'));
	echo "started";
} else {
	echo "already running";
}