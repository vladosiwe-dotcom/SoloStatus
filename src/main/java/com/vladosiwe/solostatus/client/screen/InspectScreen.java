package com.vladosiwe.solostatus.client.screen;

import com.vladosiwe.solostatus.client.ui.SkillIcons;
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
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.List;

import static com.vladosiwe.solostatus.client.ui.SysDraw.*;
import static com.vladosiwe.solostatus.client.ui.SystemFonts.*;

/**
 * Окно осмотра другого охотника в стиле окна Системы: стеклянное окно с табличкой, модель цели слева,
 * сведения справа. Показывает только то, что прислал сервер: базовое для всех, подробное — при навыке «Оценка».
 */
public class InspectScreen extends Screen {
    private static final Stat[] ORDER = {Stat.STR, Stat.VIT, Stat.AGI, Stat.INT, Stat.PER, Stat.LUCK};
    private final int entityId;
    private final CompoundTag tag;
    private final long openTime = System.currentTimeMillis();
    private int left, top, w, h;
    private ItemStack hoverStack = ItemStack.EMPTY;
    private List<Component> tooltip;

    public InspectScreen(int entityId, CompoundTag tag) {
        super(Component.translatable("solostatus.inspect.title"));
        this.entityId = entityId;
        this.tag = tag;
    }

    @Override
    protected void init() {
        w = Mth.clamp((int) (width * 0.62f), 300, 400);
        h = Mth.clamp((int) (height * 0.72f), 196, 250);
        left = (width - w) / 2;
        top = (height - h) / 2 + 4;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private float since() {
        return System.currentTimeMillis() - openTime;
    }

    /** Строки проявляются по очереди после раскрытия окна. */
    private float row(int i) {
        return Mth.clamp((since() - 220 - i * 35) / 140f, 0f, 1f);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        float t = Mth.clamp(since() / 200f, 0f, 1f);
        g.fillGradient(0, 0, width, height, a(0xA0040A14, t), a(0xC0061424, t));
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        renderBackground(g, mx, my, partialTick);
        hoverStack = ItemStack.EMPTY;
        tooltip = null;
        int vis = window(g, left, top, w, h, since() / 220f, 1f);
        if (vis < h - 1) return;
        titlePlate(g, font, trBold("solostatus.inspect.plate"), left + w / 2, top - 9, 1f);

        LivingEntity target = minecraft != null && minecraft.level != null && minecraft.level.getEntity(entityId) instanceof Player p ? p : null;

        // модель цели
        int mw = Math.min(96, w / 3 - 6);
        int mx0 = left + 12, my0 = top + 16, mh = h - 28;
        g.fill(mx0, my0, mx0 + mw, my0 + mh, a(0x60061424, row(0)));
        g.renderOutline(mx0, my0, mw, mh, a(0x6655C8FF, row(0)));
        if (target != null && row(0) > 0.3f) {
            InventoryScreen.renderEntityInInventoryFollowsMouse(g, mx0 + 2, my0 + 2, mx0 + mw - 2, my0 + mh - 2, Math.max(30, mh / 3), 0.0625f, mx, my, target);
        }

        int x = mx0 + mw + 12, cw = left + w - 12 - x;
        int y = top + 16;

        // имя, ранг, уровень, класс
        HunterRank rank = HunterRank.byIndex(tag.getInt("rank"));
        text(g, font, bold(tag.getString("name")), x, y, INK, row(1));
        Component rl = title(rank.letter);
        textRight(g, font, rl, x + cw, y - 2, rank.color | 0xFF000000, row(1));
        y += 12;
        HunterClass hc = null;
        for (HunterClass c : HunterClass.VALUES) if (c.key.equals(tag.getString("class"))) hc = c;
        Specialization spec = null;
        for (Specialization s : Specialization.VALUES) if (s.key.equals(tag.getString("spec"))) spec = s;
        Component cls = hc == null ? Component.translatable("solostatus.class.none") : spec == null ? hc.displayName()
                : Component.empty().append(hc.displayName()).append(" · ").append(tag.getBoolean("advanced") ? spec.advancedName() : spec.displayName());
        Component lvl = trBold("solostatus.ui.lv", tag.getInt("level"));
        text(g, font, lvl, x, y, GOLD, row(1));
        text(g, font, body(cls), x + font.width(lvl) + 8, y, DIM, row(1));
        y += 13;
        divider(g, x, y, cw, row(2));
        y += 6;

        if (tag.getBoolean("private")) {
            wrap(g, body(Component.translatable("solostatus.inspect.private")), x, y, cw, DIM, row(3));
            renderFooter(g);
            return;
        }

        // характеристики: две колонки, как в окне STATUS
        if (tag.getBoolean("overwhelmed")) {
            y += wrap(g, body(Component.translatable("solostatus.inspect.overwhelmed")), x, y, cw, RED, row(3)) + 4;
        } else {
            int[] stats = tag.getIntArray("stats");
            int colW = (cw - 10) / 2;
            for (int i = 0; i < ORDER.length; i++) {
                Stat s = ORDER[i];
                if (s.ordinal() >= stats.length) continue;
                int sx = x + (i % 2) * (colW + 10), sy = y + (i / 2) * 12;
                float ra = row(3 + i / 2);
                text(g, font, bold(s.shortName()), sx, sy, DIM, ra);
                textRight(g, font, bold(String.valueOf(stats[s.ordinal()])), sx + colW, sy, INK, ra);
            }
            y += 38;
        }
        divider(g, x, y, cw, row(6));
        y += 6;

        int appraisal = tag.getInt("appraisal");
        if (appraisal <= 0) {
            wrap(g, body(Component.translatable("solostatus.inspect.need_appraisal")), x, y, cw, DIM, row(7));
            renderFooter(g);
            return;
        }
        text(g, font, trBold("solostatus.inspect.appraisal", appraisal), x, y, VIOLET, row(7));
        y += 12;

        // экипировка
        if (tag.getBoolean("equipment") && target != null) {
            int ex = x;
            for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                ItemStack st = target.getItemBySlot(slot);
                g.fill(ex, y, ex + 18, y + 18, a(0x90061424, row(8)));
                g.renderOutline(ex, y, 18, 18, a(st.isEmpty() ? 0x4055C8FF : 0x9955C8FF, row(8)));
                if (row(8) > 0.5f) g.renderItem(st, ex + 1, y + 1);
                if (!st.isEmpty() && inside(mx, my, ex + 1, y + 1, 16, 16)) hoverStack = st;
                ex += 20;
            }
            y += 22;
        }

        // мастерство
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
            y += wrap(g, body(Component.translatable("solostatus.inspect.mastery", sb.toString())), x, y, cw, INK, row(9)) + 3;
        }

