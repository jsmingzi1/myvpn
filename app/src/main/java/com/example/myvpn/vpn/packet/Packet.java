package com.example.myvpn;

import android.util.Log;

import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


class IP {
    public byte     version;
    public byte     ihl;
    public byte     tos;
    public int      tot_len;    // uint16_t
    public short    id;
    public short    frag_off;
    public short    ttl;        // uint8_t
    public short    protocol;  // uint8_t
    public short    check;
    public int      saddr;
    public int      daddr;

    // save the data
    byte [] data;
    private TCP tcp = null;

    public IP(byte[] data, int offset, int length) {
        this.data = new byte[length];
        System.arraycopy(data, offset, this.data, 0, length);
        decode();
    }

    public boolean decode() {
        if (data.length < 20)
            return false;

        int offset = 0;
        version = (byte)((data[offset] & 0xF0) >> 4);
        ihl = (byte)((data[offset] & 0xF) << 2);
        tos = (byte)(data[offset + 1] & 0xFF);
        tot_len = (int)((data[offset + 2] & 0xFF) << 8 | (data[offset + 3] & 0xFF));
        id = (short)((data[offset + 4] & 0xFF) << 8 | (data[offset + 5] & 0xFf));
        frag_off = (short)((data[offset + 6] & 0xFF) << 8 | (data[offset + 7] & 0xFF));
        ttl = (short)(data[offset + 8] & 0xFF);
        protocol = (short)(data[offset + 9] & 0xFF);
        check = (short)((data[offset + 10] & 0xFF) << 8 | (data[offset + 11] & 0xFF));
        saddr = (int)((data[offset + 12] & 0xFF) << 24 | (data[offset + 13] & 0xFF) << 16 | (data[offset + 14] & 0xFF) << 8 | (data[offset + 15] & 0xFF));
        daddr = (int)((data[offset + 16] & 0xFF) << 24 | (data[offset + 17] & 0xFF) << 16 | (data[offset + 18] & 0xFF) << 8 | (data[offset + 19] & 0xFF));

        return true;
    }
    public boolean encode() {
        if (data.length < 20)
            return false;

        int offset = 0;
        data[offset] = (byte)((version & 0xF) << 4 | ((ihl >> 2) & 0xF));
        data[offset + 1] = (byte)(tos & 0xFF);
        data[offset + 2] = (byte)((tot_len & 0xFF00) >> 8);
        data[offset + 3] = (byte)(tot_len & 0xFF);
        data[offset + 4] = (byte)((id & 0xFF00) >> 8);
        data[offset + 5] = (byte)(id & 0xFF);
        data[offset + 6] = (byte)((frag_off & 0xFF00) >> 8);
        data[offset + 7] = (byte)(frag_off & 0xFF);
        data[offset + 8] = (byte)(ttl & 0xFF);
        data[offset + 9] = (byte)(protocol & 0xFF);
        data[offset + 10] = (byte)((check & 0xFF00) >> 8);
        data[offset + 11] = (byte)(check & 0xFF);
        data[offset + 12] = (byte)((saddr & 0xFF000000) >> 24);
        data[offset + 13] = (byte)((saddr & 0xFF0000) >> 16);
        data[offset + 14] = (byte)((saddr & 0xFF00) >> 8);
        data[offset + 15] = (byte)(saddr & 0xFF);
        data[offset + 16] = (byte)((daddr & 0xFF000000) >> 24);
        data[offset + 17] = (byte)((daddr & 0xFF0000) >> 16);
        data[offset + 18] = (byte)((daddr & 0xFF00) >> 8);
        data[offset + 19] = (byte)(daddr & 0xFF);
        return true;
    }

    // offset is ip header offset
    public void recalcIPCheckSum() {
        long answer;

        if (data.length < 20)
            return;

        data[10] = 0;
        data[11] = 0;
        answer = Tools.partCksum(0, data, 0, data.length);
        answer = ~answer & 0xFFFF;

        data[10] = (byte)((answer & 0xFF00) >> 8);
        data[11] = (byte)((answer & 0xFF));
    }

    public TCP getTCP() {
        if (protocol != 0x06)
            return null;
        if (tcp==null)
            tcp = new TCP(data, ihl, data.length-ihl);
        return tcp;
    }

    public byte[] getPayload() {
        byte[] payload = new byte[data.length-ihl];
        System.arraycopy(data, ihl, payload, 0, data.length-ihl);
        return payload;
    }

}

