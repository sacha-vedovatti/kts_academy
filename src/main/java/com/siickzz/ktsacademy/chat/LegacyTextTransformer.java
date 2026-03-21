package com.siickzz.ktsacademy.chat;

import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

import java.util.Optional;

public final class LegacyTextTransformer {
    private LegacyTextTransformer() {}

    public static Text apply(Text input) {
        if (input == null) {
            return Text.empty();
        }
        MutableText result = Text.empty();
        input.visit((style, string) -> {
            if (string == null || string.isEmpty()) {
                return Optional.empty();
            }
            Text parsed = LegacyTextParser.parse(string, style);
            result.append(parsed);
            return Optional.empty();
        }, Style.EMPTY);
        return result;
    }
}

