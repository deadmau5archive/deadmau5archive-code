import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.SocketException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import java.util.Scanner;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.VideoListResponse;

public class TwitchThing2 {

	public static final String CONFIG_FILE = "config.txt";
	public static String DIR = "M:/deadmau5/rescue/";
	public static String TEMP = null;

	// general
	public static String HLS_PATH = DIR + "playlist_rescue_gen/"; //DIR + "playlists_real/";
	public static String INDEX_FILE = DIR + "hls_array_rescue_gen.txt"; // DIR + "hls_array.txt";
	public static int NUM_DL_THREADS = 8;
	public static final int HLS_ARRAY_START = 0;
	public static final int HLS_ARRAY_END_EXCL = 1000000;
	public static Direction PROCESS_DIRECTION = Direction.LATEST_TO_FIRST;
	public static MergeTechnique MERGING_TECHNIQUE = MergeTechnique.CONCAT_LIST_TO_MP4;
	private static final String VOD_SERVER = "https://d2nvs31859zcd8.cloudfront.net/"; // "https://vod-secure.twitch.tv/";

	// rescue
	public static final String VODPATH = DIR + "pool/";
	public static final boolean DO_VERIFY = false;
	public static boolean USE_BROKEN_FILE_INFO = true;
	public static boolean FORBID_MUTE_DOWNLOADS = true; // set to false for
															// normal!!!

	// misc
	public static boolean GUI = true;
	public static boolean WRITE_MUTES_FILE = false;
	public static boolean FORBID_MISSING_TS = true;
	public static boolean SKIP_GAME_STREAMS = false; // pubg
	public static boolean PRESERVE_TEMP_DIR = false; // if true, will change the
														// temp dir after an
														// error occurred.
	public static final boolean DONT_PRESERVE_TEMP_DIR_ON_TS_NOT_FOUND = true; // delete
																				// temp
																				// folder
																				// if
																				// downloading
																				// fails
																				// because
																				// of
																				// ts
																				// 404
	public static boolean ENABLE_PARTFIX_MODE = false; // don't touch this
	public static boolean RETRY_ON_FFMPEG_FAIL = true;
	public static int MAX_VIDS_IN_UPLOAD_DIR = 1200;

	// upload mute
	static boolean DO_COPYRIGHT_MUTING = true;
	static int COPYRIGHT_MUTE_EXPAND = 5;

	static enum Direction {
		LATEST_TO_FIRST, FIRST_TO_LATEST
	}

	static enum MergeTechnique {
		CONCAT_LIST_TO_MP4, JOIN_TS_AND_CONVERT
	}
	
	public static void main(String[] args) {
		loopDownloadAll();
	}

	public static void main2(String[] args) {
		try {
			if (new File(CONFIG_FILE).exists() && new File(CONFIG_FILE).isFile()) {
				System.out.println("reading " + CONFIG_FILE + "...");
				Properties prop = new Properties();
				prop.load(new FileInputStream(new File(CONFIG_FILE)));

				DIR = prop.getProperty("dir", "");
				INDEX_FILE = DIR + prop.getProperty("index", "hls_array.txt");
				HLS_PATH = DIR + prop.getProperty("playlists", "playlists_real/");
				NUM_DL_THREADS = Integer.parseInt(prop.getProperty("dlthreads", "8"));
				String order = prop.getProperty("order", "ltf");
				if (order.equals("ftl")) {
					PROCESS_DIRECTION = Direction.FIRST_TO_LATEST;
				} else if (order.equals("ltf")) {
					PROCESS_DIRECTION = Direction.LATEST_TO_FIRST;
				}
				GUI = Integer.parseInt(prop.getProperty("gui", "1")) != 0;
			} else {
				System.out.println("no " + CONFIG_FILE + " found, using default settings...");
			}

			new File(DIR + "locks/").mkdirs();
			new File(DIR + "logerror/").mkdirs();
			new File(DIR + "upload/").mkdirs();
			new File(DIR + "ffmpeg-logs/").mkdirs();
			new File(DIR + "temp/").mkdirs();

			if (!new File(HLS_PATH).exists() || !new File(HLS_PATH).isDirectory()) {
				throw new Exception("missing playlist directory");
			}
			if (!new File(INDEX_FILE).exists() || !new File(INDEX_FILE).isFile()) {
				throw new Exception("missing playlist directory");
			}

			String credIdx = new String(Files.readAllBytes(Paths.get(DIR + "cred/index.txt")));
			Deadmau5.credIndex = Integer.parseInt(credIdx.trim());
		} catch (Exception e) {
			System.err.println("INITIALISATION ERROR!");
			e.printStackTrace();
			return;
		}
		System.out.println("initialized!");

		// gen refresh token
		/*
		 * try { List<String> scopes = Lists.newArrayList(
		 * "https://www.googleapis.com/auth/youtube",
		 * "https://www.googleapis.com/auth/youtube.upload",
		 * "https://www.googleapis.com/auth/youtube.force-ssl",
		 * "https://www.googleapis.com/auth/youtube.readonly"); Credential
		 * credential = YtAuth.authorize(scopes, "whatever", "gamma"); //delete
		 * "cred/whatever" first! System.out.println("ref "
		 * +credential.getRefreshToken()); } catch (IOException e) {
		 * e.printStackTrace(); }
		 */

		// download part i-j
		/*
		 * VodInfo info = new VodInfo(); info.fileLocations = new String[1];
		 * newTempDir(); try { for (int i = 900; i<=935; i++)
		 * downloadTs("4ae5fd4fceaa76240d8d_deadmau5_32103344096_1075084439", 0,
		 * i+".ts", info); } catch (Exception e) { e.printStackTrace(); }
		 */
		// for (int i = 900; i<=935; i++)
		// System.out.println("ffmpeg -i D:/deadmau5/test/851/vid/"+i+".ts -i
		// //RASPI/deadmau5/361604301-audio/"+i+".ts -c copy -map 1:a -map 0:v
		// D:/deadmau5/test/851/out/"+i+".ts -y");

		// write playlist info file
		// numparts partstarttimes numsegments totalLength discoTimes
		// (segmentlengths tsFiles)
		/*
		 * JSONArray vids = null; try { vids = new JSONArray(new JSONTokener(new
		 * FileReader(new File(DIR+"hls_array.txt")))); int len = vids.length();
		 * JSONObject playlistInfo = new JSONObject(); for (int i = 0; i < len;
		 * i++) { JSONObject vid = vids.getJSONObject(i); String hls =
		 * vid.getString("hlsurl"); System.out.println(hls); VodInfo info =
		 * readPlaylist(hls);
		 * 
		 * JSONObject vidInfo = new JSONObject(); vidInfo.put("partStartTimes",
		 * new JSONArray(info.partStartTimes)); vidInfo.put("numSegments",
		 * info.tsFiles.size()); vidInfo.put("totalLength", info.totalLength);
		 * vidInfo.put("numParts", info.numParts); vidInfo.put("discoTimes", new
		 * JSONArray(info.discoTimesDouble));
		 * 
		 * playlistInfo.put(hls, vidInfo); } File muteFile = new
		 * File(DIR+"playlistinfo.txt"); FileWriter w = new
		 * FileWriter(muteFile); playlistInfo.write(w); w.close(); } catch
		 * (Exception e) { e.printStackTrace(); }
		 */

		// loop();
		// uploadLoop();
		getUploadedVideoTimes();

	}