class TCP {
    public int      source;     // uint16_t
    public int      dest;       // uint16_t
    public long     seq;        // uint32_t
    public long     ack_seq;    // uint32_t
    public byte     doff;
    public byte     res1;
    public byte     cwr;
    public byte     ecn;
    public byte     urg;
    public byte     ack;
    public byte     psh;
    public byte     rst;
    public byte     syn;
    public byte     fin;
    public int      window;     // uint16_t
    public short    check;
    public short    urg_ptr;

    //
    byte[] data;
    private HTTP http = null;
    public TCP(byte[] data, int offset, int length) {
        this.data = new byte[length];
        System.arraycopy(data, offset, this.data, 0, length);
        decode();
    }

    public boolean decode() {
        if (data.length < 20)
            return false;

        int offset = 0;
        source = (int)((data[offset] & 0xFF) << 8 | (data[offset + 1] & 0xFF));
        dest = (int)((data[offset + 2] & 0xFF) << 8 | (data[offset + 3] & 0xFF));
        seq = (int)((data[offset + 4] & 0xFF) << 24 | (data[offset + 5] & 0xFF) << 16 | (data[offset + 6] & 0xFF) << 8 | (data[offset + 7] & 0xFF));
        ack_seq = (int)((data[offset + 8] & 0xFF) << 24 | (data[offset + 9] & 0xFF) << 16 | (data[offset + 10] & 0xFF) << 8 | (data[offset + 11] & 0xFF));
        doff = (byte)(((data[offset + 12] & 0xF0) >> 4) << 2);
        res1 = (byte)(data[offset + 12] & 0xF);
        cwr = (byte)((data[offset + 13] & 0x80) >> 7);
        ecn = (byte)((data[offset + 13] & 0x40) >> 6);
        urg = (byte)((data[offset + 13] & 0x20) >> 5);
        ack = (byte)((data[offset + 13] & 0x10) >> 4);
        psh = (byte)((data[offset + 13] & 0x8) >> 3);
        rst = (byte)((data[offset + 13] & 0x4) >> 2);
        syn = (byte)((data[offset + 13] & 0x2) >> 1);
        fin = (byte)(data[offset + 13] & 0x1);
        window = (int)((data[offset + 14] & 0xFF) << 8 | data[offset + 15] & 0xFF);
        check = (short)((data[offset + 16] & 0xFF) << 8 | data[offset + 17] & 0xFF);
        urg_ptr = (short)((data[offset + 18] & 0xFF) << 8 | data[offset + 19] & 0xFF);

        return true;
    }

    public boolean encode() {
        if (data.length < 20)
            return false;
        int offset = 0;
        data[offset] = (byte)((source & 0xFF00) >> 8);
        data[offset + 1] = (byte)(source & 0xFF);
        data[offset + 2] = (byte)((dest & 0xFF00) >> 8);
        data[offset + 3] = (byte)(dest & 0xFF);
        data[offset + 4] = (byte)((seq & 0xFF000000) >> 24);
        data[offset + 5] = (byte)((seq & 0xFF0000) >> 16);
        data[offset + 6] = (byte)((seq & 0xFF00) >> 8);
        data[offset + 7] = (byte)(seq & 0xFF);
        data[offset + 8] = (byte)((ack_seq & 0xFF000000) >> 24);
        data[offset + 9] = (byte)((ack_seq & 0xFF0000) >> 16);
        data[offset + 10] = (byte)((ack_seq & 0xFF00) >> 8);
        data[offset + 11] = (byte)(ack_seq & 0xFF);
        data[offset + 12] = (byte)((((doff >> 2) & 0xF) << 4) | (res1 & 0xF));
        data[offset + 13] = (byte)(cwr << 7 | ecn << 6 | urg << 5 | ack << 4 | psh << 3 | rst << 2 | syn << 1 | fin);
        data[offset + 14] = (byte)((window & 0xFF00) >> 8);
        data[offset + 15] = (byte)(window & 0xFF);
        data[offset + 16] = (byte)((check & 0xFF00) >> 8);
        data[offset + 17] = (byte)(check & 0xFF);
        data[offset + 18] = (byte)((urg_ptr & 0xFF00) >> 8);
        data[offset + 19] = (byte)(urg_ptr & 0xFF);
        return true;
    }

    public byte[] getPayload() {
        byte[] payload = new byte[data.length-doff];
        System.arraycopy(data, doff, payload, 0, data.length-doff);
        return payload;
    }

