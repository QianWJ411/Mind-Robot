package com.github.qianwj.mindrobot.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import com.github.qianwj.mindrobot.MindRobot;

/**
 * Minimal robot renderer: a textured box. No model class is involved, so nothing can be missing at runtime.
 * <p>
 * The robot the client is currently looking through is skipped, the camera sits inside its head (R4).
 */
public class RenderRobot extends Render {

    private static final ResourceLocation ROBOT_TEXTURE = new ResourceLocation(
        MindRobot.MODID,
        "textures/entities/robot.png");

    private static final float HALF_WIDTH = 0.3F;
    private static final float BODY_HEIGHT = 1.8F;
    private static final double UNIT = 1.0D;

    public RenderRobot() {
        shadowSize = 0.4F;
    }

    @Override
    public void doRender(Entity entity, double x, double y, double z, float yaw, float partialTicks) {
        if (entity == Minecraft.getMinecraft().renderViewEntity) {
            return;
        }
        bindEntityTexture(entity);
        GL11.glPushMatrix();
        GL11.glTranslatef((float) x, (float) y, (float) z);
        GL11.glRotatef(180.0F - yaw, 0.0F, 1.0F, 0.0F);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        renderBody();
        GL11.glPopMatrix();
    }

    @Override
    protected ResourceLocation getEntityTexture(Entity entity) {
        return ROBOT_TEXTURE;
    }

    private void renderBody() {
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();

        // south (+z)
        tessellator.setNormal(0.0F, 0.0F, 1.0F);
        addVertex(tessellator, -HALF_WIDTH, 0.0F, HALF_WIDTH, 0.0D, 1.0D);
        addVertex(tessellator, HALF_WIDTH, 0.0F, HALF_WIDTH, 1.0D, 1.0D);
        addVertex(tessellator, HALF_WIDTH, BODY_HEIGHT, HALF_WIDTH, 1.0D, 0.0D);
        addVertex(tessellator, -HALF_WIDTH, BODY_HEIGHT, HALF_WIDTH, 0.0D, 0.0D);

        // north (-z)
        tessellator.setNormal(0.0F, 0.0F, -1.0F);
        addVertex(tessellator, HALF_WIDTH, 0.0F, -HALF_WIDTH, 0.0D, 1.0D);
        addVertex(tessellator, -HALF_WIDTH, 0.0F, -HALF_WIDTH, 1.0D, 1.0D);
        addVertex(tessellator, -HALF_WIDTH, BODY_HEIGHT, -HALF_WIDTH, 1.0D, 0.0D);
        addVertex(tessellator, HALF_WIDTH, BODY_HEIGHT, -HALF_WIDTH, 0.0D, 0.0D);

        // east (+x)
        tessellator.setNormal(1.0F, 0.0F, 0.0F);
        addVertex(tessellator, HALF_WIDTH, 0.0F, HALF_WIDTH, 0.0D, 1.0D);
        addVertex(tessellator, HALF_WIDTH, 0.0F, -HALF_WIDTH, 1.0D, 1.0D);
        addVertex(tessellator, HALF_WIDTH, BODY_HEIGHT, -HALF_WIDTH, 1.0D, 0.0D);
        addVertex(tessellator, HALF_WIDTH, BODY_HEIGHT, HALF_WIDTH, 0.0D, 0.0D);

        // west (-x)
        tessellator.setNormal(-1.0F, 0.0F, 0.0F);
        addVertex(tessellator, -HALF_WIDTH, 0.0F, -HALF_WIDTH, 0.0D, 1.0D);
        addVertex(tessellator, -HALF_WIDTH, 0.0F, HALF_WIDTH, 1.0D, 1.0D);
        addVertex(tessellator, -HALF_WIDTH, BODY_HEIGHT, HALF_WIDTH, 1.0D, 0.0D);
        addVertex(tessellator, -HALF_WIDTH, BODY_HEIGHT, -HALF_WIDTH, 0.0D, 0.0D);

        // top (+y)
        tessellator.setNormal(0.0F, 1.0F, 0.0F);
        addVertex(tessellator, -HALF_WIDTH, BODY_HEIGHT, HALF_WIDTH, 0.0D, 1.0D);
        addVertex(tessellator, HALF_WIDTH, BODY_HEIGHT, HALF_WIDTH, 1.0D, 1.0D);
        addVertex(tessellator, HALF_WIDTH, BODY_HEIGHT, -HALF_WIDTH, 1.0D, 0.0D);
        addVertex(tessellator, -HALF_WIDTH, BODY_HEIGHT, -HALF_WIDTH, 0.0D, 0.0D);

        // bottom (-y)
        tessellator.setNormal(0.0F, -1.0F, 0.0F);
        addVertex(tessellator, -HALF_WIDTH, 0.0F, -HALF_WIDTH, 0.0D, 1.0D);
        addVertex(tessellator, HALF_WIDTH, 0.0F, -HALF_WIDTH, 1.0D, 1.0D);
        addVertex(tessellator, HALF_WIDTH, 0.0F, HALF_WIDTH, 1.0D, 0.0D);
        addVertex(tessellator, -HALF_WIDTH, 0.0F, HALF_WIDTH, 0.0D, 0.0D);

        tessellator.draw();
    }

    private void addVertex(Tessellator tessellator, float x, float y, float z, double textureU, double textureV) {
        tessellator.addVertexWithUV(x, y, z, textureU * UNIT, textureV * UNIT);
    }
}
