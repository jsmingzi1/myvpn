package com.example.myvpn.vpn.packet.proxy.core;

import android.util.Log;

import com.example.myvpn.Tools;
import com.example.myvpn.vpn.MyVpnConnection;
import com.example.myvpn.vpn.MyVpnService;
import com.example.myvpn.vpn.packet.proxy.tunnel.Tunnel;
import com.example.myvpn.vpn.packet.proxy.tunnel.httpconnect.HttpConnectTunnel;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;

public class TcpProxyServer implements Runnable {

    public boolean Stopped;
    public short Port;

    Selector m_Selector;
    ServerSocketChannel m_ServerSocketChannel;
    Thread m_ServerThread;
    static String function_header="TcpProxyServer";

    public TcpProxyServer(int port) throws IOException {
        m_Selector = Selector.open();
        if (m_Selector.isOpen() == false)
            Log.w(function_header, "selector is not open");
        m_ServerSocketChannel = ServerSocketChannel.open();
        m_ServerSocketChannel.configureBlocking(false);

        InetAddress addr = InetAddress.getByAddress(Tools.ipTobytes(MyVpnConnection.Instance.LOCAL_ADDRESS));
        //m_ServerSocketChannel.socket().bind(new InetSocketAddress(InetAddress.getByName(MyVpnConnection.Instance.LOCAL_ADDRESS), port));
        m_ServerSocketChannel.socket().bind(new InetSocketAddress(addr, port));

        m_ServerSocketChannel.register(m_Selector, SelectionKey.OP_ACCEPT);
        InetSocketAddress a = (InetSocketAddress) m_ServerSocketChannel.socket().getLocalSocketAddress();
        Log.w(function_header, "m_ServerSocketChannel.socket() bind local address is:" + a.getAddress().getHostAddress()
                +","+m_ServerSocketChannel.socket().isBound() + "," + m_ServerSocketChannel.socket().isClosed());
        this.Port = (short)m_ServerSocketChannel.socket().getLocalPort();
        Log.w(function_header, "actual bind port is "+Port);

    }

    public void start() {
        m_ServerThread = new Thread(this);
        m_ServerThread.setName("TcpProxyServerThread");
        m_ServerThread.start();
    }

    public void stop() {
        Log.w(function_header, "stop function is called");
        Thread.dumpStack();
        this.Stopped = true;
        if (m_Selector != null) {
            try {
                m_Selector.close();
                m_Selector = null;
            } catch (Exception e) {
                //LocalVpnService.Instance.writeLog(e.getLocalizedMessage());
                e.printStackTrace();
            }
        }

        if (m_ServerSocketChannel != null) {
            try {
                m_ServerSocketChannel.close();
                m_ServerSocketChannel = null;
            } catch (Exception e) {
                //LocalVpnService.Instance.writeLog("close channel failed ", e.getLocalizedMessage());
                e.printStackTrace();
            }
        }
    }

