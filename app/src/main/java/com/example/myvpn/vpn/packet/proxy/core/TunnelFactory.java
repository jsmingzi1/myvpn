package com.example.myvpn.vpn.packet.proxy.core;

import android.util.Log;

import com.example.myvpn.vpn.packet.proxy.tunnel.Config;
import com.example.myvpn.vpn.packet.proxy.tunnel.RawTunnel;
import com.example.myvpn.vpn.packet.proxy.tunnel.Tunnel;
import com.example.myvpn.vpn.packet.proxy.tunnel.httpconnect.HttpConnectConfig;
import com.example.myvpn.vpn.packet.proxy.tunnel.httpconnect.HttpConnectTunnel;
import com.example.myvpn.vpn.packet.proxy.tunnel.shadowsocks.ShadowsocksConfig;
import com.example.myvpn.vpn.packet.proxy.tunnel.shadowsocks.ShadowsocksTunnel;

import java.net.InetSocketAddress;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

public class TunnelFactory {

    public static Tunnel wrap(SocketChannel channel, Selector selector) {
        return new RawTunnel(channel, selector);
    }

    public static Tunnel createTunnelByConfig(InetSocketAddress destAddress, Selector selector) throws Exception {
        if (destAddress.isUnresolved()) {
            Log.w("createTunnelByConfig", "destAddress.isUnresolved() "+destAddress.getHostName());
            Config config = ProxyConfig.Instance.getDefaultTunnelConfig(destAddress);
            if (config instanceof HttpConnectConfig) {
                //Log.w("createTunnelByConfig", "HttpConnectConfig tunnel is created");
                return new HttpConnectTunnel((HttpConnectConfig) config, selector);
            } else if (config instanceof ShadowsocksConfig) {
                return new ShadowsocksTunnel((ShadowsocksConfig) config, selector);
            }
            throw new Exception("The config is unknow.");
        } else {
            return new RawTunnel(destAddress, selector);
        }

    }

}
