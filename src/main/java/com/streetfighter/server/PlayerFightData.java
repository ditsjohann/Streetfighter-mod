package com.streetfighter.server;

import com.streetfighter.fight.FighterType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;

/** Learned styles and win records, stored in the player's persistent NBT (copied across deaths). */
public final class PlayerFightData {
    public static final String KEY = "streetfighter";

    private PlayerFightData() {
    }

    private static CompoundTag root(Player player) {
        CompoundTag persistent = player.getPersistentData();
        if (!persistent.contains(KEY, Tag.TAG_COMPOUND)) persistent.put(KEY, new CompoundTag());
        return persistent.getCompound(KEY);
    }

    public static List<String> unlockedStyles(Player player) {
        List<String> out = new ArrayList<>();
        ListTag list = root(player).getList("styles", Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) out.add(list.getString(i));
        return out;
    }

    public static boolean hasStyle(Player player, FighterType type) {
        return unlockedStyles(player).contains(type.id);
    }

    /** @return true if this is newly learned */
    public static boolean unlock(Player player, FighterType type) {
        if (!type.learnable || hasStyle(player, type)) return false;
        CompoundTag root = root(player);
        ListTag list = root.getList("styles", Tag.TAG_STRING);
        list.add(StringTag.valueOf(type.id));
        root.put("styles", list);
        return true;
    }

    public static void resetStyles(Player player) {
        root(player).remove("styles");
    }

    public static int recordWin(Player player, String key) {
        CompoundTag wins = root(player).getCompound("wins");
        int n = wins.getInt(key) + 1;
        wins.putInt(key, n);
        root(player).put("wins", wins);
        return n;
    }

    public static int totalWins(Player player) {
        CompoundTag wins = root(player).getCompound("wins");
        int n = 0;
        for (String k : wins.getAllKeys()) n += wins.getInt(k);
        return n;
    }

    public static void copy(Player from, Player to) {
        CompoundTag src = from.getPersistentData();
        if (src.contains(KEY, Tag.TAG_COMPOUND)) to.getPersistentData().put(KEY, src.getCompound(KEY).copy());
    }
}
