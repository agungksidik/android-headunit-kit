package com.agung.headhome;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Minimal ADB client for the head unit's own adbd on 127.0.0.1:5555. It only runs "shell:reboot".
 * The wire format helpers are static and pure so they can be unit tested.
 */
public final class LocalAdb {

    public static final int A_CNXN = 0x4e584e43;
    public static final int A_AUTH = 0x48545541;
    public static final int A_OPEN = 0x4e45504f;
    public static final int A_OKAY = 0x59414b4f;
    public static final int A_CLSE = 0x45534c43;
    public static final int A_WRTE = 0x45545257;

    public static final int VERSION = 0x01000000;
    public static final int MAX_DATA = 4096;

    public static final int AUTH_TOKEN = 1;
    public static final int AUTH_SIGNATURE = 2;
    public static final int AUTH_RSAPUBLICKEY = 3;

    public static final int HEADER_SIZE = 24;
    public static final int PUBLIC_KEY_STRUCT_SIZE = 524;

    private static final String HOST = "127.0.0.1";
    private static final int PORT = 5555;
    private static final int KEY_BITS = 2048;
    private static final int MAX_PAYLOAD = 1 << 20;
    private static final String KEY_LABEL = " headunit-home@unit\0";
    private static final byte[] SHA1_DIGEST_INFO_PREFIX = {
            0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02, 0x1a, 0x05, 0x00, 0x04, 0x14
    };

    private LocalAdb() {
    }

    // ---- pure helpers ----

    /** Sum of all bytes, as ADB defines the data checksum. */
    public static int checksum(byte[] data) {
        int sum = 0;
        for (byte b : data) {
            sum += b & 0xff;
        }
        return sum;
    }

    /** The 24-byte message header: command, arg0, arg1, data length, data checksum, magic. */
    public static byte[] buildHeader(int command, int arg0, int arg1, byte[] data) {
        ByteBuffer b = ByteBuffer.allocate(HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(command);
        b.putInt(arg0);
        b.putInt(arg1);
        b.putInt(data.length);
        b.putInt(checksum(data));
        b.putInt(command ^ 0xffffffff);
        return b.array();
    }

    /** Header followed by the payload. */
    public static byte[] buildMessage(int command, int arg0, int arg1, byte[] data) {
        byte[] header = buildHeader(command, arg0, arg1, data);
        byte[] out = new byte[header.length + data.length];
        System.arraycopy(header, 0, out, 0, header.length);
        System.arraycopy(data, 0, out, header.length, data.length);
        return out;
    }

    /** The 524-byte Android ADB public key struct (before base64). */
    public static byte[] encodePublicKey(RSAPublicKey key) {
        BigInteger n = key.getModulus();
        if (n.bitLength() != KEY_BITS) {
            throw new IllegalArgumentException("Only 2048-bit keys are supported");
        }
        BigInteger two32 = BigInteger.ONE.shiftLeft(32);
        long n0inv = n.mod(two32).modInverse(two32).negate().mod(two32).longValue();
        BigInteger rr = BigInteger.ONE.shiftLeft(KEY_BITS * 2).mod(n);

        ByteBuffer b = ByteBuffer.allocate(PUBLIC_KEY_STRUCT_SIZE).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(KEY_BITS / 32);
        b.putInt((int) n0inv);
        b.put(toLittleEndian(n, KEY_BITS / 8));
        b.put(toLittleEndian(rr, KEY_BITS / 8));
        b.putInt(key.getPublicExponent().intValue());
        return b.array();
    }

    /** Base64 of the key struct plus the key label and a NUL, as sent in AUTH RSAPUBLICKEY. */
    public static byte[] publicKeyPayload(RSAPublicKey key) {
        byte[] b64 = Base64.getEncoder().encode(encodePublicKey(key));
        byte[] label = KEY_LABEL.getBytes(StandardCharsets.UTF_8);
        byte[] out = new byte[b64.length + label.length];
        System.arraycopy(b64, 0, out, 0, b64.length);
        System.arraycopy(label, 0, out, b64.length, label.length);
        return out;
    }

    /** RSA signature of the 20-byte token, treated as an already computed SHA-1 digest. */
    public static byte[] signToken(PrivateKey key, byte[] token) throws Exception {
        byte[] data = new byte[SHA1_DIGEST_INFO_PREFIX.length + token.length];
        System.arraycopy(SHA1_DIGEST_INFO_PREFIX, 0, data, 0, SHA1_DIGEST_INFO_PREFIX.length);
        System.arraycopy(token, 0, data, SHA1_DIGEST_INFO_PREFIX.length, token.length);
        Signature sig = Signature.getInstance("NONEwithRSA");
        sig.initSign(key);
        sig.update(data);
        return sig.sign();
    }

    static byte[] toLittleEndian(BigInteger value, int length) {
        byte[] be = value.toByteArray();
        byte[] out = new byte[length];
        for (int i = 0; i < length; i++) {
            int src = be.length - 1 - i;
            out[i] = src >= 0 ? be[src] : 0;
        }
        return out;
    }

    // ---- key storage ----

    public static KeyPair loadOrCreateKey(File dir) throws Exception {
        File privFile = new File(dir, "adbkey.pk8");
        File pubFile = new File(dir, "adbkey.x509");
        KeyFactory kf = KeyFactory.getInstance("RSA");
        if (privFile.isFile() && pubFile.isFile()) {
            try {
                PrivateKey priv = kf.generatePrivate(new PKCS8EncodedKeySpec(readAll(privFile)));
                RSAPublicKey pub = (RSAPublicKey) kf.generatePublic(new X509EncodedKeySpec(readAll(pubFile)));
                return new KeyPair(pub, priv);
            } catch (Exception ignored) {
                // Corrupt files: fall through and generate a new pair.
            }
        }
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(KEY_BITS);
        KeyPair pair = gen.generateKeyPair();
        writeAll(privFile, pair.getPrivate().getEncoded());
        writeAll(pubFile, pair.getPublic().getEncoded());
        return pair;
    }

    private static byte[] readAll(File f) throws IOException {
        return Files.readAllBytes(f.toPath());
    }

    private static void writeAll(File f, byte[] data) throws IOException {
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(data);
        }
    }

