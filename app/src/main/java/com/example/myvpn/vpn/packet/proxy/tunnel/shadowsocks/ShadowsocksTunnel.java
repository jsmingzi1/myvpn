package com.example.myvpn.vpn.packet.proxy.tunnel.shadowsocks;

import com.example.myvpn.vpn.packet.proxy.tunnel.Tunnel;
import com.example.myvpn.vpn.packet.proxy.tunnel.shadowsocks.crypto.CryptFactory;
import com.example.myvpn.vpn.packet.proxy.tunnel.shadowsocks.crypto.ICrypt;

import java.nio.ByteBuffer;
import java.nio.channels.Selector;

public class ShadowsocksTunnel extends Tunnel {

    private ICrypt m_Encryptor;
    private ShadowsocksConfig m_Config;
    private boolean m_TunnelEstablished;

    public ShadowsocksTunnel(ShadowsocksConfig config, Selector selector) throws Exception {
        super(config.ServerAddress, selector);
        m_Config = config;
        m_Encryptor = CryptFactory.get(m_Config.EncryptMethod, m_Config.Password);
    }

    @Override
    protected void onConnected(ByteBuffer buffer) throws Exception {
        buffer.clear();

        // https://shadowsocks.org/en/spec/protocol.html
        buffer.put((byte) 0x03); // domain
        byte[] domainBytes = m_DestAddress.getHostName().getBytes();
        buffer.put((byte) domainBytes.length); // domain length;
        buffer.put(domainBytes);
        buffer.putShort((short) m_DestAddress.getPort());
        buffer.flip();
        byte[] _header = new byte[buffer.limit()];
        buffer.get(_header);

        buffer.clear();
        buffer.put(m_Encryptor.encrypt(_header));
        buffer.flip();

        if (write(buffer, true)) {
            m_TunnelEstablished = true;
            onTunnelEstablished();
        } else {
            m_TunnelEstablished = true;
            this.beginReceive();
        }
    }

    @Override
    protected boolean isTunnelEstablished() {
        return m_TunnelEstablished;
    }

    @Override
    protected void beforeSend(ByteBuffer buffer) throws Exception {
        byte[] bytes = new byte[buffer.limit()];
        buffer.get(bytes);

        byte[] newBytes = m_Encryptor.encrypt(bytes);

        buffer.clear();
        buffer.put(newBytes);
        buffer.flip();
    }

    @Override
    protected void afterReceived(ByteBuffer buffer) throws Exception {
        byte[] bytes = new byte[buffer.limit()];
        buffer.get(bytes);
        byte[] newBytes = m_Encryptor.decrypt(bytes);
        buffer.clear();
        buffer.put(newBytes);
        buffer.flip();
    }

    @Override
    protected void onDispose() {
        m_Config = null;
        m_Encryptor = null;
    }

}
