package com.github.qianwj.mindrobot;

import java.io.File;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

import com.github.qianwj.mindrobot.item.HelmetTier;

public class Config {

    public static String greeting = "Hello World";

    /** Category holding the driving overlay knobs (D12). */
    private static final String CATEGORY_NOISE = "helmet.noise";
    /** Category holding the fixed energy draw of a driven robot (R40). */
    private static final String CATEGORY_ENERGY = "robot.energy";

    /** Master switch of the driving noise overlay. */
    public static boolean enableNoise = true;
    /** Overlay opacity of the brain computer helmet MKI, MKII and MKIII (0 disables that tier's grain). */
    public static float noiseMk1 = 0.35F;
    public static float noiseMk2 = 0.15F;
    public static float noiseMk3 = 0.04F;
    /** Multiplier of the per tier glitch band frequency (0 disables the bands, the grain stays untouched). */
    public static float glitchChance = 1.0F;

    /**
     * Fixed energy draw of a driven robot (R40). The numbers are the same whatever the voltage of the battery
     * box or the tier of the cells inside it: a bigger or better box only means the robot runs longer, never
     * faster and never stronger. 0 turns that particular draw off.
     */
    public static int euPerTickIdle = 8;
    public static int euPerBlockMined = 200;
    public static int euPerAttack = 100;

    public static void synchronizeConfiguration(File configFile) {
        Configuration configuration = new Configuration(configFile);

        greeting = configuration.getString("greeting", Configuration.CATEGORY_GENERAL, greeting, "How shall I greet?");

        enableNoise = configuration.getBoolean(
            "enableNoise",
            CATEGORY_NOISE,
            true,
            "Draw the helmet noise overlay while a robot is being driven (D12).");

        noiseMk1 = readBoundedFloat(
            configuration,
            "noiseMk1",
            HelmetTier.MK1.getDefaultNoiseAlpha(),
            "Opacity of the MKI grain overlay in the range 0 - 1.");
        noiseMk2 = readBoundedFloat(
            configuration,
            "noiseMk2",
            HelmetTier.MK2.getDefaultNoiseAlpha(),
            "Opacity of the MKII grain overlay in the range 0 - 1.");
        noiseMk3 = readBoundedFloat(
            configuration,
            "noiseMk3",
            HelmetTier.MK3.getDefaultNoiseAlpha(),
            "Opacity of the MKIII grain overlay in the range 0 - 1.");
        glitchChance = readBoundedFloat(
            configuration,
            "glitchChance",
            1.0F,
            0.0F,
            5.0F,
            "Multiplier of the glitch band frequency, 0 disables the bands.");

        // R40: the draw is fixed, only the capacity of the installed box changes how long it lasts.
        euPerTickIdle = configuration.getInt(
            "euPerTickIdle",
            CATEGORY_ENERGY,
            euPerTickIdle,
            0,
            100000,
            "EU a driven robot draws every tick while it is controlled (R40).");
        euPerBlockMined = configuration.getInt(
            "euPerBlockMined",
            CATEGORY_ENERGY,
            euPerBlockMined,
            0,
            1000000,
            "Extra EU for every block the robot breaks (R40).");
        euPerAttack = configuration.getInt(
            "euPerAttack",
            CATEGORY_ENERGY,
            euPerAttack,
            0,
            1000000,
            "Extra EU for every attack the robot lands (R40).");

        if (configuration.hasChanged()) {
            configuration.save();
        }
    }

    /**
     * Reads a float and keeps it inside {@code [min, max]}.
     * <p>
     * A malformed entry (wrong type, {@code NaN}) falls back to the default and a value outside the range is
     * clamped, so a hand edited configuration file can never break the overlay or the game start.
     */
    private static float readBoundedFloat(Configuration configuration, String key, float defaultValue, String comment) {
        return readBoundedFloat(configuration, key, defaultValue, 0.0F, 1.0F, comment);
    }

    private static float readBoundedFloat(Configuration configuration, String key, float defaultValue, float min,
        float max, String comment) {
        Property property = configuration.get(CATEGORY_NOISE, key, Float.toString(defaultValue), comment);
        property.setMinValue(min);
        property.setMaxValue(max);
        property.comment = comment + " [range: " + min + " ~ " + max + ", default: " + defaultValue + "]";

        float value;
        try {
            value = Float.parseFloat(
                property.getString()
                    .trim());
        } catch (NumberFormatException invalid) {
            MindRobot.LOG
                .warn("Config entry {}.{} is not a number, falling back to {}", CATEGORY_NOISE, key, defaultValue);
            value = defaultValue;
        }
        if (Float.isNaN(value)) {
            value = defaultValue;
        }
        return Math.max(min, Math.min(max, value));
    }
}
