package fr.hugoviaud.zwack;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Random;
import java.util.UUID;

/** Wire formats shared by FTMS, Cycling Power and Cycling Speed & Cadence. */
final class Protocol {
    static final int MAX_POWER = 1000;
    static final int MAX_CADENCE = 200;
    static UUID uuid(int shortId) {
        return UUID.fromString(String.format(java.util.Locale.ROOT, "0000%04x-0000-1000-8000-00805f9b34fb", shortId));
    }
    static ByteBuffer buffer(int size) { return ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN); }
    static byte[] u16(int value) { return buffer(2).putShort((short) value).array(); }
    static byte[] u32(int value) { return buffer(4).putInt(value).array(); }
    static int clamp(int value, int max) { return Math.max(0, Math.min(max, value)); }
    static int sample(int base, int variation, int max, Random random) {
        return clamp(base + (variation == 0 ? 0 : random.nextInt(variation * 2 + 1) - variation), max);
    }
    static byte[] indoorBike(int power, int cadence, int speedHundredths) {
        return buffer(8).putShort((short) 0x44).putShort((short) speedHundredths)
            .putShort((short) (cadence * 2)).putShort((short) power).array();
    }
    static byte[] cyclingPower(int power, int revolutions, int eventTime) {
        return buffer(8).putShort((short) 0x20).putShort((short) power)
            .putShort((short) revolutions).putShort((short) eventTime).array();
    }
    static byte[] cadence(int revolutions, int eventTime) {
        return buffer(5).put((byte) 2).putShort((short) revolutions).putShort((short) eventTime).array();
    }
    static byte[] features() { return buffer(8).putInt(0x4002).putInt(0x2008).array(); }
    static byte[] powerRange() { return buffer(6).putShort((short) 0).putShort((short) MAX_POWER).putShort((short) 1).array(); }
    static byte[] result(int opcode, int result) { return new byte[] {(byte) 0x80, (byte) opcode, (byte) result}; }
    static int expectedLength(int opcode) {
        return switch (opcode) { case 0, 1, 7 -> 1; case 5 -> 3; case 8 -> 2; case 0x11 -> 7; default -> -1; };
    }
    static int target(byte[] value) { return ByteBuffer.wrap(value).order(ByteOrder.LITTLE_ENDIAN).getShort(1); }

    /** Last whole crank event, in 1/1024s, retained unchanged when cadence is zero. */
    static final class Crank {
        double total;
        int lastEvent;
        long previous = -1;
        void update(long nowMs, int cadence) {
            if (previous < 0) { previous = nowMs; return; }
            long elapsed = Math.max(0, nowMs - previous);
            previous = nowMs;
            double old = total;
            total += cadence * elapsed / 60000.0;
            if (cadence > 0 && (long) total > (long) old) {
                double eventMs = nowMs - (total - Math.floor(total)) * 60000.0 / cadence;
                lastEvent = (int) Math.round(eventMs * 1024.0 / 1000.0) & 0xffff;
            }
        }
        int revolutions() { return (int) ((long) total & 0xffff); }
    }
}
