package com.example.myvpn;
import java.io.*;
import java.util.zip.*;


public class gzipcls {
    private static final char[] HEX_CHAR = {'0', '1', '2', '3', '4', '5',
            '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};
    public static String bytesToHexFun1(byte[] bytes) {
        // 一个byte为8位，可用两个十六进制位标识
        char[] buf = new char[bytes.length * 2];
        int a = 0;
        int index = 0;
        for(byte b : bytes) { // 使用除与取余进行转换
            if(b < 0) {
                a = 256 + b;
            } else {
                a = b;
            }

            buf[index++] = HEX_CHAR[a / 16];
            buf[index++] = HEX_CHAR[a % 16];
        }

        return new String(buf);
    }

    private static byte[] HextoBytes(String str) {
        if(str == null || str.trim().equals("")) {
            return new byte[0];
        }

        byte[] bytes = new byte[str.length() / 2];
        for(int i = 0; i < str.length() / 2; i++) {
            String subStr = str.substring(i * 2, i * 2 + 2);
            bytes[i] = (byte) Integer.parseInt(subStr, 16);
        }

        return bytes;
    }

    public static byte[] compressToByte(String str)
    {
        String str1 = null;
        ByteArrayOutputStream bos = null;
        try
        {
            bos = new ByteArrayOutputStream();
            BufferedOutputStream dest = null;

            byte b[] = str.getBytes();
            GZIPOutputStream gz = new GZIPOutputStream(bos,b.length);
            gz.write(b,0,b.length);
            bos.close();
            gz.close();

        }
        catch(Exception e) {
            System.out.println(e);
            e.printStackTrace();
        }
        byte b1[] = bos.toByteArray();
        return b1;
    }

    public static String compress(String str)
    {
        String str1 = null;
        ByteArrayOutputStream bos = null;
        try
        {
            bos = new ByteArrayOutputStream();
            BufferedOutputStream dest = null;

            byte b[] = str.getBytes();
            GZIPOutputStream gz = new GZIPOutputStream(bos,b.length);
            gz.write(b,0,b.length);
            bos.close();
            gz.close();

        }
        catch(Exception e) {
            System.out.println(e);
            e.printStackTrace();
        }
        byte b1[] = bos.toByteArray();
        return bytesToHexFun1(b1);
    }

    public static String deCompressHex(String str)
    {
        String s1 = null;

        try
        {
            byte b[] = HextoBytes(str);//str.getBytes();
            InputStream bais = new ByteArrayInputStream(b);
            GZIPInputStream gs = new GZIPInputStream(bais);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            int numBytesRead = 0;
            byte [] tempBytes = new byte[6000];
            try
            {
                while ((numBytesRead = gs.read(tempBytes, 0, tempBytes.length)) != -1)
                {
                    baos.write(tempBytes, 0, numBytesRead);
                }

                s1 = new String(baos.toByteArray());
                s1= baos.toString();
            }
            catch(ZipException e)
            {
                e.printStackTrace();
            }
        }
        catch(Exception e) {
            e.printStackTrace();
        }
        return s1;
    }

}