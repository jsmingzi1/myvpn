package com.example.myvpn;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.net.VpnService;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

public class MyVpnClient extends AppCompatActivity {
    public interface Prefs {
        String NAME = "connection";
        String SERVER_ADDRESS = "server.address";
        String SERVER_PORT = "server.port";
        String SHARED_SECRET = "shared.secret";
        String PROXY_HOSTNAME = "proxyhost";
        String PROXY_PORT = "proxyport";
        String ALLOW = "allow";
        String PACKAGES = "packages";
        String CONNECT_STATUS = "Connect";
    }

    //@Override
    //protected void onCreate(Bundle savedInstanceState) {
    //    super.onCreate(savedInstanceState);
    //    setContentView(R.layout.activity_main);
    //}

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
/*
        final TextView serverAddress = findViewById(R.id.address);
        final TextView serverPort = findViewById(R.id.port);
        final TextView sharedSecret = findViewById(R.id.secret);
        final TextView proxyHost = findViewById(R.id.proxyhost);
        final TextView proxyPort = findViewById(R.id.proxyport);

        final RadioButton allowed = findViewById(R.id.allowed);
        final TextView packages = findViewById(R.id.packages);
*/
        final SharedPreferences prefs = getSharedPreferences(Prefs.NAME, MODE_PRIVATE);
/*        serverAddress.setText(prefs.getString(Prefs.SERVER_ADDRESS, ""));
        int serverPortPrefValue = prefs.getInt(Prefs.SERVER_PORT, 0);
        serverPort.setText(String.valueOf(serverPortPrefValue == 0 ? "" : serverPortPrefValue));
        sharedSecret.setText(prefs.getString(Prefs.SHARED_SECRET, ""));
        proxyHost.setText(prefs.getString(Prefs.PROXY_HOSTNAME, ""));
        int proxyPortPrefValue = prefs.getInt(Prefs.PROXY_PORT, 0);
        proxyPort.setText(proxyPortPrefValue == 0 ? "" : String.valueOf(proxyPortPrefValue));

        allowed.setChecked(prefs.getBoolean(Prefs.ALLOW, true));
        packages.setText(String.join(", ", prefs.getStringSet(
                Prefs.PACKAGES, Collections.emptySet())));
*/
        findViewById(R.id.connect).setOnClickListener(v -> {
/*            if (!checkProxyConfigs(proxyHost.getText().toString(),
                    proxyPort.getText().toString())) {
                return;
            }

            final Set<String> packageSet =
                    Arrays.stream(packages.getText().toString().split(","))
                            .map(String::trim)
                            .filter(s -> !s.isEmpty())
                            .collect(Collectors.toSet());
            if (!checkPackages(packageSet)) {
                return;
            }

            int serverPortNum;
            try {
                serverPortNum = Integer.parseInt(serverPort.getText().toString());
            } catch (NumberFormatException e) {
                serverPortNum = 0;
            }
            int proxyPortNum;
            try {
                proxyPortNum = Integer.parseInt(proxyPort.getText().toString());
            } catch (NumberFormatException e) {
                proxyPortNum = 0;
            }*/
            prefs.edit()
                    .putString(Prefs.SERVER_ADDRESS, "192.227.147.146")//serverAddress.getText().toString())
                    .putInt(Prefs.SERVER_PORT, 8000)//serverPortNum)
                    .putString(Prefs.SHARED_SECRET, "test")//sharedSecret.getText().toString())
                    .putString(Prefs.PROXY_HOSTNAME, "")//proxyHost.getText().toString())
                    .putInt(Prefs.PROXY_PORT, 0)//proxyPortNum)
                    .putBoolean(Prefs.ALLOW, false)//allowed.isChecked())
                    .putStringSet(Prefs.PACKAGES, null)//packageSet)
                    .commit();
            //final SharedPreferences prefs = getSharedPreferences(MyVpnClient.Prefs.NAME, MODE_PRIVATE);
            //((android.widget.Button)findViewById(R.id.connect)).setText(prefs.getString(MyVpnClient.Prefs.CONNECT_STATUS, "Connecting"));
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
            Intent intent = new Intent(this, SelectAppActivity.class);
            startActivity(intent);
            finish();
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

    private boolean checkPackages(Set<String> packageNames) {
        final boolean hasCorrectPackageNames = packageNames.isEmpty() ||
                getPackageManager().getInstalledPackages(0).stream()
                        .map(pi -> pi.packageName)
                        .collect(Collectors.toSet())
                        .containsAll(packageNames);
        if (!hasCorrectPackageNames) {
            Toast.makeText(this, R.string.unknown_package_names, Toast.LENGTH_SHORT).show();
        }
        return hasCorrectPackageNames;
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result == RESULT_OK) {
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
