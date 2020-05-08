import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.DateFormatSymbols;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.Playlist;
import com.google.api.services.youtube.model.PlaylistItem;
import com.google.api.services.youtube.model.PlaylistItemListResponse;
import com.google.api.services.youtube.model.PlaylistItemSnippet;
import com.google.api.services.youtube.model.PlaylistListResponse;
import com.google.api.services.youtube.model.PlaylistSnippet;
import com.google.api.services.youtube.model.PlaylistStatus;
import com.google.api.services.youtube.model.ResourceId;
import com.google.api.services.youtube.model.Video;
import com.google.api.services.youtube.model.VideoSnippet;
import com.google.api.services.youtube.model.VideoStatus;
import com.google.common.collect.Lists;


public class Playlists {

	
	static final String LTF = "PLEh04lts8PqRVN_eELVbdMSgCHKrELjUn";
	static final String FTL = "PLEh04lts8PqTG5jRBe1XXOKw5TPYKkiqc";
	
	public static void main(String[] args) {
		
		//saveVideoCopynotices();
		
		//genDesc();
		
		//System.out.println(TwitchThing2.listVid("7UVuGgCj9Fw"));
		
		//addVidsToPlaylist();
		
		//createMonthPlaylists();
		
		
	}
	
	private static void createRefreshToken(int credIndex) {
		
		try {
			List<String> scopes = Lists.newArrayList("https://www.googleapis.com/auth/youtube",
					"https://www.googleapis.com/auth/youtube.upload",
					"https://www.googleapis.com/auth/youtube.force-ssl",
					"https://www.googleapis.com/auth/youtube.readonly");
			Credential credential = YtAuth.authorize(scopes, "whatever", "gamma");
			System.out.println("ref " + credential.getRefreshToken());
		} catch (IOException e) {
			e.printStackTrace();
		}
		 
	}
	
	private static void uploadVidsInFolder(File folder, int start, String addToPlaylist) {
		File[] files = folder.listFiles();
		for (int i = start; i<files.length; i++) {
			System.out.println("[UPLOADING] "+files[i].getName());
			Deadmau5.VideoMetadata m = new Deadmau5.VideoMetadata();
			m.title = files[i].getName().substring(9); //remove timestamp in front
			m.title = m.title.substring(0, m.title.length() - 4); //remove ".mp4"
			m.desc = "";
			m.tags = new ArrayList<>();
			m.tags.add("deadmau5");
			m.tags.add("livestream");
			String videoId = Deadmau5.uploadToYt(m, files[i].getAbsolutePath());
			
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			files[i].renameTo(new File(files[i].getParent()+"\\"+"UPLOADED_"+files[i].getName()));
			
			if (addToPlaylist != null) insertVideoIntoPlaylist(addToPlaylist, videoId);
		}
	}

