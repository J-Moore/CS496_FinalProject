package com.jonathonwmoore.jwm.cs496finalproject;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

public class CreatePlaylistActivity extends AppCompatActivity {

    private static final String BACKEND_API_URL = "http://cs496-finalproject-1146.appspot.com";
    private static final String ECHONEST_BASE_URL = "http://developer.echonest.com/api/v4/";
    private static final String ECHONEST_API_KEY = "5RXPP7LSQLSY4SJGW";
    private static final String API_TARGET_KEY = "com_jonathonwmoore_jwm_cs496finalproj_api_target";
    private String STATION_CATALOG_KEY, STATION_NAME;
    private String albumOrSong;


    // UI elements
    private Button submitBySongButton, submitByArtistButton;
    private EditText editTextName, editTextSong, editTextArtist;
    private TextView displayResult;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_playlist);

        submitBySongButton = (Button)findViewById(R.id.create_button_song);
        submitByArtistButton = (Button)findViewById(R.id.create_button_artist);
        editTextName = (EditText)findViewById(R.id.create_station_name);
        editTextSong = (EditText)findViewById(R.id.create_edittext_song);
        editTextArtist = (EditText)findViewById(R.id.create_edittext_artist);
        displayResult = (TextView)findViewById(R.id.debug_create_textview);

    }

    /** submit by song button pressed */
    public void submitSong(View view) {
        albumOrSong = "song";
        Log.d("CreatePlaylistActivity", "submitSong() called");
        // validate song is not empty
        String name = editTextName.getText().toString();
        String song = editTextSong.getText().toString();
        boolean goodData = true;
        editTextSong.setError(null);
        editTextName.setError(null);

        if (name.length() == 0) {
            editTextName.setError("name is required");
            goodData = false;
        }
        if (song.length() == 0) {
            editTextSong.setError("song name is required");
            goodData = false;
        }

        if (goodData) {

            // search for spotify song ID based on name entered
            // then use that spotify ID to create a tasteprofile with EchoNest
            // because EchoNest can do artists by name but not songs...
            String encodedSongName = "";
            try {
                encodedSongName = URLEncoder.encode(song, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
            String surl = "https://api.spotify.com/v1/search?"
                    + "q=" + encodedSongName
                    + "type=track";
            new GetRequestCall().execute("GET", "lookupSpotifyTrackData", surl);

            // create GET url for Echo Nest
            String url = ECHONEST_BASE_URL + "tasteprofile/create";
            String postParameters;
            try {
                postParameters = "api_key=" + URLEncoder.encode(ECHONEST_API_KEY, "UTF-8");
                postParameters += "&format=json";
                postParameters += "&name=" + StationActivity.backendApiID + "_" + URLEncoder.encode(name, "UTF-8");
                postParameters += "&type=general";

            } catch (Exception e) {
                e.printStackTrace();
                postParameters = null;
            }
            new GetRequestCall().execute("POST", "createTasteProfile", url, postParameters);
        }
    }

    public void updateSongTrack(JSONObject jobj) {
        String songTrack = "";

        Log.d("updateSongTrack", jobj.toString());

    }

    /** submit by artist button pressed */
    public void submitArtist(View view) {
        albumOrSong = "artist";
        Log.d("CreatePlaylistActivity", "submitArtist() called");
        // validate artist is not empty
        String name = editTextName.getText().toString();
        String artist = editTextArtist.getText().toString();
        boolean goodData = true;
        editTextArtist.setError(null);
        editTextName.setError(null);

        if (name.length() == 0) {
            editTextName.setError("name is required");
            goodData = false;
        }
        if (artist.length() == 0) {
            editTextArtist.setError("artist name is required");
            goodData = false;
        }

        if (goodData) {
            // create GET url for Echo Nest
            String url = ECHONEST_BASE_URL + "tasteprofile/create";
            String postParameters;
            try {
                postParameters = "api_key=" + URLEncoder.encode(ECHONEST_API_KEY, "UTF-8");
                postParameters += "&format=json";
                postParameters += "&name=" + StationActivity.backendApiID + "_" + URLEncoder.encode(name, "UTF-8");
                postParameters += "&type=general";

            } catch (Exception e) {
                e.printStackTrace();
                postParameters = null;
            }
            new GetRequestCall().execute("POST", "createTasteProfile", url, postParameters);
        }
    }

    /** We have just gotten a response from Echo Nest after creating a Taste Profile and now must
     * 1) save data to backend
     * 2) start playlist with dynamic
      * @param obj - JSON response from Echo Nest
     */
    public void parseNewCatalogResponse(JSONObject obj) {

        Log.d("CreatePlaylistActivity", "parseNewCatalogResponse() called with " + obj.toString());
        String type = "";
        String artistName = editTextArtist.getText().toString();
        String songName = editTextSong.getText().toString();
        // parse Catalog Key
        try {
            STATION_CATALOG_KEY = obj.getJSONObject("response").get("id").toString();
            Log.d("STATION_CATALOG_KEY", STATION_CATALOG_KEY);
            STATION_NAME = obj.getJSONObject("response").get("name").toString();
            Log.d("STATION_NAME", STATION_NAME);
            type = obj.getJSONObject("response").get("type").toString();
            Log.d("STATION_TYPE", type);
            if (type.equals("artist")) {
                Log.d("STATION_ARTIST", artistName);
                // give Catalog it's first item as specified by user
                updateTasteProfile("artist", artistName);
            }
            if (type.equals("song")) {
                Log.d("STATION_SONG", songName);
                // give Catalog it's first item as specified by user
                updateTasteProfile("song", songName);
            }
        } catch (JSONException e) {
            e.printStackTrace();
            Toast toast = Toast.makeText(
                    getApplicationContext(),
                    "Error parsing result from server",
                    Toast.LENGTH_SHORT
            );
            toast.show();
        }

        // save Catalog Key to backend
        String url = BACKEND_API_URL + "/station";
        String loc = Double.toString(MainActivity.currentLatitude) + "," + Double.toString(MainActivity.currentLongitude);
        String postParameters;
        try {
            postParameters = "name=" + URLEncoder.encode(STATION_NAME, "UTF-8");
            postParameters += "&latlon=" + URLEncoder.encode(loc, "UTF-8");
            postParameters += "&catalog_key=" + URLEncoder.encode(STATION_CATALOG_KEY, "UTF-8");
            postParameters += "&owner=" + URLEncoder.encode(StationActivity.backendApiID, "UTF-8");
        } catch (Exception e) {
            e.printStackTrace();
            postParameters = null;
        }
        Log.d("SAVING_CATALOG", "POST:" + url + ", PARAMETERS:" + postParameters);
        new GetRequestCall().execute("POST", "addStationToBackend", url, postParameters);

        // return catalog ID to StationActivity.java
        Intent i = new Intent();
        i.putExtra("stationName", STATION_NAME);
        i.putExtra("stationCatalog", STATION_CATALOG_KEY);
        i.putExtra("stationType", albumOrSong);
        if (albumOrSong.equals("artist")) {
            i.putExtra("filter", artistName);
        }
        if (albumOrSong.equals("song")) {
            i.putExtra("filter", songName);
        }
        setResult(307, i);

        finish();

    }

    public void updateTasteProfile(String artsontype, String artson) {
        JSONObject updateObject = new JSONObject();
        JSONObject itemObject = new JSONObject();
        if (artsontype.equals("artist")) {
            try {
                itemObject.put("artist_name", artson);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        else if (artsontype.equals("song")) {
            try {
                itemObject.put("song_name", artson);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        try {
            updateObject.put("action", "update");
            updateObject.put("item", itemObject);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        String url = ECHONEST_BASE_URL + "catalog/update";
        String postParameters = "api_key=" + ECHONEST_API_KEY
                + "data_type=json"
                + "fromat=json"
                + "id=" + STATION_CATALOG_KEY
                + "data=" + updateObject.toString();
        Log.d("update JSON", updateObject.toString());

        new GetRequestCall().execute("POST", "updateCatalog", url, postParameters);
    }

    /** class for asynchronous HttpURLConnection call */
    private class GetRequestCall extends AsyncTask<String, Integer, JSONObject> {

        @Override
        protected JSONObject doInBackground(String... params) {
            Log.d("HTTP CALL", params[0] + ", " + params[1] + ", " + params[2]);
            HttpURLConnection conn = null;
            URL url;
            JSONObject responseObject = new JSONObject();
            JSONObject testObj = new JSONObject();
            String responseString = "";
            try {
                url = new URL(params[2]);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod(params[0]);   // POST or GET
                if (params[0].equals("POST")) {
                    conn.setFixedLengthStreamingMode(params[3].getBytes().length);

                    // Send request
                    DataOutputStream outputStream = new DataOutputStream(conn.getOutputStream());
                    outputStream.writeBytes(params[3]);
                    outputStream.flush();
                    outputStream.close();
                }
                responseString = readStream(conn.getInputStream());
            } catch (Exception e) {
                e.printStackTrace();
                Log.e("CALL CAUSING EXCP", "type:" + params[0] + ", url:" + params[1] + ", return:" + params[2]);
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }

            // turn response string into JSON object
            try {
                responseObject = new JSONObject(responseString);
            } catch (JSONException e) {
                e.printStackTrace();
                Log.e("JSON Parser", "Error parsing JSON data " + e.toString());
                responseObject = testJsonString(responseString);
            }

            // add key to tell us which method made the request
            try {
                responseObject.put(API_TARGET_KEY, params[1]);
            } catch (JSONException e) {
                e.printStackTrace();
                Log.e("JSON Error", "Error affixing method tag");
            }
            return responseObject;
        }

        /** because there are somehow hidden characters in front of some of the API responses */
        private JSONObject testJsonString(String sn) {
            JSONObject jObj = new JSONObject();
            int idx = 0;
            int strLen = sn.length();
            while (idx < strLen) {
                try {
                    jObj = new JSONObject(sn.substring(idx));
                    break;
                } catch (JSONException e) {
                    Log.e("JSON Parser", "Error parsing data [" + e.getMessage() + "] " + sn);
                }
                idx++;
            }

            Log.d("Successful JSON parse", "String sn[" + Integer.toString(idx) + "] to " + jObj.toString());
            return jObj;
        }

        private String readStream(InputStream is) {
            BufferedReader reader = null;
            StringBuilder response = new StringBuilder();
            try {
                reader = new BufferedReader(new InputStreamReader(is, "iso-8859-1"), 8);
                String line = null;
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
            if (findTarget.equals("createTasteProfile")) {
                Log.d("CreatePlaylistActivity", "taste profile received from Echo Nest");
                Log.d("results object", result.toString());
                parseNewCatalogResponse(result);
            }

            // saving new station to backend API
            else if (findTarget.equals("addStationToBackend")) {
                Toast toast = Toast.makeText(
                        getApplicationContext(),
                        "New Station Saved",
                        Toast.LENGTH_SHORT
                );
                toast.show();
            }

            // track data back from spotify GET request
            else if (findTarget.equals("lookupSpotifyTrackData")) {
                updateSongTrack(result);
            }

            else {
                Log.d("HTTP Async response", "Error parsing result from server: " + result.toString());
            }
        }
    }
}
