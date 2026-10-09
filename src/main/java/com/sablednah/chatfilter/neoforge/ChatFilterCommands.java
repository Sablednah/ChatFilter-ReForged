package com.sablednah.chatfilter.neoforge;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.sablednah.chatfilter.ChatFilterConfig;
import com.sablednah.chatfilter.core.Hit;
import com.sablednah.chatfilter.core.Outcome;
import com.sablednah.chatfilter.integration.StandardsBridge;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * {@code /chatfilter} — for staff. Gated by the {@code chatfilter.admin} node rather than an op
 * level, so a permissions manager can hand it to a moderator.
 *
 * <ul>
 *     <li>{@code reload} — recompile the lists and report any mistakes in them</li>
 *     <li>{@code test <text>} — what would happen to a line, and why, without anyone seeing it</li>
 *     <li>{@code add|remove <list> <entry>}, {@code list <list>} — edit the lists from in game,
 *         asked for on the old page and saved straight to the config file</li>
 *     <li>{@code strikes <player> [clear]}</li>
 *     <li>{@code alerts [on|off|toggle]} — for anyone with {@code chatfilter.alerts}</li>
 * </ul>
 */
public final class ChatFilterCommands {

    /** Every list that can be edited in game, by the name staff type. */
    private static Map<String, ModConfigSpec.ConfigValue<List<? extends String>>> lists() {
        Map<String, ModConfigSpec.ConfigValue<List<? extends String>>> m = new LinkedHashMap<>();
        m.put("profanity.anywhere", ChatFilterConfig.PROFANITY.anywhere);
        m.put("profanity.words", ChatFilterConfig.PROFANITY.words);
        m.put("profanity.patterns", ChatFilterConfig.PROFANITY.patterns);
        m.put("profanity.allowed", ChatFilterConfig.PROFANITY.allowed);
        m.put("severe.anywhere", ChatFilterConfig.SEVERE.anywhere);
        m.put("severe.words", ChatFilterConfig.SEVERE.words);
        m.put("severe.patterns", ChatFilterConfig.SEVERE.patterns);
        m.put("severe.allowed", ChatFilterConfig.SEVERE.allowed);
        m.put("links.allowed", ChatFilterConfig.LINK_ALLOWED);
        m.put("commands", ChatFilterConfig.COMMANDS);
        m.put("responses", ChatFilterConfig.RESPONSES);
        return m;
    }

