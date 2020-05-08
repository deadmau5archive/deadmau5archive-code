import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.cert.X509Certificate;
import java.text.DateFormatSymbols;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Scanner;
import java.util.TimeZone;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

public class TsStreamToVideo {

	static final String CHANNEL = "deadmau5";
	
	static final String MIXER_VIDS_PLAYLIST_ID = "PLEh04lts8PqQ6SV7jYtQV7arNXIkuvlRT";

	static String SRC = "\\\\RASPI\\streamrec\\mixer\\dl\\deadmau5\\";
	static String BASE_DIR = "F:\\mixer\\J\\";
	static String TEMP_DIR = BASE_DIR + "tmp\\";

	static String info_url = " --- REMOVED ---"; //url to the mixer dl info page (use key as GET argument for auth)

	static final String ENSPACE = "\u2002";
	static final String EMDASH = "\u2014";

	static HashMap<Integer, String> typeMap;
	static HashMap<Integer, String> typeMapFull;
	static List<VodInfo> vods;
	
	// when true, will only generate the info.txt and the info_proc files for all the videos
	// but not convert / upload anything. useful for generating configs for the web script
	static boolean DRY_RUN = false;
	
	// when true will regenerate and update all the descriptions
	static boolean DESCRIPTION_UPDATE_MODE = false;
	static boolean VIDEO_UPDATE_VISIBILITY_ONLY = false;
	
	final static boolean NOTIFY_SUBS_ON_UPLOAD = true;
	
	static int PROCESSING_START = 0; //index (not stream number!) where to start
	static int PROCESSING_LIMIT = 10000;
	
	static final String DEFAULT_PRIVACY = "private"; //private unlisted public

