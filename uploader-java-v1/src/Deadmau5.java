import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import org.json.JSONArray;
import org.json.JSONObject;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.googleapis.media.MediaHttpUploader;
import com.google.api.client.googleapis.media.MediaHttpUploaderProgressListener;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.InputStreamContent;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.Video;
import com.google.api.services.youtube.model.VideoSnippet;
import com.google.api.services.youtube.model.VideoStatus;



public class Deadmau5 {
	
	static int credIndex = 0;
	static int credIndexSmall = 0;
	
	private static boolean QUOTA_CHANGE_LONG_WAIT = false;
	
	public static void main(String[] args) {
		System.err.println("old code");
		// ==> use main() in TwitchThing2 !!!
	}
	
	/*
	
	final static int START_VIDEO = 0;
	
	static class MuteInfo {
		boolean muted = false;
		List<String> mutedSegments = new ArrayList<>();
		List<String> discoTimes = null;
	}
	
	static  boolean USE_EXISTING_TEMP_FILES = false;
	
	static void doDlAndUploadLoop() {
		JSONArray vids = null;
		try {
			vids = new JSONArray(new JSONTokener(new FileReader(new File(DIR+"hls_array.txt"))));
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		int len = vids.length();
		for (int i = START_VIDEO; i<len; i++) {
			boolean uploadSuccess = false;
			
			// LATEST TO FIRST
			int vidIdx = i;
			int vidDisplayNum = len-i;
			// FIRST TO LATEST
			//int vidIdx = len-1-i;
			//int vidDisplayNum = i+1;
			
			JSONObject vid = vids.getJSONObject(vidIdx);
			String hls = vid.getString("hlsurl");
			
			if (new File(DIR+"log\\"+hls+".txt").exists()) {
				System.out.println("(i) skipping "+hls+" because it's already uploaded");
				continue;
			}
			
			System.out.println();
			System.out.println("==================================================================");
			System.out.println("Processing Video "+hls);
			System.out.println("Length = "+vid.getInt("length")+"s ("+secToTime(vid.getInt("length"))+")");
			System.out.println("Upload date: "+vid.getString("time"));
			System.out.println("Video No. #"+vidDisplayNum);
			System.out.println("==================================================================");
			System.out.println();
			
			boolean dlsucc = false;
			boolean rescue = false;
			MuteInfo muteInfo = new MuteInfo();
			
			File rescuedVodFolder = new File(VODPATH+vid.getString("id")+"\\");
			if (USE_EXISTING_TEMP_FILES && new File(TEMP+hls+".ts").exists()) {
				System.out.println("file is already there in temp dir, using present file...");
				dlsucc = true;
				if (rescuedVodFolder.exists() && rescuedVodFolder.isDirectory()) {
					rescue = true;
				}
			} else if (rescuedVodFolder.exists() && rescuedVodFolder.isDirectory()) {
				System.out.println("found rescue vod, joining...");
				//we have a rescue vod
				rescue = true;
				dlsucc = joinTs(rescuedVodFolder, hls, muteInfo);
			} else {
				//succ = dlVid2(hls); //ffmpeg
				dlsucc = dlVid(hls, muteInfo); //download segements
			}
			
			try {
				if (dlsucc) {
					String vidStatus = uploadToYt(vid, ".ts", vidDisplayNum, len, rescue, muteInfo);
					//delVid2(hls);
					
					if (vidStatus != null && vidStatus.length() > 0) {
						File log = new File(DIR+"log\\"+hls+".txt");
						FileWriter w = new FileWriter(log);
						w.write(vidStatus);
						w.close();
						uploadSuccess = true;
					}
				} else {
					System.err.println("problems with download");
				}
				
			} catch (Exception e) {
				System.err.println("SOMETHING WENT HORRIBLY WRONG "+hls);
				e.printStackTrace();
			}
			
			//clearTmp();
			if (uploadSuccess) {
				System.out.println("deleting uploaded vid from temp dir");
				try {
					new File(TEMP+hls+".ts").delete();
				} catch (Exception e) {
					System.err.println("DELETE ERROR: "+e.toString());
				}
				
			}
			
			try {
				if (!uploadSuccess) {
					File logerror = new File(DIR+"logerror\\"+hls+".txt");
					FileWriter w = new FileWriter(logerror);
					w.write("error at "+System.currentTimeMillis());
					w.close();
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
			
		}
			
	}
	
	
	static boolean joinTs(File folder, String hlsUrl, MuteInfo minfo) {
		
		PlaylistInfo playlistInfo = readPlaylist(hlsUrl, minfo);
		List<String> tsFiles = playlistInfo.tsFiles;
		
		int numfiles = folder.list().length;
		System.out.println(numfiles+" .ts files on disk");
		if (tsFiles.size() != numfiles) {
			System.err.println("NUMBER OF SEGMENTS MISMATCH!!! ("+numfiles+" files on disk, "+tsFiles.size()+" in HLS m3u8)");
			return false;
		}
		
		
		try {
			FileOutputStream out = new FileOutputStream(TEMP+hlsUrl+".ts");
			byte[] buf = new byte[4096];
			System.out.println("joining files... ");
			for (int i = 0; i<numfiles; i++) {
				String tsfile = tsFiles.get(i);
				double progr = i / (double) numfiles;
				System.out.print("\rjoining "+tsfile+"\tfile "+(i+1)+" of "+numfiles+"\tprogress: "+String.format("%.03f", progr*100)+"%");
				InputStream in = new FileInputStream(new File(folder.getAbsolutePath()+"\\"+tsfile));
				int b = 0;
				while ((b = in.read(buf)) >= 0) {
					out.write(buf, 0, b);
					out.flush();
				}
				in.close();
			}
			out.close();
			System.out.println();
			
			System.out.println("join complete!");
			
			System.out.println("=> writing discontinuity information to disk...");
			JSONObject muteInfoFile = new JSONObject();
			muteInfoFile.put("filemap", JSONObject.NULL);
			muteInfoFile.put("segmentStrings", new JSONArray());
			muteInfoFile.put("muted", false);
			muteInfoFile.put("discontinuity", minfo.discoTimes == null ? JSONObject.NULL : new JSONArray(minfo.discoTimes));
			try {
				File muteFile = new File(DIR+"mutes\\mutes-"+hlsUrl+".txt");
				FileWriter w = new FileWriter(muteFile);
				muteInfoFile.write(w);
				w.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
			
			return true;
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return false;
		
	}
	
	static boolean dlVid2(String hlsUrl) { //ffmpeg
		
		String cmd = "ffmpeg -i \"https://vod-secure.twitch.tv/"+hlsUrl+"/chunked/index-dvr.m3u8\" -c copy -bsf:a aac_adtstoasc \""+TEMP+hlsUrl+".mp4\"";
		
		System.out.println("RUNNING "+cmd+"\n");
		try {
			String line;
			Process p = Runtime.getRuntime().exec(cmd);
			BufferedReader input = new BufferedReader(new InputStreamReader(p.getErrorStream()));
			while ((line = input.readLine()) != null) {
				System.out.println(line);
			}
			input.close();
			p.waitFor();
			if (p.exitValue() == 0) return true;
		} catch (Exception err) {
			err.printStackTrace();
		}
		
		return false;
	}
	
	static int DEBUG_MAX_TS = -1; //40;
	
	static PlaylistInfo readPlaylist(String hlsUrl, MuteInfo muteInfo) {
		
		if (DEBUG_MAX_TS>0) {
			System.err.println("####################################################");
			System.err.println("####################################################");
			System.err.println(" WARNING: A MAXIMUM AMOUNT OF TS FILES IS SET! ("+DEBUG_MAX_TS+")");
			System.err.println("####################################################");
			System.err.println("####################################################");
		}
		
		//String m3u8 = readStringFromURL("https://vod-secure.twitch.tv/"+hlsUrl+"/chunked/index-dvr.m3u8");
		String m3u8 = null;
		try {
			m3u8 = new String(Files.readAllBytes(Paths.get(HLS_PATH+hlsUrl+".txt")));
		} catch (IOException e) {
			//e.printStackTrace();
			System.out.println("file not found, trying download...");
			m3u8 = readStringFromURL("https://vod-secure.twitch.tv/"+hlsUrl+"/chunked/index-dvr.m3u8");
		}
		String[] lines = m3u8.split("\n");
		List<String> tsFiles = new ArrayList<>();
		List<Double> segmentLengths = new ArrayList<>();
		List<String> discoErrors = new ArrayList<>();
		double totalLength = 0;
		double pos = 0;
		for (String line : lines) {
			line = line.trim();
			if (line.startsWith("#EXT-X-TWITCH-TOTAL-SECS:")) {
				String totalsecs = line.substring(25, line.length());
				totalLength = Double.parseDouble(totalsecs);
			}
			if (line.startsWith("#EXTINF:")) {
				String extinfo = line.substring(8, line.indexOf(','));
				double segLen = Double.parseDouble(extinfo);
				segmentLengths.add(segLen);
				pos += segLen;
			}
			if (line.equals("#EXT-X-DISCONTINUITY")) {
				String discoTimestamp = secToTime(pos);
				if (!discoErrors.contains(discoTimestamp)) discoErrors.add(discoTimestamp);
			}
			if (!line.startsWith("#") && line.endsWith(".ts")) {
				tsFiles.add(line);
			}
			if (DEBUG_MAX_TS > 0 && tsFiles.size() >= DEBUG_MAX_TS) break;
		}
		System.out.println(tsFiles.size()+" .ts files in playlist");
		
		PlaylistInfo info = new PlaylistInfo();
		info.tsFiles = tsFiles;
		info.segmentLengths = segmentLengths;
		info.totalLength = totalLength;
		
		if (discoErrors.size() > 0) {
			muteInfo.discoTimes = discoErrors;
			System.out.println("STREAM HAS DISCONTINUITY ERRORS");
		}
		return info;
	}
	
	static class PlaylistInfo {
		List<String> tsFiles;
		List<Double> segmentLengths;
		double totalLength;
	}
	
	static boolean dlVid(String hlsUrl, MuteInfo mute) {
		
		System.out.println("DOWNLOADING "+hlsUrl);
		
		PlaylistInfo playlistInfo = readPlaylist(hlsUrl, mute);
		List<String> tsFiles = playlistInfo.tsFiles;
		List<Double> segmentLengths = playlistInfo.segmentLengths;
		double totalLength = playlistInfo.totalLength;
		
		
		/*
		String path = DIR+hlsUrl+"\\";
		new File(path).mkdirs();
		
		for (String ts: tsFiles) {
			System.out.println("\tchunk "+ts);
			try {
				URL website = new URL("https://vod-secure.twitch.tv/"+hlsUrl+"/chunked/"+ts);
				ReadableByteChannel rbc = Channels.newChannel(website.openStream());
				FileOutputStream fos = new FileOutputStream(path+ts);
				fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}/
		
		
		try {
			DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(TEMP+hlsUrl+".ts")));
			
			int DL_WAIT = -1;
			long lastDlTime = 0;
			double pos = 0;
			boolean isCurrentlyMuted = false;
			double currentMuteStart = 0;
			int nummuted = 0;
			HashMap<String, String> mutes = new HashMap<>();
			
			long startTime = System.currentTimeMillis();
			for (int i = 0; i<tsFiles.size(); i++) {
				String ts = tsFiles.get(i);
				double segLen = segmentLengths.get(i);
				double progr = i / (double) tsFiles.size();
				long timeElapsed = System.currentTimeMillis()-startTime;
				double secs = timeElapsed / 1000d;
				double speed = secs > 0.05 ? pos / secs : 1;
				System.out.print("\rdownloading "+ts+",\tfile "+(i+1)+" of "+tsFiles.size()+"\tnum_muted:"+nummuted+
						"\ttime_elapsed:"+secs+"s ("+secToTime(secs)+")\tavg.speed:"+String.format("%.02f", speed)+"x"+
						"\tprogress:"+String.format("%.03f", progr*100)+"%");
				
				lastDlTime = System.currentTimeMillis();
				URL chunk = new URL("https://vod-secure.twitch.tv/"+hlsUrl+"/chunked/"+ts);
				HttpURLConnection connection = (HttpURLConnection) chunk.openConnection();
				if (connection.getResponseCode() != 200) {
					//System.out.println("\nerror 404 for "+ts+", might be muted");
					String tsMuted = (ts.replace(".ts", "-muted.ts"));
					chunk = new URL("https://vod-secure.twitch.tv/"+hlsUrl+"/chunked/"+tsMuted);
					connection = (HttpURLConnection) chunk.openConnection();
					if (connection.getResponseCode() != 200) {
						System.out.println("\nerror 404 for "+ts+", and muted ts not found");
						//continue;
						out.close();
						return false; //investigate if that happens
					} else {
						//System.out.println("muted ts found, using that one... ("+tsMuted+")");
						nummuted++;
						mutes.put(ts, tsMuted);
						
						System.out.print("\rMUTE FOUND! "+ts+",\tfile "+(i+1)+" of "+tsFiles.size()+"\tnum_muted:"+nummuted+
								"\ttime_elapsed:"+secs+"s ("+secToTime(secs)+")\tavg.speed:"+String.format("%.02f", speed)+"x"+
								"\tprogress:"+String.format("%.03f", progr*100)+"%");
						
						mute.muted = true;
						if (!isCurrentlyMuted) {
							currentMuteStart = pos;
							isCurrentlyMuted = true;
						}
					}
				} else {
					if (isCurrentlyMuted) { //end mute here
						isCurrentlyMuted = false;
						mute.mutedSegments.add(secToTime(currentMuteStart)+" - "+secToTime(pos));
					}
				}
				
				InputStream input = connection.getInputStream();
				byte[] buffer = new byte[4096];
				int n;
				while ((n = input.read(buffer)) != -1) 
				{
					out.write(buffer, 0, n);
				}
				input.close();
				
				if (DL_WAIT>0) while (lastDlTime+DL_WAIT > System.currentTimeMillis()) {
					Thread.sleep(100);
				}

				pos += segLen;
			}
			out.close();
			System.out.println("\ndone downloading!");
			
			if (isCurrentlyMuted) { //if muted at end of vod
				mute.mutedSegments.add(secToTime(currentMuteStart)+" - "+secToTime(totalLength));
			}
			System.out.println(" has mute: "+mute.muted+"\tmuted files: "+nummuted+"\tmuted segments: "+mute.mutedSegments.size());
			
			System.out.println(" (i) pos - total length difference: " +(pos - totalLength));
			
			System.out.println("=> writing mute & discontinuity information to disk...");
			JSONObject muteInfoFile = new JSONObject();
			muteInfoFile.put("filemap", new JSONObject(mutes));
			muteInfoFile.put("segmentStrings", new JSONArray(mute.mutedSegments));
			muteInfoFile.put("muted", mute.muted);
			muteInfoFile.put("discontinuity", mute.discoTimes == null ? JSONObject.NULL : new JSONArray(mute.discoTimes));
			try {
				File muteFile = new File(DIR+"mutes\\mutes-"+hlsUrl+".txt");
				FileWriter w = new FileWriter(muteFile);
				muteInfoFile.write(w);
				w.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
			
			return true;
			
		} catch (Exception e1) {
			e1.printStackTrace();
		}
		
		return false;
		
		
		
	}
	
	static String secToTime(double secs) {
		int s = (int) Math.round(secs);
		int h = s/3600;
		s-= h*3600;
		int m = s/60;
		s-= m*60;
		String timestamp = String.format("%02d", s);
		if (h == 0) timestamp = m+":"+timestamp;
		else timestamp = h+":"+String.format("%02d", m)+":"+timestamp;
		return timestamp;
	}
	
	public static String readStringFromURL(String requestURL) 
	{
	    try (Scanner scanner = new Scanner(new URL(requestURL).openStream(),
	            StandardCharsets.UTF_8.toString()))
	    {
	        scanner.useDelimiter("\\A");
	        return scanner.hasNext() ? scanner.next() : "";
	    } catch (Exception e) {
	    	e.printStackTrace();
	    }
		return null;
	}
	
	static void clearTmp() {
		System.out.print("clearing temp dir... ");
		try {
			File[] list = new File(TEMP).listFiles();
			for(File f : list) {
				System.out.println(" /"+f.getName());
				f.delete();
			}
			System.out.println(" ...done!");
		} catch (Exception e) {
			System.err.println("error clearing temp dir: "+e.toString());
		}
		
	}*/
	
	
	static final String YT_API_KEY = "--- REMOVED ---";
	static final String YT_API_URL = "https://www.googleapis.com/youtube/v3";
	

