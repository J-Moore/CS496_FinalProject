package com.jonathonwmoore.jwm.cs496finalproject;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.spotify.sdk.android.authentication.AuthenticationClient;
import com.spotify.sdk.android.authentication.AuthenticationRequest;
import com.spotify.sdk.android.authentication.AuthenticationResponse;
import com.spotify.sdk.android.player.Config;
import com.spotify.sdk.android.player.Spotify;
import com.spotify.sdk.android.player.ConnectionStateCallback;
import com.spotify.sdk.android.player.Player;
import com.spotify.sdk.android.player.PlayerNotificationCallback;
import com.spotify.sdk.android.player.PlayerState;
import com.squareup.picasso.Picasso;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class StationActivity extends Activity implements
        PlayerNotificationCallback, ConnectionStateCallback {

    private static final String CLIENT_ID = "57c09fd478c546cfbbbb05309bdc2f7f";
    private static final String CLIENT_SECRET = "thisIsMySecret";
    private static final String REDIRECT_URI = "cs496-final-proj-login://callback";
    private static final String API_TARGET_KEY = "com_jonathonwmoore_jwm_cs496finalproj_api_target";
    private static final String BACKEND_API_URL = "http://cs496-finalproject-1146.appspot.com";
    private static final String ECHONEST_BASE_URL = "http://developer.echonest.com/api/v4/";
    private static final String ECHONEST_API_KEY = "5RXPP7LSQLSY4SJGW";
    private static final String GOOGLE_LOCATION_API_KEY = "AIzaSyA5sBrA-a8fWtNdc4pUYFJXn1Ich0BYKy8";
    private static final int REQUEST_CODE = 810918;
    private String SPOTIFY_ACCESS_TOKEN;
    public static String spotifyUserID, echonestSessionID, backendApiID;
    private String currentTrackSpotifyID = null;
    private boolean PLAYING_MY_OWN = true;

    // Spotify player
    private Player mPlayer;
    private String currentTrack;

    // variables for playlist
    private JSONArray echoNestPlaylist = new JSONArray();
    //private List<String> songPlaylist = new ArrayList();

    //UI elements
    private TextView displayResult, displayInstr;
    private Button pauseButton, nextButton, likeButton;
    private ImageView displayAlbum;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_station);

        displayResult = (TextView)findViewById(R.id.debug_textview);
        displayInstr = (TextView)findViewById(R.id.station_instr);
        displayAlbum = (ImageView) findViewById(R.id.album_image);
        pauseButton = (Button) findViewById(R.id.player_button_play_pause);
        nextButton = (Button) findViewById(R.id.player_button_next);
        likeButton = (Button) findViewById(R.id.player_button_like);
        pauseButton.setTag(1);

        AuthenticationRequest.Builder builder = new AuthenticationRequest.Builder(CLIENT_ID,
                AuthenticationResponse.Type.TOKEN,
                REDIRECT_URI);
        builder.setScopes(new String[]{"user-read-private", "streaming"});
        AuthenticationRequest request = builder.build();
        AuthenticationClient.openLoginActivity(this, REQUEST_CODE, request);

        pauseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final int status = (Integer) v.getTag();
                if (status == 1) {
                    mPlayer.pause();
                    pauseButton.setText("Play");
                    v.setTag(0);
                } else {
                    mPlayer.resume();
                    pauseButton.setText("Pause");
                    v.setTag(1);
                }
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);

        // Check if result comes from the correct activity
        if (requestCode == REQUEST_CODE) {
            AuthenticationResponse response = AuthenticationClient.getResponse(resultCode, intent);
            if (response.getType() == AuthenticationResponse.Type.TOKEN) {
                Config playerConfig = new Config(this, response.getAccessToken(), CLIENT_ID);
                Spotify.getPlayer(playerConfig, this, new Player.InitializationObserver() {
                    @Override
                    public void onInitialized(Player player) {
                        mPlayer = player;
                        mPlayer.addConnectionStateCallback(StationActivity.this);
                        mPlayer.addPlayerNotificationCallback(StationActivity.this);
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        Log.e("StationActivity", "Could not initialize player: " + throwable.getMessage());
                    }
                });

                // get user name
                String getActivity = "getSpotifyUserData";
                String getUrl = "https://api.spotify.com/v1/me";
                SPOTIFY_ACCESS_TOKEN = response.getAccessToken();
                new GetRequestCall().execute("GET", getActivity, getUrl, SPOTIFY_ACCESS_TOKEN);

            } else if (response.getType() == AuthenticationResponse.Type.ERROR) {
                //TODO: Handle error response
            } else {
                //TODO: Most likely auth flow was canceled if it comes here
            }
        }

        // response from CreatePlaylistActivity after creating a station
        if (resultCode == 307) {
            PLAYING_MY_OWN = true;
            String catalogKey = intent.getExtras().get("stationCatalog").toString();
            String stationName = intent.getExtras().get("stationName").toString();
            String stationType = intent.getExtras().get("stationType").toString();
            String typeName = intent.getExtras().get("filter").toString();
            String typeParameter = stationType + "=" + typeName;

            String displayName;
            Integer uScoreIdx = stationName.indexOf("_") + 1;
            displayName = stationName.substring(uScoreIdx);
            displayInstr.setText("Now playing playlist:  " + displayName);

            Toast toast = Toast.makeText(
                    getApplicationContext(),
                    "Please wait while we get your station information",
                    Toast.LENGTH_SHORT
            );
            toast.show();

            // call echo nest to create dynamic playlist session

            try {
                stationType = URLEncoder.encode(stationType, "UTF-8");
                typeName = URLEncoder.encode(typeName, "UTF-8");
                catalogKey = URLEncoder.encode(catalogKey, "UTF-8");
            } catch (Exception e) {
                e.printStackTrace();
            }
            String url = ECHONEST_BASE_URL + "playlist/dynamic/create"
                    + "?api_key=" + ECHONEST_API_KEY
                    + "&format=json"
                    + "&type=catalog-radio"
                    + "&seed_catalog=" + catalogKey
                    + "&" + stationType + "=" + typeName
                    + "&session_catalog=" + catalogKey
                    + "&bucket=id:spotify"
                    + "&bucket=tracks"
                    + "&limit=true";
            Log.d("createDynamicPlaylist", "Calling with: " + url);
            new GetRequestCall().execute("GET", "echonestGetSession", url);
        }

        // response from SavedPlaylistActivity after selecting an existing station
        if (resultCode == 823) {
            PLAYING_MY_OWN = true;
            String catalogKey = intent.getExtras().get("stationCatalog").toString();
            String stationName = intent.getExtras().get("stationName").toString();

            String displayName;
            Integer uScoreIdx = stationName.indexOf("_") + 1;
            displayName = stationName.substring(uScoreIdx);

            displayInstr.setText("Now playing playlist " + displayName);

            Toast toast = Toast.makeText(
                    getApplicationContext(),
                    "Now playing playlist " + stationName,
                    Toast.LENGTH_SHORT
            );
            toast.show();
            // call echo nest to play the station

            try {
                catalogKey = URLEncoder.encode(catalogKey, "UTF-8");
            } catch (Exception e) {
                e.printStackTrace();
            }
            String url = ECHONEST_BASE_URL + "playlist/dynamic/create"
                    + "?api_key=" + ECHONEST_API_KEY
                    + "&format=json"
                    + "&type=catalog-radio"
                    + "&seed_catalog=" + catalogKey
                    + "&session_catalog=" + catalogKey
                    + "&bucket=id:spotify"
                    + "&bucket=tracks"
                    + "&limit=true";
            Log.d("createDynamicPlaylist", "Calling with: " + url);
            new GetRequestCall().execute("GET", "echonestGetSession", url);
        }

        /** playlist was deleted, check that it isn't this one */
        if (resultCode == 1109) {
            //TODO: check that currently playing playlist wasn't deleted
            String catalogKey = intent.getExtras().get("stationCatalog").toString();
            String stationName = intent.getExtras().get("stationName").toString();
            String stationBackendID = intent.getExtras().get("stationBackendID").toString();


        }

        /** return from search for playlist */
        if (resultCode == 228) {
            PLAYING_MY_OWN = false;
            String catalogKey = intent.getExtras().get("stationCatalog").toString();
            String stationName = intent.getExtras().get("stationName").toString();

            String displayName;
            Integer uScoreIdx = stationName.indexOf("_") + 1;
            displayName = stationName.substring(uScoreIdx);

            displayInstr.setText("Now playing playlist " + displayName);

            Toast toast = Toast.makeText(
                    getApplicationContext(),
                    "Now playing playlist " + stationName,
                    Toast.LENGTH_SHORT
            );
            toast.show();
            // call echo nest to play the station

            try {
                catalogKey = URLEncoder.encode(catalogKey, "UTF-8");
            } catch (Exception e) {
                e.printStackTrace();
            }

            // Calling WITHOUT session_catalog set so we do not update the other user's playlist
            String url = ECHONEST_BASE_URL + "playlist/dynamic/create"
                    + "?api_key=" + ECHONEST_API_KEY
                    + "&format=json"
                    + "&type=catalog-radio"
                    + "&seed_catalog=" + catalogKey
                    + "&bucket=id:spotify"
                    + "&bucket=tracks"
                    + "&limit=true";
            Log.d("createDynamicPlaylist", "Calling with: " + url);
            new GetRequestCall().execute("GET", "echonestGetSession", url);
        }

    }

    @Override
    public void onLoggedIn() {
        Log.d("StationActivity", "User logged in");
        Toast toast = Toast.makeText(
                getApplicationContext(),
                "Welcome!",
                Toast.LENGTH_SHORT
        );
        toast.show();
    }

    @Override
    public void onLoggedOut() {
        Log.d("StationActivity", "User logged out");
    }

    @Override
    public void onLoginFailed(Throwable error) {
        Log.d("StationActivity", "Login failed");
    }

    @Override
    public void onTemporaryError() {
        Log.d("StationActivity", "Temporary error occurred");
    }

    @Override
    public void onConnectionMessage(String message) {
        Log.d("StationActivity", "Received connection message: " + message);
    }

    @Override
    public void onPlaybackEvent(EventType eventType, PlayerState playerState) {
        Log.d("StationActivity", "Playback event received: " + eventType.name());
        if (eventType == EventType.TRACK_CHANGED) {
            Toast toast = Toast.makeText(
                    getApplicationContext(),
                    "Now playing next song",
                    Toast.LENGTH_SHORT
            );
            toast.show();

            currentTrackSpotifyID = playerState.trackUri;

            try {
                // playerState.trackUri gives result as "spotify:track:<ID>"
                String trackID = playerState.trackUri.substring(14);

                // asynchronous web request to GET track metadata
                String imageURI = "https://api.spotify.com/v1/tracks/" + trackID;
                new GetRequestCall().execute("GET", "spotifyMetaData", imageURI);
            } catch (Exception e) {
                e.printStackTrace();
            }

            // asynchronous web request to GET next song in playlist
            String nextSongURL = "http://developer.echonest.com/api/v4/playlist/dynamic/next"
                    + "?api_key=" + ECHONEST_API_KEY
                    + "&format=json"
                    + "&session_id=" + echonestSessionID;
            new GetRequestCall().execute("GET", "echonestPlaylistUpdate", nextSongURL);
        }
    }

    @Override
    public void onPlaybackError(ErrorType errorType, String errorDetails) {
        Log.d("StationActivity", "Playback error received: " + errorType.name());
    }

    @Override
    protected void onDestroy() {
        Spotify.destroyPlayer(this);
        super.onDestroy();
    }

    public void userLogout(View view) {
        AuthenticationClient.clearCookies(getApplicationContext());
        Spotify.destroyPlayer(this);
        Intent intent = new Intent(this, MainActivity.class);
        //EditText editText = (EditText) findViewById(R.id.id_goes_here);
        //String message = editText.getText().toString();
        //intent.putExtra(EXTRA_MESSAGE, message);
        spotifyUserID = null;
        backendApiID = null;
        startActivity(intent);
    }

    /** Skip song */
    public void skipSong(View view) {
        mPlayer.skipToNext();
        //TODO: update playlist by indicating song is disliked
        if (PLAYING_MY_OWN) {
            if (currentTrackSpotifyID.length() > 0) {
                String url = ECHONEST_BASE_URL + "playlist/dynamic/feedback"
                        + "?api_key=" + ECHONEST_API_KEY
                        + "&format=json"
                        + "&session_id=" + echonestSessionID
                        + "&skip_song=" + currentTrackSpotifyID;
                Log.d("createDynamicPlaylist", "Calling with: " + url);
                new GetRequestCall().execute("GET", "echonestSendSkip", url);
            }
        } else {
            Toast toast = Toast.makeText(
                    getApplicationContext(),
                    "Song Skipped",
                    Toast.LENGTH_SHORT
            );
            toast.show();
        }
    }

    /** Like song */
    public void likeSong(View view) {
        //TODO: update playlist by indicating song is liked
        if (PLAYING_MY_OWN) {
            if (currentTrackSpotifyID.length() > 0) {
                String url = ECHONEST_BASE_URL + "playlist/dynamic/feedback"
                        + "?api_key=" + ECHONEST_API_KEY
                        + "&format=json"
                        + "&session_id=" + echonestSessionID
                        + "&favorite_song=" + currentTrackSpotifyID;
                Log.d("createDynamicPlaylist", "Calling with: " + url);
                new GetRequestCall().execute("GET", "echonestSendLike", url);
            }
        } else {
            Toast toast = Toast.makeText(
                    getApplicationContext(),
                    "Like functionality only available for your own playlists",
                    Toast.LENGTH_SHORT
            );
            toast.show();
        }
    }

    /** Create a new station */
    public void createStation(View view) {
        Intent i = new Intent(getApplicationContext(), CreatePlaylistActivity.class);
        startActivityForResult(i, 307);
    }

    /** Select Saved or Favorited station */
    public void selectStation(View view) {
        Intent i = new Intent(getApplicationContext(), SavedPlaylistActivity.class);
        startActivityForResult(i, 823);
    }

    /** Search for nearby stations */
    public void searchStation(View view) {
        Intent i = new Intent(getApplicationContext(), SearchStationActivity.class);
        startActivityForResult(i, 228);
    }

    /** Delete an existing station */
    public void deleteStation(View view) {
        Intent i = new Intent(getApplicationContext(), DeleteStationActivity.class);
        startActivityForResult(i, 1109);

    }

    /** sends POST request to add user to backend database */
    public void createUserBackendAccount() {
        Log.d("StationActivity", "createUserBackendAccount() called");
        String url = BACKEND_API_URL + "/user";
        String loc = Double.toString(MainActivity.currentLatitude) + "," + Double.toString(MainActivity.currentLongitude);
        String postParameters;
        try {
            postParameters = "name=" + URLEncoder.encode(spotifyUserID, "UTF-8");
            postParameters += "&last_location=" + URLEncoder.encode(loc, "UTF-8");

        } catch (Exception e) {
            e.printStackTrace();
            postParameters = null;
        }
        new GetRequestCall().execute("POST", "addUserToBackend", url, postParameters);
    }

    /** Creates dynamic playlist (only called when first received new catalog from CreatePlaylistActivity) */
    public void createDynamicPlaylistSession(String key, String type, String typeName) {
        Log.d("StationActivity", "createDynamicPlaylistSession() called with catalog:" + key + ", type:" + type + ", " + typeName);

        try {
            type = URLEncoder.encode(type, "UTF-8");
            typeName = URLEncoder.encode(typeName, "UTF-8");
            key = URLEncoder.encode(key, "UTF-8");
        } catch (Exception e) {
            e.printStackTrace();
        }
        String url = ECHONEST_BASE_URL + "playlist/dynamic/create"
                + "?api_key=" + ECHONEST_API_KEY
                + "&format=json"
                + "&type=" + type + "-radio"
                + "&" + type + "=" + typeName
                + "&session_catalog=" + key
                + "&results=1"
                + "&bucket=id:spotify"
                + "&bucket=tracks"
                + "&limit=true";
        Log.d("createDynamicPlaylist", "Calling with: " + url);
        new GetRequestCall().execute("GET", "echonestPlaylist", url);
    }

    /** sends PUT request to update user location to backend database */
    public void updateUserBackendLocation() {
        Log.d("StationActivity", "updateUserBackendLocation() called");
        String url = BACKEND_API_URL + "/user/" + backendApiID;
        String loc = Double.toString(MainActivity.currentLatitude) + "," + Double.toString(MainActivity.currentLongitude);
        String postParameters;
        try {
            postParameters = "last_location=" + URLEncoder.encode(loc, "UTF-8");

        } catch (Exception e) {
            e.printStackTrace();
            postParameters = null;
        }
        new GetRequestCall().execute("PUT", "updateUserLocation", url, postParameters);
    }

    /** Save the current user to API database */
    public void checkBackendForCurrentUser() {
        Log.d("StationActivity", "checkBackendForCurrentUser() called");
        //TODO: hash userID so they aren't saved to API in the clear

        //Check if user is already in database
        String url = BACKEND_API_URL + "/user/all?name=" + spotifyUserID;
        new GetRequestCall().execute("GET", "checkBackendForUser", url);
    }

    /** class for asynchronous HttpURLConnection call */
    private class GetRequestCall extends AsyncTask<String, Integer, JSONObject> {

        @Override
        protected JSONObject doInBackground(String... params) {
            Log.d("HTTP CALL", params[0] + ", " + params[1] + ", " + params[2]);
            HttpURLConnection conn = null;
            URL url;
            JSONObject responseObject = new JSONObject();
            try {
                url = new URL(params[2]);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod(params[0]);
                if (params[1].equals("getSpotifyUserData")) {
                    conn.setRequestProperty("Authorization", "Bearer " + params[3]);
                }
                if ((params[1].equals("addUserToBackend")) | (params[1].equals("updateUserLocation"))) {
                    conn.setFixedLengthStreamingMode(params[3].getBytes().length);

                    // Send request
                    DataOutputStream outputStream = new DataOutputStream(conn.getOutputStream());
                    outputStream.writeBytes(params[3]);
                    outputStream.flush();
                    outputStream.close();
                }
                String responseString = readStream(conn.getInputStream());
                responseObject = new JSONObject(responseString);
            } catch (Exception e) {
                e.printStackTrace();
                Log.e("CALL CAUSING EXCP", "type:" + params[0] + ", url:" + params[1] + ", return:" + params[2]);
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }

            try {
                responseObject.put(API_TARGET_KEY, params[1]);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            return responseObject;
        }

        private String readStream(InputStream is) {
            BufferedReader reader = null;
            StringBuffer response = new StringBuffer();
            try {
                reader = new BufferedReader(new InputStreamReader(is));
                String line = "";
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
            return response.toString();
        }

        @Override
        protected void onPostExecute(JSONObject result) {
            super.onPostExecute(result);
            String findTarget = "";
            //displayResult.setText(result.toString());
            try {
                findTarget = result.get(API_TARGET_KEY).toString();
            } catch (JSONException e) {
                e.printStackTrace();
            }
            if (findTarget.equals("getSpotifyUserData")) {
                try {
                    spotifyUserID = result.get("id").toString();
                    Log.d("Spotify User ID", spotifyUserID);
                    checkBackendForCurrentUser();
                } catch (JSONException e) {
                    //TODO: DISPLAY MESSAGE - UNABLE TO GET USER DATA, WILL NOT BE ABLE TO SAVE ANY PLAYLISTS
                    e.printStackTrace();
                }
            }

            else if (findTarget.equals("addUserToBackend")) {
                // response from backend after requesting user be added
                try {
                    backendApiID = result.get("id").toString();
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }

            else if (findTarget.equals("updateUserLocation")) {
                // response from backend after requesting user location be updated
                //TODO: what if an error occurs here?
            }

            else if (findTarget.equals("checkBackendForUser")) {
                result.remove(API_TARGET_KEY);
                if (result.length() > 0) {
                    Log.d("Spotify Response", "spotify user found in backend, updating account");
                    //displayResult.setText("id " + spotifyUserID + " found!");

                    // user already has been added to database
                    try {
                        Iterator<?> keys = result.keys();
                        String key = keys.next().toString();
                        backendApiID = result.getJSONObject(key).get("id").toString();
                        Log.d("Backend API ID", backendApiID);
                        updateUserBackendLocation();
                    } catch (JSONException e) {
                        e.printStackTrace();
                        //TODO: DISPLAY MESSAGE - unable to get user ID, unable to save playlists
                    }
                } else {
                    // user has not been added to backend database yet
                    Log.d("Spotify Response", "spotify user not found, creating backend account");
                    Toast toast = Toast.makeText(
                            getApplicationContext(),
                            "User not found, creating backend entry",
                            Toast.LENGTH_SHORT
                    );
                    toast.show();
                    createUserBackendAccount();
                }
            }

            else if (findTarget.equals("echonestGetSession")) {
                parseSession(result);
            }

            else if (findTarget.equals("echonestPlaylist")) {
                parsePlaylist(result);
            }

            else if (findTarget.equals("echonestPlaylistUpdate")) {
                parseNextSong(result);
            }

            else if (findTarget.equals("spotifyMetaData")) {
                parseMetadata(result);
            }

            else if (findTarget.equals("echonestSendLike")) {
                Toast toast = Toast.makeText(
                        getApplicationContext(),
                        "Song Liked",
                        Toast.LENGTH_SHORT
                );
                toast.show();
            }

            else if (findTarget.equals("echonestSendSkip")) {
                Toast toast = Toast.makeText(
                        getApplicationContext(),
                        "Song Skipped",
                        Toast.LENGTH_SHORT
                );
                toast.show();
            }

            else {
                Log.d("HTTP Async response", "Error parsing result from server: " + result.toString());
            }
        }
    }

    /** upon first getting session ID for starting playlist, we will call dynamic/next to get first song */
    public void parseSession(JSONObject resultobj) {
        try {
            String message = resultobj.getJSONObject("response").getJSONObject("status").getString("message");
            if (message.toLowerCase().equals("success")) {
                echonestSessionID = resultobj.getJSONObject("response").getString("session_id");
                String nextSongURL = "http://developer.echonest.com/api/v4/playlist/dynamic/next"
                        + "?api_key=" + ECHONEST_API_KEY
                        + "&format=json"
                        + "&session_id=" + echonestSessionID;
                new GetRequestCall().execute("GET", "echonestPlaylistUpdate", nextSongURL);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    /** starts player based on song */

    public void parsePlaylist(JSONObject resultobj) {
        String currentTrack = "";
        try {
            String message = resultobj.getJSONObject("response").getJSONObject("status").getString("message");
            if (message.toLowerCase().equals("success")) {
                mPlayer.clearQueue();
                mPlayer.skipToNext();
                echonestSessionID = resultobj.getJSONObject("response").get("session_id").toString();
                echoNestPlaylist = resultobj.getJSONObject("response").getJSONArray("songs");
                for (int i = 0; i < echoNestPlaylist.length(); i++) {
                    currentTrack = echoNestPlaylist.getJSONObject(i).getJSONArray("tracks").getJSONObject(0).getString("foreign_id");
                    //songPlaylist.add(currentTrack);
                    mPlayer.queue(currentTrack);
                }
            } else {
                Toast toast = Toast.makeText(
                        getApplicationContext(),
                        "ServerError: Unable to update radio playlist.",
                        Toast.LENGTH_SHORT
                );
                toast.show();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    /** queues up next song */
    public void parseNextSong(JSONObject resultobj) {
        String currentTrack = "";
        try {
            String message = resultobj.getJSONObject("response").getJSONObject("status").getString("message");
            if (message.toLowerCase().equals("success")) {
                echoNestPlaylist = resultobj.getJSONObject("response").getJSONArray("songs");
                for (int i = 0; i < echoNestPlaylist.length(); i++) {
                    currentTrack = echoNestPlaylist.getJSONObject(i).getJSONArray("tracks").getJSONObject(0).getString("foreign_id");
                    //songPlaylist.add(currentTrack);
                    mPlayer.queue(currentTrack);
                }
            } else {
                Toast toast = Toast.makeText(
                        getApplicationContext(),
                        "Unable to create a radio playlist from that artist name.  Please try again.",
                        Toast.LENGTH_SHORT
                );
                toast.show();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    /** parsing spotify web API metadata */
    public void parseMetadata(JSONObject resultobj) {
        String currentTrack = "";
        String currentAlbum = "";
        String currentArtist = "";
        String currentAlbumImageURL = "";
        URL imageURL;

        // get track name
        try {
            currentTrack = "Track: " + resultobj.get("name").toString();
        } catch (Exception e) {
            e.printStackTrace();
            currentTrack = "Unable to get Track Name";
        }

        // get album name
        try {
            currentAlbum = "Album: " + resultobj.getJSONObject("album").get("name").toString();
        } catch (Exception e) {
            e.printStackTrace();
            currentAlbum = "Unable to get Album Name";
        }

        // get artist name
        try {
            currentArtist = "Artist: " + resultobj.getJSONArray("artists").getJSONObject(0).get("name").toString();
        } catch (Exception e) {
            e.printStackTrace();
            currentArtist = "Unable to get Artist Name";
        }

        // get album image URL
        try {
            currentAlbumImageURL = resultobj.getJSONObject("album").getJSONArray("images").getJSONObject(0).get("url").toString();
        } catch (Exception e) {
            e.printStackTrace();
            currentAlbumImageURL = null;
        }

        // display artist information
        displayResult.setText(
                currentTrack + "\n" +
                        currentAlbum + "\n" +
                        currentArtist
        );

        // Picasso library will load image from URL with this line
        Picasso.with(StationActivity.this).load(currentAlbumImageURL).into(displayAlbum);
    }

}