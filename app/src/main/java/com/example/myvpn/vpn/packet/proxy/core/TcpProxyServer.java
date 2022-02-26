package com.example.myvpn.vpn.packet.proxy.core;

import android.util.Log;

import com.example.myvpn.Tools;
import com.example.myvpn.vpn.MyVpnConnection;
import com.example.myvpn.vpn.MyVpnService;
import com.example.myvpn.vpn.packet.proxy.tcpip.IPHeader;
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

    public static int getUnsignedShort (short data){      //将data字节型数据转换为0~65535 (0xFFFF 即 WORD)。
        return data&0x0FFFF ;
    }
    public TcpProxyServer(int port) throws IOException {
        m_Selector = Selector.open();
        if (m_Selector.isOpen() == false)
            Log.w("m_Selector", "selector is not open");
        m_ServerSocketChannel = ServerSocketChannel.open();
        m_ServerSocketChannel.configureBlocking(false);
        m_ServerSocketChannel.socket().bind(new InetSocketAddress(InetAddress.getByName(MyVpnConnection.Instance.LOCAL_ADDRESS), port));
        m_ServerSocketChannel.register(m_Selector, SelectionKey.OP_ACCEPT);
        InetSocketAddress a = (InetSocketAddress) m_ServerSocketChannel.socket().getLocalSocketAddress();
        Log.w("TcpProxyServer", "m_ServerSocketChannel.socket() bind local address is:" + a.getAddress().getHostAddress()
                +","+m_ServerSocketChannel.socket().isBound() + "," + m_ServerSocketChannel.socket().isClosed());
        this.Port = (short)m_ServerSocketChannel.socket().getLocalPort();
       // Log.w("m_Selector", "actual bind port is "+m_ServerSocketChannel.socket().getLocalPort());

        Log.w("m_Selector", "actual bind port is "+getUnsignedShort(Port));

    }

    public void start() {
        m_ServerThread = new Thread(this);
        m_ServerThread.setName("TcpProxyServerThread");
        m_ServerThread.start();
    }

    public void stop() {
        Log.w("m_Selector", "stop function is called");
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
            Log.w("TcpProxyServer", "run is called");
            while (true) {
                m_Selector.select();
                Log.w("TcpProxyServer", "after select()");

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
                        //
                    }
                    keyIterator.remove();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            this.stop();
            MyVpnService.Instance.writeLog("TcpServer thread exited.");
        }
    }

    private NatSession getNatSession(SocketChannel localChannel) {
        short portKey = (short) localChannel.socket().getPort();
        int ipKey = Tools.ipInetAddress2Int(localChannel.socket().getInetAddress());
        //int ipKey = Tools.ipInetAddress2Int(((InetSocketAddress) localChannel.getRemoteAddress()).getAddress());
        return NatSessionManager.getSession(ipKey+"-"+portKey);
    }

    private InetSocketAddress getDestAddress(NatSession session) throws Exception{
        if (session != null) {
            String action = ProxyConfig.Instance.needProxy(session.RemoteHost, session.RemoteIP, IPHeader.TCP, session.uid, session.httpAction);
            if (action.equals("proxy")) {
                return InetSocketAddress.createUnresolved(session.RemoteHost, session.RemotePort & 0xFFFF);
            } else if (action.equals("direct")) {
                return new InetSocketAddress(InetAddress.getByName(Tools.ipInt2Str(session.RemoteIP)), session.RemotePort & 0xFFFF);
            } else if (action.equals("block")) {
                return null;
            }
        }
        return null;
    }


    void onAccepted(SelectionKey key) {
        Tunnel localTunnel = null;
        try {
            Log.w("TcpProxyServer", "onAccepted is called");
            SocketChannel localChannel = m_ServerSocketChannel.accept();
            localTunnel = TunnelFactory.wrap(localChannel, m_Selector);
            localTunnel.TunnelType = "local";


            NatSession session = getNatSession(localChannel);
            assert (session != null);

            session.localTunnel = localTunnel;
            InetSocketAddress destAddress = getDestAddress(session);
            assert (destAddress != null);
            if (destAddress != null) {
                Tunnel remoteTunnel = TunnelFactory.createTunnelByConfig(destAddress, m_Selector);
                remoteTunnel.TunnelType = "remote";

                assert (remoteTunnel instanceof HttpConnectTunnel);
                if (remoteTunnel instanceof HttpConnectTunnel) {
                    ((HttpConnectTunnel) remoteTunnel).httpAction = session.httpAction;
                    Log.w("TcpProxyServer onAccepted", "remote tunnel is http, and port is "+getUnsignedShort((short)session.RemotePort)+ (session == null? " session is null":session.RemoteHost+"_"+session.httpAction));
                }
                remoteTunnel.setBrotherTunnel(localTunnel); // 关联兄弟
                localTunnel.setBrotherTunnel(remoteTunnel); // 关联兄弟
                Log.w("TcpProxyServer", "onAccepted new pair "+localTunnel.currentID + "->" + remoteTunnel.currentID);

                if (destAddress.isUnresolved()) {
                    Log.w("tcpproxyserver"+remoteTunnel.currentID, "unresolved address "+destAddress.getHostName());
                }
                else
                {
                    Log.w("tcpproxyserver"+remoteTunnel.currentID, "this is resolved address "+destAddress.getAddress());
                }
                remoteTunnel.connect(destAddress); // 开始连接
                session.RemoteTunnel = remoteTunnel;
            } else {
                Log.w("tcpproxyserver", "destination address is empty.");
                localTunnel.dispose();
            }
        } catch (ConnectException e) {
            // Network is unreachable
            if (localTunnel != null) {
                NatSessionManager.RemoveSession(localTunnel);
                localTunnel.dispose();
            }
        } catch (Exception e) {
            e.printStackTrace();

            if (localTunnel != null) {
                NatSessionManager.RemoveSession(localTunnel);
                localTunnel.dispose();
            }
        }
    }

}
