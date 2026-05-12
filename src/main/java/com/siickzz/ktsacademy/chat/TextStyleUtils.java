package com.siickzz.ktsacademy.chat;

import net.minecraft.text.Style;
import net.minecraft.text.Text;

import java.util.Optional;

final class TextStyleUtils {
    private TextStyleUtils() {}

    public static Style lastStyle(Text input, Style base) {
        if (input == null) {
            return base == null ? Style.EMPTY : base;
        }
        final Style[] current = new Style[] { base == null ? Style.EMPTY : base };
        input.visit((style, string) -> {
            if (string == null) {
                return Optional.empty();
            }
            Style resolved = style == null || style.isEmpty() ? current[0] : style.withParent(current[0]);
            current[0] = resolved;
            return Optional.empty();
        }, Style.EMPTY);
        return current[0];
    }
}