	static final String[] PROJ_NAMES = {
			// --- REMOVED ---
		};
	static final String[] REFRESH_TOKEN = {
			// --- REMOVED ---
	};
	static final String[] CLIENT_ID = {
			// --- REMOVED ---
	};
	static final String[] CLIENT_SECRET = {
			// --- REMOVED ---
	};
	
	
	 /**
     * Define a global instance of a Youtube object, which will be used
     * to make YouTube Data API requests.
     */
    private static YouTube youtube;

    /**
     * Define a global variable that specifies the MIME type of the video
     * being uploaded.
     */
    private static final String VIDEO_FILE_FORMAT = "video/*";
    
    private static HttpRequestInitializer setHttpTimeout(final HttpRequestInitializer requestInitializer) {
    	  return new HttpRequestInitializer() {
    	    @Override
    	    public void initialize(HttpRequest httpRequest) throws IOException {
    	      requestInitializer.initialize(httpRequest);
    	      //System.out.println("connect_timeout: "+httpRequest.getConnectTimeout()+", read_timeout: "+httpRequest.getReadTimeout());
    	      httpRequest.setConnectTimeout(5 * 60000);  // 5 minutes connect timeout
    	      httpRequest.setReadTimeout(5 * 60000);  // 5 minutes read timeout
    	      //System.out.println("new_connect_timeout: "+httpRequest.getConnectTimeout()+", new_read_timeout: "+httpRequest.getReadTimeout());
    	    }
    	  };
    }
    
