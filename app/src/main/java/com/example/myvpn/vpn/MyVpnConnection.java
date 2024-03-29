package com.example.myvpn.vpn;

/*
 * Copyright (C) 2017 The Android Open Source Project
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


import static java.nio.charset.StandardCharsets.US_ASCII;

import android.app.PendingIntent;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import com.example.myvpn.Tools;
import com.example.myvpn.vpn.packet.PacketProcess;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class MyVpnConnection implements Runnable {
    /**
     * Callback interface to let the {@link MyVpnService} know about new connections
     * and update the foreground notification with connection status.
     */
    public interface OnEstablishListener {
        void onEstablish(ParcelFileDescriptor tunInterface);
        void onDisestablish(ParcelFileDescriptor tunInterface);
    }

    public static MyVpnConnection Instance = null;

    /** Maximum packet size is constrained by the MTU, which is given as a signed short. */
    private static final int MAX_PACKET_SIZE = Short.MAX_VALUE;

    /** Time to wait in between losing the connection and retrying. */
    private static final long RECONNECT_WAIT_MS = TimeUnit.SECONDS.toMillis(3);

    /** Time between keepalives if there is no traffic at the moment.
     *
     * TODO: don't do this; it's much better to let the connection die and then reconnect when
     *       necessary instead of keeping the network hardware up for hours on end in between.
     **/
    private static final long KEEPALIVE_INTERVAL_MS = TimeUnit.SECONDS.toMillis(15);

    /** Time to wait without receiving any response before assuming the server is gone. */
    private static final long RECEIVE_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(40);

    /**
     * Time between polling the VPN interface for new traffic, since it's non-blocking.
     *
     * TODO: really don't do this; a blocking read on another thread is much cleaner.
     */
    private static final long IDLE_INTERVAL_MS = TimeUnit.MILLISECONDS.toMillis(100);

    /**
     * Number of periods of length {@IDLE_INTERVAL_MS} to wait before declaring the handshake a
     * complete and abject failure.
     *
     * TODO: use a higher-level protocol; hand-rolling is a fun but pointless exercise.
     */
    private static final int MAX_HANDSHAKE_ATTEMPTS = 50;

    private final VpnService mService;
    private final int mConnectionId;

    private final String mServerName;
    private final int mServerPort;
    private final byte[] mSharedSecret;

    private PendingIntent mConfigureIntent;
    private OnEstablishListener mOnEstablishListener;

    // Allowed/Disallowed packages for VPN usage
    private final boolean mGlobal;
    // for global mode, use vpn server;
    // for app mode, no need vpn server, create fake local interface, and dispatch message to http proxy
    private final Set<String> mPackages;

    public String LOCAL_ADDRESS = "ABC";
    public FileOutputStream m_VPNOutputStream=null;
    public FileInputStream m_VPNInputStream = null;
    public ParcelFileDescriptor m_VPNInterface=null;


    public MyVpnConnection(final VpnService service, final int connectionId,
                            final String serverName, final int serverPort, final byte[] sharedSecret,
                           final boolean bGlobal,
                            final Set<String> packages) {
        mService = service;
        mConnectionId = connectionId;

        mServerName = serverName;
        mServerPort= serverPort;
        mSharedSecret = sharedSecret;


        mGlobal = bGlobal;
        mPackages = packages;
        Instance = this;
    }

    /**
     * Optionally, set an intent to configure the VPN. This is {@code null} by default.
     */
    public void setConfigureIntent(PendingIntent intent) {
        mConfigureIntent = intent;
    }

    public void setOnEstablishListener(OnEstablishListener listener) {
        mOnEstablishListener = listener;
    }

    @Override
    public void run() {
        try {
            Log.w(getTag(), "run Starting");
            //download config
            MyVpnService.Instance.checkConfigFile(true);

            if (mGlobal) {
                // global mode
                final SocketAddress serverAddress = new InetSocketAddress(mServerName, mServerPort);
                for (int attempt = 0; attempt < 10; ++attempt) {
                    // Reset the counter if we were connected.
                    if (runGlobalMode(serverAddress)) {
                        attempt = 0;
                    }
                    Thread.sleep(3000);
                }
            } else {
                //App mode
                runAppMode();
            }
            Log.w(getTag(), "Giving up");
        } catch (Exception e) {
            Log.e(getTag(), "Connection failed, exiting", e);
        }
    }


    private boolean runGlobalMode(SocketAddress server)
            throws Exception {
        boolean connected = false;
        // Create a DatagramChannel as the VPN tunnel.
        Log.w(getTag(), "runGlobalMode before try");

        try (DatagramChannel tunnel = DatagramChannel.open()) {
            // Protect the tunnel before connecting to avoid loopback.
            if (!mService.protect(tunnel.socket())) {
                throw new IllegalStateException("Cannot protect the tunnel");
            }
            // Connect to the server.
            tunnel.connect(server);
            // For simplicity, we use the same thread for both reading and
            // writing. Here we put the tunnel into non-blocking mode.
            tunnel.configureBlocking(false);

            // Authenticate and configure the virtual network interface.
            m_VPNInterface = handshake(tunnel);

            // Now we are connected. Set the flag.
            connected = true;
            Log.w(getTag(), "now is connected");
            m_VPNInputStream = new FileInputStream(m_VPNInterface.getFileDescriptor());
            // Packets received need to be written to this output stream.
            m_VPNOutputStream = new FileOutputStream(m_VPNInterface.getFileDescriptor());
            // Allocate the buffer for a single packet.
            ByteBuffer packet = ByteBuffer.allocate(MAX_PACKET_SIZE);

            //if (PacketProcess.Instance == null)
            //    new PacketProcess();


            while (true) {
                // Read the outgoing packet from the input stream.
                int length = m_VPNInputStream.read(packet.array());
                if (length > 0) {
                    /*try {
                        PacketProcess.Instance.processPacket(packet.array(), 0, length);
                    } catch (Exception e) {
                        Log.w("MyVPNConnection", "PacketProcess.Instance.processPacket exception "+e);
                    }*/
                    packet.limit(length);
                    //tunnel.write(packet);
                    packet.clear();
                }
                // Read the incoming packet from the tunnel.
                length = tunnel.read(packet);
                if (length > 0) {
                    Log.i("MyVpnConnection", "lcm receive for Address is return " + length + "," + Tools.bytesToHex(packet.array(), length));
                    if (packet.get(0) != 0) {
                        // Write the incoming packet to the output stream.
                        m_VPNOutputStream.write(packet.array(), 0, length);
                        packet.clear();
                    }
                }
            }
        } catch (SocketException e) {
            Log.e("MyVpnConnection", "Cannot use socket", e);
        } finally {
            if (m_VPNInterface != null) {
                try {
                    m_VPNInterface.close();
                } catch (IOException e) {
                    Log.e("MyVpnConnection", "Unable to close interface", e);
                }
            }
            m_VPNOutputStream = null;
            m_VPNInterface = null;

            synchronized (mService) {
                if (mOnEstablishListener != null) {
                    mOnEstablishListener.onDisestablish(m_VPNInterface);
                    Log.w(getTag(), "disconnect in run function/timeout");
                }
            }
        }
        return connected;
    }



    private boolean runAppMode() // here for app mode, above run function for global mode
            throws Exception {
                Log.w(getTag(), "runAppMode before try");
                m_VPNInterface = configure("m,1400 a,10.0.0.22,32 d,8.8.8.8 r,0.0.0.0,0");

        try {

            m_VPNInputStream = new FileInputStream(m_VPNInterface.getFileDescriptor());
            m_VPNOutputStream = new FileOutputStream(m_VPNInterface.getFileDescriptor());
            ByteBuffer packet = ByteBuffer.allocate(MAX_PACKET_SIZE);

            if (PacketProcess.Instance == null)
                new PacketProcess();

            while (true) {
                int length = m_VPNInputStream.read(packet.array());
                if (length > 0) {
                    PacketProcess.Instance.processPacket(packet.array(), 0, length);
                    packet.limit(length);
                    packet.clear();
                }
            }
        } catch (Exception e) {
            Log.e(getTag(), "Cannot use socket", e);
        } finally {
            if (m_VPNInterface != null) {
                try {
                    m_VPNInterface.close();
                } catch (IOException e) {
                    Log.e(getTag(), "Unable to close interface", e);
                }
            }
            m_VPNOutputStream = null;
            m_VPNInterface = null;

            synchronized (mService) {
                if (mOnEstablishListener != null) {
                    mOnEstablishListener.onDisestablish(m_VPNInterface);
                }
            }
        }
        return true;
    }

    private ParcelFileDescriptor handshake(DatagramChannel tunnel)
            throws Exception {
        // To build a secured tunnel, we should perform mutual authentication
        // and exchange session keys for encryption. To keep things simple in
        // this demo, we just send the shared secret in plaintext and wait
        // for the server to send the parameters.

        // Allocate the buffer for handshaking. We have a hardcoded maximum
        // handshake size of 1024 bytes, which should be enough for demo
        // purposes.
        ByteBuffer packet = ByteBuffer.allocate(1024);

        // Control messages always start with zero.
        packet.put((byte) 0).put(mSharedSecret).flip();

        // Send the secret several times in case of packet loss.
        for (int i = 0; i < 3; ++i) {
            packet.position(0);
            tunnel.write(packet);
        }
        packet.clear();

        // Wait for the parameters within a limited time.

        for (int i = 0; i < MAX_HANDSHAKE_ATTEMPTS; ++i) {
            Thread.sleep(IDLE_INTERVAL_MS);

            // Normally we should not receive random packets. Check that the first
            // byte is 0 as expected.
            int length = tunnel.read(packet);
            if (length > 0 && packet.get(0) == 0) {
                return configure(new String(packet.array(), 1, length - 1, US_ASCII).trim());
            }
        }
        throw new IOException("2Timed out");
    }

    private ParcelFileDescriptor configure(String parameters) throws Exception {
        // Configure a builder while parsing the parameters.
        Log.w("MyVPNConnection", "configure parameters are "+parameters);
        VpnService.Builder builder = mService.new Builder();
        for (String parameter : parameters.split(" ")) {
            String[] fields = parameter.split(",");
            try {
                switch (fields[0].charAt(0)) {
                    case 'm':
                        builder.setMtu(Short.parseShort(fields[1]));
                        break;
                    case 'a':
                        builder.addAddress(fields[1], Integer.parseInt(fields[2]));
                        LOCAL_ADDRESS = fields[1];
                        break;
                    case 'r':
                        builder.addRoute(fields[1], Integer.parseInt(fields[2]));
                        break;
                    case 'd':
                        builder.addDnsServer(fields[1]);
                        break;
                    case 's':
                        builder.addSearchDomain(fields[1]);
                        break;
                }
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Bad parameter: " + parameter);
            }
        }

        // Create a new interface using the builder and save the parameters.
        final ParcelFileDescriptor vpnInterface;

        Log.w("myvpnconnevtion", "global parameter is " + mGlobal);
        if (mGlobal == false) {
            builder.addAllowedApplication("com.example.myvpn"); // add itsself
            for (String packageName : mPackages) {
                    Log.e(getTag(), "allow app parameters!!!!" + packageName);
                    builder.addAllowedApplication(packageName);
                    //Toast.makeText(mService, "allow apps", Toast.LENGTH_SHORT).show();
            }
        }

        builder.setSession(mServerName).setConfigureIntent(mConfigureIntent);

        synchronized (mService) {
            vpnInterface = builder.establish();
            if (mOnEstablishListener != null) {
                mOnEstablishListener.onEstablish(vpnInterface);
            }
        }
        Log.w(getTag(), "New interface: " + vpnInterface + " (" + parameters + ")");
        return vpnInterface;
    }

    private final String getTag() {
        return MyVpnConnection.class.getSimpleName();
    }
}
