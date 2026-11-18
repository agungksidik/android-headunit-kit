package com.agung.headhome;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Compares the packets of {@link ChipDsp} with a straight copy of the decompiled factory functions
 * (KernelProtocal, AudioProtocalUtil, DspUtils.byteToStr) for the same inputs.
 */
public class ChipDspTest {

    // ---- reference: copied from the decompiled factory app, names kept ----

    static byte[] generateNullProtocal(int i, byte b, byte b2) {
        byte[] bArr;
        if (i > 250) {
            bArr = new byte[i + 5];
            bArr[1] = 0;
            bArr[2] = (byte) (i & 255);
            bArr[3] = (byte) ((i >> 8) & 255);
        } else {
            bArr = new byte[i + 3];
            bArr[1] = (byte) i;
        }
        bArr[0] = -16;
        bArr[2] = b;
        bArr[3] = b2;
        return bArr;
    }

    static byte getProtocalDataStartOffset(byte[] bArr) {
        return (byte) 5;
    }

    static byte calCheckSum(byte[] bArr, int i, int i2) {
        byte b = 0;
        for (int i3 = i + 1; i3 < i2 - 1; i3++) {
            b = (byte) (b + bArr[i3]);
        }
        return b;
    }

    static void calCheckSumAndWriteEndOfData(byte[] bArr) {
        bArr[bArr.length - 1] = calCheckSum(bArr, 0, bArr.length);
    }

    static byte[] send32EQData(int[] iArr, int[] iArr2, int[] iArr3) {
        byte[] bArrGenerateNullProtocal = generateNullProtocal((iArr.length * 5) + 4, (byte) 13, (byte) -15);
        byte protocalDataStartOffset = getProtocalDataStartOffset(bArrGenerateNullProtocal);
        bArrGenerateNullProtocal[protocalDataStartOffset] = (byte) iArr.length;
        for (int i = 0; i < iArr.length; i++) {
            if (iArr2[i] == 0) {
                iArr2[i] = 20;
            }
            int i2 = i * 5;
            bArrGenerateNullProtocal[protocalDataStartOffset + 1 + i2] = (byte) (iArr[i] & 255);
            bArrGenerateNullProtocal[protocalDataStartOffset + 2 + i2] = (byte) ((iArr[i] >> 8) & 255);
            bArrGenerateNullProtocal[protocalDataStartOffset + 3 + i2] = (byte) (iArr2[i] & 255);
            bArrGenerateNullProtocal[protocalDataStartOffset + 4 + i2] = (byte) ((iArr2[i] >> 8) & 255);
            bArrGenerateNullProtocal[protocalDataStartOffset + 5 + i2] = (byte) iArr3[i];
        }
        return bArrGenerateNullProtocal;
    }

    static byte[] sendDengxiangData(boolean z) {
        byte[] bArrGenerateNullProtocal = generateNullProtocal(5, (byte) 13, (byte) 1);
        byte protocalDataStartOffset = getProtocalDataStartOffset(bArrGenerateNullProtocal);
        bArrGenerateNullProtocal[protocalDataStartOffset] = 17;
        bArrGenerateNullProtocal[protocalDataStartOffset + 1] = z ? (byte) 1 : (byte) 0;
        return bArrGenerateNullProtocal;
    }

    static byte[] sendSiteData(int i, int i2) {
        if (i2 <= 0) {
            i2 = 0;
        }
        if (i2 > 14) {
            i2 = 14;
        }
        if (i <= 0) {
            i = 0;
        }
        int i3 = i <= 14 ? i : 14;
        byte[] bArrGenerateNullProtocal = generateNullProtocal(5, (byte) 13, (byte) -7);
        byte protocalDataStartOffset = getProtocalDataStartOffset(bArrGenerateNullProtocal);
        bArrGenerateNullProtocal[protocalDataStartOffset] = (byte) i3;
        bArrGenerateNullProtocal[protocalDataStartOffset + 1] = (byte) i2;
        return bArrGenerateNullProtocal;
    }

    static String byteToStr(byte[] bArr) {
        StringBuilder sb = new StringBuilder();
        calCheckSumAndWriteEndOfData(bArr);
        for (byte b : bArr) {
            String hexString = Integer.toHexString(b & 255);
            if (hexString.length() == 1) {
                hexString = '0' + hexString;
            }
            sb.append(hexString.toUpperCase());
        }
        return sb.toString();
    }

    // ---- tests ----

    private static int[] q20() {
        int[] q = new int[32];
        java.util.Arrays.fill(q, 20);
        return q;
    }

