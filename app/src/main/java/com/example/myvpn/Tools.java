package com.example.myvpn;

import android.util.Log;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

public class Tools {

    public static long partCksum(long initcksum, byte[] data, int offset, int length) {
        long cksum;
        int idx;
        int odd;

        cksum = initcksum;

        odd = length & 1;
        length -= odd;

        for (idx = 0; idx < length; idx += 2) {
            cksum += (data[offset + idx] & 0xFF) << 8 | (data[offset + idx +1] & 0xFF);
        }

        if (odd != 0) {
            cksum += (data[offset + idx] & 0xFF) << 8;
        }

        while ((cksum >> 16) != 0) {
            cksum = (cksum &0xFFFF) + (cksum >> 16);
        }

        return cksum;
    }

    public static boolean isHTTPEnd(byte[] data) {
        if (data.length<4) return false;
        int len = data.length;
        if (data[len-4] == (byte)0x0d && data[len-3]==(byte)0x0a
                && data[len-2]==(byte)0x0d && data[len-1]==(byte)0x0a)
            return true;

        Log.w("isHTTPEnd", "this is not http end "+gzipcls.bytesToHexFun1(data));
        return false;
    }

    public static List<String> firstSplit(String src, String sep) {
        String [] ret = src.split(sep);
        List<String> retList = new ArrayList<String>();
        retList.add(ret[0].trim());
        String tmp=ret[1];
        for (int i=2; i<ret.length; i++) {
            tmp = tmp + ":" + ret[i];
        }
        retList.add(tmp.trim());
        return retList;
    }

    public static List<byte[]> split(byte[] data, byte[] sep) {
        //System.out.println("before split data is " + gzipcls.bytesToHexFun1(data));
        //System.out.println("before split sep is "+gzipcls.bytesToHexFun1(sep));
        if (data == null || sep == null || data.length < sep.length || data.length == 0 || sep.length == 0)
            return null;
        List<byte[]> result = new ArrayList<byte[]>();

        int i, j, startpos=0;
        for (i=0; i<=data.length-sep.length;) {
            for (j=0; j<sep.length; j++) {

                if (data[i+j] != sep[j])
                    break;
            }
            if (j == sep.length) {

                if (i==startpos) {
                    i+=sep.length;
                    startpos = i;
                }
                else {
                    byte[] arr = new byte[i-startpos];
                    System.arraycopy(data, startpos, arr, 0, i-startpos);
                    result.add(arr);
                    startpos = i + sep.length;
                    i = startpos;
                    //if (sep.length == 4)
                    //	System.out.println("split got one string " + gzipcls.bytesToHexFun1(arr));

                }
            }
            else
                i++;

        }

        // process last one
        if (startpos < data.length) {
            //System.out.println("last list element " + i + ", " + startpos + ", " + data.length);
            byte[] arr = new byte[data.length-startpos];
            System.arraycopy(data, startpos, arr, 0, data.length-startpos);
            result.add(arr);
            //if (sep.length == 4)
            //	System.out.println("split got one string " + gzipcls.bytesToHexFun1(arr));
        }

        return result;
    }

    public static int indexOf(byte[] data, byte[] pattern) {
        int[] failure = computeFailure(pattern);

        int j = 0;

        for (int i = 0; i < data.length; i++) {
            while (j > 0 && pattern[j] != data[i]) {
                j = failure[j - 1];
            }
            if (pattern[j] == data[i]) {
                j++;
            }
            if (j == pattern.length) {
                return i - pattern.length + 1;
            }
        }
        return -1;
    }

    private static int[] computeFailure(byte[] pattern) {
        int[] failure = new int[pattern.length];

        int j = 0;
        for (int i = 1; i < pattern.length; i++) {
            while (j>0 && pattern[j] != pattern[i]) {
                j = failure[j - 1];
            }
            if (pattern[j] == pattern[i]) {
                j++;
            }
            failure[i] = j;
        }

        return failure;
    }

    public static String ipInt2Str(int ip) {
        int first = ip>>24;
        if(first<0){
            first = 0xff+first+1;
        }
        int second = ip>>16 & 0xff;
        int third = ip>>8 & 0xff;
        int four = ip & 0xff;

        StringBuffer buf = new StringBuffer();

        buf.append(first).append(".").append(second).append(".")
                .append(third).append(".").append(four);
        return buf.toString();
    }

    public static int ipInetAddress2Int(InetAddress addr) {
        int result = 0;
        for (byte b: addr.getAddress())
        {
            result = result << 8 | (b & 0xFF);
        }
        return result;
    }
    public static byte[] concat(byte[] array1, byte[] array2)
    {
        // Determine the length of the result array
        byte[] ret = new byte[array1.length + array2.length];

        System.arraycopy(array1, 0, ret, 0, array1.length);
        System.arraycopy(array2, 0, ret, array1.length, array2.length);


        return ret;
    }
}
