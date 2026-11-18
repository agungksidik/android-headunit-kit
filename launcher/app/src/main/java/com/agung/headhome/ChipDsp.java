package com.agung.headhome;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Properties;

/**
 * Talks to the factory DSP chip the way the factory DSP app does. A port of only what is needed from the
 * decompiled factory app (class names in brackets):
 * <ul>
 * <li>{@code KernelProtocal.generateNullProtocal / getProtocalDataStartOffset / calCheckSum /
 * calCheckSumAndWriteEndOfData} (packet frame and checksum)</li>
 * <li>{@code AudioProtocalUtil.send32EQData} (all 32 bands in one packet, type 13, data type 0xF1)</li>
 * <li>{@code AudioProtocalUtil.sendDengxiangData} (factory loudness flag, data type 1)</li>
 * <li>{@code AudioProtocalUtil.sendSiteData} (listening position, data type 0xF9)</li>
 * <li>{@code DspUtils.byteToStr} (uppercase hex string) and {@code AudioProtocalUtil.writeDataToCPU}
 * (reflection call to {@code android.os.Hardware.setDSP_ak7604_status(String)}, 0 means success)</li>
 * <li>{@code DspManager} gain scaling: the UI value is 12 + dB, the chip value is (int)(UI * gainMax / 24),
 * with gainMax read from UartConfig.ini key dsp3_eq_max (default 30)</li>
 * </ul>
 * Not ported: the T60 platform tweaks, the 10-band MCU path used by non-ARM DSPs, and the 24-step variant for
 * K2401 and T7Max platforms, none of which apply to the tested unit.
 */
public final class ChipDsp {

    /** The 32 band centers of the factory DSP (DspConstant.DEFAULTFREQUENCE). */
    public static final int[] FREQUENCIES = {20, 25, 32, 40, 50, 63, 80, 100, 125, 160, 200, 250, 315, 400, 500,
            630, 800, 1000, 1250, 1600, 2000, 2500, 3150, 4000, 5000, 6300, 8000, 10000, 12500, 16000, 18000, 20000};

    /** The factory Q value for every band (2.0 times 10). */
    public static final int Q_VALUE = 20;

    public static final int DEFAULT_GAIN_MAX = 30;
    public static final String UART_CONFIG_PATH = "/data/nwdappconfig/app/UartConfig.ini";
    public static final String GAIN_MAX_KEY = "dsp3_eq_max";

    private static final byte HEAD = (byte) 0xF0;
    private static final byte TYPE_AUDIO = 13;
    private static final byte DATA_ALL_32PART_PARAM = -15;
    private static final byte DATA_DENGXIANG = 1;
    private static final byte DATA_SITE = -7;

    private ChipDsp() {
    }

    // ---- KernelProtocal ----

    static byte[] generateNullProtocol(int length, byte type, byte dataType) {
        byte[] p;
        if (length > 250) {
            p = new byte[length + 5];
            p[1] = 0;
            p[2] = (byte) (length & 255);
            p[3] = (byte) ((length >> 8) & 255);
        } else {
            p = new byte[length + 3];
            p[1] = (byte) length;
        }
        p[0] = HEAD;
        p[2] = type;
        p[3] = dataType;
        return p;
    }

    static int dataStartOffset() {
        return 5;
    }

    static byte checksum(byte[] p, int from, int to) {
        byte sum = 0;
        for (int i = from + 1; i < to - 1; i++) {
            sum = (byte) (sum + p[i]);
        }
        return sum;
    }

    static void writeChecksum(byte[] p) {
        p[p.length - 1] = checksum(p, 0, p.length);
    }

    // ---- AudioProtocalUtil ----

