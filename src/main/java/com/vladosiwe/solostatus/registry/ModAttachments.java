package com.vladosiwe.solostatus.registry;

import com.vladosiwe.solostatus.SoloStatus;
import com.vladosiwe.solostatus.data.PlayerData;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class ModAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, SoloStatus.MODID);

    /** Профиль охотника. copyOnDeath — навыки и прогресс не теряются при смерти. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<PlayerData>> PLAYER_DATA =
            ATTACHMENTS.register("player_data", () -> AttachmentType.serializable(() -> new PlayerData()).copyOnDeath().build());

    /** The System's own jewellery slots: two rings and an amulet. Kept on death, like the profile. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<net.neoforged.neoforge.items.ItemStackHandler>> ACCESSORIES =
            ATTACHMENTS.register("accessories", () -> AttachmentType.serializable(() -> new net.neoforged.neoforge.items.ItemStackHandler(3)).copyOnDeath().build());

    private ModAttachments() {}
}
