package com.sablednah.chatfilter.neoforge;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.sablednah.chatfilter.ChatFilter;
import com.sablednah.chatfilter.ChatFilterConfig;
import com.sablednah.chatfilter.core.Action;
import com.sablednah.chatfilter.core.CategoryPolicy;
import com.sablednah.chatfilter.core.CensorStyle;
import com.sablednah.chatfilter.core.Hit;
import com.sablednah.chatfilter.core.Judge;
import com.sablednah.chatfilter.core.Outcome;
import com.sablednah.chatfilter.core.Responder;
import com.sablednah.chatfilter.core.SpamGuard;
import com.sablednah.chatfilter.core.Strikes;
import com.sablednah.chatfilter.integration.StandardsBridge;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.FilteredText;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.CommandEvent;

/**
 * {@code ./gradlew runServer -Pselftest} — the only headless route to "does it actually catch
 * that", run on {@code ServerStartedEvent} against the shipped defaults.
 *
 * <p>The rules it keeps, borrowed from Standards next door: <b>call the real code</b> (a probe that
 * re-derives the matcher is testing the copy), and <b>test both directions</b> — a filter that
 * catches everything passes every "is it caught" check, so every disguise that must be caught sits
 * beside an innocent word that must not be.</p>
 */
public final class SelfTest {

    private final MinecraftServer server;
    private int passed;
    private final List<String> failed = new ArrayList<>();

    public SelfTest(MinecraftServer server) {
        this.server = server;
    }

    public void run() {
        ChatFilter.LOGGER.info("=== ChatFilter self-test ===");
        try {
            checkConfig();
            checkCaught();
            checkInnocent();
            checkLinks();
            checkCapsAndCensor();
            checkResponses();
            checkSpamAndStrikes();
            checkVanillaMask();
            checkCommands();
            checkStandards();
        } catch (RuntimeException e) {
            failed.add("threw: " + e);
            ChatFilter.LOGGER.error("ChatFilter self-test threw", e);
        }
        if (failed.isEmpty()) {
            ChatFilter.LOGGER.info("=== ChatFilter self-test PASSED ({} checks) ===", passed);
        } else {
            ChatFilter.LOGGER.error("=== ChatFilter self-test FAILED: {} of {} ===", failed.size(), passed + failed.size());
            failed.forEach(f -> ChatFilter.LOGGER.error("  ✗ {}", f));
        }
    }

    private void check(String name, boolean ok) {
        if (ok) {
            passed++;
            ChatFilter.LOGGER.info("  ✓ {}", name);
        } else {
            failed.add(name);
            ChatFilter.LOGGER.error("  ✗ {}", name);
        }
    }

    private static Rules rules() {
        return Rules.current();
    }

    private static boolean caught(String text, String category) {
        return rules().analyzer.analyze(text).stream().anyMatch(h -> h.category().equals(category));
    }

    private static String maskedForOthers(String text) {
        Outcome o = rules().judge.judge(text, rules().analyzer.analyze(text));
        return o.censoredText(text);
    }

    // ------------------------------------------------------------------ the shipped config

    private void checkConfig() {
        check("the shipped config compiles without complaint", rules().problems.isEmpty());
        check("profanity masks by default", rules().judge.policy("profanity").action() == Action.MASK);
    }

    /** Every disguise from the old page's comments, and then some. */
    private void checkCaught() {
        String[][] cases = {
                {"what the fuck", "plain"},
                {"FUCK", "capitals"},
                {"fuuuuuck", "stretched letters"},
                {"f.u.c.k", "dotted"},
                {"s h i t", "spaced"},
                {"s-h-i-t happens", "hyphenated"},
                {"sh1t", "leet 1 for i"},
                {"$h!t", "leet symbols"},
                {"5hit", "leet 5 for s"},
                {"f*ck", "self-censored vowel"},
                {"sh*t", "another self-censored vowel"},
                {"ＦＵＣＫ", "full-width letters"},
                {"shït", "accent"},
                {"fцck", "Cyrillic look-alike"},
                {"f​uck", "zero-width space"},
                {"f&cuck", "colour code inside"},
                {"motherfucker", "inside a longer word"},
                {"you ass", "whole word"},
                {"asses", "whole word with a suffix"},
                {"@ss", "whole word in leet"},
                {"crappy", "suffix after a doubled letter"},
                {"`fuck", "a leading backtick (the old plugin let this through)"},
        };
        for (String[] c : cases) {
            check("catches " + c[1] + ": " + c[0], caught(c[0], "profanity"));
        }
        check("masks exactly the word: 'what the fuck' -> 'what the ####'",
                maskedForOthers("what the fuck").equals("what the ####"));
        // The whole span, gaps included: masking only the letters would leave "# # # #", which
        // spells out exactly how long the word was and where it went.
        check("masks a spaced word as one span: 's h i t' -> '#######'",
                maskedForOthers("s h i t").equals("#######"));
        check("masks the accented original, not a folded copy: 'shït' -> '####'",
                maskedForOthers("shït").equals("####"));
    }

