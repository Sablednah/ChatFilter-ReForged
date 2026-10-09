package com.sablednah.chatfilter.neoforge;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.sablednah.chatfilter.ChatFilter;
import com.sablednah.chatfilter.ChatFilterConfig;
import com.sablednah.chatfilter.core.Action;
import com.sablednah.chatfilter.core.CategoryPolicy;
import com.sablednah.chatfilter.core.Hit;
import com.sablednah.chatfilter.core.Outcome;
import com.sablednah.chatfilter.core.Responder;
import com.sablednah.chatfilter.core.SpamGuard;
import com.sablednah.chatfilter.core.Strikes;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerPlayer;

/**
 * Where a message gets judged, and where everything that follows from the judgement happens —
 * once.
 *
 * <p>Two entry points, and the split is the design. {@link #peek} is pure: what <em>would</em>
 * happen to this text from this player. The vanilla mask asks it, because the mask is computed
 * before the chat event fires and may be asked about text that is then blocked for some other
 * reason. {@link #handle} is the real thing — permission to chat, spam, strikes, alerts, replies —
 * and is called from exactly one place per message: the chat event on a vanilla server, Standards'
 * seam when Standards is delivering, or the command event for a command. That is what keeps one
 * swear from costing two strikes.</p>
 */
public final class FilterService {

    /** The decider used when a player without {@code chatfilter.chat} speaks. */
    public static final String NO_CHAT = "chat";

    private static final SpamGuard SPAM = new SpamGuard();
    private static final Strikes STRIKES = new Strikes();
    private static final Responder RESPONDER = new Responder();

    /** A reply waiting for its tick. */
    private record Pending(int dueTick, List<String> replies, Map<String, String> values) {}

    private static final java.util.ArrayDeque<Pending> PENDING = new java.util.ArrayDeque<>();

    private FilterService() {}

    /** What would happen, with no side effects. Bypass and the links permission are honoured. */
    public static Outcome peek(ServerPlayer player, String text) {
        Rules rules = Rules.current();
        if (ChatFilterPermissions.has(player, ChatFilterPermissions.BYPASS)) {
            return Outcome.pass(List.of());
        }
        return rules.judge.judge(text, hits(rules, player, text));
    }

    /**
     * Judge a message and act on it.
     *
     * @param channel where it was said, for logs and alerts: {@code chat}, {@code private},
     *                {@code /f chat}, …
     * @param conversational whether keyword replies may fire — public chat only
     */
    public static Outcome handle(ServerPlayer player, String text, String channel, boolean conversational) {
        Rules rules = Rules.current();
        if (!ChatFilterPermissions.has(player, ChatFilterPermissions.CHAT)) {
            Messages.tell(player, ChatFilterConfig.MSG_NO_CHAT.get(), Map.of("player", name(player)));
            return new Outcome(Outcome.Kind.BLOCK, null, List.of(), NO_CHAT, List.of());
        }
        long now = System.currentTimeMillis();
        if (ChatFilterPermissions.has(player, ChatFilterPermissions.BYPASS)) {
            if (conversational) respond(player, text, rules, now);
            return Outcome.pass(List.of());
        }

        List<Hit> hits = new ArrayList<>(hits(rules, player, text));
        String spam = SPAM.check(player.getUUID(), text, now,
                enabled(rules, "repeat") ? ChatFilterConfig.REPEAT_SECONDS.get() : 0,
                ChatFilterConfig.FLOOD_MESSAGES.get(),
                enabled(rules, "flood") ? ChatFilterConfig.FLOOD_SECONDS.get() : 0);
        if (spam != null) {
            hits.add(new Hit(spam, null, spam));
        }

        Outcome outcome = rules.judge.judge(text, hits);
        report(player, text, channel, outcome, rules, now);

        if (!outcome.stops()) {
            SPAM.delivered(player.getUUID(), text, now);
            if (conversational) respond(player, text, rules, now);
        }
        return outcome;
    }

    /** Forget a player's spam history — on logout, so a reconnect is not a repeat. */
    public static void forget(ServerPlayer player) {
        SPAM.forget(player.getUUID());
    }

    public static int strikes(java.util.UUID player) {
        return STRIKES.count(player, System.currentTimeMillis(), decayMillis());
    }

    public static void clearStrikes(java.util.UUID player) {
        STRIKES.clear(player);
    }

    // ------------------------------------------------------------------ internals

    private static List<Hit> hits(Rules rules, ServerPlayer player, String text) {
        List<Hit> hits = rules.analyzer.analyze(text);
        if (!hits.isEmpty() && ChatFilterPermissions.has(player, ChatFilterPermissions.LINKS)) {
            hits = hits.stream().filter(h -> !h.category().equals("links")).toList();
        }
        return hits;
    }

    private static boolean enabled(Rules rules, String category) {
        return rules.judge.policy(category).enabled();
    }

