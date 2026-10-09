package com.sablednah.chatfilter.integration;

import com.sablednah.chatfilter.core.Outcome;
import com.sablednah.chatfilter.neoforge.ChatFilterPermissions;
import com.sablednah.chatfilter.neoforge.FilterService;
import com.sablednah.standards.api.chat.Chat;
import com.sablednah.standards.api.chat.MessageFilter;
import com.sablednah.standards.api.chat.Screening;

import net.minecraft.server.level.ServerPlayer;

/** The one class that imports Standards. Loaded only by {@link StandardsBridge} after its check. */
final class StandardsFilter {

    private StandardsFilter() {}

    static void register() {
        Chat.registerFilter(new MessageFilter() {
            @Override
            public String id() {
                return "chatfilter:filter";
            }

            @Override
            public Screening screen(ServerPlayer sender, String text, String channel) {
                boolean chat = MessageFilter.CHAT.equals(channel);
                Outcome outcome = FilterService.handle(sender, text, channel, chat);
                return switch (outcome.kind()) {
                    case PASS -> Screening.pass();
                    // The sender has already been told, if their category says to.
                    case BLOCK -> Screening.block(null);
                    // Standards delivers the sender's own copy in its real format: decorations,
                    // nickname, the /msg confirmation. Nobody else gets it.
                    case SHADOW -> Screening.shadow();
                    // The sender and chatfilter.see keep the original; vanilla's own delivery of
                    // the same line is already masked, so Standards may leave it alone.
                    case MASK -> Screening.censor(outcome.censoredText(text),
                            viewer -> viewer == sender || sees(viewer)).vanillaHandled();
                    // REPLACE is for everyone, the sender included.
                    case REWRITE -> Screening.censor(outcome.text(), StandardsFilter::sees);
                };
            }
        });
    }

    private static boolean sees(ServerPlayer viewer) {
        return viewer != null && ChatFilterPermissions.has(viewer, ChatFilterPermissions.SEE);
    }
}