	static void getUploadedVideoTimes() {
		try {
			String[] locations = { "log", "logpart", "copystrike/dropin" };
			for (String dir : locations) {
				System.out.println("dir: " + dir);
				File[] logs = new File(DIR + dir + "/").listFiles();
				for (File h : logs) {
					System.out.println(h.getName());
					String ytId = new String(Files.readAllBytes(Paths.get(h.getAbsolutePath()))).trim();
					if (!new File(DIR + "viddurations/" + ytId + ".txt").exists()) {
						int time = getVidDurationSec(ytId);
						if (time > 0) {
							System.out.println("\t" + secToTime(time) + " (" + time + "s)");
							File timeinfo = new File(DIR + "viddurations/" + ytId + ".txt");
							FileWriter w = new FileWriter(timeinfo);
							w.write(String.valueOf(time));
							w.close();
						} else if (time == 0) {
							System.out.println("still processing...");
						} else if (time == -1) {
							File timeinfo = new File(DIR + "viddurations/" + ytId + ".txt");
							FileWriter w = new FileWriter(timeinfo);
							w.write("n/a");
							w.close();
						}
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	static int getVidDurationSec(String vid) {
		String json = listVid(vid);
		JSONObject videoInfo = new JSONObject(new JSONTokener(json));
		if (videoInfo.getJSONArray("items").length() > 0) {
			String duration = videoInfo.getJSONArray("items").getJSONObject(0).getJSONObject("contentDetails")
					.getString("duration");
			return (int) Duration.parse(duration).getSeconds();
		} else {
			System.out.println("ERROR: VIDEO DOES NOT EXIST (processing abandoned?)");
			return -1;
		}
	}

	static String listVid(String vid) {
		// https://developers.google.com/youtube/v3/docs/videos/list
		try {
			YouTube youtube = Deadmau5.getYouTubeService(Deadmau5.credIndexSmall);

			HashMap<String, String> parameters = new HashMap<>();
			// parameters.put("part", "snippet,contentDetails,statistics");
			// contentDetails,fileDetails,id,liveStreamingDetails,localizations,player,processingDetails,recordingDetails,snippet,statistics,status,suggestions,topicDetails
			parameters.put("part", "contentDetails");
			parameters.put("id", vid);

			YouTube.Videos.List videosListByIdRequest = youtube.videos().list(parameters.get("part").toString());
			if (parameters.containsKey("id") && parameters.get("id") != "") {
				videosListByIdRequest.setId(parameters.get("id").toString());
			}

			VideoListResponse response = videosListByIdRequest.execute();

			// cycle through projects so quota gets spread equally
			Deadmau5.credIndexSmall = (Deadmau5.credIndexSmall + 1) % Deadmau5.PROJ_NAMES.length;
			return response.toString();
		} catch (GoogleJsonResponseException e) {
			if (e.getDetails().getMessage().contains("exceeded") && e.getDetails().getMessage().contains("quota")) {
				Deadmau5.credIndexSmall = (Deadmau5.credIndexSmall + 1) % Deadmau5.PROJ_NAMES.length;
				return listVid(vid);
			} else {
				System.err.println("GoogleJsonResponseException code: " + e.getDetails().getCode() + " : "
						+ e.getDetails().getMessage());
				e.printStackTrace();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	static boolean shouldStop = false;
	static boolean shouldPauseDownload = false;

	static class VodInfo {
		List<String> tsFiles;
		String[] fileLocations;
		List<Double> segmentLengths;
		double totalLength;
		boolean muted = false;
		HashMap<String, String> mutes = new HashMap<>();
		// List<String> missing = new ArrayList<>();
		// List<String> mutedSegments = new ArrayList<>();
		// List<String> discoTimes;
		List<Double> mutedSegmentsStartTimes = new ArrayList<>();
		List<Double> mutedSegmentsEndTimes = new ArrayList<>();
		List<Double> discoTimesDouble;
		int numParts = 1;
		List<Double> partStartTimes = new ArrayList<>();
		List<Integer> partStartIndex = new ArrayList<>();
		boolean ignorePart[];
		public boolean partFixMode;
		// double[] partCompensate;
		String videoid;
	}

	static void uploadLoop() {
		File uploadDir = new File(DIR + "upload/");

		JSONObject copystrike = null;
		try {
			copystrike = new JSONObject(
					new JSONTokener(new FileReader(new File(DIR + "copystrike/copyright_info_hls.txt"))));
		} catch (Exception e1) {
			e1.printStackTrace();
		}

		while (true) {
			File[] files = uploadDir.listFiles();
			System.out.println(files.length + " files in upload dir...");

			for (File f : files) {
				if (f.getName().endsWith(".txt") && !f.getName().startsWith("uploaded")) {
					String partDashHls = f.getName().substring(0, f.getName().length() - 4);
					System.out.println("UPLOADING " + partDashHls + ".mp4");
					try {
						JSONObject videoInfo = new JSONObject(new JSONTokener(new FileReader(f)));
						Deadmau5.VideoMetadata metadata = new Deadmau5.VideoMetadata();
						metadata.title = videoInfo.getString("title");
						metadata.desc = videoInfo.getString("description");
						JSONArray tags = videoInfo.getJSONArray("tags");
						metadata.tags = new ArrayList<>();
						for (int i = 0; i < tags.length(); i++)
							metadata.tags.add(tags.getString(i));

						int flags = silenceCopyright(partDashHls, copystrike.getJSONObject((partDashHls)));
						// 0b0001 = use copyright info (if false then normal
						// upload & delete video will proceed)
						// 0b0010 = skip this video (because already done)
						// 0b0100 = set video file to mute critical
						// 0b1000 = set video file to mute all
						boolean deleteMetadataEntry = true;
						String videoFile = DIR + "upload/" + partDashHls + ".mp4";
						String videoIdLogFile = DIR + "logpart/" + partDashHls + ".txt";
						if ((flags & 1) == 1 || DO_COPYRIGHT_MUTING) {
							if ((flags & 2) == 2)
								continue;
							deleteMetadataEntry = false;
							videoFile = null;
							if ((flags & 4) == 4) {
								videoFile = DIR + "upload/" + partDashHls + ".MUTE_CRITICAL.mp4";
								videoIdLogFile = DIR + "copystrike/dropin/" + partDashHls + ".MUTE_CRITICAL.txt";
								metadata.title = "(M:CRT) "
										+ metadata.title.substring(0, Math.min(metadata.title.length(), 92));
							} else if ((flags & 8) == 8) {
								videoFile = DIR + "upload/" + partDashHls + ".MUTE_ALL.mp4";
								videoIdLogFile = DIR + "copystrike/dropin/" + partDashHls + ".MUTE_ALL.txt";
								metadata.title = "(M:ALL) "
										+ metadata.title.substring(0, Math.min(metadata.title.length(), 92));
							}
						}

						String vidStatus = Deadmau5.uploadToYt(metadata, videoFile);
						if (vidStatus == null || vidStatus.length() == 0) {
							throw new Exception("Upload failed " + partDashHls);
						} else {
							File log = new File(videoIdLogFile);
							FileWriter w = new FileWriter(log);
							w.write(vidStatus);
							w.close();
							// f.renameTo(new
							// File(f.getParentFile().getAbsolutePath()+"/uploaded-"+f.getName()));
							if (deleteMetadataEntry)
								f.delete(); // delete metadata
							new File(videoFile).delete(); // delete video
						}
					} catch (Exception e) {
						e.printStackTrace();
					}
					break; // uploaded one file, recheck dir in case something
							// changed
				}
			}

			try {
				Thread.sleep(10000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	private static int silenceCopyright(String partDashHls, JSONObject copystrikeInfo) {
		if (!DO_COPYRIGHT_MUTING)
			return 0;

		System.out.println("(C): " + partDashHls);
		String srcFile = DIR + "upload/" + partDashHls + ".mp4";
		String copystrikeSituation = copystrikeInfo.getString("blocked_in");
		String criticalLogFile = DIR + "copystrike/dropin/" + partDashHls + ".MUTE_CRITICAL.txt";
		String everythingLogFile = DIR + "copystrike/dropin/" + partDashHls + ".MUTE_ALL.txt";
		JSONArray claims = copystrikeInfo.getJSONArray("claims");
		if (copystrikeSituation.equals("all")) {
			System.out.println(partDashHls + " CRITICAL CLAIMS:");
			if (!new File(criticalLogFile).exists()) {
				System.out.println("creating critical claims mute video...");
				// mute critical to
				// DIR+"upload/"+partDashHls+".MUTE_CRITICAL.mp4"
				ffmpegMute(srcFile, DIR + "upload/" + partDashHls + ".MUTE_CRITICAL.mp4", claims, 1);
				return 1 | 4; // do critical claims mute
			} else {
				System.out.println(partDashHls + " critical claim mute video exists already!");
			}
		} else {
			System.out.println(partDashHls + " does not have critical claims that block the video everywhere!");
		}

		System.out.println(partDashHls + " ALL CLAIMS:");
		if (!new File(everythingLogFile).exists()) {
			System.out.println("creating all claims mute video...");
			// mute everything to DIR+"upload/"+partDashHls+".MUTE_ALL.mp4";
			boolean needMuteAll = ffmpegMute(srcFile, DIR + "upload/" + partDashHls + ".MUTE_ALL.mp4", claims, 2);
			if (needMuteAll)
				return 1 | 8; // do all claims mute
		} else {
			System.out.println(partDashHls + " all claim mute video exists already!");
		}

		// all vids we want exist already:
		return 1 | 2;
	}

	private static boolean ffmpegMute(String srcFile, String destFile, JSONArray claims, int level) {
		// level 1 = mute critical only
		// level 2 = mute every claim
		String audioFilter = "";
		int muteSome = 0;
		for (int i = 0; i < claims.length(); i++) {
			JSONObject claim = claims.getJSONObject(i);
			String policy = claim.getString("blocked_in");
			if (policy.equals("some")) {
				if (level < 2)
					continue; // only mute critical
				muteSome++;
			}
			// if policy == all then mute no matter the level
			int startTime = claim.getInt("start") - COPYRIGHT_MUTE_EXPAND;
			int endTime = claim.getInt("end") + COPYRIGHT_MUTE_EXPAND;
			if (startTime < 0)
				startTime = 0;
			if (!audioFilter.isEmpty())
				audioFilter += ", ";
			audioFilter += "volume=enable='between(t," + startTime + "," + endTime + ")':volume=0";
		}
		if (level == 2 && muteSome == 0) {
			// no additional mutes in contrast to level 1. we don't need a
			// mute_all version!
			System.out.println(
					"level 2 claims identical to level 1 claims (no additional claims muted in some countries only)");
			return false;
		}

		String ffmpeg = "ffmpeg -i \"" + srcFile + "\" -af \"" + audioFilter + "\" -c:v copy \"" + destFile + "\" -y";
		System.out.println("MUTE_COMMAND: " + ffmpeg);

		try {
			runAndGetStdErr(ffmpeg, true, null, null, null);
		} catch (IOException e) {
			e.printStackTrace();
		}

		System.out.println("muting complete!");
		return true;
	}

	static void newTempDir() {
		String time = String.valueOf(System.currentTimeMillis());
		TEMP = DIR + "temp/" + time + "/";
		new File(TEMP).mkdirs();
		System.out.println("using temp dir " + TEMP);
		if (tempDirLbl != null)
			tempDirLbl.setText(time);
	}

	static JLabel tempDirLbl;
	
	static void loopDownloadAll() {

		JFrame f = null;
		if (GUI) {
			f = new JFrame();
			f.setLayout(new FlowLayout());
			JButton stop = new JButton("stop");
			stop.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					shouldStop = true;
					stop.setEnabled(false);
				}
			});
			JButton pause = new JButton("pause");
			pause.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					shouldPauseDownload = !shouldPauseDownload;
					pause.setText(shouldPauseDownload ? "resume" : "pause");
				}
			});
			tempDirLbl = new JLabel("loading...");
			f.add(pause);
			f.add(stop);
			f.add(tempDirLbl);
			f.setVisible(true);
			f.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
			f.setSize(300, 100);
			f.setLocationRelativeTo(null);
		}

		JSONArray vids = null;
		try {
			vids = new JSONArray(new JSONTokener(new FileReader(new File(INDEX_FILE))));
		} catch (Exception e) {
			e.printStackTrace();
		}

		int len = vids.length();
		for (int i = HLS_ARRAY_START; i < len && i < HLS_ARRAY_END_EXCL; i++) {

			// LATEST TO FIRST
			int vidIdx = i;
			int vidDisplayNum = len - i;
			if (PROCESS_DIRECTION == Direction.FIRST_TO_LATEST) {
				// FIRST TO LATEST
				vidIdx = len - 1 - i;
				vidDisplayNum = i + 1;
			}
			
			
			JSONObject vid = vids.getJSONObject(vidIdx);
			String hls = vid.getString("hlsurl");
			
			
			if (new File(DIR + "locks/" + hls + ".txt").exists()) {
				System.out.println("(i) skipping " + hls + " because it's locked");
				continue;
			}
			
			try {
				//new File(DIR + "locks/" + hls + ".txt").createNewFile();
				FileWriter w = new FileWriter(DIR + "locks/" + hls + ".txt");
				w.write(System.currentTimeMillis()+"");
				w.close();
				
				String videoid = vid.getString("id");
				VodInfo info = readPlaylist(videoid, hls);
				info.videoid = videoid;
				
				tempDirLbl.setText(videoid);
				
				new File(DIR + "videos/" + info.videoid+"/").mkdir();
				dlTsFilesMultithread(hls, info);
			} catch (Exception e) {
				System.err.println("SOMETHING WENT WRONG @ #" + vidDisplayNum + " " + hls);
				e.printStackTrace();
				File logerror = new File(DIR + "logerror/" + hls + ".txt");
				try {
					FileWriter w = new FileWriter(logerror);
					w.write("error at " + System.currentTimeMillis() + ": " + e.toString() + "\ntemp dir path:" + TEMP
							+ "\n\n");
					e.printStackTrace(new PrintWriter(w));
					w.close();
				} catch (IOException e1) {
					e1.printStackTrace();
				}
			}
			


			if (shouldStop) {
				System.out.println("########### HALT ###########");
				break;
			}
		}

		System.out.println("\nexecution complete!");
		if (f != null)
			f.dispose();
		System.exit(0);

	}

	static void loop() {

		JFrame f = null;
		if (GUI) {
			f = new JFrame();
			f.setLayout(new FlowLayout());
			JButton stop = new JButton("stop");
			stop.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					shouldStop = true;
					stop.setEnabled(false);
				}
			});
			tempDirLbl = new JLabel("loading...");
			f.add(stop);
			f.add(tempDirLbl);
			f.setVisible(true);
			f.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
			f.setSize(300, 100);
			f.setLocationRelativeTo(null);
		}

		newTempDir();

		JSONArray vids = null;
		try {
			vids = new JSONArray(new JSONTokener(new FileReader(new File(INDEX_FILE))));
		} catch (Exception e) {
			e.printStackTrace();
		}

		int len = vids.length();
		for (int i = HLS_ARRAY_START; i < len && i < HLS_ARRAY_END_EXCL; i++) {

			while (true) {
				int numVidsInUploadDir = 0;
				File[] filesInUploadDir = new File(DIR + "upload/").listFiles();
				for (File fu : filesInUploadDir) {
					if (fu.getName().endsWith(".txt")) {
						numVidsInUploadDir++;
					}
				}
				if (numVidsInUploadDir >= MAX_VIDS_IN_UPLOAD_DIR) {
					System.out.println(numVidsInUploadDir + " vids in upload dir! waiting... (limit="
							+ MAX_VIDS_IN_UPLOAD_DIR + ")");
					try {
						Thread.sleep(10000);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				} else
					break;
			}

			// LATEST TO FIRST
			int vidIdx = i;
			int vidDisplayNum = len - i;
			if (PROCESS_DIRECTION == Direction.FIRST_TO_LATEST) {
				// FIRST TO LATEST
				vidIdx = len - 1 - i;
				vidDisplayNum = i + 1;
			}

			processVid(vids, vidIdx, vidDisplayNum);

			if (shouldStop) {
				System.out.println("########### HALT ###########");
				break;
			}
		}

		System.out.println("\nexecution complete!");
		if (f != null)
			f.dispose();
		System.exit(0);

	}

	static void processVid(JSONArray vids, int vidIdx, int vidDisplayNum) {
		int len = vids.length();

		JSONObject vid = vids.getJSONObject(vidIdx);
		String hls = vid.getString("hlsurl");

		if (new File(DIR + "log/" + hls + ".txt").exists()) {
			System.out.println("(i) skipping " + hls + " because it's already uploaded");
			return;
		}
		if (new File(DIR + "locks/" + hls + ".txt").exists()) {
			System.out.println("(i) skipping " + hls + " because it's locked");
			return;
		}
		String cat = vid.getString("game");
		cat = cat == null ? null : cat.trim();
		if (SKIP_GAME_STREAMS && cat != null && (cat
				.contains("PLAYERUNKNOWN") /*
											 * || cat.contains("Elysium") ||
											 * cat.contains("Rocket")
											 */)) {
			System.out.println("(i) skipping " + hls + " because it's a game stream! (category = " + cat + ")");
			return;
		}

		System.out.println();
		System.out.println("==================================================================");
		System.out.println("Processing Video " + hls);
		System.out.println("Length = " + vid.getInt("length") + "s (" + secToTime(vid.getInt("length")) + ")");
		System.out.println("Upload date: " + vid.getString("time"));
		System.out.println("Video No. #" + vidDisplayNum);
		System.out.println("==================================================================");
		System.out.println("current temp dir = " + TEMP);
		System.out.println();

		try {
			new File(DIR + "locks/" + hls + ".txt").createNewFile(); // lock
																		// this
																		// video
			clearTmp();

			boolean rescue = false;
			VodInfo info = readPlaylist(vid.getString("id"), hls);

			File rescuedVodFolder = new File(VODPATH + vid.getString("id") + "/");
			if (rescuedVodFolder.exists() && rescuedVodFolder.isDirectory()) {
				System.out.println("found rescue vod");
				rescue = true;
				verifyTsFiles(rescuedVodFolder, hls, info);
				// TODO use info in "broken" folder
			} else {
				if (NUM_DL_THREADS > 1) {
					dlTsFilesMultithread(hls, info);
				} else {
					dlTsFiles(hls, info);
				}
			}

			if (MERGING_TECHNIQUE == MergeTechnique.CONCAT_LIST_TO_MP4) {
				try {
					genJoinList(hls, info);
					ffmpegConcat(hls, info);
					for (int p = 0; p < info.numParts; p++)
						checkLength(p, hls, info);
				} catch (Exception e) {
					if (RETRY_ON_FFMPEG_FAIL) {
						System.out.println("CONCAT FAILED, retrying with joining ts files...");
						joinTs(hls, info);
						convertJoinTsToMp4(hls, info);
					} else
						throw e;
				}
			} else if (MERGING_TECHNIQUE == MergeTechnique.JOIN_TS_AND_CONVERT) {
				joinTs(hls, info);
				convertJoinTsToMp4(hls, info);
			}
			// can't do checklength with join ts, becuase sometimes it gets
			// longer

			System.out.println("uploading...");
			// List<String> ytVidIds = new ArrayList<>();
			for (int p = 0; p < info.numParts; p++) {
				if (info.partFixMode && info.ignorePart[p]) {
					System.out.println("skipping metadata file for part " + p);
					continue;
				}

				// checkLength(p, hls, info);

				if (info.numParts > 1)
					System.out.println("uploading PART " + p);
				Deadmau5.VideoMetadata metadata = Deadmau5.genTitleAndDescription(vid, vidDisplayNum, len, rescue, info,
						p);

				JSONObject videoInfo = new JSONObject();
				videoInfo.put("title", metadata.title);
				videoInfo.put("description", metadata.desc);
				videoInfo.put("tags", new JSONArray(metadata.tags));
				try {
					File videoInfoFile = new File(DIR + "upload/" + p + "-" + hls + ".txt");
					FileWriter w = new FileWriter(videoInfoFile);
					videoInfo.write(w);
					w.close();
				} catch (Exception e) {
					e.printStackTrace();
				}

				/*
				 * String filename =
				 * DIR+"upload/"+p+"-"+vid.getString("hlsurl")+".mp4"; String
				 * vidStatus = Deadmau5.uploadToYt(metadata, filename); if
				 * (vidStatus == null || vidStatus.length() == 0) { throw new
				 * Exception("Upload failed (Part "+p+")."); } else {
				 * ytVidIds.add(vidStatus); //update log file after every part
				 * so we have the ids of earlier successful parts if later parts
				 * fail to upload File log = new File(DIR+"log/"+hls+".txt");
				 * FileWriter w = new FileWriter(log); w.write(String.join(" ",
				 * ytVidIds)); w.close(); }
				 */
			}

		} catch (Exception e) {
			System.err.println("SOMETHING WENT WRONG @ #" + vidDisplayNum + " " + hls);
			e.printStackTrace();
			File logerror = new File(DIR + "logerror/" + hls + ".txt");
			try {
				FileWriter w = new FileWriter(logerror);
				w.write("error at " + System.currentTimeMillis() + ": " + e.toString() + "\ntemp dir path:" + TEMP
						+ "\n\n");
				e.printStackTrace(new PrintWriter(w));
				w.close();
			} catch (IOException e1) {
				e1.printStackTrace();
			}
			boolean changeTempDir = true;
			if (DONT_PRESERVE_TEMP_DIR_ON_TS_NOT_FOUND && e.toString().contains("ts not found")) {
				// this is so sad, but we don't have to keep the files. we know
				// what went wrong
				changeTempDir = false;
				System.out.println("[ERROR] TS NOT FOUND ERROR OCCURRED, NOT CHANGING TEMP DIR!");
			}
			if (changeTempDir && PRESERVE_TEMP_DIR)
				newTempDir(); // change temp dir so we can use current one to
								// investigate what happened
		}
	}

	private static final double TOLERANCE = 2;

	private static void checkLength(int p, String hls, VodInfo info) throws Exception {
		// ffprobe concat.mp4 -show_entries format=duration -v quiet -of
		// csv="p=0"
		String file = DIR + "upload/" + p + "-" + hls + ".mp4";
		FFMpegReport rept = new FFMpegReport() {
			@Override
			void onOutput(String out) {
				try {
					length = Double.parseDouble(out.trim());
				} catch (Exception e) {
					System.err.println("error in reading ffmpeg output");
					e.printStackTrace();
				}
			}
		};
		System.out.println("ffprobe \"" + file + "\" -show_entries format=duration -v quiet -of csv=\"p=0\"");
		runAndGetStdOut("ffprobe \"" + file + "\" -show_entries format=duration -v quiet -of csv=\"p=0\"", false, rept,
				null, null);
		Thread.sleep(500);

		double partStart = info.partStartTimes.get(p);
		double partEnd = p == info.numParts - 1 ? info.totalLength : info.partStartTimes.get(p + 1);
		double partLength = partEnd - partStart;// - info.partCompensate[p];
		double diff = Math.abs(partLength - rept.length);
		System.out.println("concated mp4 video length: " + rept.length + "s");
		if (diff > TOLERANCE) {
			throw new Exception("concat video length does not match part length! (difference = " + diff + "s)");
		} else {
			System.out.println(" (i) length difference (part length to mp4 video file length): " + diff + "s");
		}
	}

	static class FFMpegReport {
		boolean ffmpegError = false;
		double length = 0;

		void onOutput(String out) {
		}
	}

	private static void ffmpegConcat(String hls, VodInfo info) throws Exception {
		System.out.println("concatenating files...");
		for (int p = 0; p < info.numParts; p++) {
			if (info.partFixMode && info.ignorePart[p]) {
				System.out.println("skipping concat for part " + p);
				continue;
			}
			if (info.numParts > 1)
				System.out.println("concatenating part " + p);
			String ffmpeg = "ffmpeg -f concat -safe 0 -i \"" + TEMP + p + "-" + hls
					+ ".txt\" -c copy -bsf:a aac_adtstoasc \"" + DIR + "upload/" + p + "-" + hls + ".mp4\" -y";
			FFMpegReport report = new FFMpegReport() {
				@Override
				void onOutput(String out) {
					if (!out.contains("decode_slice_header")) { // ignore these
																// errors,
																// they're fine
																// (i think)
						if (out.contains("error"))
							ffmpegError = true;
					}
				}
			};
			// runAndGetStdErr(ffmpeg, true, report, null,
			// TEMP+"ffmpeg-"+p+"-"+hls+".log");
			runAndGetStdErr(ffmpeg, true, report, null, DIR + "ffmpeg-logs/ffmpeg-" + p + "-" + hls + ".log");
			// TODO if there is a faulty file that can't be concat'd in, just
			// leave it out maybe
			if (report.ffmpegError) {
				throw new Exception("an ffmpeg error has occurred when concatenating " + hls
						+ "! stopping... (otherwise it will upload an unfinished video)");
			}
		}
		System.out.println("concatenation complete!");
	}

	static void convertJoinTsToMp4(String hls, VodInfo info) throws Exception {
		// source: twitch leecher
		/*
		 * Arguments = "-y" + (cropInfo.CropStart ? " -ss " +
		 * cropInfo.Start.ToString(CultureInfo.InvariantCulture) : null) +
		 * " -i \"" + sourceFile + "\" -analyzeduration " + int.MaxValue +
		 * " -probesize " + int.MaxValue +
		 * " -c:v copy -c:a copy -bsf:a aac_adtstoasc" + (cropInfo.CropEnd ?
		 * " -t " + cropInfo.Length.ToString(CultureInfo.InvariantCulture) :
		 * null) + " \"" + outputFile + "\"",
		 */
		System.out.println("converting files...");
		for (int p = 0; p < info.numParts; p++) {
			System.out.println("converting part " + p);
			String ffmpeg = "ffmpeg -i \"" + TEMP + p + "-" + hls + ".ts\" " + "-analyzeduration " + Integer.MAX_VALUE
					+ " -probesize " + Integer.MAX_VALUE + " " + "-c:v copy -c:a copy -bsf:a aac_adtstoasc \"" + DIR
					+ "upload/" + p + "-" + hls + ".mp4\" -y";
			runAndGetStdErr(ffmpeg, true, null, null, DIR + "ffmpeg-logs/join-" + p + "-" + hls + ".log");
		}
		System.out.println("converting complete!");
	}

	static void joinTs(String hlsUrl, VodInfo info) throws Exception {
		int numfiles = info.tsFiles.size();
		System.out.println(numfiles + " .ts files on disk");

		byte[] buf = new byte[4096];
		System.out.println("joining files... ");

		int i = 0;
		for (int p = 0; p < info.numParts; p++) {
			System.out.println("joining part " + p);
			int limit = 0;
			if (p == info.numParts - 1) {
				limit = info.fileLocations.length;
			} else {
				limit = info.partStartIndex.get(p + 1); // all files up to next
														// part
			}
			FileOutputStream out = new FileOutputStream(TEMP + p + "-" + hlsUrl + ".ts");
			for (; i < limit; i++) {
				String tsfile = info.fileLocations[i];
				double progr = i / (double) numfiles;
				System.out.print("\rjoining " + tsfile + "\tfile " + (i + 1) + " of " + numfiles + "\tprogress: "
						+ String.format("%.03f", progr * 100) + "%");
				InputStream in = new FileInputStream(new File(TEMP + tsfile));
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
	}

	private static void genJoinList(String hls, VodInfo info) throws Exception {
		System.out.println("making join list...");
		if (info.fileLocations.length != info.segmentLengths.size()) {
			throw new Exception("error " + info.fileLocations.length + " file locations but "
					+ info.segmentLengths.size() + " segment lengths");
		}
		if (info.partStartIndex.size() != info.numParts) {
			throw new Exception("missing part start indices! (" + info.numParts + " parts but only "
					+ info.partStartIndex.size() + " indices)");
		}
		int i = 0;
		for (int p = 0; p < info.numParts; p++) {
			int limit = 0;
			if (p == info.numParts - 1) {
				limit = info.fileLocations.length;
			} else {
				limit = info.partStartIndex.get(p + 1); // all files up to next
														// part
			}
			if (info.partFixMode && info.ignorePart[p]) {
				System.out.println("skipping join list for part " + p);
				i = limit;
				continue;
			}
			File join = new File(TEMP + p + "-" + hls + ".txt");
			FileWriter w = new FileWriter(join);
			for (; i < limit; i++) {
				if (info.fileLocations[i] != null) {
					w.write("file '" + info.fileLocations[i].trim() + "'\n");
					w.write("duration " + info.segmentLengths.get(i) + "\n");
				} else {
					System.out.println("concat list: leaving out file[" + i + "] (" + info.tsFiles.get(i)
							+ ") because it's null!");
				}
			}
			w.close();
		}
	}

	static VodInfo readPlaylist(String videoid, String hlsUrl) throws Exception {
		System.out.println("reading m3u8 hls playlist file " + hlsUrl + "...");
		String m3u8 = null;
		try {
			m3u8 = new String(Files.readAllBytes(Paths.get(HLS_PATH + hlsUrl + ".txt")));
		} catch (IOException e) {
			//System.out.println("file not found, trying download...");
			/*
			System.out.println("fetching nomute playlist...");
			String m3u8_nomute = readStringFromURL("https://vod-secure.twitch.tv/" + hlsUrl + "/chunked/index-dvr.m3u8");
			
			FileWriter w = new FileWriter(new File(DIR + "playlists_nomute/" + hlsUrl + ".txt"));
			w.write(m3u8_nomute);
			w.close();
			
			System.out.println("fetching usher...");
			String clientid = "kimne78kx3ncx6brgo4mv6wki5h1ko";
			String access_token = readStringFromURL("https://api.twitch.tv/api/vods/" + videoid + "/access_token?client_id=" + clientid);
			
			JSONObject accessToken = new JSONObject(new JSONTokener(access_token));
			String token = accessToken.getString("token");
			String sig = accessToken.getString("sig");
			
			String urlencToken = URLEncoder.encode(token, "UTF-8");
			String usher = readStringFromURL("https://usher.ttvnw.net/vod/"+videoid+".m3u8?token="+urlencToken+"&sig="+sig+"&allow_source=true&allow_audio_only=true");
			
			w = new FileWriter(new File(DIR + "usher/" + videoid + ".txt"));
			w.write(usher);
			w.close();
			
			System.out.println("finding real playlist...");
			String[] usherlines = usher.split("\n");
			String stream = "chunked";
			String m3u8realurl = null;
			for (int i = 0; i<usherlines.length; i++) {
				String line = usherlines[i].trim();
				if (i+2 < usherlines.length && line.contains("GROUP-ID=\""+stream+"\"")) {
					m3u8realurl = usherlines[i+2];
					break;
				}
			}
			if (m3u8realurl == null) throw new Exception("m3u8 url not found from usher");
			System.out.println("real playlist url: "+m3u8realurl);
			
			System.out.println("fetching real playlist...");
			m3u8 = readStringFromURL(m3u8realurl);
			
			w = new FileWriter(new File(DIR + "playlists_real/" + hlsUrl + ".txt"));
			w.write(m3u8);
			w.close();
			*/
			throw new Exception("could not read playlist file "+HLS_PATH + hlsUrl + ".txt");
		}
		String[] lines = m3u8.split("\n");
		List<String> tsFiles = new ArrayList<>();
		List<Double> segmentLengths = new ArrayList<>();
		List<Double> discoErrors = new ArrayList<>();
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
				// String discoTimestamp = secToTime(pos);
				if (!discoErrors.contains(pos))
					discoErrors.add(pos);
			}
			if (!line.startsWith("#") && line.endsWith(".ts")) {
				tsFiles.add(line);
			}
		}
		System.out.println(tsFiles.size() + " .ts files in playlist");

		VodInfo info = new VodInfo();
		info.tsFiles = tsFiles;
		info.fileLocations = new String[tsFiles.size()];
		info.segmentLengths = segmentLengths;
		info.totalLength = totalLength;

		if (discoErrors.size() > 0) {
			info.discoTimesDouble = discoErrors;
			System.out.println("STREAM HAS DISCONTINUITY ERRORS");
		}

		if (info.tsFiles.size() != info.segmentLengths.size()) {
			throw new Exception("error " + info.tsFiles.size() + " ts files in m3u8 but " + info.segmentLengths.size()
					+ " segment lengths");
		}
		int videoMaxLen = 12 * 3600 - 5;
		if (info.totalLength > videoMaxLen) {
			int numParts = (int) Math.round(Math.ceil(info.totalLength / videoMaxLen));
			System.out.println("video is longer than 12 hours");
			System.out.println("splitting into " + numParts + " parts!");
			info.numParts = numParts;
		}
		info.ignorePart = new boolean[info.numParts];
		// info.partCompensate = new double[info.numParts];
		int numTsFiles = info.tsFiles.size();
		int i = 0;
		int limit = 0;
		pos = 0;
		for (int p = 0; p < info.numParts; p++) {
			info.partStartTimes.add(pos);
			info.partStartIndex.add(i);
			if (p == info.numParts - 1) {
				limit = numTsFiles;
			} else {
				limit = numTsFiles * (p + 1) / info.numParts;
			}
			for (; i < limit; i++) {
				pos += info.segmentLengths.get(i);
			}
			boolean skipPart = ENABLE_PARTFIX_MODE && new File(DIR + "logpart/" + p + "-" + hlsUrl + ".txt").exists();
			if (skipPart) {
				info.partFixMode = true;
				System.out.println("skipping PART " + p + " because already uploaded!");
			}
			info.ignorePart[p] = skipPart;
		}

		return info;
	}

	private static void verifyTsFiles(File rescuedVodFolder, String hls, VodInfo info) throws Exception {
		System.out.println("verifying ts files on disk @ " + rescuedVodFolder.getAbsolutePath());

		File brokenFile = new File(DIR + "/broken/broken-" + hls + ".txt");
		List<String> brokenList = null;
		if (USE_BROKEN_FILE_INFO && brokenFile.exists()) {
			System.out.println("Using broken file info!");
			JSONArray brokenListJson = null;
			try {
				brokenListJson = new JSONArray(new JSONTokener(new FileReader(brokenFile)));
			} catch (Exception e) {
				e.printStackTrace();
			}
			brokenList = new ArrayList<>(brokenListJson.length());
			for (int i = 0; i < brokenListJson.length(); i++)
				brokenList.add(brokenListJson.getString(i).trim());
		}

		int numfiles = info.tsFiles.size();
		for (int p = 0; p < info.numParts; p++) {
			if (info.partFixMode && info.ignorePart[p]) {
				System.out.println("\nignoring download of part " + p);
				continue;
			}
			int limit = numfiles;
			if (p + 1 < info.partStartIndex.size())
				limit = info.partStartIndex.get(p + 1);
			System.out.println("\ndownloading part " + p + " starting at index " + info.partStartIndex.get(p));
			for (int i = info.partStartIndex.get(p); i < limit; i++) {
				String tsfile = info.tsFiles.get(i);
				double progr = i / (double) numfiles;
				System.out.print("\rverifying " + tsfile + "\tfile " + (i + 1) + " of " + numfiles + "\tprogress: "
						+ String.format("%.03f", progr * 100) + "%");

				boolean hasError = false;
				if (new File(rescuedVodFolder.getAbsolutePath() + "/" + tsfile).exists()) {
					if (DO_VERIFY) {
						String ffmpeg = "ffmpeg -v error -i \"" + (rescuedVodFolder.getAbsolutePath() + "/" + tsfile)
								+ "\" -f null - ";
						List<String> errors = new ArrayList<>();
						runAndGetStdErr(ffmpeg, false, null, errors, null);
						for (String err : errors) {
							if (err.trim().length() > 0)
								hasError = true;
							System.out.println("\nFFMPEG ERROR: " + err);
						}
					} else if (USE_BROKEN_FILE_INFO) {
						hasError = brokenList.contains(tsfile);
						if (hasError)
							System.out.println("\nBROKEN FILE " + tsfile);
					}
				} else
					hasError = true; // file is missing

				if (hasError) { // local file is broken, download form server
					downloadTs(hls, i, /* p, */ tsfile, info);
					// what to do when only muted file is available? -> deal
					// with it
				} else {
					info.fileLocations[i] = rescuedVodFolder.getAbsolutePath() + "/" + tsfile; // can
																								// use
																								// local
																								// file
																								// because
																								// it's
																								// not
																								// broken
				}
			}
		}

		System.out.println("\nverifying complete!");
	}

	static class MultithreadDownloadManager {
		private int dlIndex = 0;
		private double dlPos = 0;
		private int numThreadsDone = 0;
		private Exception errorOccurred = null;
		private VodInfo info;
		private boolean interrupt;
		int numFiles = 0;

		public MultithreadDownloadManager(VodInfo info) {
			this.info = info;
			numFiles = info.tsFiles.size();
		}

		synchronized int nextDownloadIndex() {
			int idx = dlIndex;
			dlIndex++;
			if (idx > 0 && idx <= numFiles)
				dlPos += info.segmentLengths.get(idx - 1);
			return idx;
		}

		synchronized int currentDlIndex() {
			int i = dlIndex > 0 ? (dlIndex - 1) : 0;
			return i < numFiles ? i : numFiles - 1;
		}

		synchronized double currentDlPos() {
			return dlPos;
		}

		synchronized void done() {
			numThreadsDone++;
		}

		synchronized boolean isDone() {
			return numThreadsDone >= NUM_DL_THREADS;
		}

		public synchronized void exception(Exception e) {
			errorOccurred = e;
		}

		public synchronized boolean hasErrorOccurred() {
			return errorOccurred != null;
		}

		public Exception getException() {
			return errorOccurred;
		}

		public void interrupt() {
			interrupt = true;
		}
	}

	// does not support partfixmode or partcompensate
	private static void dlTsFilesMultithread(String hls, VodInfo info) throws Exception {
		System.out.println("downloading ts files (" + NUM_DL_THREADS + " threads) ...");

		int numTsFiles = info.tsFiles.size();
		long startTime = System.currentTimeMillis();
		MultithreadDownloadManager mgr = new MultithreadDownloadManager(info);

		Thread[] dlThreads = new Thread[NUM_DL_THREADS];
		for (int t = 0; t < NUM_DL_THREADS; t++) {
			dlThreads[t] = new Thread(new Runnable() {
				@Override
				public void run() {
					int idx = -1;
					while ((idx = mgr.nextDownloadIndex()) < info.tsFiles.size() && !mgr.interrupt) {
						try {
							downloadTs(hls, idx, info.tsFiles.get(idx), info);
						} catch (Exception e) {
							mgr.exception(e);
							return;
						}
						while (shouldPauseDownload) {
							try {
								Thread.sleep(1000);
							} catch (InterruptedException e) {}
						}
					}
					mgr.done();
				}
			});
			dlThreads[t].start();
		}

		while (!mgr.isDone()) {
			if (mgr.hasErrorOccurred()) {
				System.out.println("stopping all threads...");
				mgr.interrupt();
				for (int t = 0; t < NUM_DL_THREADS; t++) {
					dlThreads[t].join();
				}
				System.out.println("all threads stopped.");
				throw mgr.getException();
			}

			int i = mgr.currentDlIndex();
			double pos = mgr.currentDlPos();

			String ts = info.tsFiles.get(i);
			double progr = i / (double) numTsFiles;
			long timeElapsed = System.currentTimeMillis() - startTime;
			double secs = timeElapsed / 1000d;
			double speed = secs > 0.05 ? pos / secs : 1;
			System.out.print("\r"
					+ (shouldPauseDownload ? "[PAUSED]" : "downloading")
					+ " " + ts + ",\tfile " + (i + 1) + " of " + numTsFiles + "\tthreads:"
					+ NUM_DL_THREADS + "\ttime_elapsed:" + secs + "s (" + secToTime(secs) + ")\tavg.speed:"
					+ String.format("%.02f", speed) + "x" + "\tprogress:" + String.format("%.03f", progr * 100) + "%"
					+ "\t.\t.\t.");

			Thread.sleep(500); // refresh this message every .5s
		}

		for (int t = 0; t < NUM_DL_THREADS; t++) {
			dlThreads[t].join();
		}

		System.out.println("\ndone downloading!");
		System.out.println(
				" (i) pos - total length difference (should be ~0): " + (mgr.currentDlPos() - info.totalLength));
	}

	private static void dlTsFiles(String hls, VodInfo info) throws Exception {
		System.out.println("downloading ts files...");

		int numTsFiles = info.tsFiles.size();
		long startTime = System.currentTimeMillis();
		double pos = 0;

		int nummuted = 0;
		boolean isCurrentlyMuted = false;
		double currentMuteStart = 0;

		for (int p = 0; p < info.numParts; p++) {
			if (info.partFixMode && info.ignorePart[p]) {
				System.out.println("\nignoring download of part " + p);
				continue;
			}
			int limit = numTsFiles;
			if (p + 1 < info.partStartIndex.size())
				limit = info.partStartIndex.get(p + 1);
			System.out.println("\ndownloading part " + p + " starting at index " + info.partStartIndex.get(p));
			for (int i = info.partStartIndex.get(p); i < limit; i++) {
				String ts = info.tsFiles.get(i);
				double segLen = info.segmentLengths.get(i);
				double progr = i / (double) numTsFiles;
				long timeElapsed = System.currentTimeMillis() - startTime;
				double secs = timeElapsed / 1000d;
				double speed = secs > 0.05 ? pos / secs : 1;
				System.out.print("\rdownloading " + ts + ",\tfile " + (i + 1) + " of " + numTsFiles + "\tnum_muted:"
						+ nummuted + "\ttime_elapsed:" + secs + "s (" + secToTime(secs) + ")\tavg.speed:"
						+ String.format("%.02f", speed) + "x" + "\tprogress:" + String.format("%.03f", progr * 100)
						+ "%");
				TsDownloadInfo result = downloadTs(hls, i, ts, info);

				if (result.muted) {
					nummuted++;
					System.out.print("\rMUTE FOUND! " + ts + ",\tfile " + (i + 1) + " of " + numTsFiles + "\tnum_muted:"
							+ nummuted + "\ttime_elapsed:" + secs + "s (" + secToTime(secs) + ")\tavg.speed:"
							+ String.format("%.02f", speed) + "x" + "\tprogress:" + String.format("%.03f", progr * 100)
							+ "%");
					if (!isCurrentlyMuted) {
						currentMuteStart = pos;
						isCurrentlyMuted = true;
					}
				} else {
					if (isCurrentlyMuted) { // end mute here
						isCurrentlyMuted = false;
						// info.mutedSegments.add(secToTime(currentMuteStart)+"
						// - "+secToTime(pos));
						info.mutedSegmentsStartTimes.add(currentMuteStart);
						info.mutedSegmentsEndTimes.add(pos);
					}
				}

				pos += segLen;
			}
		}

		if (isCurrentlyMuted) { // if muted at end of vod
			// info.mutedSegments.add(secToTime(currentMuteStart)+" -
			// "+secToTime(info.totalLength));
			info.mutedSegmentsStartTimes.add(currentMuteStart);
			info.mutedSegmentsEndTimes.add(info.totalLength);
		}
		System.out.println("\ndone downloading!");
		System.out.println(" has mute: " + info.muted + "\tmuted files: " + nummuted + "\tmuted segments: "
				+ info.mutedSegmentsStartTimes.size());
		System.out.println(" (i) pos - total length difference (should be ~0): " + (pos - info.totalLength));

		if (!info.partFixMode && WRITE_MUTES_FILE) {
			System.out.println("=> writing mute & discontinuity information to disk...");
			JSONObject muteInfoFile = new JSONObject();
			muteInfoFile.put("filemap", new JSONObject(info.mutes));
			/*
			 * if (info.missing.size()>0) { muteInfoFile.put("missing", new
			 * JSONArray(info.missing)); muteInfoFile.put("partCompensate", new
			 * JSONArray(info.partCompensate)); }
			 */
			// muteInfoFile.put("segmentStrings", new
			// JSONArray(info.mutedSegments));
			muteInfoFile.put("mutedSegmentsStartTimes", new JSONArray(info.mutedSegmentsStartTimes));
			muteInfoFile.put("mutedSegmentsEndTimes", new JSONArray(info.mutedSegmentsEndTimes));
			muteInfoFile.put("muted", info.muted);
			muteInfoFile.put("discontinuityTimes",
					info.discoTimesDouble == null ? JSONObject.NULL : new JSONArray(info.discoTimesDouble));

			File muteFile = new File(DIR + "mutes/mutes-" + hls + ".txt");
			FileWriter w = new FileWriter(muteFile);
			muteInfoFile.write(w);
			w.close();
		} else {
			System.out.println(" [!] skipping mute & discontinuity file");
		}
	}

	private static class TsDownloadInfo {
		boolean muted;
	}

	private static TsDownloadInfo downloadTs(String hls, int index,
			/* int part, */ String filename, VodInfo info) throws Exception {
		try {
			TsDownloadInfo result = new TsDownloadInfo();
			URL chunk = new URL(VOD_SERVER + hls + "/chunked/" + filename);
			HttpURLConnection connection = (HttpURLConnection) chunk.openConnection();
			if (connection.getResponseCode() == 403) {
				String tsMuted = (filename.replace(".ts", "-muted.ts"));
				chunk = new URL(VOD_SERVER + hls + "/chunked/" + tsMuted);
				connection = (HttpURLConnection) chunk.openConnection();
				if (connection.getResponseCode() == 403) {
					if (FORBID_MISSING_TS) {
						throw new Exception("ts not found and muted ts not found (error " + connection.getResponseCode()
								+ ") for " + filename);
					} else {
						// maybe not throw an exception if this starts to happen
						// regularly and just leave file out
						System.out.println("ts file not found: " + filename);
						// info.missing.add(filename);
						// info.partCompensate[part] +=
						// info.segmentLengths.get(index);
						return result;
					}
				} else if (connection.getResponseCode() != 200) {
					System.out.println("HTTP ERROR " + connection.getResponseCode() + " OCCURRED");
					System.out.println("chilling for 60s and retrying...");
					Thread.sleep(60 * 1000);
					return downloadTs(hls, index, filename, info); // retry
				} else {
					if (FORBID_MUTE_DOWNLOADS)
						throw new Exception(filename
								+ " is muted. mutes are not allowed currently. change FORBID_MUTE_DOWNLOADS option.");
					info.mutes.put(filename, tsMuted);
					info.muted = true;
					result.muted = true;
				}
			} else if (connection.getResponseCode() != 200) {
				System.out.println("HTTP ERROR " + connection.getResponseCode() + " OCCURRED");
				// maybe vod server has some problems and we should chill for a
				// minute?
				System.out.println("chilling for 60s and retrying...");
				Thread.sleep(60 * 1000);
				return downloadTs(hls, index, filename, info); // retry
			}
			InputStream input = connection.getInputStream();
			//String outFile = TEMP + filename;
			String outFile = DIR+"videos/" +info.videoid+ "/"+ filename;
			DataOutputStream out = new DataOutputStream(
					new BufferedOutputStream(new FileOutputStream(outFile)));
			byte[] buffer = new byte[4096];
			int n;
			while ((n = input.read(buffer)) != -1) {
				out.write(buffer, 0, n);
			}
			input.close();
			out.close();

			// info.fileLocations[index]= TEMP+filename; //will be stored in
			// temp
			info.fileLocations[index] = filename; // will be stored in temp
			return result;
		} catch (SocketException se) {
			System.out.println("what the frick socket");
			se.printStackTrace();
			Thread.sleep(5000); // wait a bit
			return downloadTs(hls, index, filename, info); // retry
		}
	}

	@SuppressWarnings("unused")
	private static void verifyLocalStreams() {
		System.out.println("VERIFYING LOCAL STREAMS!");

		JSONArray vids = null;
		try {
			vids = new JSONArray(new JSONTokener(new FileReader(new File(DIR + "hls_array.txt"))));
		} catch (Exception e) {
			e.printStackTrace();
		}

		int len = vids.length();
		for (int k = 0; k < len; k++) {

			// LATEST TO FIRST
			int vidIdx = k;
			int vidDisplayNum = len - k;
			// FIRST TO LATEST
			// int vidIdx = len-1-i;
			// int vidDisplayNum = i+1;

			JSONObject vid = vids.getJSONObject(vidIdx);
			String hls = vid.getString("hlsurl");

			try {
				File rescuedVodFolder = new File(VODPATH + vid.getString("id") + "/");
				if (!rescuedVodFolder.exists())
					continue;

				System.out.println();
				System.out.println("==================================================================");
				System.out.println("Processing Video " + hls);
				System.out.println("Length = " + vid.getInt("length") + "s (" + secToTime(vid.getInt("length")) + ")");
				System.out.println("Upload date: " + vid.getString("time"));
				System.out.println("Video No. #" + vidDisplayNum);
				System.out.println("==================================================================");
				System.out.println();

				VodInfo info = readPlaylist(vid.getString("id"), hls);
				List<String> broken = new ArrayList<>();

				int numfiles = info.tsFiles.size();
				for (int i = 0; i < numfiles; i++) {
					String tsfile = info.tsFiles.get(i);
					double progr = i / (double) numfiles;
					System.out.print("\rverifying " + tsfile + "\tfile " + (i + 1) + " of " + numfiles + "\tprogress: "
							+ String.format("%.03f", progr * 100) + "%");

					boolean hasError = false;
					if (new File(rescuedVodFolder.getAbsolutePath() + "/" + tsfile).exists()) {
						String ffmpeg = "ffmpeg -v error -i \"" + (rescuedVodFolder.getAbsolutePath() + "/" + tsfile)
								+ "\" -f null - ";
						List<String> errors = new ArrayList<>();
						runAndGetStdErr(ffmpeg, false, null, errors, null);
						for (String err : errors) {
							if (err.trim().length() > 0)
								hasError = true;
							System.out.println("\nFFMPEG ERROR: " + err);
						}
					} else
						hasError = true; // file is missing

					if (hasError) { // local file is broken
						broken.add(tsfile);
					}
				}
				System.out.println();

				if (broken.size() > 0) {
					System.out.println(broken.size() + " broken files, writing to disk...");
					try {
						File muteFile = new File(DIR + "broken/broken-" + hls + ".txt");
						FileWriter w = new FileWriter(muteFile);
						new JSONArray(broken).write(w);
						w.close();
					} catch (Exception e) {
						e.printStackTrace();
					}
				} else {
					System.out.println("no broken files found! :)");
				}

			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	// UTIL:

	static void clearTmp() {
		System.out.print("clearing temp dir... ");
		try {
			File[] list = new File(TEMP).listFiles();
			for (File f : list) {
				// System.out.println(" /"+f.getName());
				f.delete();
			}
			System.out.println(" done!");
		} catch (Exception e) {
			System.err.println("error clearing temp dir: " + e.toString());
		}
	}

	private static void runAndGetStdOut(String ffmpeg, boolean print, FFMpegReport report, List<String> errors,
			String logFile) throws IOException {
		runAndGetOutput(ffmpeg, print, report, errors, logFile, false);
	}

	private static void runAndGetStdErr(String ffmpeg, boolean print, FFMpegReport report, List<String> errors,
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
	}

	public static String readStringFromURL(String requestURL) {
		try (Scanner scanner = new Scanner(new URL(requestURL).openStream(), StandardCharsets.UTF_8.toString())) {
			scanner.useDelimiter("\\A");
			return scanner.hasNext() ? scanner.next() : "";
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
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

}
