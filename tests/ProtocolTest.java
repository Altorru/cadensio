package fr.hugoviaud.zwack;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Random;

public final class ProtocolTest {
    static ByteBuffer read(byte[] bytes) { return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN); }
    static void check(boolean condition, String name) { if (!condition) throw new AssertionError(name); }
    public static void main(String[] args) {
        ByteBuffer bike = read(Protocol.indoorBike(275, 93, 1850));
        check(bike.getShort() == 0x44, "FTMS flags must match fields present");
        check(bike.getShort() == 1850, "Speed uses hundredths of km/h");
        check(bike.getShort() == 186, "Cadence uses half RPM");
        check(bike.getShort() == 275 && !bike.hasRemaining(), "Signed power and exact FTMS frame size");
        check(read(Protocol.features()).getInt() == 0x4002, "Measurement features");
        check(read(Protocol.features()).getInt(4) == 0x2008, "Power target and simulation features");
        ByteBuffer power = read(Protocol.cyclingPower(250, 65535, 65534));
        check(power.getShort() == 0x20 && power.getShort() == 250, "CPS crank flag and power");
        check((power.getShort() & 0xffff) == 65535 && (power.getShort() & 0xffff) == 65534, "Unsigned crank counters");
        check(Arrays.equals(Protocol.cadence(1, 1024), new byte[]{2,1,0,0,4}), "CSC frame");
        check(Protocol.expectedLength(0x05) == 3 && Protocol.expectedLength(0x11) == 7, "Control command lengths");
        check(Protocol.expectedLength(0x7f) == -1, "Unknown opcode");
        check(Protocol.target(new byte[]{5, (byte) 0xff, (byte) 0xff}) == -1, "Negative targets must remain signed");
        check(Arrays.equals(Protocol.result(5, 3), new byte[]{(byte) 128,5,3}), "Control-point response format");
        Random random = new Random(42);
        boolean variedPower = false, variedCadence = false;
        for (int i=0; i<10000; i++) {
            int watts = Protocol.sample(150, 7, 1000, random), rpm = Protocol.sample(90, 2, 200, random);
            check(watts >= 143 && watts <= 157, "Power variation independently bounded");
            check(rpm >= 88 && rpm <= 92, "Cadence variation independently bounded");
            variedPower |= watts != 150; variedCadence |= rpm != 90;
            check(Protocol.sample(0, 100, 1000, random) >= 0, "No negative power at lower bound");
            check(Protocol.sample(1000, 100, 1000, random) <= 1000, "Power capped at upper bound");
            check(Protocol.sample(200, 20, 200, random) <= 200, "Cadence capped at upper bound");
        }
        check(variedPower && variedCadence, "Both variation sources are active");
        check(Protocol.sample(150, 0, 1000, random) == 150, "Zero variation is exactly constant");
        Protocol.Crank crank = new Protocol.Crank();
        crank.update(0, 90); crank.update(1000, 90);
        check(crank.revolutions() == 1 && crank.lastEvent == 683, "90 RPM crank timing, first second");
        crank.update(2000, 90);
        check(crank.revolutions() == 3 && crank.lastEvent == 2048, "Fractional revolutions are retained");
        int prior = crank.lastEvent; crank.update(62000, 0);
        check(crank.revolutions() == 3 && crank.lastEvent == prior, "No phantom cadence when stopped");
        crank.update(64000, 60);
        check(crank.revolutions() == 5 && crank.lastEvent == 0, "Event timestamp wraps after 64s");
        crank.total = 65535; crank.update(66000, 60);
        check(crank.revolutions() == 1, "Crank revolution counter wraps at 16 bits");
        System.out.println("PASS: BLE frames, target parsing, independent bounded variations, zero jitter, crank integration and rollover.");
    }
}
