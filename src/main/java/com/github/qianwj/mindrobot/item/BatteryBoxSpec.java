package com.github.qianwj.mindrobot.item;

/**
 * Size and voltage ladder of the robot battery box (R39).
 * <p>
 * The box copies the two axes a GT battery buffer has: how many batteries fit (1x .. 4x) and how high a
 * voltage the box is rated for. The rated voltage decides which batteries may be inserted: a battery is
 * accepted only when its {@code ic2.api.item.IElectricItem#getTier()} is at most the tier of the box, so an LV
 * box can never be filled with an EV cell.
 * <p>
 * Both axes live in the item metadata, one combined value, so a box is fully described by its stack and needs
 * no extra NBT just to exist. Only the batteries inside live in NBT.
 */
public final class BatteryBoxSpec {

    /** Number of voltages, used to pack the two axes into one metadata value. */
    public static final int VOLTAGE_COUNT = 5;
    /** Number of sizes, used for the bounds check of a metadata value. */
    public static final int SIZE_COUNT = 4;
    /** Number of valid metadata values of a battery box. */
    public static final int METADATA_COUNT = SIZE_COUNT * VOLTAGE_COUNT;

    /** First tier ever handed out by {@code IElectricItem#getTier()}: tier 1 is LV. */
    private static final int LOWEST_TIER = 1;

    private BatteryBoxSpec() {}

    /** How many batteries fit into the box: 1x, 2x, 3x or 4x. */
    public enum Size {

        ONE(1),
        TWO(2),
        THREE(3),
        FOUR(4);

        private final int slotCount;

        Size(int slotCount) {
            this.slotCount = slotCount;
        }

        /** Number of internal battery slots. */
        public int getSlotCount() {
            return slotCount;
        }

        /** Resolves a metadata packed size index without ever returning {@code null}. */
        public static Size byIndex(int index) {
            Size[] values = values();
            return values[Math.max(0, Math.min(values.length - 1, index))];
        }
    }

    /**
     * Rated voltage of the box. The tier values follow the IC2 ladder (1 = LV, 2 = MV, 3 = HV, 4 = EV,
     * 5 = IV), which is what {@code IElectricItem#getTier()} reports for a battery.
     */
    public enum Voltage {

        LV(1, "LV"),
        MV(2, "MV"),
        HV(3, "HV"),
        EV(4, "EV"),
        IV(5, "IV");

        private final int tier;
        private final String label;

        Voltage(int tier, String label) {
            this.tier = tier;
            this.label = label;
        }

        /** IC2 tier of this voltage, 1 for LV. */
        public int getTier() {
            return tier;
        }

        /** Short label used in tooltips and in the item name. */
        public String getLabel() {
            return label;
        }

        /** Resolves a metadata packed voltage index without ever returning {@code null}. */
        public static Voltage byIndex(int index) {
            Voltage[] values = values();
            return values[Math.max(0, Math.min(values.length - 1, index))];
        }
    }

    /** Packs a size and a voltage into the metadata value of a battery box stack. */
    public static int encode(Size size, Voltage voltage) {
        return size.ordinal() * VOLTAGE_COUNT + voltage.ordinal();
    }

    /** Size encoded in a metadata value; out of range values fall back to the smallest box. */
    public static Size sizeOf(int metadata) {
        return Size.byIndex(metadata / VOLTAGE_COUNT);
    }

    /** Voltage encoded in a metadata value; out of range values fall back to LV. */
    public static Voltage voltageOf(int metadata) {
        return Voltage.byIndex(metadata % VOLTAGE_COUNT);
    }

    /**
     * True when a battery of this tier fits a box of that voltage.
     * <p>
     * The comparison deliberately has no lower bound: whatever base the IC2 tier ladder uses (the API documents
     * 1 = LV, an older build could report 0), a low tier battery must fit the smallest box. The clamp below only
     * protects against a nonsensical negative report, it never rejects an LV cell.
     */
    public static boolean fits(int batteryTier, Voltage boxVoltage) {
        return fits(batteryTier, boxVoltage.getTier());
    }

    /**
     * Same rule against a bare tier number, for the code that only has the mirrored tier of a box at hand
     * (the client side of the container, which cannot read the item stacks of an entity inventory).
     */
    public static boolean fits(int batteryTier, int boxTier) {
        return Math.max(LOWEST_TIER, batteryTier) <= boxTier;
    }

    /** Voltage of a tier number, or {@link Voltage#LV} for anything out of range. */
    public static Voltage voltageOfTier(int tier) {
        for (Voltage voltage : Voltage.values()) {
            if (voltage.getTier() == tier) {
                return voltage;
            }
        }
        return Voltage.LV;
    }
}
