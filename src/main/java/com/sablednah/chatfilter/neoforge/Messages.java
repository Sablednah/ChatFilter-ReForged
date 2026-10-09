package com.sablednah.chatfilter.neoforge;

import java.util.Map;

import com.sablednah.chatfilter.ChatFilterConfig;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerPlayer;

/**
 * Owner-written text to components: {@code &} colour codes, {@code &#rrggbb}, and placeholders.
 *
 * <p>Colour codes are resolved here, server-side, so they reach unmodified clients. Player text is
 * never passed through {@link #colour} — only the owner's templates are — so a player cannot paint
 * their message by typing {@code &c}.</p>
 */
public final class Messages {

    private Messages() {}

    /** Fill {@code {key}} placeholders. {@code %N}, the old plugin's name placeholder, still works. */
    public static String fill(String template, Map<String, String> values) {
        String out = template;
        for (var e : values.entrySet()) {
            out = out.replace("{" + e.getKey() + "}", e.getValue());
        }
        if (values.containsKey("player")) {
            out = out.replace("%N", values.get("player"));
        }
        return out;
    }

    /** Send a templated line with the configured prefix. A blank template sends nothing. */
    public static void tell(ServerPlayer player, String template, Map<String, String> values) {
        if (template == null || template.isBlank()) return;
        player.sendSystemMessage(prefixed(fill(template, values)));
    }

    public static Component prefixed(String text) {
        return colour(ChatFilterConfig.PREFIX.get() + text);
    }

    /**
     * {@code &}-coded text to a component. A literal value placed into a template (a player's
     * message in a staff alert) should be added with {@link #literalInto} instead, or its own
     * {@code &}s would be read as codes.
     */
    public static MutableComponent colour(String text) {
        MutableComponent out = Component.empty();
        Style style = Style.EMPTY;
        StringBuilder run = new StringBuilder();
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '&' && i + 1 < text.length()) {
                char code = Character.toLowerCase(text.charAt(i + 1));
                if (code == '#' && i + 8 <= text.length() && isHex(text, i + 2, i + 8)) {
                    flush(out, run, style);
                    style = Style.EMPTY.withColor(TextColor.fromRgb(Integer.parseInt(text.substring(i + 2, i + 8), 16)));
                    i += 8;
                    continue;
                }
                ChatFormatting f = ChatFormatting.getByCode(code);
                if (f != null) {
                    flush(out, run, style);
                    style = f == ChatFormatting.RESET ? Style.EMPTY
                            : f.isColor() ? Style.EMPTY.withColor(f) : style.applyFormat(f);
                    i += 2;
                    continue;
                }
            }
            run.append(c);
            i++;
        }
        flush(out, run, style);
        return out;
    }

    /** A template with one placeholder carrying text that must stay literal. */
    public static MutableComponent literalInto(String template, String key, String literal) {
        String marker = "{" + key + "}";
        int at = template.indexOf(marker);
        if (at < 0) return colour(template);
        MutableComponent head = colour(template.substring(0, at));
        // The literal inherits the colour in force where it sits, which is what the owner meant
        // by writing "&f{message}".
        Style tail = lastStyle(head);
        head.append(Component.literal(literal).withStyle(tail));
        head.append(colour(template.substring(at + marker.length())));
        return head;
    }

    private static Style lastStyle(MutableComponent c) {
        var siblings = c.getSiblings();
        return siblings.isEmpty() ? c.getStyle() : siblings.get(siblings.size() - 1).getStyle();
    }

    private static void flush(MutableComponent out, StringBuilder run, Style style) {
        if (run.isEmpty()) return;
        out.append(Component.literal(run.toString()).withStyle(style));
        run.setLength(0);
    }

    private static boolean isHex(String s, int from, int to) {
        for (int k = from; k < to; k++) {
            if (Character.digit(s.charAt(k), 16) < 0) return false;
        }
        return true;
    }
}
