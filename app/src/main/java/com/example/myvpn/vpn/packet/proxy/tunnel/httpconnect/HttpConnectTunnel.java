package com.example.myvpn.vpn.packet.proxy.tunnel.httpconnect;

import android.util.Log;

import com.example.myvpn.Tools;
import com.example.myvpn.vpn.MyVpnService;
import com.example.myvpn.vpn.packet.proxy.core.ProxyConfig;
import com.example.myvpn.vpn.packet.proxy.tunnel.Tunnel;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;

//import com.example.myvpn.vpn.packet.proxy.core.LocalVpnService;
// this http connect tunnel is for proxy, if traffics no need proxy, use rawtunnel.
public class HttpConnectTunnel extends Tunnel {

    private boolean m_TunnelEstablished;
    private HttpConnectConfig m_Config;
    public static InetSocketAddress prxoyServer = null;
    public String httpAction = null; //if this is set, then it's http, no need "CONNECT TUNNEL, just use normal proxy, or need CONNECT TUNNEL
    //http proxy, the normal one only support http, for https only can use CONNECT TUNNEL

    public HttpConnectTunnel(HttpConnectConfig config, Selector selector) throws IOException {
        super(config.ServerAddress, selector);
        m_Config = config;

        if (prxoyServer == null) {
            //prxoyServer = new InetSocketAddress("142.93.24.89", 3128);
            //prxoyServer = new InetSocketAddress("192.227.147.146", 3128);
            prxoyServer = ProxyConfig.Instance.getDefaultProxy().ServerAddress;
            Log.w("HttpConnectTunnel", "init the default proxy "+prxoyServer.getAddress().getHostAddress());
            //prxoyServer = new InetSocketAddress("121.4.240.117", 443);
        }
        currentID++;
    }


    @Override
    public void connect(InetSocketAddress destAddress) throws Exception{
        //super.connect(destAddress);
        //change connect to http proxy
        Log.w(TunnelType+" httptunnel connect"+currentID, "http tunnel connect "+destAddress.getHostName());

        if (MyVpnService.Instance.protect(m_InnerChannel.socket())) { // 保护socket不走vpn
            m_DestAddress = destAddress;
            m_InnerChannel.register(m_Selector, SelectionKey.OP_CONNECT, this); // 注册连接事件
            m_InnerChannel.connect(prxoyServer); // 连接目标
            Log.w("httptunnel", "connect proxyserver "+prxoyServer.getAddress().getHostAddress()+":"
            +prxoyServer.getPort()+","
            +prxoyServer.getHostName());
        } else {

            throw new Exception("VPN protect socket failed.");
        }

    }

    @Override
    protected void onConnected(ByteBuffer buffer) throws Exception {
        String function_header=TunnelType+" HttpConnectTunnel "+Tools.ipInt2Str(session.LocalIP)+":"+session.LocalPort
                +"->"+Tools.ipInt2Str(session.RemoteIP)+":"+session.RemotePort;
        if (session.RemoteHost.equals(Tools.ipInt2Str(session.RemoteIP))) {
            //means, http first establish connection, then will send http message
            Log.w(function_header, "remote host == remote ip, not decode host yet, no need send message to proxy");
            return;
        }

        if (httpAction != null) {
            // for http, just use normal proxy
            Log.w(function_header, "this is baidu.com/mtool.chinaz.com, skip it");
            m_TunnelEstablished = true;
            onTunnelEstablished();
            return;
        }

        // for https, need first create CONNECT TUNNEL, then can transfer packets
        String request = String.format("CONNECT %s:%d HTTP/1.0\r\nProxy-Connection: keep-alive\r\nUser-Agent: %s\r\nX-App-Install-ID: %s\r\n\r\n",
                    m_DestAddress.getHostName(),
                    m_DestAddress.getPort(),
                    ProxyConfig.Instance.getUserAgent(),
                    ProxyConfig.AppInstallID);
        Log.w("HttpTunnel-onConnected " + currentID, "onConnected " + request);

        buffer.clear();
        buffer.put(request.getBytes());
        buffer.flip();
        if (this.write(buffer, true)) { // 发送连接请求到代理服务器
            this.beginReceive(); // 开始接收代理服务器响应数据
        }
    }

    void trySendPartOfHeader(ByteBuffer buffer) throws Exception {
        int bytesSent = 0;
        if (buffer.remaining() > 10) {
            int pos = buffer.position() + buffer.arrayOffset();
            String firString = new String(buffer.array(), pos, 10)/*.toUpperCase()*/;
            Log.w(TunnelType+" HttpTunnel trySendPartOfHeader"+currentID, "trySendPartOfHeader is "+firString);
            if (firString.startsWith("GET /") || firString.startsWith("POST /")) {
                String newFirst = firString;
                if (firString.startsWith("GET /"))
                    newFirst = firString.replaceFirst("GET /", "GET http://"+m_DestAddress.getHostName()+"/");
                else
                    newFirst = firString.replaceFirst("POST /", "POST http://"+m_DestAddress.getHostName()+"/");
                int limit = buffer.limit();
                Log.w(TunnelType+" HttpTunnel trySendPartOfHeader"+currentID, "trySendPartOfHeader-1 is "+newFirst);

                buffer.limit(buffer.position() + 10);
                buffer.position(buffer.position() + 10);
                super.write(ByteBuffer.wrap(newFirst.getBytes()), false);
                //super.write(buffer, false);
                bytesSent = 10 - buffer.remaining();
                buffer.limit(limit);
                if (ProxyConfig.IS_DEBUG)
                    MyVpnService.Instance.writeLog("Send %d bytes(%s) to %s", bytesSent, firString, m_DestAddress);
            }
        }
    }


    @Override
    protected void beforeSend(ByteBuffer buffer) throws Exception {
        Log.w(TunnelType+" HttpTunnel beforeSend"+currentID, "beforeSend");
        //for https proxy, nothing need do it, but for http proxy, need change request header
        if (httpAction != null) {//http msg, not https
            trySendPartOfHeader(buffer); // 尝试发送请求头的一部分，让请求头的host在第二个包里面发送，从而绕过机房的白名单机制。
        }
    }

    @Override
    protected void afterReceived(ByteBuffer buffer) throws Exception {
        Log.w(TunnelType+" HttpTunnel afterReceived"+currentID, "afterReceived");
        //for connect tunnel, need abandon this response, but for normal http proxy, will have problem.
        if (httpAction != null) {
            String response = new String(buffer.array(), buffer.position(), buffer.limit()-buffer.position());
            Log.w(TunnelType+" HttpTunnel afterReceived"+currentID, "afterReceived " + response);
        }
        if (!m_TunnelEstablished) {
            // 收到代理服务器响应数据
            // 分析响应并判断是否连接成功
            String response = new String(buffer.array(), buffer.position(), 12);
            if (response.matches("^HTTP/1.[01] 200$") || true) {
                Log.w(TunnelType+" HttpTunnel afterReceived"+currentID, "http proxy response is "+response + ", request host is " + m_DestAddress.getHostName());
                buffer.limit(buffer.position());
            } else {
                Log.w(TunnelType+" HttpTunnelERROR"+currentID, "http proxy response is "+response + ", request host is " + m_DestAddress.getHostName());

                throw new Exception(String.format("Proxy server response an error: %s", response));
            }

            m_TunnelEstablished = true;
            super.onTunnelEstablished();
        }
    }

    @Override
    protected boolean isTunnelEstablished() {
        return m_TunnelEstablished;
    }

    @Override
    protected void onDispose() {
        m_Config = null;
    }

}
