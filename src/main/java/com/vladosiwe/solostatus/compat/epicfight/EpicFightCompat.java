package com.vladosiwe.solostatus.compat.epicfight;

import com.vladosiwe.solostatus.SoloStatus;
import com.vladosiwe.solostatus.combat.Threat;
import com.vladosiwe.solostatus.data.Hunter;
import com.vladosiwe.solostatus.data.PlayerData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Интеграция с Epic Fight без compile-зависимости (через отражение).
 *
 * Все имена классов и методов сверены с epic-fight-21.17.3.1-mc1.21.1-neoforge.jar (javap):
 *  - EpicFightCapabilities.getServerPlayerPatchAsOptional(Entity) → Optional&lt;ServerPlayerPatch&gt;
 *  - LivingEntityPatch.playAnimationInClientSide(AssetAccessor, float) — анимация видна игроку и всем вокруг,
 *    но сервер НЕ выполняет фазы атаки анимации: урон остаётся за навыком системы (нет двойного урона).
 *  - Animations.&lt;ИМЯ&gt; (static AnimationAccessor) — заполняются Epic Fight при загрузке анимаций.
 *  - EpicFightEventHooks$Entity.ON_DODGE (DodgeEvent) — успешное уклонение.
 *  - EpicFightEventHooks$Entity.TAKE_DAMAGE_INCOME (TakeDamageEvent$Income: isParried(), getResult()) —
 *    парирование и блок навыками защиты Epic Fight. Подписка с низким приоритетом, чтобы прочитать результат
 *    после того, как навык защиты Epic Fight обработал удар.
 *
 * Если что-то не найдено (другая версия Epic Fight), соответствующая часть отключается с сообщением в логе.
 */
public final class EpicFightCompat {
    private static final String CAPS = "yesman.epicfight.world.capabilities.EpicFightCapabilities";
    private static final String LIVING_PATCH = "yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch";
    private static final String ENTITY_PATCH = "yesman.epicfight.world.capabilities.entitypatch.EntityPatch";
    private static final String ANIMATIONS = "yesman.epicfight.gameasset.Animations";
    private static final String ASSET_ACCESSOR = "yesman.epicfight.api.asset.AssetAccessor";
    private static final String HOOKS_ENTITY = "yesman.epicfight.api.event.EpicFightEventHooks$Entity";
    private static final String EVENT_HOOK = "yesman.epicfight.api.event.EventHook";
    private static final String SUBSCRIPTION = "yesman.epicfight.api.event.subscription.DefaultEventSubscription";
    private static final String PATCH_EVENT = "yesman.epicfight.api.event.LivingEntityPatchEvent";
    private static final String INCOME_EVENT = "yesman.epicfight.api.event.types.entity.TakeDamageEvent$Income";

    private static boolean animationsReady;
    private static boolean eventsHooked;
    private static String animationsError = "init() not called";
    private static String eventsError = "init() not called";
    /*
     * Method handles, not Class.getMethod: getMethod resolves the signature of EVERY public method of the class and
     * its superclasses, and Epic Fight's entity patches have client-only methods (LocalPlayer…). On a dedicated
     * server that throws "Attempted to load class LocalPlayer for invalid dist". A handle links only the one member.
     */
    private static final MethodHandles.Lookup L = MethodHandles.publicLookup();
    private static MethodHandle getPatch;
    private static MethodHandle playClientSide;
    private static MethodHandle getEntityPatch;
    /** PlayerPatch.toEpicFightMode(boolean sync), toVanillaMode(boolean sync), isEpicFightMode() — checked against 21.17.3.1. */
    private static MethodHandle toEpicFightMode, toVanillaMode, isEpicFightMode;
    private static String modeError = "init() not called";
    private static MethodHandle getOriginal;
    private static Class<?> animationsClass;
    private static final Map<String, Object> ANIM_CACHE = new HashMap<>();

    private EpicFightCompat() {}

