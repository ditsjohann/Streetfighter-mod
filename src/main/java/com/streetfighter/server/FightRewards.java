package com.streetfighter.server;

import com.streetfighter.SFConfig;
import com.streetfighter.entity.TreantEntity;
import com.streetfighter.fight.AIDifficulty;
import com.streetfighter.fight.FighterType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.InstrumentTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.InstrumentItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Street fighting is how you get stuff now: a beaten mob drops far more than killing it normally would
 * (a pig ~10 porkchops, a tree twice its logs...). Hard AI and perfect wins pay out more.
 */
public final class FightRewards {
    private FightRewards() {
    }

    public static void grant(ServerPlayer player, ServerLevel level, Mob mob, FighterType type, AIDifficulty difficulty,
                             boolean perfect) {
        RandomSource r = level.random;
        float mult = (float) (SFConfig.REWARD_MULTIPLIER.get() * (difficulty == AIDifficulty.HARD ? 1.5 : 1.0)
                * (perfect ? 1.25 : 1.0));
        Bag bag = new Bag(r, mult);
        switch (type) {
            case PIG -> {
                bag.add(Items.PORKCHOP, 8, 12);
                bag.add(Items.CARROT, 2, 4);
                bag.chance(Items.SADDLE, 0.15f);
            }
            case SKELETON -> {
                bag.add(Items.BONE, 8, 14);
                bag.add(Items.ARROW, 12, 24);
                bag.add(Items.BONE_MEAL, 4, 8);
                bag.chance(Items.BOW, 0.35f);
                bag.chance(Items.SKELETON_SKULL, 0.05f);
            }
            case VILLAGER -> {
                bag.add(Items.EMERALD, 6, 12);
                bag.add(Items.BREAD, 3, 6);
                bag.add(Items.WHEAT, 4, 8);
                bag.chance(Items.BELL, 0.1f);
            }
            case ALCHEMIST -> {
                bag.add(Items.EMERALD, 4, 8);
                bag.add(Items.REDSTONE, 4, 8);
                bag.add(Items.GLOWSTONE_DUST, 4, 8);
                bag.add(Items.NETHER_WART, 2, 4);
                bag.add(Items.EXPERIENCE_BOTTLE, 3, 6);
                bag.potion(Items.SPLASH_POTION, 2);
                bag.potion(Items.POTION, 1);
            }
            case CHICKEN -> {
                bag.add(Items.CHICKEN, 4, 6);
                bag.add(Items.FEATHER, 8, 16);
                bag.add(Items.EGG, 6, 12);
            }
            case GOAT -> {
                ItemStack horn = new ItemStack(Items.GOAT_HORN);
                InstrumentItem.setRandom(horn, InstrumentTags.REGULAR_GOAT_HORNS, r);
                bag.stacks.add(horn);
                bag.add(Items.LEATHER, 3, 6);
                bag.add(Items.SNOWBALL, 8, 16);
                bag.chance(Items.MILK_BUCKET, 0.5f);
            }
            case OAK -> {
                int logs = mob instanceof TreantEntity t ? Math.max(4, t.logCount()) : 5;
                bag.add(Items.OAK_LOG, logs * 2 + 2, logs * 2 + 6); // way more than chopping it
                bag.add(Items.OAK_SAPLING, 2, 4);
                bag.add(Items.APPLE, 2, 5);
                bag.add(Items.STICK, 6, 12);
                bag.chance(Items.GOLDEN_APPLE, 0.05f);
            }
            case IRON_ORE -> {
                int vein = mob instanceof com.streetfighter.entity.BlockBodyEntity b ? Math.max(1, b.blockCount()) : 2;
                bag.add(Items.RAW_IRON, vein * 2 + 4, vein * 2 + 8); // way more than mining it
                bag.add(Items.COBBLESTONE, 6, 12);
                bag.add(Items.IRON_INGOT, 1, 3);
                bag.chance(Items.RAW_IRON_BLOCK, 0.08f);
            }
            case STONE_PICKAXE -> {
                ItemStack pick = new ItemStack(Items.STONE_PICKAXE);
                var enchants = level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
                pick.enchant(enchants.getHolderOrThrow(net.minecraft.world.item.enchantment.Enchantments.EFFICIENCY),
                        difficulty == AIDifficulty.HARD ? 3 : 2);
                pick.enchant(enchants.getHolderOrThrow(net.minecraft.world.item.enchantment.Enchantments.UNBREAKING), 1);
                bag.stacks.add(pick);
                bag.add(Items.STONE_PICKAXE, 1, 1);
                bag.add(Items.COBBLESTONE, 6, 12);
                bag.add(Items.STICK, 4, 8);
            }
            case ZOMBIE -> {
                bag.add(Items.ROTTEN_FLESH, 10, 16);
                bag.add(Items.IRON_INGOT, 1, 3);
                bag.add(Items.CARROT, 1, 3);
                bag.add(Items.POTATO, 1, 3);
                bag.chance(Items.ZOMBIE_HEAD, 0.05f);
            }
            default -> {
                // any other mob: its own vanilla loot, rolled several times
                int rolls = 2 + (difficulty == AIDifficulty.HARD ? 1 : 0) + (perfect ? 1 : 0);
                rollLootTable(player, level, mob, rolls, bag.stacks);
                if (bag.stacks.isEmpty()) bag.add(Items.EMERALD, 1, 3);
            }
        }

        Component summary = summary(bag.stacks, mult); // before spawning: spawning splits the stacks
        Vec3 at = mob.position().add(0, Math.min(1.5, mob.getBbHeight() * 0.6), 0);
        for (ItemStack stack : bag.stacks) spawnFountain(level, at, stack, r);
        int xp = Math.round((type == FighterType.BRAWLER ? 12 : 20) * mult);
        ExperienceOrb.award(level, at, xp);
        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, at.x, at.y, at.z, 30, 0.4, 0.6, 0.4, 0.3);
        level.playSound(null, mob.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 1.6f);
        level.playSound(null, mob.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 1f, 0.6f);