    private static final int[] TUNED_DB = {0, 0, 1, 3, 5, 6, 6, 5, 4, 3, 2, 2, 3, 3, 4, 4, 4, 4, 4, 4, 3, 3, 2, 3,
            4, 4, 5, 5, 5, 4, 3, 2};
    /** key_7735_gain of the factory "customize" preset after the tuning, scale 15 = 0 dB. */
    private static final int[] TUNED_CHIP = {15, 15, 16, 18, 21, 22, 22, 21, 20, 18, 17, 17, 18, 18, 20, 20, 20,
            20, 20, 20, 18, 18, 17, 18, 20, 20, 21, 21, 21, 20, 18, 17};

    @Test
    public void dbToChipMatchesTheFactoryPresetFile() {
        assertArrayEquals(TUNED_CHIP, ChipDsp.dbToChip(TUNED_DB, 30));
    }

    @Test
    public void dbToChipEnds() {
        int[] db = new int[32];
        assertEquals(15, ChipDsp.dbToChip(db, 30)[0]);
        java.util.Arrays.fill(db, 12);
        assertEquals(30, ChipDsp.dbToChip(db, 30)[0]);
        java.util.Arrays.fill(db, -12);
        assertEquals(0, ChipDsp.dbToChip(db, 30)[0]);
        java.util.Arrays.fill(db, 0);
        assertEquals(12, ChipDsp.dbToChip(db, 24)[0]);
    }

    @Test
    public void eqPacketEqualsFactoryPacket() {
        int[][] cases = {TUNED_CHIP, new int[32], ChipDsp.dbToChip(TUNED_DB, 30)};
        for (int[] gains : cases) {
            byte[] expected = send32EQData(ChipDsp.FREQUENCIES.clone(), q20(), gains.clone());
            byte[] actual = ChipDsp.eqPacket(ChipDsp.FREQUENCIES, q20(), gains);
            assertArrayEquals(expected, actual);
            assertEquals(byteToStr(expected), ChipDsp.toHex(actual));
        }
    }

    @Test
    public void eqPacketShape() {
        byte[] p = ChipDsp.eqPacket(ChipDsp.FREQUENCIES, q20(), TUNED_CHIP);
        assertEquals(167, p.length);
        assertEquals((byte) 0xF0, p[0]);
        assertEquals((byte) 164, p[1]);
        assertEquals(13, p[2]);
        assertEquals((byte) 0xF1, p[3]);
        assertEquals(32, p[5]);
        // first band: 20 Hz, Q 20, gain 15
        assertEquals(20, p[6]);
        assertEquals(0, p[7]);
        assertEquals(20, p[8]);
        assertEquals(15, p[10]);
        // last band: 20000 Hz = 0x4E20
        assertEquals(0x20, p[6 + 5 * 31]);
        assertEquals(0x4E, p[7 + 5 * 31]);
    }

    @Test
    public void zeroQBecomesTwenty() {
        int[] q = new int[32];
        assertArrayEquals(send32EQData(ChipDsp.FREQUENCIES.clone(), new int[32], TUNED_CHIP.clone()),
                ChipDsp.eqPacket(ChipDsp.FREQUENCIES, q, TUNED_CHIP));
    }

    @Test
    public void loudnessAndSitePacketsEqualFactory() {
        assertArrayEquals(sendDengxiangData(false), ChipDsp.loudnessPacket(false));
        assertArrayEquals(sendDengxiangData(true), ChipDsp.loudnessPacket(true));
        int[][] sites = {{3, 11}, {3, 7}, {7, 7}, {12, 7}, {-4, 99}, {20, -1}};
        for (int[] s : sites) {
            assertArrayEquals(sendSiteData(s[0], s[1]), ChipDsp.sitePacket(s[0], s[1]));
        }
        assertEquals(byteToStr(sendDengxiangData(false)), ChipDsp.toHex(ChipDsp.loudnessPacket(false)));
        assertEquals(byteToStr(sendSiteData(3, 11)), ChipDsp.toHex(ChipDsp.sitePacket(3, 11)));
    }

    @Test
    public void checksumIsTheSumOfBytesBetweenHeadAndEnd() {
        byte[] p = ChipDsp.loudnessPacket(true);
        ChipDsp.toHex(p);
        byte sum = 0;
        for (int i = 1; i < p.length - 1; i++) {
            sum = (byte) (sum + p[i]);
        }
        assertEquals(sum, p[p.length - 1]);
    }

    @Test
    public void readIntPropertyFallsBackToDefault() {
        assertEquals(30, ChipDsp.readIntProperty(new java.io.File("/nonexistent/UartConfig.ini"), "dsp3_eq_max", 30));
    }
}