    public static void init() {
        String step = "EpicFightCapabilities";
        try {
            ClassLoader cl = EpicFightCompat.class.getClassLoader();
            Class<?> caps = Class.forName(CAPS, false, cl);
            getPatch = L.findStatic(caps, "getServerPlayerPatchAsOptional", MethodType.methodType(Optional.class, Entity.class));
            step = "LivingEntityPatch.playAnimationInClientSide";
            Class<?> living = Class.forName(LIVING_PATCH, false, cl);
            playClientSide = L.findVirtual(living, "playAnimationInClientSide",
                    MethodType.methodType(void.class, Class.forName(ASSET_ACCESSOR, false, cl), float.class));
            step = "Animations";
            animationsClass = Class.forName(ANIMATIONS, false, cl);
            animationsReady = true;
            animationsError = null;
            SoloStatus.LOGGER.info("Epic Fight: анимации навыков подключены");
        } catch (Throwable t) {
            animationsError = step + ": " + t;
            SoloStatus.LOGGER.warn("Epic Fight: анимации навыков недоступны ({}). Навыки будут использовать ванильный взмах.", animationsError);
        }
        try {
            Class<?> playerPatch = Class.forName("yesman.epicfight.world.capabilities.entitypatch.player.PlayerPatch", false, EpicFightCompat.class.getClassLoader());
            toEpicFightMode = L.findVirtual(playerPatch, "toEpicFightMode", MethodType.methodType(void.class, boolean.class));
            toVanillaMode = L.findVirtual(playerPatch, "toVanillaMode", MethodType.methodType(void.class, boolean.class));
            isEpicFightMode = L.findVirtual(playerPatch, "isEpicFightMode", MethodType.methodType(boolean.class));
            modeError = null;
            SoloStatus.LOGGER.info("Epic Fight: боевой режим включается только с оружием в руке");
        } catch (Throwable t) {
            modeError = t.toString();
            SoloStatus.LOGGER.warn("Epic Fight: автопереключение режима недоступно ({})", modeError);
        }
        try {
            hookDodge();
            hookTakeDamage();
            eventsHooked = true;
            eventsError = null;
            SoloStatus.LOGGER.info("Epic Fight: уклонения и парирования засчитываются в прогресс системы");
        } catch (Throwable t) {
            eventsError = t.toString();
            SoloStatus.LOGGER.warn("Epic Fight: события уклонения/парирования недоступны ({})", t.toString());
        }
    }

    public static boolean modeSwitchReady() { return modeError == null; }
    public static String modeError() { return modeError; }

    /**
     * Battle mode only with a weapon in hand: tools, blocks, food and an empty hand put the player in Epic Fight's
     * vanilla (mining) mode, a weapon switches to battle mode. Applied when the held item changes, so the player can
     * still toggle by hand while holding the same item.
     */
    public static void matchModeToHeldItem(ServerPlayer player, boolean weapon) {
        if (modeError != null || getPatch == null) return;
        try {
            Optional<?> patch = (Optional<?>) getPatch.invoke((Entity) player);
            if (patch.isEmpty()) return;
            boolean battle = (boolean) isEpicFightMode.invoke(patch.get());
            if (weapon && !battle) toEpicFightMode.invoke(patch.get(), true);
            else if (!weapon && battle) toVanillaMode.invoke(patch.get(), true);
        } catch (Throwable ignored) {
        }
    }

    /** Test helper: whether Epic Fight's battle mode is on for this player (null if it has no patch). */
    public static Boolean battleMode(ServerPlayer player) {
        try {
            Optional<?> patch = (Optional<?>) getPatch.invoke((Entity) player);
            return patch.isEmpty() ? null : (Boolean) (boolean) isEpicFightMode.invoke(patch.get());
        } catch (Throwable t) {
            return null;
        }
    }

    /** For the integration test: the patch → entity path used by every dodge/parry handler is resolved. */
    public static boolean patchAccessReady() { return getEntityPatch != null && getOriginal != null; }

    /** Для проверки интеграции: анимации и события Epic Fight подключены. */
    public static boolean animationsReady() { return animationsReady; }
    public static boolean eventsHooked() { return eventsHooked; }
    /** Why the part did not connect (null when it did). */
    public static String animationsError() { return animationsError; }
    public static String eventsError() { return eventsError; }

    /** Имена анимаций, которых нет в этой версии Epic Fight (проверяется наличие поля в классе Animations). */
    public static java.util.List<String> missingAnimations(java.util.Collection<String> names) {
        java.util.List<String> missing = new java.util.ArrayList<>();
        for (String n : names) {
            try {
                if (animationsClass.getField(n).get(null) == null) missing.add(n + " (not loaded)");
            } catch (Throwable t) {
                missing.add(n);
            }
        }
        return missing;
    }

    // ============================== Анимации ==============================

