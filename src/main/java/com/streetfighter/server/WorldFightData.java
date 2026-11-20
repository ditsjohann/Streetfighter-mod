package com.streetfighter.server;

import com.streetfighter.fight.AIDifficulty;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Per-world settings: the AI difficulty picked when the world was started. */
public class WorldFightData extends SavedData {
    private static final String NAME = "streetfighter";
    private AIDifficulty difficulty;

    public static WorldFightData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(WorldFightData::new, WorldFightData::load, null), NAME);
    }

    private static WorldFightData load(CompoundTag tag, HolderLookup.Provider provider) {
        WorldFightData data = new WorldFightData();
        if (tag.contains("difficulty")) data.difficulty = AIDifficulty.byId(tag.getString("difficulty"));
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        if (difficulty != null) tag.putString("difficulty", difficulty.id);
        return tag;
    }

    /** Null until someone has chosen. */
    public AIDifficulty difficulty() {
        return difficulty;
    }

    public AIDifficulty difficultyOrDefault() {
        return difficulty == null ? AIDifficulty.NORMAL : difficulty;
    }

    /** Sets and immediately writes the choice to disk, so it survives even a crash. */
    public void setDifficulty(MinecraftServer server, AIDifficulty difficulty) {
        this.difficulty = difficulty;
        setDirty();
        server.overworld().getDataStorage().save();
    }
}