	private static void listVideosInPlaylist(String playlistId, String txtfile) {
		try {
			YouTube youtubeService = Deadmau5.getYouTubeService(Deadmau5.credIndex);
			
			List<PlaylistItem> items = new ArrayList<>();
	        String pageToken = null;
	        do {
	        	YouTube.PlaylistItems.List request = youtubeService.playlistItems()
	    				.list("snippet,contentDetails")
	    				.setMaxResults(50L)
	    				.setPlaylistId(playlistId);
    			
    			if (pageToken != null) {
    				request = request.setPageToken(pageToken);
    			}
    			
    			PlaylistItemListResponse response = request.execute();
    			List<PlaylistItem> items2 = response.getItems();
    			items.addAll(items2);
    			
    			pageToken = response.getNextPageToken();
    			System.out.println(pageToken);
	        } while (pageToken != null);
	        
	        System.out.println(items.size());
	        
	        JSONArray r = new JSONArray();
	        for (PlaylistItem i: items) {
	        	r.put(new JSONObject(i.toString()));
	        }
	        
	        File log = new File(txtfile);
			FileWriter w = new FileWriter(log);
			r.write(w);
			w.close();
			
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	static void addVidsToPlaylist() {
		JSONArray vids = null;
		try {
			vids = new JSONArray(new JSONTokener(new FileReader(new File("F:/deadmau5/hls_array.txt"))));
			JSONObject ytLinkList = new JSONObject(new JSONTokener(new FileReader(new File("F:/deadmau5/php/definite_yt_links_DROPIN.txt"))));
			
			int start = 0;
			int len = vids.length();
			for (int i = start; i<len; i++) {
				String vidid = vids.getJSONObject(len-1-i).getString("id");
				System.out.println(vidid);
				if (ytLinkList.has(vidid)) {
					JSONArray ytLinks = ytLinkList.getJSONArray(vidid);
					for (int k = 0; k<ytLinks.length(); k++) {
						if (ytLinks.isNull(k)) continue;
						String ytLink = ytLinks.getString(k);
						insertVideoIntoPlaylist(FTL, ytLink);
					}
					Thread.sleep(500);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	static void saveVideoCopynotices() {
		try {
			JSONArray vids = new JSONArray(new JSONTokener(new FileReader(new File("F:/deadmau5/hls_array.txt"))));
			JSONObject ytLinkList = new JSONObject(new JSONTokener(new FileReader(new File("F:/deadmau5/php/definite_yt_links.txt"))));
			
			int[] blocked = {12,171,178,180,165,326,6,153,687,720,794,818};
			for (int b: blocked) {
				int idx = 874-b;
				String vidid = vids.getJSONObject(idx).getString("id");
				JSONArray ytLinks = ytLinkList.getJSONArray(vidid);
				for (int k = 0; k<ytLinks.length(); k++) {
					String ytLink = ytLinks.getString(k);
					if (ytLink != null) {
						String page = request("https://www.youtube.com/video_copynotice?v="+ytLink);
						FileWriter w = new FileWriter("F:/deadmau5/copystrike/copyright_notices/"+k+"-"+vidid+"_"+ytLink+".txt");
						w.write(page);
						w.close();
					}
				}
			}
			
			
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	static String request(String url) throws Exception {
		URL myURL = new URL(url);
		HttpURLConnection myURLConnection = (HttpURLConnection)myURL.openConnection();

		myURLConnection.setRequestMethod("GET");
		
		BufferedReader  r = new BufferedReader( new FileReader(new File("F:/deadmau5/copystrike/headers.txt")));
		String h = null;
		while ((h = r.readLine()) != null) {
			int c = h.indexOf(':');
			//System.out.println(h.substring(0, c)+"==="+h.substring(c+1, h.length()).trim());
			myURLConnection.setRequestProperty(h.substring(0, c), h.substring(c+1, h.length()).trim());
		}
		r.close();
		
		myURLConnection.setUseCaches(false);
		myURLConnection.setDoInput(true);
		myURLConnection.setDoOutput(true);
		
		BufferedReader br = new BufferedReader(new InputStreamReader((myURLConnection.getInputStream())));
		String output;
		StringBuilder sb = new StringBuilder();
		while ((output = br.readLine()) != null) {
			sb.append(output);
		}
		
		return  (sb.toString());
	}
	
	private static void genDesc() {
		try {
			
			JSONArray vids = new JSONArray(new JSONTokener(new FileReader(new File("F:/deadmau5/hls_array.txt"))));
			JSONObject playlistinfo = new JSONObject(new JSONTokener(new FileReader(new File("F:/deadmau5/playlistinfo.txt"))));
			JSONObject mutedSegments = new JSONObject(new JSONTokener(new FileReader(new File("F:/deadmau5/php/muted_segments_from_playlist.txt"))));
			JSONObject ytLinks = new JSONObject(new JSONTokener(new FileReader(new File("F:/deadmau5/php/definite_yt_links_DROPIN.txt"))));
			JSONObject fullhlsurls = new JSONObject(new JSONTokener(new FileReader(new File("F:/deadmau5/php/chunkedhlsurls.txt"))));
			JSONObject copyrightInfo = new JSONObject(new JSONTokener(new FileReader(new File("F:/deadmau5/copystrike/copyright_info_hls.txt"))));
			
			String vidNext = null; //from last iteration
			String skipNext = "";
			int vc = 0;
			for (int i = 0; i<vids.length(); i++) {
				JSONObject vid = vids.getJSONObject(i);
				int displaynum = vids.length() - i;
				String id = vid.getString("id");
				boolean rescue = displaynum >= 851; 
				JSONObject playlistInfoVid = playlistinfo.getJSONObject(vid.getString("hlsurl"));
				JSONArray videoMutedSegments = null;
				if (mutedSegments.has(id)) videoMutedSegments = mutedSegments.getJSONArray(id);
				
				JSONArray ytLinksVid = ytLinks.getJSONArray(id);
				if (ytLinksVid.isNull(0)) {
					skipNext = "#"+displaynum;
					continue; //this is a non processed video
				}
				
				String vidPrev = null;
				int j= i;
				String skipPrev = "";
				while (j+1<vids.length() && vidPrev == null) {
					JSONArray prevYtLinks = ytLinks.getJSONArray(vids.getJSONObject(j+1).getString("id"));
					if (prevYtLinks.isNull(0)) {
						skipPrev = "#"+(vids.length()-(j+1));
						j++;
					} else {
						vidPrev = prevYtLinks.getString(0);
					}
				}
				
				vidPrev = vidPrev == null ? null : vidPrev+(skipPrev.isEmpty()?"":(" (skiping "+skipPrev+")"));
				vidNext = vidNext == null ? null : vidNext+(skipNext.isEmpty()?"":(" (skiping "+skipNext+")"));
				
				for (int p = 0; p<playlistInfoVid.getInt("numParts"); p++) {
					
					JSONArray claims = null;
					if (copyrightInfo.has(p+"-"+vid.getString("hlsurl"))) {
						claims = copyrightInfo.getJSONObject(p+"-"+vid.getString("hlsurl")).getJSONArray("claims");
					}
					String cNormalVersion = ytLinksVid.getString(p);
					String cUnblockedVersion = null;
					File muteUnblockFile = new File("F:/deadmau5/copystrike/dropin/"+p+"-"+vid.getString("hlsurl")+".MUTE_ALL.txt");
					if (muteUnblockFile.exists()) {
						cUnblockedVersion = new String(Files.readAllBytes(Paths.get(muteUnblockFile.getAbsolutePath()))).trim();
					}
					
					if (displaynum < 851) continue;
					System.out.println("#"+displaynum+" p="+p+" yt="+cNormalVersion);
					Deadmau5.VideoMetadata meta = Deadmau5.genTitleAndDescription2(vid, displaynum, vids.length(), rescue, p, 
							playlistInfoVid, 
							videoMutedSegments, 
							ytLinksVid, 
							vidPrev,
							vidNext, 
							fullhlsurls.getString(id),
							claims, cUnblockedVersion, null);
					updateVideo(cNormalVersion, meta, "public");
					vc++;
					
					if (cUnblockedVersion != null) {
						System.out.println("[UNBLOCK] #"+displaynum+" p="+p+" yt="+cUnblockedVersion);
						Deadmau5.VideoMetadata meta2 = Deadmau5.genTitleAndDescription2(vid, displaynum, vids.length(), rescue, p, 
								playlistInfoVid, 
								videoMutedSegments, 
								ytLinksVid, 
								vidPrev,
								vidNext, 
								fullhlsurls.getString(id),
								claims, null, cNormalVersion);
						updateVideo(cUnblockedVersion, meta2, "unlisted");
					}
				}
				
				skipNext = "";
				vidNext = ytLinksVid.getString(0);
			}
			System.out.println(vc);
			
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/*static void createMonthPlaylistsAndWriteFile() {
		HashMap<String, String> playlists = createMonthPlaylists();
		
		File f = new File("F:/deadmau5/ytmonthplaylists_DONT.txt");
		
		JSONObject ytmonthplaylistsFile = new JSONObject(playlists);
		
		try {
			FileWriter w = new FileWriter(f);
			ytmonthplaylistsFile.write(w);
			w.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		
	}*/
	
	private static void updateVideo(String videoId, Deadmau5.VideoMetadata meta, String priv) {
		
		try {
			YouTube youtube = Deadmau5.getYouTubeService(Deadmau5.credIndex);
	    	
			HashMap<String, String> parameters = new HashMap<>();
	        parameters.put("part", "snippet,status");


	        Video video = new Video();
	        video.setId(videoId);
	        VideoSnippet snippet = new VideoSnippet();
	        snippet.setCategoryId("22"); //People & Blogs
	        snippet.setTitle(meta.title);
            snippet.setDescription(meta.desc);
            snippet.setTags(meta.tags);
	        VideoStatus status = new VideoStatus();
	        status.setPrivacyStatus(priv);
	        status.setEmbeddable(true);

	        video.setSnippet(snippet);
	        video.setStatus(status);

	        YouTube.Videos.Update videosUpdateRequest = youtube.videos().update(parameters.get("part").toString(), video);

	        Video response = videosUpdateRequest.execute();
	        System.out.println(response);

		} catch (GoogleJsonResponseException e) {
        	if (e.getDetails().getMessage().contains("exceeded") && e.getDetails().getMessage().contains("quota")) {
        		Deadmau5.quotaExceeded();
        		updateVideo(videoId, meta, priv);
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
		
		
	}

	static void deleteAllPlaylists() {
		List<Playlist> allPlaylists = listMyPlaylists();
		
		for (Playlist p : allPlaylists) {
			String id = p.getId();
        	System.out.println("deleting playlist "+id);
        	deletePlaylist(id);
        }
	}
	
	static List<Playlist> listMyPlaylists() {
		    try {
		    	YouTube youtube = Deadmau5.getYouTubeService(Deadmau5.credIndexSmall);
		    	
		        HashMap<String, String> parameters = new HashMap<>();
		        parameters.put("part", "snippet,contentDetails");
		        parameters.put("mine", "true");
		        parameters.put("maxResults", "50");

		        YouTube.Playlists.List playlistsListMineRequest  = youtube.playlists().list(parameters.get("part").toString());
		        if (parameters.containsKey("mine") && parameters.get("mine") != "") {
		            boolean mine = (parameters.get("mine") == "true") ? true : false;
		            playlistsListMineRequest.setMine(mine);
		        }

		        if (parameters.containsKey("maxResults")) {
		            playlistsListMineRequest.setMaxResults(Long.parseLong(parameters.get("maxResults").toString()));
		        }


		        PlaylistListResponse response = playlistsListMineRequest.execute();
		        System.out.println(response);
		        
		        return response.getItems();
		    } catch (Exception e) {
		    	e.printStackTrace();
		    }
			return null;

	}
	
	static void deletePlaylist (String id) {
		    try {
		    	YouTube youtube = Deadmau5.getYouTubeService(Deadmau5.credIndexSmall);
		    	
		        HashMap<String, String> parameters = new HashMap<>();
		        parameters.put("id", id);
		        parameters.put("onBehalfOfContentOwner", "");

		        YouTube.Playlists.Delete playlistsDeleteRequest = youtube.playlists().delete(parameters.get("id").toString());
		        if (parameters.containsKey("onBehalfOfContentOwner") && parameters.get("onBehalfOfContentOwner") != "") {
		            playlistsDeleteRequest.setOnBehalfOfContentOwner(parameters.get("onBehalfOfContentOwner").toString());
		        }

		        playlistsDeleteRequest.execute();
		    } catch (Exception e) {
		    	e.printStackTrace();
		    }

	}
	
	static HashMap<String, String> createMonthPlaylists() {
		//HashMap<String, String> playlists = new HashMap<>();
		
		JSONObject monthLists = null;
		JSONObject ytLinkList = null;
		JSONObject ytMonthPlaylists = null;
		try {
			monthLists = new JSONObject(new JSONTokener(new FileReader(new File("F:/deadmau5/php/month_playlists2.txt"))));
			ytLinkList = new JSONObject(new JSONTokener(new FileReader(new File("F:/deadmau5/php/definite_yt_links_DROPIN.txt"))));
			ytMonthPlaylists = new JSONObject(new JSONTokener(new FileReader(new File("F:/deadmau5/ytmonthplaylists.txt"))));
			
			for (int y = 2014; y<=2019; y++) {
				for (int m = 1 /*(y==2016?9:1)*/; m<=12; m++) {
					String monthId = y+"-"+String.format("%02d", m);
					if (monthLists.has(monthId)) {
						JSONArray votm = monthLists.getJSONArray(monthId);
						System.out.println(monthId +": "+votm.length()+" videos...");
						
						//String playlist = createPlaylist(months[m-1]+" "+y);
						//System.out.println("new playlist: "+playlist);
						String playlist = ytMonthPlaylists.getString(monthId);
						//playlists.put(monthId, playlist);
						
						List<Integer> missing = new ArrayList<>();
						for (int i = 0; i<votm.length(); i++) {
							String vidid = votm.getJSONObject(i).getString("id");
							if (ytLinkList.has(vidid)) {
								JSONArray ytLinks = ytLinkList.getJSONArray(vidid);
								for (int k = 0; k<ytLinks.length(); k++) {
									if (ytLinks.isNull(k)) {
										missing.add(votm.getJSONObject(i).getInt("dispnum")); 
										continue;
									}
									String ytLink = ytLinks.getString(k);
									//insertVideoIntoPlaylist(playlist, ytLink);
								}
								
							}
						}
						//Thread.sleep(5000);
						
						updatePlaylist(playlist, ytMonthPlaylists, y, m, missing);
						
					}
					
				}
			}
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		//return playlists;
		return null;
	}
	
	private static void updatePlaylist(String playlistID, JSONObject ytMonthPlaylists, int y, int m, List<Integer> missingVids) {
		String[] months = new DateFormatSymbols().getMonths();
		
		String monthId = y+"-"+String.format("%02d", m);
		
		int monthNext = y * 12 + (m-1) + 1;
		int monthPrev = y * 12 + (m-1) - 1;
		String monthIdNext = (monthNext/12)+"-"+String.format("%02d", (monthNext%12)+1);
		String monthIdPrev = (monthPrev/12)+"-"+String.format("%02d", (monthPrev%12)+1);
		
	  String missing = "";
        if (missingVids.size() > 0) {
        	missing += "(missing ";
        	for (int i = 0; i<missingVids.size(); i++) {
        		missing += "#"+missingVids.get(i);
        		if (i < missingVids.size()-2) {
        			missing += ", ";
        		} else if (i < missingVids.size()-1) {
        			missing += " and ";
        		}
        	}
        	missing += ")\n\n";
        }
        String prevNextLinks = "";
        if (ytMonthPlaylists.has(monthIdPrev)) {
        	prevNextLinks += "Previous: https://www.youtube.com/playlist?list="+ytMonthPlaylists.getString(monthIdPrev)+"\n";
        }
        if (ytMonthPlaylists.has(monthIdNext)) {
        	prevNextLinks += "Next: https://www.youtube.com/playlist?list="+ytMonthPlaylists.getString(monthIdNext)+"\n";
        }
		String desc = missing+"https://deadmau5archive.github.io/timeline/#"+monthId+"\n\n"+prevNextLinks;
        String title = months[m-1]+" "+y;
        
        System.out.println(title);
        System.out.println(desc);
        

	    try {
	    	YouTube youtube = Deadmau5.getYouTubeService(5);
	    	
	        HashMap<String, String> parameters = new HashMap<>();
	        parameters.put("part", "snippet,status");
	        parameters.put("onBehalfOfContentOwner", "");


	        Playlist playlist = new Playlist();
	        playlist.set("id", playlistID);
	        PlaylistSnippet snippet = new PlaylistSnippet();
	        snippet.set("description", desc);
	        snippet.set("title", title);
	        PlaylistStatus status = new PlaylistStatus();
	        status.set("privacyStatus", "public");


	        playlist.setSnippet(snippet);
	        playlist.setStatus(status);

	        YouTube.Playlists.Update playlistsUpdateRequest = youtube.playlists().update(parameters.get("part").toString(), playlist);

	        if (parameters.containsKey("onBehalfOfContentOwner") && parameters.get("onBehalfOfContentOwner") != "") {
	            playlistsUpdateRequest.setOnBehalfOfContentOwner(parameters.get("onBehalfOfContentOwner").toString());
	        }

	        Playlist response = playlistsUpdateRequest.execute();
	        System.out.println(response);
	    } catch (Exception e) {
	    	e.printStackTrace();
	    }
	    //*/
	}

	static void insertVideoIntoPlaylist(String playlistid, String videoid) {
		try {
			YouTube youtube = Deadmau5.getYouTubeService(Deadmau5.credIndex);
	    	
	        HashMap<String, String> parameters = new HashMap<>();
	        parameters.put("part", "snippet");


	        PlaylistItem playlistItem = new PlaylistItem();
	        PlaylistItemSnippet snippet = new PlaylistItemSnippet();
	        snippet.set("playlistId", playlistid);
	        ResourceId resourceId = new ResourceId();
	        resourceId.set("kind", "youtube#video");
	        resourceId.set("videoId", videoid);

	        snippet.setResourceId(resourceId);
	        playlistItem.setSnippet(snippet);

	        YouTube.PlaylistItems.Insert playlistItemsInsertRequest = youtube.playlistItems().insert(parameters.get("part").toString(), playlistItem);

	        if (parameters.containsKey("onBehalfOfContentOwner") && parameters.get("onBehalfOfContentOwner") != "") {
	            playlistItemsInsertRequest.setOnBehalfOfContentOwner(parameters.get("onBehalfOfContentOwner").toString());
	        }

	        PlaylistItem response = playlistItemsInsertRequest.execute();
	        System.out.println(response);
		} catch (GoogleJsonResponseException e) {
        	if (e.getDetails().getMessage().contains("exceeded") && e.getDetails().getMessage().contains("quota")) {
        		Deadmau5.quotaExceeded();
        		insertVideoIntoPlaylist(playlistid, videoid);
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

	}
	
	static String createPlaylist(String title) {
		 try {
		    	YouTube youtube = Deadmau5.getYouTubeService(Deadmau5.credIndexSmall);
		    	
		        HashMap<String, String> parameters = new HashMap<>();
		        parameters.put("part", "snippet,status");


		        Playlist playlist = new Playlist();
		        PlaylistSnippet snippet = new PlaylistSnippet();
		        PlaylistStatus status = new PlaylistStatus();

		        snippet.setTitle(title);
		        //snippet.setDescription("Test description");
		        status.setPrivacyStatus("unlisted");
		        
		        playlist.setSnippet(snippet);
		        playlist.setStatus(status);

		        YouTube.Playlists.Insert playlistsInsertRequest = youtube.playlists().insert(parameters.get("part").toString(), playlist);

		        if (parameters.containsKey("onBehalfOfContentOwner") && parameters.get("onBehalfOfContentOwner") != "") {
		            playlistsInsertRequest.setOnBehalfOfContentOwner(parameters.get("onBehalfOfContentOwner").toString());
		        }

		        Playlist response = playlistsInsertRequest.execute();
		        
		        return response.getId();
		    } catch (Exception e) {
		    	e.printStackTrace();
		    }
		return null;
	}
	
}
