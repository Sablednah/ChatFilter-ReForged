package com.sablednah.chatfilter.core;

import java.util.List;

/**
 * One word-list category, as configured.
 *
 * @param anywhere entries matched inside other words too — the old {@code profanity} list
 * @param words    entries matched only as whole words — the old {@code profanityWordMatch}
 * @param patterns regular expressions, matched against the lower-cased, accent-folded text
 * @param allowed  words that are never caught, even when an entry above is inside them —
 *                 {@code scunthorpe}, {@code shiitake}
 */
public record WordListSettings(String name, CategoryPolicy policy, List<String> anywhere,
        List<String> words, List<String> patterns, List<String> allowed) {
}
