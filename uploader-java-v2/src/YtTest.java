import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.googleapis.media.MediaHttpUploader;
import com.google.api.client.googleapis.media.MediaHttpUploaderProgressListener;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.InputStreamContent;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.Playlist;
import com.google.api.services.youtube.model.PlaylistItem;
import com.google.api.services.youtube.model.PlaylistItemSnippet;
import com.google.api.services.youtube.model.PlaylistSnippet;
import com.google.api.services.youtube.model.PlaylistStatus;
import com.google.api.services.youtube.model.ResourceId;
import com.google.api.services.youtube.model.Video;
import com.google.api.services.youtube.model.VideoSnippet;
import com.google.api.services.youtube.model.VideoStatus;
import com.google.common.collect.Lists;

public class YtTest {

	public static void main(String[] args) {

		//http://localhost:8080/Callback
		//genRefreshToken(2+4);
		//testUpload();

		//System.out.println("YtTest");
		//genRefreshToken(1);
		

	}
	
	static void testUpload() {
		VideoMetadata m = new VideoMetadata();
		m.title = "test";
		m.desc = "";
		m.tags = new ArrayList<>();
		uploadToYt(m, "F:\\test\\out.mp4");
	}
	
	static void genRefreshToken(int credIdx) {
		try {
			List<String> scopes = Lists.newArrayList(
					"https://www.googleapis.com/auth/youtube",
					"https://www.googleapis.com/auth/youtube.upload",
					"https://www.googleapis.com/auth/youtube.force-ssl",
					"https://www.googleapis.com/auth/youtube.readonly");
	        //Credential credential = YtAuth.authorize(scopes, "whatever", "gamma"); //delete "cred/whatever" first!
			
			GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
	                HTTP_TRANSPORT, JSON_FACTORY, CLIENT_ID[credIdx], CLIENT_SECRET[credIdx], scopes)//.setCredentialDataStore(datastore)
	        		.setAccessType("offline").setApprovalPrompt("force")
	                .build();

	        // Build the local server and bind it to port 8080
	        LocalServerReceiver localReceiver = new LocalServerReceiver.Builder().setPort(8080).build();

	        // Authorize.
	        Credential credential = new AuthorizationCodeInstalledApp(flow, localReceiver).authorize("user");
			
	        System.out.println("ref "+credential.getRefreshToken());
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	static class VideoMetadata {
		String title;
		String desc;
		List<String> tags;
		
		String privacy = "private";
		
		boolean notifySubscribers = TsStreamToVideo.NOTIFY_SUBS_ON_UPLOAD;
	}

	private static final String VIDEO_FILE_FORMAT = "video/*";

	static String uploadToYt(VideoMetadata metadata, String filename) {
		
		System.out.println("[uploadToYt] title=\""+metadata.title+"\" filename="+filename);

		// This OAuth 2.0 access scope allows an application to upload files
		// to the authenticated user's YouTube channel, but doesn't allow
		// other types of access.

		try {

			YouTube youtube = getYouTubeService(credIndex);

			// Add extra information to the video before uploading.
			Video videoObjectDefiningMetadata = new Video();

			// Set the video to be publicly visible. This is the default
			// setting. Other supporting settings are "unlisted" and "private."
			VideoStatus status = new VideoStatus();
			status.setPrivacyStatus(metadata.privacy);
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
					// new FileInputStream(new
					// File(TEMP+vid.get("hlsurl").toString()+".ts")));
					new FileInputStream(vidFile));
			// new FileInputStream(new File(TEMP+"asdfasd.mp4")));

			// Insert the video. The command sends three arguments. The first
			// specifies which information the API request is setting and which
			// information the API response should return. The second argument
			// is the video resource that contains metadata about the new video.
			// The third argument is the actual video content.
			YouTube.Videos.Insert videoInsert = youtube.videos().insert("snippet,statistics,status",
					videoObjectDefiningMetadata, mediaContent);
			
			videoInsert.setNotifySubscribers(metadata.notifySubscribers);

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
			System.out.println("Video file size: " + vidBytes + " bytes ("
					+ String.format("%.03f", vidBytes / 1000000000d) + " GB)");
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
						System.out.print("\rUploading\tbytes uploaded: " + uploader.getNumBytesUploaded() + " ("
								+ String.format("%.03f", gb) + " GB)\tprogress: "
								+ String.format("%.03f", percent * 100) + "%");
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
			// System.out.println(" - Video Count: " +
			// returnedVideo.getStatistics().getViewCount());

			return returnedVideo.getId();

		} catch (GoogleJsonResponseException e) {
			
			// why tf does it crash here with NullPointerException and then EXIT THE PROGRAM???
			try {
				if (e != null && e.getDetails() != null && e.getDetails().getMessage() != null &&
						e.getDetails().getMessage().contains("exceeded") && e.getDetails().getMessage().contains("quota")) {
	        		quotaExceeded();
	        		return uploadToYt(metadata, filename);
	        	} else {
	        		System.out.println(e);
	        		System.out.println(e.getMessage());
	        		e.printStackTrace();
	        		
	        		Thread.sleep(5000);
	        		System.out.println("RETRYING UPLOAD ("+metadata.title+")...");
	        		return uploadToYt(metadata, filename);
	        	}
			} catch (Exception e2) {
				System.err.println("what the actual fuck");
				System.err.println(e == null ? "e is null!" : "e is not null");
				e.printStackTrace();
				e2.printStackTrace();
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
	
	static void updateVideo(String videoId, YtTest.VideoMetadata meta) {
		System.out.println("[updateVideo] ytid="+videoId+" title=\""+meta.title+"\" privacy="+meta.privacy);
		
		try {
			YouTube youtube = getYouTubeService(credIndex);

			HashMap<String, String> parameters = new HashMap<>();
			parameters.put("part", "snippet,status");

			Video video = new Video();
			video.setId(videoId);
			VideoSnippet snippet = new VideoSnippet();
			snippet.setCategoryId("22"); // People & Blogs
			snippet.setTitle(meta.title);
			snippet.setDescription(meta.desc);
			snippet.setTags(meta.tags);
			VideoStatus status = new VideoStatus();
			status.setPrivacyStatus(meta.privacy);
			status.setEmbeddable(true);

			video.setSnippet(snippet);
			video.setStatus(status);

			YouTube.Videos.Update videosUpdateRequest = youtube.videos().update(parameters.get("part").toString(),
					video);

			Video response = videosUpdateRequest.execute();
			System.out.println(response);

		} catch (GoogleJsonResponseException e) {
			if (e.getDetails().getMessage().contains("exceeded") && e.getDetails().getMessage().contains("quota")) {
				quotaExceeded();
				updateVideo(videoId, meta);
			} else {
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
	
	static String createPlaylist(String title, String privacy) {
		System.out.println("[createPlaylist] title="+title+" privacy="+privacy);
		 try {
		    	YouTube youtube = getYouTubeService(credIndex);
		    	
		        HashMap<String, String> parameters = new HashMap<>();
		        parameters.put("part", "snippet,status");


		        Playlist playlist = new Playlist();
		        PlaylistSnippet snippet = new PlaylistSnippet();
		        PlaylistStatus status = new PlaylistStatus();

		        snippet.setTitle(title);
		        //snippet.setDescription("Test description");
		        status.setPrivacyStatus(privacy);
		        
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
	
	static void insertVideoIntoPlaylist(String playlistid, String videoid) {
		System.out.println("[insertVideoIntoPlaylist] video "+videoid+" --> playlist "+playlistid);
		try {
			YouTube youtube = getYouTubeService(credIndex);
	    	
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
        		quotaExceeded();
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
	
	static void quotaExceeded() {
    	System.err.println("ERROR: QUOTA EXCEEDED...");
    	int waitTime = 2000;
		try {
			Thread.sleep(waitTime);
		} catch (InterruptedException e1) {
			e1.printStackTrace();
		}
		
		System.out.println("switching credentials...");
		credIndex = (credIndex + 1) % PROJ_NAMES.length;
	}
	
	static int credIndex = 0;
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

	public static final HttpTransport HTTP_TRANSPORT = new NetHttpTransport();

	public static final JsonFactory JSON_FACTORY = new JacksonFactory();

	public static YouTube getYouTubeService(int credIdx) throws IOException {

		System.out.println("[YT Auth] using '" + PROJ_NAMES[credIdx] + "' credentials");
		Credential credential = new GoogleCredential.Builder().setJsonFactory(JSON_FACTORY)
				.setTransport(HTTP_TRANSPORT).setClientSecrets(CLIENT_ID[credIdx], CLIENT_SECRET[credIdx])
				.build().setRefreshToken(REFRESH_TOKEN[credIdx]);

		// This object is used to make YouTube Data API requests.
		return new YouTube.Builder(HTTP_TRANSPORT, JSON_FACTORY, setHttpTimeout(credential))
				.setApplicationName("youtube-cmdline-uploadvideo-sample").build();
	}

	private static HttpRequestInitializer setHttpTimeout(final HttpRequestInitializer requestInitializer) {
		return new HttpRequestInitializer() {
			@Override
			public void initialize(HttpRequest httpRequest) throws IOException {
				requestInitializer.initialize(httpRequest);
				// System.out.println("connect_timeout:
				// "+httpRequest.getConnectTimeout()+", read_timeout:
				// "+httpRequest.getReadTimeout());
				httpRequest.setConnectTimeout(5 * 60000); // 5 minutes connect
															// timeout
				httpRequest.setReadTimeout(5 * 60000); // 5 minutes read timeout
				// System.out.println("new_connect_timeout:
				// "+httpRequest.getConnectTimeout()+", new_read_timeout:
				// "+httpRequest.getReadTimeout());
			}
		};
	}

}
