package com.sablednah.chatfilter;

import java.util.List;

import com.sablednah.chatfilter.core.Action;
import com.sablednah.chatfilter.core.CensorStyle;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Everything an owner can change, in {@code config/chatfilter-common.toml}.
 *
 * <p>Sensible defaults, highly configurable: out of the box it masks the classic word list
 * silently, blocks links and floods, quietens shouting, filters the usual private-message commands
 * and still answers "eleven". Every list can be emptied — including the triggers, which the old
 * plugin would quietly put back on every load.</p>
 *
 * <p>Edits apply on save; {@code /chatfilter reload} is there to say so and to report mistakes.</p>
 */
public final class ChatFilterConfig {

    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    /** The settings every category shares. */
    public static final class Category {
        public final ModConfigSpec.BooleanValue enabled;
        public final ModConfigSpec.EnumValue<Action> action;
        public final ModConfigSpec.BooleanValue tellSender;
        public final ModConfigSpec.ConfigValue<String> message;
        public final ModConfigSpec.IntValue strikes;
        public final ModConfigSpec.BooleanValue alertStaff;

        Category(boolean on, Action act, boolean tell, String msg, int strikeCount, boolean alert,
                String actionNote) {
            enabled = B.comment("Whether this category runs at all.").define("enabled", on);
            action = B.comment(
                    "What happens to a message it catches.",
                    "  LOG     - record it (console, staff alerts, strikes) and change nothing",
                    "  MASK    - hide the caught words from everyone but the sender, through the client's",
                    "            own chat filter: other players see ### (hover: filtered by the server), the",
                    "            sender sees what they typed, and chat stays signed. Silent.",
                    "  REPLACE - rewrite the caught words for everyone, using [censor]",
                    "  SHADOW  - the sender sees it sent and nobody else sees it at all. Silent. Chat only;",
                    "            a command caught this way is quietly not run.",
                    "  BLOCK   - stop it",
                    actionNote).defineEnum("action", act);
            tellSender = B.comment("Send the sender 'message' when this category REPLACEs or BLOCKs.",
                    "MASK and SHADOW never tell anybody - that is the point of them.").define("tellSender", tell);
            message = B.comment("What the sender is told. Blank says nothing. {player} (or the old %N) is their name;",
                    "& colour codes work.").define("message", msg);
            strikes = B.comment("Strikes added each time. 0 for none. See [strikes].").defineInRange("strikes", strikeCount, 0, 100);
            alertStaff = B.comment("Tell staff with alerts on (permission chatfilter.alerts).").define("alertStaff", alert);
        }
    }

    /** A category with word lists. */
    public static final class Words {
        public final Category category;
        public final ModConfigSpec.ConfigValue<List<? extends String>> anywhere;
        public final ModConfigSpec.ConfigValue<List<? extends String>> words;
        public final ModConfigSpec.ConfigValue<List<? extends String>> patterns;
        public final ModConfigSpec.ConfigValue<List<? extends String>> allowed;

        Words(boolean on, Action act, boolean tell, String msg, int strikeCount, boolean alert,
                List<String> anywhereDefault, List<String> wordsDefault, List<String> allowedDefault) {
            category = new Category(on, act, tell, msg, strikeCount, alert, "");
            anywhere = B.comment("Caught anywhere, even inside another word. Best for words that are never innocent.",
                    "Leetspeak, accents, look-alike letters, stretched letters (fuuuck) and spelled-out",
                    "letters (f.u.c.k) are all handled - list the plain word once.")
                    .defineListAllowEmpty("anywhere", anywhereDefault, () -> "", ChatFilterConfig::isString);
            words = B.comment("Caught only as a whole word, plus the endings in [matching] wordSuffixes. For words",
                    "that hide inside innocent ones: 'ass' here leaves 'class' and 'grass' alone.")
                    .defineListAllowEmpty("words", wordsDefault, () -> "", ChatFilterConfig::isString);
            patterns = B.comment("Regular expressions, matched case-insensitively against the accent-folded text.",
                    "For anything the two lists cannot say. A broken pattern is reported by /chatfilter reload",
                    "and skipped; the rest keep working.")
                    .defineListAllowEmpty("patterns", List.of(), () -> "", ChatFilterConfig::isString);
            allowed = B.comment("Never caught, even with a listed word inside them - the Scunthorpe problem.")
                    .defineListAllowEmpty("allowed", allowedDefault, () -> "", ChatFilterConfig::isString);
        }
    }

