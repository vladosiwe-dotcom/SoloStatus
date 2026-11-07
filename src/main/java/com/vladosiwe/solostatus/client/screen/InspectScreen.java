package com.vladosiwe.solostatus.client.screen;

import com.vladosiwe.solostatus.clazz.HunterClass;
import com.vladosiwe.solostatus.clazz.Specialization;
import com.vladosiwe.solostatus.combat.WeaponType;
import com.vladosiwe.solostatus.mastery.MasterySystem;
import com.vladosiwe.solostatus.rank.HunterRank;
import com.vladosiwe.solostatus.skill.SkillDef;
import com.vladosiwe.solostatus.skill.Skills;
import com.vladosiwe.solostatus.stats.Stat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Окно осмотра другого охотника. Показывает только те поля, которые прислал сервер:
 * базовая информация для всех, расширенная — при навыке «Оценка».
 */
public class InspectScreen extends Screen {
    private final int entityId;
    private final CompoundTag tag;
    private final long openTime = System.currentTimeMillis();
    private int left, top, w, h;
    private ItemStack hoverStack = ItemStack.EMPTY;

    public InspectScreen(int entityId, CompoundTag tag) {
        super(Component.translatable("solostatus.inspect.title"));
        this.entityId = entityId;
        this.tag = tag;
    }

    @Override
    protected void init() {
        w = Math.min(360, width - 16);
        h = Math.min(230, height - 16);
        left = (width - w) / 2;
        top = (height - h) / 2;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fillGradient(0, 0, width, height, 0x90000814, 0xA0001020);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        renderBackground(g, mx, my, partialTick);
        hoverStack = ItemStack.EMPTY;
        float a = Mth.clamp((System.currentTimeMillis() - openTime) / 200f, 0f, 1f);
        Holo.panel(g, left, top, w, h, a);
        if (a < 0.4f) return;

        LivingEntity target = minecraft != null && minecraft.level != null && minecraft.level.getEntity(entityId) instanceof Player p ? p : null;
        int x = left + 10, y = top + 8;
        g.drawString(font, Component.translatable("solostatus.inspect.title"), x, y, Holo.ACCENT, false);
        y += 14;

        int modelW = 90;
        Holo.box(g, x, y, modelW, h - 32, 0x803FB4FF);
        if (target != null) {
            InventoryScreen.renderEntityInInventoryFollowsMouse(g, x + 2, y + 2, x + modelW - 2, y + h - 34, 55, 0.0625f, mx, my, target);
        }

        int ix = x + modelW + 8;
        int iy = y;
        HunterRank rank = HunterRank.byIndex(tag.getInt("rank"));
        g.drawString(font, tag.getString("name"), ix, iy, Holo.TEXT, false);
        Component r = Component.translatable("solostatus.status.rank", rank.displayName());
        g.drawString(font, r, left + w - 10 - font.width(r), iy, rank.color, false);
        iy += 11;
        g.drawString(font, Component.translatable("solostatus.status.level", tag.getInt("level")), ix, iy, Holo.GOLD, false);
        iy += 12;

        if (tag.getBoolean("private")) {
            Holo.wrapped(g, font, Component.translatable("solostatus.inspect.private"), ix, iy, w - modelW - 30, Holo.DIM);
            return;
        }
        HunterClass hc = null;
        for (HunterClass c : HunterClass.VALUES) if (c.key.equals(tag.getString("class"))) hc = c;
        Specialization spec = null;
        for (Specialization s : Specialization.VALUES) if (s.key.equals(tag.getString("spec"))) spec = s;
        Component classLine = hc == null ? Component.translatable("solostatus.class.none") : spec == null ? hc.displayName()
                : Component.empty().append(hc.displayName()).append(" · ").append(tag.getBoolean("advanced") ? spec.advancedName() : spec.displayName());
        g.drawString(font, Component.translatable("solostatus.status.class", classLine), ix, iy, Holo.TEXT, false);
        iy += 13;

        // Характеристики
        if (tag.getBoolean("overwhelmed")) {
            iy += Holo.wrapped(g, font, Component.translatable("solostatus.inspect.overwhelmed"), ix, iy, w - modelW - 30, Holo.RED) + 4;
        } else {
            int[] stats = tag.getIntArray("stats");
            for (int i = 0; i < Math.min(stats.length, Stat.VALUES.length); i++) {
                Stat s = Stat.VALUES[i];
                int col = i % 3, row = i / 3;
                int sx = ix + col * 70, sy = iy + row * 11;
                g.drawString(font, s.shortName(), sx, sy, s.color, false);
                g.drawString(font, String.valueOf(stats[i]), sx + 30, sy, Holo.TEXT, false);
            }
            iy += 26;
        }

        int appraisal = tag.getInt("appraisal");
        if (appraisal <= 0) {
            Holo.wrapped(g, font, Component.translatable("solostatus.inspect.need_appraisal"), ix, iy, w - modelW - 30, Holo.DIM);
            return;
        }
        g.drawString(font, Component.translatable("solostatus.inspect.appraisal", appraisal), ix, iy, Holo.PURPLE, false);
        iy += 12;

        // Экипировка (видимые предметы цели на клиенте)
        if (tag.getBoolean("equipment") && target != null) {
            int ex = ix;
            for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                ItemStack st = target.getItemBySlot(slot);
                g.fill(ex, iy, ex + 18, iy + 18, 0x80102030);
                g.renderOutline(ex, iy, 18, 18, 0x603FB4FF);
                g.renderItem(st, ex + 1, iy + 1);
                if (!st.isEmpty() && Holo.inside(mx, my, ex + 1, iy + 1, 16, 16)) hoverStack = st;
                ex += 20;
            }
            iy += 22;
        }

        // Мастерство
        CompoundTag mastery = tag.getCompound("mastery");
        if (!mastery.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            int n = 0;
            for (String key : mastery.getAllKeys()) {
                if (n++ >= 6) break;
                String name = key.startsWith("magic:") ? MasterySystem.schoolName(key.substring(6)).getString() : WeaponType.byKey(key).displayName().getString();
                if (sb.length() > 0) sb.append(", ");
                sb.append(name).append(' ').append(mastery.getInt(key));
            }
            iy += Holo.wrapped(g, font, Component.translatable("solostatus.inspect.mastery", sb.toString()), ix, iy, w - modelW - 30, Holo.TEXT) + 2;
        }

        // Навыки
        ListTag skills = tag.getList("skills", Tag.TAG_COMPOUND);
        int sx = ix;
        for (int i = 0; i < skills.size(); i++) {
            CompoundTag s = skills.getCompound(i);
            SkillDef def = Skills.get(s.getString("id"));
            if (def == null) continue;
            if (sx + 18 > left + w - 10) { sx = ix; iy += 20; }
            if (iy + 18 > top + h - 6) break;
            g.renderItem(new ItemStack(def.icon), sx, iy);
            if (Holo.inside(mx, my, sx, iy, 16, 16)) {
                g.renderComponentTooltip(font, List.of(def.name(), Component.translatable("solostatus.skills.rank", s.getInt("rank"), def.maxRank)), mx, my);
            }
            sx += 18;
        }
        if (!hoverStack.isEmpty()) g.renderTooltip(font, hoverStack, mx, my);
    }
}
