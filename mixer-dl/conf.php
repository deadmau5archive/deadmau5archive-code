<?php

$cfg = [];

$cfg['db']['server'] = "localhost";
$cfg['db']['user'] = "username";
$cfg['db']['password'] = "password";

//get id here: https://mixer.com/api/v1/channels/{channelname}
$cfg['channel_list'] = [
        [
                "name" => "deadmau5",
                "id" => "118130430",
        ]
];

$cfg['dl_dir'] = (__DIR__)."/dl/";
$cfg['job_script_name'] = "mixerdljobmp4";
$cfg['job_proc_name'] = "mixer-dl/mixerdljobmp4";



?>