    // ------------------------------------------------------------------ matching
    public static final ModConfigSpec.BooleanValue STRIP_ACCENTS;
    public static final ModConfigSpec.BooleanValue LOOKALIKES;
    public static final ModConfigSpec.BooleanValue STRIP_COLOUR_CODES;
    public static final ModConfigSpec.IntValue JOIN_SPELLED_OUT;
    public static final ModConfigSpec.BooleanValue LEETSPEAK;
    public static final ModConfigSpec.BooleanValue WILDCARDS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> WORD_SUFFIXES;

    // ------------------------------------------------------------------ censor
    public static final ModConfigSpec.EnumValue<CensorStyle> CENSOR_STYLE;
    public static final ModConfigSpec.ConfigValue<String> CENSOR_TEXT;

    // ------------------------------------------------------------------ categories
    public static final Words PROFANITY;
    public static final Words SEVERE;
    public static final Category LINKS;
    public static final ModConfigSpec.BooleanValue LINK_IPS;
    public static final ModConfigSpec.BooleanValue LINK_SPELLED_DOTS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> LINK_TLDS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> LINK_ALLOWED;
    public static final Category CAPS;
    public static final ModConfigSpec.IntValue CAPS_MIN_LETTERS;
    public static final ModConfigSpec.IntValue CAPS_PERCENT;
    public static final Category REPEAT;
    public static final ModConfigSpec.IntValue REPEAT_SECONDS;
    public static final Category FLOOD;
    public static final ModConfigSpec.IntValue FLOOD_MESSAGES;
    public static final ModConfigSpec.IntValue FLOOD_SECONDS;

    // ------------------------------------------------------------------ strikes, alerts
    public static final ModConfigSpec.BooleanValue STRIKES_ENABLED;
    public static final ModConfigSpec.IntValue STRIKE_DECAY_MINUTES;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> STRIKE_LADDER;
    public static final ModConfigSpec.BooleanValue ALERT_CONSOLE;
    public static final ModConfigSpec.BooleanValue ALERT_STAFF;

    // ------------------------------------------------------------------ commands
    public static final ModConfigSpec.BooleanValue COMMANDS_ENABLED;
    public static final ModConfigSpec.BooleanValue COMMANDS_AUTO_DETECT;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> COMMANDS;

    // ------------------------------------------------------------------ responses
    public static final ModConfigSpec.BooleanValue RESPONSES_ENABLED;
    public static final ModConfigSpec.IntValue RESPONSE_COOLDOWN;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> RESPONSES;

    // ------------------------------------------------------------------ messages
    public static final ModConfigSpec.ConfigValue<String> PREFIX;
    public static final ModConfigSpec.ConfigValue<String> MSG_NO_CHAT;
    public static final ModConfigSpec.ConfigValue<String> MSG_ALERT;
    public static final ModConfigSpec.ConfigValue<String> MSG_STRIKE_ALERT;

    public static final ModConfigSpec SPEC;

