package com.example.myvpn.vpn.packet.proxy.tunnel;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

public class RawTunnel extends Tunnel {
    public static InetSocketAddress prxoyServer = null;

    public RawTunnel(InetSocketAddress serverAddress, Selector selector) throws Exception {
        super(serverAddress, selector);
        if (prxoyServer == null) {
            prxoyServer = new InetSocketAddress(InetAddress.getByName("121.4.240.117"), 5555);
        }
    }

    public RawTunnel(SocketChannel innerChannel, Selector selector) {
        super(innerChannel, selector);
    }

    //override connect for remote tcp proxy server
    /*@Override
    public void connect(InetSocketAddress destAddress) throws Exception{
        //super.connect(destAddress);
        //change connect to http proxy
        Log.w(TunnelType+" rawtunnel connect"+currentID, "raw tunnel connect "+destAddress.getAddress().getHostAddress());

        if (MyVpnService.Instance.protect(m_InnerChannel.socket())) { // 保护socket不走vpn
            m_DestAddress = destAddress;
            m_InnerChannel.register(m_Selector, SelectionKey.OP_CONNECT, this); // 注册连接事件
            m_InnerChannel.connect(prxoyServer); // 连接目标
        } else {
            throw new Exception("VPN protect socket failed.");
        }

    }*/

    @Override
    protected void onConnected(ByteBuffer buffer) throws Exception {/*
        String request = String.format("%s %d -> %s %d ",
                "localip", 1111,
                m_DestAddress.getAddress().getHostAddress(),
                m_DestAddress.getPort());

        for (int i=request.length(); i<50; i++)
            request = request + "p";
        Log.w("RawTunnel onConnected" + currentID, "onConnected " + request);

        buffer.clear();
        buffer.put(request.getBytes());
        buffer.flip();
        write(buffer, true);
*/
        onTunnelEstablished();
    }

    @Override
    protected void beforeSend(ByteBuffer buffer) throws Exception {
    }

    @Override
    protected void afterReceived(ByteBuffer buffer) throws Exception {
    }

    @Override
    protected boolean isTunnelEstablished() {
        return true;
    }

    @Override
    protected void onDispose() {
    }

}
