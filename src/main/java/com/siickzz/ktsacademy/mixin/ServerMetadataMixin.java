package com.siickzz.ktsacademy.mixin;

import com.siickzz.ktsacademy.motd.MotdListener;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerMetadata;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Intercepte {@code MinecraftServer#getServerMetadata()} pour remplacer
 * la description (MOTD) du serveur par le MOTD configurable du mod.
 *
 * La méthode retourne un {@code ServerMetadata} nullable (pas un Optional).
 */
@Mixin(MinecraftServer.class)
public class ServerMetadataMixin {

    @Inject(method = "getServerMetadata", at = @At("RETURN"), cancellable = true)
    private void ktsacademy$injectMotd(CallbackInfoReturnable<ServerMetadata> cir)
    {
        ServerMetadata original = cir.getReturnValue();
        if (original == null)
            return;

        Text motd = MotdListener.getMotd();
        if (motd == null)
            return;

        // ServerMetadata : (Text description, Optional<Players> players, Optional<Version> version, Optional<Favicon> favicon, boolean secureChatEnforced)
        ServerMetadata updated = new ServerMetadata(
                motd,
                original.players(),
                original.version(),
                original.favicon(),
                original.secureChatEnforced()
        );
        cir.setReturnValue(updated);
    }
}