	public static void main(String[] args) {
		//YtTest.credIndex = 5;
		
		System.out.println("DRY_RUN="+DRY_RUN);
		System.out.println("DESCRIPTION_UPDATE_MODE="+DESCRIPTION_UPDATE_MODE);
		System.out.println("DEFAULT_PRIVACY="+DEFAULT_PRIVACY);
		System.out.println("PROCESSING_START="+PROCESSING_START);
		System.out.println("PROCESSING_LIMIT="+PROCESSING_LIMIT);
		System.out.println();
		
		new File(BASE_DIR).mkdirs();
		new File(TEMP_DIR).mkdirs();  // joined ts files go in here (gets deleted after)
		new File(BASE_DIR + "ffmpeg-logs\\").mkdirs(); //logs for ffmpeg ts to mp4 conversion
		new File(BASE_DIR + "upload\\").mkdirs(); //converted videos go in here (gets deleted after)
		new File(BASE_DIR + "processed\\").mkdirs(); //marks a vod contentId as processed (with txt files)
		new File(BASE_DIR + "info_proc\\").mkdirs(); //info about processing (with json files)
		new File(BASE_DIR + "ytids\\").mkdirs();  //saves ids of uploaded yt videos
		new File(BASE_DIR + "monthplaylists\\").mkdirs();  //txt files (yyyy-mm.txt) with month playlist ids
		new File(BASE_DIR + "vidinplaylist\\").mkdirs();  //stores what vid is in which playlist already (<youtubeID>_<playlistID>.txt)

		disableBiggestBullshitEver();
		JSONObject db = new JSONObject(new JSONTokener(readUrl(info_url)));
		System.out.println(db.length() + " VODs in DB");
		
		List<Integer> typeList = new ArrayList<>();
		vods = new ArrayList<>();
		int numUnprocessed = 0;

		for (Object n : db.names()) {
			VodInfo vi = new VodInfo();

			String key = (String) n; // key is contentId
			vi.contentId = key;
			if (new File(BASE_DIR + "processed\\" + key + ".txt").exists()) {
				vi.processed = true;
				// can't continue because need all streams in order in vods list
				// for correct stream numbers
			} else
				numUnprocessed++;

			JSONObject vod = db.getJSONObject(key).getJSONObject("json");
			String uploadDateFull = vod.getString("uploadDate");
			Date d = Date.from(Instant.parse(uploadDateFull));
			vi.vod = vod;
			vi.uploadDatems = d.getTime();

			//if (!vi.processed) {
				int typeId = vod.getInt("typeId");
				if (!typeList.contains(typeId))
					typeList.add(typeId);
			//}

			vods.add(vi);
		}
		System.out.println(numUnprocessed + " VODs unprocessed");

		System.out.println("Loading types...");
		typeMap = new HashMap<>();
		typeMapFull = new HashMap<>();
		for (int type : typeList) {
			JSONObject typeObj = new JSONObject(new JSONTokener(readUrl("https://mixer.com/api/v1/types/" + type)));
			String cat = typeObj.getString("name");
			String catShort = cat;
			if (cat.startsWith("PLAYERUNKNOWN")) {
				catShort = "PUBG";
			}
			System.out.println("Loading type " + type + ": " + cat);
			typeMap.put(type, catShort);
			typeMapFull.put(type, cat);
		}

		System.out.println("Sorting VODs...");
		Collections.sort(vods, new Comparator<VodInfo>() {
			@Override
			public int compare(VodInfo o1, VodInfo o2) {
				if (o1.uploadDatems == o2.uploadDatems)
					return 0;
				return o1.uploadDatems < o2.uploadDatems ? -1 : 1;
			}
		});
		
		//save info.txt
		JSONArray sortedVods = new JSONArray();
		for (int i = 0; i < vods.size(); i++) {
			JSONObject vod = vods.get(i).vod;
			vod.put("category_string", typeMapFull.get(vod.getInt("typeId")));
			sortedVods.put(vod);
		}
		try {
			File dbInfoFile = new File(BASE_DIR + "info.txt");
			FileWriter w = new FileWriter(dbInfoFile);
			w.write(sortedVods.toString());
			w.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

		System.out.println("Generating VOD info...");
		int processing_count = 0;
		
		for (int i = PROCESSING_START; i < vods.size(); i++) {
			VodInfo info = vods.get(i);
			info.streamIndex = i;
			
			System.out.println("\n\n --- ["+i+" "+info.contentId+"] ---");
			
			//readExtraInfo(info);
			//storeExtraInfo(info);
			
			if (DESCRIPTION_UPDATE_MODE) {
				updateLinksInDescription(info);
			} else if (!info.processed && !DRY_RUN) {
				// read hls playlist and event log
				readExtraInfo(info);
				storeExtraInfo(info);
				
				// porcess (convert & upload)
				process(info);
				
				// if multi-part, update decription for part links:
				if (info.numParts > 1) updateLinksInDescription(info);
				//now that new vid is uploaded, change desc of last one to include the link
				updateLinksInDescriptionOfLastVideo(info);
				
				// add this video to it's month playlist, and also to the all videos playlist.
				doPlaylistThings(info);
			}
				
			processing_count++;
			if (processing_count >= PROCESSING_LIMIT){
				System.out.println("limit reached!");
				break;
			}
			
			//new Scanner(System.in).nextLine();
		}
		
		/*for (int i = 0; i < vods.size(); i++) {
			if (vods.get(i).contentId.equals("ae53749f-3d8d-4717-9fa8-efb55a8fb602")) {
				process(vods.get(i));
			}
		}*/

		System.out.println("DONE!");
	}

	private static void doPlaylistThings(VodInfo info) {
		System.out.println("inserting videos to playlists...");
		//get month playlist, or create if not exist
		if (info.timeRecordingStarted == null) {
			System.err.println("timeRecordingStarted is null");
			return;
		}
		String yyyymm = getDateStringESTUTC(info.timeRecordingStarted).substring(0, 7); //can do only if event log has been read before (which it has)
		System.out.println("yyymm = "+yyyymm);
		File playlistF = new File(BASE_DIR + "monthplaylists\\"+yyyymm+".txt");
		String monthPlaylistId = null;
		if (playlistF.exists()) {
			try {
				monthPlaylistId = new String(Files.readAllBytes(Paths.get(playlistF.getAbsolutePath()))).trim();
			} catch (IOException e) {
				e.printStackTrace();
			}
			System.out.println("got month playlist id: "+monthPlaylistId);
		} else {
			String[] months = new DateFormatSymbols().getMonths();
			int monthInt = Integer.parseInt(yyyymm.substring(5,7));
			monthPlaylistId = YtTest.createPlaylist(months[monthInt-1]+" "+yyyymm.substring(0,4), DEFAULT_PRIVACY);
			try {
				FileWriter w = new FileWriter(playlistF);
				w.write(monthPlaylistId);
				w.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			System.out.println("created month playlist id: "+monthPlaylistId);
		}
		
		//add to month playlist if not in already
		//add to mixer playlist if not in already (PLEh04lts8PqQ6SV7jYtQV7arNXIkuvlRT)
		
		for (int p = 0; p<info.numParts; p++) {
			System.out.println("inserting part "+p+" into playlistst...");
			
			File ytIdFile = new File(BASE_DIR + "ytids\\" + p + "-" + info.contentId + ".txt");
			System.out.println("part "+p+" ytid file:"+ytIdFile.getAbsolutePath());
			String ytId = null;
			try {
				ytId = new String(Files.readAllBytes(Paths.get(ytIdFile.getAbsolutePath()))).trim();
			} catch (IOException e) {
				e.printStackTrace();
			}
			
			if (!checkIfInPlaylist(ytId, monthPlaylistId)) {
				YtTest.insertVideoIntoPlaylist(monthPlaylistId, ytId);
				createVidInPlaylistFlagFile(ytId, monthPlaylistId);
			}
			if (!checkIfInPlaylist(ytId, MIXER_VIDS_PLAYLIST_ID)) {
				YtTest.insertVideoIntoPlaylist(MIXER_VIDS_PLAYLIST_ID, ytId);
				createVidInPlaylistFlagFile(ytId, MIXER_VIDS_PLAYLIST_ID);
			}
			
		}
	}
	
	private static boolean checkIfInPlaylist(String ytId, String playlistId) {
		File flag = new File(BASE_DIR + "vidinplaylist\\"+ytId+"_"+playlistId+".txt");
		boolean inPlaylist = flag.exists();
		System.out.println("[checkIfInPlaylist] ytId="+ytId+" playlistId="+playlistId+" RESULT="+inPlaylist);
		return inPlaylist;
	}
	
	private static void createVidInPlaylistFlagFile(String ytId, String playlistId) {
		System.out.println("creating playlist flag file "+ytId+"_"+playlistId+".txt");
		if (ytId == null || ytId.isEmpty()) {
			System.err.println("yt id is empty!");
			return;
		}
		if (playlistId == null || playlistId.isEmpty()) {
			System.err.println("playlist id is empty!");
			return;
		}
		try {
			new File(BASE_DIR + "vidinplaylist\\"+ytId+"_"+playlistId+".txt").createNewFile();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static void updateLinksInDescriptionOfLastVideo(VodInfo info) {
		System.out.println("[updateLinksInDescriptionOfLastVideo] thisvideo=["+info.streamIndex+" "+info.contentId+"]");
		if (info.streamIndex > 0) {
			VodInfo lastVod = vods.get(info.streamIndex-1);
			System.out.println("lastvideo=["+lastVod.streamIndex+" "+lastVod.contentId+"]");
			updateLinksInDescription(lastVod);
		}
	}
	
	private static void updateLinksInDescription(VodInfo info) {
		System.out.println("[updateLinksInDescription] video=["+info.streamIndex+" "+info.contentId+"]");
		
		if (!info.extra) {
			System.err.println("[updateLinksInDescription] video (idx="+(info.streamIndex)+") does not have extra info, trying to read it now...");
			readExtraInfo(info);
		}
		
		try {
			JSONObject procInfo = new JSONObject(new JSONTokener(new FileInputStream(
					new File(BASE_DIR + "info_proc\\"+info.contentId+".txt"))));
			
			int parts = procInfo.getInt("numParts");
			System.out.println(parts+" parts");
			for (int p = 0; p < parts; p++) {
				String videoPartYtId = getYtId(p, info.contentId);
				System.out.println("updating part "+p+" (ytid="+videoPartYtId+")");
				YtTest.VideoMetadata meta = genVidMetadata(info, p);
				if (VIDEO_UPDATE_VISIBILITY_ONLY) ChannelManager.updateVideoPrivacy(videoPartYtId, meta.privacy);
				else YtTest.updateVideo(videoPartYtId, meta);
			} 
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	static List<String> getTags(String cat) {
		List<String> tags = new ArrayList<String>();
		if (cat.length() > 0)
			tags.add(cat.trim());
		tags.add("deadmau5");
		tags.add("live");
		tags.add("livestream");
		tags.add("stream");
		tags.add("mau5");
		tags.add("mixer");
		tags.add("vod");
		tags.add("archive");
		tags.add("gaming");
		tags.add("studio");
		tags.add("music");
		tags.add("production");
		return tags;
	}

	static String readUrl(String url) {
		try {
			Scanner s = new Scanner(new URL(url).openStream(), "UTF-8");
			String out = s.useDelimiter("\\A").next();
			s.close();
			return out;
		} catch (MalformedURLException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

	/*static void readPlaylist(VodInfo vi) {
		System.out.println("reading m3u8 hls playlist file " + vi.contentId + "...");
		String m3u8 = null;
		try {
			m3u8 = new String(Files.readAllBytes(Paths.get(SRC + vi.contentId + "\\index.m3u8")));
		} catch (IOException e) {
			e.printStackTrace();
		}
		String[] lines = m3u8.split("\n");
		List<String> tsFiles = new ArrayList<>();
		List<Double> segmentLengths = new ArrayList<>();
		double pos = 0;
		for (String line : lines) {
			line = line.trim();
			if (line.startsWith("#EXTINF:")) {
				String extinfo = line.substring(8, line.indexOf(','));
				double segLen = Double.parseDouble(extinfo);
				segmentLengths.add(segLen);
				pos += segLen;
			}
			// #EXT-X-DISCONTINUITY doesn't exist on mixer
			if (!line.startsWith("#") && line.endsWith(".ts")) {
				tsFiles.add(line);
			}
		}
		System.out.println(tsFiles.size() + " .ts files in playlist");

		vi.tsFiles = tsFiles;
		vi.segmentLengths = segmentLengths;
		vi.totalLength = vi.vod.getInt("durationInSeconds");
		System.out.println("length = " + vi.totalLength);

		if (vi.tsFiles.size() != vi.segmentLengths.size()) {
			System.err.println("error " + vi.tsFiles.size() + " ts files in m3u8 but " + vi.segmentLengths.size()
					+ " segment lengths!!!");
		}
		int videoMaxLen = 12 * 3600 - 5;
		if (vi.totalLength > videoMaxLen) {
			int numParts = (int) Math.round(Math.ceil(vi.totalLength / videoMaxLen));
			System.out.println("video is longer than 12 hours");
			System.out.println("splitting into " + numParts + " parts!");
			vi.numParts = numParts;
		}
		int numTsFiles = vi.tsFiles.size();
		int i = 0;
		int limit = 0;
		pos = 0;
		for (int p = 0; p < vi.numParts; p++) {
			vi.partStartTimes.add(pos);
			vi.partStartIndex.add(i);
			if (p == vi.numParts - 1) {
				limit = numTsFiles;
			} else {
				limit = numTsFiles * (p + 1) / vi.numParts;
			}
			for (; i < limit; i++) {
				pos += vi.segmentLengths.get(i);
			}
		}
	}*/
	
	static void calcLengths(VodInfo vi) { // readPlaylist v2
		
		vi.totalLength = vi.vod.getInt("durationInSeconds");
		System.out.println("length = " + vi.totalLength);

		int videoMaxLen = 12 * 3600 - 5;
		if (vi.totalLength > videoMaxLen) {
			int numParts = (int) Math.round(Math.ceil(vi.totalLength / videoMaxLen));
			System.out.println("video is longer than 12 hours");
			System.out.println("splitting into " + numParts + " parts!");
			vi.numParts = numParts;
		}
		
		
		for (int p = 0; p<vi.numParts; p++) {
			vi.partStartTimes.add((int)( vi.totalLength * p / vi.numParts ));
		}
	}
	
	private static void readRecordingStartEndTimes(VodInfo vi) {
		System.out.println("reading chat/events file " + vi.contentId + "...");
		try {
			BufferedReader br = new BufferedReader(new FileReader(new File(SRC + "meta\\" + vi.contentId + "\\chat.txt")));
			String event = null;
			int numEvents = 0;
			while ((event = br.readLine()) != null) {
				event = event.trim();
				if (!event.isEmpty()) {
					numEvents++;
					JSONObject ev = new JSONObject(new JSONTokener(event));
					String evDate = ev.getString("date");
					String evType = ev.getJSONObject("content").getString("event");
					if (evType.equalsIgnoreCase("RecordingStarted")) {
						vi.timeRecordingStarted = evDate;
						System.out.println("RecordingStarted = "+evDate);
					} else if (evType.equalsIgnoreCase("RecordingEnded")) {
						vi.timeRecordingEnded = evDate;
						System.out.println("RecordingEnded = "+evDate);
					}
				}
			}
			br.close();
			
			if (numEvents == 0) {
				vi.warnNoEvents = true;
				System.err.println("no events found in chat log! putting warnNoEvents");
			}
			if (vi.timeRecordingStarted == null) {
				System.err.println("timeRecordingStarted is null. calculating it from uploadDate - durationInSeconds");
				vi.warnTimeApprox = true;
				//calculatex approximate start time from uploadDate - durationInSeconds
				int mixerProcessingLeewaySeconds = 5 * 60;
				long timeStartApprox = vi.uploadDatems - vi.vod.getInt("durationInSeconds")*1000 - mixerProcessingLeewaySeconds*1000;
				vi.timeRecordingStarted = timeMsToISODateString(timeStartApprox);
			}
			if (vi.timeRecordingEnded == null) {
				System.err.println("timeRecordingEnded is null. calculating it from timeRecordingStarted + durationInSeconds");
				long timeStart = dateStringToTimeMs(vi.timeRecordingStarted);
				timeStart += vi.vod.getInt("durationInSeconds")*1000;
				vi.timeRecordingEnded = timeMsToISODateString(timeStart);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	static void readExtraInfo(VodInfo vi) {
		//readPlaylist(vi);
		calcLengths(vi);
		readRecordingStartEndTimes(vi);
		
		vi.extra = true;
	}
	
	static void storeExtraInfo(VodInfo vi) {
		System.out.println("saving info_proc for "+vi.contentId);
		//write info file:
		JSONObject procInfo = new JSONObject();
		String catFull = typeMapFull.get(vi.vod.getInt("typeId")).trim();
		procInfo.put("category", catFull);
		procInfo.put("numParts", vi.numParts);
		JSONArray partStartTimes = new JSONArray();
		for (double start: vi.partStartTimes) {
			partStartTimes.put(start);
		}
		procInfo.put("partStartTimes", partStartTimes);
		procInfo.put("RecordingStarted", vi.timeRecordingStarted);
		procInfo.put("RecordingEnded", vi.timeRecordingEnded);
		
		//put warnings (for web):
		if (vi.warnNoEvents) procInfo.put("warnNoEvents", true);
		if (vi.warnTimeApprox) procInfo.put("warnTimeApprox", true);
		
		try {
			File procInfoFile = new File(BASE_DIR + "info_proc\\"+vi.contentId+".txt");
			FileWriter w = new FileWriter(procInfoFile);
			w.write(procInfo.toString());
			w.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	static void process(VodInfo vi) {
		
		if (DRY_RUN) return;
		
		System.out.println("PROCESSING STREAM "+vi.streamIndex+": "+vi.contentId);

		File tempDir = new File(TEMP_DIR + vi.contentId + "\\");
		tempDir.mkdirs();

		try {
			//joinTs(vi);
			//convertJoinTsToMp4(vi);
			
			cutUpMp4(vi);
			
		} catch (Exception e) {
			e.printStackTrace();
			//System.err.println("something went wrong when joining/converting the VOD! aborting processing...");
			System.err.println("something went wrong when trimming the VOD parts! aborting processing...");
			return;
		}

		// upload:
		uploadParts(vi);

		// delete temp dir
		clearTmp(tempDir);

		// mark as processed:
		try {
			new File(BASE_DIR + "processed\\" + vi.contentId + ".txt").createNewFile();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private static void cutUpMp4(VodInfo vi) throws IOException {
		if (vi.numParts > 1) {
			for (int p = 0; p<vi.numParts; p++) {
				int start = vi.partStartTimes.get(p);
				int end = p+1 < vi.numParts ? vi.partStartTimes.get(p+1) : -1;
				int t = end-start;
				
				String i = new File(SRC+ "vid\\"+ vi.contentId+"\\source.mp4").getAbsolutePath();
				String out = new File(BASE_DIR + "upload\\" + p + "-" + vi.contentId + ".mp4").getAbsolutePath();
				i = Paths.get(i).normalize().toString();
				out = Paths.get(out).normalize().toString();
				
				System.out.println("trimming part " + p);
				
				List<String> command = new ArrayList<>();
				command.add("ffmpeg");
				if (start > 0) {
					command.add("-ss");
					command.add(String.valueOf(start));
				}
				command.add("-i");
				command.add(i);
				if (end != -1) {
					command.add("-t");
					command.add(String.valueOf(t));
				}
				command.add("-c");
				command.add("copy");
				command.add(out);
				command.add("-y");
				
				/*String ffmpeg = "ffmpeg "
						+ (start > 0 ? "-ss "+start+" " : "")
						+ "-i \"" +i+ "\" " 
						+ (end != -1 ? "-t "+t+" " : "")
						+ "-c copy "
						+ "\""+out+"\" "
						+ "-y"; */
				
				System.out.println("[FFMPEG] " + String.join(" ", command));
				
				runAndGetStdErr(command, true, null, null,
						BASE_DIR + "ffmpeg-logs\\trim-" + p + "-" + vi.contentId + ".log");
				
				vi.mp4Files.add(out);
			}
			vi.deleteMp4 = true;
		} else {
			vi.mp4Files.add(SRC +"vid/"+vi.contentId+"/source.mp4");
		}
		
		System.out.println("using mp4 sources:");
		for (String mp4 : vi.mp4Files) System.out.println(mp4);
	}

	static void rmdir(File d) throws Exception {
		File[] list = d.listFiles();
		for (File f : list) {
			if (f.isFile())
				f.delete();
			else if (f.isDirectory())
				rmdir(f);
		}
		d.delete();
	}
	
	static YtTest.VideoMetadata genVidMetadata(VodInfo vi, int p) {
		System.out.println("[genVidMetadata] contentId="+vi.contentId+" part="+p);
		if (vi.timeRecordingStarted == null) {
			System.err.println("timeRecordingStarted is null");
			return null;
		}
		String streamDate = getDateStringESTUTC(vi.timeRecordingStarted).substring(0, 10);
		String cat = typeMap.get(vi.vod.getInt("typeId")).trim();
		YtTest.VideoMetadata meta = new YtTest.VideoMetadata();
		meta.title = "[" + streamDate + " | " + cat + "]" + ENSPACE
				+ vi.vod.getString("title") + ENSPACE + EMDASH + ENSPACE + "Mixer#"
				+ String.format("%03d", vi.streamIndex+1)
				+ (vi.numParts > 1 ? (" Part " + (p + 1) + "/" + vi.numParts) : "");

		meta.desc = genDescription(vi, p); 
		meta.tags = getTags(cat);
		
		meta.privacy = DEFAULT_PRIVACY;
		
		return meta;
	}

	static void uploadParts(VodInfo vi) {
		System.out.println("[uploadParts] video=["+vi.streamIndex+" "+vi.contentId+"] parts="+vi.numParts);
		
		for (int p = 0; p < vi.numParts; p++) {
			//File video = new File(BASE_DIR + "upload\\" + p + "-" + vi.contentId + ".mp4");
			File video = new File(vi.mp4Files.get(p));

			YtTest.VideoMetadata meta = genVidMetadata(vi, p);
			if (meta == null) {
				System.err.println("failed to generate video metadata");
				continue;
			}
			
			System.out.println("TITLE: "+meta.title);
			System.out.println("DESC: "+meta.desc);
			System.out.println("FILE: "+video.getAbsolutePath());

			String ytid = YtTest.uploadToYt(meta, video.getAbsolutePath());
			//String ytid = "DUMMY-TEST-ID;P=" + p + ";N=" + vi.streamIndex;

			System.out.println("returned ytid = "+ytid);
			try {
				File ytidFile = new File(BASE_DIR + "ytids\\" + p + "-" + vi.contentId + ".txt");
				FileWriter w = new FileWriter(ytidFile);
				w.write(ytid);
				w.close();
			} catch (Exception e) {
				e.printStackTrace();
			}

			if (vi.deleteMp4) {
				System.out.println("deleting mp4 file "+video.getAbsolutePath()+"...");
				video.delete();
			} else {
				System.out.println("(keep mp4 file "+video.getAbsolutePath()+")");
			}
		}
	}

	private static String file_get_contents(String path) {
		File f = new File(path);
		if (!f.exists())
			return null;
		try {
			return new String(Files.readAllBytes(Paths.get(f.getAbsolutePath())));
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

	private static String getYtId(int part, String contentId) {
		String ytid = file_get_contents(BASE_DIR + "ytids\\" + part + "-" + contentId + ".txt");
		System.out.println("[getYtId] contentId="+contentId+"  ==>  "+ytid);
		return ytid;
	}

	private static String genDescription(VodInfo vi, int curPartIdx) {

		String desc = "";
		boolean multipart = vi.numParts > 1;
		
		desc += "Watch with chatlog: https://deadmau5archive.github.io/deadmau5?vod="+vi.vod.getString("shareableId")+"\n";

		if (multipart) {
			boolean bullet = false;
			for (int p = 0; p < vi.numParts; p++) {
				if (p == curPartIdx)
					continue;
				if (bullet)
					desc += " \u2022 ";
				String partLink = getYtId(p, vi.contentId);
				if (partLink == null)
					partLink = "\u2013"; // endash
				else
					partLink = "https://youtu.be/" + partLink;
				desc += "Part " + (p + 1) + ": " + partLink;
				bullet = true;
			}
			desc += "\n";
		}

		String prevVideoId = null;
		String nextVideoId = null;
		
		if (vi.streamIndex > 0) {
			prevVideoId = getYtId(0, vods.get(vi.streamIndex-1).contentId);
		}
		if (vi.streamIndex < vods.size()-1) {
			nextVideoId = getYtId(0, vods.get(vi.streamIndex+1).contentId);
		}
		
		System.out.println("[genDescription] idx="+vi.streamIndex+" prevVideoId="+prevVideoId+" nextVideoId="+prevVideoId);

		if (prevVideoId == null)
			desc += "Next: https://youtu.be/" + nextVideoId + "\n";
		else if (nextVideoId == null)
			desc += "Previous: https://youtu.be/" + prevVideoId + "\n";
		else
			desc += "Previous: https://youtu.be/" + prevVideoId + " \u2022 Next: https://youtu.be/" + nextVideoId
					+ "\n";

		if (vi.timeRecordingStarted == null) {
			System.err.println("timeRecordingStarted is null");
			return null;
		}
		
		String recordStartDateString = getDateStringESTUTC(vi.timeRecordingStarted);
		String recordEndDateString = getDateStringESTUTC(vi.timeRecordingEnded);
		String vodUploadDateString = getDateStringESTUTC(vi.vod.getString("uploadDate"));
		String vodExpireDateString = getDateStringESTUTC(vi.vod.getString("expirationDate"));

		String fullHlsUrl = "(?)";
		String fullMp4Url = "(?)";
		for (Object o : vi.vod.getJSONArray("contentLocators")) {
			JSONObject cl = (JSONObject) o;
			if (cl.getString("locatorType").equals("SmoothStreaming")) {
				fullHlsUrl = cl.getString("uri");
				fullHlsUrl = fullHlsUrl.substring(0, fullHlsUrl.indexOf('?'));
			}
			if (cl.getString("locatorType").equals("Download")) {
				fullMp4Url = cl.getString("uri");
				fullMp4Url = fullMp4Url.substring(0, fullMp4Url.indexOf('?'));
			}
		}
		
		String approxWarn = "";
		if (vi.warnTimeApprox) {
			approxWarn = "(approx.\u00B9) ";
		}

		String catFull = typeMapFull.get(vi.vod.getInt("typeId")).trim();
		desc += "\n" + "Title: " + vi.vod.getString("title") + "\n"
				+ "Category: " + catFull + "\n" 
				+ "Streamed at: " + approxWarn+ recordStartDateString + "\n" 
				+ "Stream ended at: " + approxWarn + recordEndDateString + "\n" 
				+ "Original length: " + secToTime(vi.vod.getInt("durationInSeconds")) + "\n" 
				+ "Original resolution: " + vi.vod.getInt("width")+"x"+vi.vod.getInt("height") + "\n" 
				+ "Original FPS: " + vi.vod.getInt("fps") + "\n" 
				+ "VOD published at: " + vodUploadDateString + "\n" 
				+ "VOD expired at: " + vodExpireDateString + "\n" 
				+ "Mixer video URL: https://mixer.com/" + CHANNEL+ "?vod=" + vi.vod.getString("shareableId") + "\n" 
				+ "HLS playlist URL: " + fullHlsUrl + "\n"
				+ "MP4 download URL: " + fullMp4Url + "\n"
				+ "Source: Mixer VOD service\n";

		if (vi.warnTimeApprox) {
			desc += "\n\u00B9 Mixer only provides start/end timestamps for streams in the stream's event log, not in the VOD metadata. "
					+ "For this stream, these timestamps were missing from the event log (for unknown reasons). "
					+ "So they have been calculated approximately by subtracting the stream's duration plus 5 minutes leeway (for processing time mixer usually takes) from the VOD publish timestamp. "
					+ "This should be more or less accurate since VODs are usually published automatically a few minutes after the end of a stream.\n";
		}
		
		desc += "\nThis is a recorded livestream from https://mixer.com/deadmau5\n"
				+ "All rights belong to Joel Zimmerman (deadmau5).\n"
				+ "This video is not monetized. If you see ads, they're placed by copyright holders that claimed the video or by YouTube.\n";

		if (multipart) {
			desc += "This video was split into multiple parts because YouTube does not allow videos longer than 12 hours as of the time of upload.\n";
		}

		return desc;
	}
	
	private static String getDateStringESTUTC(String dateInput) {
		if (dateInput.contains(".")) {
			dateInput = dateInput.substring(0, dateInput.indexOf('.'))+"Z";
		}
		
		String dateISO = dateInput;
		String datePretty = dateISO.substring(0, dateInput.indexOf('Z')).replace('T', ' ');
		
		SimpleDateFormat inFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
		inFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
		Date date = null;
		try {
			date = inFormat.parse(dateISO);
		} catch (ParseException e) {
			e.printStackTrace();
		}
		SimpleDateFormat outFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		outFormat.setTimeZone(TimeZone.getTimeZone("EST"));
		
		return outFormat.format(date) + " EST (" + datePretty + " UTC)";
	}
	
	private static long dateStringToTimeMs(String dateInput) {
		if (dateInput.contains(".")) {
			dateInput = dateInput.substring(0, dateInput.indexOf('.'))+"Z";
		}
		SimpleDateFormat inFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
		inFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
		Date date = null;
		try {
			date = inFormat.parse(dateInput);
		} catch (ParseException e) {
			e.printStackTrace();
		}
		return date.getTime();
	}
	
	private static String timeMsToISODateString(long ms) {
		Date date = new Date(ms);
		SimpleDateFormat outFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
		outFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
		return outFormat.format(date);
	}

	static String secToTime(double secs) {
		int s = (int) Math.round(secs);
		int h = s / 3600;
		s -= h * 3600;
		int m = s / 60;
		s -= m * 60;
		String timestamp = String.format("%02d", s);
		if (h == 0)
			timestamp = m + ":" + timestamp;
		else
			timestamp = h + ":" + String.format("%02d", m) + ":" + timestamp;
		return timestamp;
	}

	static void clearTmp(File tmp) {
		System.out.print("clearing temp dir... ");
		try {
			rmdir(tmp);
			System.out.println(" done!");
		} catch (Exception e) {
			System.err.println("error clearing temp dir: " + e.toString());
		}
	}

	static class VodInfo {
		boolean extra = false; //has extra info been read

		boolean processed = false;

		JSONObject vod;
		long uploadDatems;
		int streamIndex;
		
		String timeRecordingStarted = null;
		String timeRecordingEnded  = null;

		//List<Double> segmentLengths;
		double totalLength;

		String contentId;
		int numParts = 1;
		//List<String> tsFiles;
		
		List<String> mp4Files = new ArrayList<>();
		boolean deleteMp4 = false;

		//List<Double> partStartTimes = new ArrayList<>();
		//List<Integer> partStartIndex = new ArrayList<>();
		List<Integer> partStartTimes = new ArrayList<>();
		
		//warnings
		boolean warnTimeApprox = false;
		boolean warnNoEvents = false;
	}

	/*static void joinTs(VodInfo info) throws Exception {
		int numfiles = info.tsFiles.size();
		System.out.println(numfiles + " .ts files on disk");

		byte[] buf = new byte[4096];
		System.out.println("joining files... ");

		int i = 0;
		for (int p = 0; p < info.numParts; p++) {
			System.out.println("joining part " + p);
			int limit = 0;
			if (p == info.numParts - 1) {
				limit = numfiles;
			} else {
				limit = info.partStartIndex.get(p + 1); // all files up to next part
			}
			//limit = 10; // for testing
			FileOutputStream out = new FileOutputStream(TEMP_DIR + info.contentId + "\\part" + p + ".ts");
			for (; i < limit; i++) {
				InputStream in = null;
				try {
					String tsfile = info.tsFiles.get(i);
					double progr = i / (double) numfiles;
					System.out.print("\rjoining " + tsfile + "\tfile " + (i + 1) + " of " + numfiles + "\tprogress: "
							+ String.format("%.03f", progr * 100) + "%");
					in = new FileInputStream(new File(SRC + info.contentId + "\\" + tsfile));
				} catch (Exception e) {
					System.err.println(e.toString());
					Thread.sleep(1000);
					i--; //try again
					continue;
				}
				int b = 0;
				while ((b = in.read(buf)) >= 0) {
					out.write(buf, 0, b);
					out.flush();
				}
				in.close();
			}
			out.close();
			System.out.println();
		}

		System.out.println("join complete!");
	}*/

	/*static void convertJoinTsToMp4(VodInfo info) throws Exception {
		// source: twitch leecher
		/*
		 * Arguments = "-y" + (cropInfo.CropStart ? " -ss " +
		 * cropInfo.Start.ToString(CultureInfo.InvariantCulture) : null) +
		 * " -i \"" + sourceFile + "\" -analyzeduration " + int.MaxValue +
		 * " -probesize " + int.MaxValue +
		 * " -c:v copy -c:a copy -bsf:a aac_adtstoasc" + (cropInfo.CropEnd ?
		 * " -t " + cropInfo.Length.ToString(CultureInfo.InvariantCulture) :
		 * null) + " \"" + outputFile + "\"",
		 *
		System.out.println("converting files...");
		for (int p = 0; p < info.numParts; p++) {
			System.out.println("converting part " + p);
			String ffmpeg = "ffmpeg -i \"" + TEMP_DIR + info.contentId + "\\part" + p + ".ts\" " + "-analyzeduration "
					+ Integer.MAX_VALUE + " -probesize " + Integer.MAX_VALUE + " "
					+ "-c:v copy -c:a copy -bsf:a aac_adtstoasc \"" + BASE_DIR + "upload/" + p + "-" + info.contentId
					+ ".mp4\" -y";
			runAndGetStdErr(ffmpeg, true, null, null,
					BASE_DIR + "ffmpeg-logs/join-" + p + "-" + info.contentId + ".log");
		}
		System.out.println("converting complete!");
	}*/

	static class FFMpegReport {
		boolean ffmpegError = false;
		double length = 0;

		void onOutput(String out) {
		}
	}

	/*private static void runAndGetStdErr(String ffmpeg, boolean print, FFMpegReport report, List<String> errors,
			String logFile) throws IOException {
		runAndGetOutput(ffmpeg, print, report, errors, logFile, true);
	}

	private static void runAndGetOutput(String ffmpeg, boolean print, FFMpegReport report, List<String> errors,
			String logFile, boolean stderr) throws IOException {
		String line;
		Process p = Runtime.getRuntime().exec(ffmpeg);
		BufferedReader input = new BufferedReader(
				new InputStreamReader(stderr ? p.getErrorStream() : p.getInputStream()));
		PrintWriter w = null;
		if (logFile != null)
			w = new PrintWriter(new File(logFile));
		while ((line = input.readLine()) != null) {
			line = line.trim();
			if (print) {
				if (line.startsWith("frame=")) {
					System.out.print("\r" + line);
				} else {
					System.out.print("\n" + line);
				}
			}
			if (report != null)
				report.onOutput(line);
			if (errors != null)
				errors.add(line);
			if (w != null)
				w.write(line + "\n");
		}
		input.close();
		if (w != null)
			w.close();
		if (print)
			System.out.println();
	}*/
	
	private static void runAndGetStdErr(List<String> command, boolean print, FFMpegReport report, List<String> errors,
			String logFile) throws IOException {
		runAndGetOutput(command, print, report, errors, logFile, true);
	}

	private static void runAndGetOutput(List<String> command, boolean print, FFMpegReport report, List<String> errors,
			String logFile, boolean stderr) throws IOException {
		String line;
		ProcessBuilder pb = new ProcessBuilder(command);
		Process p = pb.start(); //Runtime.getRuntime().exec(ffmpeg);
		BufferedReader input = new BufferedReader(
				new InputStreamReader(stderr ? p.getErrorStream() : p.getInputStream()));
		PrintWriter w = null;
		if (logFile != null)
			w = new PrintWriter(new File(logFile));
		while ((line = input.readLine()) != null) {
			line = line.trim();
			if (print) {
				if (line.startsWith("frame=")) {
					System.out.print("\r" + line);
				} else {
					System.out.print("\n" + line);
				}
			}
			if (report != null)
				report.onOutput(line);
			if (errors != null)
				errors.add(line);
			if (w != null)
				w.write(line + "\n");
		}
		input.close();
		if (w != null)
			w.close();
		if (print)
			System.out.println();
	}

	
	private static void disableBiggestBullshitEver() {
		TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {
			public java.security.cert.X509Certificate[] getAcceptedIssuers() {
				return new X509Certificate[0];
			}

			public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) {
			}

			public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) {
			}
		} };

		// Install the all-trusting trust manager
		try {
			SSLContext sc = SSLContext.getInstance("SSL");
			sc.init(null, trustAllCerts, new java.security.SecureRandom());
			HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
		} catch (GeneralSecurityException e) {
		}
	}
}
