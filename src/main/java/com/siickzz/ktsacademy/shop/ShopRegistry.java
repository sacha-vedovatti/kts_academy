package com.siickzz.ktsacademy.shop;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class ShopRegistry
{
    private static final Logger LOGGER = LoggerFactory.getLogger("CobbleEconomy");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final Map<String, ShopItem> ITEMS = new LinkedHashMap<>();
    private static final Map<Identifier, ShopItem> ITEMS_BY_ID = new HashMap<>();
    private static final Map<String, ShopCategoryDef> CATEGORIES = new LinkedHashMap<>();
	private static final Set<String> QUANTITY_SELECTOR_CATEGORIES = new LinkedHashSet<>();
    private static volatile boolean INITIALIZED = false;
    private static Path configFile;

    public record ShopCategoryDef(String name, String displayName, String iconItemId) {}

    private ShopRegistry() {}

    public static void init()
    {
        if (INITIALIZED)
            return;
        synchronized (ShopRegistry.class) {
            if (INITIALIZED)
                return;

            Path configDir = FabricLoader.getInstance().getConfigDir().resolve("ktsacademy");
            configFile = configDir.resolve("shop.json");

            try {
                Files.createDirectories(configDir);
            } catch (IOException ignored) {}
            loadConfig(configFile);
            INITIALIZED = true;
        }
    }

    public static void reload()
    {
        init();
        synchronized (ShopRegistry.class) {
            loadConfig(configFile);
        }
    }

    public static void register(String category, String key, String itemId, String displayName, double buyPrice, double sellPrice, int amount, String giveCommand)
    {
        String cat = normalizeCategoryName(category);
        if (cat.isBlank())
            return;

        int safeAmount = Math.max(1, amount);
        ShopItem shopItem = new ShopItem(cat, Identifier.of(itemId), displayName, buyPrice, sellPrice, safeAmount, giveCommand);

        if (shopItem.item() == Items.AIR)
            LOGGER.warn("[KTS Academy] Unknown itemId '{}' for shop key '{}' (category '{}')", itemId, key, cat);
        ITEMS.put(key.toLowerCase(), shopItem);
        ITEMS_BY_ID.put(shopItem.itemId(), shopItem);
    }

    public static ShopItem get(String key)
    {
        init();
        return ITEMS.get(key.toLowerCase());
    }

    public static ShopItem getById(Identifier id)
    {
        init();
        return ITEMS_BY_ID.get(id);
    }

    public static Set<String> keys()
    {
        init();
        return Collections.unmodifiableSet(ITEMS.keySet());
    }

    public static List<ShopCategoryDef> categoriesSorted()
    {
        init();
        return new ArrayList<>(CATEGORIES.values());
    }

    public static ShopCategoryDef category(String name)
    {
        init();
        return CATEGORIES.get(normalizeCategoryName(name));
    }

    public static boolean useQuantitySelectorForCategory(String category)
    {
        init();

        String cat = normalizeCategoryName(category);
        if (!QUANTITY_SELECTOR_CATEGORIES.isEmpty())
            return QUANTITY_SELECTOR_CATEGORIES.contains(cat);
        return !isBoostersCategory(cat);
    }

    public static Map<String, ShopItem> all()
    {
        init();
        return Collections.unmodifiableMap(ITEMS);
    }

    public static java.util.List<ShopItem> allItemsSorted()
    {
        init();
        return new ArrayList<>(ITEMS.values());
    }

    public static java.util.List<ShopItem> itemsByCategory(String category)
    {
        init();

        String cat = normalizeCategoryName(category);
        return ITEMS.values().stream()
            .filter(i -> Objects.equals(i.category(), cat))
            .toList();
    }

    private static void loadConfig(Path path)
    {
        ITEMS.clear();
        ITEMS_BY_ID.clear();
        CATEGORIES.clear();
        QUANTITY_SELECTOR_CATEGORIES.clear();

        ShopConfig cfg = null;

        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            cfg = GSON.fromJson(reader, ShopConfig.class);
        } catch (IOException e) {
            LOGGER.warn("[KTS Academy] Could not read shop config at {}", path);
        }
        if (cfg != null && cfg.categories != null && !cfg.categories.isEmpty()) {
            for (ShopConfig.ShopConfigCategory cat : cfg.categories) {
                if (cat == null)
                    continue;
                String name = normalizeCategoryName(cat.name);
                if (name.isBlank())
                    continue;
                String displayName = (cat.displayName == null || cat.displayName.isBlank()) ? name : cat.displayName;
                CATEGORIES.put(name, new ShopCategoryDef(name, displayName, cat.iconItemId));
            }
        }

        List<ShopConfig.ShopConfigItem> entries = flatten(cfg);
        if (cfg == null || entries.isEmpty()) {
            LOGGER.warn("[KTS Academy] Shop config missing/empty; shop disabled");
            return;
        }

        boolean enableAllExceptBoosters = false;
        if (cfg != null && cfg.quantitySelector != null && cfg.quantitySelector.enabledCategories != null) {
            for (String raw : cfg.quantitySelector.enabledCategories) {
                if (raw == null)
                    continue;

                String trimmed = raw.trim();
                if (trimmed.isEmpty())
                    continue;

                String up = trimmed.toUpperCase(java.util.Locale.ROOT);
                if ("*".equals(trimmed) || "ALL".equals(up) || "ALL_EXCEPT_BOOSTERS".equals(up)) {
                    enableAllExceptBoosters = true;
                    continue;
                }

                String cat = normalizeCategoryName(trimmed);
                if (!cat.isBlank())
                    QUANTITY_SELECTOR_CATEGORIES.add(cat);
            }
        }
        for (ShopConfig.ShopConfigItem entry : entries) {
            if (entry == null)
                continue;
            if (entry.key == null || entry.key.isBlank())
                continue;
            if (entry.category == null || entry.category.isBlank())
                continue;
            if (entry.itemId == null || entry.itemId.isBlank())
                continue;

            String displayName = entry.displayName == null ? entry.key : entry.displayName;
            String category = normalizeCategoryName(entry.category);

            if (category.isBlank())
                continue;
            CATEGORIES.putIfAbsent(category, new ShopCategoryDef(category, category, null));

            int amount = 1;

            if (entry.amount != null)
                amount = entry.amount;
            try {
                register(category, entry.key, entry.itemId, displayName, entry.buyPrice, entry.sellPrice, amount, entry.giveCommand);
            } catch (Throwable ignored) {}
        }
        if (enableAllExceptBoosters || QUANTITY_SELECTOR_CATEGORIES.isEmpty()) {
            QUANTITY_SELECTOR_CATEGORIES.clear();
            for (String cat : CATEGORIES.keySet()) {
                if (cat == null || cat.isBlank())
                    continue;
                if (isBoostersCategory(cat))
                    continue;
                QUANTITY_SELECTOR_CATEGORIES.add(cat);
            }
        }
    }

    private static boolean isBoostersCategory(String normalizedCategory)
    {
        if (normalizedCategory == null)
            return false;

        String s = normalizedCategory.trim().toLowerCase(java.util.Locale.ROOT);

        if (s.isEmpty())
            return false;
        return  s.contains("booster") || s.contains("pack");
    }

    /**
     * Supports format:
     * - { "categories": [ { "name": "BALLS", "items": [ ... ] } ] }
     */
    private static List<ShopConfig.ShopConfigItem> flatten(ShopConfig cfg)
    {
        if (cfg == null)
            return java.util.List.of();
        if (cfg.categories == null || cfg.categories.isEmpty())
            return java.util.List.of();

        List<ShopConfig.ShopConfigItem> out = new ArrayList<>();

        for (ShopConfig.ShopConfigCategory cat : cfg.categories) {
            if (cat == null)
                continue;

            String catName = cat.name == null ? null : cat.name.trim();

            if (cat.items == null || cat.items.isEmpty())
                continue;
            for (ShopConfig.ShopConfigItem item : cat.items) {
                if (item == null)
                    continue;
                if (item.category == null || item.category.isBlank())
                    item.category = catName;
                out.add(item);
            }
        }
        return out;
    }

    private static String normalizeCategoryName(String raw)
    {
        if (raw == null)
            return "";
        return raw.trim().toUpperCase();
    }
}
