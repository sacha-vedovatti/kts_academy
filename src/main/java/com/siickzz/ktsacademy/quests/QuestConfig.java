package com.siickzz.ktsacademy.quests;

import java.util.List;
import java.util.Map;

/**
 * Configuration des quêtes — format simplifié.
 *
 * Exemple minimal d'une quête :
 * {
 *   "id": "capture_10",
 *   "category": "POKEMON",
 *   "type": "CAPTURE_ANY",
 *   "iconItemId": "cobblemon:poke_ball",
 *   "title": "Collectionneur",
 *   "description": "Capture des Pokémon.",
 *   "tiers": [
 *     { "goal": 10,  "reward": 200 },
 *     { "goal": 50,  "reward": 500 },
 *     { "goal": 100, "reward": 1000 }
 *   ]
 * }
 *
 * Pour une quête simple (sans paliers) :
 * {
 *   "id": "capture_pikachu",
 *   "category": "POKEMON",
 *   "type": "CAPTURE_SPECIES",
 *   "target": "pikachu",
 *   "iconItemId": "cobblemon:poke_ball",
 *   "title": "Attraper un Pikachu",
 *   "description": "Capture Pikachu.",
 *   "goal": 1,
 *   "reward": 500
 * }
 */
public final class QuestConfig
{

    public List<QuestCategoryConfig> categories;
    public LegendaryCategoryConfig legendaryCategory;
    public List<QuestDefConfig> quests;
    public TierDropsConfig tierDrops;

    public static final class QuestCategoryConfig
    {
        public String name;
        public String displayName;
        public String iconItemId;
    }

    public static final class LegendaryCategoryConfig
    {
        public boolean enabled = true;
        public String category = "LEGENDARIES";
        public String displayName = "Légendaires";
        public String iconItemId = "cobblemon:master_ball";
        public double reward = 2500.0;
        public List<String> species;
        public Map<String, String> speciesIcons;
    }

    public static final class QuestDefConfig
    {
        public String id;
        public String category;
        /**
         * Type de quête :
         * CAPTURE_ANY, CAPTURE_SPECIES, CAPTURE_SHINY_ANY,
         * BATTLE_WIN_ANY, TRADE_ANY, FISH_POKEMON_ANY,
         * SHOP_BUY_ANY, SHOP_SELL_ANY,
         * HARVEST_ITEM, MINE_ORE, POKEDEX_CAUGHT
         */
        public String type;
        public String target;
        public String iconItemId;
        public String title;
        public String description;
        public Integer goal;
        public Double reward;
        public List<QuestTierConfig> tiers;
    }

    public static final class QuestTierConfig
    {
        public Integer goal;
        public Double reward;
    }

    public static final class TierDropsConfig
    {
        public boolean enabled = false;
        public Integer rollsPerTier = 1;
        public boolean dropOnGroundIfFull = true;
        public boolean notifyPlayer = true;
        public List<TierDropEntryConfig> drops;
    }

    public static final class TierDropEntryConfig
    {
        public String itemId;
        public Double chance;
        public Integer min = 1;
        public Integer max = 1;
    }
}
