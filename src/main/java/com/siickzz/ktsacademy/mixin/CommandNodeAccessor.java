package com.siickzz.ktsacademy.mixin;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.CommandNode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(CommandNode.class)
public interface CommandNodeAccessor<S> {
    @Accessor("command")
    Command<S> getCommand();

    @Accessor("command")
    void setCommand(Command<S> command);
}