    public static YouTube getYouTubeService(int credIdx) throws IOException {
    	// Authorize the request.
    	/*
    	List<String> scopes = Lists.newArrayList("https://www.googleapis.com/auth/youtube.upload");
        Credential credential = YtAuth.authorize(scopes, "uploadvideo");
        System.out.println("ref "+credential.getRefreshToken());
        System.exit(0);;
        //*/
    	
    	System.out.println("[YT Auth] using '"+PROJ_NAMES[credIdx]+"' credentials");
        Credential credential = new GoogleCredential.Builder().setJsonFactory(YtAuth.JSON_FACTORY).setTransport(YtAuth.HTTP_TRANSPORT)
        		.setClientSecrets(CLIENT_ID[credIdx], CLIENT_SECRET[credIdx]).build().setRefreshToken(REFRESH_TOKEN[credIdx]);

        // This object is used to make YouTube Data API requests.
        return new YouTube.Builder(YtAuth.HTTP_TRANSPORT, YtAuth.JSON_FACTORY, setHttpTimeout(credential)).setApplicationName(
                "youtube-cmdline-uploadvideo-sample").build();
    }



    /**
     * Upload the user-selected video to the user's YouTube channel. The code
     * looks for the video in the application's project folder and uses OAuth
     * 2.0 to authorize the API request.
     *
     * @param args command line args (not used).
     */
    static String uploadToYt(VideoMetadata metadata, String filename) {

        // This OAuth 2.0 access scope allows an application to upload files
        // to the authenticated user's YouTube channel, but doesn't allow
        // other types of access.
        

        try {
            
        	youtube = getYouTubeService(credIndex);

            // Add extra information to the video before uploading.
            Video videoObjectDefiningMetadata = new Video();

            // Set the video to be publicly visible. This is the default
            // setting. Other supporting settings are "unlisted" and "private."
            VideoStatus status = new VideoStatus();
            status.setPrivacyStatus("private");
            videoObjectDefiningMetadata.setStatus(status);

            // Most of the video's metadata is set on the VideoSnippet object.
            VideoSnippet snippet = new VideoSnippet();

            snippet.setTitle(metadata.title);
            snippet.setDescription(metadata.desc);
            snippet.setTags(metadata.tags);


            // Add the completed snippet object to the video resource.
            videoObjectDefiningMetadata.setSnippet(snippet);

            File vidFile = new File(filename);
            InputStreamContent mediaContent = new InputStreamContent(VIDEO_FILE_FORMAT,
                    //new FileInputStream(new File(TEMP+vid.get("hlsurl").toString()+".ts")));
            		new FileInputStream(vidFile));
            		//new FileInputStream(new File(TEMP+"asdfasd.mp4")));

            // Insert the video. The command sends three arguments. The first
            // specifies which information the API request is setting and which
            // information the API response should return. The second argument
            // is the video resource that contains metadata about the new video.
            // The third argument is the actual video content.
            YouTube.Videos.Insert videoInsert = youtube.videos()
                    .insert("snippet,statistics,status", videoObjectDefiningMetadata, mediaContent);

            // Set the upload type and add an event listener.
            MediaHttpUploader uploader = videoInsert.getMediaHttpUploader();

            // Indicate whether direct media upload is enabled. A value of
            // "True" indicates that direct media upload is enabled and that
            // the entire media content will be uploaded in a single request.
            // A value of "False," which is the default, indicates that the
            // request will use the resumable media upload protocol, which
            // supports the ability to resume an upload operation after a
            // network interruption or other transmission failure, saving
            // time and bandwidth in the event of network failures.
            uploader.setDirectUploadEnabled(false);

            long vidBytes = vidFile.length();
            System.out.println("Video file size: "+ vidBytes+" bytes ("+String.format("%.03f", vidBytes / 1000000000d)+" GB)");
            System.out.println("starting upload...");
            MediaHttpUploaderProgressListener progressListener = new MediaHttpUploaderProgressListener() {
                public void progressChanged(MediaHttpUploader uploader) throws IOException {
                    switch (uploader.getUploadState()) {
                        case INITIATION_STARTED:
                            System.out.println("Initiation Started");
                            break;
                        case INITIATION_COMPLETE:
                            System.out.println("Initiation Completed");
                            break;
                        case MEDIA_IN_PROGRESS:
                        	double percent = uploader.getNumBytesUploaded() / (double) vidBytes;
                        	double gb = uploader.getNumBytesUploaded() / 1000000000d;
                            System.out.print("\rUploading\tbytes uploaded: "+uploader.getNumBytesUploaded()+" ("+String.format("%.03f", gb)+" GB)\tprogress: "+String.format("%.03f", percent*100)+"%");
                            break;
                        case MEDIA_COMPLETE:
                            System.out.println("\nUpload Completed!");
                            break;
                        case NOT_STARTED:
                            System.out.println("Upload Not Started!");
                            break;
                    }
                }
            };
            uploader.setProgressListener(progressListener);

            // Call the API and upload the video.
            Video returnedVideo = videoInsert.execute();

            // Print data about the newly inserted video from the API response.
            System.out.println("\n========== Returned Video ==========");
            System.out.println("  - Id: " + returnedVideo.getId());
            System.out.println("  - Title: " + returnedVideo.getSnippet().getTitle());
            System.out.println("  - Tags: " + returnedVideo.getSnippet().getTags());
            System.out.println("  - Privacy Status: " + returnedVideo.getStatus().getPrivacyStatus());
            //System.out.println("  - Video Count: " + returnedVideo.getStatistics().getViewCount());
            
            return returnedVideo.getId();

        } catch (GoogleJsonResponseException e) {
        	if (e.getDetails().getMessage().contains("exceeded") && e.getDetails().getMessage().contains("quota")) {
        		quotaExceeded();
        		return uploadToYt(metadata, filename);
        	} else {
        		System.err.println("GoogleJsonResponseException code: " + e.getDetails().getCode() + " : "
                        + e.getDetails().getMessage());
                e.printStackTrace();
        	}
        } catch (IOException e) {
            System.err.println("IOException: " + e.getMessage());
            e.printStackTrace();
        } catch (Throwable t) {
            System.err.println("Throwable: " + t.getMessage());
            t.printStackTrace();
        }
        
        return null;
    }
    