    static {
        B.comment("How hard the filter looks. All of these apply to every word list.").push("matching");
        STRIP_ACCENTS = B.comment("Fold accented letters to their base, so 'shït' is 'shit'. Turn off if your",
                "players' language makes that fold innocent words rude.").define("stripAccents", true);
        LOOKALIKES = B.comment("Fold look-alike letters from other alphabets (Cyrillic 'а' for 'a') and the",
                "'fancy text' alphabets people paste.").define("lookalikes", true);
        STRIP_COLOUR_CODES = B.comment("Ignore & colour codes inside words ('f&cuck').").define("stripColourCodes", true);
        JOIN_SPELLED_OUT = B.comment("Join words spelled out letter by letter ('s h i t', 's.h.i.t') when at least",
                "this many single letters are in a row. 0 turns it off.").defineInRange("joinSpelledOut", 3, 0, 20);
        LEETSPEAK = B.comment("Read 3 as e, 0 as o, $ as s, @ as a and so on - the old 'aggressiveMatching'.")
                .define("leetspeak", true);
        WILDCARDS = B.comment("Read * in place of a vowel as that vowel: 'f*ck', 'sh*t'.").define("wildcards", true);
        WORD_SUFFIXES = B.comment("Endings a whole-word entry also catches: 'boob' catches 'boobs'.")
                .defineListAllowEmpty("wordSuffixes", List.of("s", "es", "ed", "er", "ers", "ing", "y"),
                        () -> "", ChatFilterConfig::isString);
        B.pop();

        B.comment("How REPLACE writes over a word.").push("censor");
        CENSOR_STYLE = B.comment("FIXED - the whole word becomes 'text'", "REPEAT - every letter becomes the first character of 'text'",
                "GRAWLIX - cartoon swearing the length of the word: @#$%").defineEnum("style", CensorStyle.FIXED);
        CENSOR_TEXT = B.comment("The replacement. The old plugin's default.").define("text", "!@$#");
        B.pop();

        B.comment("Everyday swearing. The old plugin's lists, masked silently by default.").push("profanity");
        PROFANITY = new Words(true, Action.MASK, true, "Oi {player}! Mind your language!", 1, true,
                List.of("shit", "fuck", "piss", "cunt"),
                List.of("ass", "arse", "poo", "boob", "crap", "twat", "wank", "bollocks", "bitch"),
                List.of("scunthorpe", "shiitake", "shitake"));
        B.pop();

        B.comment("Words that should stop a message outright, and cost more. Empty by default - every",
                "server's list is different, and nobody should get one they did not choose.").push("severe");
        SEVERE = new Words(true, Action.BLOCK, true, "That one is not welcome here.", 3, true,
                List.of(), List.of(), List.of());
        B.pop();

        B.comment("Web addresses and IP addresses.").push("links");
        LINKS = new Category(true, Action.BLOCK, true, "Links need a word with staff first.", 0, true,
                "MASK hides just the address. Players with chatfilter.links may always post links.");
        LINK_IPS = B.comment("Also catch IP addresses (and their :port).").define("ips", true);
        LINK_SPELLED_DOTS = B.comment("Also catch 'example dot com', 'example(.)com' and friends.").define("spelledDots", true);
        LINK_TLDS = B.comment("Endings that make 'word.ending' a link. Kept to ones people actually post. Endings that are",
                "also everyday words (.me .to .it .in .no .at .be .us) are left out, because 'did it.no' is a",
                "missing space, not a link - add any your players do post.")
                .defineListAllowEmpty("tlds", List.of("com", "net", "org", "io", "gg", "co", "uk", "de", "tv",
                        "xyz", "info", "biz", "ru", "fr", "eu", "nl", "ca", "au", "ly", "dev", "app", "club",
                        "online", "site", "store", "fun", "pro", "top", "link", "live", "shop", "space",
                        "world", "cc", "ws", "su", "pl", "br", "jp", "cn", "ch", "fi", "dk", "nz", "ie",
                        "lol", "host", "network", "games", "sex", "porn", "xxx"), () -> "", ChatFilterConfig::isString);
        LINK_ALLOWED = B.comment("Domains anybody may post, with their subdomains: put your own website here.")
                .defineListAllowEmpty("allowedDomains", List.of("minecraft.net", "minecraft.wiki"),
                        () -> "", ChatFilterConfig::isString);
        B.pop();

        B.comment("SHOUTING.").push("caps");
        CAPS = new Category(true, Action.REPLACE, false, "", 0, false, "REPLACE lowercases the line.");
        CAPS_MIN_LETTERS = B.comment("Lines with fewer letters are never shouting: 'GG', 'OK', 'LOL'.")
                .defineInRange("minLetters", 8, 1, 256);
        CAPS_PERCENT = B.comment("Share of capital letters, out of all letters, above which a line is shouting.")
                .defineInRange("maxPercent", 70, 1, 100);
        B.pop();

        B.comment("Saying the same thing again.").push("repeat");
        REPEAT = new Category(true, Action.BLOCK, true, "You said that already. Everyone heard.", 0, false,
                "MASK and REPLACE mean nothing for a repeat and act as LOG.");
        REPEAT_SECONDS = B.comment("A message the same as your last one within this long is a repeat. 0 turns it off.")
                .defineInRange("seconds", 30, 0, 3600);
        B.pop();

        B.comment("Too many messages too fast.").push("flood");
        FLOOD = new Category(true, Action.BLOCK, true, "Slow down. Chat will still be here in a moment.", 0, true,
                "MASK and REPLACE mean nothing for a flood and act as LOG.");
        FLOOD_MESSAGES = B.comment("More than this many messages ...").defineInRange("messages", 5, 1, 100);
        FLOOD_SECONDS = B.comment("... within this many seconds is a flood. 0 turns it off.").defineInRange("seconds", 4, 0, 600);
        B.pop();

        B.comment("Escalation for repeat offenders. Strikes expire one at a time, so an occasional slip never",
                "adds up.").push("strikes");
        STRIKES_ENABLED = B.define("enabled", true);
        STRIKE_DECAY_MINUTES = B.comment("How long one strike lasts.").defineInRange("decayMinutes", 30, 1, 10080);
        STRIKE_LADDER = B.comment("What happens on reaching a number of strikes - each fires once, on the way up.",
                "  N = alert              tell staff with alerts on",
                "  N = tell <message>     tell the player",
                "  N = command <command>  run a command as the server. {player} and {strikes} are filled in.",
                "Examples:",
                "  \"5 = command mute {player} 10m Language\"   (Standards' /mute)",
                "  \"8 = command kick {player} Mind your language\"")
                .defineListAllowEmpty("ladder", List.of("3 = alert"), () -> "", ChatFilterConfig::isString);
        B.pop();

        B.comment("Who hears about a caught message.").push("alerts");
        ALERT_CONSOLE = B.comment("Log caught messages to the console - the old 'showInConsole'.").define("console", true);
        ALERT_STAFF = B.comment("Tell online staff with permission chatfilter.alerts (each can turn it off with",
                "/chatfilter alerts off).").define("staff", true);
        B.pop();

        B.comment("Chat that arrives as a command - the old plugin's best-loved feature.").push("commands");
        COMMANDS_ENABLED = B.define("enabled", true);
        COMMANDS_AUTO_DETECT = B.comment("Also filter the message of any command that takes one the way /msg and /say do,",
                "listed or not.").define("autoDetect", true);
        COMMANDS = B.comment("Commands to filter, without the slash. An entry can be several words ('f chat'). Only the",
                "message part is filtered where it can be found, so a player called Assassin can still be /msg'd;",
                "otherwise everything after the entry is.")
                .defineListAllowEmpty("list", List.of("msg", "tell", "w", "whisper", "pm", "m", "r", "reply",
                        "me", "say", "teammsg", "tm", "mail send", "f chat", "faction chat", "party chat", "p chat"),
                        () -> "", ChatFilterConfig::isString);
        B.pop();

        B.comment("Automatic replies to keywords. 'trigger|other trigger = reply'. Whole words only.").push("responses");
        RESPONSES_ENABLED = B.define("enabled", true);
        RESPONSE_COOLDOWN = B.comment("Seconds before the same reply can fire again.").defineInRange("cooldownSeconds", 30, 0, 86400);
        RESPONSES = B.comment("Empty the list to have none. It stays empty.")
                .defineListAllowEmpty("rules", List.of("eleven|11 = That's ridiculous, it's not even funny."),
                        () -> "", ChatFilterConfig::isString);
        B.pop();

        B.comment("Wording. & colour codes work. Blank says nothing.").push("messages");
        PREFIX = B.comment("Put before everything ChatFilter tells a player.").define("prefix", "&9[ChatFilter] &f");
        MSG_NO_CHAT = B.comment("To a player without chatfilter.chat.").define("noChat", "I'm sorry {player}, I can't let you do that.");
        MSG_ALERT = B.comment("To staff. {player} {channel} {category} {term} {message}.")
                .define("alert", "&8[&cCF&8] &7{player} &8({channel}, {category}: {term})&7: &f{message}");
        MSG_STRIKE_ALERT = B.comment("To staff, from the strike ladder. {player} {strikes}.")
                .define("strikeAlert", "&8[&cCF&8] &7{player} &fhas {strikes} strikes.");
        B.pop();

        SPEC = B.build();
    }

    private static boolean isString(Object o) {
        return o instanceof String;
    }

    private ChatFilterConfig() {}
}
