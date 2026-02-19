package com.siickzz.ktsacademy.motd;

import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MotdListener {
    private static final Logger LOGGER = LoggerFactory.getLogger("KTSAcademy-MOTD");
    private static volatile MotdConfig config;

    private MotdListener() {}

    public static void register()
    {
        config = MotdManager.load();
        LOGGER.info("[KTS Academy] MOTD system initialized.");
    }

    public static void reload()
    {
        config = MotdManager.load();
        LOGGER.info("[KTS Academy] Server MOTD reloaded.");
    }

    public static Text getMotd()
    {
        MotdConfig cfg = config;

        if (cfg == null)
            return null;
        return MotdManager.buildMotd(cfg);
    }
}


