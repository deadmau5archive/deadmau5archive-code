import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.DateFormatSymbols;
import java.util.HashMap;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.ChannelListResponse;
import com.google.api.services.youtube.model.Playlist;
import com.google.api.services.youtube.model.PlaylistListResponse;
import com.google.api.services.youtube.model.PlaylistSnippet;
import com.google.api.services.youtube.model.PlaylistStatus;
import com.google.api.services.youtube.model.Video;
import com.google.api.services.youtube.model.VideoStatus;


public class ChannelManager {

	//for managing lots of videos.
	
	static final String TWITCH_LTF = "PLEh04lts8PqRVN_eELVbdMSgCHKrELjUn";
	static final String TWITCH_FTL = "PLEh04lts8PqTG5jRBe1XXOKw5TPYKkiqc";
	static final String MIXER_FTL = "PLEh04lts8PqQ6SV7jYtQV7arNXIkuvlRT";
	
	public static void main(String[] args) {
		
		//listChannelInfo(5);
		
		//changeTwitchVideosVisibility("private");
		
		String newPlaylistVisibility = "unlisted";
		changeTwitchPlaylistsVisibility(newPlaylistVisibility);
		changeMixerPlaylistsVisibility(newPlaylistVisibility);
		updatePlaylist(TWITCH_LTF, newPlaylistVisibility, "All twitch streams (latest to first)");
		updatePlaylist(TWITCH_FTL, newPlaylistVisibility, "All twitch streams (first to latest)");
		updatePlaylist(MIXER_FTL, newPlaylistVisibility, "Mixer streams");
		
		
	}
	
