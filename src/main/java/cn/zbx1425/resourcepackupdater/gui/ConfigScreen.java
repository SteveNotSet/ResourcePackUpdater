package cn.zbx1425.resourcepackupdater.gui;

import cn.zbx1425.resourcepackupdater.Config;
import cn.zbx1425.resourcepackupdater.ResourcePackUpdater;
import cn.zbx1425.resourcepackupdater.gui.gl.GlHelper;
import cn.zbx1425.resourcepackupdater.mappings.Text;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.HashMap;

public class ConfigScreen extends Screen {

    public ConfigScreen() {
        super(Text.translatable("ResourcePackUpdater Config"));
    }

    private boolean isShowingLog = false;

    private final HashMap<Config.SourceProperty, Button> sourceButtons = new HashMap<>();

    @Override
    protected void init() {
        super.init();
        final int PADDING = 10;
        int btnWidthOuter = (width - PADDING * 2) / 2;
        int btnWidthInner = btnWidthOuter - PADDING * 2;
        Button btnShowLog = Button.builder(Text.translatable("Show Logs from Last Run"), (btn) -> {
            isShowingLog = true;
        }).bounds(PADDING + PADDING, 40, btnWidthInner, 20).build();
        Button btnReload = Button.builder(Text.translatable("Update & Reload"), (btn) -> {
            assert minecraft != null;
            minecraft.reloadResourcePacks();
        }).bounds(PADDING + btnWidthOuter + PADDING, 40, btnWidthInner, 20).build();
        Button btnReturn = Button.builder(Text.translatable("Return"), (btn) -> {
            assert minecraft != null;
            minecraft.setScreen(null);
        }).bounds(PADDING + btnWidthOuter + PADDING, height - 40, btnWidthInner, 20).build();
        addRenderableWidget(btnShowLog);
        addRenderableWidget(btnReload);
        addRenderableWidget(btnReturn);

        int btnY = 90;
        for (Config.SourceProperty source : ResourcePackUpdater.CONFIG.sourceList.value) {
            Button btnUseSource = Button.builder(Text.translatable(source.name), (btn) -> {
                ResourcePackUpdater.CONFIG.selectedSource.value = source;
                try {
                    ResourcePackUpdater.CONFIG.save();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                updateBtnEnable();
            }).bounds(PADDING + PADDING, btnY, btnWidthInner, 20).build();
            sourceButtons.put(source, btnUseSource);
            btnY += 20;
            addRenderableWidget(btnUseSource);
        }
        updateBtnEnable();
    }

    private void updateBtnEnable() {
        for (var entry : sourceButtons.entrySet()) {
            entry.getValue().active = !ResourcePackUpdater.CONFIG.selectedSource.value.equals(entry.getKey());
        }
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        if (isShowingLog) {
            GlHelper.initGlStates();
            try {
                ResourcePackUpdater.GL_PROGRESS_SCREEN.setToException();
                if (!ResourcePackUpdater.GL_PROGRESS_SCREEN.shouldContinuePausing(false)) {
                    isShowingLog = false;
                }
            } catch (GlHelper.MinecraftStoppingException ignored) {
                isShowingLog = false;
            }
            GlHelper.resetGlStates();
        } else {
            guiGraphics.fillGradient(0, 0, this.width, this.height, 0xff014e7c, 0xff02142a);
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            guiGraphics.blit(GlProgressScreen.PRELOAD_HEADER_TEXTURE, 10, 10, 256, 16, 0, 0, 512, 32, 512, 32);
            guiGraphics.drawString(font, "Source Servers:", 20, 76, 0xFFFFFFFF, true);
            guiGraphics.drawString(font, "https://www.zbx1425.cn", 20, height - 40, 0xFFFFFFFF, true);
            super.render(guiGraphics, mouseX, mouseY, delta);
        }
    }
}