    public HTTP getHttp() {
        if (http == null) {
            byte[] payload = getPayload();
            if (isHTTPRequest())
                http = new HTTPRequest(payload, 0, payload.length);
            else if (isHTTPResponse())
                http = new HTTPResponse(payload, 0, payload.length);
        }

        return http;
    }

    // offset is ip header offset
    public void recalcTCPCheckSum() {
        long calccksum;
        int offset = 0, length = data.length;
        int ipTotLen = (int)((data[offset + 2] & 0xFF) << 8 | (data[offset + 3] & 0xFF));
        int tcpOffset = offset + ((data[offset] & 0xF) << 2);
        int tcpLen = ipTotLen - ((data[offset] & 0xF) << 2);

        if (length < ipTotLen)
            return;

        byte[] phdr = new byte[4];
        phdr[0] = 0;
        phdr[1] = 6;
        phdr[2] = (byte)((tcpLen >> 8) & 0xFF);
        phdr[3] = (byte)((tcpLen & 0xFF));

        data[tcpOffset + 16] = 0;
        data[tcpOffset + 17] = 0;

        calccksum = Tools.partCksum(0L, data, offset + 12,4);
        calccksum = Tools.partCksum(calccksum, data, offset + 16,4);
        calccksum = Tools.partCksum(calccksum, phdr, 0, 4);
        calccksum = Tools.partCksum(calccksum, data, tcpOffset, tcpLen);
        calccksum = ~calccksum & 0xFFFF;

        data[tcpOffset + 16] = (byte)((calccksum & 0xFF00) >> 8);
        data[tcpOffset + 17] = (byte)((calccksum & 0xFF));
    }

    public boolean isHTTPRequest() {
        int len = data.length - doff;
        String str = new String(data, doff,  len>10?10:len);
        if (str.startsWith("GET")
                || str.startsWith("HEAD")
                || str.startsWith("POST")
                || str.startsWith("PUT")
                || str.startsWith("DELETE")
                || str.startsWith("TRACE")
                || str.startsWith("OPTIONS")
                || str.startsWith("CONNECT")
                || str.startsWith("PATCH"))
            return true;
        else
            return false;
    }

    public boolean isHTTPResponse() {
        int len = data.length - doff;
        String str = new String(data, doff,  len>10?10:len);
        return str.startsWith("HTTP");
    }

}


abstract class HTTP{
    byte []data;
    String headerline;
    Map<String, String> header = new HashMap<String, String>();
    int body_offset = -1;
    List<byte[]> trunkedbody = new ArrayList<byte[]>();

    boolean isBodyCompleted = false;

    public HTTP(byte []data, int offset, int length) {
        this.data = new byte[length];
        System.arraycopy(data, offset, this.data, 0, length);
        decode();
    }

    public boolean decode() {
        List<byte[]> ret = Tools.split(data, new byte[]{(byte)0x0d, (byte)0x0a, (byte)0x0d, (byte)0x0a});
        //decode header line
        List<byte[]> headers = Tools.split(ret.get(0), new byte[]{(byte)0x0d, (byte)0x0a});
        headerline = new String(headers.get(0));
        for (int i=1; i<headers.size(); i++) {
            List<String> line = Tools.firstSplit(new String(headers.get(i)), ":");
            header.put(line.get(0), line.get(1));
        }

        //decode body if exist
        //System.out.println("deocode string is " + gzipcls.bytesToHexFun1(data));
        //System.out.println("decode is " + ret.size());
        if (ret.size() > 1) {
            body_offset = data.length - ret.get(1).length;
            //	System.out.println("body len is "+ret.get(1).length);
        }
        isBodyCompleted = Tools.isHTTPEnd(data);

        return true;
    }

    //Check HTTP Request and Response
    public boolean isFullHTTP() {
        return isBodyCompleted;
    };

    //result true: last packet, false: not last
    public boolean appendTrunkedBody(byte[] body, int offset, int length) {
        byte[] bd = new byte[length];
        System.arraycopy(body, offset, bd, 0, length);
        if (trunkedbody == null)
            trunkedbody = new ArrayList<byte[]>();
        trunkedbody.add(bd);

        isBodyCompleted = Tools.isHTTPEnd(bd);
        return isBodyCompleted;
    }

    public byte[] getHeaderByte() {
        byte[] headerByte = null;
        int len = data.length;
        if (body_offset > 0)
            len = body_offset;

        headerByte = new byte[len];
        System.arraycopy(data, 0, headerByte, 0, len);
        return headerByte;
    }

