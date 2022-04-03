package com.example.myvpn.ui;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.myvpn.R;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;


public class ServersActivity extends AppCompatActivity {
    public static final String TAG = "com.example.myvpn.serversactivity";

    public JSONArray jsonServers = null;
    public int selectedId = -1;
    public String selectedName = null;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_servers);

        selectedId = getIntent().getIntExtra("selectedId", -1);
        ServersListViewTask task = new ServersListViewTask();
        task.execute();
    }



    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        View listitem;
        switch (item.getItemId()){
            case android.R.id.home:
                JSONObject obj = getSelectedServer();
                if (obj != null) {
                    Intent intent = new Intent();
                    intent.putExtra("SelectedServer", obj.toString());
                    Log.e("Error", "when1 return from serversactivity, obj is " + obj.toString());

                    setResult(RESULT_OK, intent);
                    super.onBackPressed();
                    break;
                }
        }
        return true;
    }

    @Override
    public void onBackPressed() {
        JSONObject obj = getSelectedServer();

        Intent intent = new Intent();
        intent.putExtra("SelectedServer", obj.toString());
        Log.e("Error", "when return from serversactivity, obj is " + obj.toString());
        setResult(RESULT_OK, intent);


        super.onBackPressed();
    }

    private JSONObject getSelectedServer() {
        JSONObject obj = null;
        try {
            for (int i = 0; i < jsonServers.length(); i++) {

                if (jsonServers.getJSONObject(i).getString("name").equals(selectedName)) {
                    obj = jsonServers.getJSONObject(i);
                }
            }
        } catch (Exception e) {

        }
        finally {
            return obj;
        }
    }

    public class ServersListViewTask extends AsyncTask<Void, Void, String> {

        @Override
        protected String doInBackground(Void... params) {
            jsonServers = GetServersList();
            return null;
        }
        @Override
        protected void onPostExecute(String msg) {
            super.onPostExecute(msg);
            try {
                if (jsonServers != null) {
                    ArrayList<String> names = new ArrayList<String>();
                    for (int i = 0; i < jsonServers.length(); i++) {
                        JSONObject obj = (JSONObject) jsonServers.getJSONObject(i);
                        names.add(obj.getString("name"));
                        if (obj.getInt("id") == selectedId) {
                            selectedName = obj.getString("name");
                            ((TextView)findViewById(R.id.defaultserverid)).setText("Current Line: " + selectedName);
                        }
                    }
                    ArrayAdapter<String> adapter = new ArrayAdapter<String>(ServersActivity.this, android.R.layout.simple_list_item_1, android.R.id.text1, names);
                    ListView mListView = (ListView) findViewById(R.id.listview_servers);
                    mListView.setAdapter(adapter);

                    mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                        @Override
                        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                            selectedName = (String) parent.getItemAtPosition(position);
                            ((TextView)findViewById(R.id.defaultserverid)).setText("Current Line: " + selectedName);

                        }
                    });

                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        JSONArray GetServersList() {
            OkHttpClient client = new OkHttpClient();
            Request request = new Request.Builder()
                    .url("http://47.102.185.8:5000/api/v1/servers/all")
                    .build();
            Response responses = null;
            JSONArray Jarray = null;
            try {
                Log.e(TAG, "fetch json from server ");

                responses = client.newCall(request).execute();
                String jsonData = responses.body().string();
                Log.e(TAG, "jsonData is "+jsonData);
                Jarray = new JSONArray(jsonData);
            } catch (Exception e) {
                e.printStackTrace();
                Log.e(TAG, e.toString());
            }
            finally {
                return Jarray;
            }
        }
    }

}