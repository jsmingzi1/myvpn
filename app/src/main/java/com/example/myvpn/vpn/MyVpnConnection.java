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


import android.app.PendingIntent;
import android.content.pm.PackageManager;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import android.util.Log;

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

import static java.nio.charset.StandardCharsets.US_ASCII;

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
    private final Set<String> mPackages;

    public String LOCAL_ADDRESS = "";
    public FileOutputStream m_VPNOutputStream=null;
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

            // If anything needs to be obtained using the network, get it now.
            // This greatly reduces the complexity of seamless handover, which
            // tries to recreate the tunnel without shutting down everything.
            // In this demo, all we need to know is the server address.
            final SocketAddress serverAddress = new InetSocketAddress(mServerName, mServerPort);

            //download config
            MyVpnService.Instance.checkConfigFile(true);



            // We try to create the tunnel several times.
            // TODO: The better way is to work with ConnectivityManager, trying only when the
            // network is available.
            // Here we just use a counter to keep things simple.
            for (int attempt = 0; attempt < 10; ++attempt) {
                // Reset the counter if we were connected.
                if (run(serverAddress)) {
                    attempt = 0;
                }

                // Sleep for a while. This also checks if we got interrupted.
                Thread.sleep(3000);
            }
            Log.w(getTag(), "Giving up");
        } catch (IOException | InterruptedException | IllegalArgumentException | IllegalStateException e) {
            Log.e(getTag(), "Connection failed, exiting", e);
        }
    }

    public static String bytesToHex(byte[] in, int len) {
        final StringBuilder builder = new StringBuilder();
        for (int i=0;i<len;i++) {
            builder.append(String.format("%02x", in[i]));
        }
        //for(byte b : in) {
        //    builder.append(String.format("%02x", b));
        //}
        return builder.toString();
    }
    private boolean run(SocketAddress server)
            throws IOException, InterruptedException, IllegalArgumentException {
        ParcelFileDescriptor iface = null;
        boolean connected = false;
        // Create a DatagramChannel as the VPN tunnel.
        Log.w(getTag(), "lcm before try");

        try (DatagramChannel tunnel = DatagramChannel.open()) {

            // Protect the tunnel before connecting to avoid loopback.
            if (!mService.protect(tunnel.socket())) {
                throw new IllegalStateException("Cannot protect the tunnel");
            }

            //prefs.edit().putString(MyVpnClient.Prefs.CONNECT_STATUS, "Connecting").commit();
            // Connect to the server.
            tunnel.connect(server);

            // For simplicity, we use the same thread for both reading and
            // writing. Here we put the tunnel into non-blocking mode.
            tunnel.configureBlocking(false);

            // Authenticate and configure the virtual network interface.
            iface = handshake(tunnel);

            // Now we are connected. Set the flag.
            connected = true;
            Log.w(getTag(), "now is connected");
            //reportCompletion("");
            //prefs.edit().putString(MyVpnClient.Prefs.CONNECT_STATUS, "Connected").commit();
            // Packets to be sent are queued in this input stream.
            FileInputStream in = new FileInputStream(iface.getFileDescriptor());

            // Packets received need to be written to this output stream.
            FileOutputStream out = new FileOutputStream(iface.getFileDescriptor());
            m_VPNInterface = iface;
            m_VPNOutputStream = out;
            // Allocate the buffer for a single packet.
            ByteBuffer packet = ByteBuffer.allocate(MAX_PACKET_SIZE);

            //process packet
            if (PacketProcess.Instance == null)
                new PacketProcess();

            // Timeouts:
            //   - when data has not been sent in a while, send empty keepalive messages.
            //   - when data has not been received in a while, assume the connection is broken.
            long lastSendTime = System.currentTimeMillis();
            long lastReceiveTime = System.currentTimeMillis();
            Log.w(getTag(), "lcm before while");
            // We keep forwarding packets till something goes wrong.
            while (true) {
                // Assume that we did not make any progress in this iteration.
                boolean idle = true;

                boolean bProcessed = false;
                // Read the outgoing packet from the input stream.
                int length = in.read(packet.array());
                if (length > 0) {
                    // Write the outgoing packet to the tunnel.
                    //DatagramPacket dp = new DatagramPacket(packet.array(), length);
                    //Log.i(getTag(), "lcm send for Address is "+length+","+bytesToHex(packet.array(), length));
                    //Log.w(getTag(), "begin "+ length + " limit is "+packet.limit() + ", position is "+packet.position());
                    try {
                        bProcessed = PacketProcess.Instance.processPacket(true, packet.array(), 0, length, null);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    //Packet.IP ip = new Packet.IP(packet.array(), 0, length);
                    //if (ip.protocol == 6) {
                    //    if (Packet.TCP.isHTTP(packet.array(), ip.ihl, length-ip.ihl)) {
                    //        Log.w(getTag(), "this is http request: " + Packet.TCP.getHttpHead(packet.array(), ip.ihl, length-ip.ihl));
                    //    }
                    //}
                    //Log.w(getTag(), "middle1 limit is "+packet.limit() + ", position is "+packet.position());

                    packet.limit(length);
                    //Log.w(getTag(), "middle2 limit is "+packet.limit() + ", position is "+packet.position());

                    if (bProcessed==false)
                        tunnel.write(packet);
                    //Log.w(getTag(), "middle3 limit is "+packet.limit() + ", position is "+packet.position());

                    packet.clear();
                    //Log.w(getTag(), "end limit is "+packet.limit() + ", position is "+packet.position());

                    // There might be more outgoing packets.
                    idle = false;
                    lastSendTime = System.currentTimeMillis();
                }
                //Log.i(getTag(), "lcm 1Address is out");
                //DatagramPacket dp = new DatagramPacket(packet, length);
                // Read the incoming packet from the tunnel.
                length = tunnel.read(packet);
                if (length > 0) {
                    //Log.i(getTag(), "lcm receive for Address is return "+length+","+bytesToHex(packet.array(), length));
                    // Ignore control messages, which start with zero.
                    if (packet.get(0) != 0) {
                        //process http message
                        try {
                            bProcessed = PacketProcess.Instance.processPacket(false, packet.array(), 0, length, out);
                        } catch (Exception e) {
                            e.printStackTrace();
                            Log.w("myvpnconnection", "failed when processPacket "+e.toString());
                        }

                        // Write the incoming packet to the output stream.
                        if (bProcessed==false)
                            out.write(packet.array(), 0, length);
                    } else {
                        Log.i("idle test", "receive one control msg.");
                    }
                    packet.clear();

                    // There might be more incoming packets.
                    idle = false;
                    lastReceiveTime = System.currentTimeMillis();
                }

                // If we are idle or waiting for the network, sleep for a
                // fraction of time to avoid busy looping.
                /*if (idle) {
                    Thread.sleep(IDLE_INTERVAL_MS);
                    final long timeNow = System.currentTimeMillis();

                    if (lastSendTime + KEEPALIVE_INTERVAL_MS <= timeNow) {
                        // We are receiving for a long time but not sending.
                        // Send empty control messages.
                        packet.put((byte) 0).limit(1);
                        for (int i = 0; i < 3; ++i) {
                            packet.position(0);
                            tunnel.write(packet);
                        }
                        packet.clear();
                        lastSendTime = timeNow;
                    } else if (lastReceiveTime + RECEIVE_TIMEOUT_MS <= timeNow) {
                        // We are sending for a long time but not receiving.
                        throw new IllegalStateException("1Timed out");
                    }
                }*/
            }
        } catch (SocketException e) {
            Log.e(getTag(), "Cannot use socket", e);
        } finally {
            if (iface != null) {
                try {
                    iface.close();
                } catch (IOException e) {
                    Log.e(getTag(), "Unable to close interface", e);
                }
            }
            m_VPNOutputStream = null;
            m_VPNInterface = null;

            synchronized (mService) {
                if (mOnEstablishListener != null) {
                    mOnEstablishListener.onDisestablish(iface);
                    Log.w(getTag(), "disconnect in run function/timeout");
                }
            }
        }
        return connected;
    }

    private ParcelFileDescriptor handshake(DatagramChannel tunnel)
            throws IOException, InterruptedException {
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

    private ParcelFileDescriptor configure(String parameters) throws IllegalArgumentException {
        // Configure a builder while parsing the parameters.
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
            for (String packageName : mPackages) {
                try {
                    Log.e(getTag(), "allow app parameters!!!!" + packageName);
                        builder.addAllowedApplication(packageName);
                        //Toast.makeText(mService, "allow apps", Toast.LENGTH_SHORT).show();
                } catch (PackageManager.NameNotFoundException e){
                    Log.w(getTag(), "Package not available: " + packageName, e);
                }
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
        return MyVpnConnection.class.getSimpleName() + "[" + mConnectionId + "]";
    }
}
