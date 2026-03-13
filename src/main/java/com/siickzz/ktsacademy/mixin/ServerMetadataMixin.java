package com.siickzz.ktsacademy.mixin;

import com.siickzz.ktsacademy.motd.MotdListener;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerMetadata;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

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

        ServerMetadata updated = new ServerMetadata(motd, original.players(), original.version(), original.favicon(), original.secureChatEnforced());
        cir.setReturnValue(updated);
    }
}
