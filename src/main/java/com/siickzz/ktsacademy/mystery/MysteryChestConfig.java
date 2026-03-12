package com.siickzz.ktsacademy.mystery;

import com.google.gson.JsonObject;

import java.util.List;

public final class MysteryChestConfig {
    public List<ChestConfig> chests;

    public static final class ChestConfig {
        public String id;
        public String displayName;
        public boolean enabled = true;
        public String keyItemId;
        public boolean requireEnchantedKey = true;
        public Integer rolls = 1;
        public boolean dropOnGroundIfFull = true;
        public boolean notifyPlayer = true;
        public List<String> blockIds;
        public List<DropEntryConfig> drops;
    }

    public static final class DropEntryConfig {
        public String itemId;
        public Double chance;
        public Integer min = 1;
        public Integer max = 1;
        public JsonObject components;
    }
}