    /** Console, staff, strikes and the sender's message — everything a caught message sets off. */
    private static void report(ServerPlayer player, String text, String channel, Outcome outcome,
            Rules rules, long now) {
        // Only hits in enabled categories count; a disabled category's findings are noise.
        List<Hit> counted = outcome.hits().stream()
                .filter(h -> rules.judge.policy(h.category()).enabled()).toList();
        if (counted.isEmpty()) return;

        Set<String> categories = counted.stream().map(Hit::category)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        String terms = counted.stream().map(Hit::term).distinct().collect(Collectors.joining(", "));
        String who = name(player);

        if (ChatFilterConfig.ALERT_CONSOLE.get()) {
            ChatFilter.LOGGER.info("[ChatFilter] {} ({}, {} -> {}): {}", who, channel,
                    String.join("+", categories), outcome.kind(), text);
        }

        MinecraftServer server = player.level().getServer();
        boolean alert = categories.stream().anyMatch(c -> rules.judge.policy(c).alert());
        if (alert && ChatFilterConfig.ALERT_STAFF.get() && server != null) {
            String template = Messages.fill(ChatFilterConfig.MSG_ALERT.get(), Map.of(
                    "player", who, "channel", channel, "category", String.join("+", categories),
                    "term", terms));
            Component line = Messages.literalInto(template, "message", text);
            for (ServerPlayer staff : server.getPlayerList().getPlayers()) {
                if (staff != player && ChatFilterPermissions.receivesAlerts(staff)) {
                    staff.sendSystemMessage(line);
                }
            }
        }

        // The sender, if the winning category says so — and never for the silent actions.
        if (outcome.decider() != null && (outcome.kind() == Outcome.Kind.BLOCK
                || outcome.kind() == Outcome.Kind.REWRITE)) {
            CategoryPolicy p = rules.judge.policy(outcome.decider());
            if (p.tellSender() && (p.action() == Action.BLOCK || p.action() == Action.REPLACE)) {
                Messages.tell(player, p.message(), Map.of("player", who));
            }
        }

        if (ChatFilterConfig.STRIKES_ENABLED.get() && server != null) {
            int add = categories.stream().mapToInt(c -> rules.judge.policy(c).strikes()).sum();
            if (add > 0) {
                int[] total = new int[1];
                List<Strikes.Step> crossed = STRIKES.add(player.getUUID(), add, now, decayMillis(),
                        rules.ladder, total);
                for (Strikes.Step step : crossed) {
                    runStep(server, player, step, total[0]);
                }
            }
        }
    }

    private static void runStep(MinecraftServer server, ServerPlayer player, Strikes.Step step, int total) {
        Map<String, String> values = Map.of("player", name(player), "strikes", String.valueOf(total));
        switch (step.kind()) {
            case "alert" -> {
                Component line = Messages.colour(Messages.fill(ChatFilterConfig.MSG_STRIKE_ALERT.get(), values));
                for (ServerPlayer staff : server.getPlayerList().getPlayers()) {
                    if (ChatFilterPermissions.receivesAlerts(staff)) staff.sendSystemMessage(line);
                }
                ChatFilter.LOGGER.info("[ChatFilter] {} has {} strikes", name(player), total);
            }
            case "tell" -> Messages.tell(player, step.value(), values);
            case "command" -> {
                String command = Messages.fill(step.value(), values);
                // Next tick, not now: this can run in the middle of the chat or command it is
                // reacting to, and a kick mid-packet is how a server ends up with a half-handled
                // connection.
                server.schedule(new TickTask(server.getTickCount() + 1, () -> {
                    ChatFilter.LOGGER.info("[ChatFilter] strike {} for {}: /{}", total, name(player), command);
                    server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
                }));
            }
            default -> { }
        }
    }

    private static void respond(ServerPlayer player, String text, Rules rules, long now) {
        if (!ChatFilterConfig.RESPONSES_ENABLED.get() || rules.responses.isEmpty()) return;
        List<String> replies = RESPONDER.respond(text, rules.responses, now, ChatFilterConfig.RESPONSE_COOLDOWN.get());
        if (replies.isEmpty()) return;
        MinecraftServer server = player.level().getServer();
        if (server == null) return;
        // Queued and released from the server tick, NOT server.schedule(new TickTask(tick + 1)):
        // the server runs tick-dated tasks early whenever it has time to spare, while vanilla
        // delivers the chat line itself through a queued task of its own, so "next tick" lost the
        // race and the reply arrived before the line it answers. Found in a live test.
        synchronized (PENDING) {
            PENDING.addLast(new Pending(server.getTickCount() + ChatFilterConfig.RESPONSE_DELAY.get(),
                    replies, Map.of("player", name(player))));
        }
    }

    /** Release any replies that are due. Called at the end of every server tick. */
    public static void tick(MinecraftServer server) {
        List<Pending> due = new ArrayList<>();
        synchronized (PENDING) {
            while (!PENDING.isEmpty() && PENDING.peekFirst().dueTick() <= server.getTickCount()) {
                due.add(PENDING.pollFirst());
            }
        }
        for (Pending p : due) {
            for (String reply : p.replies()) {
                server.getPlayerList().broadcastSystemMessage(Messages.colour(Messages.fill(reply, p.values())), false);
            }
        }
    }

    private static long decayMillis() {
        return ChatFilterConfig.STRIKE_DECAY_MINUTES.get() * 60_000L;
    }

    static String name(ServerPlayer player) {
        return player.getName().getString();
    }
}
