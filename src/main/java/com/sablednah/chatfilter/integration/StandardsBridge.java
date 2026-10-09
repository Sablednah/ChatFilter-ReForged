package com.sablednah.chatfilter.integration;

import com.sablednah.chatfilter.ChatFilter;

/**
 * SableCraft Standards, when it is installed.
 *
 * <p>Standards delivers a lot of chat itself — decorated lines, party and faction channels,
 * {@code /msg}, {@code /r}, {@code /me}, {@code /mail} — and asks registered filters about every one
 * of them through its {@code MessageFilter} seam. With the seam active, ChatFilter does its chat
 * work there instead of in the chat event, so each message is judged once and Standards can give
 * each viewer their own copy.</p>
 *
 * <p>This class only checks and reports; {@link StandardsFilter} is the one that imports Standards,
 * and it is not loaded until the check has passed — a soft dependency must never be able to throw
 * a {@code NoClassDefFoundError} at a server that does not have it.</p>
 */
public final class StandardsBridge {

    private static volatile boolean active;

    private StandardsBridge() {}

    /** Whether Standards is screening its own deliveries through ChatFilter. */
    public static boolean active() {
        return active;
    }

    /** Call once at setup, only when the {@code standards} mod is loaded. */
    public static void install() {
        try {
            Class<?> chat = Class.forName("com.sablednah.standards.api.chat.Chat");
            Class<?> filter = Class.forName("com.sablednah.standards.api.chat.MessageFilter");
            chat.getMethod("registerFilter", filter);
        } catch (ReflectiveOperationException | LinkageError e) {
            ChatFilter.LOGGER.warn("ChatFilter: this version of Standards has no message-filter seam. "
                    + "Plain chat and commands are still filtered, but Standards' formatted chat, party "
                    + "and faction channels, /r and /mail are not. Update Standards to fix that.");
            return;
        }
        StandardsFilter.register();
        active = true;
        ChatFilter.LOGGER.info("ChatFilter: screening Standards' chat, channels, /msg, /r, /me and /mail");
    }
}
