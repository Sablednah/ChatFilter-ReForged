package com.sablednah.chatfilter;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.sablednah.chatfilter.core.BuildInfo;
import com.sablednah.chatfilter.integration.StandardsBridge;
import com.sablednah.chatfilter.neoforge.ChatEvents;
import com.sablednah.chatfilter.neoforge.Rules;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * ChatFilter ReForged — a server-side chat filter.
 *
 * <p>A rebuild of the 2012 Bukkit plugin, read for intent and written fresh. What it does, and the
 * three routes a message can take through it:</p>
 * <ul>
 *     <li><b>Vanilla chat</b> and vanilla's signed commands: judged in the chat or command event,
 *         masked through vanilla's own chat filter (see {@code mixin/ServerPlayerFilterMixin}).</li>
 *     <li><b>Other chat-like commands</b> ({@code /r}, {@code /f chat}, anything listed): parsed,
 *         judged on their message only, and re-parsed with the censored text.</li>
 *     <li><b>Standards' own deliveries</b>, when it is installed: judged through its filter seam,
 *         per viewer.</li>
 * </ul>
 *
 * <p>Everything that decides — normalising, matching, judging, strikes, spam — lives in
 * {@code core} with no Minecraft imports, so a port to another loader re-implements only the thin
 * {@code neoforge} layer.</p>
 */
@Mod(ChatFilter.MODID)
public final class ChatFilter {

    public static final String MODID = "chatfilter";

    public static final Logger LOGGER = LogUtils.getLogger();

    public ChatFilter(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, ChatFilterConfig.SPEC);
        modEventBus.addListener(ChatFilter::onConfig);
        modEventBus.addListener(ChatFilter::onConfigReload);
        modEventBus.addListener(ChatFilter::onSetup);
        NeoForge.EVENT_BUS.register(ChatEvents.class);

        // Which build, not just which release. Same format across the family of mods.
        LOGGER.info("ChatFilter {}", BuildInfo.describe());
    }

    private static void onConfig(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == ChatFilterConfig.SPEC) Rules.rebuild();
    }

    private static void onConfigReload(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == ChatFilterConfig.SPEC) Rules.rebuild();
    }

    private static void onSetup(FMLCommonSetupEvent event) {
        if (ModList.get().isLoaded("standards")) {
            event.enqueueWork(StandardsBridge::install);
        }
    }
}
