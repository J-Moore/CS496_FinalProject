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
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;

public class SearchStationActivity extends AppCompatActivity {

    private static final String API_TARGET_KEY = "com_jonathonwmoore_jwm_cs496finalproj_api_target";
    private static final String BACKEND_API_URL = "http://cs496-finalproject-1146.appspot.com";
    private ArrayList<String> STATION_CATALOG_KEY = new ArrayList<String>();
    private ArrayList<String> STATION_NAME = new ArrayList<String>();

    //UI elements
    private LinearLayout screenLayout;
    private TextView screenInstr;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search_station);

        screenLayout = (LinearLayout)findViewById(R.id.search_screen_layout_id);
        screenInstr = (TextView)findViewById(R.id.search_screen_instr);

        // query backend database for stations near the current user
        String url = BACKEND_API_URL + "/station/all"
                + "?near=true"
                + "&user_name=" + StationActivity.spotifyUserID
                + "&range=1000";
        new GetRequestCall().execute("GET", "searchForPlaylists", url);

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

            try {
                findTarget = result.get(API_TARGET_KEY).toString();
            } catch (JSONException e) {
                e.printStackTrace();
            }
            if (findTarget.equals("searchForPlaylists")) {
                parsePlaylistData(result);
            }

            else {
                Log.d("HTTP Async response", "Error parsing result from server: " + result.toString());
            }
        }
    }


    /** Parse the json station list from backend API server */
    public void parsePlaylistData(JSONObject jobj) {
        // remove API_USER_ID from EchoNest profile name
        jobj.remove(API_TARGET_KEY);

        // iterate through objects in return and display each playlist as a button
        Iterator<?> keys = jobj.keys();
        int buttonID = 0;
        if (!keys.hasNext()) {
            screenInstr.setText("No stations were found in your area.  Play one of your own or Create a new one to get started!");
        }
        while (keys.hasNext()) {
            String key = keys.next().toString();
            try {
                if (jobj.get(key) instanceof JSONObject) {

                    Button btn = new Button(this);
                    btn.setId(buttonID);
                    final int id_ = btn.getId();

                    String btnName = jobj.getJSONObject(key).get("name").toString();
                    String stationName = jobj.getJSONObject(key).get("catalog_key").toString();

                    STATION_CATALOG_KEY.add(buttonID, stationName);
                    STATION_NAME.add(buttonID, btnName);

                    Integer uScoreIdx = btnName.indexOf("_") + 1;
                    btnName = btnName.substring(uScoreIdx);

                    btn.setText(btnName);
                    btn.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
                    screenLayout.addView(btn);
                    btn.setOnClickListener(new View.OnClickListener() {
                        public void onClick(View view) {
                            returnToStation(id_);
                        }
                    });
                }
            } catch (JSONException e) {
                e.printStackTrace();
                Toast toast = Toast.makeText(
                        getApplicationContext(),
                        "Error parson JSON object playlist",
                        Toast.LENGTH_SHORT
                );
                toast.show();
            }
            buttonID++;
        }
    }

    public void returnToStation(int btnId) {
        // return catalog ID to StationActivity.java
        Intent i = new Intent();
        i.putExtra("stationName", STATION_NAME.get(btnId));
        i.putExtra("stationCatalog", STATION_CATALOG_KEY.get(btnId));
        Toast.makeText(getApplicationContext(),
                "stationName=" + STATION_NAME.get(btnId) + " && stationCatalog=" + STATION_CATALOG_KEY.get(btnId),
                Toast.LENGTH_SHORT)
                .show();

        setResult(228, i);

        finish();
    }


}
