package com.vladosiwe.solostatus.compat.ironsspells;

import com.vladosiwe.solostatus.SoloStatus;
import com.vladosiwe.solostatus.clazz.HunterClass;
import com.vladosiwe.solostatus.data.Hunter;
import com.vladosiwe.solostatus.data.PlayerData;
import com.vladosiwe.solostatus.mastery.MasterySystem;
import com.vladosiwe.solostatus.stats.AttributeSystem;
import com.vladosiwe.solostatus.stats.Stat;
import com.vladosiwe.solostatus.stats.StatFormulas;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Интеграция с Iron's Spells 'n Spellbooks без compile-зависимости.
 *
 * 1) Атрибуты (подтверждённый механизм — ванильный реестр атрибутов): INT и пассивки специализаций
 *    дают модификаторы к irons_spellbooks:spell_power, max_mana, mana_regen, cooldown_reduction
 *    и школьным *_spell_power. Сами заклинания не изменяются.
 * 2) Школа магии определяется по id типа урона (irons_spellbooks:&lt;школа&gt;_magic) — так магическое мастерство
 *    растёт от реального урона заклинаниями по противникам.
 * 3) Событие применения заклинания (SpellOnCastEvent, наследник PlayerEvent: getEntity, getSchoolType, getSpellLevel;
 *    SchoolType.getId() → ResourceLocation) подключается через отражение. Сверено с исходниками Iron's Spells
 *    ветки 1.21 (1.21.1-3.16.3). Если в другой версии класс или методы изменятся, функция отключится
 *    с сообщением в логе, остальные части интеграции продолжат работать.
 */
public final class IronsSpellsCompat {
    public static final String MODID = "irons_spellbooks";
    private static final String[] SCHOOLS = {"fire", "ice", "lightning", "holy", "ender", "blood", "evocation", "nature", "eldritch"};

    private static boolean castHooked;

    private IronsSpellsCompat() {}

    /** Для проверки интеграции: событие применения заклинаний подключено. */
    public static boolean castHooked() { return castHooked; }

    public static void init() {
        hookSpellCast();
    }

    private static Optional<Holder.Reference<Attribute>> attr(String path) {
        return BuiltInRegistries.ATTRIBUTE.getHolder(ResourceLocation.fromNamespaceAndPath(MODID, path));
    }

    private static void set(ServerPlayer p, String attribute, double amount, AttributeModifier.Operation op) {
        attr(attribute).ifPresent(h -> AttributeSystem.set(p, h, SoloStatus.id("irons_" + attribute), amount, op));
    }

    public static void applyAttributes(ServerPlayer p, PlayerData d) {
        double spellPower = Math.min(0.40, StatFormulas.magicPower(d) * 0.8);
        if (d.hunterClass == HunterClass.MAGE) spellPower += 0.05;
        set(p, "spell_power", spellPower, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        // magic is the harder path: INT adds half of what it used to (cap 100 mana, +15% regen)
        set(p, "max_mana", Math.min(100, StatFormulas.eff(d, Stat.INT)), AttributeModifier.Operation.ADD_VALUE);
        double regen = Math.min(0.15, StatFormulas.eff(d, Stat.INT) * 0.003) + d.skillRank("arcane_flow") * 0.05;
        set(p, "mana_regen", regen, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        set(p, "cooldown_reduction", d.skillRank("arcane_flow") * 0.02, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);

        int att = d.skillRank("elemental_attunement");
        int flow = d.skillRank("arcane_flow");
        int grace = d.skillRank("divine_grace");
        for (String school : SCHOOLS) {
            double v = 0;
            if (att > 0 && (school.equals("fire") || school.equals("ice") || school.equals("lightning"))) v += 0.03 * att;
            if (flow > 0 && (school.equals("ender") || school.equals("evocation") || school.equals("eldritch"))) v += 0.02 * flow;
            if (grace > 0 && (school.equals("holy") || school.equals("nature"))) v += 0.04 * grace;
            set(p, school + "_spell_power", v, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        }
    }

    @Nullable
    public static String school(DamageSource source) {
        return source.typeHolder().unwrapKey()
                .filter(k -> k.location().getNamespace().equals(MODID))
                .map(k -> {
                    String path = k.location().getPath();
                    return path.endsWith("_magic") ? path.substring(0, path.length() - 6) : path;
                })
                .orElse(null);
    }

    // ------------------------------------------------------------------
    // Событие применения заклинания (через отражение)
    // ------------------------------------------------------------------

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void hookSpellCast() {
        try {
            Class cls = Class.forName("io.redspace.ironsspellbooks.api.events.SpellOnCastEvent");
            Method getEntity = cls.getMethod("getEntity");
            Method getSchool = cls.getMethod("getSchoolType");
            Method getLevel = cls.getMethod("getSpellLevel");
            Consumer listener = ev -> onCast(ev, getEntity, getSchool, getLevel);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, (Class<Event>) cls, (Consumer<Event>) listener);
            castHooked = true;
            SoloStatus.LOGGER.info("Iron's Spells: отслеживание применения заклинаний подключено");
        } catch (Throwable t) {
            SoloStatus.LOGGER.warn("Iron's Spells: событие применения заклинаний недоступно в этой версии ({}). " +
                    "Магическое мастерство будет расти только от урона заклинаниями.", t.toString());
        }
    }

    private static void onCast(Object event, Method getEntity, Method getSchool, Method getLevel) {
        try {
            if (event instanceof ICancellableEvent c && c.isCanceled()) return;
            if (!(getEntity.invoke(event) instanceof ServerPlayer p)) return;
            PlayerData d = Hunter.get(p);
            if (!d.hasClass()) return;
            Object schoolType = getSchool.invoke(event);
            String school = "arcane";
            if (schoolType != null) {
                Object id = schoolType.getClass().getMethod("getId").invoke(schoolType);
                if (id instanceof ResourceLocation rl) school = rl.getPath();
            }
            int spellLevel = getLevel.invoke(event) instanceof Integer i ? i : 1;
            d.addCounter("spell_casts", 1);
            // Анти-фарм: опыт мастерства за применение начисляется только в бою (последние 10 секунд)
            long now = p.serverLevel().getGameTime();
            if (now - d.lastCombatTick < 200) {
                MasterySystem.addMagic(p, d, school, 1.0 + spellLevel * 0.3);
            }
        } catch (Exception e) {
            // Не роняем игру из-за несовпадения API — просто пропускаем событие
        }
    }
}
