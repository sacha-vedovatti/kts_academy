package com.siickzz.ktsacademy.chat;

import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class LegacyTextParser {
    private LegacyTextParser() {}

    public static Text parse(String input) {
        if (input == null || input.isEmpty()) {
            return Text.empty();
        }

        MutableText result = Text.empty();
        StringBuilder buffer = new StringBuilder();
        Style style = Style.EMPTY;

        int length = input.length();
        for (int i = 0; i < length; i++) {
            char c = input.charAt(i);
            if ((c == '&' || c == '\u00a7') && i + 1 < length) {
                char code = Character.toLowerCase(input.charAt(i + 1));
                Formatting formatting = Formatting.byCode(code);
                if (formatting != null) {
                    flushBuffer(result, buffer, style);
                    style = applyFormatting(style, formatting);
                    i++;
                    continue;
                }
            }
            buffer.append(c);
        }
        flushBuffer(result, buffer, style);
        return result;
    }

    private static void flushBuffer(MutableText result, StringBuilder buffer, Style style) {
        if (buffer.length() == 0) {
            return;
        }
        result.append(Text.literal(buffer.toString()).setStyle(style));
        buffer.setLength(0);
    }

    private static Style applyFormatting(Style style, Formatting formatting) {
        if (formatting == Formatting.RESET) {
            return Style.EMPTY;
        }
        if (formatting.isColor()) {
            return Style.EMPTY.withFormatting(formatting);
        }
        return style.withFormatting(formatting);
    }
}