    /** send32EQData: frequencies, Q values and chip-scale gains for every band. Inputs are not modified. */
    public static byte[] eqPacket(int[] frequency, int[] q, int[] chipGain) {
        byte[] p = generateNullProtocol(frequency.length * 5 + 4, TYPE_AUDIO, DATA_ALL_32PART_PARAM);
        int o = dataStartOffset();
        p[o] = (byte) frequency.length;
        for (int i = 0; i < frequency.length; i++) {
            int qv = q[i] == 0 ? 20 : q[i];
            int k = i * 5;
            p[o + 1 + k] = (byte) (frequency[i] & 255);
            p[o + 2 + k] = (byte) ((frequency[i] >> 8) & 255);
            p[o + 3 + k] = (byte) (qv & 255);
            p[o + 4 + k] = (byte) ((qv >> 8) & 255);
            p[o + 5 + k] = (byte) chipGain[i];
        }
        return p;
    }

    /** sendDengxiangData: the factory loudness flag. */
    public static byte[] loudnessPacket(boolean on) {
        byte[] p = generateNullProtocol(5, TYPE_AUDIO, DATA_DENGXIANG);
        int o = dataStartOffset();
        p[o] = 17;
        p[o + 1] = on ? (byte) 1 : (byte) 0;
        return p;
    }

    /** sendSiteData: listening position as fader and balance, each 0 to 14 (7 is the center). */
    public static byte[] sitePacket(int fader, int balance) {
        int b = Math.max(0, Math.min(14, balance));
        int f = Math.max(0, Math.min(14, fader));
        byte[] p = generateNullProtocol(5, TYPE_AUDIO, DATA_SITE);
        int o = dataStartOffset();
        p[o] = (byte) f;
        p[o + 1] = (byte) b;
        return p;
    }

    // ---- DspUtils.byteToStr ----

    /** Writes the checksum and returns the packet as an uppercase hex string. */
    public static String toHex(byte[] packet) {
        writeChecksum(packet);
        StringBuilder sb = new StringBuilder();
        for (byte b : packet) {
            String h = Integer.toHexString(b & 255);
            if (h.length() == 1) {
                h = '0' + h;
            }
            sb.append(h.toUpperCase());
        }
        return sb.toString();
    }

    // ---- DspManager gain scaling ----

    /**
     * Converts dB (-12 to +12, 1 dB steps) to chip values: UI value = 12 + dB, chip = (int)(UI * gainMax / 24).
     * With gainMax 30, 0 dB is 15 and +6 dB is 22.
     */
    public static int[] dbToChip(int[] db, int gainMax) {
        float diff = gainMax / 24.0f;
        int[] out = new int[db.length];
        for (int i = 0; i < db.length; i++) {
            int ui = Math.max(0, Math.min(24, 12 + db[i]));
            out[i] = (int) (ui * diff);
        }
        return out;
    }

    /** LoadDataUtils.loadKeyValueFromSD: an int property from a Java properties file, or the default. */
    public static int readIntProperty(File file, String key, int defaultValue) {
        if (!file.exists()) {
            return defaultValue;
        }
        try (FileInputStream in = new FileInputStream(file)) {
            Properties props = new Properties();
            props.load(in);
            String v = props.getProperty(key, "");
            return v.isEmpty() ? defaultValue : Integer.parseInt(v.trim());
        } catch (IOException | RuntimeException e) {
            return defaultValue;
        }
    }

    // ---- writeDataToCPU ----

    /** Outcome of a chip call. BLOCKED: Android refused the reflection call (hidden API), FAILED: the chip answered non-zero. */
    public enum Result { OK, BLOCKED, FAILED }

    /** Calls android.os.Hardware.setDSP_ak7604_status(hex) by reflection. */
    public static Result send(String hex) {
        try {
            Class<?> hardware = Class.forName("android.os.Hardware");
            Method m = hardware.getMethod("setDSP_ak7604_status", String.class);
            Object result = m.invoke(null, hex);
            if (result == null) {
                return Result.BLOCKED;
            }
            return result instanceof Integer && (Integer) result == 0 ? Result.OK : Result.FAILED;
        } catch (java.lang.reflect.InvocationTargetException e) {
            return Result.FAILED;
        } catch (Throwable t) {
            // NoSuchMethodException, IllegalAccessException, ClassNotFound or a linkage error: hidden API denial.
            return Result.BLOCKED;
        }
    }

    public static Result sendPacket(byte[] packet) {
        return send(toHex(packet));
    }
}