    /** The other direction: a filter that catches these is worse than none. */
    private void checkInnocent() {
        String[] clean = {"hello there", "this hit the spot", "class", "grass is green", "assassin",
                "Scunthorpe United", "shiitake mushrooms", "pass the salt", "I assume so", "cocktail",
                "a s k me", "classic", "Mississippi", "bass guitar", "therapist", "poorly"};
        for (String c : clean) {
            check("leaves alone: " + c, !caught(c, "profanity"));
        }
    }

    private void checkLinks() {
        check("catches a link", caught("join example.com now", "links"));
        check("catches a link with a scheme and path", caught("https://example.org/page?x=1", "links"));
        check("catches 'example dot com'", caught("go to example dot com", "links"));
        check("catches 'example(.)net'", caught("example(.)net", "links"));
        check("catches an IP and port", caught("connect to 192.168.1.20:25565", "links"));
        check("allows an allowed domain", !caught("see minecraft.net", "links"));
        check("allows a subdomain of an allowed domain", !caught("see help.minecraft.net", "links"));
        check("leaves a version number alone", !caught("updated to 1.21.11", "links"));
        check("leaves 'e.g.' alone", !caught("bring food e.g. bread", "links"));
        check("leaves an impossible IP alone", !caught("300.1.1.1", "links"));
        check("leaves a missing space alone", !caught("I did it.no way", "links"));
        Outcome link = rules().judge.judge("join example.com", rules().analyzer.analyze("join example.com"));
        check("a link is blocked by default", link.kind() == Outcome.Kind.BLOCK && "links".equals(link.decider()));
    }

    private void checkCapsAndCensor() {
        Outcome shout = rules().judge.judge("THIS IS VERY LOUD", rules().analyzer.analyze("THIS IS VERY LOUD"));
        check("shouting is lowercased", shout.kind() == Outcome.Kind.REWRITE
                && "this is very loud".equals(shout.text()));
        check("'GG' is not shouting", !caught("GG", "caps"));
        Outcome both = rules().judge.judge("WHAT THE FUCK IS THIS", rules().analyzer.analyze("WHAT THE FUCK IS THIS"));
        check("a masked word on a rewritten line becomes ### in the rewrite",
                both.kind() == Outcome.Kind.REWRITE && "what the #### is this".equals(both.text()));

        CategoryPolicy replace = new CategoryPolicy(true, Action.REPLACE, false, "", 0, false);
        List<Hit> hits = rules().analyzer.analyze("oh shit and fuck");
        check("REPLACE, FIXED style: the old plugin's !@$#", new Judge(java.util.Map.of("profanity", replace),
                CensorStyle.FIXED, "!@$#").judge("oh shit and fuck", hits).text().equals("oh !@$# and !@$#"));
        check("REPLACE, REPEAT style keeps the length", new Judge(java.util.Map.of("profanity", replace),
                CensorStyle.REPEAT, "*").judge("oh shit", rules().analyzer.analyze("oh shit")).text().equals("oh ****"));
        CategoryPolicy block = new CategoryPolicy(true, Action.BLOCK, true, "", 0, false);
        check("the firmest action wins", new Judge(java.util.Map.of("profanity", replace, "caps", block),
                CensorStyle.FIXED, "x").judge("OH SHIT THIS IS LOUD", rules().analyzer.analyze("OH SHIT THIS IS LOUD"))
                .kind() == Outcome.Kind.BLOCK);
    }