        // навыки: иконки в цвете происхождения, имя и ранг во всплывающей подсказке
        ListTag skills = tag.getList("skills", Tag.TAG_COMPOUND);
        int sx = x;
        for (int i = 0; i < skills.size(); i++) {
            CompoundTag s = skills.getCompound(i);
            SkillDef def = Skills.get(s.getString("id"));
            if (def == null) continue;
            if (sx + 16 > x + cw) { sx = x; y += 18; }
            if (y + 16 > top + h - 14) break;
            g.renderOutline(sx, y, 16, 16, a(0x4055C8FF, row(10)));
            SkillIcons.draw(g, def, sx + 2, y + 2, 12, true, row(10));
            if (inside(mx, my, sx, y, 16, 16)) {
                tooltip = List.of(def.name(), Component.translatable("solostatus.skills.rank", s.getInt("rank"), def.maxRank));
            }
            sx += 18;
        }
        renderFooter(g);
        if (!hoverStack.isEmpty()) g.renderTooltip(font, hoverStack, mx, my);
        else if (tooltip != null) g.renderComponentTooltip(font, tooltip, mx, my);
    }

    private void renderFooter(GuiGraphics g) {
        textRight(g, font, tr("solostatus.inspect.close_hint"), left + w - 10, top + h - 12, 0xFF4F6F8F, row(4));
    }

    private int wrap(GuiGraphics g, Component text, int x, int y, int width, int color, float alpha) {
        int yy = y;
        for (FormattedCharSequence line : font.split(text, width)) {
            if (alpha >= 0.03f) g.drawString(font, line, x, yy, a(color, alpha), false);
            yy += 10;
        }
        return yy - y;
    }
}