    @Override
    public void run() {
        try {
            Log.w(function_header, "run is called");
            while (true) {
                m_Selector.select();
                Log.w(function_header, "after select()");

                Iterator<SelectionKey> keyIterator = m_Selector.selectedKeys().iterator();
                while (keyIterator.hasNext()) {
                    SelectionKey key = keyIterator.next();
                    if (key.isValid()) {
                        try {
                            if (key.isReadable()) {
                                ((Tunnel) key.attachment()).onReadable(key);
                            } else if (key.isWritable()) {
                                ((Tunnel) key.attachment()).onWritable(key);
                            } else if (key.isConnectable()) {
                                ((Tunnel) key.attachment()).onConnectable();
                            } else if (key.isAcceptable()) {
                                onAccepted(key);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    else {
                        Log.w(function_header, "select key is invalid");

                    }
                    keyIterator.remove();
                }
            }
        } catch (Exception e) {
            Log.w(function_header, "run exception "+e.toString());
            e.printStackTrace();
        } finally {
            this.stop();
            MyVpnService.Instance.writeLog("TcpServer thread exited.");
        }
    }

    private NatSession getNatSession(SocketChannel localChannel) {
        //int portKey = localChannel.socket().getPort();
        //int ipKey = Tools.ipInetAddress2Int(localChannel.socket().getInetAddress());
        try {
            int ipKey = Tools.ipInetAddress2Int(((InetSocketAddress) localChannel.getRemoteAddress()).getAddress());
            int portKey = ((InetSocketAddress) localChannel.getRemoteAddress()).getPort();
            Log.w(function_header, "getNatSession ipkey "+Tools.ipInt2Str(ipKey)
                +" portKey "+(short)portKey);

            return NatSessionManager.getSession(ipKey, (short)portKey);
        } catch (Exception e) {
            Log.w(function_header, "getNatSession exception");
        }
        return null;
    }

    private InetSocketAddress getDestAddress(NatSession session) throws Exception{
        if (session != null) {

            String action = ProxyConfig.Instance.needProxy(session/*session.RemoteHost, session.RemoteIP, IPHeader.TCP, session.uid, session.httpAction*/);

            if (action.startsWith("proxy")) { //format maybe: proxy:default, or proxy:AAA
                Log.w(function_header, "getDestAddress with action proxy");

                return InetSocketAddress.createUnresolved(session.RemoteHost, Tools.convertShortPortReadbleInt(session.RemotePort));
            } else if (action.equals("direct")) {
                Log.w(function_header, "getDestAddress with action direct");

                return new InetSocketAddress(InetAddress.getByName(Tools.ipInt2Str(session.RemoteIP)), Tools.convertShortPortReadbleInt(session.RemotePort));
            } else if (action.equals("block")) {
                Log.w(function_header, "getDestAddress with action block");

                return null;
            }
        }
        return null;
    }


    void onAccepted(SelectionKey key) {
        Tunnel localTunnel = null;
        try {
            Log.w(function_header, "onAccepted is called");
            SocketChannel localChannel = m_ServerSocketChannel.accept();
            localTunnel = TunnelFactory.wrap(localChannel, m_Selector);
            localTunnel.TunnelType = "local";


            NatSession session = getNatSession(localChannel);
            function_header="TcpProxyServer "+Tools.ipInt2Str(session.LocalIP)+":"+session.LocalPort
                    +"->"+Tools.ipInt2Str(session.RemoteIP)+":"+session.RemotePort;
            Log.w(function_header, "onAccepted is called");
            assert (session != null);

            session.localTunnel = localTunnel;
            InetSocketAddress destAddress = getDestAddress(session);
            //if ()
            //assert (destAddress != null);
            if (destAddress != null) {
                Tunnel remoteTunnel = TunnelFactory.createTunnelByConfig(destAddress, m_Selector);
                remoteTunnel.TunnelType = "remote";

                //assert (remoteTunnel instanceof HttpConnectTunnel);
                if (remoteTunnel instanceof HttpConnectTunnel) {
                    ((HttpConnectTunnel) remoteTunnel).httpAction = session.httpAction;
                    Log.w(function_header, "remote tunnel is http, and port is "+session.LocalPort+"->"+session.RemotePort+ " " +(session == null? " session is null":session.RemoteHost+"_"+session.httpAction));
                }
                remoteTunnel.setBrotherTunnel(localTunnel); // 关联兄弟
                localTunnel.setBrotherTunnel(remoteTunnel); // 关联兄弟
                Log.w(function_header, "onAccepted new pair "+localTunnel.currentID + "->" + remoteTunnel.currentID);

                if (destAddress.isUnresolved()) {
                    Log.w(function_header+" "+remoteTunnel.currentID, "unresolved address "+destAddress.getHostName());
                }
                else
                {
                    Log.w(function_header+" "+remoteTunnel.currentID, "this is resolved address "+destAddress.getAddress());
                }
                remoteTunnel.connect(destAddress); // 开始连接
                session.RemoteTunnel = remoteTunnel;
                localTunnel.session = session;
                remoteTunnel.session=session;
                session.isConnected=true;
            } else {
                Log.w(function_header, "destination address is empty.");
                localTunnel.dispose();
            }
        } catch (ConnectException e) {
            // Network is unreachable
            if (localTunnel != null) {
                Log.w("TcpProxyServer", "ConnectException remove session");
                NatSessionManager.RemoveSession(localTunnel);
                localTunnel.dispose();
            }
        } catch (Exception e) {
            e.printStackTrace();

            if (localTunnel != null) {
                Log.w("TcpProxyServer", "Exception remove session");

                NatSessionManager.RemoveSession(localTunnel);
                localTunnel.dispose();
            }
        }
    }

}
