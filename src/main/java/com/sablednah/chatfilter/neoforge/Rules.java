package com.sablednah.chatfilter.neoforge;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.sablednah.chatfilter.ChatFilter;
import com.sablednah.chatfilter.ChatFilterConfig;
import com.sablednah.chatfilter.core.Analyzer;
import com.sablednah.chatfilter.core.CapsSettings;
import com.sablednah.chatfilter.core.CategoryPolicy;
import com.sablednah.chatfilter.core.Judge;
import com.sablednah.chatfilter.core.LinkSettings;
import com.sablednah.chatfilter.core.MatchSettings;
import com.sablednah.chatfilter.core.Responder;
import com.sablednah.chatfilter.core.Strikes;
import com.sablednah.chatfilter.core.WordListSettings;

/**
 * The config, compiled. Rebuilt whenever the file loads or changes, then swapped in whole, so a
 * message being judged on one thread never sees half an old list and half a new one — the mask is
 * computed off the server thread.
 */
public final class Rules {

    private static volatile Rules current = empty();

    public final Analyzer analyzer;
    public final Judge judge;
    public final List<List<String>> commands;
    public final List<Strikes.Step> ladder;
    public final List<Responder.Rule> responses;
    public final List<String> problems;

    private Rules(Analyzer analyzer, Judge judge, List<List<String>> commands,
            List<Strikes.Step> ladder, List<Responder.Rule> responses, List<String> problems) {
        this.analyzer = analyzer;
        this.judge = judge;
        this.commands = commands;
        this.ladder = ladder;
        this.responses = responses;
        this.problems = problems;
    }

    public static Rules current() {
        return current;
    }

    /** Recompile from the config. Problems are logged and kept for {@code /chatfilter reload}. */
    public static Rules rebuild() {
        Rules next = compile();
        current = next;
        for (String p : next.problems) {
            ChatFilter.LOGGER.warn("ChatFilter config: {}", p);
        }
        return next;
    }

    private static Rules compile() {
        List<String> problems = new ArrayList<>();
        MatchSettings match = new MatchSettings(ChatFilterConfig.STRIP_ACCENTS.get(),
                ChatFilterConfig.LOOKALIKES.get(), ChatFilterConfig.STRIP_COLOUR_CODES.get(),
                ChatFilterConfig.JOIN_SPELLED_OUT.get(), ChatFilterConfig.LEETSPEAK.get(),
                ChatFilterConfig.WILDCARDS.get(), strings(ChatFilterConfig.WORD_SUFFIXES.get()));

        Map<String, CategoryPolicy> policies = new LinkedHashMap<>();
        policies.put("profanity", policy(ChatFilterConfig.PROFANITY.category));
        policies.put("severe", policy(ChatFilterConfig.SEVERE.category));
        policies.put("links", policy(ChatFilterConfig.LINKS));
        policies.put("caps", policy(ChatFilterConfig.CAPS));
        policies.put("repeat", policy(ChatFilterConfig.REPEAT));
        policies.put("flood", policy(ChatFilterConfig.FLOOD));

        List<WordListSettings> lists = List.of(
                words("profanity", policies.get("profanity"), ChatFilterConfig.PROFANITY),
                words("severe", policies.get("severe"), ChatFilterConfig.SEVERE));
        LinkSettings links = new LinkSettings(policies.get("links"), ChatFilterConfig.LINK_IPS.get(),
                ChatFilterConfig.LINK_SPELLED_DOTS.get(), strings(ChatFilterConfig.LINK_TLDS.get()),
                strings(ChatFilterConfig.LINK_ALLOWED.get()));
        CapsSettings caps = new CapsSettings(policies.get("caps"), ChatFilterConfig.CAPS_MIN_LETTERS.get(),
                ChatFilterConfig.CAPS_PERCENT.get());

        Analyzer analyzer = new Analyzer(match, lists, links, caps);
        problems.addAll(analyzer.problems());
        Judge judge = new Judge(policies, ChatFilterConfig.CENSOR_STYLE.get(), ChatFilterConfig.CENSOR_TEXT.get());

        List<List<String>> commands = new ArrayList<>();
        for (String c : strings(ChatFilterConfig.COMMANDS.get())) {
            List<String> words = List.of(c.strip().replaceFirst("^/", "").toLowerCase(Locale.ROOT).split("\\s+"));
            if (!words.isEmpty() && !words.get(0).isEmpty()) commands.add(words);
        }

        List<Strikes.Step> ladder = new ArrayList<>();
        for (String line : strings(ChatFilterConfig.STRIKE_LADDER.get())) {
            Strikes.Step step = Strikes.Step.parse(line);
            if (step == null) {
                problems.add("strikes: cannot read ladder entry '" + line
                        + "' - expected 'N = alert', 'N = tell <message>' or 'N = command <command>'");
            } else {
                ladder.add(step);
            }
        }

        List<Responder.Rule> responses = new ArrayList<>();
        for (String line : strings(ChatFilterConfig.RESPONSES.get())) {
            Responder.Rule rule = Responder.Rule.parse(line);
            if (rule == null) {
                problems.add("responses: cannot read '" + line + "' - expected 'trigger|trigger = reply'");
            } else {
                responses.add(rule);
            }
        }
        return new Rules(analyzer, judge, List.copyOf(commands), List.copyOf(ladder),
                List.copyOf(responses), List.copyOf(problems));
    }

    /** Before the config has loaded: catches nothing. */
    private static Rules empty() {
        CategoryPolicy off = CategoryPolicy.OFF;
        Analyzer analyzer = new Analyzer(new MatchSettings(false, false, false, 0, false, false, List.of()),
                List.of(), new LinkSettings(off, false, false, List.of(), List.of()), new CapsSettings(off, 1, 100));
        return new Rules(analyzer, new Judge(Map.of(), com.sablednah.chatfilter.core.CensorStyle.FIXED, ""),
                List.of(), List.of(), List.of(), List.of());
    }

    private static CategoryPolicy policy(ChatFilterConfig.Category c) {
        return new CategoryPolicy(c.enabled.get(), c.action.get(), c.tellSender.get(), c.message.get(),
                c.strikes.get(), c.alertStaff.get());
    }

    private static WordListSettings words(String name, CategoryPolicy policy, ChatFilterConfig.Words w) {
        return new WordListSettings(name, policy, strings(w.anywhere.get()), strings(w.words.get()),
                strings(w.patterns.get()), strings(w.allowed.get()));
    }

    private static List<String> strings(List<? extends String> list) {
        return list.stream().map(String::valueOf).toList();
    }
}
