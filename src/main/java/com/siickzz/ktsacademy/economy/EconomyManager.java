/*
 ** EPITECH PROJECT, 2026
 ** KTS Academy
 ** File description:
 ** EconomyManager class
*/

package com.siickzz.ktsacademy.economy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.player.PlayerEntity;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Comparator;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class EconomyManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type STORAGE_TYPE = new TypeToken<Map<String, PlayerAccount>>() {}.getType();

    private static final Map<UUID, PlayerAccount> ACCOUNTS = new ConcurrentHashMap<>();
    private static Path accountsFile;

	public record BalanceEntry(UUID uuid, double balance) {}

    private EconomyManager() {}

    public static void init()
    {
        Path configDir = FabricLoader.getInstance().getConfigDir();
        Path modDir = configDir.resolve("ktsacademy");
        accountsFile = modDir.resolve("accounts.json");

        try {
            Files.createDirectories(modDir);
        } catch (IOException ignored) {}
        load();
    }

    public static PlayerAccount get(PlayerEntity player)
    {
        return get(player.getUuid());
    }

    public static PlayerAccount get(UUID uuid)
    {
        return ACCOUNTS.computeIfAbsent(uuid, id -> new PlayerAccount());
    }

    public static void save()
    {
        if (accountsFile == null)
            return;

        Map<String, PlayerAccount> storage = new ConcurrentHashMap<>();

        for (Map.Entry<UUID, PlayerAccount> entry : ACCOUNTS.entrySet())
            storage.put(entry.getKey().toString(), entry.getValue());
        try (BufferedWriter writer = Files.newBufferedWriter(accountsFile, StandardCharsets.UTF_8)) {
            GSON.toJson(storage, STORAGE_TYPE, writer);
        } catch (IOException ignored) {}
    }

	public static Set<UUID> knownPlayers()
    {
		return ACCOUNTS.keySet();
	}

    public static List<BalanceEntry> topBalances(int limit)
    {
        int safeLimit = Math.max(1, Math.min(limit, 50));

        if (ACCOUNTS.isEmpty())
            return List.of();

        ArrayList<BalanceEntry> entries = new ArrayList<>(ACCOUNTS.size());

        for (Map.Entry<UUID, PlayerAccount> e : ACCOUNTS.entrySet()) {
            UUID uuid = e.getKey();
            PlayerAccount acc = e.getValue();
            double bal = acc == null ? 0.0 : acc.getBalance();

            entries.add(new BalanceEntry(uuid, bal));
        }
        entries.sort(Comparator.comparingDouble(BalanceEntry::balance).reversed());
        if (entries.size() > safeLimit)
            return entries.subList(0, safeLimit);
        return entries;
    }

    public static void reload()
    {
        if (accountsFile == null) {
            init();
            return;
        }
        synchronized (EconomyManager.class) {
            save();
            ACCOUNTS.clear();
            load();
        }
    }

    private static void load()
    {
        if (accountsFile == null || !Files.exists(accountsFile))
            return;
        try (BufferedReader reader = Files.newBufferedReader(accountsFile, StandardCharsets.UTF_8)) {
            Map<String, PlayerAccount> storage = GSON.fromJson(reader, STORAGE_TYPE);

            if (storage == null)
                return;
            for (Map.Entry<String, PlayerAccount> entry : storage.entrySet()) {
                try {
                    UUID uuid = UUID.fromString(entry.getKey());
                    PlayerAccount account = entry.getValue() == null ? new PlayerAccount() : entry.getValue();

                    ACCOUNTS.put(uuid, account);
                } catch (IllegalArgumentException ignored) {}
            }
        } catch (IOException ignored) {}
    }
}
