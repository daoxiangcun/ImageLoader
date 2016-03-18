package com.example.imageloader.loader;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import android.text.TextUtils;

//////////�������������ӵ�����:http://www.xcoder.cn/index.php/archives/1401
/////////��Ҫȷ���Ƿ���ȷ
public class LoaderUtils {
    public static String encodeMd5(String value) {
        byte[] digest = encodeMd5Bytes(value);
        StringBuilder sb = new StringBuilder();
        if (digest != null) {
            for(byte b : digest) {
                sb.append(Integer.toHexString(b & 0xff));
            }
        }
        return sb.toString();
    }

    private static byte[] encodeMd5Bytes(String value) {
        if(TextUtils.isEmpty(value)) {
            return null;
        }
        MessageDigest digester = null;;
        try {
            digester = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return null;
        }
        digester.update(value.getBytes());
        return digester.digest();
    }
}
