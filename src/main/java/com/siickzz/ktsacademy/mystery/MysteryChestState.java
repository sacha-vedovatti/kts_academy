package com.siickzz.ktsacademy.mystery;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import net.minecraft.server.world.ServerWorld;

import java.util.HashMap;
import java.util.Map;

public final class MysteryChestState extends PersistentState {
    private static final String DATA_NAME = "ktsacademy_mystery_chests";
    private static final String LIST_KEY = "Chests";

    private static final PersistentState.Type<MysteryChestState> TYPE = new PersistentState.Type<>(MysteryChestState::new, MysteryChestState::fromNbt, null);

    private final Map<BlockPos, String> chests = new HashMap<>();

    public static MysteryChestState get(ServerWorld world)
    {
        return world.getPersistentStateManager().getOrCreate(TYPE, DATA_NAME);
    }

    public void set(BlockPos pos, String crateId)
    {
        if (pos == null || crateId == null || crateId.isBlank())
            return;
        chests.put(pos.toImmutable(), crateId);
        markDirty();
    }

    public boolean remove(BlockPos pos)
    {
        if (pos == null)
            return false;

        boolean removed = chests.remove(pos) != null;
        if (removed)
            markDirty();
        return removed;
    }

    public String get(BlockPos pos)
    {
        if (pos == null)
            return null;
        return chests.get(pos);
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries)
    {
        NbtList list = new NbtList();

        for (var entry : chests.entrySet()) {
            BlockPos pos = entry.getKey();
            String id = entry.getValue();
            if (pos == null || id == null || id.isBlank())
                continue;

            NbtCompound tag = new NbtCompound();
            tag.putInt("x", pos.getX());
            tag.putInt("y", pos.getY());
            tag.putInt("z", pos.getZ());
            tag.putString("id", id);
            list.add(tag);
        }
        nbt.put(LIST_KEY, list);
        return nbt;
    }

    private static MysteryChestState fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries)
    {
        MysteryChestState state = new MysteryChestState();
        if (nbt == null || !nbt.contains(LIST_KEY, NbtElement.LIST_TYPE))
            return state;

        NbtList list = nbt.getList(LIST_KEY, NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) {
            NbtCompound tag = list.getCompound(i);
            if (!tag.contains("id", NbtElement.STRING_TYPE))
                continue;

            String id = tag.getString("id");
            BlockPos pos = new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z"));
            if (!id.isBlank())
                state.chests.put(pos, id);
        }
        return state;
    }
}
