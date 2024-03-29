package com.example.myvpn.vpn.packet.proxy.tunnel;

import android.annotation.SuppressLint;
import android.util.Log;

import com.example.myvpn.vpn.MyVpnService;
import com.example.myvpn.vpn.packet.proxy.core.NatSession;
import com.example.myvpn.vpn.packet.proxy.tunnel.httpconnect.HttpConnectTunnel;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

public abstract class Tunnel {

    public final static int BUFFER_SIZE = 20000;
    final static ByteBuffer GL_BUFFER = ByteBuffer.allocate(BUFFER_SIZE);
    public static long SessionCount;

    protected abstract void onConnected(ByteBuffer buffer) throws Exception;

    protected abstract boolean isTunnelEstablished();

    protected abstract void beforeSend(ByteBuffer buffer) throws Exception;

    protected abstract void afterReceived(ByteBuffer buffer) throws Exception;

    protected abstract void onDispose();

    protected SocketChannel m_InnerChannel;
    private ByteBuffer m_SendRemainBuffer;
    protected Selector m_Selector;
    private Tunnel m_BrotherTunnel;
    private boolean m_Disposed;
    protected InetSocketAddress m_ServerEP;
    protected InetSocketAddress m_DestAddress;
    public String TunnelType = "local"; //default is local, another is remote
    public long currentID  = 0;
    public NatSession session=null;


    public Tunnel(SocketChannel innerChannel, Selector selector) {
        this.m_InnerChannel = innerChannel;
        this.m_Selector = selector;
        currentID = SessionCount++;
    }

    public Tunnel(InetSocketAddress serverAddress, Selector selector) throws IOException {
        SocketChannel innerChannel = SocketChannel.open();
        innerChannel.configureBlocking(false);
        this.m_InnerChannel = innerChannel;
        this.m_Selector = selector;
        this.m_ServerEP = serverAddress;
        currentID = SessionCount++;

    }

    public void setBrotherTunnel(Tunnel brotherTunnel) {
        m_BrotherTunnel = brotherTunnel;
    }

    public void connect(InetSocketAddress destAddress) throws Exception {
        Log.w(TunnelType + " connect"+currentID, "connect is called "+destAddress.isUnresolved()+","+destAddress.getHostName());

        if (MyVpnService.Instance.protect(m_InnerChannel.socket())) { // 保护socket不走vpn
            m_DestAddress = destAddress;
            m_InnerChannel.register(m_Selector, SelectionKey.OP_CONNECT, this); // 注册连接事件
            m_InnerChannel.connect(m_ServerEP); // 连接目标
        } else {

            throw new Exception("VPN protect socket failed.");
        }
    }

    protected void beginReceive() throws Exception {
        if (m_InnerChannel.isBlocking()) {
            m_InnerChannel.configureBlocking(false);
        }
        m_InnerChannel.register(m_Selector, SelectionKey.OP_READ, this); // 注册读事件
    }


    protected boolean write(ByteBuffer buffer, boolean copyRemainData) throws Exception {
        Log.w(TunnelType + " connect"+currentID, "write is called "+(m_DestAddress==null?"null":m_DestAddress.getHostName()));

        int bytesSent;
        while (buffer.hasRemaining()) {
            bytesSent = m_InnerChannel.write(buffer);
            if (bytesSent == 0) {
                break; // 不能再发送了，终止循环
            }
        }

        if (buffer.hasRemaining()) { // 数据没有发送完毕
            if (copyRemainData) { // 拷贝剩余数据，然后侦听写入事件，待可写入时写入
                // 拷贝剩余数据
                if (m_SendRemainBuffer == null) {
                    m_SendRemainBuffer = ByteBuffer.allocate(buffer.capacity());
                }
                m_SendRemainBuffer.clear();
                m_SendRemainBuffer.put(buffer);
                m_SendRemainBuffer.flip();
                m_InnerChannel.register(m_Selector, SelectionKey.OP_WRITE, this); // 注册写事件
            }
            return false;
        } else { // 发送完毕了
            return true;
        }
    }