        player.sendSystemMessage(summary);
    }

    private static void rollLootTable(ServerPlayer player, ServerLevel level, Mob mob, int rolls, List<ItemStack> out) {
        try {
            LootTable table = level.getServer().reloadableRegistries().getLootTable(mob.getLootTable());
            LootParams params = new LootParams.Builder(level)
                    .withParameter(LootContextParams.THIS_ENTITY, mob)
                    .withParameter(LootContextParams.ORIGIN, mob.position())
                    .withParameter(LootContextParams.DAMAGE_SOURCE, level.damageSources().playerAttack(player))
                    .withOptionalParameter(LootContextParams.ATTACKING_ENTITY, player)
                    .withOptionalParameter(LootContextParams.DIRECT_ATTACKING_ENTITY, player)
                    .withParameter(LootContextParams.LAST_DAMAGE_PLAYER, player)
                    .withLuck(player.getLuck())
                    .create(LootContextParamSets.ENTITY);
            for (int i = 0; i < rolls; i++) out.addAll(table.getRandomItems(params));
        } catch (Exception e) {
            // a broken or missing loot table shouldn't stop the reward
        }
    }

    private static void spawnFountain(ServerLevel level, Vec3 at, ItemStack stack, RandomSource r) {
        while (!stack.isEmpty()) {
            ItemStack part = stack.split(Math.min(stack.getMaxStackSize(), stack.getCount()));
            ItemEntity item = new ItemEntity(level, at.x, at.y, at.z, part);
            item.setDeltaMovement((r.nextDouble() - 0.5) * 0.3, 0.3 + r.nextDouble() * 0.25, (r.nextDouble() - 0.5) * 0.3);
            item.setPickUpDelay(15);
            level.addFreshEntity(item);
        }
    }

    private static Component summary(List<ItemStack> stacks, float mult) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (ItemStack s : stacks) counts.merge(s.getHoverName().getString(), s.getCount(), Integer::sum);
        MutableComponent c = Component.literal("LOOT ").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD);
        List<String> parts = new ArrayList<>();
        counts.forEach((name, n) -> parts.add(n + "x " + name));
        c.append(Component.literal(String.join(", ", parts)).withStyle(ChatFormatting.WHITE));
        if (mult > 1.01f) c.append(Component.literal("  (x" + String.format("%.2f", mult).replaceAll("0+$", "").replaceAll("\\.$", "") + " bonus)")
                .withStyle(ChatFormatting.GOLD));
        return c;
    }

    /** Collects stacks, applying the reward multiplier to counts. */
    private static final class Bag {
        final List<ItemStack> stacks = new ArrayList<>();
        final RandomSource r;
        final float mult;

        Bag(RandomSource r, float mult) {
            this.r = r;
            this.mult = mult;
        }

        void add(Item item, int min, int max) {
            int n = Math.max(1, Math.round((min + r.nextInt(max - min + 1)) * mult));
            stacks.add(new ItemStack(item, n));
        }

        void chance(Item item, float p) {
            if (r.nextFloat() < Math.min(1f, p * mult)) stacks.add(new ItemStack(item));
        }

        void potion(Item bottle, int count) {
            var pool = List.of(Potions.HEALING, Potions.STRENGTH, Potions.SWIFTNESS, Potions.REGENERATION,
                    Potions.FIRE_RESISTANCE, Potions.POISON);
            int n = Math.max(1, Math.round(count * mult));
            for (int i = 0; i < n; i++) stacks.add(PotionContents.createItemStack(bottle, pool.get(r.nextInt(pool.size()))));
        }
    }
}
