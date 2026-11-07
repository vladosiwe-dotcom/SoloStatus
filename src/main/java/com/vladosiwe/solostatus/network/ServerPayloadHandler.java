package com.vladosiwe.solostatus.network;

import com.vladosiwe.solostatus.clazz.ClassSystem;
import com.vladosiwe.solostatus.clazz.HunterClass;
import com.vladosiwe.solostatus.clazz.Specialization;
import com.vladosiwe.solostatus.config.SoloConfig;
import com.vladosiwe.solostatus.data.Hunter;
import com.vladosiwe.solostatus.data.PlayerData;
import com.vladosiwe.solostatus.skill.SkillSystem;
import com.vladosiwe.solostatus.stats.AttributeSystem;
import com.vladosiwe.solostatus.stats.Stat;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Обработка запросов клиента. Каждое значение проверяется на сервере:
 * клиент не может сам изменить уровень, очки, навыки, редкость оружия или прогресс достижений.
 */
public final class ServerPayloadHandler {
    private ServerPayloadHandler() {}

    private static void run(IPayloadContext ctx, java.util.function.Consumer<ServerPlayer> action) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer p) action.accept(p);
        });
    }

    public static void allocateStat(Payloads.AllocateStat msg, IPayloadContext ctx) {
        run(ctx, p -> {
            PlayerData d = Hunter.get(p);
            Stat stat = Stat.byIndex(msg.stat());
            if (stat == null || !d.hasClass()) return;
            int max = SoloConfig.MAX_STAT_VALUE.get();
            int amount = Math.max(1, Math.min(msg.amount(), d.freeStatPoints));
            amount = Math.min(amount, max - d.stat(stat));
            if (amount <= 0 || d.freeStatPoints < amount) return;
            d.freeStatPoints -= amount;
            d.stats[stat.ordinal()] += amount;
            AttributeSystem.apply(p);
            d.markDirty();
        });
    }

    public static void chooseClass(Payloads.ChooseClass msg, IPayloadContext ctx) {
        run(ctx, p -> ClassSystem.chooseClass(p, HunterClass.byIndex(msg.index())));
    }

    public static void chooseSpec(Payloads.ChooseSpec msg, IPayloadContext ctx) {
        run(ctx, p -> ClassSystem.chooseSpec(p, Specialization.byIndex(msg.index())));
    }

    public static void learnSkill(Payloads.LearnSkill msg, IPayloadContext ctx) {
        if (msg.id().length() > 64) return;
        run(ctx, p -> SkillSystem.learnOrUpgrade(p, msg.id()));
    }

    public static void equipSkill(Payloads.EquipSkill msg, IPayloadContext ctx) {
        if (msg.id().length() > 64) return;
        run(ctx, p -> SkillSystem.equip(p, msg.slot(), msg.id()));
    }

    public static void castSkill(Payloads.CastSkill msg, IPayloadContext ctx) {
        run(ctx, p -> SkillSystem.cast(p, msg.slot()));
    }

    public static void respec(Payloads.Respec msg, IPayloadContext ctx) {
        if (msg.mode() != 0 && msg.mode() != 1) return;
        run(ctx, p -> SkillSystem.respec(p, msg.mode()));
    }

    public static void inspect(Payloads.InspectRequest msg, IPayloadContext ctx) {
        run(ctx, p -> InspectService.handle(p, msg.entityId()));
    }

    public static void markSeen(Payloads.MarkSeen msg, IPayloadContext ctx) {
        run(ctx, p -> {
            PlayerData d = Hunter.get(p);
            if (msg.what() == 0) d.unseenAchievements.clear();
            else d.unseenSkills.clear();
            d.dirty = true;
        });
    }

    public static void setPrivacy(Payloads.SetPrivacy msg, IPayloadContext ctx) {
        run(ctx, p -> {
            PlayerData d = Hunter.get(p);
            d.privateProfile = msg.hidden();
            d.dirty = true;
        });
    }
}