    static long lastQuotaExceedTime = 0;
    static void quotaExceeded() {
    	System.err.println("ERROR: QUOTA EXCEEDED...");
    	int waitTime = 2000;
    	if (QUOTA_CHANGE_LONG_WAIT && System.currentTimeMillis() - lastQuotaExceedTime < 3*60*1000) { //if less than 3 mins ago
    		waitTime = 2 * 60 * 1000; //2 min wait time
    	}
    	lastQuotaExceedTime = System.currentTimeMillis();
		try {
			Thread.sleep(waitTime);
		} catch (InterruptedException e1) {
			e1.printStackTrace();
		}
		
		System.out.println("switching credentials...");
		int oldCredIndex = credIndex;
		credIndex = (credIndex + 1) % PROJ_NAMES.length;
		try {
			File indexfile = new File(TwitchThing2.DIR+"cred\\index.txt");
			FileWriter w = new FileWriter(indexfile);
			w.write(String.valueOf(credIndex));
			w.close();
			
			File exfile = new File(TwitchThing2.DIR+"cred\\quotaextime\\"+PROJ_NAMES[oldCredIndex]+".txt");
			FileWriter w2 = new FileWriter(exfile);
			w2.write(String.valueOf(System.currentTimeMillis() / 1000L));
			w2.close();
		} catch (Exception ex) {
			ex.printStackTrace();
		}		
	}

