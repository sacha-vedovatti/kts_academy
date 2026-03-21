package com.siickzz.ktsacademy.chat;

import java.util.regex.Pattern;

public final class ChatTemplate {
    private static final Pattern LEGACY_FORMAT_PATTERN = Pattern.compile("(?i)(?:&|\\u00a7)[0-9a-fk-or]");

    private ChatTemplate() {}

    public static String apply(String template, String playerName, String message, boolean allowMessageFormatting) {
        String safeTemplate = template == null ? "" : template;
        String safePlayer = playerName == null ? "" : playerName;
        String safeMessage = message == null ? "" : message;
        if (!allowMessageFormatting) {
            safeMessage = stripLegacyFormatting(safeMessage);
        }
        return safeTemplate
                .replace("{player}", safePlayer)
                .replace("{message}", safeMessage);
    }

    public static String stripLegacyFormatting(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        return LEGACY_FORMAT_PATTERN.matcher(input).replaceAll("");
    }
}

