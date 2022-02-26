package com.example.myvpn.vpn.packet;

import android.util.Log;

import com.example.myvpn.Tools;
import com.example.myvpn.vpn.MyVpnConnection;
import com.example.myvpn.vpn.MyVpnService;
import com.example.myvpn.vpn.packet.proxy.core.DnsProxy;
import com.example.myvpn.vpn.packet.proxy.core.HttpHostHeaderParser;
import com.example.myvpn.vpn.packet.proxy.core.NatSession;
import com.example.myvpn.vpn.packet.proxy.core.NatSessionManager;
import com.example.myvpn.vpn.packet.proxy.core.ProxyConfig;
import com.example.myvpn.vpn.packet.proxy.core.TcpProxyServer;
import com.example.myvpn.vpn.packet.proxy.dns.DnsPacket;
import com.example.myvpn.vpn.packet.proxy.tcpip.CommonMethods;
import com.example.myvpn.vpn.packet.proxy.tcpip.IPHeader;
import com.example.myvpn.vpn.packet.proxy.tcpip.TCPHeader;
import com.example.myvpn.vpn.packet.proxy.tcpip.UDPHeader;
import com.example.myvpn.vpn.packet.proxy.tunnel.Tunnel;
import com.google.common.base.Splitter;

import org.apache.commons.io.IOUtils;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Arrays;


public class PacketProcess {

    public static PacketProcess Instance = null;
    //public static String ProxyUrl;
    //public static boolean IsRunning = false;
    //public static DatabaseReader maxmindReader;
    private int DEFAULT_IDLE_TIME = 1;
    private int MAX_IDLE_TIME = 20;

    private static int ID;
    private static int LOCAL_IP;


    private boolean blockingMode = true;
    private TcpProxyServer m_TcpProxyServer;
    private DnsProxy m_DnsProxy;

    private byte[] m_Packet;
    private IPHeader m_IPHeader;
    private TCPHeader m_TCPHeader;
    private UDPHeader m_UDPHeader;
    private ByteBuffer m_DNSBuffer;

    private long m_SentBytes;
    private long m_ReceivedBytes;

    public PacketProcess() {
        ID++;
        m_Packet = new byte[Tunnel.BUFFER_SIZE];
        m_IPHeader = new IPHeader(m_Packet, 0);
        m_TCPHeader = new TCPHeader(m_Packet, 20);
        m_UDPHeader = new UDPHeader(m_Packet, 20);
        m_DNSBuffer = ((ByteBuffer) ByteBuffer.wrap(m_Packet).position(28)).slice();
        Instance = this;
        LOCAL_IP = CommonMethods.ipStringToInt(MyVpnConnection.Instance.LOCAL_ADDRESS);
        runProxy();
        Log.w("PacketProcess", "calling PacketProcess");
    }


