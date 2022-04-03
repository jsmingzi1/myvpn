package com.example.myvpn.vpn.packet.proxy.core;

import android.annotation.SuppressLint;
import android.util.Log;

import com.example.myvpn.Tools;
import com.example.myvpn.vpn.packet.proxy.tcpip.CommonMethods;
import com.example.myvpn.vpn.packet.proxy.tunnel.Config;
import com.example.myvpn.vpn.packet.proxy.tunnel.Tunnel;
import com.example.myvpn.vpn.packet.proxy.tunnel.httpconnect.HttpConnectConfig;
import com.example.myvpn.vpn.packet.proxy.tunnel.shadowsocks.ShadowsocksConfig;
import com.google.common.base.Splitter;

import org.apache.http.conn.util.InetAddressUtils;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;


public class ProxyConfig {
    class ProxyRuleItem {
        Boolean BoolValue =true; // if change of this item expression, true, don't change, false, change it
        String Key;
        String Operation;
        ArrayList<String> values = new ArrayList<>();
    }
    class ProxyRule {
        ArrayList<ProxyRuleItem> oneRule = new ArrayList<>();
        String action="direct";
    }
    public static ArrayList<ProxyRule> m_AllRules = new ArrayList<>();

    public static final ProxyConfig Instance = new ProxyConfig();
    public static String AppInstallID;
    public static String AppVersion;
    public final static int FAKE_NETWORK_MASK = CommonMethods.ipStringToInt("255.255.0.0");
    public final static int FAKE_NETWORK_IP = CommonMethods.ipStringToInt("172.25.0.0");

    // config item
    public ArrayList<Config> m_ProxyList;

    // rules
    HashMap<String, String> m_DomainMap; // 完全匹配
    HashMap<String, String> m_DomainKeywordMap; // 关键词匹配
    HashMap<String, String> m_DomainSuffixMap; // 前缀匹配
    HashMap<String, String> m_IPCountryMap; // ip country
    HashMap<String, String> m_IPCidrMap; // ip cidr
    HashMap<String, String> m_ProcessMap; // process

    public static boolean IS_DEBUG = false;
    public boolean globalMode = true;


    int m_dns_ttl;
    String m_user_agent;
    boolean m_isolate_http_host_header = false;
    int m_mtu;
    String m_final_action = "direct";

    Timer m_Timer;

    // eg: domain:action or ip:action
    private static ConcurrentHashMap<String, String> ruleActionCache;

    public class IPAddress {
        public final String Address;
        public final int PrefixLength;

        public IPAddress(String address, int prefixLength) {
            this.Address = address;
            this.PrefixLength = prefixLength;
        }

        public IPAddress(String ipAddresString) {
            String[] arrStrings = Splitter.on('/').splitToList(ipAddresString).toArray(new String[0]);
            String address = arrStrings[0];
            int prefixLength = 32;
            if (arrStrings.length > 1) {
                prefixLength = Integer.parseInt(arrStrings[1]);
            }
            this.Address = address;
            this.PrefixLength = prefixLength;
        }

        @SuppressLint("DefaultLocale")
        @Override
        public String toString() {
            return String.format("%s/%d", Address, PrefixLength);
        }

        @Override
        public boolean equals(Object o) {
            if (o == null) {
                return false;
            } else {
                return this.toString().equals(o.toString());
            }
        }
    }

    public ProxyConfig() {
        m_ProxyList = new ArrayList<Config>();

        m_DomainMap = new HashMap<String, String>();
        m_DomainKeywordMap = new HashMap<String, String>();
        m_DomainSuffixMap = new HashMap<String, String>();

        m_IPCountryMap = new HashMap<String, String>();
        m_IPCidrMap = new HashMap<String, String>();

        m_ProcessMap = new HashMap<String, String>();

        ruleActionCache = new ConcurrentHashMap<>();

        m_Timer = new Timer();
        m_Timer.schedule(m_Task, 120000, 120000); // 每两分钟刷新一次。
    }