    public byte[] getBodyByte() {
        byte[] bodyByte = null;
        if (body_offset <= 0)
            return null;

        bodyByte = new byte[data.length-body_offset];
        int offset = body_offset;
        System.arraycopy(data, offset, bodyByte, 0, data.length - offset);
        return bodyByte;
    }

    public String decodeBody() {

        if (isBodyCompleted == false) return "";
        byte[] result_body = null;
        if (header.get("Transfer-Encoding").equals("chunked")) {

            if (body_offset >= 0) {
                byte[] bd = new byte[data.length - body_offset];
                System.arraycopy(data, body_offset, bd, 0, data.length - body_offset);
                //System.out.println("bd size is " + bd.length);
                //System.out.println("body is " + gzipcls.bytesToHexFun1(bd));
                List<byte[]> list = Tools.split(bd, new byte[]{(byte)0x0d, (byte)0x0a});
        		/*System.out.println("list size is "+list.size());
        		for (int j=0;j<list.size();j++)
        			System.out.println("list " + j + " is " + gzipcls.bytesToHexFun1(list.get(j)));*/
                for (int i=1; i<list.size(); i+=2)
                    if (result_body == null)
                        result_body = list.get(i);
                    else
                        result_body = Tools.concat(result_body, list.get(i));

                //System.out.println("result is "+gzipcls.bytesToHexFun1(result_body));
            }

            if (trunkedbody.size() > 0) {
                byte[] result_body1 = null;
                //System.out.println("body trunked is " + gzipcls.bytesToHexFun1(trunkedbody.get(0)));
                List<byte[]> list = Tools.split(trunkedbody.get(0), new byte[]{(byte)0x0d, (byte)0x0a});

                for (int i=1; i<list.size(); i+=2) {
                    if (result_body == null)
                        result_body = list.get(i);
                    else
                        result_body = Tools.concat(result_body, list.get(i));
                }

            }

        }
        else {
            //not trunked body
            result_body = new byte[data.length - body_offset];
            System.arraycopy(data, body_offset, result_body, 0, data.length - body_offset);
        }

        Log.w("result_body", "result_body is " + trunkedbody.size()  + "," + gzipcls.bytesToHexFun1(result_body));
        if (header.get("Content-Encoding").equals("gzip")) {
            Log.w("packet process", "before decompress, the data is "+gzipcls.bytesToHexFun1(result_body));
            return gzipcls.deCompressHex(gzipcls.bytesToHexFun1(result_body));
        }
        return "";
    }

    // encode modified trunked body, then return it;
    // as it has two packet, the previous packet need change it too.
    public byte[] encodeTrunkedBody(String str) {
        byte[] retTrunked = null;

        if (body_offset <= 0
            || header.get("Transfer-Encoding").equals("chunked") == false
            || header.get("Content-Encoding").equals("gzip") == false) return null;

        byte[] newbody = gzipcls.compressToByte(str);
        Log.w("encodeTrunkedBody", "body_offset " + body_offset);

        Log.w("encodeTrunkedBody", "old body is " + getBodyByte().length);
        byte[] oldbody = getBodyByte();
        int len1 = getOriFromTrunkedData(oldbody).length;
        int len2 = newbody.length-len1;

        byte[] newbodyWrap = wrapTrunkedData(newbody, 0, len1);
        if (newbodyWrap.length != oldbody.length) {
            Log.w("packet process", "encodeTrunkedBody body1 wrap differnet size "+oldbody.length+","+newbodyWrap.length);
        }
        System.arraycopy(newbodyWrap, 0, data, body_offset, newbodyWrap.length);


        retTrunked = Tools.concat(wrapTrunkedData(newbody, len1, len2), new byte[]{(byte)0x30, (byte)0x0d, (byte)0x0a, (byte)0x0d, (byte)0x0a});
        Log.w("encodeTrunkedBody", "new body-1:"+str+","+gzipcls.bytesToHexFun1(newbody));
        Log.w("encodeTrunkedBody", "new body0:"+gzipcls.bytesToHexFun1(getBodyByte()));
        Log.w("encodeTrunkedBody", "new body1:"+gzipcls.bytesToHexFun1(retTrunked));

        // re-decode the http data
        trunkedbody.clear();
        trunkedbody.add(retTrunked);
        //decode();
        Log.w("packet process", "re-decode body is " + decodeBody());
        return retTrunked;

    }

