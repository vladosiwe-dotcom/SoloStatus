package com.vladosiwe.solostatus.network;

import com.vladosiwe.solostatus.config.SoloConfig;
import com.vladosiwe.solostatus.data.Hunter;
import com.vladosiwe.solostatus.data.PlayerData;
import com.vladosiwe.solostatus.mastery.MasteryMath;
import com.vladosiwe.solostatus.skill.SkillDef;
import com.vladosiwe.solostatus.skill.SkillSource;
import com.vladosiwe.solostatus.skill.Skills;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Осмотр статуса другого игрока. Сервер сам решает, какие поля отправить:
 *  - всем: имя, уровень, ранг, класс, основные характеристики (если профиль не скрыт);
 *  - с навыком «Оценка»: мастерство, часть навыков (ранг 1 — 4, ранг 2 — 8, ранг 3 — все не скрытые), экипировка;
 *  - если цель выше рангом на 2+ ступени, а «Оценка» ниже 3 ранга — характеристики скрыты («???»).
 * Цель должна быть в пределах inspectRange и в прямой видимости.
 */
public final class InspectService {
    private InspectService() {}

    public static void handle(ServerPlayer viewer, int entityId) {
        if (!SoloConfig.ALLOW_INSPECT.get()) {
            Notices.warn(viewer, "solostatus.error.inspect_disabled");
            return;
        }
        Entity e = viewer.serverLevel().getEntity(entityId);
        if (!(e instanceof ServerPlayer target) || target == viewer) return;
        int range = SoloConfig.INSPECT_RANGE.get();
        if (viewer.distanceToSqr(target) > (double) range * range || !viewer.hasLineOfSight(target)) {
            Notices.warn(viewer, "solostatus.error.inspect_range");
            return;
        }
        PacketDistributor.sendToPlayer(viewer, new Payloads.InspectResult(entityId, build(Hunter.get(viewer), Hunter.get(target), target)));
    }

    static CompoundTag build(PlayerData viewer, PlayerData t, ServerPlayer target) {
        CompoundTag tag = new CompoundTag();
        tag.putString("name", target.getGameProfile().getName());
        tag.putInt("level", t.level);
        tag.putInt("rank", t.rank.ordinal());
        tag.putBoolean("private", t.privateProfile);
        if (t.privateProfile) return tag;

        if (t.hunterClass != null) tag.putString("class", t.hunterClass.key);
        if (t.specialization != null) tag.putString("spec", t.specialization.key);
        tag.putBoolean("advanced", t.advanced);

        int appraisal = viewer.skillRank("appraisal");
        boolean overwhelmed = t.rank.ordinal() - viewer.rank.ordinal() >= 2 && appraisal < 3;
        tag.putBoolean("overwhelmed", overwhelmed);
        if (!overwhelmed) tag.putIntArray("stats", t.stats.clone());

        tag.putInt("appraisal", appraisal);
        if (appraisal <= 0 || overwhelmed) return tag;

        CompoundTag mastery = new CompoundTag();
        t.mastery.forEach((k, v) -> {
            int lvl = MasteryMath.level(v);
            if (lvl > 0) mastery.putInt(k, lvl);
        });
        tag.put("mastery", mastery);

        int limit = appraisal == 1 ? 4 : appraisal == 2 ? 8 : Integer.MAX_VALUE;
        ListTag skills = new ListTag();
        int n = 0;
        for (var entry : t.skills.entrySet()) {
            SkillDef def = Skills.get(entry.getKey());
            if (def == null || def.source == SkillSource.HIDDEN) continue;
            if (n++ >= limit) break;
            CompoundTag s = new CompoundTag();
            s.putString("id", entry.getKey());
            s.putInt("rank", entry.getValue());
            skills.add(s);
        }
        tag.put("skills", skills);
        tag.putInt("rankScore", t.rankScore);
        tag.putInt("achievements", t.achievements.size());
        tag.putBoolean("equipment", true);
        return tag;
    }
}
