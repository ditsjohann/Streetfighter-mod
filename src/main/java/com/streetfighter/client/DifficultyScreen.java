package com.streetfighter.client;

import com.streetfighter.fight.AIDifficulty;
import com.streetfighter.network.ModNetwork;
import com.streetfighter.network.SetDifficultyPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Shown once when a new world starts: pick how tough the mobs fight. */
public class DifficultyScreen extends Screen {
    public DifficultyScreen() {
        super(Component.literal("Street Fighter - AI Difficulty"));
    }

    @Override
    protected void init() {
        int cx = width / 2;
        int y = height / 2 + 10;
        addRenderableWidget(Button.builder(Component.literal("§aNORMAL"), b -> choose(AIDifficulty.NORMAL))
                .bounds(cx - 155, y, 150, 24).build());
        addRenderableWidget(Button.builder(Component.literal("§cHARD"), b -> choose(AIDifficulty.HARD))
                .bounds(cx + 5, y, 150, 24).build());
    }

    private void choose(AIDifficulty d) {
        ModNetwork.sendToServer(new SetDifficultyPacket(d.id));
        onClose();
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        int cx = width / 2;
        int top = height / 2 - 70;
        g.pose().pushPose();
        g.pose().translate(cx, top, 0);
        g.pose().scale(2.5f, 2.5f, 1f);
        g.drawCenteredString(font, "STREET FIGHTER", 0, 0, 0xFFFFC21A);
        g.pose().popPose();
        g.drawCenteredString(font, "A new world! How hard should mobs fight when you challenge them?", cx, top + 32, 0xFFFFFFFF);
        g.drawCenteredString(font, "Normal: slower reactions, blocks sometimes, rarely combos.", cx, top + 50, 0xFF9CFF9C);
        g.drawCenteredString(font, "Hard: fast reactions, blocks & anti-airs, punishes and combos.", cx, top + 62, 0xFFFF9C9C);
        g.drawCenteredString(font, "(Ops can change it later with /streetfighter difficulty)", cx, height / 2 + 44, 0xFF9A9A9A);
    }
}
