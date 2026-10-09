package com.sablednah.chatfilter.core;

/**
 * How one category treats what it catches.
 *
 * @param enabled    whether the category runs at all
 * @param action     what happens to the message
 * @param tellSender whether the sender is sent {@code message} (blank always means say nothing)
 * @param message    what they are told; {@code {player}} and the old {@code %N} both work
 * @param strikes    strikes added per caught message; 0 for none
 * @param alert      whether staff with alerts on are told
 */
public record CategoryPolicy(boolean enabled, Action action, boolean tellSender, String message,
        int strikes, boolean alert) {

    public static final CategoryPolicy OFF = new CategoryPolicy(false, Action.LOG, false, "", 0, false);
}
