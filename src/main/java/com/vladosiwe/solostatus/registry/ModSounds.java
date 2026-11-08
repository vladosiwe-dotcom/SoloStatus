package com.vladosiwe.solostatus.registry;

import com.vladosiwe.solostatus.SoloStatus;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Звуки Системы. По умолчанию (assets/solostatus/sounds.json) каждый ссылается на подходящий ванильный звук,
 * поэтому мод звучит сразу. Свою озвучку (например, голос «Системы») можно подложить ресурспаком:
 * assets/solostatus/sounds.json с "replace": true и файлами .ogg — без пересборки мода (docs/SOUNDS.md).
 */
public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, SoloStatus.MODID);
    /** Все звуки по порядку — для панели проверки звуков в окне Системы. */
    public static final Map<String, DeferredHolder<SoundEvent, SoundEvent>> ALL = new LinkedHashMap<>();

    public static final DeferredHolder<SoundEvent, SoundEvent> OPEN = reg("system.open");
    public static final DeferredHolder<SoundEvent, SoundEvent> CLOSE = reg("system.close");
    public static final DeferredHolder<SoundEvent, SoundEvent> CLICK = reg("system.click");
    public static final DeferredHolder<SoundEvent, SoundEvent> STAT_UP = reg("system.stat_up");
    public static final DeferredHolder<SoundEvent, SoundEvent> NOTICE = reg("system.notice");
    public static final DeferredHolder<SoundEvent, SoundEvent> LEVEL_UP = reg("system.level_up");
    public static final DeferredHolder<SoundEvent, SoundEvent> AWAKENING = reg("system.awakening");
    public static final DeferredHolder<SoundEvent, SoundEvent> ACHIEVEMENT = reg("system.achievement");
    public static final DeferredHolder<SoundEvent, SoundEvent> HIDDEN_ACHIEVEMENT = reg("system.hidden_achievement");
    public static final DeferredHolder<SoundEvent, SoundEvent> RANK_UP = reg("system.rank_up");
    public static final DeferredHolder<SoundEvent, SoundEvent> TITLE = reg("system.title");
    public static final DeferredHolder<SoundEvent, SoundEvent> ERROR = reg("system.error");
    public static final DeferredHolder<SoundEvent, SoundEvent> PRESENCE = reg("system.presence");

    private static DeferredHolder<SoundEvent, SoundEvent> reg(String name) {
        DeferredHolder<SoundEvent, SoundEvent> h = SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(SoloStatus.id(name)));
        ALL.put(name, h);
        return h;
    }

    private ModSounds() {}
}
