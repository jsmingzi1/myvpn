package com.example.myvpn.vpn.packet.proxy.core;

import android.util.Log;

import com.example.myvpn.vpn.MyVpnService;
import com.example.myvpn.vpn.packet.proxy.tcpip.CommonMethods;

import java.util.Locale;


public class HttpHostHeaderParser {

    public static String parseHost(byte[] buffer, int offset, int count) {
        try {
            switch (buffer[offset]) {
                case 'G'://GET
                case 'H'://HEAD
                case 'P'://POST,PUT
                case 'D'://DELETE
                case 'O'://OPTIONS
                case 'T'://TRACE
                case 'C'://CONNECT
                    return getHttpHost(buffer, offset, count);
                case 0x16://SSL
                    return getSNI(buffer, offset, count);
            }
        } catch (Exception e) {
            e.printStackTrace();
            MyVpnService.Instance.writeLog("Error: parseHost: %s", e);
        }
        return null;
    }

    static String getHttpHost(byte[] buffer, int offset, int count) {
        Log.w("HttpHostHeaderParser", "gethttphost");
        String headerString = new String(buffer, offset, count);
        String[] headerLines = headerString.split("\\r\\n");
        String requestLine = headerLines[0];
        if (requestLine.startsWith("GET") || requestLine.startsWith("POST") || requestLine.startsWith("HEAD") || requestLine.startsWith("OPTIONS")) {
            for (int i = 1; i < headerLines.length; i++) {
                String[] nameValueStrings = headerLines[i].split(":");
                if (nameValueStrings.length == 2) {
                    String name = nameValueStrings[0].toLowerCase(Locale.ENGLISH).trim();
                    String value = nameValueStrings[1].trim();
                    if ("host".equals(name)) {
                        Log.w("http header", "from http request, get host info " + value + "_" + requestLine);
                        return value+"_"+requestLine;
                    }
                }
            }
        }
        return null;
    }

    static String getSNI(byte[] buffer, int offset, int count) {
        Log.w("HttpHostHeaderParser", "getsni");

        int limit = offset + count;
        if (count > 43 && buffer[offset] == 0x16) {//TLS Client Hello
            offset += 43;//skip 43 bytes header

            //read sessionID:
            if (offset + 1 > limit) return null;
            int sessionIDLength = buffer[offset++] & 0xFF;
            offset += sessionIDLength;

            //read cipher suites:
            if (offset + 2 > limit) return null;
            int cipherSuitesLength = CommonMethods.readShort(buffer, offset) & 0xFFFF;
            offset += 2;
            offset += cipherSuitesLength;

            //read Compression method:
            if (offset + 1 > limit) return null;
            int compressionMethodLength = buffer[offset++] & 0xFF;
            offset += compressionMethodLength;

            if (offset == limit) {
                System.err.println("TLS Client Hello packet doesn't contains SNI info.(offset == limit)");
                return null;
            }

            //read Extensions:
            if (offset + 2 > limit) return null;
            int extensionsLength = CommonMethods.readShort(buffer, offset) & 0xFFFF;
            offset += 2;

            if (offset + extensionsLength > limit) {
                System.err.println("TLS Client Hello packet is incomplete.");
                return null;
            }

            while (offset + 4 <= limit) {
                int type0 = buffer[offset++] & 0xFF;
                int type1 = buffer[offset++] & 0xFF;
                int length = CommonMethods.readShort(buffer, offset) & 0xFFFF;
                offset += 2;

                if (type0 == 0x00 && type1 == 0x00 && length > 5) { //have SNI
                    offset += 5;//skip SNI header.
                    length -= 5;//SNI size;
                    if (offset + length > limit) return null;
                    Log.w("http header", "from https request, get host info " + new String(buffer, offset, length));
                    return new String(buffer, offset, length);
                } else {
                    offset += length;
                }
            }

            System.err.println("TLS Client Hello packet doesn't contains Host field info.");
            return null;
        } else {
            System.err.println("Bad TLS Client Hello packet.");
            return null;
        }
    }
}