    TimerTask m_Task = new TimerTask() {
        @Override
        public void run() {
            refreshProxyServer(); // 定时更新dns缓存
        }

        // 定时更新dns缓存
        void refreshProxyServer() {
            try {
                for (int i = 0; i < m_ProxyList.size(); i++) {
                    try {
                        Config config = m_ProxyList.get(0);
                        InetAddress address = InetAddress.getByName(config.ServerAddress.getHostName());
                        if (address != null && !address.equals(config.ServerAddress.getAddress())) {
                            config.ServerAddress = new InetSocketAddress(address, config.ServerAddress.getPort());
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    };


    public static boolean isFakeIP(int ip) {
        return (ip & ProxyConfig.FAKE_NETWORK_MASK) == ProxyConfig.FAKE_NETWORK_IP;
    }

    public Config getDefaultProxy() {
        return getProxy("default");
    }

    public Config getProxy(String name) {
        Log.w("getProxy", "size is "+m_ProxyList.size()+", name is "+name);
        if (m_ProxyList.size() > 0) {
            for (int i=0; i<m_ProxyList.size(); i++){
                if (m_ProxyList.get(i).ConfigName.equals(name))
                    return m_ProxyList.get(i);
            }

            if (name.equals("default"))
                return m_ProxyList.get(0);
        }

        return null;
    }

    public Config getDefaultTunnelConfig(InetSocketAddress destAddress) {
        return getDefaultProxy();
    }

    public int getDnsTTL() {
        if (m_dns_ttl < 30) {
            m_dns_ttl = 30;
        }
        return m_dns_ttl;
    }



    public String getUserAgent() {
        if (m_user_agent == null || m_user_agent.isEmpty()) {
            m_user_agent = System.getProperty("http.agent");
        }
        return m_user_agent;
    }

    public int getMTU() {
        if (m_mtu > 1400 && m_mtu <= Tunnel.BUFFER_SIZE) {
            return m_mtu;
        } else {
            return 1500;
        }
    }

    private String getDomainState(String domain) {
        domain = domain.toLowerCase();
        if (m_DomainMap.get(domain) != null) {
            return m_DomainMap.get(domain);
        }
        for (String key : m_DomainSuffixMap.keySet()) {
            if (domain.endsWith(key)) {
                return m_DomainSuffixMap.get(key);
            }
        }

        for (String key : m_DomainKeywordMap.keySet()) {
            if (domain.contains(key)) {
                return m_DomainKeywordMap.get(key);
            }
        }

        return null;
    }
    private boolean judgeAction(String input, String Operation, ArrayList<String> values) {
        //if (true) return true;
        for (int i=0;i<values.size(); i++) {
            Log.w("ProxyConfig", "judgeAction "+input + ", "+Operation+", "+values.get(i));
            if (Operation.equalsIgnoreCase("equals") && input.equals(values.get(i)))
                return true;
            else if (Operation.equalsIgnoreCase("prefix") && input.startsWith(values.get(i)))
                return true;
            else if (Operation.equalsIgnoreCase("suffix") && input.endsWith(values.get(i)))
                return true;
            else if (Operation.equalsIgnoreCase("contains") && input.contains(values.get(i)))
                return true;
            else if (Operation.equalsIgnoreCase("exists") && input.length() > 0)
                return true;
        }
        return false;
    }
    public String getAction(NatSession session) {
        Log.w("ProxyConfig", "getAction now!!!");
        //if (true) return "direct";

        int ruleIdx, itemIdx;
        if (m_AllRules ==null) {
            Log.w("ProxyConfig", "getAction m_AllRules is empty!!!");
            return "direct";
        }

        for (ruleIdx=0; ruleIdx < m_AllRules.size(); ruleIdx++) {

            ProxyRule rule = m_AllRules.get(ruleIdx);
            for (itemIdx=0; itemIdx<rule.oneRule.size(); itemIdx++) {
                ProxyRuleItem item = rule.oneRule.get(itemIdx);
                //check item
                if (item.Key.equalsIgnoreCase("host")) {
                    if (false == judgeAction(session.RemoteHost, item.Operation, item.values))
                        break; //one item failed, quit this rule
                } else if (item.Key.equalsIgnoreCase("ip")) {
                    if (false == judgeAction(Tools.ipInt2Str(session.RemoteIP), item.Operation, item.values))
                        break; //one item failed, quit this rule
                } else if (item.Key.equalsIgnoreCase("protocol")) {
                    if (false == judgeAction(Tools.convertProtocolInt2Str(session.protocolType), item.Operation, item.values))
                        break; //one item failed, quit this rule
                } else if (item.Key.equalsIgnoreCase("httpaction")) {
                    if (false == judgeAction(session.httpAction, item.Operation, item.values))
                        break; //one item failed, quit this rule
                } else {
                    break;//not match, quit this rule;
                }
            }
            if (itemIdx == rule.oneRule.size()) //all match
            {
                Log.w("ProxyConfig", "getAction condition match, action is "+rule.action);

                return rule.action;
            }
        }

        return "direct";
    }

    public String needProxy(NatSession session/*String host, int ip, int protocol, int uid, String httpAction*/) {
        // 无视配置文件，都走代理
        //Log.w("needProxy", "globalMode is "+globalMode);
        //if (host.endsWith("baidu.com") || host.contains("chinaz.com"))
        //if (httpAction == null || httpAction.length() == 0) return "proxy"; //only https use proxy, other use direct
        if (true) return getAction(session);
        if (true) return "direct";
        //if (host.endsWith("netease.com") && (httpAction == null || httpAction.length() == 0))
        //    return "proxy";
        //if (host.equals("mam.netease.com") && httpAction.contains("GET /api/config/getClientIp"))
        //    return "proxy";
        if (true)
            return "proxy";
        if (globalMode)
            return "proxy";
        String host = session.RemoteHost;
        int ip = session.RemoteIP;
        String ipStr = "";
        if (ip != 0) {
            ipStr = CommonMethods.ipIntToString(ip);
        }

        if (host != null) {
            if (InetAddressUtils.isIPv4Address(host) || InetAddressUtils.isIPv6Address(host)) {
                if (DnsProxy.NoneProxyIPDomainMaps.get(ip) != null) {
                    host = DnsProxy.NoneProxyIPDomainMaps.get(ip);
                }
            }

            String action = ruleActionCache.get(host);
            if (action != null) {
                return action;
            }

            action = getDomainState(host);
            if (action != null) {
                ruleActionCache.put(host, action);
                return action;
            }
        }
/*
        if (ip != 0) {
            if (isFakeIP(ip)) {
                return "proxy";
            }

            String action = ruleActionCache.get(ipStr);
            if (action != null) {
                return action;
            }

            String domain = DnsProxy.NoneProxyIPDomainMaps.get(ip);
            // ip cidr
            for (String key : m_IPCidrMap.keySet()) {
                if (SubnetUtil.inSubnet(key, ipStr)) {
                    if (ProxyConfig.IS_DEBUG) {

                    }

                    action = m_IPCidrMap.get(key);
                    ruleActionCache.put(ipStr, action);
                    return action;
                }
            }


        }
*/
        return m_final_action;
    }


    public int loadProxyRuleFromLines(String[] lines) throws Exception {
        m_AllRules.clear();

        Log.w("loadfromlines", "content size is "+lines.length);
        //Thread.dumpStack();

        for (String line : lines) {
            line = line.trim();
            if (line.trim().isEmpty() || line.trim().startsWith("#") || line.trim().startsWith("//")) {
                continue;
            }
            if (line.startsWith("proxy ")) {
                addProxyToList(line.substring(5).trim());
                continue;
            }

            String action="proxy"; //proxy as default for each line rule
            String[] parts = line.split("\\s+");
            if (parts.length>1) {
                String lastPart = parts[parts.length - 1];
                if (lastPart.startsWith("proxy:")) {
                    action = lastPart;
                    line = line.substring(0, line.length()-lastPart.length());
                } else if (lastPart.equals("direct")) {
                    action = "direct";
                    line = line.substring(0, line.length()-lastPart.length());
                }
            }

            boolean bValid = true;
            ProxyRule rule = new ProxyRule();
            String[] items = line.split(";");
            for (int i=0; i<items.length; i++) {
                if (items[i].trim().length() == 0)
                    continue;
                ProxyRuleItem ruleItem = new ProxyRuleItem();
                if (items[i].startsWith("!")) {
                    ruleItem.BoolValue = false;
                    items[i] = items[i].substring(1).trim();
                }
                String[] itemValues = items[i].split("\\s+");
                if (itemValues.length < 2) {
                    Log.w("parse rule1", "skip invalid rule "+line);
                    bValid = false;
                    break;
                }
                if (itemValues[0].equals("host") || itemValues[0].equals("ip") || itemValues[0].equals("protocol") || itemValues[0].equals("uid") || itemValues[0].equals("httpaction")) {
                    ruleItem.Key = itemValues[0];
                }
                else {
                    Log.w("parse rule2", "skip invalid rule "+line);
                    bValid = false;
                    break;
                }

                if (itemValues[1].equals("exists"))
                    ruleItem.Operation = itemValues[1];
                else if (itemValues.length > 2 && (itemValues[1].equals("equals") || itemValues[1].equals("prefix") || itemValues[1].equals("suffix") || itemValues[1].equals("contains"))) {
                    ruleItem.Operation = itemValues[1];
                    for (int j=2; j<itemValues.length; j++)
                        ruleItem.values.add(itemValues[j]);
                } else {
                    Log.w("parse rule3", "skip invalid rule "+line);
                    bValid = false;
                    break;
                }

                rule.oneRule.add(ruleItem);
                rule.action=action;
            }
            if (bValid == false)
                continue;

            m_AllRules.add(rule);
        }

        return m_AllRules.size();
    }

    public void addProxyToList(String proxyString) throws Exception {
        Log.w("ProxyConfig", "addProxyToList "+proxyString);
        Config config = null;
        proxyString=proxyString.trim();
        String[] strArr = proxyString.split("\\s+");
        String configname="default";
        if (strArr.length>1){
            configname = strArr[0];
            proxyString = proxyString.substring(configname.length()).trim();
        }
        if (proxyString.startsWith("ss://")) {
            config = ShadowsocksConfig.parse(configname, proxyString);
        } else {
            if (!proxyString.toLowerCase().startsWith("http://")) {
                proxyString = "http://" + proxyString;
            }
            //Log.w("ProxyConfig", "proxy string is "+proxyString);

            config = HttpConnectConfig.parse(configname, proxyString);
        }
        if (!m_ProxyList.contains(config)) {
            //Log.w("ProxyConfig", "add proxy config "+config.ServerAddress);
            m_ProxyList.add(config);
        }
    }
}
