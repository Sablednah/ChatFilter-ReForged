package com.sablednah.chatfilter.core;

import java.util.List;

/**
 * How hard the matcher looks. See {@link Normalizer} and {@link TermCompiler} for what each does.
 *
 * @param joinMinimum shortest run of spelled-out letters ({@code s h i t}) to join; 0 turns it off
 * @param suffixes    endings a whole-word entry also catches ({@code boob} → {@code boobs})
 */
public record MatchSettings(boolean stripAccents, boolean lookalikes, boolean stripColourCodes,
        int joinMinimum, boolean leet, boolean wildcards, List<String> suffixes) {
}
