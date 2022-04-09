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
        Log.w("PacketProcess", "create PacketProcess");
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

    public boolean processPacket(byte[] packet, int offset, int length) throws Exception {
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

                TCPHeader tcpHeader = m_TCPHeader;
                tcpHeader.m_Offset = ipHeader.getHeaderLength();
                int tcpDataSize = ipHeader.getDataLength() - tcpHeader.getHeaderLength();
                String function_header="PacketProcess "+Tools.ipInt2Str(ipHeader.getSourceIP())+":"+Tools.convertShortPortReadbleInt(tcpHeader.getSourcePort())
                        +"->"+Tools.ipInt2Str(ipHeader.getDestinationIP())+":"+Tools.convertShortPortReadbleInt(tcpHeader.getDestinationPort())
                        +" size "+tcpDataSize;

                Log.w(function_header, "process tcp message");
                int uid = 0;
                /**
                 * 以下代码易导致性能问题
                 */

                int dest_ip=ipHeader.getDestinationIP();
                short sourcePort = tcpHeader.getSourcePort();
                if (ipHeader.getSourceIP() == LOCAL_IP/*true*/) {
                    Log.w(function_header, "call onIPPacketReceived " + m_IPHeader.getProtocol() + "," + Tools.ipInt2Str(m_IPHeader.getSourceIP())
                            +":"+(m_TCPHeader.getSourcePort())
                            + "-->"+Tools.ipInt2Str(m_IPHeader.getDestinationIP())
                            +":" + (m_TCPHeader.getDestinationPort())+ ", LOCAL_IP is "+Tools.ipInt2Str(LOCAL_IP));

                    if (tcpHeader.getSourcePort() == m_TcpProxyServer.Port) { // 收到本地TCP服务器数据
                        Log.w(function_header, "received packet from local tcp server");

                        NatSession session = NatSessionManager.getSession(ipHeader.getDestinationIP()
                                ,tcpHeader.getDestinationPort());
                        assert(session!=null);
                        if (session != null) {
                            Log.w(function_header+"-received from local tcpproxyserver session", "local "
                                    +Tools.ipInt2Str(session.LocalIP)+ " "
                                    +session.LocalPort + " "
                                    +Tools.ipInt2Str(session.RemoteIP) + " "
                                    +session.RemotePort);
                            ipHeader.setSourceIP(session.RemoteIP);
                            tcpHeader.setSourcePort(session.RemotePort);
                            ipHeader.setDestinationIP(LOCAL_IP);
                            tcpHeader.setDestinationPort(session.LocalPort);
                            CommonMethods.ComputeTCPChecksum(ipHeader, tcpHeader);
                            // write to tun
                            MyVpnConnection.Instance.m_VPNOutputStream.write(ipHeader.m_Data, ipHeader.m_Offset, size);
                            m_ReceivedBytes += size;
                        }
                        else {
                            Log.e(function_header+"-received from local tcpproxyserver", "session is empty, received from local tcp proxy, but not found session "
                            +Tools.ipInt2Str(ipHeader.getDestinationIP())
                                    +"("+ipHeader.getDestinationIP()+")"
                            +" "+tcpHeader.getDestinationPort());
                        }
                        return true;
                    }
                    else {
                        // 添加端口映射
                        Log.w(function_header, "forward received packet to tcpproxyserver");

                        NatSession session = NatSessionManager.getSession(dest_ip, sourcePort);
                        if (session == null) {
                            Log.w(function_header, "get session null, will create one");
                            session = NatSessionManager.createSession(ipHeader.getSourceIP(), sourcePort, dest_ip, tcpHeader.getDestinationPort(), uid);
                            session.protocolType=2;//0 HTTP, 1 HTTPS, 2 TCP, 3 UDP
                        } else {
                            Log.w(function_header, "get session succeed!!! with dest ip "+Tools.ipInt2Str(dest_ip) + "("+dest_ip+")"+", source port "+sourcePort);
                        }
                        Log.w(function_header,"session is "
                                +Tools.ipInt2Str(session.LocalIP)+ " "
                                +session.LocalPort + " "
                                +Tools.ipInt2Str(session.RemoteIP) + " "
                                +session.RemotePort);

                        session.LastNanoTime = System.nanoTime();
                        session.PacketSent++; // 注意顺序
                        Log.w(function_header, "for current session, infor is "+session.PacketSent);

                        if (session.PacketSent >= 2 && tcpDataSize == 0 && tcpHeader.getAckID()!=0 && session.isConnected==false) {
                            Log.w(function_header, "for current session, is second sent, and datasize is zero, will return now ack id "+tcpHeader.getAckID());
                            //return true; // 丢弃tcp握手的第二个ACK报文。因为客户端发数据的时候也会带上ACK，这样可以在服务器Accept之前分析出HOST信息。
                        }

                        // 首次有属于，分析数据，找到host
                        if (session.BytesSent == 0 && tcpDataSize > 0) {
                            Log.w(function_header, "try to analysis http host");

                            int dataOffset = tcpHeader.m_Offset + tcpHeader.getHeaderLength();
                            String host = HttpHostHeaderParser.parseHost(tcpHeader.m_Data, dataOffset, tcpDataSize);
                            if (host != null) {
                                Log.w(function_header, "http header is "+host);
                                if (host.contains("_")) { //http
                                    session.RemoteHost = host.split("_")[0];
                                    session.httpAction = host.split("_")[1];
                                    session.protocolType=0;//HTTP
                                    Log.w(function_header, "http header is host "+session.RemoteHost
                                            +":"+session.RemotePort
                                            +", action "+session.httpAction);
                                }
                                else {
                                    session.protocolType=1; //HTTPS
                                    session.RemoteHost = host;//https
                                    Log.w(function_header, "http header is not host "+session.RemoteHost
                                    +"："+session.RemotePort);
                                    //return true;
                                }


                            }
                            else {
                                // for non-http(s) message
                                assert(false);
                                Log.w(function_header, "non http(s) message, parse host is empty");
                            }


                        }

                        // 转发给本地TCP服务器
                        Log.v(function_header, "before change destination " + Tools.bytesToHex(ipHeader.m_Data, ipHeader.m_Offset, size));

                        ipHeader.setSourceIP(ipHeader.getDestinationIP());
                        ipHeader.setDestinationIP(LOCAL_IP);
                        tcpHeader.setDestinationPort(m_TcpProxyServer.Port);
                        CommonMethods.ComputeTCPChecksum(ipHeader, tcpHeader);
                        Log.v(function_header, "before and after change destination "
                                + tcpHeader.getDestinationPort()
                                + "->" + m_TcpProxyServer.Port);

                        Log.w(function_header, "finally forward received packet to tcpproxyserver "+Tools.ipInt2Str(LOCAL_IP)+", tcpheader "
                                +tcpHeader.toString());

                        Log.v(function_header, "after change destination " + Tools.bytesToHex(ipHeader.m_Data, ipHeader.m_Offset, size));

                        MyVpnConnection.Instance.m_VPNOutputStream.write(ipHeader.m_Data, ipHeader.m_Offset, size);
                        session.BytesSent += tcpDataSize; // 注意顺序
                        m_SentBytes += size;

                        return true;
                    }
                }
                else
                {
                    Log.v(function_header, "received package which source ip not local ip ");
                }
                // there's another condition that received packet, not sure why, ignore it
                //return true;
                break;
            case IPHeader.UDP:
                // 转发DNS数据包
                UDPHeader udpHeader = m_UDPHeader;
                udpHeader.m_Offset = ipHeader.getHeaderLength();
                String function_header1="PacketProcess UDP "+Tools.ipInt2Str(ipHeader.getSourceIP())+":"+udpHeader.getSourcePort()
                        +"->"+Tools.ipInt2Str(ipHeader.getDestinationIP())+":"+udpHeader.getDestinationPort();
                Log.v(function_header1, "receive udp message");

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
                    Log.w("UDP DNS not local ip","source ip is "+Tools.ipInt2Str(ipHeader.getSourceIP()));
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
