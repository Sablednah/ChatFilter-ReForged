package com.sablednah.chatfilter.core;

/**
 * One thing a category found.
 *
 * @param category which category ({@code profanity}, {@code links}, …)
 * @param span     what it covers in the original text; {@code null} for whole-message findings
 *                 such as caps or flooding
 * @param term     the list entry, pattern or rule that matched — for logs and {@code /chatfilter test}
 */
public record Hit(String category, Span span, String term) {

    public boolean wholeMessage() {
        return span == null;
    }
}