    // wrap it to "len\r\n<src>\r\n"
    public static byte[] wrapTrunkedData(byte[] src, int offset, int len) {
        if (src == null || len<=0 || src.length <= len) return null;

        byte[] lenByte = String.format("%x", len).getBytes();
        byte[] ret = new byte[lenByte.length + 2 + len + 2];
        System.arraycopy(lenByte, 0, ret, 0, lenByte.length);
        ret[lenByte.length] = (byte)0x0d;
        ret[lenByte.length+1] = (byte)0x0a;
        System.arraycopy(src, offset, ret, lenByte.length+2, len);
        ret[ret.length-2] = (byte)0x0d;
        ret[ret.length-1] = (byte)0x0a;

        return ret;
    }

    // get data from trunked data, only one section
    public static byte[] getOriFromTrunkedData(byte[] src) {
        if (src == null || src.length <= 0) return null;
        List<byte[]> arrList = Tools.split(src, new byte[]{(byte)0x0d, (byte)0x0a});
        String byteLen = new String(arrList.get(0));
        int realLen = Integer.decode("0x"+byteLen);
        if (arrList.get(1).length != realLen) {
            Log.w("http getOriFromTrunkedData", "verify length failed");
        }
        return arrList.get(1);
    }
}

class HTTPRequest extends HTTP {
    public HTTPRequest(byte[] data, int offset, int length) {
        super(data, offset, length);
    }

}
class HTTPResponse extends HTTP{
    public HTTPResponse(byte[] data, int offset, int length) {
        super(data, offset, length);
    }

}

class HTTPResponseSessionStore implements Comparable{
    int saddr;
    int daddr;
    int sport;
    int dport;
    int protocol;
    public HTTPResponseSessionStore(int saddr, int daddr, int sport, int dport, int protocol) {
        this.saddr = saddr;
        this.daddr = daddr;
        this.sport = sport;
        this.dport = dport;
        this.protocol = protocol;
    }

    @Override
    public int compareTo(Object o) {
        if (o instanceof HTTPResponseSessionStore) {
            HTTPResponseSessionStore sess = (HTTPResponseSessionStore)o;
            if (saddr != sess.saddr) return saddr - sess.saddr;
            if (daddr != sess.daddr) return daddr - sess.daddr;
            if (sport != sess.sport) return sport - sess.sport;
            if (dport != sess.dport) return dport - sess.dport;
            if (protocol != sess.protocol) return protocol - sess.protocol;
            return 0;
        }
        throw new RuntimeException("Not type of HTTPResponseSessionStore");
    }

    @Override
    public boolean equals(Object o) {
        return compareTo(o)==0;
    }
}

public class Packet {
    // use this to store http reponse session, as some response will use multiple frames
    //static List<HTTPResponseSessionStore> TempHTTPResponseSessionStore = new ArrayList<HTTPResponseSessionStore>();
    static Map<HTTPResponseSessionStore, IP> TempHTTPResponseSessionStore = new HashMap<HTTPResponseSessionStore, IP>();
    public static boolean existSession(HTTPResponseSessionStore session) {
        for (HTTPResponseSessionStore each : TempHTTPResponseSessionStore.keySet()) {
            if (each.equals(session))
                return true;
        }

        return false;
    }

    public static boolean addSession(HTTPResponseSessionStore session, IP ip) {
        if (existSession(session)) return false;
        TempHTTPResponseSessionStore.put(session, ip);
        return true;
    }

    public static boolean removeSession(HTTPResponseSessionStore session) {
        if (existSession(session) == false)
            return false;

        for (HTTPResponseSessionStore each : TempHTTPResponseSessionStore.keySet()) {
            if (each.equals(session)) {
                TempHTTPResponseSessionStore.remove(each);
                return true;
            }
        }
        return false;
    }

    public static IP getSession(HTTPResponseSessionStore session) {
        for (HTTPResponseSessionStore each : TempHTTPResponseSessionStore.keySet()) {
            if (each.equals(session))
                return TempHTTPResponseSessionStore.get(each);
        }
        return null;
    }

