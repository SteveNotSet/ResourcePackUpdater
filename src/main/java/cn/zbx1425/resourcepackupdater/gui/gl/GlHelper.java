package cn.zbx1425.resourcepackupdater.gui.gl;

import cn.zbx1425.resourcepackupdater.ResourcePackUpdater;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public class GlHelper {

    public static void clearScreen(float r, float g, float b) {
        RenderSystem.clearColor(r, g, b, 1f);
        RenderSystem.clear(16640, Minecraft.ON_OSX);
    }

    private static ShaderInstance previousShader;
    private static Matrix4f lastProjectionMat;
    private static VertexSorting lastVertexSorting;

    public static void initGlStates() {
        previousShader = RenderSystem.getShader();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.getModelViewStack().pushPose();
        RenderSystem.getModelViewStack().setIdentity();
        RenderSystem.applyModelViewMatrix();
        lastProjectionMat = RenderSystem.getProjectionMatrix();
        lastVertexSorting = RenderSystem.getVertexSorting();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.enableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
    }

    public static void resetGlStates() {
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.getModelViewStack().popPose();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.setShader(() -> previousShader);
        RenderSystem.setProjectionMatrix(lastProjectionMat, lastVertexSorting);
    }

    public static final ResourceLocation PRELOAD_FONT_TEXTURE =
            new ResourceLocation(ResourcePackUpdater.MOD_ID, "textures/font/cantarell.png");
    public static final SimpleFont preloadFont = new SimpleFont(PRELOAD_FONT_TEXTURE);

    private static BufferBuilder bufferBuilder;

    public static void begin(ResourceLocation texture) {
        bufferBuilder = Tesselator.getInstance().getBuilder();
        bufferBuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        Minecraft.getInstance().getTextureManager().getTexture(texture).setFilter(true, false);
        RenderSystem.setShaderTexture(0, texture);
    }

    public static void end() {
        Tesselator.getInstance().end();
    }

    public static void swapBuffer() throws MinecraftStoppingException {
        Window window = Minecraft.getInstance().getWindow();
        if (window.shouldClose()) {
            throw new MinecraftStoppingException();
        } else {
            window.updateDisplay();
        }
    }

    public static void blit(float x1, float y1, float width, float height, float u1, float v1, float u2, float v2, int color) {
        float x2 = x1 + width;
        float y2 = y1 + height;
        withColor(bufferBuilder.vertex(x1, y1, 1f).uv(u1, v1), color).endVertex();
        withColor(bufferBuilder.vertex(x2, y1, 1f).uv(u2, v1), color).endVertex();
        withColor(bufferBuilder.vertex(x2, y2, 1f).uv(u2, v2), color).endVertex();
        withColor(bufferBuilder.vertex(x1, y2, 1f).uv(u1, v2), color).endVertex();
    }

    public static void blit(float x1, float y1, float width, float height, int color) {
        float x2 = x1 + width;
        float y2 = y1 + height;
        withColor(bufferBuilder.vertex(x1, y1, 1f).uv(preloadFont.whiteU, preloadFont.whiteV), color).endVertex();
        withColor(bufferBuilder.vertex(x2, y1, 1f).uv(preloadFont.whiteU, preloadFont.whiteV), color).endVertex();
        withColor(bufferBuilder.vertex(x2, y2, 1f).uv(preloadFont.whiteU, preloadFont.whiteV), color).endVertex();
        withColor(bufferBuilder.vertex(x1, y2, 1f).uv(preloadFont.whiteU, preloadFont.whiteV), color).endVertex();
    }

    public static void drawShadowString(float x1, float y1, float width, float height, float fontSize,
                                  String text, int color, boolean monospace, boolean noWrap) {
        drawString(x1 + fontSize / 16, y1 + fontSize / 16, width, height, fontSize, text, 0xFF222222, monospace, noWrap);
        drawString(x1, y1, width, height, fontSize, text, color, monospace, noWrap);
    }

    public static void drawString(float x1, float y1, float width, float height, float fontSize,
                                  String text, int color, boolean monospace, boolean noWrap) {
        float CHAR_SPACING = 0f;
        float LINE_SPACING = 0.25f;

        var x = x1;
        var y = y1;
        for (char chr : text.toCharArray()) {
            if (chr == '\n') {
                y += fontSize + LINE_SPACING * fontSize;
                x = x1;
            } else if (chr == '\r') {
                // Ignore CR
            } else if (chr == '\t') {
                // Align to 10 spaces
                float alignToPixels = (preloadFont.spaceWidthPl + CHAR_SPACING) * 10 * fontSize;
                x = (float) (Math.ceil((x - x1) / alignToPixels) * alignToPixels + x1);
            } else if (chr == ' ') {
                x += (preloadFont.spaceWidthPl + CHAR_SPACING) * fontSize;
            } else {
                SimpleFont.GlyphProperty glyph = preloadFont.getGlyph(chr);
                float advance = glyph.advancePl * fontSize;

                if (x + advance + CHAR_SPACING * fontSize > x1 + width) {
                    if (noWrap) {
                        continue;
                    } else {
                        y += fontSize + LINE_SPACING * fontSize;
                        x = x1;
                    }
                }
                if (y + fontSize > y1 + height) {
                    return;
                }

                blit(x + glyph.offsetXPl * fontSize, y + (preloadFont.baseLineYPl + glyph.offsetYPl) * fontSize,
                        glyph.widthPl * fontSize, glyph.heightPl * fontSize,
                        glyph.u1, glyph.v1, glyph.u2, glyph.v2, color);
                x += advance + CHAR_SPACING * fontSize;
            }
        }
    }

    public static float getStringWidth(String text, float fontSize) {
        float CHAR_SPACING = 0f;

        float width = 0;
        float x = 0;
        for (char chr : text.toCharArray()) {
            if (chr == '\n') {
                width = Math.max(width, x);
                x = 0;
            } else if (chr == '\r') {
                // Ignore CR
            } else if (chr == '\t') {
                // Align to 10 spaces
                float alignToPixels = (preloadFont.spaceWidthPl + CHAR_SPACING) * 10 * fontSize;
                x = (float) (Math.ceil(x / alignToPixels) * alignToPixels);
            } else if (chr == ' ') {
                x += (preloadFont.spaceWidthPl + CHAR_SPACING) * fontSize;
            } else {
                SimpleFont.GlyphProperty glyph = preloadFont.getGlyph(chr);
                x += glyph.advancePl * fontSize + CHAR_SPACING * fontSize;
            }
        }
        return Math.max(width, x);
    }

    public static void setMatIdentity() {
        RenderSystem.getModelViewStack().setIdentity();
    }

    public static void setMatPixel() {
        Matrix4f matrix = new Matrix4f();
        matrix.scale(2, -2, 1);
        matrix.translate(-0.5f, -0.5f, 0);
        float rawWidth = Minecraft.getInstance().getWindow().getWidth();
        float rawHeight = Minecraft.getInstance().getWindow().getHeight();
        matrix.scale(1 / rawWidth, 1 / rawHeight, 1);
        RenderSystem.setProjectionMatrix(matrix, VertexSorting.ORTHOGRAPHIC_Z);
    }

    public static void setMatScaledPixel() {
        Matrix4f matrix = new Matrix4f();
        matrix.scale(2, -2, 1);
        matrix.translate(-0.5f, -0.5f, 0);
        matrix.scale(1f / getWidth(), 1f / getHeight(), 1);
        RenderSystem.setProjectionMatrix(matrix, VertexSorting.ORTHOGRAPHIC_Z);
    }

    public static int getWidth() {
        int rawWidth = Minecraft.getInstance().getWindow().getWidth();
        if (rawWidth < 854) {
            return rawWidth;
        } else if (rawWidth < 1920) {
            return (int)((rawWidth - 854) * 1f / (1920 - 854) * (1366 - 854) + 854);
        } else {
            return 1366;
        }
    }

    public static int getHeight() {
        int rawWidth = Minecraft.getInstance().getWindow().getWidth();
        int rawHeight = Minecraft.getInstance().getWindow().getHeight();
        return (int)(rawHeight * (getWidth() * 1f / rawWidth));
    }

    public static void setMatCenterForm(float width, float height, float widthPercent) {
        Matrix4f matrix = new Matrix4f();
        matrix.scale(2, -2, 1);
        matrix.translate(-0.5f, -0.5f, 0);
        float rawWidth = Minecraft.getInstance().getWindow().getWidth();
        float rawHeight = Minecraft.getInstance().getWindow().getHeight();
        matrix.scale(1f / rawWidth, 1f / rawHeight, 1);
        float formRawWidth = rawWidth * widthPercent;
        float formRawHeight = height / width * formRawWidth;
        matrix.translate((rawWidth - formRawWidth) / 2f, (rawHeight - formRawHeight) / 2f, 0);
        matrix.scale(formRawWidth / width, formRawHeight / height, 1);
        RenderSystem.setProjectionMatrix(matrix, VertexSorting.ORTHOGRAPHIC_Z);
    }

    public static void enableScissor(float x, float y, float width, float height) {
        Matrix4f posMap = RenderSystem.getProjectionMatrix();
        Vector3f bottomLeft = posMap.transformPosition(new Vector3f(x, y + height, 0));
        Vector3f topRight = posMap.transformPosition(new Vector3f(x + width, y, 0));
        float x1 = Mth.map(bottomLeft.x, -1, 1, 0, Minecraft.getInstance().getWindow().getWidth());
        float y1 = Mth.map(bottomLeft.y, -1, 1, 0, Minecraft.getInstance().getWindow().getHeight());
        float x2 = Mth.map(topRight.x, -1, 1, 0, Minecraft.getInstance().getWindow().getWidth());
        float y2 = Mth.map(topRight.y, -1, 1, 0, Minecraft.getInstance().getWindow().getHeight());
        RenderSystem.enableScissor((int)x1, (int)y1, (int)(x2 - x1), (int)(y2 - y1));
    }

    public static void disableScissor() {
        RenderSystem.disableScissor();
    }

    private static VertexConsumer withColor(VertexConsumer vc, int color) {
        int a = color >>> 24 & 0xFF;
        int r = color >>> 16 & 0xFF;
        int g = color >>> 8 & 0xFF;
        int b = color & 0xFF;
        return vc.color(r, g, b, a);
    }

    public static class MinecraftStoppingException extends RuntimeException {
        public MinecraftStoppingException() {
            super("Minecraft is now stopping.");
        }
    }
}
