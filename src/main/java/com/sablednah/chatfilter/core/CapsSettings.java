package com.sablednah.chatfilter.core;

/**
 * @param minLetters  lines with fewer letters than this are never caps — "OK", "GG", "LOL"
 * @param maxPercent  share of capital letters, out of all letters, above which a line is shouting
 */
public record CapsSettings(CategoryPolicy policy, int minLetters, int maxPercent) {
}
