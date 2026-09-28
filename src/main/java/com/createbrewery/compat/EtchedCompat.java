package com.createbrewery.compat;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

public final class EtchedCompat {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String ETCHED_MOD_ID = "etched";

    private static Boolean etchedLoaded = null;
    private static Constructor<?> playPacketConstructor = null;
    private static Method getTracksMethod = null;

    private EtchedCompat() {}

    public static boolean isLoaded() {
        if (etchedLoaded == null) {
            try {
                etchedLoaded = ModList.get().isLoaded(ETCHED_MOD_ID);
            } catch (Throwable t) {
                etchedLoaded = false;
            }
        }
        return etchedLoaded;
    }

    public static boolean isEtchedDisc(ItemStack stack) {
        if (!isLoaded() || stack == null || stack.isEmpty()) return false;

        // 1. Direct DataComponent checks for Etched music components
        try {
            DataComponentType<?> music = BuiltInRegistries.DATA_COMPONENT_TYPE.get(ResourceLocation.fromNamespaceAndPath(ETCHED_MOD_ID, "music"));
            if (music != null && stack.has(music)) return true;

            DataComponentType<?> album = BuiltInRegistries.DATA_COMPONENT_TYPE.get(ResourceLocation.fromNamespaceAndPath(ETCHED_MOD_ID, "album"));
            if (album != null && stack.has(album)) return true;

            DataComponentType<?> cover = BuiltInRegistries.DATA_COMPONENT_TYPE.get(ResourceLocation.fromNamespaceAndPath(ETCHED_MOD_ID, "album_cover"));
            if (cover != null && stack.has(cover)) return true;
        } catch (Throwable ignored) {}

        // 2. Item ID check for etched items
        try {
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (itemId != null && ETCHED_MOD_ID.equals(itemId.getNamespace())) {
                String path = itemId.getPath();
                if ("etched_music_disc".equals(path) || "album_cover".equals(path)) {
                    return true;
                }
            }
        } catch (Throwable ignored) {}

        // 3. Fallback: reflection check via PlayableRecord
        try {
            Class<?> prClass = Class.forName("gg.moonflower.etched.api.record.PlayableRecord");
            Method isPlayable = prClass.getMethod("isPlayableRecord", ItemStack.class);
            Boolean res = (Boolean) isPlayable.invoke(null, stack);
            if (Boolean.TRUE.equals(res)) return true;
        } catch (Throwable ignored) {}

        return false;
    }

    public static Component getTrackDisplayName(ItemStack stack, HolderLookup.Provider registries) {
        if (!isLoaded() || stack == null || stack.isEmpty()) {
            return stack == null ? Component.empty() : stack.getHoverName();
        }

        try {
            if (getTracksMethod == null) {
                Class<?> prClass = Class.forName("gg.moonflower.etched.api.record.PlayableRecord");
                getTracksMethod = prClass.getMethod("getTracks", HolderLookup.Provider.class, ItemStack.class);
            }
            List<?> tracks = (List<?>) getTracksMethod.invoke(null, registries, stack);
            if (tracks != null && !tracks.isEmpty()) {
                Object firstTrack = tracks.get(0);
                Method getDisplayName = firstTrack.getClass().getMethod("getDisplayName");
                Component titleComp = (Component) getDisplayName.invoke(firstTrack);
                if (titleComp != null && !titleComp.getString().isBlank()) {
                    return titleComp;
                }
            }
        } catch (Throwable ignored) {}

        return stack.getHoverName();
    }

    public static boolean playEtchedDisc(ServerLevel level, BlockPos pos, ItemStack disc) {
        if (!isLoaded() || level == null || disc == null || disc.isEmpty()) return false;

        try {
            initPacketConstructor();
            if (playPacketConstructor == null) return false;

            Object packet = playPacketConstructor.newInstance(disc.copy(), pos, null);
            PacketDistributor.sendToPlayersNear(
                level,
                null,
                pos.getX() + 0.5,
                pos.getY() + 0.5,
                pos.getZ() + 0.5,
                64.0,
                (CustomPacketPayload) packet
            );
            return true;
        } catch (Throwable t) {
            LOGGER.error("Failed to stream Etched disc at {}", pos, t);
            return false;
        }
    }

    private static void initPacketConstructor() {
        if (playPacketConstructor != null) return;
        try {
            Class<?> packetClass = Class.forName("gg.moonflower.etched.common.network.play.ClientboundPlayBlockMusicPacket");
            playPacketConstructor = packetClass.getConstructor(ItemStack.class, BlockPos.class, UUID.class);
        } catch (Throwable t) {
            LOGGER.warn("Could not find ClientboundPlayBlockMusicPacket constructor for Etched", t);
        }
    }
}
