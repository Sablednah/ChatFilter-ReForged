package com.sablednah.chatfilter.neoforge;

import com.sablednah.chatfilter.core.Outcome;
import com.sablednah.chatfilter.integration.StandardsBridge;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.CommandEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;

/** The game-bus side: chat, commands, logout, the {@code /chatfilter} command and the nodes. */
public final class ChatEvents {

    private ChatEvents() {}

    /**
     * Public chat on a server where Standards is not delivering it.
     *
     * <p>HIGHEST, so a blocked line is stopped before any other mod formats, routes or logs it.
     * With Standards' seam active this does nothing: Standards asks us itself, after its mute gate,
     * and doing it here as well would judge every line twice.</p>
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onChat(ServerChatEvent event) {
        if (StandardsBridge.active()) return;
        ServerPlayer player = event.getPlayer();
        String raw = event.getRawText();
        Outcome outcome = FilterService.handle(player, raw, "chat", true);
        switch (outcome.kind()) {
            case BLOCK -> event.setCanceled(true);
            case SHADOW -> {
                event.setCanceled(true);
                // What they would have seen. Their own copy only; nobody else gets a line at all.
                player.sendSystemMessage(Component.translatable("chat.type.text",
                        player.getDisplayName(), Component.literal(raw)));
            }
            case REWRITE -> event.setMessage(Component.literal(outcome.text()));
            // MASK: the text filter has already attached the mask to this very message.
            case MASK, PASS -> { }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onCommand(CommandEvent event) {
        CommandScreen.screen(event);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        FilterService.tick(event.getServer());
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            FilterService.forget(player);
        }
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        ChatFilterCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onGatherNodes(PermissionGatherEvent.Nodes event) {
        ChatFilterPermissions.onGatherNodes(event);
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        if (Boolean.getBoolean("chatfilter.selftest")) {
            new SelfTest(event.getServer()).run();
        }
    }
}
