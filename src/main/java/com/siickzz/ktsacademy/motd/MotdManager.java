package com.siickzz.ktsacademy.motd;

import com.google.gson.Gson;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class MotdManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("KTS");
    private static final Gson GSON = new Gson();

    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("ktsacademy").resolve("motd.json");

    private static final String SMALL_CAPS = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀsᴛᴜᴠᴡxʏᴢ";
    private static final int FULLWIDTH_UPPER_OFFSET = 'Ａ' - 'A';
    private static final int FULLWIDTH_LOWER_OFFSET = 'ａ' - 'a';

    private MotdManager() {}

    public static MotdConfig load()
    {
        if (!Files.exists(CONFIG_PATH))
            createDefault();
        try (Reader r = Files.newBufferedReader(CONFIG_PATH)) {
            MotdConfig cfg = GSON.fromJson(r, MotdConfig.class);

            if (cfg == null || cfg.lines == null || cfg.lines.isEmpty()) {
                LOGGER.warn("[Server] wrong motd.json file: empty or not valid.");
                return defaultConfig();
            }
            LOGGER.info("[Server] MOTd configuration loaded in {}", CONFIG_PATH);
            return cfg;
        } catch (IOException e) {
            LOGGER.error("[Server] cannot read MOTD config file: {}", e.getMessage());
            return defaultConfig();
        }
    }

    public static Text buildMotd(MotdConfig config)
    {
        MutableText root = Text.empty();
        List<MotdConfig.LineConfig> lines = config.lines;
        int max = Math.min(lines.size(), 2);

        for (int i = 0; i < max; i++) {
            if (i > 0)
                root.append(Text.literal("\n"));
            root.append(buildLine(lines.get(i)));
        }
        return root;
    }

    private static Text buildLine(MotdConfig.LineConfig line)
    {
        String raw = line.text == null ? "" : line.text;
        String fontStyle = line.font == null ? "normal" : line.font.toLowerCase();
        boolean bold = line.bold;

        if (line.gradient != null)
            return buildGradientLine(raw, fontStyle, bold, line.gradient);
        else
            return buildLegacyLine(raw, fontStyle, bold);
    }

    private static Text buildGradientLine(String raw, String fontStyle, boolean bold, MotdConfig.GradientConfig grad)
    {
        int colorFrom = parseHex(grad.from);
        int colorTo   = parseHex(grad.to);
        String stripped = raw.replaceAll("§[0-9a-fk-or]", "");

        int len = stripped.length();
        MutableText result = Text.empty();
        if (len == 0)
            return Text.empty();
        for (int i = 0; i < len; i++) {
            char c = stripped.charAt(i);
            float t = (len == 1) ? 0f : (float) i / (len - 1);
            int rgb = interpolateRgb(colorFrom, colorTo, t);
            String mapped = applyFont(String.valueOf(c), fontStyle);
            MutableText ch = Text.literal(mapped).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(rgb)).withBold(bold));

            result.append(ch);
        }
        return result;
    }

    private static Text buildLegacyLine(String raw, String fontStyle, boolean bold)
    {
        MutableText result = Text.empty();
        Style currentStyle = bold ? Style.EMPTY.withBold(true) : Style.EMPTY;
        StringBuilder buf = new StringBuilder();
        int i = 0;

        while (i < raw.length()) {
            char c = raw.charAt(i);

            if (c == '§' && i + 1 < raw.length()) {
                if (!buf.isEmpty()) {
                    result.append(Text.literal(applyFont(buf.toString(), fontStyle)).setStyle(currentStyle));
                    buf.setLength(0);
                }

                char code = Character.toLowerCase(raw.charAt(i + 1));
                currentStyle = applyFormattingCode(currentStyle, code);
                i += 2;
            } else {
                buf.append(c);
                i++;
            }
        }
        if (!buf.isEmpty())
            result.append(Text.literal(applyFont(buf.toString(), fontStyle)).setStyle(currentStyle));
        return result;
    }

    private static Style applyFormattingCode(Style style, char code)
    {
        return switch (code) {
            case '0' -> style.withColor(Formatting.BLACK).withBold(false).withItalic(false).withUnderline(false).withStrikethrough(false).withObfuscated(false);
            case '1' -> style.withColor(Formatting.DARK_BLUE).withBold(false).withItalic(false).withUnderline(false).withStrikethrough(false).withObfuscated(false);
            case '2' -> style.withColor(Formatting.DARK_GREEN).withBold(false).withItalic(false).withUnderline(false).withStrikethrough(false).withObfuscated(false);
            case '3' -> style.withColor(Formatting.DARK_AQUA).withBold(false).withItalic(false).withUnderline(false).withStrikethrough(false).withObfuscated(false);
            case '4' -> style.withColor(Formatting.DARK_RED).withBold(false).withItalic(false).withUnderline(false).withStrikethrough(false).withObfuscated(false);
            case '5' -> style.withColor(Formatting.DARK_PURPLE).withBold(false).withItalic(false).withUnderline(false).withStrikethrough(false).withObfuscated(false);
            case '6' -> style.withColor(Formatting.GOLD).withBold(false).withItalic(false).withUnderline(false).withStrikethrough(false).withObfuscated(false);
            case '7' -> style.withColor(Formatting.GRAY).withBold(false).withItalic(false).withUnderline(false).withStrikethrough(false).withObfuscated(false);
            case '8' -> style.withColor(Formatting.DARK_GRAY).withBold(false).withItalic(false).withUnderline(false).withStrikethrough(false).withObfuscated(false);
            case '9' -> style.withColor(Formatting.BLUE).withBold(false).withItalic(false).withUnderline(false).withStrikethrough(false).withObfuscated(false);
            case 'a' -> style.withColor(Formatting.GREEN).withBold(false).withItalic(false).withUnderline(false).withStrikethrough(false).withObfuscated(false);
            case 'b' -> style.withColor(Formatting.AQUA).withBold(false).withItalic(false).withUnderline(false).withStrikethrough(false).withObfuscated(false);
            case 'c' -> style.withColor(Formatting.RED).withBold(false).withItalic(false).withUnderline(false).withStrikethrough(false).withObfuscated(false);
            case 'd' -> style.withColor(Formatting.LIGHT_PURPLE).withBold(false).withItalic(false).withUnderline(false).withStrikethrough(false).withObfuscated(false);
            case 'e' -> style.withColor(Formatting.YELLOW).withBold(false).withItalic(false).withUnderline(false).withStrikethrough(false).withObfuscated(false);
            case 'f' -> style.withColor(Formatting.WHITE).withBold(false).withItalic(false).withUnderline(false).withStrikethrough(false).withObfuscated(false);
            case 'k' -> style.withObfuscated(true);
            case 'l' -> style.withBold(true);
            case 'm' -> style.withStrikethrough(true);
            case 'n' -> style.withUnderline(true);
            case 'o' -> style.withItalic(true);
            case 'r' -> Style.EMPTY;
            default  -> style;
        };
    }

    private static String applyFont(String text, String fontStyle)
    {
        return switch (fontStyle) {
            case "small_caps" -> toSmallCaps(text);
            case "monospace"  -> toFullwidth(text);
            case "bold", "italic", "normal" -> text;
            default -> text;
        };
    }

    private static String toSmallCaps(String text)
    {
        StringBuilder sb = new StringBuilder(text.length());

        for (char c : text.toCharArray()) {
            if (c >= 'a' && c <= 'z')
                sb.append(SMALL_CAPS.charAt(c - 'a'));
            else if (c >= 'A' && c <= 'Z')
                sb.append(SMALL_CAPS.charAt(c - 'A'));
            else
                sb.append(c);
        }
        return sb.toString();
    }

    private static String toFullwidth(String text)
    {
        StringBuilder sb = new StringBuilder(text.length());

        for (char c : text.toCharArray()) {
            if (c >= 'A' && c <= 'Z')
                sb.append((char)(c + FULLWIDTH_UPPER_OFFSET));
            else if (c >= 'a' && c <= 'z')
                sb.append((char)(c + FULLWIDTH_LOWER_OFFSET));
            else if (c >= '0' && c <= '9')
                sb.append((char)(c + ('０' - '0')));
            else
                sb.append(c);
        }
        return sb.toString();
    }

    private static int parseHex(String hex)
    {
        if (hex == null)
            return 0xFFFFFF;

        String h = hex.startsWith("#") ? hex.substring(1) : hex;
        try {
            return Integer.parseUnsignedInt(h, 16) & 0xFFFFFF;
        } catch (NumberFormatException e) {
            LOGGER.warn("[MOTD] Couleur hex invalide : '{}', utilisation du blanc.", hex);
            return 0xFFFFFF;
        }
    }

    private static int interpolateRgb(int from, int to, float t)
    {
        int r1 = (from >> 16) & 0xFF, g1 = (from >> 8) & 0xFF, b1 = from & 0xFF;
        int r2 = (to   >> 16) & 0xFF, g2 = (to   >> 8) & 0xFF, b2 = to   & 0xFF;
        int r = Math.round(r1 + t * (r2 - r1));
        int g = Math.round(g1 + t * (g2 - g1));
        int b = Math.round(b1 + t * (b2 - b1));

        return (r << 16) | (g << 8) | b;
    }

    private static MotdConfig defaultConfig()
    {
        MotdConfig cfg = new MotdConfig();
        cfg.lines = new ArrayList<>();

        MotdConfig.LineConfig l1 = new MotdConfig.LineConfig();
        l1.text = "KTS Academy";
        l1.font = "small_caps";

        MotdConfig.GradientConfig g = new MotdConfig.GradientConfig();
        g.from = "#FFD700";
        g.to = "#FF4500";
        l1.gradient = g;
        cfg.lines.add(l1);
        return cfg;
    }

    private static void createDefault()
    {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            MotdConfig def = defaultConfig();

            try (Writer w = Files.newBufferedWriter(CONFIG_PATH)) {
                new Gson().toJson(def, w);
            }
            LOGGER.info("[Server] MOTd configuration file created.");
        } catch (IOException e) {
            LOGGER.error("[Server] Cannot create MOTd configuration file: {}", e.getMessage());
        }
    }
}
