package com.siickzz.ktsacademy.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class MainConfigManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("KTS-Academy-Config");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Object LOCK = new Object();

    private static volatile boolean INITIALIZED = false;
    private static Path configFile;
    private static volatile MainConfig CONFIG = new MainConfig();

    private MainConfigManager() {}

    public static void init() {
        if (INITIALIZED) {
            return;
        }
        synchronized (LOCK) {
            if (INITIALIZED) {
                return;
            }
            configFile = FabricLoader.getInstance().getConfigDir().resolve("main.json");
            ensureConfigExists();
            loadConfig();
            INITIALIZED = true;
        }
    }

    public static MainConfig get() {
        init();
        return CONFIG;
    }

    public static void reload() {
        init();
        loadConfig();
    }

    private static void ensureConfigExists() {
        try {
            if (!Files.exists(configFile)) {
                writeDefaultConfig(configFile);
                return;
            }
            if (Files.size(configFile) == 0L) {
                writeDefaultConfig(configFile);
            }
        } catch (IOException e) {
            LOGGER.warn("[KTS Academy] Impossible de verifier la config main.json: {}", e.getMessage());
        }
    }

    private static void loadConfig() {
        try (BufferedReader reader = Files.newBufferedReader(configFile, StandardCharsets.UTF_8)) {
            MainConfig loaded = GSON.fromJson(reader, MainConfig.class);
            if (loaded == null) {
                CONFIG = new MainConfig();
                writeDefaultConfig(configFile);
                return;
            }
            CONFIG = loaded;
        } catch (IOException e) {
            LOGGER.warn("[KTS Academy] Impossible de lire main.json: {}", e.getMessage());
            CONFIG = new MainConfig();
        }
    }

    private static void writeDefaultConfig(Path path) {
        try {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(new MainConfig(), writer);
            }
        } catch (IOException e) {
            LOGGER.warn("[KTS Academy] Impossible d'ecrire main.json par defaut: {}", e.getMessage());
        }
    }
}

