package com.siickzz.ktsacademy.chat;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PlaceholderService {
    private static final Logger LOGGER = LoggerFactory.getLogger("KTS-Academy-Placeholders");
    private static final PlaceholderEngine ENGINE = createEngine();

    private PlaceholderService() {}

    public static Text apply(Text text, ServerPlayerEntity player, boolean enabled) {
        if (!enabled) {
            return text;
        }
        return ENGINE.apply(text, player);
    }

    private static PlaceholderEngine createEngine() {
        if (!FabricLoader.getInstance().isModLoaded("placeholder-api")) {
            return (text, player) -> text;
        }
        try {
            Class<?> clazz = Class.forName("com.siickzz.ktsacademy.chat.Pb4PlaceholderEngine");
            return (PlaceholderEngine) clazz.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            LOGGER.warn("[KTS Academy] Placeholder API indisponible: {}", e.getMessage());
            return (text, player) -> text;
        }
    }
}

