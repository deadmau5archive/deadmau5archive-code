# deadmau5 archive code

**WARNING  
THIS CODE IS NOT DOCUMENTED, NOR WAS IT EVER SUPPOSED TO BE RELEASED  
IT IS VERY MESSY  
PROCEED AT YOUR OWN RISK**

---

this is a collection of programs and scripts i made for downloading, transcoding and uploading streams to youtube for the [archive page](https://deadmau5archive.github.io/) and also managing the youtube channel.  
it does not include the scripts i use to generate the actual webpage though.  

below here is a short description for every script:

### mixer-dl
downloads vods from mixer (including chat and thumbnails).

conf.php configures the script

requires a mysql database. the structure is in mixer_save.sql

this is designed for a unix system, but should work on windows too. i run it every hour with a cron job

### mixer-dl-info

this is a webpage that displays info about vods downloaded by mixer-dl. 

uploader-java-v2 uses it to get the vod list.

this is designed for a unix system, but should work on windows too. 

### uploader-java-v1

does transcoding (ts to mp4) and uploading and other stuff for managing the youtube channel.  
it also has a mass download option for old twitch vods 

this was made for twitch

### uploader-java-v2

also does transcoding (or rather, did, before i switched to just uploading mixer's mp4 file of the stream. but the code is there) and uploading and other stuff for managing the youtube channel.

this was made for mixer and is largely a copy of v1, but with some new stuff and some stuff adjusted