    private ChatFilterCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("chatfilter")
                .executes(ChatFilterCommands::help)
                .then(Commands.literal("reload").requires(ChatFilterPermissions::admin)
                        .executes(ChatFilterCommands::reload))
                .then(Commands.literal("test").requires(ChatFilterPermissions::admin)
                        .then(Commands.argument("text", StringArgumentType.greedyString())
                                .executes(ChatFilterCommands::test)))
                .then(Commands.literal("add").requires(ChatFilterPermissions::admin)
                        .then(Commands.argument("list", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(lists().keySet(), b))
                                .then(Commands.argument("entry", StringArgumentType.greedyString())
                                        .executes(ctx -> edit(ctx, true)))))
                .then(Commands.literal("remove").requires(ChatFilterPermissions::admin)
                        .then(Commands.argument("list", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(lists().keySet(), b))
                                .then(Commands.argument("entry", StringArgumentType.greedyString())
                                        .suggests(ChatFilterCommands::suggestEntries)
                                        .executes(ctx -> edit(ctx, false)))))
                .then(Commands.literal("list").requires(ChatFilterPermissions::admin)
                        .then(Commands.argument("list", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(lists().keySet(), b))
                                .executes(ChatFilterCommands::show)))
                .then(Commands.literal("strikes").requires(ChatFilterPermissions::admin)
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> strikes(ctx, false))
                                .then(Commands.literal("clear").executes(ctx -> strikes(ctx, true)))))
                .then(Commands.literal("alerts")
                        .requires(s -> s.getPlayer() != null
                                && ChatFilterPermissions.has(s.getPlayer(), ChatFilterPermissions.ALERTS))
                        .executes(ctx -> alerts(ctx, null))
                        .then(Commands.literal("on").executes(ctx -> alerts(ctx, Boolean.TRUE)))
                        .then(Commands.literal("off").executes(ctx -> alerts(ctx, Boolean.FALSE)))
                        .then(Commands.literal("toggle").executes(ctx -> alerts(ctx, null)))));
    }

    private static int help(CommandContext<CommandSourceStack> ctx) {
        say(ctx, "&7ChatFilter " + com.sablednah.chatfilter.core.BuildInfo.describe()
                + (StandardsBridge.active() ? " &8- screening Standards' chat too" : ""));
        if (ChatFilterPermissions.admin(ctx.getSource())) {
            say(ctx, "&7/chatfilter reload | test <text> | add/remove <list> <entry> | list <list> | strikes <player> [clear] | alerts [on|off|toggle]");
        }
        return 1;
    }

    private static int reload(CommandContext<CommandSourceStack> ctx) {
        Rules rules = Rules.rebuild();
        if (rules.problems.isEmpty()) {
            say(ctx, "&7Reloaded. No complaints. Edits to the file apply on save anyway; this is for peace of mind.");
        } else {
            say(ctx, "&eReloaded, with " + rules.problems.size() + " thing(s) skipped:");
            for (String p : rules.problems) say(ctx, "&7  " + p);
        }
        return 1;
    }

    private static int test(CommandContext<CommandSourceStack> ctx) {
        String text = StringArgumentType.getString(ctx, "text");
        Rules rules = Rules.current();
        List<Hit> hits = rules.analyzer.analyze(text);
        Outcome outcome = rules.judge.judge(text, hits);
        if (hits.isEmpty()) {
            say(ctx, "&7Nothing caught. That would go through untouched.");
            return 1;
        }
        for (Hit h : hits) {
            var p = rules.judge.policy(h.category());
            String where = h.wholeMessage() ? "the whole line"
                    : "'" + text.substring(h.span().start(), h.span().end()) + "'";
            ctx.getSource().sendSystemMessage(Messages.literalInto("&7" + h.category() + " &8("
                    + (p.enabled() ? p.action() : "off") + ")&7: {w}", "w", where + " via " + h.term()));
        }
        String label = switch (outcome.kind()) {
            case PASS -> "&7Result: logged only.";
            case MASK -> "&7Result: masked. Others see: &f{t}";
            case REWRITE -> "&7Result: rewritten to: &f{t}";
            case SHADOW -> "&7Result: shadowed. The sender sees it sent; nobody else sees it.";
            case BLOCK -> "&7Result: blocked.";
        };
        // The text is typed by a person, so it goes in literally rather than as & codes.
        ctx.getSource().sendSystemMessage(Messages.literalInto(label, "t", outcome.censoredText(text)));
        say(ctx, "&8(Spam checks and permissions are not part of a test.)");
        return 1;
    }

    private static int edit(CommandContext<CommandSourceStack> ctx, boolean add) {
        String name = StringArgumentType.getString(ctx, "list").toLowerCase(Locale.ROOT);
        String entry = StringArgumentType.getString(ctx, "entry").strip();
        var value = lists().get(name);
        if (value == null) {
            say(ctx, "&7There is no list called '" + name + "'. Try: " + String.join(", ", lists().keySet()));
            return 0;
        }
        List<String> current = new ArrayList<>(value.get().stream().map(String::valueOf).toList());
        boolean present = current.stream().anyMatch(e -> e.equalsIgnoreCase(entry));
        if (add == present) {
            say(ctx, add ? "&7'" + entry + "' is already on " + name + "."
                    : "&7'" + entry + "' was not on " + name + " to begin with.");
            return 0;
        }
        if (add) {
            current.add(entry);
        } else {
            current.removeIf(e -> e.equalsIgnoreCase(entry));
        }
        value.set(current);
        ChatFilterConfig.SPEC.save();
        Rules rules = Rules.rebuild();
        say(ctx, "&7" + (add ? "Added '" + entry + "' to " : "Removed '" + entry + "' from ") + name
                + ". Saved to the config file.");
        for (String p : rules.problems) say(ctx, "&e  " + p);
        return 1;
    }

    private static int show(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "list").toLowerCase(Locale.ROOT);
        var value = lists().get(name);
        if (value == null) {
            say(ctx, "&7There is no list called '" + name + "'. Try: " + String.join(", ", lists().keySet()));
            return 0;
        }
        List<? extends String> entries = value.get();
        if (entries.isEmpty()) {
            say(ctx, "&7" + name + " is empty.");
        } else {
            say(ctx, "&7" + name + " (" + entries.size() + "): &f"
                    + entries.stream().map(String::valueOf).collect(Collectors.joining("&7, &f")));
        }
        return entries.size();
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions>
            suggestEntries(CommandContext<CommandSourceStack> ctx,
                    com.mojang.brigadier.suggestion.SuggestionsBuilder b) {
        var value = lists().get(StringArgumentType.getString(ctx, "list").toLowerCase(Locale.ROOT));
        return value == null ? b.buildFuture()
                : SharedSuggestionProvider.suggest(value.get().stream().map(String::valueOf), b);
    }

    private static int strikes(CommandContext<CommandSourceStack> ctx, boolean clear) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        String who = target.getName().getString();
        if (clear) {
            FilterService.clearStrikes(target.getUUID());
            say(ctx, "&7" + who + "'s slate is clean.");
            return 1;
        }
        int n = FilterService.strikes(target.getUUID());
        say(ctx, n == 0 ? "&7" + who + " has no strikes." : "&7" + who + " has " + n + " strike" + (n == 1 ? "" : "s") + ".");
        return n;
    }

    /** Tri-state, like every switch in this family: {@code on} and {@code off} for a script, bare for a human. */
    private static int alerts(CommandContext<CommandSourceStack> ctx, Boolean on) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        boolean nowOn = on != null ? on : ChatFilterPermissions.alertsOff(player);
        ChatFilterPermissions.setAlertsOff(player, !nowOn);
        say(ctx, nowOn ? "&7Alerts on. You will hear about it." : "&7Alerts off. Ignorance, restored.");
        return 1;
    }

    private static void say(CommandContext<CommandSourceStack> ctx, String text) {
        ctx.getSource().sendSystemMessage(Messages.colour(text));
    }
}
