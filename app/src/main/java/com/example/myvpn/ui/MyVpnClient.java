package com.example.myvpn;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.VpnService;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.json.JSONObject;

import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MyVpnClient extends AppCompatActivity {
    public interface Prefs {
        String PREF_NAME = "connection";
        String SHARED_SECRET = "shared.secret";
        String GLOBAL = "global";
        String PACKAGES = "packages";
        String JSON_SERVER="jsonserver";
    }
    private final int MYVPN_SERVICE_REQUEST = 9;
    private final int SELECT_APP_PACKAGES_REQUEST = 10;
    private final int SELECT_SERVER_REQUEST = 11;
    private SharedPreferences prefs ;

    public static final String action_vpnservice_connect = "com.example.myvpn.LOCAL_BROADCAST_CONNECT";
    public static final String action_vpnservice_disconnect = "com.example.myvpn.LOCAL_BROADCAST_DISCONNECT";
    public static LocalBroadcastManager localBroadcastManager;
    private LocalReceiver localReceiver;
    private boolean bConnect = false; //whether vpn is connected, value is set in local broadcase receiver which receives action from myvpnservice.


        @Override
        public void onDestroy() {
            localBroadcastManager.unregisterReceiver(localReceiver);
            super.onDestroy();
        }


        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.activity_main);

            prefs = getSharedPreferences(Prefs.PREF_NAME, MODE_PRIVATE);
            prefs.edit().putString(Prefs.SHARED_SECRET, "test").commit();

            //set local broadreceiver to receive the actions from myvpnservice(connect or disconnect
            localReceiver = new LocalReceiver();
            localBroadcastManager = LocalBroadcastManager.getInstance(this);
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(action_vpnservice_connect);
            intentFilter.addAction(action_vpnservice_disconnect);
            localBroadcastManager.registerReceiver(localReceiver, intentFilter);

            //click connect button
            setButton4Connect();

            //button to show vpn servers list
            setButton4ServersList();

            //setup for global or app packages modes
            RadioGroup radioGroup = (RadioGroup)findViewById(R.id.radiogroupmode);
            radioGroup.setOnCheckedChangeListener((group, optionId)->{
                if (optionId == R.id.radioButtonApps) {
                    findViewById(R.id.imageButtonEditApps).setVisibility(View.VISIBLE);
                    prefs.edit().putBoolean(Prefs.GLOBAL, false).commit();
                }
                else {
                    findViewById(R.id.imageButtonEditApps).setVisibility(View.INVISIBLE);
                    prefs.edit().putBoolean(Prefs.GLOBAL, true).commit();
                }

            });
            if (prefs.getBoolean(Prefs.GLOBAL, true)) {
                ((RadioButton) findViewById(R.id.radioButtonGlobal)).setSelected(true);
                Log.w("myvpnclient create function", "global is true");
            }
            else {
                ((RadioButton) findViewById(R.id.radioButtonApps)).setChecked(true);
                Log.w("myvpnclient create function", "global is false");
            }


            Map<String, ?> all = prefs.getAll();
            for (Map.Entry<String, ?> entry : all.entrySet()) {
                Log.w("main map values", entry.getKey() + ": " + entry.getValue().toString());
            }

            // choose app button for non-global packages mode
            findViewById(R.id.imageButtonEditApps).setOnClickListener(v-> {
                Intent intent = new Intent(MyVpnClient.this, SelectAppActivity.class);
                Set<String> set = prefs.getStringSet(Prefs.PACKAGES, Collections.emptySet());
                ArrayList<String> packages = new ArrayList<>(set);
                //Log.e("ERROR", "start select app activity with input size " + packages.size());
                intent.putStringArrayListExtra("SelectedPackages", packages);
                startActivityForResult(intent, SELECT_APP_PACKAGES_REQUEST);
            });

    }


    private void setButton4ServersList()
    {
        //first init the default vpn server
        String servername = "No Invalidate Server!";
        String strobj = prefs.getString(Prefs.JSON_SERVER, "");
        if (strobj != null && strobj.length() > 0) {
            try {
                JSONObject obj = new JSONObject(strobj);
                servername = obj.getString("name");
            }catch (Exception e) {}
        }
        Log.w("setButton4ServersList", "default server name is "+servername);
        ((TextView) findViewById(R.id.textViewRoute)).setText(servername);


        findViewById(R.id.imageButtonRoute).setOnClickListener(v -> {
            //provide multiple connections for choice
            Toast.makeText(MyVpnClient.this, "No Spare Connections Avaliable", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(MyVpnClient.this, ServersActivity.class);
            String strObj = prefs.getString(Prefs.JSON_SERVER, "");
            if (strobj != null && strobj.length() > 0) {
                try {
                    JSONObject obj = new JSONObject(strobj);
                    intent.putExtra("selectedId", obj.getInt("id"));
                }catch (Exception e) {}
            }

            startActivityForResult(intent, SELECT_SERVER_REQUEST);
        });
    }

    private void setButton4Connect() {
        findViewById(R.id.connect).setOnClickListener(v -> {
            //verify server ip and port
            try {
                JSONObject obj = new JSONObject(prefs.getString(Prefs.JSON_SERVER, ""));
                if (obj.getString("name").length() == 0 || obj.getInt("port") <= 0)
                    throw new Exception("wrong ip or port");
            } catch (Exception e) {
                Toast.makeText(this, "Invalidate Server, Please choose one.", Toast.LENGTH_SHORT).show();
                return;
            }

            if (bConnect) {
                startService(getServiceIntent().setAction(MyVpnService.ACTION_DISCONNECT));
            }
            else {
                ((android.widget.Button)findViewById(R.id.connect)).setText("Connecting");
                Intent intent = VpnService.prepare(MyVpnClient.this);
                if (intent != null) {
                    startActivityForResult(intent, MYVPN_SERVICE_REQUEST);
                } else {
                    onActivityResult(MYVPN_SERVICE_REQUEST, RESULT_OK, null);
                }
            }
        });
    }
    private boolean isPackageExisted(String targetPackage){
        List<ApplicationInfo> packages;
        PackageManager pm;

        pm = getPackageManager();
        packages = pm.getInstalledApplications(0);
        for (ApplicationInfo packageInfo : packages) {
            if(packageInfo.packageName.equals(targetPackage))
                return true;
        }
        return false;
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        Log.w("onActivityResult", "enter parameters " + request + ", " + result);

        if (request == SELECT_APP_PACKAGES_REQUEST) {
             if (data != null) {
                    ArrayList<String> packages = data.getStringArrayListExtra("SelectedPackages");
                    Set<String> setPackages = new HashSet<String>(packages);
                    prefs.edit().putStringSet(Prefs.PACKAGES, setPackages).commit();
                    Log.w("onActivityResult", "return from selectappactivity " + packages.size());
                }
        }
        else if (request==SELECT_SERVER_REQUEST) {
            if (data!=null){
                String strObj = data.getStringExtra("SelectedServer");
                if (strObj!=null && strObj.length()>0) {
                    try {
                        JSONObject obj = new JSONObject(strObj);
                        if (obj != null) {
                            obj.getString("name");
                            ((TextView) findViewById(R.id.textViewRoute)).setText(obj.getString("name"));
                            prefs.edit().putString(Prefs.JSON_SERVER, obj.toString()).commit();
                            Log.w("onActivityResult", "select server result " + obj.toString());
                        }
                    } catch (Exception e) {}
                }
            }
        }
        else if (request == MYVPN_SERVICE_REQUEST) {
            //myvpnclient will start activity for myvpnservice, if the activity started, then can call startservice to real startup the service
            //Toast.makeText(this, "before start service " + result, Toast.LENGTH_SHORT).show();
            startService(getServiceIntent().setAction(MyVpnService.ACTION_CONNECT));
            //Toast.makeText(this, "after start service", Toast.LENGTH_SHORT).show();
        }
        else
        {
            Log.w("onActivityResult", "onActivityResult else logic " + request + ", " + result);
        }

    }
/*
    @Override
    protected void onStart() {
        super.onStart();
        if(vpn())
            ((android.widget.Button)findViewById(R.id.connect)).setText("Disconnect");
        else
            ((android.widget.Button)findViewById(R.id.connect)).setText("Connect");
    }*/

    private boolean vpn() {
        String iface = "";
        try {
            for (NetworkInterface networkInterface : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (networkInterface.isUp())
                    iface = networkInterface.getName();
                Log.i("DEBUG", "IFACE NAME: " + iface);
                if ( iface.contains("tun") || iface.contains("ppp") || iface.contains("pptp")) {
                    return true;
                }
            }
        } catch (SocketException e1) {
            e1.printStackTrace();
        }

        return false;
    }

    private Intent getServiceIntent() {
        return new Intent(this, MyVpnService.class);
    }

    class LocalReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            Log.w("onReceive", "test:" + intent.getAction());
            if (intent.getAction() == action_vpnservice_connect) {
                ((android.widget.Button)findViewById(R.id.connect)).setText("Success");
                Drawable top = getResources().getDrawable(R.mipmap.select03, null);
                ((android.widget.Button)findViewById(R.id.connect)).setCompoundDrawablesWithIntrinsicBounds(null, top , null, null);
                bConnect = true;
            }
            else if (intent.getAction() == action_vpnservice_disconnect) {
                ((android.widget.Button)findViewById(R.id.connect)).setText("Connect");
                Drawable top = getResources().getDrawable(R.mipmap.power, null);
                ((android.widget.Button)findViewById(R.id.connect)).setCompoundDrawablesWithIntrinsicBounds(null, top , null, null);
                bConnect = false;
            }
        }
    }
}
