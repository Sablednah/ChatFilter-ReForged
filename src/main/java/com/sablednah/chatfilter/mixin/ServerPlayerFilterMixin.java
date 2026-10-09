package com.sablednah.chatfilter.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.sablednah.chatfilter.neoforge.MaskingTextFilter;
import com.sablednah.chatfilter.neoforge.MaskPolicy;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.TextFilter;

/**
 * The two places vanilla's chat filter is decided, for each player.
 *
 * <ul>
 *     <li>{@code getTextFilter} — what a player's text is run through. Wrapped, never replaced, so
 *         a filtering service configured in {@code server.properties} still runs underneath.</li>
 *     <li>{@code shouldFilterMessageTo} — whether a message's mask applies for a given recipient.
 *         Vanilla says "only if one of them has filtering switched on in their client"; ChatFilter
 *         says "unless the recipient has {@code chatfilter.see}". The sender is never masked from
 *         themselves — vanilla's own rule, kept, and the reason MASK is silent.</li>
 * </ul>
 *
 * <p>Every caller of either goes through these two methods — chat, {@code /msg}, {@code /me},
 * {@code /say}, {@code /teammsg} — which is why two small injections cover all of them. Check this
 * class first when porting to a new Minecraft version: if either method is renamed the mixin fails
 * loudly at startup ({@code defaultRequire = 1}), which is the point.</p>
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerFilterMixin {

    @Unique
    private TextFilter chatfilter$masking;

    @Inject(method = "getTextFilter", at = @At("RETURN"), cancellable = true)
    private void chatfilter$wrapTextFilter(CallbackInfoReturnable<TextFilter> cir) {
        if (chatfilter$masking == null) {
            chatfilter$masking = new MaskingTextFilter((ServerPlayer) (Object) this, cir.getReturnValue());
        }
        cir.setReturnValue(chatfilter$masking);
    }

    @Inject(method = "shouldFilterMessageTo", at = @At("HEAD"), cancellable = true)
    private void chatfilter$maskFor(ServerPlayer receiver, CallbackInfoReturnable<Boolean> cir) {
        if (receiver != (Object) this && MaskPolicy.masks(receiver)) {
            cir.setReturnValue(true);
        }
    }
}