	static void listChannelInfo(int credIndex) {
		try {
			YouTube youtubeService = YtTest.getYouTubeService(credIndex);
	        // Define and execute the API request
	        YouTube.Channels.List request = youtubeService.channels()
	            .list("snippet,contentDetails,statistics");
	        ChannelListResponse response = request.setMine(true).execute();
	        System.out.println(response);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	static void changeMixerVideosVisibility(String vis) {
	}  
	
	static void changeTwitchPlaylistsVisibility (String vis) {

		//HashMap<String, String> playlists = new HashMap<>();
		
		JSONObject ytMonthPlaylists = null;
		try {
			//String ytmonthplaylists = "F:/deadmau5/ytmonthplaylists.txt";
			String ytmonthplaylists = "F:/mixer/web/twitch/ytmonthplaylists.txt";
			ytMonthPlaylists = new JSONObject(new JSONTokener(new FileReader(new File(ytmonthplaylists)))); 
			
			for (int y = 2014; y<=2019; y++) {
				for (int m = 1 /*(y==2016?9:1)*/; m<=12; m++) {
					String monthId = y+"-"+String.format("%02d", m);
					
					if (ytMonthPlaylists.has(monthId)) {
						String playlist = ytMonthPlaylists.getString(monthId);
						updatePlaylist(playlist, vis, monthIdToPlaylistTitle(monthId));
					}
					
				}
			}
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		
	
	}
	
	
	private static String monthIdToPlaylistTitle(String monthId) {
		String[] split = monthId.split("-");
		int m = Integer.parseInt(split[1]);
		String[] months = new DateFormatSymbols().getMonths();
		return months[m-1]+" "+split[0];
	}

	static void changeMixerPlaylistsVisibility (String vis) {

		String path = "F:/mixer/J/monthplaylists";
		File[] files = new File(path).listFiles();
		for (File f: files) {
			try {
				String id = new String(Files.readAllBytes(Paths.get(f.getAbsolutePath()))).trim();
				String monthid = f.getName().substring(0, f.getName().length()-4);
				updatePlaylist(id, vis, monthIdToPlaylistTitle(monthid));
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
	
	}
	
	static void changeTwitchVideosVisibility(String vis) {

		try {
			
			String pathPrefix = "D:/Stuff/youtube/deadmau5archive/F/deadmau5/";
			
			JSONArray vids = new JSONArray(new JSONTokener(new FileReader(new File(pathPrefix+"hls_array.txt"))));
			JSONObject playlistinfo = new JSONObject(new JSONTokener(new FileReader(new File(pathPrefix+"playlistinfo.txt"))));
			JSONObject ytLinks = new JSONObject(new JSONTokener(new FileReader(new File(pathPrefix+"php/definite_yt_links_DROPIN.txt"))));
			
			int vc = 0;
			for (int i = 0; i<vids.length(); i++) {
				JSONObject vid = vids.getJSONObject(i);
				int displaynum = vids.length() - i;
				String id = vid.getString("id");
				JSONObject playlistInfoVid = playlistinfo.getJSONObject(vid.getString("hlsurl"));
				
				JSONArray ytLinksVid = ytLinks.getJSONArray(id);
				if (ytLinksVid.isNull(0)) {
					continue; //this is a non processed video
				}
				
				
				for (int p = 0; p<playlistInfoVid.getInt("numParts"); p++) {
					
					String cNormalVersion = ytLinksVid.getString(p);
					String cUnblockedVersion = null;
					File muteUnblockFile = new File(pathPrefix+"copystrike/dropin/"+p+"-"+vid.getString("hlsurl")+".MUTE_ALL.txt");
					if (muteUnblockFile.exists()) {
						cUnblockedVersion = new String(Files.readAllBytes(Paths.get(muteUnblockFile.getAbsolutePath()))).trim();
					}
					
					//if (displaynum >= 65) continue; //TODO remove 
					System.out.println("#"+displaynum+" p="+p+" yt="+cNormalVersion);
					updateVideoPrivacy(cNormalVersion, vis);
					vc++;
					
					if (cUnblockedVersion != null) {
						System.out.println("[UNBLOCK] #"+displaynum+" p="+p+" yt="+cUnblockedVersion);
						updateVideoPrivacy(cUnblockedVersion, vis);
					}
				}
				
			}
			System.out.println(vc);
			
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	
	}
	
	private static void updatePlaylistPrivacy(String playlistId, String priv) {
		updatePlaylist(playlistId, priv, null);
	}

	private static void updatePlaylist(String playlistId, String priv, String title) {
		 try {
			 YouTube youtube =  YtTest.getYouTubeService(YtTest.credIndex);
		    	
		        HashMap<String, String> parameters = new HashMap<>();
		        parameters.put("part", "snippet,status");
		        //parameters.put("onBehalfOfContentOwner", "");

		        Playlist playlist = new Playlist();
		        playlist.setId(playlistId);
		        
		        //fetch snippet so we keep description (and title, if not given)
		        // because for some fucking reason we can't just update the privacy without giving the snippet. thanks yt
		        YouTube.Playlists.List request = youtube.playlists().list("snippet");
		        PlaylistListResponse listresponse = request.setId(playlistId).execute();
		        PlaylistSnippet snippet = listresponse.getItems().get(0).getSnippet();
		        
		        System.out.println("old title = "+snippet.getTitle());
		        if (title != null) snippet.setTitle(title);
		        
		        PlaylistStatus status = new PlaylistStatus();
		        status.set("privacyStatus", priv);

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
	}
	
	
	static void updateVideoPrivacy(String videoId, String priv) {
		
		try {
			YouTube youtube =  YtTest.getYouTubeService(YtTest.credIndex);
	    	
			HashMap<String, String> parameters = new HashMap<>();
	        parameters.put("part", "status");


	        Video video = new Video();
	        video.setId(videoId);
	        VideoStatus status = new VideoStatus();
	        status.setPrivacyStatus(priv);
	        status.setEmbeddable(true);

	        video.setStatus(status);

	        YouTube.Videos.Update videosUpdateRequest = youtube.videos().update(parameters.get("part").toString(), video);

	        Video response = videosUpdateRequest.execute();
	        System.out.println(response);

		} catch (GoogleJsonResponseException e) {
        	if (e.getDetails().getMessage().contains("exceeded") && e.getDetails().getMessage().contains("quota")) {
        		YtTest.quotaExceeded();
        		updateVideoPrivacy(videoId, priv);
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
	
}
