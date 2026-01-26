package com.siickzz.economy.shop;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
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

public final class ShopRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger("CobbleEconomy");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final Map<String, ShopItem> ITEMS = new LinkedHashMap<>();
    private static final Map<Identifier, ShopItem> ITEMS_BY_ID = new HashMap<>();
    private static final Map<String, ShopCategoryDef> CATEGORIES = new LinkedHashMap<>();
	private static final Set<String> QUANTITY_SELECTOR_CATEGORIES = new LinkedHashSet<>();
    private static volatile boolean INITIALIZED = false;
    private static Path configFile;

    public record ShopCategoryDef(String name, String displayName, String iconItemId) {
    }

    private ShopRegistry() {
    }

    public static void init() {
        if (INITIALIZED)
            return;
        synchronized (ShopRegistry.class) {
            if (INITIALIZED) {
                return;
            }

            Path configDir = FabricLoader.getInstance().getConfigDir().resolve("economy");
            configFile = configDir.resolve("shop.json");
            try {
                Files.createDirectories(configDir);
            } catch (IOException ignored) {
            }

            if (!Files.exists(configFile)) {
                writeDefaultConfig(configFile);
            }
            loadConfig(configFile);
            INITIALIZED = true;
        }
    }

    public static void reload() {
        init();
        synchronized (ShopRegistry.class) {
            loadConfig(configFile);
        }
    }

    public static void register(String category, String key, String itemId, String displayName, double buyPrice, double sellPrice, int amount, String giveCommand) {
        String cat = normalizeCategoryName(category);
        if (cat.isBlank()) {
            return;
        }

        int safeAmount = Math.max(1, amount);
        ShopItem shopItem = new ShopItem(cat, Identifier.of(itemId), displayName, buyPrice, sellPrice, safeAmount, giveCommand);

        // If the registry doesn't know this item, it resolves to AIR.
        if (shopItem.item() == Items.AIR) {
            LOGGER.warn("[CobbleEconomy] Unknown itemId '{}' for shop key '{}' (category '{}')", itemId, key, cat);
        }

        ITEMS.put(key.toLowerCase(), shopItem);
        ITEMS_BY_ID.put(shopItem.itemId(), shopItem);
    }

    public static ShopItem get(String key) {
        init();
        return ITEMS.get(key.toLowerCase());
    }

    public static ShopItem getById(Identifier id) {
        init();
        return ITEMS_BY_ID.get(id);
    }

    public static Set<String> keys() {
        init();
        return Collections.unmodifiableSet(ITEMS.keySet());
    }

    public static List<ShopCategoryDef> categoriesSorted() {
        init();
        return new ArrayList<>(CATEGORIES.values());
    }

    public static ShopCategoryDef category(String name) {
        init();
        return CATEGORIES.get(normalizeCategoryName(name));
    }

    public static boolean useQuantitySelectorForCategory(String category) {
        init();
        String cat = normalizeCategoryName(category);
        // If an explicit list was configured, use it.
        if (!QUANTITY_SELECTOR_CATEGORIES.isEmpty()) {
            return QUANTITY_SELECTOR_CATEGORIES.contains(cat);
        }
        // Default behavior: enable for all categories except boosters.
        return !isBoostersCategory(cat);
    }

    public static Map<String, ShopItem> all() {
        init();
        return Collections.unmodifiableMap(ITEMS);
    }

    public static java.util.List<ShopItem> allItemsSorted() {
        init();
        return new ArrayList<>(ITEMS.values());
    }

    public static java.util.List<ShopItem> itemsByCategory(String category) {
        init();
        String cat = normalizeCategoryName(category);
        return ITEMS.values().stream()
            .filter(i -> Objects.equals(i.category(), cat))
            .toList();
    }

    private static void loadConfig(Path path) {
        ITEMS.clear();
        ITEMS_BY_ID.clear();
        CATEGORIES.clear();
        QUANTITY_SELECTOR_CATEGORIES.clear();

        ShopConfig cfg = null;
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            cfg = GSON.fromJson(reader, ShopConfig.class);
        } catch (IOException e) {
            LOGGER.warn("[CobbleEconomy] Could not read shop config at {}", path);
        }

        // Build category list
        if (cfg != null && cfg.categories != null && !cfg.categories.isEmpty()) {
            for (ShopConfig.ShopConfigCategory cat : cfg.categories) {
                if (cat == null) continue;
                String name = normalizeCategoryName(cat.name);
                if (name.isBlank()) continue;
                String displayName = (cat.displayName == null || cat.displayName.isBlank()) ? name : cat.displayName;
                CATEGORIES.put(name, new ShopCategoryDef(name, displayName, cat.iconItemId));
            }
        } else if (cfg != null && cfg.items != null && !cfg.items.isEmpty()) {
            // Legacy file: infer categories from items
            for (ShopConfig.ShopConfigItem item : cfg.items) {
                if (item == null) continue;
                String name = normalizeCategoryName(item.category);
                if (name.isBlank()) continue;
                CATEGORIES.putIfAbsent(name, new ShopCategoryDef(name, name, null));
            }
        }

        List<ShopConfig.ShopConfigItem> entries = flatten(cfg);

        if (cfg == null || entries.isEmpty()) {
            LOGGER.warn("[CobbleEconomy] Shop config empty/invalid; using defaults");
            cfg = defaultConfig();
            CATEGORIES.clear();
            if (cfg.categories != null) {
                for (ShopConfig.ShopConfigCategory cat : cfg.categories) {
                    if (cat == null) continue;
                    String name = normalizeCategoryName(cat.name);
                    if (name.isBlank()) continue;
                    String displayName = (cat.displayName == null || cat.displayName.isBlank()) ? name : cat.displayName;
                    CATEGORIES.put(name, new ShopCategoryDef(name, displayName, cat.iconItemId));
                }
            }
            entries = flatten(cfg);
        }

        boolean enableAllExceptBoosters = false;
        if (cfg != null && cfg.quantitySelector != null && cfg.quantitySelector.enabledCategories != null) {
            for (String raw : cfg.quantitySelector.enabledCategories) {
                if (raw == null) continue;
                String trimmed = raw.trim();
                if (trimmed.isEmpty()) continue;
                String up = trimmed.toUpperCase(java.util.Locale.ROOT);
                if ("*".equals(trimmed) || "ALL".equals(up) || "ALL_EXCEPT_BOOSTERS".equals(up)) {
                    enableAllExceptBoosters = true;
                    continue;
                }

                String cat = normalizeCategoryName(trimmed);
                if (!cat.isBlank()) {
                    QUANTITY_SELECTOR_CATEGORIES.add(cat);
                }
            }
        }

        for (ShopConfig.ShopConfigItem entry : entries) {
            if (entry == null) continue;
            if (entry.key == null || entry.key.isBlank()) continue;
            if (entry.category == null || entry.category.isBlank()) continue;
            if (entry.itemId == null || entry.itemId.isBlank()) continue;
            String displayName = entry.displayName == null ? entry.key : entry.displayName;

            String category = normalizeCategoryName(entry.category);
            if (category.isBlank()) continue;
            CATEGORIES.putIfAbsent(category, new ShopCategoryDef(category, category, null));

            int amount = 1;
            if (entry.amount != null) {
                amount = entry.amount;
            }
            try {
                register(category, entry.key, entry.itemId, displayName, entry.buyPrice, entry.sellPrice, amount, entry.giveCommand);
            } catch (Throwable ignored) {
                // ignore invalid IDs
            }
        }

        // If configured as wildcard (or not configured at all), enable for all categories except boosters.
        if (enableAllExceptBoosters || QUANTITY_SELECTOR_CATEGORIES.isEmpty()) {
            QUANTITY_SELECTOR_CATEGORIES.clear();
            for (String cat : CATEGORIES.keySet()) {
                if (cat == null || cat.isBlank()) continue;
                if (isBoostersCategory(cat)) continue;
                QUANTITY_SELECTOR_CATEGORIES.add(cat);
            }
        }
    }

    private static boolean isBoostersCategory(String normalizedCategory) {
        if (normalizedCategory == null) return false;
        String s = normalizedCategory.trim().toLowerCase(java.util.Locale.ROOT);
        if (s.isEmpty()) return false;
        // Common names: "boosters", "booster", "boost"...
        return s.equals("boosters") || s.equals("booster") || s.contains("booster") || s.contains("boost");
    }

    /**
     * Supports both:
     * - Legacy: { "items": [ { "category": "BALLS", ... } ] }
     * - New:    { "categories": [ { "name": "BALLS", "items": [ ... ] } ] }
     */
    private static List<ShopConfig.ShopConfigItem> flatten(ShopConfig cfg) {
        if (cfg == null)
            return java.util.List.of();
        if (cfg.categories != null && !cfg.categories.isEmpty()) {
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

        if (cfg.items == null || cfg.items.isEmpty()) {
            return java.util.List.of();
        }
        return cfg.items;
    }

    private static String normalizeCategoryName(String raw) {
        if (raw == null) return "";
        return raw.trim().toUpperCase();
    }

    private static void writeDefaultConfig(Path path) {
        ShopConfig defaults = defaultConfig();
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            GSON.toJson(defaults, writer);
            LOGGER.info("[CobbleEconomy] Wrote default shop config to {}", path);
        } catch (IOException e) {
            LOGGER.warn("[CobbleEconomy] Could not write default shop config to {}", path);
        }
    }

    private static ShopConfig defaultConfig() {
        ShopConfig cfg = new ShopConfig();
        if (cfg.quantitySelector != null && cfg.quantitySelector.enabledCategories != null) {
            cfg.quantitySelector.enabledCategories.add("BALLS");
            cfg.quantitySelector.enabledCategories.add("HEAL");
        }

        // // Optional category metadata (icons) for default config
        // getOrCreateCategory(cfg, "BALLS").iconItemId = "cobblemon:poke_ball";
        // getOrCreateCategory(cfg, "BAGS").iconItemId = "cobblemon:small_pokebag";
        // getOrCreateCategory(cfg, "STONES").iconItemId = "cobblemon:fire_stone";

        // // Balls: prices are per-pack of 10
        // add(cfg, "pokeball", "BALLS", "cobblemon:poke_ball", "Poké Ball", 1000, 500, 10);
        // add(cfg, "greatball", "BALLS", "cobblemon:great_ball", "Great Ball", 2500, 1250, 10);
        // add(cfg, "ultraball", "BALLS", "cobblemon:ultra_ball", "Ultra Ball", 7500, 3750, 10);
        // add(cfg, "premierball", "BALLS", "cobblemon:premier_ball", "Premier Ball", 1500, 750, 10);
        // add(cfg, "luxuryball", "BALLS", "cobblemon:luxury_ball", "Luxury Ball", 8000, 4000, 10);
        // add(cfg, "healball", "BALLS", "cobblemon:heal_ball", "Heal Ball", 3000, 1500, 10);
        // add(cfg, "quickball", "BALLS", "cobblemon:quick_ball", "Quick Ball", 4000, 2000, 10);
        // add(cfg, "duskball", "BALLS", "cobblemon:dusk_ball", "Dusk Ball", 4500, 2250, 10);
        // add(cfg, "timerball", "BALLS", "cobblemon:timer_ball", "Timer Ball", 4500, 2250, 10);
        // add(cfg, "repeatball", "BALLS", "cobblemon:repeat_ball", "Repeat Ball", 4500, 2250, 10);
        // add(cfg, "netball", "BALLS", "cobblemon:net_ball", "Net Ball", 4500, 2250, 10);
        // add(cfg, "diveball", "BALLS", "cobblemon:dive_ball", "Dive Ball", 4500, 2250, 10);
        // add(cfg, "nestball", "BALLS", "cobblemon:nest_ball", "Nest Ball", 4500, 2250, 10);
        // add(cfg, "levelball", "BALLS", "cobblemon:level_ball", "Level Ball", 6000, 3000, 10);
        // add(cfg, "lureball", "BALLS", "cobblemon:lure_ball", "Lure Ball", 6000, 3000, 10);
        // add(cfg, "moonball", "BALLS", "cobblemon:moon_ball", "Moon Ball", 6000, 3000, 10);
        // add(cfg, "friendball", "BALLS", "cobblemon:friend_ball", "Friend Ball", 6000, 3000, 10);
        // add(cfg, "loveball", "BALLS", "cobblemon:love_ball", "Love Ball", 6000, 3000, 10);
        // add(cfg, "fastball", "BALLS", "cobblemon:fast_ball", "Fast Ball", 6000, 3000, 10);
        // add(cfg, "heavyball", "BALLS", "cobblemon:heavy_ball", "Heavy Ball", 6000, 3000, 10);

        // // Pokébags
        // add(cfg, "pokebag_small", "BAGS", "cobblemon:small_pokebag", "Small Pokébag", 2000, 1000, 1);
        // add(cfg, "pokebag_medium", "BAGS", "cobblemon:medium_pokebag", "Medium Pokébag", 5000, 2500, 1);
        // add(cfg, "pokebag_large", "BAGS", "cobblemon:large_pokebag", "Large Pokébag", 12000, 6000, 1);
        // add(cfg, "pokebag_huge", "BAGS", "cobblemon:huge_pokebag", "Huge Pokébag", 25000, 12500, 1);
        // // Common alternate IDs
        // add(cfg, "pokebag_small_alt", "BAGS", "cobblemon:small_poke_bag", "Small Pokébag", 2000, 1000, 1);
        // add(cfg, "pokebag_medium_alt", "BAGS", "cobblemon:medium_poke_bag", "Medium Pokébag", 5000, 2500, 1);
        // add(cfg, "pokebag_large_alt", "BAGS", "cobblemon:large_poke_bag", "Large Pokébag", 12000, 6000, 1);
        // add(cfg, "pokebag_huge_alt", "BAGS", "cobblemon:huge_poke_bag", "Huge Pokébag", 25000, 12500, 1);
        // add(cfg, "pokebag_small_alt2", "BAGS", "cobblemon:poke_bag_small", "Small Pokébag", 2000, 1000, 1);
        // add(cfg, "pokebag_medium_alt2", "BAGS", "cobblemon:poke_bag_medium", "Medium Pokébag", 5000, 2500, 1);
        // add(cfg, "pokebag_large_alt2", "BAGS", "cobblemon:poke_bag_large", "Large Pokébag", 12000, 6000, 1);
        // add(cfg, "pokebag_huge_alt2", "BAGS", "cobblemon:poke_bag_huge", "Huge Pokébag", 25000, 12500, 1);

        // // Evolution stones
        // add(cfg, "fire_stone", "STONES", "cobblemon:fire_stone", "Fire Stone", 3000, 1500, 1);
        // add(cfg, "water_stone", "STONES", "cobblemon:water_stone", "Water Stone", 3000, 1500, 1);
        // add(cfg, "thunder_stone", "STONES", "cobblemon:thunder_stone", "Thunder Stone", 3000, 1500, 1);
        // add(cfg, "leaf_stone", "STONES", "cobblemon:leaf_stone", "Leaf Stone", 3000, 1500, 1);
        // add(cfg, "moon_stone", "STONES", "cobblemon:moon_stone", "Moon Stone", 3500, 1750, 1);
        // add(cfg, "sun_stone", "STONES", "cobblemon:sun_stone", "Sun Stone", 3500, 1750, 1);
        // add(cfg, "dawn_stone", "STONES", "cobblemon:dawn_stone", "Dawn Stone", 4000, 2000, 1);
        // add(cfg, "dusk_stone", "STONES", "cobblemon:dusk_stone", "Dusk Stone", 4000, 2000, 1);
        // add(cfg, "ice_stone", "STONES", "cobblemon:ice_stone", "Ice Stone", 4000, 2000, 1);
        // add(cfg, "shiny_stone", "STONES", "cobblemon:shiny_stone", "Shiny Stone", 4000, 2000, 1);
        return cfg;
    }

    private static void add(ShopConfig cfg, String key, String category, String itemId, String displayName, double buyPrice, double sellPrice, int amount) {
        ShopConfig.ShopConfigCategory cat = getOrCreateCategory(cfg, category);
        ShopConfig.ShopConfigItem item = new ShopConfig.ShopConfigItem();
        item.key = key;
        // category can be omitted when nested, but keeping it set makes the file resilient if moved back to legacy.
        item.category = category;
        item.itemId = itemId;
        item.displayName = displayName;
        item.amount = amount;
        item.giveCommand = null;
        item.buyPrice = buyPrice;
        item.sellPrice = sellPrice;
        cat.items.add(item);
    }

    private static ShopConfig.ShopConfigCategory getOrCreateCategory(ShopConfig cfg, String name) {
        if (cfg.categories == null) {
            cfg.categories = new ArrayList<>();
        }
        for (ShopConfig.ShopConfigCategory existing : cfg.categories) {
            if (existing != null && existing.name != null && existing.name.equalsIgnoreCase(name)) {
                if (existing.items == null) {
                    existing.items = new ArrayList<>();
                }
                return existing;
            }
        }

        ShopConfig.ShopConfigCategory created = new ShopConfig.ShopConfigCategory();
        created.name = name;
        created.items = new ArrayList<>();
        cfg.categories.add(created);
        return created;
    }
}
