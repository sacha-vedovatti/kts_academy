package com.siickzz.ktsacademy.quests.gui;

import com.siickzz.ktsacademy.quests.QuestDef;
import com.siickzz.ktsacademy.quests.QuestManager;
import com.siickzz.ktsacademy.quests.QuestProgress;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class QuestGui {

    // Tout le GUI est en 9×6 (54 slots)
    private static final int ROWS      = 6;
    private static final int SIZE      = ROWS * 9; // 54
    private static final int PAGE_SIZE = 45;        // 5 lignes de quêtes, dernière ligne = contrôles

    // Slots de contrôle (dernière ligne)
    private static final int SLOT_BACK = 49;
    private static final int SLOT_PREV = 45;
    private static final int SLOT_NEXT = 53;

    private static final ItemStack FILLER_DARK  = filler(Items.BLACK_STAINED_GLASS_PANE);
    private static final ItemStack FILLER_LIGHT = filler(Items.GRAY_STAINED_GLASS_PANE);

    private QuestGui() {}

    // =========================================================================
    // Entrée publique
    // =========================================================================

    public static void open(ServerPlayerEntity player)
    {
        openCategories(player, 0);
    }

    // =========================================================================
    // Menu catégories (9×6, texture 0.png)
    // =========================================================================

    private static void openCategories(ServerPlayerEntity player, int page)
    {
        Inventory inv = new SimpleInventory(SIZE);
        String[] slotCat = new String[SIZE];
        fill(inv, FILLER_DARK);

        List<QuestManager.QuestCategory> cats = QuestManager.categories();
        int start = page * PAGE_SIZE;
        int end   = Math.min(cats.size(), start + PAGE_SIZE);

        // Contrôles
        inv.setStack(SLOT_BACK, named(Items.BARRIER, "§cFermer"));
        slotCat[SLOT_BACK] = "__close__";
        if (page > 0) {
            inv.setStack(SLOT_PREV, named(Items.ARROW, "§ePrécédent"));
            slotCat[SLOT_PREV] = "__prev__";
        }
        if (end < cats.size()) {
            inv.setStack(SLOT_NEXT, named(Items.ARROW, "§eSuivant"));
            slotCat[SLOT_NEXT] = "__next__";
        }

        int slot = 0;
        for (int i = start; i < end; i++) {
            if (slot >= PAGE_SIZE)
                break;
            QuestManager.QuestCategory cat = cats.get(i);
            List<QuestDef> quests = QuestManager.questsByCategory(cat.name());
            int total = quests.size();
            int done  = countAllClaimed(player, quests);
            Item icon = resolveItem(cat.iconItemId(), Items.BOOK);

            ItemStack stack = new ItemStack(icon);
            stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§b§l" + cat.displayName()));

            List<Text> lore = new ArrayList<>();
            lore.add(Text.literal("§7Quêtes : §f" + total));
            lore.add(Text.literal("§7Terminées : §a" + done + "§7/§f" + total));
            lore.add(Text.literal("§8Cliquer pour ouvrir"));
            stack.set(DataComponentTypes.LORE, new LoreComponent(lore));
            inv.setStack(slot, stack);
            slotCat[slot] = cat.name();
            slot++;
        }
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
            (id, pInv, p) -> new QuestHandler(id, pInv, inv, slotCat, null, page, true),
            Text.literal("§0Quêtes")
        ));
    }

    // =========================================================================
    // Liste des quêtes (9×6, texture 1/2/3.png selon page)
    // =========================================================================

    private static void openCategory(ServerPlayerEntity player, String category, int page)
    {
        Inventory inv = new SimpleInventory(SIZE);
        String[] slotQuest = new String[SIZE];
        fill(inv, FILLER_LIGHT);

        List<QuestDef> quests = new ArrayList<>(QuestManager.questsByCategory(category));
        quests.sort((a, b) -> {
            int ra = statusRank(QuestManager.progress(player, a.id()), QuestManager.effectiveGoal(a, QuestManager.progress(player, a.id())));
            int rb = statusRank(QuestManager.progress(player, b.id()), QuestManager.effectiveGoal(b, QuestManager.progress(player, b.id())));
            return ra != rb ? Integer.compare(ra, rb) : String.CASE_INSENSITIVE_ORDER.compare(a.title(), b.title());
        });

        int start = page * PAGE_SIZE;
        int end   = Math.min(quests.size(), start + PAGE_SIZE);

        // Contrôles
        inv.setStack(SLOT_BACK, named(Items.ARROW, "§eRetour"));
        slotQuest[SLOT_BACK] = "__back__";
        if (page > 0) {
            inv.setStack(SLOT_PREV, named(Items.ARROW, "§ePrécédent"));
            slotQuest[SLOT_PREV] = "__prev__";
        }
        if (end < quests.size()) {
            inv.setStack(SLOT_NEXT, named(Items.ARROW, "§eSuivant"));
            slotQuest[SLOT_NEXT] = "__next__";
        }

        int slot = 0;
        for (int i = start; i < end; i++) {
            if (slot >= PAGE_SIZE)
                break;

            QuestDef quest = quests.get(i);
            QuestProgress qp = QuestManager.progress(player, quest.id());
            int    goal    = QuestManager.effectiveGoal(quest, qp);
            double reward  = QuestManager.effectiveReward(quest, qp);
            String title   = QuestManager.effectiveTitle(quest, qp);
            String desc    = QuestManager.effectiveDescription(quest, qp);
            boolean done   = qp.progress >= goal;
            boolean claimed = qp.claimed;
            Item icon = questIcon(category, quest, qp);

            ItemStack stack = new ItemStack(icon);
            stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§b" + title));

            List<Text> lore = new ArrayList<>();
            if (desc != null && !desc.isBlank())
                lore.add(Text.literal("§7" + desc));

            int shown = Math.min(goal, Math.max(0, qp.progress));
            lore.add(Text.literal("§7Progression : §f" + shown + "§7/§f" + goal + " " + progressBar(shown, goal)));
            lore.add(Text.literal("§7Récompense : §e" + QuestManager.formatMoney(reward) + " $"));
            if (done && !claimed)
                lore.add(Text.literal("§a▶ Cliquer pour réclamer !"));
            else if (claimed)
                lore.add(Text.literal("§8✔ Récompense réclamée"));
            else
                lore.add(Text.literal("§8Continue ta progression…"));
            stack.set(DataComponentTypes.LORE, new LoreComponent(lore));
            inv.setStack(slot, stack);
            slotQuest[slot] = quest.id();
            slot++;
        }

        QuestManager.QuestCategory catDef = QuestManager.category(category);
        String catTitle = catDef != null ? catDef.displayName() : category;
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
            (id, pInv, p) -> new QuestHandler(id, pInv, inv, slotQuest, category, page, false),
            Text.literal("§0Quêtes — " + catTitle)
        ));
    }

    // =========================================================================
    // Handler unique (catégories ET liste)
    // =========================================================================

    private static final class QuestHandler extends GenericContainerScreenHandler
    {
        private final String[] slots;
        private final String   category; // null = mode catégories
        private final int      page;
        private final boolean  isCategoryMenu;

        QuestHandler(int syncId, PlayerInventory pInv, Inventory inv,
                     String[] slots, String category, int page, boolean isCategoryMenu)
        {
            super(ScreenHandlerType.GENERIC_9X6, syncId, pInv, inv, ROWS);
            this.slots          = slots;
            this.category       = category;
            this.page           = page;
            this.isCategoryMenu = isCategoryMenu;
        }

        @Override
        public void onSlotClick(int slotIndex, int button, SlotActionType action, PlayerEntity player)
        {
            if (player.getWorld().isClient || !(player instanceof ServerPlayerEntity sp)) {
                super.onSlotClick(slotIndex, button, action, player);
                return;
            }
            // Bloquer tout transfert d'items
            if (slotIndex < 0 || slotIndex >= SIZE)
                return;
            if (action != SlotActionType.PICKUP)
                return;

            String id = slots[slotIndex];
            if (id == null)
                return;
            if (isCategoryMenu) {
                switch (id) {
                    case "__close__" -> sp.closeHandledScreen();
                    case "__prev__"  -> openCategories(sp, Math.max(0, page - 1));
                    case "__next__"  -> openCategories(sp, page + 1);
                    default          -> openCategory(sp, id, 0);
                }
            } else {
                switch (id) {
                    case "__back__" -> openCategories(sp, 0);
                    case "__prev__" -> openCategory(sp, category, Math.max(0, page - 1));
                    case "__next__" -> openCategory(sp, category, page + 1);
                    default -> {
                        QuestDef quest = QuestManager.quest(id);
                        if (quest == null)
                            return;

                        QuestProgress qp = QuestManager.progress(sp, id);
                        int goal = QuestManager.effectiveGoal(quest, qp);
                        if (qp.progress >= goal && !qp.claimed) {
                            if (QuestManager.tryClaim(sp, id))
                                openCategory(sp, category, page);
                        } else {
                            sp.sendMessage(Text.literal(
                                "§7Progression : §f" + Math.min(goal, qp.progress) + "§7/§f" + goal
                            ), false);
                        }
                    }
                }
            }
        }

        @Override public boolean canUse(PlayerEntity player)
        {
            return true;
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static Item questIcon(String category, QuestDef quest, QuestProgress progress)
    {
        boolean claimed = progress.claimed;
        boolean done    = progress.progress >= QuestManager.effectiveGoal(quest, progress);

        if (quest.iconItemId() != null && !quest.iconItemId().isBlank()) {
            Item i = resolveItem(quest.iconItemId(), Items.AIR);
            if (i != Items.AIR)
                return i;
        }
        if (claimed)
            return Items.GRAY_DYE;
        if (done)
            return Items.LIME_DYE;
        if (category != null && category.toUpperCase(Locale.ROOT).contains("LEGEND")) {
            if (quest.type() == QuestDef.QuestType.CAPTURE_SPECIES && quest.target() != null) {
                String iconId = QuestManager.legendaryIconItemIdForSpecies(quest.target());

                if (iconId != null) {
                    Item i = resolveItem(iconId, Items.AIR);
                    if (i != Items.AIR)
                        return i;
                }
            }
            return Items.NETHER_STAR;
        }
        return switch (quest.type()) {
            case BATTLE_WIN_ANY    -> Items.IRON_SWORD;
            case TRADE_ANY         -> Items.EMERALD;
            case FISH_POKEMON_ANY  -> Items.FISHING_ROD;
            case SHOP_BUY_ANY      -> Items.CHEST;
            case SHOP_SELL_ANY     -> Items.HOPPER;
            case CAPTURE_SHINY_ANY -> Items.AMETHYST_SHARD;
            case HARVEST_ITEM      -> Items.SWEET_BERRIES;
            case POKEDEX_CAUGHT    -> Items.KNOWLEDGE_BOOK;
            case MINE_ORE          -> Items.IRON_PICKAXE;
            case CAPTURE_SPECIES   -> Items.NAME_TAG;
            default                -> Items.PAPER;
        };
    }

    private static String progressBar(int progress, int goal) {
        int width  = 10;
        int filled = (int) Math.round((Math.max(0, Math.min(progress, goal)) / (double) Math.max(1, goal)) * width);
        StringBuilder sb = new StringBuilder("§7[");

        for (int i = 0; i < width; i++)
            sb.append(i < filled ? "§a■" : "§8■");
        return sb.append("§7]").toString();
    }

    private static int statusRank(QuestProgress p, int goal)
    {
        if (p.progress >= goal && !p.claimed)
            return 0;
        if (p.progress < goal)
            return 1;
        return 2;
    }

    private static int countAllClaimed(ServerPlayerEntity player, List<QuestDef> quests) {
        int count = 0;

        for (QuestDef q : quests) {
            QuestProgress qp = QuestManager.progress(player, q.id());
            int goal = q.isTiered() && q.tiers() != null ? q.tiers().get(q.tiers().size() - 1).goal() : q.goal();

            if (qp.progress >= goal)
                count++;
        }
        return count;
    }

    private static void fill(Inventory inv, ItemStack stack)
    {
        for (int i = 0; i < inv.size(); i++)
            inv.setStack(i, stack.copy());
    }

    private static ItemStack filler(Item item)
    {
        ItemStack s = new ItemStack(item);

        s.set(DataComponentTypes.CUSTOM_NAME, Text.literal(" "));
        return s;
    }

    private static ItemStack named(Item item, String name)
    {
        ItemStack s = new ItemStack(item);

        s.set(DataComponentTypes.CUSTOM_NAME, Text.literal(name));
        return s;
    }

    private static Item resolveItem(String itemId, Item fallback)
    {
        if (itemId == null || itemId.isBlank())
            return fallback;
        try {
            Item i = Registries.ITEM.get(Identifier.of(itemId));
            return i == Items.AIR ? fallback : i;
        } catch (Throwable ignored) {
            return fallback;
        }
    }
}