	static class VideoMetadata {
    	String title;
    	String desc;
    	List<String> tags;
    }
	
	static VideoMetadata genTitleAndDescription2(
				JSONObject vid,
				int vidnum,
				int numvids, 
				boolean isRescue, 
	    		int partIndex,
	    		JSONObject playlistinfo,
	    		JSONArray mutedSegmentsFromPlaylist,
	    		JSONArray ytLinkListSelf,
	    		String prevVideoId,
	    		String nextVideoId,
	    		String fullHlsUrl,
	    		
	    		JSONArray mutedSegmentsClaims,
	    		String unblockLink,
	    		String noUnblockLink
    		) throws Exception {
    	
    	VideoMetadata metadata = new VideoMetadata();
    	
    	boolean muted = mutedSegmentsFromPlaylist != null && !isRescue;
    	boolean disco = playlistinfo.getJSONArray("discoTimes").length() > 0;
    	int numParts = playlistinfo.getInt("numParts");
    	boolean multipart = numParts > 1;
    	
    	boolean mutedClaims = false;
    	if (mutedSegmentsClaims != null) {
        	int claimedSegInThisPart = 0;
        	for (int i = 0; i<mutedSegmentsClaims.length(); i++) {
        		JSONObject claim = mutedSegmentsClaims.getJSONObject(i);
        		if (unblockLink != null && claim.getString("blocked_in").equals("some")) {
        			continue;
        		}
    			claimedSegInThisPart++;
        	}
        	if (claimedSegInThisPart > 0) {
        		mutedClaims = true;
    		}
        }
    	
        SimpleDateFormat inFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        inFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    	Date recordDate = inFormat.parse(vid.getString("time"));
    	SimpleDateFormat outFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    	outFormat.setTimeZone(TimeZone.getTimeZone("EST"));
    	
    	boolean hasgame = vid.getString("game") != null && !vid.getString("game").trim().equals("");
    	String gameShort = vid.getString("game");
    	if (hasgame) {
    		gameShort = gameShort
    				.replace("PLAYERUNKNOWN'S BATTLEGROUNDS", "PUBG")
    				.replace("Counter-Strike: Global Offensive", "CS:GO");
    	}
    	String videoTitle = 
    			"#"+vidnum+
    			(multipart ? " Part "+(partIndex+1)+"/"+numParts : "")+ " "+
    			"["+(vid.getString("time").substring(0, 10))+(hasgame?(" | "+gameShort):"")+"] "
    			+vid.getString("title");
    	if (videoTitle.length() > 100) {
    		videoTitle = videoTitle.substring(0, 99) + "\u2026"; // ...
    	}
    	metadata.title = videoTitle; //max. title length is 100
        //https://stackoverflow.com/questions/1001540/how-to-write-a-utf-8-file-with-java
        // muted: \uD83D\uDD07
        // speaker with 3 lines: \uD83D\uDD0A
        
    	
    	int superskript = 1;
        String discoStar = "";
        String muteStar = "";
    	
    	String lbls = "";
    	//if (isRescue) tags += "\u2705"; //checkmark
    	if (muted || mutedClaims) {
    		muteStar = getSuperskript(superskript); superskript++;
    		lbls += "\uD83D\uDD07"+muteStar; //mute speaker
    	}
    	if (disco) {
        	discoStar = getSuperskript(superskript); superskript++;
        	lbls += "\u26A0"+discoStar;
        }
    	
    	if (lbls.isEmpty()) {
    		lbls = "\u2705"; //checkmark
    	}
        String desc = "";
        // desc += "If blocked in your country, try this version: ";
        desc += "["+lbls+"] https://deadmau5archive.github.io/videos/"+vid.getString("id")+
        		(numParts>1 ? "/"+(partIndex+1) : "")+
        		(noUnblockLink != null ? "/unblocked" : "")+"\n";
        
        if (unblockLink != null) {
        	desc += "(BLOCKED IN SOME COUNTRIES!) unblocked version: https://youtu.be/"+unblockLink+"\n";
        } else if (noUnblockLink != null) {
        	desc += "(SOME SONGS MUTED TO PREVENT BLOCK!) normal version: https://youtu.be/"+noUnblockLink+"\n";
        }
        
        if (multipart) {
        	boolean bullet = false;
        	for (int p = 0; p < numParts; p++) {
        		if (p == partIndex) continue;
        		if (bullet) desc += " \u2022 ";
        		String partLink = ytLinkListSelf.getString(p);
        		if (partLink == null) partLink = "\u2013"; //endash
        		else partLink = "https://youtu.be/"+partLink;
        		desc += "Part "+(p+1)+": "+partLink;
        		bullet = true;
        	}
        	desc += "\n";
        }
        if (prevVideoId == null) desc += "Next: https://youtu.be/"+nextVideoId+"\n";
        else if (nextVideoId == null) desc += "Previous: https://youtu.be/"+prevVideoId+"\n";
        else desc += "Previous: https://youtu.be/"+prevVideoId+" \u2022 Next: https://youtu.be/"+nextVideoId+"\n";
        //if (!multipart) desc += "\n";
        
        
        desc += "\n"+
		"Title: "+vid.getString("title")+"\n"+
        "Streamed at: "+outFormat.format(recordDate)+" EST ("+(vid.getString("time").replace("T", " ").replace("Z", ""))+" UTC)\n"+
        "Category: "+vid.getString("game")+"\n"+
        "Original length: "+ TwitchThing2.secToTime(vid.getInt("length"))+"\n"+
        "Twitch video URL: https://twitch.tv/videos/"+vid.getString("id")+"\n"+
        "HLS playlist URL: "+fullHlsUrl+"\n"+
        //"Video #"+vidnum+" of "+numvids+" total VODs (as of upload date)\n"+
        "\n"+
        "Source: "+(isRescue ? "Live recording" : "Twitch VOD service")+"\n"+
        "Twitch mute status: " + (isRescue ? "No mutes, this is a live recording" : (muted ? "Parts of the audio are muted" : "None of the audio has been muted")) +"\n"+
        "YouTube mute status: " + (mutedClaims ? "Parts of the audio had to be muted" : "None of the audio had to be muted") +"\n"+
        "Discontinuity status: " + (disco ? "This stream has discontinuity errors" : "No discontinuity")+"\n";
        
        double partStart = playlistinfo.getJSONArray("partStartTimes").getDouble(partIndex);
        double partEnd = partIndex==numParts-1 ? playlistinfo.getDouble("totalLength") : playlistinfo.getJSONArray("partStartTimes").getDouble(partIndex+1);
        if (muted || mutedClaims) {
        	desc += "\n"+muteStar;
        }
        if (muted) {
    		desc += "MUTED SEGMENTS:\n";
    		int numMutedSegInThisPart = 0;
    		for (int i = 0; i<mutedSegmentsFromPlaylist.length(); i++) {
    			JSONArray segmentInfo = mutedSegmentsFromPlaylist.getJSONArray(i);
    			double segStart = segmentInfo.getDouble(0);
    			double segEnd = segmentInfo.getDouble(1);
    			if (segEnd >= partStart-0.1 && segStart <= partEnd+0.1) {
    				double segStartInPart = segStart - partStart;
    				double segEndInPart = segEnd - partStart;
    				if (segStartInPart < 0) segEndInPart = 0;
    				if (segEndInPart > partEnd - partStart) segEndInPart = partEnd - partStart;
    				desc += TwitchThing2.secToTime(segStartInPart)+" - "+
        					TwitchThing2.secToTime(segEndInPart)+"\n";
    				numMutedSegInThisPart++;
    			}
    		}
    		if (numMutedSegInThisPart == 0) {
    			desc += "(none in this part)\n";
    		}
    	}
        if (mutedClaims) {
        	desc += "SONGS REMOVED:\n";
        	int claimedSegInThisPart = 0;
        	double partLength = partEnd - partStart;
        	for (int i = 0; i<mutedSegmentsClaims.length(); i++) {
        		JSONObject claim = mutedSegmentsClaims.getJSONObject(i);
        		if (unblockLink != null && claim.getString("blocked_in").equals("some")) {
        			continue;
        		}
        		double segStart = claim.getDouble("start") - TwitchThing2.COPYRIGHT_MUTE_EXPAND;
    			double segEnd = claim.getDouble("end") + TwitchThing2.COPYRIGHT_MUTE_EXPAND;
    			if (segStart < 0) segStart = 0;
    			if (segEnd > partLength) segEnd = partLength;
    			
    			desc += claim.getString("title")+" ("+TwitchThing2.secToTime(segStart)+" - "+
    					TwitchThing2.secToTime(segEnd)+")\n";
    			claimedSegInThisPart++;
        	}
        	if (claimedSegInThisPart == 0) {
    			desc += "(none in this part)\n";
    		}
        }
        
        if (disco) {
        	desc += "\n"+discoStar+"DISCONTINUITY AT:\n";
        	int numDiscoTimesInThisPart = 0;
        	JSONArray discoTimes = playlistinfo.getJSONArray("discoTimes");
        	for (int i = 0; i<discoTimes.length(); i++) {
        		double dis = discoTimes.getDouble(i);
        		if (dis >= partStart-0.1 && dis <= partEnd+0.1) {
        			desc += TwitchThing2.secToTime(dis - partStart)+"\n";
        			numDiscoTimesInThisPart++;
        		}
    		}
        	if (numDiscoTimesInThisPart == 0) {
    			desc += "(none in this part)\n";
    		}
        }
        
        desc += "\nThis is a recorded livestream from https://twitch.tv/deadmau5\n"+
                "All rights belong to Joel Zimmerman (deadmau5).\n"+
                "This video is not monetized. If you see ads, they're placed by copyright holders that claimed the video or by YouTube.\n";
        
        if (multipart) {
        	desc += "This video was split into multiple parts because YouTube does not allow videos longer than 12 hours as of the time of upload.\n";
        }
        if (muted) {
    		desc += "Parts of the audio have been muted automatically by Twitch for copyright reasons.\n";
    	}
        if (mutedClaims) {
    		desc += "Parts of the audio have been muted by me so YouTube wouldn't block the video.\n";
    	}
        
        /*
        System.out.println("VIDEO TITLE: "+videoTitle);
        System.out.println("VIDEO DESCRIPTION:");
        System.out.println(desc+"\n");
        //*/
        
        File log = new File("F:/deadmau5/desctest.txt");
        OutputStreamWriter w =  new OutputStreamWriter(new FileOutputStream(log), StandardCharsets.UTF_8);
		w.write(desc);
		w.close();
        
        metadata.desc = desc;
        
        List<String> tags = new ArrayList<String>();
        if (hasgame) {
        	String game = vid.getString("game").trim();
            if (game.length()>0) tags.add(game);
        }
        tags.add("deadmau5");
        tags.add("livestream");
        tags.add("stream");
        tags.add("mau5");
        tags.add("twitch");
        tags.add("vod");
        tags.add("archive");
        metadata.tags = tags;
        
		return metadata;
    	
    }
    
    
    static VideoMetadata genTitleAndDescription(JSONObject vid, int vidnum, int numvids, boolean isRescue, TwitchThing2.VodInfo info, 
    		int partIndex) throws Exception {
    	
    	VideoMetadata metadata = new VideoMetadata();
    	
    	boolean muted = info.muted;
    	boolean multipart = info.numParts > 1;
    	
    	// This code uses a Calendar instance to create a unique name and
        // description for test purposes so that you can easily upload
        // multiple files. You should remove this code from your project
        // and use your own standard names instead.
        SimpleDateFormat inFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        inFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    	Date recordDate = inFormat.parse(vid.getString("time"));
    	SimpleDateFormat outFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    	outFormat.setTimeZone(TimeZone.getTimeZone("EST"));
    	
    	String videoTitle = "#"+vidnum+
    			(multipart ? " Part "+(partIndex+1)+"/"+info.numParts : "")+
    			" ["+(vid.getString("time").substring(0, 10))+" | "+vid.getString("game")+"] "+vid.getString("title");
    	metadata.title = videoTitle.substring(0, Math.min(videoTitle.length(), 100)); //max. title length is 100
        //https://stackoverflow.com/questions/1001540/how-to-write-a-utf-8-file-with-java
        // muted: \uD83D\uDD07
        // speaker with 3 lines: \uD83D\uDD0A
        
        String desc = "";
        
        int superskript = 1;
        String discoStar = "";
        String muteStar = "";
        
        //TODO links
        List<String> partLinks = new ArrayList<>();
        String prevLink = "?";
        String nextLink = "?";
        
        if (multipart) {
        	boolean bullet = false;
        	for (int p = 0; p < info.numParts; p++) {
        		if (p == partIndex) continue;
        		if (bullet) desc += " \u2022 ";
        		String partLink = p<partLinks.size() ? partLinks.get(p) : "?";
        		desc += "Part "+(p+1)+": "+partLink;
        		bullet = true;
        	}
        	desc += "\n";
        }
        
        desc += "Previous: "+prevLink+" \u2022 Next: "+nextLink+"\n";
        
        if (isRescue) {
        	if (muted) {
        		muteStar = getSuperskript(superskript); superskript++;
        		desc += "[\uD83D\uDD07] A tiny portion of the audio is muted (I fucked up)."+muteStar+"\n";
        	} else {
        		desc += "[\uD83D\uDD0A] Audio is not muted (This is a live recording).\n";
        	}
        } else {
        	if (muted) {
        		muteStar = getSuperskript(superskript); superskript++;
        		desc += "[\uD83D\uDD07] Parts of the audio are muted (This is a Twitch VOD)."+muteStar+"\n";
        	} else {
        		//desc += "[\uD83D\uDD0A] None of the audio appears to have been muted (This is a Twitch VOD).\n";
        		desc += "[\uD83D\uDD0A] None of the audio has been muted (This is a Twitch VOD).\n";
        	}
        	
        }
        if (info.discoTimesDouble != null) {
        	discoStar = getSuperskript(superskript); superskript++;
        	desc += "[\u26A0] This stream has discontinuity errors."+discoStar+"\n";
        }
        
        desc += "\n"+
        "Streamed at "+outFormat.format(recordDate)+" EST ("+(vid.getString("time").replace("T", " ").replace("Z", ""))+" UTC)\n"+
        "Title: "+vid.getString("title")+"\n"+
        "Category: "+vid.getString("game")+"\n"+
        "Original length: "+ TwitchThing2.secToTime(vid.getInt("length"))+"\n"+
        "Twitch video URL: https://twitch.tv/videos/"+vid.getString("id")+"\n"+
        //"HLS playlist URL: https://vod-secure.twitch.tv/"+vid.getString("hlsurl")+"/chunked/index-dvr.m3u8\n"+
        "Video #"+vidnum+" of "+numvids+" total VODs (as of upload date)\n"+
        "\n"+
        "This is a recorded livestream from https://twitch.tv/deadmau5\n"+
        "All rights belong to Joel Zimmerman (deadmau5).\n"+
        "This video is not monetized. If you see ads, they're placed by copyright holders that claimed the video or by YouTube.\n";
        if (multipart) {
        	desc += "This video was split into multiple parts because YouTube does not allow videos longer than 12 hours as of the time of upload.";
        }
        if (isRescue) {
        	desc += "This recording was done live, before Twitch "+(muted?"":"potentially ")+"muted parts of the VOD.";
        	if (muted) {
        		desc += " However, my live recording had some corrupted segments so I had to redownload those from Twitch, "
        				+ "and they happened to be ones that got muted. So there are a few short muted parts in this video.";
        	} else {
        		desc += " This video therefore does not have muted segments.";
        	}
        	desc += "\n";
        } else {
        	desc += "This recording was obtained from Twitch's VOD service.";
        	if (muted) {
        		desc += " Parts of the audio have been muted automatically by Twitch for copyright reasons.";
        	}
        	desc += "\n";
        }
        double partStart = info.partStartTimes.get(partIndex);
        double partEnd = partIndex==info.numParts-1 ? info.totalLength : info.partStartTimes.get(partIndex+1);
        if (muted) {
    		desc += "\n"+muteStar+"MUTED SEGMENTS:\n";
    		int numMutedSegInThisPart = 0;
    		for (int i = 0; i<info.mutedSegmentsStartTimes.size(); i++) {
    			double segStart = info.mutedSegmentsStartTimes.get(i);
    			double segEnd = info.mutedSegmentsEndTimes.get(i);
    			if (segEnd >= partStart-0.1 && segStart <= partEnd+0.1) {
    				double segStartInPart = segStart - partStart;
    				double segEndInPart = segEnd - partStart;
    				if (segStartInPart < 0) segEndInPart = 0;
    				if (segEndInPart > partEnd - partStart) segEndInPart = partEnd - partStart;
    				desc += TwitchThing2.secToTime(segStartInPart)+" - "+
        					TwitchThing2.secToTime(segEndInPart)+"\n";
    				numMutedSegInThisPart++;
    			}
    		}
    		if (numMutedSegInThisPart == 0) {
    			desc += "(none in this part)\n";
    		}
    	}
        if (info.discoTimesDouble != null) {
        	desc += "\n"+discoStar+"DISCONTINUITY ERRORS AT:\n";
        	int numDiscoTimesInThisPart = 0;
        	for (Double disco: info.discoTimesDouble) {
        		if (disco >= partStart-0.1 && disco <= partEnd+0.1) {
        			desc += TwitchThing2.secToTime(disco - partStart)+"\n";
        			numDiscoTimesInThisPart++;
        		}
    		}
        	if (numDiscoTimesInThisPart == 0) {
    			desc += "(none in this part)\n";
    		}
        }
        
        System.out.println("VIDEO TITLE: "+videoTitle);
        System.out.println("VIDEO DESCRIPTION:");
        System.out.println(desc+"\n");
        /*File log = new File(DIR+"desctest.txt");
        OutputStreamWriter w =
                new OutputStreamWriter(new FileOutputStream(log), StandardCharsets.UTF_8);
		w.write(desc);
		w.close();*/
        
        metadata.desc = desc;
        
        List<String> tags = new ArrayList<String>();
        String game = vid.getString("game").trim();
        if (game.length()>0) tags.add(game);
        tags.add("deadmau5");
        tags.add("livestream");
        tags.add("stream");
        tags.add("mau5");
        tags.add("twitch");
        tags.add("vod");
        tags.add("archive");
        metadata.tags = tags;
        
		return metadata;
    	
    }
    
    static String getSuperskript(int n) {
    	// ^1 \u00B9     ^2 \u00B2
    	if (n == 1) return "\u00B9";
    	if (n == 2) return "\u00B2";
    	if (n == 3) return "\u00B3";
    	if (n == 4) return "\u2074";
    	String star = "";
    	for (int i = 0; i<n; i++) star += "*";
    	return star;
    }

}