    //write back the response of DNS
    public void sendUDPPacket(IPHeader ipHeader, UDPHeader udpHeader) {
        try {
            CommonMethods.ComputeUDPChecksum(ipHeader, udpHeader);
            MyVpnConnection.Instance.m_VPNOutputStream.write(ipHeader.m_Data, ipHeader.m_Offset, ipHeader.getTotalLength());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }




    public void runProxy() {
        try {
            m_TcpProxyServer = new TcpProxyServer(0);
            m_TcpProxyServer.start();

            m_DnsProxy = new DnsProxy();
            m_DnsProxy.start();

            //initilize proxy config
            ProxyConfig.AppInstallID = MyVpnService.Instance.getAppInstallID(); // 获取安装ID
            ProxyConfig.AppVersion = MyVpnService.Instance.getVersionName(); // 获取版本号

            FileInputStream fis = null;
            try {
                fis = MyVpnService.Instance.openFileInput(MyVpnService.configFile);
                String rules = IOUtils.toString(fis, Charset.defaultCharset());
                String[] lines = Splitter.onPattern("\r?\n").splitToList(rules).toArray(new String[0]);
                int ruleCount = ProxyConfig.Instance.loadProxyRuleFromLines(lines);
                MyVpnService.Instance.writeLog("Load config from file done, " + ruleCount + " rules");
            } catch (Exception e) {
                String errString = e.getMessage();
                if (errString == null || errString.isEmpty()) {
                    errString = e.toString();
                }
                MyVpnService.Instance.writeLog("Load failed with error: %s", errString);
            } finally {
                if (fis != null) {
                    try {
                        fis.close();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }

            //writeLog("Blacklist apps " + String.join(", ", ProxyConfig.Instance.getProcessListByAction("block")));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }




    public boolean processPacket(boolean bOut, byte[] packet, int offset, int length, FileOutputStream out1) throws Exception {
        if (bOut==false) {
            return false; //for packet which received from vpn server, no need special process
        }
        //if (true) return false;
        //return false;
        Arrays.fill(m_Packet, (byte)0);
        System.arraycopy(packet, offset, m_Packet, 0, length);
        return onIPPacketReceived(m_IPHeader, length);
    }

        /**
         * 收到ip数据包
         *
         * @param ipHeader
         * @param size
         * @throws IOException
         * return value: true - processed, false - not process
         */
    boolean onIPPacketReceived(IPHeader ipHeader, int size) throws IOException {
        switch (ipHeader.getProtocol()) {
            case IPHeader.TCP:
                //if (true) return false;
                TCPHeader tcpHeader = m_TCPHeader;
                tcpHeader.m_Offset = ipHeader.getHeaderLength();

                int uid = 0;
                /**
                 * 以下代码易导致性能问题
                 */
                /*if (ProxyConfig.Instance.firewallMode) {
                    try {
                        uid = TcpUdpClientInfo.getUidForConnectionFromJni(
                                ipHeader.getVersion(), IPHeader.TCP,
                                CommonMethods.ipIntToString(ipHeader.getSourceIP()), tcpHeader.getSourcePort(),
                                CommonMethods.ipIntToString(ipHeader.getDestinationIP()), tcpHeader.getDestinationPort()
                        );
                        if (uid > 0) {
                            String packageName = TcpUdpClientInfo.getPackageNameForUid(MyVpnService.Instance.getPackageManager(), uid);
                            if (packageName != null && ProxyConfig.Instance.getProcessListByAction("block").contains(packageName)) {
                                return true;
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }*/

                int source_ip=ipHeader.getSourceIP();
                if (/*ipHeader.getSourceIP() == LOCAL_IP*/true) {
                    Log.w("PacketProcess", "call onIPPacketReceived " + m_IPHeader.getProtocol() + "," + Tools.ipInt2Str(m_IPHeader.getSourceIP())
                            +":"+TcpProxyServer.getUnsignedShort(m_TCPHeader.getSourcePort())
                            + "-->"+Tools.ipInt2Str(m_IPHeader.getDestinationIP())
                            +":" + TcpProxyServer.getUnsignedShort(m_TCPHeader.getDestinationPort())+ ", LOCAL_IP is "+Tools.ipInt2Str(LOCAL_IP));

                    if (tcpHeader.getSourcePort() == m_TcpProxyServer.Port) { // 收到本地TCP服务器数据
                        Log.w("PacketProcess", "received packet from local tcp server");

                        NatSession session = NatSessionManager.getSession(ipHeader.getDestinationIP()
                                +"-"+tcpHeader.getDestinationPort());
                        assert(session!=null);
                        if (session != null) {
                            ipHeader.setSourceIP(/*ipHeader.getDestinationIP()*/session.RemoteIP);
                            tcpHeader.setSourcePort(session.RemotePort);
                            //ipHeader.setDestinationIP(/*LOCAL_IP*/session.LocalIP);

                            CommonMethods.ComputeTCPChecksum(ipHeader, tcpHeader);
                            // write to tun
                            MyVpnConnection.Instance.m_VPNOutputStream.write(ipHeader.m_Data, ipHeader.m_Offset, size);
                            m_ReceivedBytes += size;
                        }
                        else {
                            Log.e("packetprocess", "received from local tcp proxy, but not found session");
                        }
                        return true;
                    } else {
                        // 添加端口映射
                        Log.w("PacketProcess", "forward received packet to tcpproxyserver");

                        int sourcePort = tcpHeader.getSourcePort();

                        NatSession session = NatSessionManager.getSession(""+source_ip + "-" + sourcePort);
                        if (session == null) {
                            session = NatSessionManager.createSession(source_ip, (short)sourcePort, ipHeader.getDestinationIP(), tcpHeader.getDestinationPort(), uid, ipHeader.m_Data, 0, size);
                        }

                        session.LastNanoTime = System.nanoTime();
                        session.PacketSent++; // 注意顺序

                        int tcpDataSize = ipHeader.getDataLength() - tcpHeader.getHeaderLength();
                        if (session.PacketSent == 2 && tcpDataSize == 0) {
                            //return true; // 丢弃tcp握手的第二个ACK报文。因为客户端发数据的时候也会带上ACK，这样可以在服务器Accept之前分析出HOST信息。
                        }

                        // 分析数据，找到host
                        if (session.BytesSent == 0 && tcpDataSize > 10) {
                            int dataOffset = tcpHeader.m_Offset + tcpHeader.getHeaderLength();
                            String host = HttpHostHeaderParser.parseHost(tcpHeader.m_Data, dataOffset, tcpDataSize);
                            if (host != null) {
                                //Log.w("PacketProcess "+TcpProxyServer.getUnsignedShort((short)portKey), "http header is "+host);
                                if (host.contains("_")) {
                                    session.RemoteHost = host.split("_")[0];
                                    session.httpAction = host.split("_")[1];
                                    Log.w("PacketProcess", "http header is host "+session.RemoteHost
                                            +", action "+session.httpAction);
                                }
                                else {
                                    session.RemoteHost = host;
                                    //return true;
                                }


                                /*if (host.equals("ifconfig.me") && session.firstData != null) {
                                    MyVpnConnection.Instance.m_VPNOutputStream.write(session.firstData);
                                    session.firstData = null;
                                    return false;
                                }*/
                            }
                            else {
                                // for non-http(s) message
                                assert(false);
                                Log.w("PacketProcess", "parse host is empty");
                                if (session.firstData != null) {
                                    MyVpnConnection.Instance.m_VPNOutputStream.write(session.firstData);
                                    session.firstData = null;
                                    Log.w("packetprocess", "none http(s) message");
                                    return false;
                                }
                            }


                        }

                        // 转发给本地TCP服务器
                        //ipHeader.setSourceIP(ipHeader.getDestinationIP());
                        ipHeader.setDestinationIP(LOCAL_IP);
                        tcpHeader.setDestinationPort(m_TcpProxyServer.Port);
                        CommonMethods.ComputeTCPChecksum(ipHeader, tcpHeader);

                        Log.w("PacketProcess", "finally forward received packet to tcpproxyserver for ip "
                                + DnsProxy.reverseLookupNoneProxy(ipHeader.getSourceIP())+", "+tcpHeader.toString());

                        //byte [] aa = new byte[size];
                        //System.arraycopy(ipHeader.m_Data, ipHeader.m_Offset, aa, 0, size);
                        //Log.w("PacketProcess", "rewrite the destination ip and port:" + ipHeader.m_Offset + "," + size
                        //        +", ip packet length is " + ipHeader.getTotalLength()+","+ipHeader.getHeaderLength()+","+ipHeader.getDataLength()
                        //        +":"+ gzipcls.bytesToHexFun1(aa));
                        MyVpnConnection.Instance.m_VPNOutputStream.write(ipHeader.m_Data, ipHeader.m_Offset, size);
                        session.BytesSent += tcpDataSize; // 注意顺序
                        m_SentBytes += size;

                        return true;
                    }
                }
                else
                {
                    Log.w("PacketProcess", "received package which source ip not local ip " + DnsProxy.reverseLookupNoneProxy(ipHeader.getSourceIP())
                        + ", " + Tools.ipInt2Str(ipHeader.getSourceIP()) + "->" + Tools.ipInt2Str(ipHeader.getDestinationIP()));
                }
                // there's another condition that received packet, not sure why, ignore it
                //return true;
                break;
            case IPHeader.UDP:
                // 转发DNS数据包
                UDPHeader udpHeader = m_UDPHeader;
                udpHeader.m_Offset = ipHeader.getHeaderLength();

                if (ipHeader.getSourceIP() == LOCAL_IP && udpHeader.getDestinationPort() == 53) {

                    m_DNSBuffer.clear();
                    m_DNSBuffer.limit(ipHeader.getDataLength() - 8);

                    DnsPacket dnsPacket = DnsPacket.FromBytes(m_DNSBuffer);
                    if (dnsPacket != null && dnsPacket.Header.QuestionCount > 0) {
                        m_DnsProxy.onDnsRequestReceived(ipHeader, udpHeader, dnsPacket);
                    }
                    return true;
                }
                else if(udpHeader.getDestinationPort() == 53){
                    Log.w("UDP DNS not local ip", "source ip is "+Tools.ipInt2Str(ipHeader.getSourceIP()));
                }
                //return true;
                break;
            default:
                break;
        }
        return false;
    }

    private void dispose() {
        // 停止TcpServer
        if (m_TcpProxyServer != null) {
            m_TcpProxyServer.stop();
            m_TcpProxyServer = null;
        }

        // 停止DNS解析器
        if (m_DnsProxy != null) {
            m_DnsProxy.stop();
            m_DnsProxy = null;
        }
    }

    // https://github.com/Genymobile/gnirehtet/blob/master/app/src/main/java/com/genymobile/gnirehtet/Forwarder.java#L144-L168


}
