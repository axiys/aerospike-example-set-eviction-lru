package com.aerospike.example;

public class ByteToHex {

    public static String convert(byte[] data) {
        StringBuilder sb = new StringBuilder();
        for (byte b : data) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }
}
