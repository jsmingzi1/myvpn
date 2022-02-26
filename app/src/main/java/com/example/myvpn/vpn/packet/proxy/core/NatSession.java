package com.example.myvpn.vpn.packet.proxy.core;

import com.example.myvpn.vpn.packet.proxy.tunnel.Tunnel;

public class NatSession {
    public int LocalIP; //add
    public short LocalPort; //add
    public Tunnel localTunnel; //add
    public int RemoteIP;
    public short RemotePort;
    public Tunnel RemoteTunnel; //add
    public String RemoteHost;
    public int BytesSent;
    public int PacketSent;
    public long LastNanoTime;
    public int uid;
    public byte[] firstData; //for tcp message, store the first ACK msg
    public String httpAction;
}
