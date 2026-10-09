package com.sablednah.chatfilter.neoforge;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.sablednah.chatfilter.core.Outcome;
import com.sablednah.chatfilter.core.Span;

import net.minecraft.network.chat.FilterMask;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.FilteredText;
import net.minecraft.server.network.TextFilter;

/**
 * Vanilla's text filter for one player, with ChatFilter's mask laid on top.
 *
 * <p>Minecraft already has the whole machinery for censoring chat without breaking it: each message
 * carries a {@link FilterMask}, the signature covers the text and not the mask, and the client draws
 * masked characters as dark-grey {@code #} with a "filtered by the server" hover. Realms uses it. On
 * an ordinary server the filter is a no-op and the mask only ever reaches players whose own
 * filtering option is on. ChatFilter supplies the mask, and {@code ServerPlayerFilterMixin} decides
 * who it reaches.</p>
 *
 * <p>Only {@link #processStreamMessage} is masked — that is what chat and the message of a signed
 * command ({@code /msg}, {@code /me}, {@code /say}, {@code /teammsg}) go through. The anvil calls it
 * too and ignores the answer. Signs and books arrive through {@link #processMessageBundle}, which is
 * passed straight through: a sign keeps whichever text its writer's own client setting chose, so a
 * mask there would only ever reach the players who already filter.</p>
 *
 * <p>Pure: it asks {@link FilterService#peek} and has no side effects, because it runs before the
 * chat event and may be asked about a line that is then blocked for another reason.</p>
 */
public final class MaskingTextFilter implements TextFilter {

    private final ServerPlayer player;
    private final TextFilter inner;

    public MaskingTextFilter(ServerPlayer player, TextFilter inner) {
        this.player = player;
        this.inner = inner == null ? TextFilter.DUMMY : inner;
    }

    @Override
    public void join() {
        inner.join();
    }

    @Override
    public void leave() {
        inner.leave();
    }

    @Override
    public CompletableFuture<FilteredText> processStreamMessage(String text) {
        return inner.processStreamMessage(text).thenApply(theirs -> merge(text, theirs));
    }

    @Override
    public CompletableFuture<List<FilteredText>> processMessageBundle(List<String> texts) {
        return inner.processMessageBundle(texts);
    }

    private FilteredText merge(String text, FilteredText theirs) {
        if (theirs.mask().isFullyFiltered()) {
            return theirs; // a configured filtering service has already hidden all of it
        }
        Outcome outcome;
        try {
            outcome = FilterService.peek(player, text);
        } catch (RuntimeException e) {
            // A filter that throws must not cost the player their message.
            com.sablednah.chatfilter.ChatFilter.LOGGER.error("ChatFilter: mask failed; passing the message through", e);
            return theirs;
        }
        if (outcome.kind() != Outcome.Kind.MASK || outcome.mask().isEmpty()) {
            return theirs;
        }
        FilterMask mask = new FilterMask(text.length());
        for (Span s : outcome.mask()) {
            for (int i = Math.max(0, s.start()); i < Math.min(text.length(), s.end()); i++) {
                mask.setFiltered(i);
            }
        }
        // Keep whatever a configured filtering service hid as well. Its bits are private, but its
        // own rendering of the text gives them away: every '#' it wrote where the text has none.
        if (!theirs.mask().isEmpty()) {
            String rendered = theirs.mask().apply(text);
            if (rendered != null) {
                for (int i = 0; i < Math.min(text.length(), rendered.length()); i++) {
                    if (rendered.charAt(i) == '#' && text.charAt(i) != '#') mask.setFiltered(i);
                }
            }
        }
        return new FilteredText(text, mask);
    }
}
