package com.siickzz.economy.quests;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.siickzz.economy.economy.EconomyManager;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class QuestManager {
	private static final Logger LOGGER = LoggerFactory.getLogger("CobbleEconomy");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Type STORAGE_TYPE = new TypeToken<Map<String, QuestProfile>>() {}.getType();

	private static final Map<String, QuestDef> QUESTS = new LinkedHashMap<>();
	private static final Map<String, QuestCategory> CATEGORIES = new LinkedHashMap<>();
	private static final Map<UUID, QuestProfile> PROFILES = new ConcurrentHashMap<>();

	private static volatile boolean INITIALIZED = false;
	private static Path questsFile;
	private static Path progressFile;
	private static Path legendaryItemsFile;
	private static final Map<String, String> LEGENDARY_ICON_BY_SPECIES = new HashMap<>();
	private static final List<String> LEGENDARY_ICON_POOL = new ArrayList<>();
	private static volatile boolean LEGENDARY_USE_POOL_FALLBACK = false;

	private static final class LegendaryItemsConfig {
		public List<LegendaryItemMapping> custom;
		public List<String> occurrences;
		public boolean unique;
	}

	private static final class LegendaryItemMapping {
		public String species;
		public String itemId;
	}

	public record QuestCategory(String name, String displayName, String iconItemId) {
	}

	private QuestManager() {
	}

	public static void init() {
		if (INITIALIZED) return;
		synchronized (QuestManager.class) {
			if (INITIALIZED) return;

			Path configDir = FabricLoader.getInstance().getConfigDir().resolve("economy");
			questsFile = configDir.resolve("quests.json");
			progressFile = configDir.resolve("quests_progress.json");
			legendaryItemsFile = FabricLoader.getInstance().getConfigDir().resolve("academy").resolve("legendary_items.json");
			try {
				Files.createDirectories(configDir);
			} catch (IOException ignored) {
			}

			if (!Files.exists(questsFile)) {
				writeDefaultConfig(questsFile);
			}

			reload();
			loadProgress();
			INITIALIZED = true;
		}
	}

	public static void reload() {
		synchronized (QuestManager.class) {
			loadLegendaryItems();
			loadConfig();
		}
	}

	public static String legendaryIconItemIdForSpecies(String speciesKey) {
		init();
		String key = normalizeSpeciesKey(speciesKey);
		if (key.isBlank()) return null;

		String override = LEGENDARY_ICON_BY_SPECIES.get(key);
		if (override != null && !override.isBlank()) {
			return override;
		}
		if (!LEGENDARY_USE_POOL_FALLBACK || LEGENDARY_ICON_POOL.isEmpty()) {
			return null;
		}
		int idx = Math.floorMod(key.hashCode(), LEGENDARY_ICON_POOL.size());
		return LEGENDARY_ICON_POOL.get(idx);
	}

	public static void save() {
		if (progressFile == null) return;
		Map<String, QuestProfile> storage = new HashMap<>();
		for (var e : PROFILES.entrySet()) {
			storage.put(e.getKey().toString(), e.getValue());
		}
		try (BufferedWriter writer = Files.newBufferedWriter(progressFile, StandardCharsets.UTF_8)) {
			GSON.toJson(storage, STORAGE_TYPE, writer);
		} catch (IOException ignored) {
		}
	}

	public static QuestProfile profile(ServerPlayerEntity player) {
		init();
		return PROFILES.computeIfAbsent(player.getUuid(), id -> new QuestProfile());
	}

	public static List<QuestCategory> categories() {
		init();
		return CATEGORIES.values().stream()
			.sorted(Comparator.comparing(QuestCategory::displayName, String.CASE_INSENSITIVE_ORDER))
			.toList();
	}

	public static List<QuestDef> questsByCategory(String category) {
		init();
		String cat = normalizeCategory(category);
		return QUESTS.values().stream()
			.filter(q -> Objects.equals(q.category(), cat))
			.sorted(Comparator.comparing(QuestDef::title, String.CASE_INSENSITIVE_ORDER))
			.toList();
	}

	public static QuestCategory category(String category) {
		init();
		return CATEGORIES.get(normalizeCategory(category));
	}

	public static QuestDef quest(String id) {
		init();
		return QUESTS.get(id);
	}

	public static void onPokemonCaptured(ServerPlayerEntity player, Object pokemon) {
		init();
		String speciesKey = normalizeSpeciesKey(extractSpeciesKey(pokemon));
		if (speciesKey.isBlank()) {
			return;
		}

		QuestProfile profile = profile(player);
		boolean changed = false;

		// Reliable fallback for POKEDEX_CAUGHT: track unique captured species.
		// This avoids relying on Cobblemon pokedex events/APIs that can vary across versions.
		if (profile.capturedSpecies != null) {
			if (profile.capturedSpecies.add(speciesKey)) {
				onPokedexCaughtProgress(player, profile.capturedSpecies.size());
				changed = true;
			}
		}

		for (QuestDef quest : QUESTS.values()) {
			QuestProgress qp = profile.quests.computeIfAbsent(quest.id(), k -> new QuestProgress());
			if (qp.claimed) continue;
			int before = qp.progress;
			int goalNow = effectiveGoal(quest, qp);

			if (quest.type() == QuestDef.QuestType.CAPTURE_ANY) {
				if (qp.progress < goalNow) {
					qp.progress += 1;
					changed = true;
					if (before < goalNow && qp.progress >= goalNow) {
						announceQuestCompleted(player, quest, qp);
					}
				}
				continue;
			}
			if (quest.type() == QuestDef.QuestType.CAPTURE_SPECIES) {
				String target = normalizeSpeciesKey(quest.target());
				if (target.isBlank()) continue;
				if (!Objects.equals(target, speciesKey)) continue;
				if (qp.progress < goalNow) {
					qp.progress = goalNow;
					changed = true;
					announceQuestCompleted(player, quest, qp);
				}
			}
		}

		if (changed) {
			save();
		}
	}

	public static void onBattleWon(ServerPlayerEntity player) {
		init();
		if (player == null) return;

		QuestProfile profile = profile(player);
		boolean changed = false;

		for (QuestDef quest : QUESTS.values()) {
			if (quest.type() != QuestDef.QuestType.BATTLE_WIN_ANY) continue;
			QuestProgress qp = profile.quests.computeIfAbsent(quest.id(), k -> new QuestProgress());
			if (qp.claimed) continue;
			int before = qp.progress;
			int goalNow = effectiveGoal(quest, qp);

			if (qp.progress < goalNow) {
				qp.progress += 1;
				changed = true;
				if (before < goalNow && qp.progress >= goalNow) {
					announceQuestCompleted(player, quest, qp);
				}
			}
		}

		if (changed) {
			save();
		}
	}

	public static void onTradeCompleted(ServerPlayerEntity player) {
		init();
		if (player == null) return;

		QuestProfile profile = profile(player);
		boolean changed = false;

		for (QuestDef quest : QUESTS.values()) {
			if (quest.type() != QuestDef.QuestType.TRADE_ANY) continue;
			QuestProgress qp = profile.quests.computeIfAbsent(quest.id(), k -> new QuestProgress());
			if (qp.claimed) continue;
			int before = qp.progress;
			int goalNow = effectiveGoal(quest, qp);

			if (qp.progress < goalNow) {
				qp.progress += 1;
				changed = true;
				if (before < goalNow && qp.progress >= goalNow) {
					announceQuestCompleted(player, quest, qp);
				}
			}
		}

		if (changed) {
			save();
		}
	}

	public static void onPokemonFished(ServerPlayerEntity player) {
		init();
		if (player == null) return;

		QuestProfile profile = profile(player);
		boolean changed = false;

		for (QuestDef quest : QUESTS.values()) {
			if (quest.type() != QuestDef.QuestType.FISH_POKEMON_ANY) continue;
			QuestProgress qp = profile.quests.computeIfAbsent(quest.id(), k -> new QuestProgress());
			if (qp.claimed) continue;
			int before = qp.progress;
			int goalNow = effectiveGoal(quest, qp);

			if (qp.progress < goalNow) {
				qp.progress += 1;
				changed = true;
				if (before < goalNow && qp.progress >= goalNow) {
					announceQuestCompleted(player, quest, qp);
				}
			}
		}

		if (changed) {
			save();
		}
	}

	public static void onShopBought(ServerPlayerEntity player, int itemCount) {
		init();
		if (player == null) return;
		if (itemCount <= 0) return;

		QuestProfile profile = profile(player);
		boolean changed = false;

		for (QuestDef quest : QUESTS.values()) {
			if (quest.type() != QuestDef.QuestType.SHOP_BUY_ANY) continue;
			QuestProgress qp = profile.quests.computeIfAbsent(quest.id(), k -> new QuestProgress());
			if (qp.claimed) continue;
			int before = qp.progress;
			int goalNow = effectiveGoal(quest, qp);

			if (qp.progress < goalNow) {
				qp.progress = Math.min(goalNow, qp.progress + itemCount);
				changed = true;
				if (before < goalNow && qp.progress >= goalNow) {
					announceQuestCompleted(player, quest, qp);
				}
			}
		}

		if (changed) {
			save();
		}
	}

	public static void onShopSold(ServerPlayerEntity player, int itemCount) {
		init();
		if (player == null) return;
		if (itemCount <= 0) return;

		QuestProfile profile = profile(player);
		boolean changed = false;

		for (QuestDef quest : QUESTS.values()) {
			if (quest.type() != QuestDef.QuestType.SHOP_SELL_ANY) continue;
			QuestProgress qp = profile.quests.computeIfAbsent(quest.id(), k -> new QuestProgress());
			if (qp.claimed) continue;
			int before = qp.progress;
			int goalNow = effectiveGoal(quest, qp);

			if (qp.progress < goalNow) {
				qp.progress = Math.min(goalNow, qp.progress + itemCount);
				changed = true;
				if (before < goalNow && qp.progress >= goalNow) {
					announceQuestCompleted(player, quest, qp);
				}
			}
		}

		if (changed) {
			save();
		}
	}

	public static void onOreMined(ServerPlayerEntity player, String oreKey) {
		init();
		if (player == null) return;
		String mined = normalizeOreKey(oreKey);
		if (mined.isBlank()) return;

		QuestProfile profile = profile(player);
		boolean changed = false;

		for (QuestDef quest : QUESTS.values()) {
			if (quest.type() != QuestDef.QuestType.MINE_ORE) continue;
			String target = normalizeOreKey(quest.target());
			if (target.isBlank()) continue;
			if (!Objects.equals(target, mined)) continue;

			QuestProgress qp = profile.quests.computeIfAbsent(quest.id(), k -> new QuestProgress());
			if (qp.claimed) continue;
			int before = qp.progress;
			int goalNow = effectiveGoal(quest, qp);

			if (qp.progress < goalNow) {
				qp.progress = Math.min(goalNow, qp.progress + 1);
				changed = true;
				if (before < goalNow && qp.progress >= goalNow) {
					announceQuestCompleted(player, quest, qp);
				}
			}
		}

		if (changed) {
			save();
		}
	}

	public static void onHarvested(ServerPlayerEntity player, String itemId, int amount) {
		init();
		if (player == null) return;
		if (amount <= 0) return;
		String harvested = normalizeItemId(itemId);
		if (harvested.isBlank()) return;

		QuestProfile profile = profile(player);
		boolean changed = false;

		for (QuestDef quest : QUESTS.values()) {
			if (quest.type() != QuestDef.QuestType.HARVEST_ITEM) continue;
			String target = normalizeItemTarget(quest.target());
			if (!targetMatchesItem(target, harvested)) continue;

			QuestProgress qp = profile.quests.computeIfAbsent(quest.id(), k -> new QuestProgress());
			if (qp.claimed) continue;
			int before = qp.progress;
			int goalNow = effectiveGoal(quest, qp);

			if (qp.progress < goalNow) {
				qp.progress = Math.min(goalNow, qp.progress + amount);
				changed = true;
				if (before < goalNow && qp.progress >= goalNow) {
					announceQuestCompleted(player, quest, qp);
				}
			}
		}

		if (changed) {
			save();
		}
	}

	public static void onPokedexCaughtProgress(ServerPlayerEntity player, int caughtCount) {
		init();
		if (player == null) return;
		if (caughtCount <= 0) return;

		QuestProfile profile = profile(player);
		boolean changed = false;

		for (QuestDef quest : QUESTS.values()) {
			if (quest.type() != QuestDef.QuestType.POKEDEX_CAUGHT) continue;
			QuestProgress qp = profile.quests.computeIfAbsent(quest.id(), k -> new QuestProgress());
			if (qp.claimed) continue;
			int before = qp.progress;
			int goalNow = effectiveGoal(quest, qp);

			// We store the best-known caught count; pokedex only increases.
			int next = Math.max(qp.progress, caughtCount);
			if (next != qp.progress) {
				qp.progress = next;
				changed = true;
			}
			if (before < goalNow && qp.progress >= goalNow) {
				announceQuestCompleted(player, quest, qp);
				changed = true;
			}
		}

		if (changed) {
			save();
		}
	}

	private static void announceQuestCompleted(ServerPlayerEntity player, QuestDef quest, QuestProgress qp) {
		var server = player.getServer();
		double rewardNow = effectiveReward(quest, qp);
		String titleNow = effectiveTitle(quest, qp);
		Text msg = Text.literal(
			"§6[Quêtes] §e" + player.getName().getString()
				+ " §7a terminé: §b" + titleNow
				+ " §7(§e+" + formatMoney(rewardNow) + " ₽§7)"
		);

		if (server == null) {
			player.sendMessage(Text.literal("§aQuête terminée: §f" + titleNow + "§a ! Ouvre §f/quests§a pour réclamer."), false);
			return;
		}
		server.getPlayerManager().broadcast(msg, false);
	}

	public static boolean tryClaim(ServerPlayerEntity player, String questId) {
		init();
		QuestDef quest = quest(questId);
		if (quest == null) return false;
		QuestProfile profile = profile(player);
		QuestProgress qp = profile.quests.computeIfAbsent(questId, k -> new QuestProgress());
		if (qp.claimed) return false;
		int goalNow = effectiveGoal(quest, qp);
		if (qp.progress < goalNow) return false;

		qp.claimed = true;
		double rewardNow = effectiveReward(quest, qp);
		String titleNow = effectiveTitle(quest, qp);
		EconomyManager.get(player).add(rewardNow);
		player.sendMessage(Text.literal("§aRécompense réclamée: §e+ " + formatMoney(rewardNow) + " ₽ §7(" + titleNow + ")"), false);

		if (quest.isTiered() && quest.tiers() != null) {
			int maxTier = quest.tiers().size() - 1;
			if (qp.tier < maxTier) {
				qp.tier += 1;
				qp.claimed = false;
			}
		}
		save();
		return true;
	}

	public static int effectiveGoal(QuestDef quest, QuestProgress progress) {
		if (quest == null) return 1;
		if (quest.isTiered() && quest.tiers() != null && progress != null) {
			int idx = Math.max(0, Math.min(progress.tier, quest.tiers().size() - 1));
			return Math.max(1, quest.tiers().get(idx).goal());
		}
		return Math.max(1, quest.goal());
	}

	public static double effectiveReward(QuestDef quest, QuestProgress progress) {
		if (quest == null) return 0.0;
		if (quest.isTiered() && quest.tiers() != null && progress != null) {
			int idx = Math.max(0, Math.min(progress.tier, quest.tiers().size() - 1));
			return Math.max(0.0, quest.tiers().get(idx).reward());
		}
		return Math.max(0.0, quest.reward());
	}

	public static String effectiveTitle(QuestDef quest, QuestProgress progress) {
		if (quest == null) return "";
		if (quest.isTiered() && quest.tiers() != null && progress != null) {
			int idx = Math.max(0, Math.min(progress.tier, quest.tiers().size() - 1));
			QuestDef.QuestTier t = quest.tiers().get(idx);
			String base = (t.title() != null && !t.title().isBlank()) ? t.title() : quest.title();
			return base + " §8(Palier " + (idx + 1) + "/" + quest.tiers().size() + ")";
		}
		return quest.title();
	}

	public static String effectiveDescription(QuestDef quest, QuestProgress progress) {
		if (quest == null) return "";
		if (quest.isTiered() && quest.tiers() != null && progress != null) {
			int idx = Math.max(0, Math.min(progress.tier, quest.tiers().size() - 1));
			String d = quest.tiers().get(idx).description();
			return d == null ? "" : d;
		}
		return quest.description() == null ? "" : quest.description();
	}

	public static QuestProgress progress(ServerPlayerEntity player, String questId) {
		init();
		QuestProfile profile = profile(player);
		return profile.quests.computeIfAbsent(questId, k -> new QuestProgress());
	}

	private static void loadConfig() {
		QUESTS.clear();
		CATEGORIES.clear();

		QuestConfig cfg = null;
		try (BufferedReader reader = Files.newBufferedReader(questsFile, StandardCharsets.UTF_8)) {
			cfg = GSON.fromJson(reader, QuestConfig.class);
		} catch (IOException e) {
			LOGGER.warn("[CobbleEconomy] Could not read quests config at {}", questsFile);
		}

		if (cfg == null) {
			cfg = defaultConfig();
		}

		// Categories from config
		if (cfg.categories != null) {
			for (QuestConfig.QuestCategoryConfig cat : cfg.categories) {
				if (cat == null || cat.name == null) continue;
				String name = normalizeCategory(cat.name);
				if (name.isBlank()) continue;
				String display = (cat.displayName == null || cat.displayName.isBlank()) ? cat.name : cat.displayName;
				CATEGORIES.put(name, new QuestCategory(name, display, cat.iconItemId));
			}
		}

		// Legendary generator
		if (cfg.legendaryCategory != null && cfg.legendaryCategory.enabled) {
			LEGENDARY_USE_POOL_FALLBACK = cfg.legendaryCategory.usePoolFallback;
			if (cfg.legendaryCategory.speciesIcons != null) {
				for (var e : cfg.legendaryCategory.speciesIcons.entrySet()) {
					if (e == null) continue;
					String species = normalizeSpeciesKey(e.getKey());
					if (species.isBlank()) continue;
					String itemId = e.getValue();
					if (itemId == null || itemId.isBlank()) continue;
					LEGENDARY_ICON_BY_SPECIES.put(species, itemId.trim());
				}
			}

			String catName = normalizeCategory(cfg.legendaryCategory.category);
			String display = cfg.legendaryCategory.displayName == null ? "Légendaires" : cfg.legendaryCategory.displayName;
			CATEGORIES.putIfAbsent(catName, new QuestCategory(catName, display, cfg.legendaryCategory.iconItemId));

			List<String> species = cfg.legendaryCategory.species == null ? List.of() : cfg.legendaryCategory.species;
			for (String s : species) {
				String key = normalizeSpeciesKey(s);
				if (key.isBlank()) continue;
				String id = "legendary_capture_" + key;
				String title = "Capturer " + prettySpeciesName(s);
				String desc = "Capture " + prettySpeciesName(s) + " (1/1).";
				addQuest(new QuestDef(id, catName, QuestDef.QuestType.CAPTURE_SPECIES, key, 1, cfg.legendaryCategory.reward, title, desc, null));
			}
		}
		if (cfg.legendaryCategory == null || !cfg.legendaryCategory.enabled) {
			LEGENDARY_USE_POOL_FALLBACK = false;
		}

		// Manual quests
		if (cfg.quests != null) {
			for (QuestConfig.QuestDefConfig q : cfg.quests) {
				if (q == null || q.id == null || q.id.isBlank()) continue;
				String category = normalizeCategory(q.category);
				if (category.isBlank()) category = "GENERAL";
				CATEGORIES.putIfAbsent(category, new QuestCategory(category, category, null));
				QuestDef.QuestType type = QuestDef.QuestType.parse(q.type);
				int goal = q.goal == null ? 1 : Math.max(1, q.goal);
				double reward = q.reward == null ? 100.0 : Math.max(0.0, q.reward);
				String title = q.title == null ? q.id : q.title;
				String description = q.description == null ? "" : q.description;
				String target = q.target;

				List<QuestDef.QuestTier> tiers = null;
				if (q.tiers != null && !q.tiers.isEmpty()) {
					tiers = new ArrayList<>();
					for (QuestConfig.QuestTierConfig t : q.tiers) {
						if (t == null) continue;
						int tg = t.goal == null ? 1 : Math.max(1, t.goal);
						double tr = t.reward == null ? reward : Math.max(0.0, t.reward);
						String tt = t.title == null ? title : t.title;
						String td = t.description == null ? "" : t.description;
						tiers.add(new QuestDef.QuestTier(tg, tr, tt, td));
					}
					if (tiers.isEmpty()) tiers = null;
				}

				addQuest(new QuestDef(q.id, category, type, target, goal, reward, title, description, tiers));
			}
		}
	}

	private static void loadLegendaryItems() {
		LEGENDARY_ICON_BY_SPECIES.clear();
		LEGENDARY_ICON_POOL.clear();

		if (legendaryItemsFile == null || !Files.exists(legendaryItemsFile)) {
			return;
		}

		LegendaryItemsConfig cfg = null;
		try (BufferedReader reader = Files.newBufferedReader(legendaryItemsFile, StandardCharsets.UTF_8)) {
			cfg = GSON.fromJson(reader, LegendaryItemsConfig.class);
		} catch (IOException e) {
			LOGGER.warn("[CobbleEconomy] Could not read legendary items config at {}", legendaryItemsFile);
		}
		if (cfg == null) return;

		if (cfg.occurrences != null) {
			for (String id : cfg.occurrences) {
				if (id == null) continue;
				String s = id.trim();
				if (s.isBlank()) continue;
				LEGENDARY_ICON_POOL.add(s);
			}
		}

		if (cfg.custom != null) {
			for (LegendaryItemMapping m : cfg.custom) {
				if (m == null) continue;
				String species = normalizeSpeciesKey(m.species);
				if (species.isBlank()) continue;
				if (m.itemId == null || m.itemId.isBlank()) continue;
				LEGENDARY_ICON_BY_SPECIES.put(species, m.itemId.trim());
			}
		}
	}

	private static void addQuest(QuestDef def) {
		if (def == null) return;
		if (def.id() == null || def.id().isBlank()) return;
		QUESTS.put(def.id(), def);
	}

	private static void loadProgress() {
		if (progressFile == null || !Files.exists(progressFile)) {
			return;
		}
		try (BufferedReader reader = Files.newBufferedReader(progressFile, StandardCharsets.UTF_8)) {
			Map<String, QuestProfile> storage = GSON.fromJson(reader, STORAGE_TYPE);
			if (storage == null) return;
			for (var e : storage.entrySet()) {
				try {
					UUID id = UUID.fromString(e.getKey());
					QuestProfile profile = e.getValue() == null ? new QuestProfile() : e.getValue();
					if (profile.quests == null) profile.quests = new HashMap<>();
					if (profile.capturedSpecies == null) profile.capturedSpecies = new java.util.HashSet<>();
					PROFILES.put(id, profile);
				} catch (IllegalArgumentException ignored) {
				}
			}
		} catch (IOException ignored) {
		}
	}

	private static void writeDefaultConfig(Path path) {
		QuestConfig defaults = defaultConfig();
		try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
			GSON.toJson(defaults, writer);
			LOGGER.info("[CobbleEconomy] Wrote default quests config to {}", path);
		} catch (IOException e) {
			LOGGER.warn("[CobbleEconomy] Could not write default quests config to {}", path);
		}
	}

	private static QuestConfig defaultConfig() {
		QuestConfig cfg = new QuestConfig();

		QuestConfig.QuestCategoryConfig general = new QuestConfig.QuestCategoryConfig();
		general.name = "GENERAL";
		general.displayName = "Quêtes";
		general.iconItemId = "minecraft:book";

		cfg.categories = new ArrayList<>();
		cfg.categories.add(general);

		QuestConfig.LegendaryCategoryConfig leg = new QuestConfig.LegendaryCategoryConfig();
		leg.enabled = true;
		leg.category = "LEGENDARIES";
		leg.displayName = "Légendaires";
		leg.iconItemId = "minecraft:nether_star";
		leg.usePoolFallback = false;
		leg.reward = 2500.0;
		leg.species = List.of(
			"mewtwo",
			"mew",
			"lugia",
			"ho_oh"
		);
		leg.speciesIcons = new LinkedHashMap<>();
		leg.speciesIcons.put("mewtwo", "minecraft:nether_star");
		leg.speciesIcons.put("mew", "minecraft:nether_star");
		leg.speciesIcons.put("lugia", "minecraft:nether_star");
		leg.speciesIcons.put("ho_oh", "minecraft:nether_star");
		cfg.legendaryCategory = leg;

		cfg.quests = new ArrayList<>();
		QuestConfig.QuestDefConfig q1 = new QuestConfig.QuestDefConfig();
		q1.id = "capture_10";
		q1.category = "GENERAL";
		q1.type = "CAPTURE_ANY";
		q1.goal = 10;
		q1.reward = 500.0;
		q1.title = "Collectionneur";
		q1.description = "Capture 10 Pokémon.";
		cfg.quests.add(q1);

		QuestConfig.QuestDefConfig q2 = new QuestConfig.QuestDefConfig();
		q2.id = "capture_pikachu";
		q2.category = "GENERAL";
		q2.type = "CAPTURE_SPECIES";
		q2.target = "pikachu";
		q2.goal = 1;
		q2.reward = 200.0;
		q2.title = "Attraper un Pikachu";
		q2.description = "Capture Pikachu (1/1).";
		cfg.quests.add(q2);

		QuestConfig.QuestDefConfig b = new QuestConfig.QuestDefConfig();
		b.id = "battle_win";
		b.category = "GENERAL";
		b.type = "BATTLE_WIN_ANY";
		b.title = "Combattant";
		b.description = "Remporte des combats Pokémon.";
		b.tiers = new ArrayList<>();
		QuestConfig.QuestTierConfig bt1 = new QuestConfig.QuestTierConfig();
		bt1.goal = 10;
		bt1.reward = 200.0;
		bt1.title = "Combattant";
		bt1.description = "Remporte 10 combats Pokémon.";
		b.tiers.add(bt1);
		QuestConfig.QuestTierConfig bt2 = new QuestConfig.QuestTierConfig();
		bt2.goal = 100;
		bt2.reward = 500.0;
		bt2.title = "Combattant";
		bt2.description = "Remporte 100 combats Pokémon.";
		b.tiers.add(bt2);
		QuestConfig.QuestTierConfig bt3 = new QuestConfig.QuestTierConfig();
		bt3.goal = 500;
		bt3.reward = 2500.0;
		bt3.title = "Combattant";
		bt3.description = "Remporte 500 combats Pokémon.";
		b.tiers.add(bt3);
		cfg.quests.add(b);

		QuestConfig.QuestDefConfig t = new QuestConfig.QuestDefConfig();
		t.id = "trade";
		t.category = "GENERAL";
		t.type = "TRADE_ANY";
		t.title = "Échangeur";
		t.description = "Effectue des échanges de Pokémon.";
		t.tiers = new ArrayList<>();
		QuestConfig.QuestTierConfig tt1 = new QuestConfig.QuestTierConfig();
		tt1.goal = 5;
		tt1.reward = 300.0;
		tt1.title = "Échangeur";
		tt1.description = "Effectue 5 échanges de Pokémon.";
		t.tiers.add(tt1);
		QuestConfig.QuestTierConfig tt2 = new QuestConfig.QuestTierConfig();
		tt2.goal = 25;
		tt2.reward = 1200.0;
		tt2.title = "Échangeur";
		tt2.description = "Effectue 25 échanges de Pokémon.";
		t.tiers.add(tt2);
		QuestConfig.QuestTierConfig tt3 = new QuestConfig.QuestTierConfig();
		tt3.goal = 100;
		tt3.reward = 5000.0;
		tt3.title = "Échangeur";
		tt3.description = "Effectue 100 échanges de Pokémon.";
		t.tiers.add(tt3);
		cfg.quests.add(t);

		return cfg;
	}

	private static String normalizeCategory(String raw) {
		if (raw == null) return "";
		return raw.trim().toUpperCase(Locale.ROOT);
	}

	private static String normalizeSpeciesKey(String raw) {
		if (raw == null) return "";
		String s = raw.trim().toLowerCase(Locale.ROOT);
		if (s.isBlank()) return "";
		// common formatting: "Ho-Oh" -> "ho_oh"
		s = s.replace('-', '_').replace(' ', '_');
		return s;
	}

	private static String normalizeOreKey(String raw) {
		if (raw == null) return "";
		String s = raw.trim().toLowerCase(Locale.ROOT);
		if (s.isBlank()) return "";
		s = s.replace('-', '_').replace(' ', '_');
		return s;
	}

	private static String normalizeItemId(String raw) {
		if (raw == null) return "";
		String s = raw.trim().toLowerCase(Locale.ROOT);
		if (s.isBlank()) return "";
		return s;
	}

	private static String normalizeItemTarget(String raw) {
		if (raw == null) return "";
		String s = raw.trim().toLowerCase(Locale.ROOT);
		if (s.isBlank()) return "";
		// allow French keyword for apricorns
		s = s.replace(' ', '_');
		if (s.endsWith("s") && s.length() > 1) {
			// plural -> singular (noigrumes/apricorns)
			s = s.substring(0, s.length() - 1);
		}
		if (s.equals("noigrume") || s.equals("noixgrume") || s.equals("apricorn") || s.equals("apricorne")) {
			return "*apricorn*";
		}
		return s;
	}

	private static boolean targetMatchesItem(String target, String itemId) {
		if (itemId == null || itemId.isBlank()) return false;
		if (target == null || target.isBlank() || target.equals("*") || target.equals("any")) return true;

		String full = itemId;
		String path = full;
		int idx = full.indexOf(':');
		if (idx >= 0 && idx + 1 < full.length()) {
			path = full.substring(idx + 1);
		}

		// wildcard support
		if (target.contains("*")) {
			String regex = "^" + java.util.regex.Pattern.quote(target).replace("\\*", ".*") + "$";
			java.util.regex.Pattern p = java.util.regex.Pattern.compile(regex, java.util.regex.Pattern.CASE_INSENSITIVE);
			if (target.contains(":")) {
				return p.matcher(full).matches();
			}
			return p.matcher(path).matches();
		}

		if (target.contains(":")) {
			return full.equalsIgnoreCase(target);
		}
		return path.equalsIgnoreCase(target);
	}

	private static String prettySpeciesName(String raw) {
		if (raw == null) return "?";
		String s = raw.trim();
		if (s.isBlank()) return "?";

		String key = normalizeSpeciesKey(s);
		// A few common special cases for nicer display
		if (key.equals("ho_oh")) return "Ho-Oh";
		if (key.equals("mr_mime")) return "Mr. Mime";
		if (key.equals("mime_jr")) return "Mime Jr.";
		if (key.equals("porygon_z")) return "Porygon-Z";

		String[] parts = key.split("_");
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < parts.length; i++) {
			String p = parts[i];
			if (p.isBlank()) continue;
			if (out.length() > 0) out.append(' ');
			out.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
		}
		return out.length() == 0 ? s : out.toString();
	}

	private static String extractSpeciesKey(Object pokemon) {
		if (pokemon == null) return "";

		// Try pokemon.getSpecies().getResourceIdentifier(): Identifier
		Object species = callNoArg(pokemon, "getSpecies");
		if (species != null) {
			Object rid = callNoArg(species, "getResourceIdentifier");
			if (rid instanceof Identifier id) {
				return id.getPath();
			}
			if (rid != null) {
				try {
					Identifier id = Identifier.of(rid.toString());
					return id.getPath();
				} catch (Throwable ignored) {
				}
			}
			Object name = callNoArg(species, "getName");
			if (name != null) {
				return name.toString();
			}
		}

		// Fallback: pokemon display name
		Object display = callNoArg(pokemon, "getDisplayName");
		if (display instanceof Text t) return t.getString();
		if (display != null) return display.toString();

		return "";
	}

	private static Object callNoArg(Object target, String methodName) {
		try {
			var m = target.getClass().getMethod(methodName);
			return m.invoke(target);
		} catch (Throwable ignored) {
			return null;
		}
	}

	private static String formatMoney(double value) {
		if (value == (long) value) return Long.toString((long) value);
		return String.format(Locale.ROOT, "%.2f", value);
	}
}
