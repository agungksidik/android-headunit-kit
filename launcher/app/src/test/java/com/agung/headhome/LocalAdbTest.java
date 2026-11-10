package com.agung.headhome;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;

public class LocalAdbTest {

    @Test
    public void cnxnHeader() {
        byte[] data = "host::\0".getBytes(StandardCharsets.UTF_8);
        byte[] h = LocalAdb.buildHeader(LocalAdb.A_CNXN, LocalAdb.VERSION, LocalAdb.MAX_DATA, data);
        assertEquals(24, h.length);
        ByteBuffer b = ByteBuffer.wrap(h).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(0x4e584e43, b.getInt());
        assertEquals(0x01000000, b.getInt());
        assertEquals(4096, b.getInt());
        assertEquals(7, b.getInt());
        assertEquals(562, b.getInt());
        assertEquals(0xb1a7b1bc, b.getInt());
        // "CNXN" on the wire
        assertEquals('C', h[0]);
        assertEquals('N', h[1]);
        assertEquals('X', h[2]);
        assertEquals('N', h[3]);
    }

    @Test
    public void shellOpenPayloadIsNulTerminated() {
        byte[] p = LocalAdb.shellOpenPayload("cmd package set-home-activity 'a/b$C'");
        assertEquals("shell:cmd package set-home-activity 'a/b$C'\0", new String(p, StandardCharsets.UTF_8));
    }

    @Test
    public void checksumIsUnsignedByteSum() {
        assertEquals(0, LocalAdb.checksum(new byte[0]));
        assertEquals(255 + 1, LocalAdb.checksum(new byte[]{(byte) 0xff, 1}));
    }

    @Test
    public void messageIsHeaderPlusData() {
        byte[] data = {1, 2, 3};
        byte[] m = LocalAdb.buildMessage(LocalAdb.A_OKAY, 1, 2, data);
        assertEquals(27, m.length);
        byte[] tail = new byte[3];
        System.arraycopy(m, 24, tail, 0, 3);
        assertArrayEquals(data, tail);
    }

    @Test
    public void publicKeyStructIs524Bytes() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair pair = gen.generateKeyPair();
        RSAPublicKey pub = (RSAPublicKey) pair.getPublic();

        byte[] s = LocalAdb.encodePublicKey(pub);
        assertEquals(524, s.length);

        ByteBuffer b = ByteBuffer.wrap(s).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(64, b.getInt());
        long n0inv = b.getInt() & 0xffffffffL;
        byte[] modulus = new byte[256];
        b.get(modulus);
        byte[] rr = new byte[256];
        b.get(rr);
        assertEquals(65537, b.getInt());

        BigInteger n = pub.getModulus();
        assertEquals(n, fromLittleEndian(modulus));
        assertEquals(BigInteger.ONE.shiftLeft(4096).mod(n), fromLittleEndian(rr));
        // n0inv * n[0] == -1 (mod 2^32)
        long n0 = modulus[0] & 0xffL | (modulus[1] & 0xffL) << 8 | (modulus[2] & 0xffL) << 16 | (modulus[3] & 0xffL) << 24;
        assertEquals(0xffffffffL, (n0inv * n0) & 0xffffffffL);

        byte[] payload = LocalAdb.publicKeyPayload(pub);
        assertEquals(0, payload[payload.length - 1]);
    }

    private static BigInteger fromLittleEndian(byte[] le) {
        byte[] be = new byte[le.length];
        for (int i = 0; i < le.length; i++) {
            be[i] = le[le.length - 1 - i];
        }
        return new BigInteger(1, be);
    }
}