    // return true: no need send packet after function called, false: need send packet after it
    // bOut true: this is packet before send out, false: this is packet after received
    public static boolean processPacket(boolean bOut, byte[] packet, int offset, int length, FileOutputStream out1) throws Exception{

        IP ip = new IP(packet, offset, length);

		/*System.out.println("IP information as below:");
		System.out.format("version: %d\n", ip.version);
		System.out.format("ihl: %d\n", ip.ihl);
		System.out.format("tos: %X\n", ip.tos);
		System.out.format("tot_len: %d\n", ip.tot_len);
		System.out.format("id: %X\n", ip.id);
		System.out.format("frag_off: %X\n", ip.frag_off);
		System.out.format("ttl: %d\n", ip.ttl);
		System.out.format("protocol: %d\n", ip.protocol);
		System.out.format("check: %X\n", ip.check);
		System.out.format("saddr: %X %s\n", ip.saddr, Tools.ipInt2Str(ip.saddr));
		System.out.format("daddr: %X %s\n", ip.daddr, Tools.ipInt2Str(ip.daddr));
		System.out.println();*/

        //directly pass this packet
        if (ip.protocol != 6) {
            //out.write(packet, offset, length);
            return false;
        }
        //byte[] ipPayload = ip.getPayload();
        TCP tcp = ip.getTCP();//new TCP(ipPayload, 0, ipPayload.length);
		/*System.out.println("TCP information as below:");
		System.out.format("source: %d\n", tcp.source);
		System.out.format("dest: %d\n", tcp.dest);
		System.out.format("seq: %d\n", tcp.seq);
		System.out.format("ack_seq: %d\n", tcp.ack_seq);
		System.out.format("doff: %d\n", tcp.doff);
		System.out.format("res1: %X\n", tcp.res1);
		System.out.format("cwr: %d\n", tcp.cwr);
		System.out.format("ecn: %d\n", tcp.ecn);
		System.out.format("urg: %d\n", tcp.urg);
		System.out.format("ack: %d\n", tcp.ack);
		System.out.format("psh: %d\n", tcp.psh);
		System.out.format("rst: %d\n", tcp.rst);
		System.out.format("syn: %d\n", tcp.syn);
		System.out.format("fin: %d\n", tcp.fin);
		System.out.format("window: %d\n", tcp.window);
		System.out.format("tcp check: %X\n", tcp.check);
		System.out.format("urg_ptr: %X\n", tcp.urg_ptr);
		System.out.println();*/
        Log.i("packet process", "this is tcp message");
        HTTPResponseSessionStore session = new HTTPResponseSessionStore(ip.saddr, ip.daddr, tcp.source, tcp.dest, ip.protocol);
        byte[] tcpPayload = tcp.getPayload();
        if (tcp.isHTTPRequest()) {
            //System.out.println("this is a http request packet: "+tcpPayload.length);
            HTTP http = tcp.getHttp();
            Log.i("packet process", "this http request packge is complete "+http.isFullHTTP() + "," + http.headerline);

            //out.write(packet, offset, length);
        }
        else if (tcp.isHTTPResponse()) {
            Log.w("packet process", "this is a http response packet: " + gzipcls.bytesToHexFun1(tcpPayload));
            HTTP http = tcp.getHttp();
            Log.w("packet process", "this http response packge is complete "+http.isFullHTTP()+","+http.headerline);
            if (http.isFullHTTP() == false) {
                Packet.addSession(session, ip);
                //return;
            }
        }
        else if (Packet.existSession(session)) {
            Log.w("packet process", "this is a trunked http response packet: " + gzipcls.bytesToHexFun1(tcpPayload));
            IP previous_ip = Packet.getSession(session);
            HTTP previous_http = previous_ip.getTCP().getHttp();
            previous_http.appendTrunkedBody(tcpPayload, 0, tcpPayload.length);
            Log.w("packet process", "the trunked http resonse package is completed " + previous_http.isFullHTTP() );

            if (previous_http.isFullHTTP()) {
                String str = previous_http.decodeBody();
                Log.w("httpbody", "decodebody is " + str);
                Packet.removeSession(session);

                if (str.indexOf("192.227.147.146") >= 0) {
                    //modify previous and current packet, then send previous packet
                    //out.write(previous_ip.data, 0, previous_ip.data.length);
                    //modify packet
                    String newstr = str.replace("192.227.147.146", "175.42.128.37");
                    Log.w("packet process", "start modify packet old body is "+gzipcls.bytesToHexFun1(previous_http.getBodyByte()));
                    byte[] newTrunked = previous_http.encodeTrunkedBody(newstr);
                    String str1 = previous_http.decodeBody();
                    Log.w("pakcet process", "after modify, check content again" +
                            str1);

                    //
                    //return;
                }
            }
        }
        return false;
        //out.write(packet, offset, length);
    }
}


