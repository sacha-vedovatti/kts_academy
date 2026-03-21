package com.siickzz.ktsacademy.chat;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

final class ChatFormatter {
    private ChatFormatter() {}

    public static Text format(String template,
                              String playerName,
                              String message,
                              boolean allowMessageFormatting,
                              ServerPlayerEntity player,
                              boolean usePlaceholderApi) {
        String safeTemplate = template == null ? "" : template;
        List<Segment> segments = tokenize(safeTemplate);

        MutableText result = Text.empty();
        Style current = Style.EMPTY;

        for (Segment segment : segments) {
            switch (segment.type) {
                case TEXT -> {
                    Text placeholderText = PlaceholderService.apply(Text.literal(segment.value), player, usePlaceholderApi);
                    Text coloredText = LegacyTextTransformer.apply(placeholderText);
                    result.append(coloredText);
                    current = TextStyleUtils.lastStyle(coloredText, current);
                }
                case PLAYER -> {
                    String safePlayer = playerName == null ? "" : playerName;
                    result.append(Text.literal(safePlayer).setStyle(current));
                }
                case MESSAGE -> {
                    String safeMessage = message == null ? "" : message;
                    if (!allowMessageFormatting) {
                        safeMessage = ChatTemplate.stripLegacyFormatting(safeMessage);
                    }
                    LegacyTextParser.ParseResult parsed = LegacyTextParser.parseWithState(safeMessage, current);
                    result.append(parsed.text());
                    current = parsed.finalStyle();
                }
            }
        }

        return result;
    }

    private static List<Segment> tokenize(String template) {
        List<Segment> segments = new ArrayList<>();
        int index = 0;
        while (index < template.length()) {
            int playerIndex = template.indexOf("{player}", index);
            int messageIndex = template.indexOf("{message}", index);
            int nextIndex = nextTokenIndex(playerIndex, messageIndex);
            if (nextIndex < 0) {
                segments.add(new Segment(SegmentType.TEXT, template.substring(index)));
                break;
            }
            if (nextIndex > index) {
                segments.add(new Segment(SegmentType.TEXT, template.substring(index, nextIndex)));
            }
            if (nextIndex == playerIndex) {
                segments.add(new Segment(SegmentType.PLAYER, ""));
                index = playerIndex + "{player}".length();
            } else {
                segments.add(new Segment(SegmentType.MESSAGE, ""));
                index = messageIndex + "{message}".length();
            }
        }
        return segments;
    }

    private static int nextTokenIndex(int playerIndex, int messageIndex) {
        if (playerIndex < 0) {
            return messageIndex;
        }
        if (messageIndex < 0) {
            return playerIndex;
        }
        return Math.min(playerIndex, messageIndex);
    }

    private enum SegmentType {
        TEXT,
        PLAYER,
        MESSAGE
    }

    private record Segment(SegmentType type, String value) {}
}

