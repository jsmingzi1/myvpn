package com.example.myvpn;

/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.VpnService;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.util.Pair;
import android.widget.Toast;

import com.example.myvpn.ui.MyVpnClient;

import org.json.JSONObject;

import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class MyVpnService extends VpnService implements Handler.Callback {
    private static final String TAG =  MyVpnService.class.getSimpleName();

    public static final String ACTION_CONNECT = "com.example.android.myvpn.START";
    public static final String ACTION_DISCONNECT = "com.example.android.myvpn.STOP";

    private Handler mHandler;

    private static class Connection extends Pair<Thread, ParcelFileDescriptor> {
        public Connection(Thread thread, ParcelFileDescriptor pfd) {
            super(thread, pfd);
        }
    }

    private final AtomicReference<Thread> mConnectingThread = new AtomicReference<>();
    private final AtomicReference<Connection> mConnection = new AtomicReference<>();

    private AtomicInteger mNextConnectionId = new AtomicInteger(1);

    private PendingIntent mConfigureIntent;

    @Override
    public void onCreate() {
        // The handler is only used to show messages.
        if (mHandler == null) {
            mHandler = new Handler(this);
        }

        // Create the intent to "configure" the connection (just start MyVpnClient).
        mConfigureIntent = PendingIntent.getActivity(this, 0, new Intent(this, MyVpnClient.class),
                PendingIntent.FLAG_UPDATE_CURRENT);

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_DISCONNECT.equals(intent.getAction())) {
            disconnect();
            return START_NOT_STICKY;
        } else {
            connect();
            return START_STICKY;
        }
    }

    @Override
    public void onDestroy() {
        disconnect();
    }

    @Override
    public boolean handleMessage(Message message) {
        Toast.makeText(this, message.what, Toast.LENGTH_SHORT).show();
        Log.w("MyVPNService", "handleMessage: "+message.what);
        if (message.what != R.string.disconnected) {
            updateForegroundNotification(message.what);
        }
        return true;
    }

    private void connect() {
        // Become a foreground service. Background services can be VPN services too, but they can
        // be killed by background check before getting a chance to receive onRevoke().
        updateForegroundNotification(R.string.connecting);
        mHandler.sendEmptyMessage(R.string.connecting);

        // Extract information from the shared preferences.
        final SharedPreferences prefs = getSharedPreferences(MyVpnClient.Prefs.PREF_NAME, MODE_PRIVATE);
        final byte[] secret = prefs.getString(MyVpnClient.Prefs.SHARED_SECRET, "").getBytes();
        final boolean bGlobal = prefs.getBoolean(MyVpnClient.Prefs.GLOBAL, true);

        Set<String> packages =
                prefs.getStringSet(MyVpnClient.Prefs.PACKAGES, Collections.emptySet());
        String server;
        int port;
        try {
            JSONObject obj = new JSONObject(prefs.getString(MyVpnClient.Prefs.JSON_SERVER, ""));
            server = obj.getString("ip");
            port = obj.getInt("port");
        } catch (Exception e) {
            Toast.makeText(this, "MyVPNService connect failed for invalid server ip or port.", Toast.LENGTH_SHORT).show();
            return;
        }

        Log.w("connect", "connect in myvpnservice global parameter is " + bGlobal + ", packages size is "+packages.size());
        startConnection(new MyVpnConnection(
                this, mNextConnectionId.getAndIncrement(), server, port, secret,
                bGlobal, packages));
    }

    private void startConnection(final MyVpnConnection connection) {
        // Replace any existing connecting thread with the  new one.
        final Thread thread = new Thread(connection, "MyVpnThread");
        setConnectingThread(thread);

        // Handler to mark as connected once onEstablish is called.
        connection.setConfigureIntent(mConfigureIntent);

        connection.setOnEstablishListener(
                new MyVpnConnection.OnEstablishListener() {
                    @Override
                    public void onEstablish(ParcelFileDescriptor tunInterface) {
                        mHandler.sendEmptyMessage(R.string.connected);
                        mConnectingThread.compareAndSet(thread, null);
                        setConnection(new Connection(thread, tunInterface));

                        MyVpnClient.localBroadcastManager.sendBroadcast(new Intent(MyVpnClient.action_vpnservice_connect));
                    }

                    @Override
                    public void onDisestablish(ParcelFileDescriptor tunInterface) {
                        disconnect();
                        MyVpnClient.localBroadcastManager.sendBroadcast(new Intent(MyVpnClient.action_vpnservice_disconnect));
                    }
                }
        );

        thread.start();
    }

    private void setConnectingThread(final Thread thread) {
        final Thread oldThread = mConnectingThread.getAndSet(thread);
        if (oldThread != null) {
            oldThread.interrupt();
        }
    }

    private void setConnection(final Connection connection) {
        final Connection oldConnection = mConnection.getAndSet(connection);
        if (oldConnection != null) {
            try {
                oldConnection.first.interrupt();
                oldConnection.second.close();
            } catch (IOException e) {
                Log.w(TAG, "setConnection Closing VPN interface", e);
            }
        }
    }

    private void disconnect() {
        mHandler.sendEmptyMessage(R.string.disconnected);
        setConnectingThread(null);
        setConnection(null);
        stopForeground(true);
    }

    private void updateForegroundNotification(final int message) {
        final String NOTIFICATION_CHANNEL_ID = "MyVpn";
        NotificationManager mNotificationManager = (NotificationManager) getSystemService(
                NOTIFICATION_SERVICE);
        mNotificationManager.createNotificationChannel(new NotificationChannel(
                NOTIFICATION_CHANNEL_ID, NOTIFICATION_CHANNEL_ID,
                NotificationManager.IMPORTANCE_DEFAULT));
        startForeground(1, new Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_vpn)
                .setContentText(getString(message))
                .setContentIntent(mConfigureIntent)
                .build());
    }
}