    // ---- session ----

    private static final class Message {
        int command;
        int arg0;
        int arg1;
        byte[] data;
    }

    /** The OPEN payload for a shell service: "shell:<cmd>" and a NUL. */
    public static byte[] shellOpenPayload(String shellCmd) {
        return ("shell:" + shellCmd + "\0").getBytes(StandardCharsets.UTF_8);
    }

    /** Runs "reboot". The unit goes down mid-command, so a dropped connection counts as success. */
    public static void reboot(File keyDir) throws Exception {
        run(keyDir, "reboot", true);
    }

    /** Runs a shell command on the local adbd and returns its output. Blocking: call off the UI thread. */
    public static String exec(File keyDir, String shellCmd) throws Exception {
        return run(keyDir, shellCmd, false);
    }

    private static String run(File keyDir, String shellCmd, boolean tolerateDrop) throws Exception {
        KeyPair pair = loadOrCreateKey(keyDir);
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(HOST, PORT), 3000);
            socket.setSoTimeout(10000);
            OutputStream out = socket.getOutputStream();
            DataInputStream in = new DataInputStream(socket.getInputStream());

            out.write(buildMessage(A_CNXN, VERSION, MAX_DATA, "host::\0".getBytes(StandardCharsets.UTF_8)));
            out.flush();

            boolean signatureSent = false;
            boolean keySent = false;
            while (true) {
                Message m = read(in);
                if (m.command == A_CNXN) {
                    break;
                }
                if (m.command == A_AUTH && m.arg0 == AUTH_TOKEN) {
                    if (!signatureSent) {
                        byte[] sig = signToken(pair.getPrivate(), m.data);
                        out.write(buildMessage(A_AUTH, AUTH_SIGNATURE, 0, sig));
                        signatureSent = true;
                    } else if (!keySent) {
                        out.write(buildMessage(A_AUTH, AUTH_RSAPUBLICKEY, 0,
                                publicKeyPayload((RSAPublicKey) pair.getPublic())));
                        keySent = true;
                        // The unit may ask to accept the key on screen.
                        socket.setSoTimeout(30000);
                    } else {
                        throw new IOException("ADB authorization was rejected");
                    }
                    out.flush();
                } else {
                    throw new IOException("Unexpected ADB message 0x" + Integer.toHexString(m.command));
                }
            }

            final int localId = 1;
            out.write(buildMessage(A_OPEN, localId, 0, shellOpenPayload(shellCmd)));
            out.flush();
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try {
                while (true) {
                    Message m = read(in);
                    if (m.command == A_CLSE) {
                        break;
                    }
                    if (m.command == A_WRTE) {
                        output.write(m.data);
                        out.write(buildMessage(A_OKAY, localId, m.arg0, new byte[0]));
                        out.flush();
                    }
                }
            } catch (EOFException | SocketException | SocketTimeoutException e) {
                if (!tolerateDrop) {
                    throw e;
                }
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8).trim();
        }
    }

    private static Message read(InputStream raw) throws IOException {
        DataInputStream in = raw instanceof DataInputStream ? (DataInputStream) raw : new DataInputStream(raw);
        byte[] h = new byte[HEADER_SIZE];
        in.readFully(h);
        ByteBuffer b = ByteBuffer.wrap(h).order(ByteOrder.LITTLE_ENDIAN);
        Message m = new Message();
        m.command = b.getInt();
        m.arg0 = b.getInt();
        m.arg1 = b.getInt();
        int len = b.getInt();
        b.getInt(); // data checksum, not verified
        int magic = b.getInt();
        if (magic != (m.command ^ 0xffffffff) || len < 0 || len > MAX_PAYLOAD) {
            throw new IOException("Bad ADB message header");
        }
        m.data = new byte[len];
        in.readFully(m.data);
        return m;
    }
}
