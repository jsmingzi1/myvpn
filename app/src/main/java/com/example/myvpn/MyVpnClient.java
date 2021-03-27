package com.example.myvpn;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.VpnService;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MyVpnClient extends AppCompatActivity {
    public interface Prefs {
        String NAME = "connection";
        String SHARED_SECRET = "shared.secret";
        String GLOBAL = "global";
        String PACKAGES = "packages";
        String JSON_SERVER="jsonserver";
    }

    private final int SELECT_APP_PACKAGES_REQUEST = 10;
    private final int SELECT_SERVER_REQUEST = 11;
    private SharedPreferences prefs ;

        private Handler handler = new Handler();
        private String mConnect_Status = "Connect";
        private Runnable runnable = new Runnable() {
            public void run() {
                this.update();
                handler.postDelayed(this, 1000);// 间隔120秒
            }

            void update() {
                //刷新msg的内容
                Boolean bVpn = vpn();
                if (mConnect_Status == "Connect" && bVpn)
                {
                    mConnect_Status = "Success";
                    ((android.widget.Button)findViewById(R.id.connect)).setText(mConnect_Status);
                    Drawable top = getResources().getDrawable(R.mipmap.select03, null);
                    ((android.widget.Button)findViewById(R.id.connect)).setCompoundDrawablesWithIntrinsicBounds(null, top , null, null);

                }

                if (mConnect_Status == "Success" && bVpn==false)
                {
                    mConnect_Status = "Connect";
                    ((android.widget.Button)findViewById(R.id.connect)).setText(mConnect_Status);
                    Drawable top = getResources().getDrawable(R.mipmap.power, null);
                    ((android.widget.Button)findViewById(R.id.connect)).setCompoundDrawablesWithIntrinsicBounds(null, top , null, null);

                }
            }
        };
        @Override
        public void onDestroy() {
            handler.removeCallbacks(runnable);
            super.onDestroy();
        }


        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.activity_main);

        prefs = getSharedPreferences(Prefs.NAME, MODE_PRIVATE);

        findViewById(R.id.connect).setOnClickListener(v -> {

            RadioButton radioButtonGlobal = (RadioButton) findViewById(R.id.radioButtonGlobal);
            if (radioButtonGlobal.isChecked()) {
                prefs.edit().putBoolean(Prefs.GLOBAL, true).commit();
            }
            else {
                Set<String> pkg = prefs.getStringSet(Prefs.PACKAGES, null);
                if (pkg != null && pkg.size() > 0) {
                    boolean bChanged = false;
                    for (String str : pkg) {
                        if (isPackageExisted(str) != false) {
                            pkg.remove(str);
                            bChanged = true;
                        }
                    }

                    if (bChanged) {
                        prefs.edit()
                                .putBoolean(Prefs.GLOBAL, false)
                                .putStringSet(Prefs.PACKAGES, pkg)
                                .commit();
                    }
                }
            }

            //verify server ip and port
            try {
                JSONObject obj = new JSONObject(prefs.getString(Prefs.JSON_SERVER, ""));
                if (obj.getString("name").length() == 0 || obj.getInt("port") <= 0)
                    throw new Exception("wrong ip or port");
            } catch (Exception e) {
                Toast.makeText(this, "Invalidate Server, Please choose one.", Toast.LENGTH_SHORT).show();
                return;
            }

            prefs.edit()
                    //.putString(Prefs.SERVER_ADDRESS, "192.227.147.146")//serverAddress.getText().toString())
                    //.putInt(Prefs.SERVER_PORT, 8000)//serverPortNum)
                    .putString(Prefs.SHARED_SECRET, "test")//sharedSecret.getText().toString())
                    .commit();

            if (vpn()) {
                startService(getServiceIntent().setAction(MyVpnService.ACTION_DISCONNECT));
            }
            else {
                Intent intent = VpnService.prepare(MyVpnClient.this);
                if (intent != null) {
                    startActivityForResult(intent, 0);
                } else {
                    onActivityResult(0, RESULT_OK, null);
                }
            }
        });
        /*findViewById(R.id.disconnect).setOnClickListener(v -> {
            startService(getServiceIntent().setAction(MyVpnService.ACTION_DISCONNECT));
        });*/

        findViewById(R.id.imageButtonRoute).setOnClickListener(v -> {
            //provide multiple connections for choice
            Toast.makeText(MyVpnClient.this, "No Spare Connections Avaliable", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(MyVpnClient.this, ServersActivity.class);
            String strobj = prefs.getString(Prefs.JSON_SERVER, "");
            if (strobj != null && strobj.length() > 0) {
                try {
                    JSONObject obj = new JSONObject(strobj);
                    intent.putExtra("selectedId", obj.getInt("id"));
                }catch (Exception e) {}
            }

            startActivityForResult(intent, SELECT_SERVER_REQUEST);
        });

        //setup for global and apps modes
            RadioGroup radioGroup = (RadioGroup)findViewById(R.id.radiogroupmode);
            radioGroup.setOnCheckedChangeListener((group, optionId)->{
                if (optionId == R.id.radioButtonApps)
                    findViewById(R.id.imageButtonEditApps).setVisibility(View.VISIBLE);
                else
                    findViewById(R.id.imageButtonEditApps).setVisibility(View.INVISIBLE);

            });

            findViewById(R.id.imageButtonEditApps).setOnClickListener(v-> {
                Intent intent = new Intent(MyVpnClient.this, SelectAppActivity.class);
                Set<String> set = prefs.getStringSet(Prefs.PACKAGES, Collections.emptySet());
                ArrayList<String> packages = new ArrayList<>(set);
                //Log.e("ERROR", "start select app activity with input size " + packages.size());
                intent.putStringArrayListExtra("SelectedPackages", packages);
                startActivityForResult(intent, SELECT_APP_PACKAGES_REQUEST);
            });

        handler.postDelayed(runnable, 1000);
    }

    private boolean checkProxyConfigs(String proxyHost, String proxyPort) {
        final boolean hasIncompleteProxyConfigs = proxyHost.isEmpty() != proxyPort.isEmpty();
        if (hasIncompleteProxyConfigs) {
            Toast.makeText(this, R.string.incomplete_proxy_settings, Toast.LENGTH_SHORT).show();
        }
        return !hasIncompleteProxyConfigs;
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
        Log.e("ERROR", "onActivityResult " + request + ", " + result);

        if (request == SELECT_APP_PACKAGES_REQUEST) {
            //Log.e("ERROR", "return 1 from selectappactivity " + result);
                if (data != null) {
                    ArrayList<String> packages = data.getStringArrayListExtra("SelectedPackages");
                    Set<String> setPackages = new HashSet<String>(packages);
                    prefs.edit().putStringSet(Prefs.PACKAGES, setPackages).commit();
             //       Log.e("ERROR", "return from selectappactivity " + packages.size());
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
                            Log.e("error", "select server result " + obj.toString());
                        }
                    } catch (Exception e) {}
                }
            }
        }
        else if (result == RESULT_OK) {
            Toast.makeText(this, "before start service", Toast.LENGTH_SHORT).show();
            startService(getServiceIntent().setAction(MyVpnService.ACTION_CONNECT));
            Toast.makeText(this, "after start service", Toast.LENGTH_SHORT).show();
        }

    }

    @Override
    protected void onStart() {
        super.onStart();
        if(vpn())
            ((android.widget.Button)findViewById(R.id.connect)).setText("Disconnect");
        else
            ((android.widget.Button)findViewById(R.id.connect)).setText("Connect");
    }

    private boolean vpn() {
        String iface = "";
        try {
            for (NetworkInterface networkInterface : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (networkInterface.isUp())
                    iface = networkInterface.getName();
                Log.d("DEBUG", "IFACE NAME: " + iface);
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
}