    protected void onTunnelEstablished() throws Exception {
        this.beginReceive(); // 开始接收数据
        m_BrotherTunnel.beginReceive(); // 兄弟也开始收数据
    }

    //this is used for remotetunnel
    @SuppressLint("DefaultLocale")
    public void onConnectable() {
        Log.w(TunnelType + " onConnectable"+currentID, "onConnectable is called");

        try {
            if (m_InnerChannel.finishConnect()) { // 连接成功
                onConnected(GL_BUFFER); // 通知子类TCP已连接，子类可以根据协议实现握手等
            } else { // 连接失败

                this.dispose();
            }
        } catch (ConnectException e) {
            // Connection timed out
            this.dispose();
        } catch (SocketException e) {
            this.dispose();
        } catch (Exception e) {

            this.dispose();
        }
    }

    public void onReadable(SelectionKey key) {
        if (this instanceof RawTunnel) {
            Log.w(TunnelType + " tunnel onReadable"+currentID, "this is rawtunnel");
        }
        else if (this instanceof HttpConnectTunnel) {
            Log.w(TunnelType + " tunnel onReadable"+currentID, "this is httptunnel");
        }
        try {
            ByteBuffer buffer = GL_BUFFER;
            buffer.clear();
            int bytesRead = m_InnerChannel.read(buffer);
            //if (this instanceof HttpConnectTunnel)
            //    Log.w(TunnelType + " onReadable"+currentID, "httptunnel onReadable content is "+new String(buffer.array(), 0, bytesRead));
            if (bytesRead > 0) {
                buffer.flip();
                afterReceived(buffer); // 先让子类处理，例如解密数据
                if (isTunnelEstablished() && buffer.hasRemaining()) { // 将读到的数据，转发给兄弟
                    m_BrotherTunnel.beforeSend(buffer); // 发送之前，先让子类处理，例如做加密等
                    if (!m_BrotherTunnel.write(buffer, true)) {
                        key.cancel(); // 兄弟吃不消，就取消读取事件


                    }
                }
            } else if (bytesRead < 0) {
                this.dispose(); // 连接已关闭，释放资源。
            }
        } catch (IOException e) {
            // Connection reset by peer
            e.printStackTrace();
            this.dispose();
        } catch (Exception e) {
            e.printStackTrace();
            this.dispose();
        }
    }

    public void onWritable(SelectionKey key) {
        if (this instanceof RawTunnel) {
            Log.w(TunnelType + " tunnel onWritable"+currentID, "this is rawtunnel");
        }
        else if (this instanceof HttpConnectTunnel) {
            Log.w(TunnelType + " tunnel onWritable"+currentID, "this is httptunnel");
        }
        try {
            this.beforeSend(m_SendRemainBuffer); // 发送之前，先让子类处理，例如做加密等
            if (this.write(m_SendRemainBuffer, false)) { // 如果剩余数据已经发送完毕
                key.cancel(); // 取消写事件
                if (isTunnelEstablished()) {
                    m_BrotherTunnel.beginReceive(); // 这边数据发送完毕，通知兄弟可以收数据了
                } else {
                    this.beginReceive(); // 开始接收代理服务器响应数据
                }
            }
        } catch (Exception e) {
            this.dispose();
        }
    }

    public void dispose() {
        disposeInternal(true);
    }

    void disposeInternal(boolean disposeBrother) {
        if (m_Disposed) {
            return;
        }

        try {
            m_InnerChannel.close();
        } catch (Exception e) {
        }

        if (m_BrotherTunnel != null && disposeBrother) {
            m_BrotherTunnel.disposeInternal(false); // 把兄弟的资源也释放了。
        }

        m_InnerChannel = null;
        m_SendRemainBuffer = null;
        m_Selector = null;
        m_BrotherTunnel = null;
        m_Disposed = true;
        SessionCount--;

        onDispose();
    }
}
