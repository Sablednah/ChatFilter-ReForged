package com.sablednah.chatfilter.neoforge;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContextBuilder;
import com.mojang.brigadier.context.ParsedCommandNode;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.sablednah.chatfilter.ChatFilterConfig;
import com.sablednah.chatfilter.core.Outcome;
import com.sablednah.chatfilter.integration.StandardsBridge;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.MessageArgument;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.CommandEvent;

/**
 * Chat that arrives as a command — {@code /msg}, {@code /r}, {@code /me}, {@code /f chat} and
 * whatever else is listed.
 *
 * <h2>Only the message is filtered</h2>
 *
 * <p>The old plugin filtered the whole command line, player names included, so {@code /msg
 * Assassin hi} tripped the filter on the name. Here the command is <em>parsed</em> first and only
 * its free-text arguments — a {@link MessageArgument} like vanilla's {@code /msg}, or a greedy
 * string like Standards' {@code /r} — are judged. A listed command with no such argument falls back
 * to the old behaviour: everything after the listed words.</p>
 *
 * <h2>Changing a command</h2>
 *
 * <p>A censored command is rebuilt with the censored text and parsed again, and the new parse is
 * what runs. That is safe for signed commands too: vanilla's {@code /msg} takes the parsed text as
 * the message's displayed content and keeps the signature for what was typed, the same way a
 * decorated chat line works. A masked word that vanilla is going to deliver itself is left alone
 * instead — the client's filter mask hides it, so the line stays signed and the sender still sees
 * their own words.</p>
 */
public final class CommandScreen {

    /** Standards' own message commands. With its filter seam active, it screens these itself. */
    private static final List<String> STANDARDS_SCREENED = List.of(
            "com.sablednah.standards.neoforge.commands.MessageCommands",
            "com.sablednah.standards.neoforge.commands.MailCommands");

    private CommandScreen() {}

    /** One free-text argument in a command line. */
    record Segment(StringRange range, boolean message) {}

    public static void screen(CommandEvent event) {
        if (!ChatFilterConfig.COMMANDS_ENABLED.get()) return;
        ParseResults<CommandSourceStack> parse = event.getParseResults();
        CommandSourceStack source = parse.getContext().getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) return; // console and command blocks say what their owner wrote

        Command<CommandSourceStack> executor = executor(parse);
        if (executor == null || !parse.getExceptions().isEmpty()) {
            return; // it will not run; vanilla reports the error, there is nothing to filter
        }
        String input = parse.getReader().getString();
        Rules rules = Rules.current();
        int listedEnd = listed(input, rules.commands);
        List<Segment> segments = freeText(parse);
        boolean auto = ChatFilterConfig.COMMANDS_AUTO_DETECT.get()
                && segments.stream().anyMatch(Segment::message);
        if (listedEnd < 0 && !auto) return;

        if (StandardsBridge.active() && ownedByStandards(executor)) {
            return; // Standards asks us itself, per viewer, through its seam
        }
        if (segments.isEmpty()) {
            int start = Math.min(input.length(), listedEnd);
            while (start < input.length() && Character.isWhitespace(input.charAt(start))) start++;
            if (start >= input.length()) return;
            segments = List.of(new Segment(new StringRange(start, input.length()), false));
        }

