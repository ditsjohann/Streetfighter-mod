package com.streetfighter.client;

import net.minecraft.client.Minecraft;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Small per-player client settings that survive restarts: whether you've opened the Fight Guide, how far you got
 * in the first-fight training, and which one-time tips you've already seen.
 */
public final class ClientPrefs {
    private static Properties props;

    private ClientPrefs() {
    }

    private static Path file() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve("streetfighter-client.properties");
    }

    private static Properties props() {
        if (props == null) {
            props = new Properties();
            try (Reader r = Files.newBufferedReader(file())) {
                props.load(r);
            } catch (Exception ignored) {
                // first run
            }
        }
        return props;
    }

    private static void save() {
        try {
            Files.createDirectories(file().getParent());
            try (Writer w = Files.newBufferedWriter(file())) {
                props().store(w, "Street Fighter mod - client settings");
            }
        } catch (Exception ignored) {
            // not fatal
        }
    }

    public static boolean flag(String key) {
        return Boolean.parseBoolean(props().getProperty(key, "false"));
    }

    public static void setFlag(String key, boolean v) {
        props().setProperty(key, String.valueOf(v));
        save();
    }

    public static int integer(String key, int def) {
        try {
            return Integer.parseInt(props().getProperty(key, String.valueOf(def)));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public static void setInteger(String key, int v) {
        props().setProperty(key, String.valueOf(v));
        save();
    }
}