    private void checkResponses() {
        Responder r = new Responder();
        List<Responder.Rule> rulesList = rules().responses;
        check("the eleven response is shipped", rulesList.size() == 1);
        check("'eleven' gets the reply", r.respond("it goes up to eleven", rulesList, 0, 30).size() == 1);
        check("the reply waits out its cooldown", r.respond("eleven", rulesList, 1000, 30).isEmpty());
        check("'11' gets it after the cooldown", r.respond("I'm 11", rulesList, 31_000, 30).size() == 1);
        check("coordinates do not: 'x 110'", new Responder().respond("x 110 z 2011", rulesList, 0, 30).isEmpty());
        check("an empty list stays empty", new Responder().respond("eleven", List.of(), 0, 30).isEmpty());
    }

    private void checkSpamAndStrikes() {
        SpamGuard g = new SpamGuard();
        UUID p = UUID.randomUUID();
        check("a first message is fine", g.check(p, "hello", 0, 30, 5, 4) == null);
        g.delivered(p, "hello", 0);
        check("the same message again is a repeat", "repeat".equals(g.check(p, "Hello!", 1000, 30, 5, 4)));
        check("but not after the window", g.check(p, "hello", 31_000, 30, 5, 4) == null);
        for (int i = 0; i < 5; i++) g.delivered(p, "m" + i, 40_000 + i * 100);
        check("a sixth message within four seconds is a flood", "flood".equals(g.check(p, "again", 40_600, 30, 5, 4)));
        check("and is fine once things calm down", g.check(p, "again", 50_000, 30, 5, 4) == null);

        Strikes s = new Strikes();
        List<Strikes.Step> ladder = List.of(Strikes.Step.parse("3 = alert"), Strikes.Step.parse("5 = command kick {player}"));
        check("ladder entries parse", ladder.stream().allMatch(java.util.Objects::nonNull));
        check("a malformed ladder entry is refused", Strikes.Step.parse("three = alert") == null);
        s.add(p, 2, 0, 60_000, ladder, null);
        check("crossing 3 fires the alert once", s.add(p, 1, 1, 60_000, ladder, null).size() == 1);
        check("a fourth strike fires nothing new", s.add(p, 1, 2, 60_000, ladder, null).isEmpty());
        check("strikes expire", s.count(p, 70_000, 60_000) == 0);
    }

    // ------------------------------------------------------------------ vanilla integration

    private void checkVanillaMask() {
        check("the mixin is applied to ServerPlayer", hasField(ServerPlayer.class, "chatfilter$masking"));
        ServerPlayer fake = FakePlayerFactory.getMinecraft(server.overworld());
        FilteredText ft = fake.getTextFilter().processStreamMessage("what the fuck").join();
        check("vanilla's own text filter now carries our mask", ft.mask() != null && !ft.mask().isEmpty());
        check("and the mask hides exactly the word", "what the ####".equals(ft.mask().apply("what the fuck")));
        FilteredText clean = fake.getTextFilter().processStreamMessage("hello there").join();
        check("a clean line gets no mask", clean.mask().isEmpty());
        ServerPlayer other = FakePlayerFactory.get(server.overworld(),
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "cf_viewer"));
        check("a mask applies to other viewers", fake.shouldFilterMessageTo(other));
        check("but never to the sender", !fake.shouldFilterMessageTo(fake));