        boolean vanillaDelivers = executor.getClass().getName().startsWith("net.minecraft.");
        String channel = "/" + root(parse, input);
        StringBuilder rebuilt = new StringBuilder(input);
        boolean changed = false;
        // Right to left, so an earlier range is still valid after a later one changes length.
        List<Segment> ordered = new ArrayList<>(segments);
        ordered.sort((a, b) -> Integer.compare(b.range().getStart(), a.range().getStart()));
        for (Segment seg : ordered) {
            String text = seg.range().get(input);
            if (text.isBlank()) continue;
            Outcome outcome = FilterService.handle(player, text, channel, false);
            switch (outcome.kind()) {
                case BLOCK, SHADOW -> {
                    // A command cannot pretend to have run, so a shadow is a silent block here.
                    event.setCanceled(true);
                    return;
                }
                case REWRITE -> {
                    rebuilt.replace(seg.range().getStart(), seg.range().getEnd(), outcome.text());
                    changed = true;
                }
                case MASK -> {
                    if (!(seg.message() && vanillaDelivers)) {
                        rebuilt.replace(seg.range().getStart(), seg.range().getEnd(),
                                outcome.censoredText(text));
                        changed = true;
                    }
                }
                case PASS -> { }
            }
        }
        if (changed) {
            var dispatcher = source.getServer().getCommands().getDispatcher();
            event.setParseResults(dispatcher.parse(rebuilt.toString(), source));
        }
    }

    /** The command that will run, or null if the parse does not reach one. */
    static Command<CommandSourceStack> executor(ParseResults<CommandSourceStack> parse) {
        CommandContextBuilder<CommandSourceStack> ctx = parse.getContext();
        Command<CommandSourceStack> command = ctx.getCommand();
        while (ctx.getChild() != null) {
            ctx = ctx.getChild();
            if (ctx.getCommand() != null) command = ctx.getCommand();
        }
        return command;
    }

    /** Every free-text argument, through redirects ({@code /tell} is a redirect to {@code /msg}). */
    static List<Segment> freeText(ParseResults<CommandSourceStack> parse) {
        List<Segment> out = new ArrayList<>();
        for (CommandContextBuilder<CommandSourceStack> ctx = parse.getContext(); ctx != null; ctx = ctx.getChild()) {
            for (ParsedCommandNode<CommandSourceStack> node : ctx.getNodes()) {
                if (node.getNode() instanceof ArgumentCommandNode<?, ?> arg) {
                    ArgumentType<?> type = arg.getType();
                    if (type instanceof MessageArgument) {
                        out.add(new Segment(node.getRange(), true));
                    } else if (type instanceof StringArgumentType s
                            && s.getType() == StringArgumentType.StringType.GREEDY_PHRASE) {
                        out.add(new Segment(node.getRange(), false));
                    }
                }
            }
        }
        return out;
    }

    /**
     * Where the listed entry this command matches ends, or -1. Entries are words compared from the
     * start, so {@code f chat} matches {@code /f chat hello} but not {@code /f chatter}.
     */
    static int listed(String input, List<List<String>> entries) {
        String[] typed = input.strip().split("\\s+");
        if (typed.length == 0) return -1;
        // "minecraft:msg" is "msg".
        typed[0] = typed[0].substring(typed[0].indexOf(':') + 1);
        for (List<String> entry : entries) {
            if (entry.size() > typed.length) continue;
            boolean match = true;
            for (int i = 0; i < entry.size(); i++) {
                if (!typed[i].toLowerCase(Locale.ROOT).equals(entry.get(i))) {
                    match = false;
                    break;
                }
            }
            if (match) {
                return endOfWords(input, entry.size());
            }
        }
        return -1;
    }

    private static int endOfWords(String input, int words) {
        int i = 0;
        for (int w = 0; w < words; w++) {
            while (i < input.length() && Character.isWhitespace(input.charAt(i))) i++;
            while (i < input.length() && !Character.isWhitespace(input.charAt(i))) i++;
        }
        return i;
    }

    private static String root(ParseResults<CommandSourceStack> parse, String input) {
        var nodes = parse.getContext().getNodes();
        if (!nodes.isEmpty() && nodes.get(0).getNode() instanceof LiteralCommandNode<?> lit) {
            return lit.getLiteral();
        }
        int sp = input.indexOf(' ');
        return sp < 0 ? input : input.substring(0, sp);
    }

    private static boolean ownedByStandards(Command<CommandSourceStack> executor) {
        String name = executor.getClass().getName();
        return STANDARDS_SCREENED.stream().anyMatch(name::startsWith);
    }
}