    /**
     * Проиграть анимацию Epic Fight (только визуально). Возвращает false, если Epic Fight нет
     * или анимация не найдена — тогда вызывающий код делает обычный взмах.
     */
    public static boolean playAnimation(ServerPlayer player, String animationName) {
        if (!animationsReady || animationName == null) return false;
        try {
            Optional<?> patch = (Optional<?>) getPatch.invoke((Entity) player);
            if (patch.isEmpty()) return false;
            Object anim = animation(animationName);
            if (anim == null) return false;
            playClientSide.invoke(patch.get(), anim, 0.0f);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static Object animation(String name) {
        Object cached = ANIM_CACHE.get(name);
        if (cached != null) return cached;
        try {
            Field f = animationsClass.getField(name);
            Object value = f.get(null); // поля заполняются после регистрации анимаций Epic Fight
            if (value != null) ANIM_CACHE.put(name, value);
            return value;
        } catch (Throwable t) {
            return null;
        }
    }

    // ============================== События ==============================

    private static void resolvePatchAccess() throws Exception {
        if (getOriginal != null) return;
        ClassLoader cl = EpicFightCompat.class.getClassLoader();
        Class<?> patchEvent = Class.forName(PATCH_EVENT, false, cl);
        Class<?> livingPatch = Class.forName(LIVING_PATCH, false, cl);
        try {
            getEntityPatch = firstVirtual(patchEvent, "getEntityPatch", livingPatch, Class.forName(ENTITY_PATCH, false, cl), Object.class);
        } catch (Exception e) {
            getEntityPatch = L.unreflect(patchEvent.getMethod("getEntityPatch"));
        }
        getOriginal = firstVirtual(Class.forName(ENTITY_PATCH, false, cl), "getOriginal", Entity.class, LivingEntity.class, Object.class);
    }

    /** findVirtual with the first return type that matches (generic methods erase to their bound). */
    private static MethodHandle firstVirtual(Class<?> owner, String name, Class<?>... returnTypes) throws Exception {
        Exception last = null;
        for (Class<?> r : returnTypes) {
            try {
                return L.findVirtual(owner, name, MethodType.methodType(r));
            } catch (NoSuchMethodException | IllegalAccessException e) {
                last = e;
            }
        }
        throw last;
    }

    private static void hookDodge() throws Exception {
        resolvePatchAccess();
        Object hook = Class.forName(HOOKS_ENTITY).getField("ON_DODGE").get(null);
        register(hook, event -> {
            ServerPlayer p = playerOf(event);
            if (p == null) return;
            PlayerData d = Hunter.get(p);
            long now = p.serverLevel().getGameTime();
            if (now - d.lastBlockTick > 10) d.addCounter("dodges", 1);
            d.lastBlockTick = now; // окно контратаки
        });
    }

    private static void hookTakeDamage() throws Exception {
        Object hook = Class.forName(HOOKS_ENTITY).getField("TAKE_DAMAGE_INCOME").get(null);
        resolvePatchAccess();
        // the event classes have no client-only members, so plain reflection is safe here (verified on a dedicated server)
        Class<?> income = Class.forName(INCOME_EVENT);
        Method isParried = income.getMethod("isParried");
        Method getResult = income.getMethod("getResult");
        Method getSource = income.getMethod("getDamageSource");
        register(hook, event -> {
            try {
                if (!income.isInstance(event)) return;
                ServerPlayer p = playerOf(event);
                if (p == null) return;
                boolean parried = (Boolean) isParried.invoke(event);
                Object result = getResult.invoke(event);
                boolean blocked = result != null && "BLOCKED".equals(((Enum<?>) result).name());
                if (!parried && !blocked) return;
                PlayerData d = Hunter.get(p);
                long now = p.serverLevel().getGameTime();
                Object rawSource = getSource.invoke(event);
                if (!(rawSource instanceof net.minecraft.world.damagesource.DamageSource source)) return;
                double fairness = source.getEntity() instanceof LivingEntity le ? Threat.fairness(le, p) : 0;
                if (now - d.lastBlockTick > 10 && fairness > 0.2) {
                    d.addCounter("blocks", 1);
                    if (parried) d.addCounter("perfect_guards", 1);
                }
                d.lastBlockTick = now;
            } catch (Throwable ignored) {
            }
        });
    }

    /** Глобальная подписка на хук Epic Fight с низким приоритетом (выполняется после навыков защиты). */
    private static void register(Object hook, Consumer<Object> handler) throws Exception {
        Class<?> subType = Class.forName(SUBSCRIPTION);
        InvocationHandler ih = (proxy, method, args) -> switch (method.getName()) {
            case "fire" -> {
                try {
                    handler.accept(args[0]);
                } catch (Throwable t) {
                    // ошибки обработчика не должны ломать Epic Fight
                }
                yield null;
            }
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
            case "toString" -> "SoloStatusEpicFightListener";
            default -> null;
        };
        Object subscription = Proxy.newProxyInstance(subType.getClassLoader(), new Class<?>[]{subType}, ih);
        Method registerEvent = Class.forName(EVENT_HOOK).getMethod("registerEvent", subType, int.class);
        registerEvent.invoke(hook, subscription, -100);
    }

    private static ServerPlayer playerOf(Object event) {
        try {
            Object patch = getEntityPatch.invoke(event);
            Object original = getOriginal.invoke(patch);
            return original instanceof ServerPlayer sp ? sp : null;
        } catch (Throwable t) {
            return null;
        }
    }
}
