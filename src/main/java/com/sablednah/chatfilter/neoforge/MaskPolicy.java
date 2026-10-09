package com.sablednah.chatfilter.neoforge;

import net.minecraft.server.level.ServerPlayer;

/**
 * Who a mask applies to — kept out of the mixin so the rule lives with the rest of the mod and the
 * mixin stays two lines of plumbing.
 */
public final class MaskPolicy {

    private MaskPolicy() {}

    /**
     * Whether masked words are hidden from this recipient. Everyone but holders of
     * {@code chatfilter.see}: "only certain people can SEE swears", as the old page asked. A
     * message with no mask is unaffected either way, so this costs nothing on clean lines.
     */
    public static boolean masks(ServerPlayer receiver) {
        try {
            return !ChatFilterPermissions.has(receiver, ChatFilterPermissions.SEE);
        } catch (RuntimeException e) {
            return true; // no permission answer yet (very early in login): hide, the safe side
        }
    }
}
