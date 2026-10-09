package com.sablednah.chatfilter.neoforge;

import com.sablednah.chatfilter.ChatFilter;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.server.permission.PermissionAPI;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import net.neoforged.neoforge.server.permission.nodes.PermissionTypes;

/**
 * Permission nodes, through NeoForge's {@link PermissionAPI} — so LuckPerms, Standards' built-in
 * handler or anything else that implements it can grant them, and with none installed the defaults
 * below apply.
 *
 * <p>The commands are gated by a node too, not an op level, so a permissions manager can give
 * {@code /chatfilter} to a moderator who is not an op. MobHealth gates by op level and cannot.</p>
 */
public final class ChatFilterPermissions {

    /** Not filtered at all — the old {@code chatfilter.canswear}. Default: nobody, ops included. */
    public static final PermissionNode<Boolean> BYPASS = node("bypass", false, -1);
    /** May post links. Default: nobody. */
    public static final PermissionNode<Boolean> LINKS = node("links", false, -1);
    /** May chat at all — the old {@code chatfilter.canchat}. Default: everybody. */
    public static final PermissionNode<Boolean> CHAT = node("chat", true, -1);
    /** Sees masked and censored words as written, where the path allows a per-viewer copy. */
    public static final PermissionNode<Boolean> SEE = node("see", false, -1);
    /** Receives staff alerts. Default: ops (level 2). */
    public static final PermissionNode<Boolean> ALERTS = node("alerts", false, 2);
    /** Uses {@code /chatfilter}. Default: ops (level 2). */
    public static final PermissionNode<Boolean> ADMIN = node("admin", false, 2);

    private static final String ALERTS_OFF_KEY = "chatfilter_alerts_off";

    private ChatFilterPermissions() {}

    public static void onGatherNodes(PermissionGatherEvent.Nodes event) {
        event.addNodes(BYPASS, LINKS, CHAT, SEE, ALERTS, ADMIN);
    }

    public static boolean has(ServerPlayer player, PermissionNode<Boolean> node) {
        return PermissionAPI.getPermission(player, node);
    }

    /** For {@code .requires}: the console and command blocks always pass. */
    public static boolean admin(CommandSourceStack source) {
        ServerPlayer p = source.getPlayer();
        return p == null || has(p, ADMIN);
    }

    /** Staff who have the node and have not turned alerts off. */
    public static boolean receivesAlerts(ServerPlayer player) {
        return has(player, ALERTS) && !persisted(player).getBooleanOr(ALERTS_OFF_KEY, false);
    }

    public static boolean alertsOff(ServerPlayer player) {
        return persisted(player).getBooleanOr(ALERTS_OFF_KEY, false);
    }

    public static void setAlertsOff(ServerPlayer player, boolean off) {
        CompoundTag data = player.getPersistentData();
        CompoundTag persisted = data.getCompoundOrEmpty(Player.PERSISTED_NBT_TAG);
        persisted.putBoolean(ALERTS_OFF_KEY, off);
        data.put(Player.PERSISTED_NBT_TAG, persisted);
    }

    private static CompoundTag persisted(ServerPlayer player) {
        return player.getPersistentData().getCompoundOrEmpty(Player.PERSISTED_NBT_TAG);
    }

    /**
     * @param opLevel when the default is false, an op of at least this level still gets it; -1 for
     *                no op exception
     */
    private static PermissionNode<Boolean> node(String name, boolean everyone, int opLevel) {
        return new PermissionNode<>(ChatFilter.MODID, name, PermissionTypes.BOOLEAN,
                (player, uuid, ctx) -> {
                    if (everyone) return Boolean.TRUE;
                    if (opLevel < 0 || player == null) return Boolean.FALSE;
                    return opLevel >= 2 && Commands.LEVEL_GAMEMASTERS.check(player.permissions());
                });
    }
}
