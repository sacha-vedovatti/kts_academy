package com.siickzz.ktsacademy.mystery;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentLevelEntry;
import net.minecraft.item.EnchantedBookItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MysteryChestManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("KTSAcademy-MysteryChests");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final java.util.random.RandomGenerator RNG = java.util.random.RandomGenerator.getDefault();
    private static final String NBT_CRATE_ID = "KTSAcademyCrateId";

    private static final Set<String> LOGGED_BAD_ITEM_IDS = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static final Set<String> LOGGED_BAD_COMPONENT_IDS = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static final Identifier ACADEMY_PACK_COMPONENT_ID = Identifier.tryParse("academy:booster_pack");
    private static final Identifier CUSTOM_DATA_COMPONENT_ID = Identifier.tryParse("minecraft:custom_data");
    private static final Pattern PACK_COMPONENT_PATTERN = Pattern.compile("^([^\\[]+)\\[(academy:pack|academy:booster_pack)=\\\"([^\\\"]+)\\\"\\]$");
    private static volatile boolean LOGGED_PACK_COMPONENT_MISSING = false;
    private static volatile boolean LOGGED_CUSTOM_DATA_MISSING = false;
    private static volatile Method SET_COMPONENT_METHOD;

    private static volatile boolean INITIALIZED = false;
    private static volatile MysteryChestConfig CONFIG;
    private static volatile Set<String> KEY_ITEM_IDS = Set.of();
    private static Path configFile;

    private static volatile RegistryWrapper.WrapperLookup REGISTRY_LOOKUP;

    private MysteryChestManager() {}

    public static void init()
    {
        if (INITIALIZED)
            return;
        synchronized (MysteryChestManager.class) {
            if (INITIALIZED)
                return;

            Path configDir = FabricLoader.getInstance().getConfigDir().resolve("ktsacademy");
            configFile = configDir.resolve("crate.json");
            try {
                Files.createDirectories(configDir);
            } catch (IOException ignored) {}
            if (!Files.exists(configFile))
                writeDefaultConfig(configFile);
            reload();
            INITIALIZED = true;
        }
    }

    public static void reload()
    {
        synchronized (MysteryChestManager.class) {
            loadConfig();
        }
    }

    public static MysteryChestConfig.ChestConfig matchChest(String crateId, BlockState state)
    {
        init();
        if (CONFIG == null || CONFIG.chests == null || CONFIG.chests.isEmpty() || crateId == null || crateId.isBlank() || state == null)
            return null;

        String blockId = blockId(state);
        for (MysteryChestConfig.ChestConfig chest : CONFIG.chests) {
            if (chest == null || !chest.enabled || chest.id == null)
                continue;
            if (!chest.id.equalsIgnoreCase(crateId.trim()))
                continue;
            if (!matchesBlock(chest.blockIds, blockId))
                continue;
            return chest;
        }
        return null;
    }

    public static boolean isValidKey(MysteryChestConfig.ChestConfig chest, ItemStack stack)
    {
        if (chest == null || stack == null || stack.isEmpty() || chest.keyItemId == null || chest.keyItemId.isBlank())
            return false;

        Identifier expected = Identifier.tryParse(chest.keyItemId.trim());
        if (expected == null)
            return false;

        Identifier actual = Registries.ITEM.getId(stack.getItem());
        if (!expected.equals(actual))
            return false;
        if (chest.requireEnchantedKey && !stack.hasGlint())
            return false;
        return true;
    }

    public static List<Reward> rollLoot(MysteryChestConfig.ChestConfig chest)
    {
        if (chest == null || chest.drops == null || chest.drops.isEmpty())
            return List.of();

        List<ResolvedDrop> entries = new ArrayList<>();
        for (MysteryChestConfig.DropEntryConfig entry : chest.drops) {
            if (entry == null || entry.itemId == null || entry.itemId.isBlank())
                continue;

            double chance = entry.chance != null ? Math.max(0.0, entry.chance) : 0.0;
            if (chance <= 0.0)
                continue;

            ItemStack baseStack = buildEntryStack(entry);
            if (baseStack == null) {
                String key = entry.itemId.trim().toLowerCase(Locale.ROOT);
                if (LOGGED_BAD_ITEM_IDS.add(key))
                    LOGGER.warn("[KTSAcademy-MysteryChests] item inconnu '{}'", entry.itemId);
                continue;
            }

            int min = entry.min != null ? Math.max(1, entry.min) : 1;
            int max = entry.max != null ? Math.max(min, entry.max) : min;
            entries.add(new ResolvedDrop(baseStack, chance, min, max, entry));
        }
        if (entries.isEmpty())
            return List.of();

        int rolls = chest.rolls != null ? Math.max(1, chest.rolls) : 1;
        List<Reward> result = new ArrayList<>();
        double totalWeight = entries.stream().mapToDouble(ResolvedDrop::weight).sum();
        if (totalWeight <= 0)
            return List.of();
        for (int r = 0; r < rolls; r++) {
            ResolvedDrop pick = pick(entries, totalWeight);
            if (pick == null)
                continue;

            int count = pick.min == pick.max ? pick.min : pick.min + RNG.nextInt(pick.max - pick.min + 1);
            if (count <= 0)
                continue;

            ItemStack stack = pick.baseStack.copy();
            stack.setCount(count);
            applyKeyGlintIfNeeded(stack);
            result.add(new Reward(stack, pick.entry));
        }
        return result;
    }

    public static String crateIdFromBlockEntity(BlockEntity blockEntity)
    {
        if (blockEntity == null)
            return null;

        var world = blockEntity.getWorld();
        if (world == null)
            return null;

        NbtCompound tag = blockEntity.createNbt(world.getRegistryManager());
        if (!tag.contains(NBT_CRATE_ID, NbtElement.STRING_TYPE))
            return null;

        String id = tag.getString(NBT_CRATE_ID);
        return id != null && !id.isBlank() ? id : null;
    }

    public static String crateIdFromStack(ItemStack stack)
    {
        if (stack == null || stack.isEmpty())
            return null;

        NbtComponent blockEntityData = stack.get(DataComponentTypes.BLOCK_ENTITY_DATA);
        if (blockEntityData == null)
            return null;

        NbtCompound tag = blockEntityData.copyNbt();
        if (!tag.contains(NBT_CRATE_ID, NbtElement.STRING_TYPE))
            return null;

        String id = tag.getString(NBT_CRATE_ID);
        return id != null && !id.isBlank() ? id : null;
    }

    public static ItemStack createChestItem(String chestId)
    {
        init();
        MysteryChestConfig.ChestConfig chest = findChestById(chestId);
        if (chest == null)
            return ItemStack.EMPTY;

        Item item = resolveContainerItem(chest.blockIds);
        ItemStack stack = new ItemStack(item != null ? item : Items.CHEST);
        if (chest.displayName != null && !chest.displayName.isBlank())
            stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(chest.displayName));

        NbtCompound blockEntityTag = new NbtCompound();
        String blockEntityId = resolveBlockEntityId(chest.blockIds, stack.getItem());
        if (blockEntityId != null)
            blockEntityTag.putString("id", blockEntityId);
        blockEntityTag.putString(NBT_CRATE_ID, chest.id);
        stack.set(DataComponentTypes.BLOCK_ENTITY_DATA, NbtComponent.of(blockEntityTag));
        return stack;
    }

    public static ItemStack createKeyItem(String chestId)
    {
        init();
        MysteryChestConfig.ChestConfig chest = findChestById(chestId);
        if (chest == null || chest.keyItemId == null || chest.keyItemId.isBlank())
            return ItemStack.EMPTY;

        Item keyItem = resolveItem(chest.keyItemId.trim());
        if (keyItem == null)
            return ItemStack.EMPTY;

        ItemStack stack = new ItemStack(keyItem);
        if (chest.requireEnchantedKey)
            stack.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        return stack;
    }

    public static List<Text> describeDrops(MysteryChestConfig.ChestConfig chest)
    {
        if (chest == null || chest.drops == null || chest.drops.isEmpty())
            return List.of(Text.literal("Aucun gain configure."));

        List<Text> lines = new ArrayList<>();
        lines.add(Text.literal("Gains possibles:"));
        for (MysteryChestConfig.DropEntryConfig entry : chest.drops) {
            if (entry == null || entry.itemId == null || entry.itemId.isBlank())
                continue;
            Item item = resolveItem(entry.itemId.trim());
            if (item == null)
                continue;
            int min = entry.min != null ? Math.max(1, entry.min) : 1;
            int max = entry.max != null ? Math.max(min, entry.max) : min;
            String count = min == max ? String.valueOf(min) : (min + "-" + max);
            double chance = entry.chance != null ? entry.chance : 0.0;
            lines.add(Text.literal("- " + item.getName().getString() + " x" + count + " (chance " + chance + ")"));
        }
        if (lines.size() == 1)
            lines.add(Text.literal("Aucun gain configure."));
        return lines;
    }

    public static List<ItemStack> previewItems(MysteryChestConfig.ChestConfig chest, int maxItems)
    {
        if (chest == null || chest.drops == null || chest.drops.isEmpty())
            return List.of();

        List<ItemStack> result = new ArrayList<>();
        for (MysteryChestConfig.DropEntryConfig entry : chest.drops) {
            if (entry == null || entry.itemId == null || entry.itemId.isBlank())
                continue;
            ItemStack baseStack = buildEntryStack(entry);
            if (baseStack == null)
                continue;
            int min = entry.min != null ? Math.max(1, entry.min) : 1;
            int max = entry.max != null ? Math.max(min, entry.max) : min;
            int count = Math.min(baseStack.getMaxCount(), min == max ? min : max);
            if (count <= 0)
                count = 1;
            ItemStack stack = baseStack.copy();
            stack.setCount(count);
            applyKeyGlintIfNeeded(stack);
            result.add(stack);
            if (result.size() >= maxItems)
                break;
        }
        return result;
    }

    public static void setRegistryLookup(RegistryWrapper.WrapperLookup lookup)
    {
        REGISTRY_LOOKUP = lookup;
    }

    private static void loadConfig()
    {
        if (configFile == null || !Files.exists(configFile)) {
            CONFIG = null;
            return;
        }
        try (BufferedReader reader = Files.newBufferedReader(configFile, StandardCharsets.UTF_8)) {
            CONFIG = GSON.fromJson(reader, MysteryChestConfig.class);
            KEY_ITEM_IDS = collectKeyItemIds(CONFIG);
        } catch (IOException e) {
            LOGGER.warn("[KTSAcademy-MysteryChests] Impossible de lire la config : {}", e.getMessage());
            CONFIG = null;
            KEY_ITEM_IDS = Set.of();
        }
    }

    private static void writeDefaultConfig(Path path)
    {
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            GSON.toJson(defaultConfig(), writer);
            LOGGER.info("[KTSAcademy-MysteryChests] Config par defaut ecrite dans {}", path);
        } catch (IOException e) {
            LOGGER.warn("[KTSAcademy-MysteryChests] Impossible d'ecrire la config par defaut : {}", e.getMessage());
        }
    }

    private static MysteryChestConfig defaultConfig()
    {
        MysteryChestConfig cfg = new MysteryChestConfig();
        MysteryChestConfig.ChestConfig trial = new MysteryChestConfig.ChestConfig();
        trial.id = "trial";
        trial.displayName = "Coffre mystere";
        trial.keyItemId = "minecraft:trial_key";
        trial.requireEnchantedKey = true;
        trial.rolls = 1;
        trial.dropOnGroundIfFull = true;
        trial.notifyPlayer = true;
        trial.blockIds = List.of("minecraft:chest");
        trial.drops = List.of(
            drop("minecraft:diamond", 10.0, 1, 2),
            drop("minecraft:emerald", 20.0, 2, 5),
            drop("minecraft:gold_ingot", 70.0, 4, 12)
        );

        MysteryChestConfig.ChestConfig ominous = new MysteryChestConfig.ChestConfig();
        ominous.id = "ominous";
        ominous.displayName = "Coffre mystere ominous";
        ominous.keyItemId = "minecraft:ominous_trial_key";
        ominous.requireEnchantedKey = true;
        ominous.rolls = 1;
        ominous.dropOnGroundIfFull = true;
        ominous.notifyPlayer = true;
        ominous.blockIds = List.of("minecraft:ender_chest");
        ominous.drops = List.of(
            drop("minecraft:netherite_ingot", 2.0, 1, 1),
            drop("minecraft:diamond", 25.0, 2, 4),
            drop("minecraft:gold_ingot", 73.0, 6, 16)
        );
        cfg.chests = List.of(trial, ominous);
        return cfg;
    }

    private static Set<String> collectKeyItemIds(MysteryChestConfig cfg)
    {
        if (cfg == null || cfg.chests == null)
            return Set.of();

        Set<String> ids = new HashSet<>();
        for (MysteryChestConfig.ChestConfig chest : cfg.chests) {
            if (chest == null || chest.keyItemId == null || chest.keyItemId.isBlank())
                continue;
            ids.add(chest.keyItemId.trim().toLowerCase(Locale.ROOT));
        }
        return ids.isEmpty() ? Set.of() : ids;
    }

    private static MysteryChestConfig.DropEntryConfig drop(String itemId, double chance, int min, int max)
    {
        MysteryChestConfig.DropEntryConfig entry = new MysteryChestConfig.DropEntryConfig();

        entry.itemId = itemId;
        entry.chance = chance;
        entry.min = min;
        entry.max = max;
        return entry;
    }

    private static MysteryChestConfig.ChestConfig findChestById(String chestId)
    {
        if (CONFIG == null || CONFIG.chests == null || chestId == null || chestId.isBlank())
            return null;
        for (MysteryChestConfig.ChestConfig chest : CONFIG.chests) {
            if (chest == null || chest.id == null)
                continue;
            if (chest.id.equalsIgnoreCase(chestId.trim()))
                return chest;
        }
        return null;
    }

    private static Item resolveContainerItem(List<String> blockIds)
    {
        if (blockIds == null || blockIds.isEmpty())
            return Items.CHEST;
        for (String idStr : blockIds) {
            if (idStr == null || idStr.isBlank())
                continue;

            Identifier id = Identifier.tryParse(idStr.trim());
            if (id == null)
                continue;

            Item item = Registries.ITEM.get(id);
            if (item != Items.AIR)
                return item;
        }
        return Items.CHEST;
    }

    private static String resolveBlockEntityId(List<String> blockIds, Item fallbackItem)
    {
        if (blockIds != null) {
            for (String idStr : blockIds) {
                if (idStr == null || idStr.isBlank())
                    continue;

                Identifier id = Identifier.tryParse(idStr.trim());
                if (id == null)
                    continue;
                if (Registries.BLOCK_ENTITY_TYPE.containsId(id))
                    return id.toString();
            }
        }
        if (fallbackItem == Items.ENDER_CHEST)
            return "minecraft:ender_chest";
        if (fallbackItem == Items.CHEST)
            return "minecraft:chest";
        return null;
    }

    private static Item resolveItem(String itemId)
    {
        Identifier id = Identifier.tryParse(itemId);
        if (id == null)
            return null;

        Item item = Registries.ITEM.get(id);
        return item != Items.AIR ? item : null;
    }

    private static ResolvedDrop pick(List<ResolvedDrop> entries, double totalWeight)
    {
        if (entries.isEmpty() || totalWeight <= 0)
            return null;

        double roll = RNG.nextDouble() * totalWeight;
        double acc = 0.0;
        for (ResolvedDrop entry : entries) {
            acc += entry.weight;
            if (roll <= acc)
                return entry;
        }
        return entries.get(entries.size() - 1);
    }

    private static String blockId(BlockState state)
    {
        Identifier id = Registries.BLOCK.getId(state.getBlock());

        return id != null ? id.toString() : "";
    }

    private static boolean matchesBlock(List<String> blockIds, String blockId)
    {
        if (blockIds == null || blockIds.isEmpty())
            return true;
        if (blockId == null || blockId.isBlank())
            return false;

        String target = blockId.toLowerCase(Locale.ROOT);
        for (String id : blockIds) {
            if (id == null || id.isBlank())
                continue;
            if (target.equals(id.trim().toLowerCase(Locale.ROOT)))
                return true;
        }
        return false;
    }

    private static void applyKeyGlintIfNeeded(ItemStack stack)
    {
        if (stack == null || stack.isEmpty())
            return;
        if (KEY_ITEM_IDS == null || KEY_ITEM_IDS.isEmpty())
            return;

        Identifier actual = Registries.ITEM.getId(stack.getItem());
        if (actual == null)
            return;
        String key = actual.toString().toLowerCase(Locale.ROOT);
        if (!KEY_ITEM_IDS.contains(key))
            return;

        stack.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
    }

    private static ItemStack buildEntryStack(MysteryChestConfig.DropEntryConfig entry)
    {
        if (entry == null || entry.itemId == null || entry.itemId.isBlank())
            return null;

        String itemIdRaw = entry.itemId;
        String itemId = itemIdRaw;
        String pack = null;
        String componentId = null;
        Matcher matcher = PACK_COMPONENT_PATTERN.matcher(itemIdRaw);
        if (matcher.matches()) {
            itemId = matcher.group(1);
            componentId = matcher.group(2);
            pack = matcher.group(3);
        }

        Item item = resolveItem(itemId.trim());
        if (item == null)
            return null;

        ItemStack stack = new ItemStack(item);
        boolean skipComponents = entry.giveCommand != null && !entry.giveCommand.isBlank();
        if (!skipComponents && "academy:booster_pack".equalsIgnoreCase(itemId.trim())) {
            if (pack == null || pack.isBlank())
                pack = "base";
            String targetComponent = componentId != null ? componentId : "academy:booster_pack";
            applyAcademyPackComponent(stack, targetComponent, pack);
        }
        if (!skipComponents)
            applyConfigComponents(stack, entry.components);
        return stack;
    }

    private static void applyConfigComponents(ItemStack stack, JsonObject components)
    {
        if (stack == null || stack.isEmpty() || components == null || components.isEmpty())
            return;

        for (var entry : components.entrySet()) {
            String componentIdRaw = entry.getKey();
            if (componentIdRaw == null || componentIdRaw.isBlank())
                continue;
            Identifier componentId = Identifier.tryParse(componentIdRaw.trim());
            if (componentId == null)
                continue;

            JsonElement json = entry.getValue();
            if (tryApplyStoredEnchantments(stack, componentId, json))
                continue;
            if (tryApplyBoosterPackComponent(stack, componentId, json))
                continue;

            Object componentType = resolveComponentType(componentId);
            if (componentType == null) {
                String key = componentId.toString().toLowerCase(Locale.ROOT);
                if (LOGGED_BAD_COMPONENT_IDS.add(key))
                    LOGGER.warn("[KTSAcademy-MysteryChests] data component {} introuvable", componentId);
                continue;
            }

            Object decoded = decodeComponentValue(componentType, json);
            boolean applied = decoded != null && setComponentValue(stack, componentId, decoded);
            if (!applied && json != null && json.isJsonPrimitive() && json.getAsJsonPrimitive().isString())
                applied = setComponentIfPresent(stack, componentId, json.getAsString());
            if (!applied) {
                String key = componentId.toString().toLowerCase(Locale.ROOT);
                if (LOGGED_BAD_COMPONENT_IDS.add(key))
                    LOGGER.warn("[KTSAcademy-MysteryChests] impossible d'appliquer le component {}", componentId);
            }
        }
    }

    private static boolean tryApplyStoredEnchantments(ItemStack stack, Identifier componentId, JsonElement json)
    {
        if (stack == null || componentId == null || json == null)
            return false;
        if (!"minecraft:stored_enchantments".equals(componentId.toString()))
            return false;
        if (stack.getItem() != Items.ENCHANTED_BOOK)
            return false;
        if (!json.isJsonObject())
            return false;

        JsonObject root = json.getAsJsonObject();
        JsonObject levels = null;
        JsonElement levelsElement = root.get("levels");
        if (levelsElement != null && levelsElement.isJsonObject())
            levels = levelsElement.getAsJsonObject();
        else
            levels = root;

        boolean applied = false;
        for (var levelEntry : levels.entrySet()) {
            Identifier enchantmentId = Identifier.tryParse(levelEntry.getKey());
            if (enchantmentId == null)
                continue;
            if (!levelEntry.getValue().isJsonPrimitive())
                continue;
            int level = levelEntry.getValue().getAsInt();
            if (level <= 0)
                continue;
            RegistryEntry<Enchantment> enchantmentEntry = resolveEnchantmentEntry(enchantmentId);
            if (enchantmentEntry == null)
                continue;
            if (addStoredEnchantment(stack, enchantmentEntry, level))
                applied = true;
        }
        return applied;
    }

    private static boolean tryApplyBoosterPackComponent(ItemStack stack, Identifier componentId, JsonElement json)
    {
        if (stack == null || componentId == null || json == null)
            return false;
        String id = componentId.toString();
        if (!"academy:booster_pack".equals(id) && !"academy:pack".equals(id))
            return false;
        if (!json.isJsonPrimitive() || !json.getAsJsonPrimitive().isString())
            return false;
        applyAcademyPackComponent(stack, id, json.getAsString());
        return true;
    }

    private static boolean addStoredEnchantment(ItemStack stack, RegistryEntry<Enchantment> enchantmentEntry, int level)
    {
        for (Method method : EnchantedBookItem.class.getMethods()) {
            if (!method.getName().equals("addEnchantment"))
                continue;
            Class<?>[] params = method.getParameterTypes();
            if (params.length == 2
                && params[0] == ItemStack.class
                && EnchantmentLevelEntry.class.isAssignableFrom(params[1])) {
                try {
                    method.invoke(null, stack, new EnchantmentLevelEntry(enchantmentEntry, level));
                    return true;
                } catch (Throwable ignored) {
                    continue;
                }
            }
            if (params.length == 3
                && params[0] == ItemStack.class
                && RegistryEntry.class.isAssignableFrom(params[1])
                && params[2] == int.class) {
                try {
                    method.invoke(null, stack, enchantmentEntry, level);
                    return true;
                } catch (Throwable ignored) {
                    continue;
                }
            }
        }
        return false;
    }

    private static RegistryEntry<Enchantment> resolveEnchantmentEntry(Identifier enchantmentId)
    {
        if (enchantmentId == null)
            return null;
        RegistryWrapper.WrapperLookup lookup = REGISTRY_LOOKUP;
        if (lookup == null)
            return null;
        try {
            RegistryWrapper<?> wrapper = lookup.getWrapperOrThrow(RegistryKeys.ENCHANTMENT);
            RegistryKey<?> key = RegistryKey.of(RegistryKeys.ENCHANTMENT, enchantmentId);
            Method getOptional = wrapper.getClass().getMethod("getOptional", RegistryKey.class);
            Object opt = getOptional.invoke(wrapper, key);
            if (!(opt instanceof java.util.Optional<?> entry) || entry.isEmpty())
                return null;
            Object entryValue = entry.get();
            @SuppressWarnings("unchecked")
            RegistryEntry<Enchantment> result = (RegistryEntry<Enchantment>) entryValue;
            return result;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void applyAcademyPackComponent(ItemStack stack, String componentId, String packId)
    {
        if (stack == null || stack.isEmpty() || packId == null || packId.isBlank())
            return;
        String componentIdValue = componentId != null && !componentId.isBlank() ? componentId : "academy:booster_pack";
        Identifier componentIdentifier = Identifier.tryParse(componentIdValue);
        if (componentIdentifier == null)
            return;

        boolean appliedPrimary = setComponentIfPresent(stack, componentIdentifier, packId);
        Identifier altIdentifier = componentIdentifier.equals(Identifier.tryParse("academy:booster_pack"))
            ? Identifier.tryParse("academy:pack")
            : Identifier.tryParse("academy:booster_pack");
        if (altIdentifier != null)
            setComponentIfPresent(stack, altIdentifier, packId);

        applyCustomDataPack(stack, componentIdValue, packId);
        if (!appliedPrimary) {
            if (!LOGGED_PACK_COMPONENT_MISSING) {
                LOGGED_PACK_COMPONENT_MISSING = true;
                LOGGER.warn("[KTSAcademy-MysteryChests] data component {} introuvable; booster_pack sans pack", componentIdValue);
            }
        }
    }

    private static boolean setComponentIfPresent(ItemStack stack, Identifier componentIdentifier, String value)
    {
        if (componentIdentifier == null)
            return false;
        Object component = resolveComponentType(componentIdentifier);
        if (component == null)
            return false;

        Method method = SET_COMPONENT_METHOD;
        if (method == null) {
            method = findSetComponentMethod();
            SET_COMPONENT_METHOD = method;
        }
        if (method == null)
            return false;

        if (trySetComponent(stack, method, component, value))
            return true;
        Identifier asIdentifier = Identifier.tryParse(value);
        if (asIdentifier != null && trySetComponent(stack, method, component, asIdentifier))
            return true;
        Object decoded = decodeComponentValue(component, value);
        return decoded != null && trySetComponent(stack, method, component, decoded);
    }

    private static boolean trySetComponent(ItemStack stack, Method method, Object component, Object value)
    {
        try {
            method.invoke(stack, component, value);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean setComponentValue(ItemStack stack, Identifier componentIdentifier, Object value)
    {
        if (componentIdentifier == null)
            return false;
        Object component = resolveComponentType(componentIdentifier);
        if (component == null)
            return false;

        Method method = SET_COMPONENT_METHOD;
        if (method == null) {
            method = findSetComponentMethod();
            SET_COMPONENT_METHOD = method;
        }
        if (method == null)
            return false;

        return trySetComponent(stack, method, component, value);
    }

    private static Object decodeComponentValue(Object componentType, JsonElement json)
    {
        if (componentType == null || json == null)
            return null;

        Method codecMethod = findCodecMethod(componentType);
        if (codecMethod == null)
            return null;

        try {
            Object codecObject = codecMethod.invoke(componentType);
            if (!(codecObject instanceof Codec<?> codec))
                return null;
            DataResult<?> result = codec.parse(JsonOps.INSTANCE, json);
            return result.result().orElse(null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object decodeComponentValue(Object componentType, String rawValue)
    {
        if (componentType == null || rawValue == null)
            return null;

        Method codecMethod = findCodecMethod(componentType);
        if (codecMethod == null)
            return null;

        try {
            Object codecObject = codecMethod.invoke(componentType);
            if (!(codecObject instanceof Codec<?> codec))
                return null;
            DataResult<?> result = codec.parse(NbtOps.INSTANCE, NbtString.of(rawValue));
            return result.result().orElse(null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Method findCodecMethod(Object componentType)
    {
        for (Method method : componentType.getClass().getMethods()) {
            if (method.getParameterCount() != 0)
                continue;
            if (!method.getName().equals("codec") && !method.getName().equals("getCodec"))
                continue;
            if (Codec.class.isAssignableFrom(method.getReturnType()))
                return method;
        }
        return null;
    }

    private static Object resolveComponentType(Identifier componentIdentifier)
    {
        if (componentIdentifier == null)
            return null;
        Object component = Registries.DATA_COMPONENT_TYPE.get(componentIdentifier);
        if (component != null)
            return component;
        RegistryWrapper.WrapperLookup lookup = REGISTRY_LOOKUP;
        if (lookup == null)
            return null;
        try {
            RegistryWrapper<?> wrapper = lookup.getWrapperOrThrow(RegistryKeys.DATA_COMPONENT_TYPE);
            RegistryKey<?> key = RegistryKey.of(RegistryKeys.DATA_COMPONENT_TYPE, componentIdentifier);
            Method getOptional = wrapper.getClass().getMethod("getOptional", RegistryKey.class);
            Object opt = getOptional.invoke(wrapper, key);
            if (!(opt instanceof java.util.Optional<?> entry) || entry.isEmpty())
                return null;
            Object entryValue = entry.get();
            Method valueMethod = entryValue.getClass().getMethod("value");
            return valueMethod.invoke(entryValue);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean applyCustomDataPack(ItemStack stack, String componentIdValue, String packId)
    {
        if (CUSTOM_DATA_COMPONENT_ID == null)
            return false;
        Object customDataComponent = resolveComponentType(CUSTOM_DATA_COMPONENT_ID);
        if (customDataComponent == null) {
            if (!LOGGED_CUSTOM_DATA_MISSING) {
                LOGGED_CUSTOM_DATA_MISSING = true;
                LOGGER.warn("[KTSAcademy-MysteryChests] data component minecraft:custom_data introuvable; booster_pack sans pack");
            }
            return false;
        }

        NbtCompound customTag = new NbtCompound();
        customTag.putString("academy:pack", packId);
        customTag.putString("academy:booster_pack", packId);
        customTag.putString(componentIdValue, packId);

        Method method = SET_COMPONENT_METHOD;
        if (method == null) {
            method = findSetComponentMethod();
            SET_COMPONENT_METHOD = method;
        }
        if (method == null)
            return false;
        try {
            method.invoke(stack, customDataComponent, NbtComponent.of(customTag));
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Method findSetComponentMethod()
    {
        for (Method method : ItemStack.class.getMethods()) {
            if (!method.getName().equals("set"))
                continue;
            if (method.getParameterCount() != 2)
                continue;
            Class<?> first = method.getParameterTypes()[0];
            if (first != null && first.getName().endsWith("DataComponentType"))
                return method;
        }
        return null;
    }

    private record ResolvedDrop(ItemStack baseStack, double weight, int min, int max, MysteryChestConfig.DropEntryConfig entry) {}

    public record Reward(ItemStack stack, MysteryChestConfig.DropEntryConfig entry) {}
}
