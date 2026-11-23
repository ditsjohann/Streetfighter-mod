package com.streetfighter.client;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.SheetedDecalTextureGenerator;
import com.mojang.math.Axis;
import com.streetfighter.fight.FighterType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Mortal Kombat style X-ray finisher for the match-winning K.O. Time stops, the loser's skeleton appears inside a
 * translucent x-ray body (bone blocks inside light-blue glass), and the winner's signature blow cracks a specific bone
 * (Minecraft's block-breaking texture spreads over it) until it snaps apart. Each style has its own finisher:
 * weapon, target bone and number of blows.
 */
public final class XRayFinisher {
    public static final float DURATION = 3.8f;

    enum Bone { SKULL, JAW, SPINE, RIB, ARM, LEG, PELVIS, ALL }

    record Finisher(String name, Bone target, int blows, ItemStack weapon) {
    }

    private record Piece(Bone tag, float x, float y, float z, float sx, float sy, float sz) {
    }

    static Finisher finisherFor(FighterType style) {
        return switch (style) {
            case STEVE -> new Finisher("PICKAXE TO THE DOME", Bone.SKULL, 2, new ItemStack(Items.DIAMOND_PICKAXE));
            case SKELETON -> new Finisher("RIB COLLECTOR", Bone.RIB, 3, new ItemStack(Items.BONE));
            case VILLAGER -> new Finisher("FINAL OFFER", Bone.JAW, 2, new ItemStack(Items.EMERALD));
            case ALCHEMIST -> new Finisher("BRITTLE BONES", Bone.ALL, 1, new ItemStack(Items.SPLASH_POTION));
            case PIG -> new Finisher("HOG PILE", Bone.SPINE, 2, new ItemStack(Items.CARROT_ON_A_STICK));
            case CHICKEN -> new Finisher("PECK FRACTURE", Bone.SKULL, 3, new ItemStack(Items.FEATHER));
            case GOAT -> new Finisher("RAM RUPTURE", Bone.RIB, 2, new ItemStack(Items.GOAT_HORN));
            case OAK -> new Finisher("TIMBER SNAP", Bone.LEG, 2, new ItemStack(Items.OAK_LOG));
            case IRON_ORE -> new Finisher("SPINE SMELTER", Bone.SPINE, 3, new ItemStack(Items.RAW_IRON));
            case STONE_PICKAXE -> new Finisher("DEEP MINE", Bone.SKULL, 2, new ItemStack(Items.STONE_PICKAXE));
            case ZOMBIE -> new Finisher("BONE APPETIT", Bone.ARM, 3, new ItemStack(Items.ROTTEN_FLESH));
            case COW -> new Finisher("UDDER DESTRUCTION", Bone.RIB, 2, new ItemStack(Items.MILK_BUCKET));
            case DONKEY -> new Finisher("KICK OF DOOM", Bone.JAW, 1, new ItemStack(Items.SADDLE));
            case CREEPER -> new Finisher("TOTAL DETONATION", Bone.ALL, 1, new ItemStack(Items.GUNPOWDER));
            case BRAWLER -> new Finisher("BONE BREAKER", Bone.RIB, 2, new ItemStack(Items.BONE));
        };
    }

    private final Finisher fin;
    private final boolean quad;
    private final BlockState boneMat;
    private final List<Piece> pieces = new ArrayList<>();
    private final float start;
    private final boolean winnerIsPlayer;
    private int blowsPlayed;
    private boolean endSound;

    /**
     * @param style      the winner's fighting style (picks the finisher)
     * @param victimBody the loser's body (picks the skeleton and the bone material)
     */
    public XRayFinisher(FighterType style, FighterType victimBody, boolean winnerIsPlayer, float now) {
        this.fin = finisherFor(style);
        this.winnerIsPlayer = winnerIsPlayer;
        this.start = now;
        this.quad = switch (victimBody) {
            case PIG, GOAT, COW, DONKEY -> true;
            default -> false;
        };
        this.boneMat = switch (victimBody) {
            case OAK -> Blocks.STRIPPED_OAK_LOG.defaultBlockState();
            case IRON_ORE -> Blocks.IRON_BLOCK.defaultBlockState();
            case STONE_PICKAXE -> Blocks.OAK_PLANKS.defaultBlockState();
            default -> Blocks.BONE_BLOCK.defaultBlockState();
        };
        if (quad) buildQuad();
        else buildBiped(victimBody == FighterType.CHICKEN ? 0.7f : 1f);
    }

    private void buildBiped(float k) {
        add(Bone.SKULL, 0, 1.58f, 0, 0.42f, 0.38f, 0.4f, k);
        add(Bone.JAW, 0, 1.49f, 0.04f, 0.3f, 0.09f, 0.28f, k);
        for (int i = 0; i < 5; i++) add(Bone.SPINE, 0, 0.92f + i * 0.11f, -0.1f, 0.11f, 0.08f, 0.11f, k);
        for (int i = 0; i < 4; i++) add(Bone.RIB, 0, 1.06f + i * 0.1f, 0.02f, 0.56f - i * 0.03f, 0.05f, 0.3f, k);
        add(Bone.PELVIS, 0, 0.84f, 0, 0.5f, 0.1f, 0.24f, k);
        for (int s = -1; s <= 1; s += 2) {
            add(Bone.ARM, s * 0.4f, 1.0f, 0, 0.1f, 0.44f, 0.1f, k);
            add(Bone.ARM, s * 0.42f, 0.58f, 0.02f, 0.09f, 0.4f, 0.09f, k);
            add(Bone.LEG, s * 0.15f, 0.44f, 0, 0.12f, 0.4f, 0.12f, k);
            add(Bone.LEG, s * 0.15f, 0.04f, 0, 0.1f, 0.38f, 0.1f, k);
        }
    }

    private void buildQuad() {
        add(Bone.SKULL, 0.82f, 0.62f, 0, 0.38f, 0.34f, 0.34f, 1);
        add(Bone.JAW, 0.86f, 0.54f, 0, 0.3f, 0.08f, 0.26f, 1);
        for (int i = 0; i < 6; i++) add(Bone.SPINE, -0.6f + i * 0.22f, 0.86f, 0, 0.16f, 0.1f, 0.1f, 1);
        for (int i = 0; i < 4; i++) add(Bone.RIB, -0.25f + i * 0.17f, 0.5f, 0, 0.06f, 0.36f, 0.42f, 1);
        add(Bone.PELVIS, -0.62f, 0.76f, 0, 0.22f, 0.14f, 0.36f, 1);
        for (int s = -1; s <= 1; s += 2) {
            add(Bone.LEG, 0.45f, 0.05f, s * 0.15f, 0.1f, 0.72f, 0.1f, 1);
            add(Bone.LEG, -0.55f, 0.05f, s * 0.15f, 0.1f, 0.72f, 0.1f, 1);
        }
    }

    private void add(Bone tag, float x, float y, float z, float sx, float sy, float sz, float k) {
        pieces.add(new Piece(tag, x * k, y * k, z * k, sx * k, sy * k, sz * k));
    }

    public boolean done(float now) {
        return now - start > DURATION;
    }

    private float blowTime(int i) {
        float first = 0.75f, last = 2.35f;
        return fin.blows <= 1 ? 1.5f : first + (last - first) * i / (fin.blows - 1);
    }

    private void play(SoundEvent e, float vol, float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(e, pitch, vol));
    }

    /** Sounds on each blow; call every frame. */
    public void tick(float now) {
        float a = now - start;
        while (blowsPlayed < fin.blows && a >= blowTime(blowsPlayed)) {
            blowsPlayed++;
            boolean last = blowsPlayed == fin.blows;
            play(SoundEvents.BONE_BLOCK_BREAK, 1f, last ? 0.6f : 0.9f);
            play(SoundEvents.PLAYER_ATTACK_CRIT, 0.8f, 0.7f);
            play(SoundEvents.SKELETON_HURT, 0.7f, last ? 0.5f : 0.8f);
            if (last) {
                play(SoundEvents.ANVIL_LAND, 0.6f, 0.5f);
                if (fin.target == Bone.ALL) play(SoundEvents.GENERIC_EXPLODE.value(), 0.7f, 1.2f);
            }
        }
        if (!endSound && a > DURATION - 0.6f) {
            endSound = true;
            play(SoundEvents.WITHER_SPAWN, 0.25f, 1.6f);
        }
    }

    public void render(GuiGraphics g, Font font, FightHud hud, int w, int h, float now) {
        float a = now - start;
        float fadeIn = Mth.clamp(a / 0.3f, 0, 1), fadeOut = Mth.clamp((DURATION - a) / 0.35f, 0, 1);
        float vis = Math.min(fadeIn, fadeOut);
        // x-ray screen: deep blue, scanlines, vignette
        g.fill(0, 0, w, h, FightHud.argb((int) (215 * vis), 0x020A1E));
        for (int y = 0; y < h; y += 3) g.fill(0, y, w, y + 1, FightHud.argb((int) (40 * vis), 0x4FA8FF));
        int band = (int) (h * 0.1f);
        g.fill(0, 0, w, band, FightHud.argb((int) (255 * vis), 0x000000));
        g.fill(0, h - band, w, h, FightHud.argb((int) (255 * vis), 0x000000));
        if (vis <= 0.02f) return;

        int hitsSoFar = 0;
        for (int i = 0; i < fin.blows; i++) if (a >= blowTime(i)) hitsSoFar++;
        float lastBlow = blowTime(fin.blows - 1);
        float broken = Mth.clamp((a - lastBlow) / 0.5f, 0, 1);
        int crack = hitsSoFar == 0 ? -1 : Math.min(9, (int) (hitsSoFar * 9f / fin.blows) + (broken > 0 ? 1 : 0));

        // ---- the 3D x-ray body
        float size = Math.min(w, h) * 0.32f;
        float shake = 0;
        for (int i = 0; i < hitsSoFar; i++) shake = Math.max(shake, Math.max(0, 0.25f - (a - blowTime(i))) * 40f);
        float cx = w / 2f + (float) Math.sin(a * 60) * shake, cy = h * 0.62f;
        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate(cx, cy, 300);
        ps.scale(size, -size, size);
        ps.mulPose(Axis.XP.rotationDegrees(12));
        ps.mulPose(Axis.YP.rotationDegrees((quad ? -35 : 20) + a * 22f * (winnerIsPlayer ? 1 : -1)));
        ps.translate(0, quad ? -0.5 : -1.0, 0);
        Lighting.setupFor3DItems();
        Minecraft mc = Minecraft.getInstance();
        BlockRenderDispatcher br = mc.getBlockRenderer();
        var buf = g.bufferSource();
        for (Piece p : pieces) {
            boolean target = fin.target == Bone.ALL || p.tag == fin.target
                    || (fin.target == Bone.SKULL && p.tag == Bone.JAW && broken > 0);
            if (target && broken > 0) {
                // snapped: two halves fly apart and twist
                float sep = broken * (fin.target == Bone.ALL ? 0.12f : 0.22f);
                boolean vertical = p.sy >= p.sx && p.sy >= p.sz;
                for (int half = -1; half <= 1; half += 2) {
                    ps.pushPose();
                    float ox = vertical ? half * sep * 0.4f : half * sep, oy = vertical ? half * sep : -broken * 0.05f;
                    ps.translate(p.x + ox, p.y + oy + p.sy / 2f, p.z);
                    ps.mulPose(Axis.ZP.rotationDegrees(half * broken * 25f));
                    ps.translate(-p.sx / 2f, -p.sy / 2f, -p.sz / 2f);
                    if (vertical) ps.translate(0, half > 0 ? p.sy / 2f : 0, 0);
                    else ps.translate(half > 0 ? p.sx / 2f : 0, 0, 0);
                    ps.scale(vertical ? p.sx : p.sx / 2f, vertical ? p.sy / 2f : p.sy, p.sz);
                    block(ps, br, buf, crack);
                    ps.popPose();
                }
            } else {
                ps.pushPose();
                ps.translate(p.x - p.sx / 2f, p.y, p.z - p.sz / 2f);
                ps.scale(p.sx, p.sy, p.sz);
                block(ps, br, buf, target ? crack : -1);
                ps.popPose();
            }
        }
        g.flush();
        // translucent flesh around the bones
        BlockState glass = Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState();
        if (quad) {
            glassBox(ps, br, buf, glass, -0.78f, 0.3f, -0.3f, 1.5f, 0.72f, 0.6f);
            glassBox(ps, br, buf, glass, 0.62f, 0.5f, -0.22f, 0.46f, 0.5f, 0.44f);
        } else {
            float k = pieces.get(0).y < 1.3f ? 0.7f : 1f;
            glassBox(ps, br, buf, glass, -0.34f * k, 0.8f * k, -0.2f * k, 0.68f * k, 0.72f * k, 0.4f * k);
            glassBox(ps, br, buf, glass, -0.27f * k, 1.52f * k, -0.25f * k, 0.54f * k, 0.5f * k, 0.5f * k);
            glassBox(ps, br, buf, glass, -0.56f * k, 0.55f * k, -0.12f * k, 0.22f * k, 0.95f * k, 0.24f * k);
            glassBox(ps, br, buf, glass, 0.34f * k, 0.55f * k, -0.12f * k, 0.22f * k, 0.95f * k, 0.24f * k);
            glassBox(ps, br, buf, glass, -0.3f * k, 0f, -0.14f * k, 0.6f * k, 0.82f * k, 0.28f * k);
        }
        g.flush();
        ps.popPose();
        Lighting.setupFor3DItems();
        FighterRenderer.clearDepth(); // 2D on top of the 3D skeleton

        // ---- the winner's weapon swings in for each blow
        for (int i = 0; i < fin.blows; i++) {
            float t = a - blowTime(i) + 0.22f; // 0.22 s wind-in before contact
            if (t < 0 || t > 0.5f) continue;
            float p = Mth.clamp(t / 0.22f, 0, 1);
            float from = winnerIsPlayer ? -1 : 1;
            float x = cx + from * (1 - p) * w * 0.35f, y = cy - size * (quad ? 0.25f : 0.6f) - (1 - p) * size * 0.4f;
            ps.pushPose();
            ps.translate(x, y, 450);
            ps.mulPose(Axis.ZP.rotationDegrees(from * (-90 + p * 140)));
            ps.scale(3.2f, 3.2f, 1);
            g.renderItem(fin.weapon, -8, -8);
            ps.popPose();
            if (t > 0.22f) {
                float r = (t - 0.22f) / 0.28f;
                int ring = (int) (size * 0.2f + r * size * 0.5f);
                int al = (int) (220 * (1 - r));
                int rx = (int) cx, ry = (int) (cy - size * (quad ? 0.25f : 0.6f));
                g.fill(rx - ring, ry - 1, rx + ring, ry + 1, FightHud.argb(al, 0xFFFFFF));
                g.fill(rx - 1, ry - ring, rx + 1, ry + ring, FightHud.argb(al, 0xFFFFFF));
                hud.outlined(g, i == fin.blows - 1 ? "SNAP!" : "CRACK!", rx + from * -size * 0.55f, ry - size * 0.2f,
                        2.6f + (1 - r), FightHud.argb((int) (255 * (1 - r * 0.6f)), 0xFF5A3A), 0xFF000000);
            }
        }

        // ---- titles
        ps.pushPose();
        ps.translate(0, 0, 500);
        hud.outlined(g, "X-RAY", w / 2f, band + 18, 3.2f, FightHud.argb((int) (255 * vis), 0x7FE8FF), 0xFF00304A);
        hud.outlined(g, fin.name, w / 2f, h - band - 16, 2.0f, FightHud.argb((int) (255 * vis), 0xFFD21A), 0xFF6A1000);
        ps.popPose();
    }

    private void block(PoseStack ps, BlockRenderDispatcher br, net.minecraft.client.renderer.MultiBufferSource.BufferSource buf,
                       int crack) {
        // bones are drawn with fixed bright lighting so they read as an x-ray
        br.renderSingleBlock(boneMat, ps, buf, 0xF000F0, OverlayTexture.NO_OVERLAY);
        if (crack >= 0) {
            var vc = new SheetedDecalTextureGenerator(buf.getBuffer(ModelBakery.DESTROY_TYPES.get(crack)), ps.last(), 1.0f);
            br.renderBreakingTexture(boneMat, BlockPos.ZERO, Minecraft.getInstance().level, ps, vc);
        }
    }

    private void glassBox(PoseStack ps, BlockRenderDispatcher br, net.minecraft.client.renderer.MultiBufferSource buf,
                          BlockState glass, float x, float y, float z, float sx, float sy, float sz) {
        ps.pushPose();
        ps.translate(x, y, z);
        ps.scale(sx, sy, sz);
        br.renderSingleBlock(glass, ps, buf, 0xF000F0, OverlayTexture.NO_OVERLAY);
        ps.popPose();
    }

}
