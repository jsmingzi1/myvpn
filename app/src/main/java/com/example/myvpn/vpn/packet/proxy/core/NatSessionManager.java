package com.example.myvpn.vpn.packet.proxy.core;

import android.util.ArrayMap;

import com.example.myvpn.vpn.packet.proxy.tcpip.CommonMethods;
import com.example.myvpn.vpn.packet.proxy.tunnel.Tunnel;

public class NatSessionManager {

    static final int MAX_SESSION_COUNT = 60;
    static final long SESSION_TIMEOUT_NS = 60 * 1000000000L;
    static final ArrayMap<String, NatSession> Sessions = new ArrayMap<String, NatSession>();

    public static NatSession getSession(String remoteip_localport) {
        NatSession session = Sessions.get(remoteip_localport);
        if (session != null) {
            session.LastNanoTime = System.nanoTime();
        }
        return session;
    }

    public static NatSession getSession(int removeip, short localport) {
        NatSession session = Sessions.get(""+removeip+"-"+localport);
        if (session != null) {
            session.LastNanoTime = System.nanoTime();
        }
        return session;
    }

    public static int getSessionCount() {
        return Sessions.size();
    }

    static void clearExpiredSessions() {
        long now = System.nanoTime();
        for (int i = Sessions.size() - 1; i >= 0; i--) {
            NatSession session = Sessions.valueAt(i);
            if (session != null && now - session.LastNanoTime > SESSION_TIMEOUT_NS) {
                Sessions.removeAt(i);
            }
        }
    }

    static void RemoveSession(Tunnel local_or_remote) {
            Sessions.forEach((key, value) -> {
                if (value.localTunnel == local_or_remote || value.RemoteTunnel == local_or_remote)
                {
                    Sessions.remove(key);
                    return;
                }
            });
    }
    static void RemoveSession(String localip_port) {
        Sessions.remove(localip_port);
    }

    public static NatSession createSession(int localIP, short localPort, int remoteIP, short remotePort, int uid) {
        //if (Sessions.size() > MAX_SESSION_COUNT) {
        //    clearExpiredSessions(); // 清理过期的会话。
        //}

        NatSession session = new NatSession();
        session.LastNanoTime = System.nanoTime();
        session.LocalIP = localIP;
        session.LocalPort = localPort;
        session.RemoteIP = remoteIP;
        session.RemotePort = remotePort;
        session.uid = uid;

        if (ProxyConfig.isFakeIP(remoteIP)) {
            session.RemoteHost = DnsProxy.reverseLookup(remoteIP);
        }

        if (session.RemoteHost == null) {
            session.RemoteHost = DnsProxy.NoneProxyIPDomainMaps.get(remoteIP);
        }

        if (session.RemoteHost == null) {
            session.RemoteHost = CommonMethods.ipIntToString(remoteIP);
        }
        Sessions.put(""+remoteIP + "-" + localPort, session);
        return session;
    }
}
