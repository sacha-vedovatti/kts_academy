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

    /** Catégories affichées dans le menu principal. */
    public List<QuestCategoryConfig> categories;

    /**
     * Génération automatique des quêtes de capture pour les légendaires.
     * Chaque espèce listée génère une quête "Capturer X" dans la catégorie indiquée.
     */
    public LegendaryCategoryConfig legendaryCategory;

    /** Liste des quêtes manuelles. */
    public List<QuestDefConfig> quests;

    /** Bonus d'items donnés aléatoirement à chaque palier complété. */
    public TierDropsConfig tierDrops;

    // -------------------------------------------------------------------------

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
        /** Récompense en $ (Cobblemon Economy) pour chaque capture légendaire. */
        public double reward = 2500.0;
        /** Liste des espèces légendaires à générer comme quêtes. */
        public List<String> species;
        /** Icône par espèce (optionnel). Ex: {"mewtwo": "cobblemon:cloning_cable"} */
        public Map<String, String> speciesIcons;
    }

    public static final class QuestDefConfig
    {
        /** Identifiant unique de la quête (ex: "capture_10"). */
        public String id;
        /** Catégorie (doit correspondre à un nom dans "categories"). */
        public String category;
        /**
         * Type de quête :
         * CAPTURE_ANY, CAPTURE_SPECIES, CAPTURE_SHINY_ANY,
         * BATTLE_WIN_ANY, TRADE_ANY, FISH_POKEMON_ANY,
         * SHOP_BUY_ANY, SHOP_SELL_ANY,
         * HARVEST_ITEM, MINE_ORE, POKEDEX_CAUGHT
         */
        public String type;
        /** Cible (espèce pour CAPTURE_SPECIES, minerai pour MINE_ORE, item pour HARVEST_ITEM). */
        public String target;
        /** Item Minecraft affiché comme icône dans le GUI. */
        public String iconItemId;
        /** Titre de la quête. */
        public String title;
        /** Description courte de la quête. */
        public String description;
        /** Objectif (utilisé seulement si pas de tiers). */
        public Integer goal;
        /** Récompense en $ (utilisée seulement si pas de tiers). */
        public Double reward;
        /**
         * Paliers de la quête. Chaque palier a un objectif cumulatif et une récompense.
         * Si présents, "goal" et "reward" directs sont ignorés.
         */
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
