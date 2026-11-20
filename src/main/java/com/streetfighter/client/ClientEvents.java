package com.streetfighter.client;

import com.streetfighter.SFConfig;
import com.streetfighter.network.ChallengePacket;
import com.streetfighter.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** "Hold E on a mob to challenge it" plus the little HUD prompt. */
public final class ClientEvents {
    static boolean pendingDifficultyPrompt;

    private static LivingEntity lookTarget;
    private static int holdTarget = -1;
    private static int holdTicks;
    private static int cooldown;
    private static final int TAP_TICKS = 6;
    private static boolean tapCandidate;
    private static boolean wasHeld;
    private static int pressTicks;

    private ClientEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            lookTarget = null;
            holdTicks = 0;
            pendingDifficultyPrompt = pendingDifficultyPrompt && mc.level != null;
            return;
        }
        if (pendingDifficultyPrompt && mc.screen == null && mc.getOverlay() == null) {
            pendingDifficultyPrompt = false;
            mc.setScreen(new DifficultyScreen());
            return;
        }
        if (cooldown > 0) cooldown--;

        lookTarget = mc.screen == null && !player.isSpectator() ? findTarget(mc, player) : null;

        // When aiming at a mob, E means "challenge" if held. A quick tap still opens the inventory.
        boolean sharesInventoryKey = ClientSetup.CHALLENGE_KEY.getKey().equals(mc.options.keyInventory.getKey());
        boolean held = mc.screen == null && ClientSetup.CHALLENGE_KEY.isDown();
        boolean swallowed = false;
        if (lookTarget != null && sharesInventoryKey) {
            while (mc.options.keyInventory.consumeClick()) swallowed = true;
        }
        while (ClientSetup.CHALLENGE_KEY.consumeClick()) {
            // we only care about the held state
        }
        if (swallowed) {
            tapCandidate = true;
            pressTicks = 0;
            if (!held) { // pressed and released within one tick
                openInventory(mc, player);
                tapCandidate = false;
            }
        }

        if (held) {
            pressTicks++;
            if (lookTarget != null && cooldown == 0) {
                if (lookTarget.getId() != holdTarget) {
                    holdTarget = lookTarget.getId();
                    holdTicks = 0;
                }
                holdTicks++;
                if (holdTicks >= requiredTicks()) {
                    ModNetwork.sendToServer(new ChallengePacket(lookTarget.getId()));
                    holdTicks = 0;
                    holdTarget = -1;
                    cooldown = 40;
                    tapCandidate = false;
                }
            } else {
                holdTicks = 0;
                holdTarget = -1;
            }
        } else {
            if (wasHeld && tapCandidate && pressTicks <= TAP_TICKS && mc.screen == null) openInventory(mc, player);
            tapCandidate = false;
            pressTicks = 0;
            holdTicks = 0;
            holdTarget = -1;
        }
        wasHeld = held;
    }

    private static void openInventory(Minecraft mc, LocalPlayer player) {
        if (mc.gameMode != null && mc.gameMode.isServerControlledInventory()) {
            player.sendOpenInventory();
        } else {
            mc.getTutorial().onOpenInventory();
            mc.setScreen(new InventoryScreen(player));
        }
    }

    private static int requiredTicks() {
        return SFConfig.HOLD_TICKS.get();
    }

    private static LivingEntity findTarget(Minecraft mc, LocalPlayer player) {
        double range = SFConfig.CHALLENGE_RANGE.get();
        Vec3 eye = player.getEyePosition(1f);
        Vec3 look = player.getViewVector(1f);
        Vec3 end = eye.add(look.scale(range));
        AABB box = player.getBoundingBox().expandTowards(look.scale(range)).inflate(1.0);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, end, box,
                e -> e instanceof Mob && e.isAlive() && !e.isSpectator(), range * range);
        if (hit != null && hit.getEntity() instanceof LivingEntity living && canSee(mc, player, eye, hit.getLocation())) {
            return living;
        }
        // Forgiving fallback: the mob closest to where you're aiming (within ~32 degrees of its nearest
        // point), so short mobs like pigs and chickens are easy to pick without staring at your feet.
        LivingEntity best = null;
        double bestAngle = Math.toRadians(32);
        for (Mob mob : mc.level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(range))) {
            if (!mob.isAlive()) continue;
            AABB bb = mob.getBoundingBox();
            Vec3 center = new Vec3((bb.minX + bb.maxX) / 2, Math.max(bb.minY, Math.min(bb.maxY, eye.y)), (bb.minZ + bb.maxZ) / 2);
            Vec3 to = center.subtract(eye);
            double dist = to.length();
            if (dist > range + mob.getBbWidth() / 2 || dist < 0.01) continue;
            double angle = Math.acos(Math.max(-1, Math.min(1, to.normalize().dot(look))));
            if (angle < bestAngle && canSee(mc, player, eye, center)) {
                bestAngle = angle;
                best = mob;
            }
        }
        return best;
    }

    private static boolean canSee(Minecraft mc, LocalPlayer player, Vec3 eye, Vec3 target) {
        HitResult block = mc.level.clip(new ClipContext(eye, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        return block.getType() == HitResult.Type.MISS || block.getLocation().distanceToSqr(eye) >= target.distanceToSqr(eye) - 0.01;
    }

    /** HUD layer registered through AddGuiOverlayLayersEvent. */
    public static void renderHud(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        if (lookTarget == null || mc.screen != null || mc.options.hideGui || mc.player == null) return;
        Font font = mc.font;
        int cx = g.guiWidth() / 2;
        int y = g.guiHeight() / 2 + 14;
        String key = ClientSetup.CHALLENGE_KEY.getTranslatedKeyMessage().getString();
        String text = "Hold [" + key + "] to challenge " + lookTarget.getDisplayName().getString();
        int w = font.width(text);
        g.fill(cx - w / 2 - 4, y - 3, cx + w / 2 + 4, y + 11, 0x90000000);
        g.drawString(font, text, cx - w / 2, y, 0xFFFFD84A, true);
        if (holdTicks > 0) {
            float p = Math.min(1f, holdTicks / (float) requiredTicks());
            int bw = 80;
            int bx = cx - bw / 2, by = y + 14;
            g.fill(bx - 1, by - 1, bx + bw + 1, by + 6, 0xFF000000);
            g.fill(bx, by, bx + (int) (bw * p), by + 5, p >= 1f ? 0xFFFFFFFF : 0xFFE8432E);
            if (p > 0.5f) {
                String vs = "VS!";
                g.drawString(font, vs, cx - font.width(vs) / 2, by + 9, 0xFFFF5533, true);
            }
        }
    }
}