        int before = FilterService.strikes(fake.getUUID());
        Outcome handled = FilterService.handle(fake, "what the fuck", "selftest", false);
        check("handling a swear masks it", handled.kind() == Outcome.Kind.MASK);
        check("and costs a strike", FilterService.strikes(fake.getUUID()) == before + 1);
        FilterService.clearStrikes(fake.getUUID());
        FilterService.forget(fake);
    }

    private void checkCommands() {
        var dispatcher = server.getCommands().getDispatcher();
        CommandSourceStack console = server.createCommandSourceStack();

        var msg = dispatcher.parse("msg @s hello there friend", console);
        var segs = CommandScreen.freeText(msg);
        check("/msg's message is found", segs.size() == 1 && segs.get(0).message()
                && segs.get(0).range().get("msg @s hello there friend").equals("hello there friend"));
        var tell = dispatcher.parse("tell @s hi", console);
        check("/tell is followed through its redirect", CommandScreen.freeText(tell).size() == 1);
        check("/msg's executor is found", CommandScreen.executor(msg) != null);
        check("a bad parse has no executor", CommandScreen.executor(dispatcher.parse("msg", console)) == null);

        var entries = rules().commands;
        check("'f chat' is listed", CommandScreen.listed("f chat hello", entries) == "f chat".length());
        check("'f chatter' is not", CommandScreen.listed("f chatter hello", entries) < 0);
        check("'minecraft:msg' counts as 'msg'", CommandScreen.listed("minecraft:msg x hi", entries) > 0);

        // A real command event through the real screen: a listed command with a greedy message.
        dispatcher.register(Commands.literal("cfselftest")
                .then(Commands.argument("words", StringArgumentType.greedyString()).executes(c -> 1)));
        ServerPlayer fake = FakePlayerFactory.getMinecraft(server.overworld());
        var original = new ArrayList<String>(ChatFilterConfig.COMMANDS.get().stream().map(String::valueOf).toList());
        try {
            var withTest = new ArrayList<>(original);
            withTest.add("cfselftest");
            ChatFilterConfig.COMMANDS.set(withTest);
            Rules.rebuild();
            CommandSourceStack src = fake.createCommandSourceStack();
            CommandEvent event = new CommandEvent(dispatcher.parse("cfselftest well fuck that", src));
            CommandScreen.screen(event);
            check("a listed command's message is masked as ### by rewriting it",
                    "cfselftest well #### that".equals(event.getParseResults().getReader().getString()));
            check("and it still parses", CommandScreen.executor(event.getParseResults()) != null);

            CommandEvent clean = new CommandEvent(dispatcher.parse("cfselftest all good", src));
            var before = clean.getParseResults();
            CommandScreen.screen(clean);
            check("a clean command is left exactly as it was", clean.getParseResults() == before && !clean.isCanceled());

            CommandEvent link = new CommandEvent(dispatcher.parse("cfselftest visit example.com", src));
            CommandScreen.screen(link);
            check("a link in a command blocks it", link.isCanceled());

            // /me rather than /say: /say needs op, so a fake player's parse would fail, the screen
            // would return early, and the check would pass having tested nothing.
            CommandEvent vanilla = new CommandEvent(dispatcher.parse("me well fuck", src));
            var vanillaBefore = vanilla.getParseResults();
            check("the vanilla /me parse reaches its command", CommandScreen.executor(vanillaBefore) != null
                    && vanillaBefore.getExceptions().isEmpty());
            CommandScreen.screen(vanilla);
            check("vanilla /me is left for the mask, not rewritten",
                    vanilla.getParseResults() == vanillaBefore && !vanilla.isCanceled());
        } finally {
            ChatFilterConfig.COMMANDS.set(original);
            Rules.rebuild();
            FilterService.clearStrikes(fake.getUUID());
            FilterService.forget(fake);
        }
    }

    private void checkStandards() {
        boolean loaded = ModList.get().isLoaded("standards");
        if (!loaded) {
            check("without Standards the seam is not active", !StandardsBridge.active());
            return;
        }
        check("with Standards the seam is active", StandardsBridge.active());
        try {
            Class<?> chat = Class.forName("com.sablednah.standards.api.chat.Chat");
            List<?> filters = (List<?>) chat.getMethod("filters").invoke(null);
            // Through the interface: the filter is an anonymous class, whose own methods are not
            // accessible reflectively.
            var id = Class.forName("com.sablednah.standards.api.chat.MessageFilter").getMethod("id");
            check("and our filter is registered with it", filters.stream().anyMatch(f -> {
                try {
                    return "chatfilter:filter".equals(id.invoke(f));
                } catch (ReflectiveOperationException e) {
                    return false;
                }
            }));
        } catch (ReflectiveOperationException e) {
            check("Standards' filter list is readable", false);
        }
    }

    private static boolean hasField(Class<?> c, String name) {
        for (var f : c.getDeclaredFields()) {
            if (f.getName().equals(name)) return true;
        }
        return false;
    }
}
