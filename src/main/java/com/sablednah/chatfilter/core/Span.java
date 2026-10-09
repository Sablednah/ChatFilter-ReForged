package com.sablednah.chatfilter.core;

/** A half-open range {@code [start, end)} of characters in the original message. */
public record Span(int start, int end) {

    public boolean contains(Span other) {
        return start <= other.start && other.end <= end;
    }

    public boolean overlaps(Span other) {
        return start < other.end && other.start < end;
    }

    public int length() {
        return end - start;
    }
}
