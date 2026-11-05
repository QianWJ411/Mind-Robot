package com.github.qianwj.mindrobot.client.render;

import java.util.Random;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.RenderGameOverlayEvent;

import org.lwjgl.opengl.GL11;

import com.github.qianwj.mindrobot.Config;
import com.github.qianwj.mindrobot.MindRobot;
import com.github.qianwj.mindrobot.client.ClientLinkState;
import com.github.qianwj.mindrobot.item.HelmetTier;
import com.github.qianwj.mindrobot.item.ItemRemoteHelmet;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Driving picture noise (D12, R29/R30): the farther a helmet generation is from the prototype, the cleaner the
 * camera picture of the driven robot.
 * <p>
 * The overlay is drawn on top of the whole HUD through {@link RenderGameOverlayEvent.Post} with
 * {@link RenderGameOverlayEvent.ElementType#ALL}, which is the last overlay event of a frame and therefore
 * covers the debuff vignette, the hotbar and the chat. It never draws a single pixel on the CPU: the grain is one
 * pre-generated 16x16 texture tiled over the screen with a random UV origin, the glitch is one flat quad.
 * <p>
 * It is active only while a link is open and no screen is open, and it disappears on the next frame after the
 * link closes, so it shares the lifetime of the camera takeover.
 */
public class NoiseOverlay {

    /** Side length of {@code noise.png}; the overlay relies on GL_REPEAT and this constant to tile it. */
    private static final int TILE_SIZE = 16;
    private static final ResourceLocation NOISE_TEXTURE = new ResourceLocation(
        MindRobot.MODID,
        "textures/gui/noise.png");

    /** Frame time the configured glitch chances are calibrated for, so the frequency does not follow the FPS. */
    private static final float REFERENCE_FRAME_MILLIS = 1000.0F / 60.0F;
    /** Longest frame time taken into account; a loading hitch must not carpet the screen with glitch bands. */
    private static final long MAX_FRAME_MILLIS = 100L;

    private static final int MIN_BAND_FRAMES = 3;
    private static final int MAX_BAND_FRAMES = 8;
    private static final int MAX_BAND_HEIGHT = 4;
    /** The band is flatter than the grain, so a small alpha is scaled up when it is drawn as a solid strip. */
    private static final float BAND_ALPHA_SCALE = 2.5F;

    private final Random random = new Random();

    private long lastFrameMillis;
    private int bandFrames;
    private int bandY;
    private int bandHeight;
    private float bandAlpha;

    @SubscribeEvent
    public void onRenderGameOverlay(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.ALL) {
            return;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.thePlayer == null || minecraft.currentScreen != null
            || !ClientLinkState.isLinked()
            || !Config.enableNoise) {
            clearBand();
            return;
        }

        ScaledResolution resolution = event.resolution;
        HelmetTier tier = HelmetTier.byItemStack(minecraft.thePlayer.getCurrentArmor(ItemRemoteHelmet.HEAD_ARMOR_SLOT));
        float frameScale = frameScale();

        // Every state this pass touches is pushed and restored: a HUD handler must never leave the blend, alpha
        // test or depth test state behind, that is the classic source of broken GUIs in 1.7.10.
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT);
        try {
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

            drawGrain(minecraft, resolution, noiseAlpha(tier));
            drawGlitchBand(resolution, tier, frameScale);
        } finally {
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            GL11.glPopAttrib();
        }
    }

    /** Opacity configured for a generation, or 0 when the player turned that generation's grain off. */
    private static float noiseAlpha(HelmetTier tier) {
        switch (tier) {
            case MK2:
                return Config.noiseMk2;
            case MK3:
                return Config.noiseMk3;
            case MK1:
            default:
                return Config.noiseMk1;
        }
    }

    /**
     * One quad covering the screen whose texture coordinates are shifted by a random texel offset and span the
     * screen in tiles of {@link #TILE_SIZE} pixels. GL_REPEAT then repeats the single 16x16 texture for us, so
     * the whole overlay costs four vertices per frame instead of one draw per pixel.
     */
    private void drawGrain(Minecraft minecraft, ScaledResolution resolution, float alpha) {
        if (alpha <= 0.0F) {
            return;
        }
        int width = resolution.getScaledWidth();
        int height = resolution.getScaledHeight();
        int offsetX = random.nextInt(TILE_SIZE);
        int offsetY = random.nextInt(TILE_SIZE);

        minecraft.getTextureManager()
            .bindTexture(NOISE_TEXTURE);
        // Safe to set: the noise texture belongs to this mod and no other pass reads its sampler state.
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);

        float u0 = offsetX / (float) TILE_SIZE;
        float v0 = offsetY / (float) TILE_SIZE;
        float u1 = (offsetX + width) / (float) TILE_SIZE;
        float v1 = (offsetY + height) / (float) TILE_SIZE;

        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.setColorRGBA_F(1.0F, 1.0F, 1.0F, alpha);
        tessellator.addVertexWithUV(0.0D, height, 0.0D, u0, v1);
        tessellator.addVertexWithUV(width, height, 0.0D, u1, v1);
        tessellator.addVertexWithUV(width, 0.0D, 0.0D, u1, v0);
        tessellator.addVertexWithUV(0.0D, 0.0D, 0.0D, u0, v0);
        tessellator.draw();
    }

    /**
     * Occasional horizontal tear. Each generation only becomes a new band with its own probability; while a band
     * is alive it jitters up and down by one pixel per frame, which is what makes it read as interference rather
     * than as a painted stripe.
     */
    private void drawGlitchBand(ScaledResolution resolution, HelmetTier tier, float frameScale) {
        int width = resolution.getScaledWidth();
        int height = resolution.getScaledHeight();

        if (bandFrames > 0) {
            bandFrames--;
            int lowest = Math.max(0, height - bandHeight);
            bandY = Math.max(0, Math.min(lowest, bandY + random.nextInt(3) - 1));
        } else {
            float chance = tier.getDefaultGlitchChance() * Config.glitchChance * frameScale;
            if (Config.glitchChance <= 0.0F || chance <= 0.0F || random.nextFloat() >= Math.min(1.0F, chance)) {
                return;
            }
            bandHeight = 1 + random.nextInt(MAX_BAND_HEIGHT);
            bandY = random.nextInt(Math.max(1, height - bandHeight));
            bandFrames = MIN_BAND_FRAMES + random.nextInt(MAX_BAND_FRAMES - MIN_BAND_FRAMES + 1);
            bandAlpha = Math.min(1.0F, noiseAlpha(tier) * BAND_ALPHA_SCALE);
        }

        if (bandAlpha <= 0.0F) {
            return;
        }
        drawBand(width, bandY, bandHeight, bandAlpha);
    }

    /** Flat, deliberately over-bright strip: a solid quad, no texture, still a single tessellator draw call. */
    private void drawBand(int width, int y, int height, float alpha) {
        Tessellator tessellator = Tessellator.instance;
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        tessellator.startDrawingQuads();
        tessellator.setColorRGBA_F(0.92F, 0.97F, 1.0F, alpha);
        tessellator.addVertex(0.0D, y + height, 0.0D);
        tessellator.addVertex(width, y + height, 0.0D);
        tessellator.addVertex(width, y, 0.0D);
        tessellator.addVertex(0.0D, y, 0.0D);
        tessellator.draw();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }

    /**
     * Elapsed time since the previous frame expressed in 60 FPS frames, so the configured probabilities keep the
     * same on-screen frequency at any frame rate.
     */
    private float frameScale() {
        long now = System.nanoTime() / 1000000L;
        if (lastFrameMillis == 0L) {
            lastFrameMillis = now;
            return 1.0F;
        }
        long delta = now - lastFrameMillis;
        lastFrameMillis = now;
        // A paused game or a loading hitch must not stack up a queue of bands.
        delta = Math.max(1L, Math.min(MAX_FRAME_MILLIS, delta));
        return delta / REFERENCE_FRAME_MILLIS;
    }

    /** Drops the current tear so it does not survive a disconnect or an opened screen. */
    private void clearBand() {
        bandFrames = 0;
        bandAlpha = 0.0F;
        // Forget the frame clock as well: the first frame after reconnecting must not see a huge frame delta.
        lastFrameMillis = 0L;
    }
}
