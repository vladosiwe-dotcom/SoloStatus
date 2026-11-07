package com.vladosiwe.solostatus.client.screen;

import com.vladosiwe.solostatus.achievement.AchievementDef;
import com.vladosiwe.solostatus.achievement.Achievements;
import com.vladosiwe.solostatus.client.ClientState;
import com.vladosiwe.solostatus.client.Keys;
import com.vladosiwe.solostatus.clazz.ClassSystem;
import com.vladosiwe.solostatus.clazz.HunterClass;
import com.vladosiwe.solostatus.clazz.Specialization;
import com.vladosiwe.solostatus.combat.WeaponType;
import com.vladosiwe.solostatus.config.SoloConfig;
import com.vladosiwe.solostatus.data.PlayerData;
import com.vladosiwe.solostatus.level.LevelSystem;
import com.vladosiwe.solostatus.mastery.MasteryMath;
import com.vladosiwe.solostatus.mastery.MasterySystem;
import com.vladosiwe.solostatus.network.Payloads;
import com.vladosiwe.solostatus.quality.QualitySystem;
import com.vladosiwe.solostatus.quality.WeaponQuality;
import com.vladosiwe.solostatus.registry.ModItems;
import com.vladosiwe.solostatus.skill.*;
import com.vladosiwe.solostatus.stats.Stat;
import com.vladosiwe.solostatus.stats.StatFormulas;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Голографическое окно статуса охотника.
 * Вкладки: Status, Skills, Class, Mastery, Equipment, Achievements.
 * Окно только отображает данные сервера и отправляет запросы; все изменения проверяет сервер.
 */
public class StatusScreen extends Screen {
    public enum Tab { STATUS, SKILLS, CLASS, MASTERY, EQUIPMENT, ACHIEVEMENTS }

    private enum SkillPage { CLASS, SPEC, AWAKENED }

    private record Hotspot(int x, int y, int w, int h, Runnable action) {}

    private static Tab lastTab = Tab.STATUS;

    private Tab tab;
    private SkillPage skillPage = SkillPage.CLASS;
    @Nullable private String selectedSkill;
    private final long openTime = System.currentTimeMillis();
    private long tabTime = System.currentTimeMillis();
    private int scroll;
    private final Set<String> newAchievements = new HashSet<>();
    private final List<Hotspot> hotspots = new ArrayList<>();
    private List<Component> tooltip = null;
    private ItemStack tooltipStack = ItemStack.EMPTY;

    private int left, top, w, h, cx, cy, cw, ch;

    public StatusScreen() {
        this(ClientState.data.hasClass() ? lastTab : Tab.CLASS);
    }

    public StatusScreen(Tab tab) {
        super(Component.translatable("solostatus.screen.title"));
        this.tab = tab;
        if (tab == Tab.ACHIEVEMENTS) onOpenAchievements();
    }

    private PlayerData d() {
        return ClientState.data;
    }

    @Override
    protected void init() {
        w = Math.min(440, width - 16);
        h = Math.min(262, height - 16);
        left = (width - w) / 2;
        top = (height - h) / 2;
        cx = left + 10;
        cy = top + 44;
        cw = w - 20;
        ch = h - 52;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private float openAnim() {
        return Mth.clamp((System.currentTimeMillis() - openTime) / 220f, 0f, 1f);
    }

    // =====================================================================
    // Отрисовка
    // =====================================================================

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        float a = openAnim();
        g.fillGradient(0, 0, width, height, Holo.alpha(0x90000814, a), Holo.alpha(0xA0001020, a));
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        renderBackground(g, mx, my, partialTick);
        hotspots.clear();
        tooltip = null;
        tooltipStack = ItemStack.EMPTY;

        float a = openAnim();
        float eased = 1 - (1 - a) * (1 - a);
        g.pose().pushPose();
        float scale = 0.85f + 0.15f * eased;
        g.pose().translate(width / 2f, height / 2f, 0);
        g.pose().scale(scale, scale, 1);
        g.pose().translate(-width / 2f, -height / 2f, 0);

        Holo.panel(g, left, top, w, h, eased);
        if (a > 0.35f) {
            renderHeader(g, mx, my);
            renderTabs(g, mx, my);
            switch (tab) {
                case STATUS -> renderStatus(g, mx, my);
                case SKILLS -> renderSkills(g, mx, my);
                case CLASS -> renderClass(g, mx, my);
                case MASTERY -> renderMastery(g, mx, my);
                case EQUIPMENT -> renderEquipment(g, mx, my);
                case ACHIEVEMENTS -> renderAchievements(g, mx, my);
            }
            // мягкое появление содержимого при смене вкладки
            float ta = Mth.clamp((System.currentTimeMillis() - tabTime) / 150f, 0f, 1f);
            if (ta < 1f) g.fill(cx, cy, cx + cw, cy + ch, Holo.alpha(0xFF061424, 1 - ta));
        }
        g.pose().popPose();

        if (a >= 1f) {
            if (!tooltipStack.isEmpty()) g.renderTooltip(font, tooltipStack, mx, my);
            else if (tooltip != null) g.renderComponentTooltip(font, tooltip, mx, my);
        }
    }

    private void renderHeader(GuiGraphics g, int mx, int my) {
        PlayerData d = d();
        g.drawString(font, Component.translatable("solostatus.screen.title"), left + 10, top + 7, Holo.ACCENT, false);
        String name = minecraft != null && minecraft.player != null ? minecraft.player.getGameProfile().getName() : "";
        Component right = Component.translatable("solostatus.screen.header", name, d.level, d.rank.displayName());
        g.drawString(font, right, left + w - 10 - font.width(right), top + 7, Holo.TEXT, false);
    }

    private void renderTabs(GuiGraphics g, int mx, int my) {
        Tab[] tabs = Tab.values();
        int tw = (w - 20) / tabs.length;
        int y = top + 22;
        for (int i = 0; i < tabs.length; i++) {
            Tab t = tabs[i];
            int x = left + 10 + i * tw;
            boolean sel = t == tab;
            boolean hover = Holo.inside(mx, my, x, y, tw - 2, 16);
            g.fill(x, y, x + tw - 2, y + 16, sel ? 0xC02A6FA8 : hover ? 0x902A6FA8 : 0x80102030);
            g.renderOutline(x, y, tw - 2, 16, sel ? 0xFFFFFFFF : Holo.BORDER);
            Component label = Component.translatable("solostatus.tab." + t.name().toLowerCase());
            boolean badge = (t == Tab.ACHIEVEMENTS && !d().unseenAchievements.isEmpty())
                    || (t == Tab.SKILLS && !d().unseenSkills.isEmpty())
                    || (t == Tab.STATUS && d().freeStatPoints > 0)
                    || (t == Tab.CLASS && canPickSpec());
            g.drawCenteredString(font, label, x + (tw - 2) / 2, y + 4, sel ? Holo.TEXT : Holo.DIM);
            if (badge) g.fill(x + tw - 7, y + 2, x + tw - 4, y + 5, Holo.GOLD);
            hotspots.add(new Hotspot(x, y, tw - 2, 16, () -> switchTab(t)));
        }
    }

    private void switchTab(Tab t) {
        if (t == tab) return;
        if (tab == Tab.SKILLS) PacketDistributor.sendToServer(new Payloads.MarkSeen(1));
        tab = t;
        lastTab = t;
        scroll = 0;
        tabTime = System.currentTimeMillis();
        if (t == Tab.ACHIEVEMENTS) onOpenAchievements();
    }

    private void onOpenAchievements() {
        newAchievements.clear();
        newAchievements.addAll(d().unseenAchievements);
        if (!newAchievements.isEmpty()) PacketDistributor.sendToServer(new Payloads.MarkSeen(0));
    }

    private void button(GuiGraphics g, int x, int y, int bw, int bh, Component text, int mx, int my, boolean enabled, Runnable action) {
        Holo.button(g, font, x, y, bw, bh, text, mx, my, enabled);
        if (enabled) hotspots.add(new Hotspot(x, y, bw, bh, action));
    }

    private void hoverTip(int mx, int my, int x, int y, int bw, int bh, List<Component> lines) {
        if (Holo.inside(mx, my, x, y, bw, bh)) tooltip = lines;
    }

    private void hoverItem(int mx, int my, int x, int y, ItemStack stack) {
        if (!stack.isEmpty() && Holo.inside(mx, my, x, y, 16, 16)) tooltipStack = stack;
    }

    // =====================================================================
    // STATUS
    // =====================================================================

    private void renderStatus(GuiGraphics g, int mx, int my) {
        PlayerData d = d();
        int modelW = 96;
        Holo.box(g, cx, cy, modelW, ch, 0x803FB4FF);
        if (minecraft != null && minecraft.player != null) {
            int scale = (int) Math.min(62, ch / 3.3);
            InventoryScreen.renderEntityInInventoryFollowsMouse(g, cx + 2, cy + 2, cx + modelW - 2, cy + ch - 2, scale, 0.0625f, mx, my, minecraft.player);
        }

        int x = cx + modelW + 8;
        int rw = cw - modelW - 8;
        int y = cy;
        Component classLine = d.hunterClass == null ? Component.translatable("solostatus.class.none")
                : d.specialization == null ? d.hunterClass.displayName()
                : Component.empty().append(d.hunterClass.displayName()).append(" · ").append(d.advanced ? d.specialization.advancedName() : d.specialization.displayName());
        g.drawString(font, Component.translatable("solostatus.status.class", classLine), x, y, Holo.TEXT, false);
        // Ранг
        Component rank = Component.translatable("solostatus.status.rank", d.rank.displayName());
        int rx = x + rw - font.width(rank) - 4;
        g.drawString(font, rank, rx, y, d.rank.color, false);
        hoverTip(mx, my, rx, y, font.width(rank), 9, List.of(Component.translatable("solostatus.status.rank_score", d.rankScore),
                Component.translatable("solostatus.status.rank_hint")));
        y += 12;

        // Опыт
        if (d.level < PlayerData.MAX_LEVEL) {
            long need = LevelSystem.xpToNext(d.level);
            g.drawString(font, Component.translatable("solostatus.status.level", d.level), x, y, Holo.GOLD, false);
            String xpText = d.xp + " / " + need;
            g.drawString(font, xpText, x + rw - font.width(xpText) - 4, y, Holo.DIM, false);
            Holo.bar(g, x, y + 10, rw - 4, 4, d.xp / (double) need, 0xFFFFD36B);
        } else {
            long need = LevelSystem.overflowNeed(d.overflowAwakenings);
            g.drawString(font, Component.translatable("solostatus.status.limit", d.overflowAwakenings, LevelSystem.MAX_OVERFLOW_AWAKENINGS), x, y, Holo.GOLD, false);
            Holo.bar(g, x, y + 10, rw - 4, 4, d.overflowXp / (double) need, 0xFFFF9B3F);
        }
        y += 18;

        // Здоровье и энергия
        if (minecraft != null && minecraft.player != null) {
            float hp = minecraft.player.getHealth(), maxHp = minecraft.player.getMaxHealth();
            g.drawString(font, Component.translatable("solostatus.status.hp", Math.round(hp), Math.round(maxHp)), x, y, 0xFFFF7A7A, false);
            Holo.bar(g, x + 70, y + 2, rw / 2 - 74, 4, hp / Math.max(1, maxHp), 0xFFFF5A5A);
            float maxE = (float) StatFormulas.maxEnergy(d);
            int ex = x + rw / 2;
            g.drawString(font, Component.translatable("solostatus.status.energy", Math.round(d.energy), Math.round(maxE)), ex, y, 0xFF6EC8FF, false);
            Holo.bar(g, ex + 70, y + 2, rw / 2 - 74, 4, d.energy / Math.max(1, maxE), 0xFF3FB4FF);
        }
        y += 14;

        // Характеристики
        Holo.box(g, x, y, rw - 4, 6 * 15 + 16, 0x603FB4FF);
        Component pts = Component.translatable("solostatus.status.free_points", d.freeStatPoints);
        g.drawString(font, pts, x + 4, y + 4, d.freeStatPoints > 0 ? Holo.GOLD : Holo.DIM, false);
        int max = maxStat();
        int sy = y + 16;
        for (Stat s : Stat.VALUES) {
            int value = d.stat(s);
            g.drawString(font, s.shortName(), x + 4, sy, s.color, false);
            g.drawString(font, String.valueOf(value), x + 34, sy, Holo.TEXT, false);
            g.drawString(font, statEffect(d, s), x + 58, sy, Holo.DIM, false);
            hoverTip(mx, my, x + 2, sy - 2, rw - 30, 13, List.of(s.fullName().copy().withColor(s.color), s.description(),
                    Component.translatable("solostatus.status.soft_cap", softCap())));
            boolean canAdd = d.freeStatPoints > 0 && value < max && d.hasClass();
            Stat stat = s;
            button(g, x + rw - 22, sy - 2, 14, 12, Component.literal("+"), mx, my, canAdd, () -> {
                int amount = hasShiftDown() ? 5 : 1;
                PacketDistributor.sendToServer(new Payloads.AllocateStat(stat.ordinal(), amount));
            });
            sy += 15;
        }
        y += 6 * 15 + 22;

        // Основная экипировка
        if (minecraft != null && minecraft.player != null && y + 18 <= cy + ch) {
            g.drawString(font, Component.translatable("solostatus.status.equipment"), x, y + 4, Holo.DIM, false);
            int ix = x + font.width(Component.translatable("solostatus.status.equipment")) + 6;
            for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                ItemStack st = minecraft.player.getItemBySlot(slot);
                g.fill(ix, y, ix + 18, y + 18, 0x80102030);
                g.renderOutline(ix, y, 18, 18, qualityColor(st));
                g.renderItem(st, ix + 1, y + 1);
                hoverItem(mx, my, ix + 1, y + 1, st);
                ix += 20;
            }
        }
    }

    private int maxStat() {
        try { return SoloConfig.MAX_STAT_VALUE.get(); } catch (Exception e) { return 150; }
    }

    private int softCap() {
        try { return SoloConfig.STAT_SOFT_CAP.get(); } catch (Exception e) { return 40; }
    }

    private Component statEffect(PlayerData d, Stat s) {
        return switch (s) {
            case STR -> Component.translatable("solostatus.effect.str", Holo.pct(StatFormulas.physicalDamage(d)));
            case AGI -> Component.translatable("solostatus.effect.agi", Holo.pct(StatFormulas.moveSpeed(d)), Holo.pct(StatFormulas.dodgeChance(d)));
            case PER -> Component.translatable("solostatus.effect.per", Holo.pct(StatFormulas.critChance(d)), Holo.pct(StatFormulas.projectileDamage(d)));
            case VIT -> Component.translatable("solostatus.effect.vit", Math.round(StatFormulas.bonusHealth(d) * 10) / 10.0, Holo.pct(StatFormulas.damageReduction(d)));
            case INT -> Component.translatable("solostatus.effect.int", Holo.pct(StatFormulas.magicPower(d)), Math.round(StatFormulas.maxEnergy(d)));
            case LUCK -> Component.translatable("solostatus.effect.luck", Holo.pct(StatFormulas.extraDropChance(d)), Holo.pct(StatFormulas.qualityBonus(d)));
        };
    }

    private int qualityColor(ItemStack st) {
        WeaponQuality q = QualitySystem.get(st);
        return q != null ? q.tier().color : 0x603FB4FF;
    }

    // =====================================================================
    // SKILLS
    // =====================================================================

    private void renderSkills(GuiGraphics g, int mx, int my) {
        PlayerData d = d();
        if (!d.hasClass()) {
            g.drawCenteredString(font, Component.translatable("solostatus.skills.no_class"), cx + cw / 2, cy + ch / 2, Holo.DIM);
            return;
        }
        int detailW = 152;
        int treeW = cw - detailW - 6;
        // Подвкладки
        SkillPage[] pages = SkillPage.values();
        int pw = (treeW - 4) / pages.length;
        for (int i = 0; i < pages.length; i++) {
            SkillPage page = pages[i];
            int px = cx + i * pw;
            boolean sel = page == skillPage;
            g.fill(px, cy, px + pw - 2, cy + 13, sel ? 0xC02A6FA8 : 0x80102030);
            g.renderOutline(px, cy, pw - 2, 13, sel ? 0xFFFFFFFF : 0x803FB4FF);
            g.drawCenteredString(font, Component.translatable("solostatus.skills.page." + page.name().toLowerCase()), px + (pw - 2) / 2, cy + 3, sel ? Holo.TEXT : Holo.DIM);
            hotspots.add(new Hotspot(px, cy, pw - 2, 13, () -> { skillPage = page; tabTime = System.currentTimeMillis(); }));
        }
        int ty = cy + 18;
        int th = ch - 18 - 26;
        Holo.box(g, cx, ty, treeW, th, 0x603FB4FF);

        List<SkillDef> list = new ArrayList<>();
        switch (skillPage) {
            case CLASS -> {
                list.addAll(Skills.forClass(d.hunterClass));
                SkillDef ult = Skills.ultimate(d.hunterClass);
                if (ult != null) list.add(ult);
            }
            case SPEC -> {
                for (Specialization s : d.hunterClass.specializations()) list.addAll(Skills.forSpec(s));
                Specialization[] specs = d.hunterClass.specializations();
                g.drawString(font, specs[0].displayName(), cx + 8, ty + 4, d.specialization == specs[0] ? Holo.ACCENT : Holo.DIM, false);
                g.drawString(font, specs[1].displayName(), cx + 8 + 2 * 48, ty + 4, d.specialization == specs[1] ? Holo.ACCENT : Holo.DIM, false);
            }
            case AWAKENED -> list.addAll(Skills.awakenings());
        }

        Map<String, int[]> pos = new HashMap<>();
        int ox = cx + 12, oy = ty + 18;
        if (skillPage == SkillPage.AWAKENED) {
            int perRow = Math.max(1, (treeW - 16) / 30);
            for (int i = 0; i < list.size(); i++) pos.put(list.get(i).id, new int[]{ox + (i % perRow) * 30, oy + (i / perRow) * 30});
        } else {
            for (SkillDef def : list) pos.put(def.id, new int[]{ox + def.treeColumn * 48, oy + def.treeRow * 36});
        }
        // Связи
        for (SkillDef def : list) {
            if (def.parent == null || !pos.containsKey(def.parent)) continue;
            int[] a = pos.get(def.parent), b = pos.get(def.id);
            int color = d.hasSkill(def.id) ? Holo.ACCENT : 0x804A6A8A;
            int ax = a[0] + 22, ay = a[1] + 11, bx = b[0], by = b[1] + 11;
            int midX = (ax + bx) / 2;
            g.fill(ax, ay, midX, ay + 1, color);
            g.fill(midX, Math.min(ay, by), midX + 1, Math.max(ay, by) + 1, color);
            g.fill(midX, by, bx, by + 1, color);
        }
        // Узлы
        for (SkillDef def : list) {
            int[] p = pos.get(def.id);
            if (p[1] + 22 > ty + th) continue;
            renderNode(g, d, def, p[0], p[1], mx, my);
        }
        if (selectedSkill == null || !pos.containsKey(selectedSkill)) {
            selectedSkill = list.isEmpty() ? null : list.get(0).id;
        }

        // Слоты
        int sy = cy + ch - 22;
        g.drawString(font, Component.translatable("solostatus.skills.slots"), cx, sy + 6, Holo.DIM, false);
        int slx = cx + font.width(Component.translatable("solostatus.skills.slots")) + 6;
        for (int i = 0; i < PlayerData.SKILL_SLOTS; i++) {
            int x = slx + i * 24;
            g.fill(x, sy, x + 20, sy + 20, 0x80102030);
            g.renderOutline(x, sy, 20, 20, Holo.BORDER);
            SkillDef def = Skills.get(d.slots[i]);
            if (def != null) {
                g.renderItem(new ItemStack(def.icon), x + 2, sy + 2);
                hoverTip(mx, my, x, sy, 20, 20, List.of(def.name(), Component.translatable("solostatus.skills.slot_clear")));
                int slot = i;
                hotspots.add(new Hotspot(x, sy, 20, 20, () -> PacketDistributor.sendToServer(new Payloads.EquipSkill(slot, ""))));
            }
            g.drawString(font, Keys.SKILLS[i].getTranslatedKeyMessage(), x + 1, sy - 8 < cy ? sy : sy + 21, Holo.DIM, false);
        }
        Component sp = Component.translatable("solostatus.skills.points", d.skillPoints);
        g.drawString(font, sp, cx + treeW - font.width(sp) - 2, sy + 6, d.skillPoints > 0 ? Holo.GOLD : Holo.DIM, false);

        renderSkillDetail(g, d, Skills.get(selectedSkill), cx + treeW + 6, cy, detailW, ch, mx, my);
    }

    private void renderNode(GuiGraphics g, PlayerData d, SkillDef def, int x, int y, int mx, int my) {
        boolean learned = d.hasSkill(def.id);
        boolean hiddenLocked = def.hidden() && !learned;
        boolean available = !learned && def.availableFor(d) && SkillSystem.canAdvance(d, def) == null;
        boolean wrongPath = !def.availableFor(d);
        int border = learned ? Holo.ACCENT : hiddenLocked ? Holo.PURPLE : available ? Holo.GOLD : Holo.LOCKED;
        if (available && (System.currentTimeMillis() / 400) % 2 == 0) border = 0xFFFFFFFF;
        boolean selected = def.id.equals(selectedSkill);
        g.fill(x, y, x + 22, y + 22, selected ? 0xD02A6FA8 : 0xC0081828);
        g.renderOutline(x, y, 22, 22, border);
        if (selected) g.renderOutline(x - 2, y - 2, 26, 26, 0x80FFFFFF);
        if (hiddenLocked) {
            g.drawCenteredString(font, "?", x + 11, y + 7, Holo.PURPLE);
        } else {
            g.renderItem(new ItemStack(def.icon), x + 3, y + 3);
            if (!learned) g.fill(x + 1, y + 1, x + 21, y + 21, wrongPath ? 0xB0000000 : 0x80000000);
        }
        g.pose().pushPose();
        g.pose().translate(0, 0, 200);
        if (learned) {
            String r = d.skillRank(def.id) + "/" + def.maxRank;
            g.drawString(font, r, x + 22 - font.width(r), y + 14, Holo.TEXT, true);
        }
        if (d.unseenSkills.contains(def.id)) g.drawString(font, "!", x + 1, y + 1, Holo.GOLD, true);
        g.pose().popPose();
        hotspots.add(new Hotspot(x, y, 22, 22, () -> selectedSkill = def.id));
        if (Holo.inside(mx, my, x, y, 22, 22)) tooltip = List.of(hiddenLocked ? Component.literal("???") : def.name());
    }

    private void renderSkillDetail(GuiGraphics g, PlayerData d, @Nullable SkillDef def, int x, int y, int dw, int dh, int mx, int my) {
        Holo.box(g, x, y, dw, dh, 0x803FB4FF);
        if (def == null) return;
        int tx = x + 5, tw = dw - 10;
        int yy = y + 5;
        boolean learned = d.hasSkill(def.id);
        boolean hiddenLocked = def.hidden() && !learned;
        int rank = d.skillRank(def.id);

        g.drawString(font, hiddenLocked ? Component.literal("???") : def.name(), tx, yy, hiddenLocked ? Holo.PURPLE : Holo.ACCENT, false);
        yy += 11;
        MutableComponent sub = Component.empty().append(def.category.displayName()).append(" · ")
                .append(Component.translatable("solostatus.source." + def.source.name().toLowerCase()));
        g.drawString(font, sub, tx, yy, Holo.DIM, false);
        yy += 10;
        g.drawString(font, Component.translatable("solostatus.skills.rank", rank, def.maxRank), tx, yy, Holo.TEXT, false);
        yy += 12;

        if (hiddenLocked) {
            yy += Holo.wrapped(g, font, def.hint(), tx, yy, tw, Holo.DIM);
            return;
        }
        yy += Holo.wrapped(g, font, def.description(), tx, yy, tw, Holo.TEXT) + 3;

        // Сравнение уровней навыка
        if (def.isActive()) {
            int cur = Math.max(1, rank);
            String cdNow = String.format(Locale.ROOT, "%.1f", def.cooldownFor(cur) / 20.0);
            String cdNext = rank > 0 && rank < def.maxRank ? " → " + String.format(Locale.ROOT, "%.1f", def.cooldownFor(rank + 1) / 20.0) : "";
            g.drawString(font, Component.translatable("solostatus.skills.cooldown", cdNow + cdNext), tx, yy, Holo.DIM, false);
            yy += 10;
            g.drawString(font, Component.translatable("solostatus.skills.energy", Math.round(SkillSystem.energyCost(d, def))), tx, yy, Holo.DIM, false);
            yy += 10;
        }
        if (rank > 0 && rank < def.maxRank) {
            g.drawString(font, Component.translatable("solostatus.skills.next_rank", Math.round(effectGain(def) * 100)), tx, yy, Holo.GREEN, false);
            yy += 11;
        }

        // Требования
        if (!learned) {
            g.drawString(font, Component.translatable("solostatus.skills.requirements"), tx, yy, Holo.GOLD, false);
            yy += 10;
            if (!def.availableFor(d)) {
                Component who = def.spec != null ? def.spec.displayName() : def.hunterClass != null ? def.hunterClass.displayName() : Component.empty();
                yy += Holo.wrapped(g, font, Component.translatable("solostatus.skills.only_for", who), tx, yy, tw, Holo.RED);
            }
            yy = requirementLine(g, Req.level(def.baseLevel), d, tx, yy, tw);
            for (Requirement r : def.requirements) yy = requirementLine(g, r, d, tx, yy, tw);
            if (def.source.autoAwaken()) {
                Holo.bar(g, tx, yy + 2, tw, 3, def.unlockProgress(d), 0xFFC08CFF);
                yy += 8;
                Holo.wrapped(g, font, Component.translatable("solostatus.skills.awaken_hint"), tx, yy, tw, Holo.DIM);
            }
        } else if (rank < def.maxRank) {
            int next = rank + 1;
            yy = requirementLine(g, Req.level(def.levelForRank(next)), d, tx, yy, tw);
        }

        // Кнопки
        int by = y + dh - 18;
        if (def.isActive() && learned) {
            int bw = (tw - 6) / 4;
            for (int i = 0; i < PlayerData.SKILL_SLOTS; i++) {
                int slot = i;
                boolean inSlot = def.id.equals(d.slots[i]);
                button(g, tx + i * (bw + 2), by - 16, bw, 14, Component.literal(inSlot ? "[" + (i + 1) + "]" : String.valueOf(i + 1)), mx, my, true,
                        () -> PacketDistributor.sendToServer(new Payloads.EquipSkill(slot, def.id)));
            }
        }
        boolean manual = !def.source.autoAwaken() && def.source != SkillSource.CLASS_START || learned;
        if (manual && rank < def.maxRank) {
            String err = SkillSystem.canAdvance(d, def);
            int cost = def.costForRank(rank + 1);
            Component label = Component.translatable(learned ? "solostatus.skills.upgrade" : "solostatus.skills.learn", cost);
            button(g, tx, by, tw, 14, label, mx, my, err == null, () -> {
                PacketDistributor.sendToServer(new Payloads.LearnSkill(def.id));
                Holo.chime();
            });
            if (err != null && Holo.inside(mx, my, tx, by, tw, 14)) tooltip = List.of(Component.translatable(err));
        }
    }

    /** Ориентировочный прирост эффекта за ранг для подсказки сравнения. */
    private double effectGain(SkillDef def) {
        return def.category == SkillCategory.PASSIVE ? 0.20 : def.source == SkillSource.SPEC || def.source == SkillSource.ULTIMATE ? 0.15 : 0.12;
    }

    private int requirementLine(GuiGraphics g, Requirement r, PlayerData d, int x, int y, int width) {
        boolean met = r.met(d);
        int h = Holo.wrapped(g, font, Component.literal(met ? "✔ " : "• ").append(r.describe(d)), x, y, width, met ? Holo.GREEN : Holo.DIM);
        if (!met) {
            Holo.bar(g, x + 2, y + h, width - 4, 2, r.progress(d), 0xFF3FB4FF);
            h += 4;
        }
        return y + h + 1;
    }

    // =====================================================================
    // CLASS
    // =====================================================================

    private boolean canPickSpec() {
        PlayerData d = d();
        int lvl;
        try { lvl = SoloConfig.SPECIALIZATION_LEVEL.get(); } catch (Exception e) { lvl = 5; }
        return d.hasClass() && d.specialization == null && d.level >= lvl;
    }

    private void renderClass(GuiGraphics g, int mx, int my) {
        PlayerData d = d();
        if (!d.hasClass()) {
            g.drawCenteredString(font, Component.translatable("solostatus.class.choose_title"), cx + cw / 2, cy, Holo.ACCENT);
            g.drawCenteredString(font, Component.translatable("solostatus.class.choose_sub"), cx + cw / 2, cy + 11, Holo.DIM);
            int cols = 3;
            int cardW = (cw - (cols - 1) * 6) / cols;
            int cardH = (ch - 28 - 6) / 2;
            for (int i = 0; i < HunterClass.VALUES.length; i++) {
                HunterClass hc = HunterClass.VALUES[i];
                int x = cx + (i % cols) * (cardW + 6);
                int y = cy + 26 + (i / cols) * (cardH + 6);
                boolean hover = Holo.inside(mx, my, x, y, cardW, cardH);
                g.fill(x, y, x + cardW, y + cardH, hover ? 0xD0103A60 : 0xB0081828);
                g.renderOutline(x, y, cardW, cardH, hover ? 0xFFFFFFFF : hc.color);
                g.renderItem(new ItemStack(hc.icon()), x + 4, y + 4);
                g.drawString(font, hc.displayName(), x + 24, y + 5, hc.color, false);
                g.drawString(font, Component.empty().append(hc.primary.shortName()).append(" +3  ").append(hc.secondary.shortName()).append(" +2"), x + 24, y + 15, Holo.DIM, false);
                Holo.wrapped(g, font, hc.description(), x + 4, y + 27, cardW - 8, Holo.TEXT);
                hotspots.add(new Hotspot(x, y, cardW, cardH, () -> confirm(
                        Component.translatable("solostatus.class.confirm", hc.displayName()),
                        Component.translatable("solostatus.class.confirm.desc"),
                        () -> PacketDistributor.sendToServer(new Payloads.ChooseClass(hc.ordinal())), Tab.CLASS)));
            }
            return;
        }

        HunterClass hc = d.hunterClass;
        int halfW = (cw - 6) / 2;
        // Класс
        Holo.box(g, cx, cy, halfW, ch, hc.color);
        g.renderItem(new ItemStack(hc.icon()), cx + 5, cy + 5);
        g.drawString(font, hc.displayName(), cx + 25, cy + 9, hc.color, false);
        int yy = cy + 26;
        yy += Holo.wrapped(g, font, hc.description(), cx + 5, yy, halfW - 10, Holo.TEXT) + 4;
        g.drawString(font, Component.translatable("solostatus.class.start_skills"), cx + 5, yy, Holo.GOLD, false);
        yy += 10;
        for (SkillDef def : Skills.forClass(hc)) {
            if (def.source != SkillSource.CLASS_START) continue;
            g.renderItem(new ItemStack(def.icon), cx + 5, yy);
            g.drawString(font, def.name(), cx + 24, yy + 4, Holo.TEXT, false);
            yy += 18;
        }
        SkillDef ult = Skills.ultimate(hc);
        if (ult != null) {
            g.drawString(font, Component.translatable("solostatus.class.ultimate"), cx + 5, yy + 2, Holo.GOLD, false);
            yy += 12;
            g.renderItem(new ItemStack(ult.icon), cx + 5, yy);
            g.drawString(font, ult.name(), cx + 24, yy + 4, d.hasSkill(ult.id) ? Holo.ACCENT : Holo.DIM, false);
        }

        // Специализация
        int sx = cx + halfW + 6;
        Holo.box(g, sx, cy, halfW, ch, 0x803FB4FF);
        int specLevel;
        try { specLevel = SoloConfig.SPECIALIZATION_LEVEL.get(); } catch (Exception e) { specLevel = 5; }
        int advLevel;
        try { advLevel = SoloConfig.ADVANCEMENT_LEVEL.get(); } catch (Exception e) { advLevel = 40; }
        yy = cy + 5;
        g.drawString(font, Component.translatable("solostatus.class.spec_title"), sx + 5, yy, Holo.ACCENT, false);
        yy += 12;
        if (d.specialization == null) {
            boolean can = d.level >= specLevel;
            g.drawString(font, can ? Component.translatable("solostatus.class.spec_choose")
                    : Component.translatable("solostatus.class.spec_locked", specLevel), sx + 5, yy, can ? Holo.GOLD : Holo.DIM, false);
            yy += 12;
            int cardH = (cy + ch - yy - 8) / 2;
            for (Specialization s : hc.specializations()) {
                boolean hover = can && Holo.inside(mx, my, sx + 4, yy, halfW - 8, cardH);
                g.fill(sx + 4, yy, sx + halfW - 4, yy + cardH, hover ? 0xD0103A60 : 0xA0081828);
                g.renderOutline(sx + 4, yy, halfW - 8, cardH, hover ? 0xFFFFFFFF : can ? Holo.BORDER : Holo.LOCKED);
                g.drawString(font, s.displayName(), sx + 8, yy + 4, can ? Holo.TEXT : Holo.DIM, false);
                Holo.wrapped(g, font, s.description(), sx + 8, yy + 15, halfW - 16, Holo.DIM);
                if (can) {
                    hotspots.add(new Hotspot(sx + 4, yy, halfW - 8, cardH, () -> confirm(
                            Component.translatable("solostatus.spec.confirm", s.displayName()),
                            Component.translatable("solostatus.spec.confirm.desc"),
                            () -> PacketDistributor.sendToServer(new Payloads.ChooseSpec(s.ordinal())), Tab.CLASS)));
                }
                yy += cardH + 4;
            }
            return;
        }
        Specialization s = d.specialization;
        g.drawString(font, d.advanced ? s.advancedName() : s.displayName(), sx + 5, yy, Holo.GOLD, false);
        yy += 11;
        yy += Holo.wrapped(g, font, s.description(), sx + 5, yy, halfW - 10, Holo.TEXT) + 6;

        g.drawString(font, Component.translatable("solostatus.class.trial"), sx + 5, yy, Holo.ACCENT, false);
        yy += 11;
        if (d.advanced) {
            Holo.wrapped(g, font, Component.translatable("solostatus.class.trial_done"), sx + 5, yy, halfW - 10, Holo.GREEN);
        } else {
            if (d.level < advLevel) {
                g.drawString(font, Component.translatable("solostatus.class.trial_locked", advLevel), sx + 5, yy, Holo.DIM, false);
                yy += 11;
            }
            for (Requirement r : ClassSystem.trialRequirements(hc)) yy = requirementLine(g, r, d, sx + 5, yy, halfW - 10);
        }
    }

    private void confirm(Component title, Component message, Runnable onYes, Tab back) {
        if (minecraft == null) return;
        minecraft.setScreen(new ConfirmScreen(yes -> {
            if (yes) onYes.run();
            minecraft.setScreen(new StatusScreen(back));
        }, title, message));
    }

    // =====================================================================
    // MASTERY
    // =====================================================================

    private void renderMastery(GuiGraphics g, int mx, int my) {
        PlayerData d = d();
        List<String> keys = new ArrayList<>();
        for (WeaponType t : WeaponType.VALUES) if (t.hasMastery()) keys.add(t.key);
        d.mastery.keySet().stream().filter(k -> k.startsWith("magic:")).sorted().forEach(keys::add);

        int rowH = 26;
        int visible = ch / rowH;
        scroll = Mth.clamp(scroll, 0, Math.max(0, keys.size() - visible));
        g.enableScissor(cx, cy, cx + cw, cy + ch);
        int y = cy;
        for (int i = scroll; i < keys.size() && y + rowH <= cy + ch + 1; i++) {
            String key = keys.get(i);
            double xp = d.masteryXp(key);
            int lvl = MasteryMath.level(xp);
            boolean magic = key.startsWith("magic:");
            WeaponType type = magic ? null : WeaponType.byKey(key);
            Component name = magic ? MasterySystem.schoolName(key.substring(6)) : type.displayName();
            Holo.box(g, cx, y, cw, rowH - 3, lvl > 0 ? 0x803FB4FF : 0x404A5A6A);
            g.renderItem(new ItemStack(magic ? net.minecraft.world.item.Items.ENCHANTED_BOOK : type.icon), cx + 4, y + 3);
            g.drawString(font, Component.translatable(magic ? "solostatus.mastery.magic" : "solostatus.mastery.weapon", name), cx + 24, y + 3, lvl > 0 ? Holo.TEXT : Holo.DIM, false);
            String lv = lvl + " / " + MasteryMath.MAX_LEVEL;
            g.drawString(font, lv, cx + 160, y + 3, Holo.GOLD, false);
            Holo.bar(g, cx + 24, y + 14, 180, 3, MasteryMath.progress(xp), 0xFF3FB4FF);
            Component bonus = Component.translatable("solostatus.mastery.bonus", Holo.pct(MasteryMath.damageBonus(lvl)), Holo.pct(MasteryMath.critBonus(lvl)));
            g.drawString(font, bonus, cx + 214, y + 3, Holo.DIM, false);
            // Вехи
            int[] marks = {10, 25, 40, 50};
            for (int m = 0; m < marks.length; m++) {
                int bx = cx + 214 + m * 22;
                boolean reached = lvl >= marks[m];
                g.fill(bx, y + 13, bx + 18, y + 20, reached ? 0xC02A6FA8 : 0x80102030);
                g.drawString(font, String.valueOf(marks[m]), bx + 3, y + 13, reached ? Holo.TEXT : Holo.LOCKED, false);
                hoverTip(mx, my, bx, y + 13, 18, 7, List.of(Component.translatable("solostatus.notice.mastery_milestone." + marks[m])));
            }
            y += rowH;
        }
        g.disableScissor();
        if (keys.size() > visible) g.drawString(font, "▼", cx + cw - 8, cy + ch - 9, Holo.DIM, false);
    }

    // =====================================================================
    // EQUIPMENT
    // =====================================================================

    private void renderEquipment(GuiGraphics g, int mx, int my) {
        PlayerData d = d();
        int modelW = 110;
        Holo.box(g, cx, cy, modelW, ch, 0x803FB4FF);
        if (minecraft == null || minecraft.player == null) return;
        int scale = (int) Math.min(70, ch / 3.0);
        InventoryScreen.renderEntityInInventoryFollowsMouse(g, cx + 2, cy + 2, cx + modelW - 2, cy + ch - 2, scale, 0.0625f, mx, my, minecraft.player);

        int x = cx + modelW + 8;
        int rw = cw - modelW - 8;
        int y = cy;
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND};
        for (EquipmentSlot slot : slots) {
            ItemStack st = minecraft.player.getItemBySlot(slot);
            Holo.box(g, x, y, rw, 22, qualityColor(st));
            g.renderItem(st, x + 3, y + 3);
            hoverItem(mx, my, x + 3, y + 3, st);
            Component name = st.isEmpty() ? Component.translatable("solostatus.equipment.empty") : st.getHoverName();
            g.drawString(font, name, x + 24, y + 3, st.isEmpty() ? Holo.DIM : Holo.TEXT, false);
            WeaponQuality q = QualitySystem.get(st);
            Component line = q == null ? Component.translatable("solostatus.equipment.slot." + slot.getName())
                    : Component.empty().append(q.tier().displayName()).append(Component.literal("  " + Holo.pct(q.effectiveDamageBonus())))
                    .append(q.broken() ? Component.translatable("solostatus.equipment.broken") : q.condition() == WeaponQuality.DAMAGED ? Component.translatable("solostatus.equipment.damaged") : Component.empty());
            g.drawString(font, line, x + 24, y + 12, Holo.DIM, false);
            y += 24;
        }
        // Сброс и приватность
        y += 4;
        int orbs = minecraft.player.getInventory().countItem(ModItems.RESET_ORB.get());
        g.drawString(font, Component.translatable("solostatus.equipment.orbs", orbs), x, y, orbs > 0 ? Holo.PURPLE : Holo.DIM, false);
        y += 12;
        int bw = (rw - 4) / 2;
        boolean canRespec = d.hasClass() && (orbs > 0 || minecraft.player.isCreative());
        button(g, x, y, bw, 14, Component.translatable("solostatus.respec.skills"), mx, my, canRespec, () -> confirm(
                Component.translatable("solostatus.respec.skills.title"), Component.translatable("solostatus.respec.skills.warn"),
                () -> PacketDistributor.sendToServer(new Payloads.Respec(0)), Tab.EQUIPMENT));
        button(g, x + bw + 4, y, bw, 14, Component.translatable("solostatus.respec.stats"), mx, my, canRespec, () -> confirm(
                Component.translatable("solostatus.respec.stats.title"), Component.translatable("solostatus.respec.stats.warn"),
                () -> PacketDistributor.sendToServer(new Payloads.Respec(1)), Tab.EQUIPMENT));
        y += 18;
        boolean priv = d.privateProfile;
        button(g, x, y, rw, 14, Component.translatable(priv ? "solostatus.privacy.on" : "solostatus.privacy.off"), mx, my, true,
                () -> PacketDistributor.sendToServer(new Payloads.SetPrivacy(!priv)));
    }

    // =====================================================================
    // ACHIEVEMENTS
    // =====================================================================

    private void renderAchievements(GuiGraphics g, int mx, int my) {
        PlayerData d = d();
        List<AchievementDef> rows = new ArrayList<>();
        int hiddenTotal = 0, hiddenDone = 0, total = 0;
        for (AchievementDef def : Achievements.all()) {
            if (def.hidden()) {
                hiddenTotal++;
                if (d.achievements.contains(def.id())) { hiddenDone++; rows.add(def); }
            } else {
                total++;
                rows.add(def);
            }
        }
        // Новые — сверху, затем выполненные, затем остальные
        rows.sort(Comparator.comparingInt((AchievementDef a) -> newAchievements.contains(a.id()) ? 0 : d.achievements.contains(a.id()) ? 1 : 2));
        long doneVisible = rows.stream().filter(a -> !a.hidden() && d.achievements.contains(a.id())).count();
        g.drawString(font, Component.translatable("solostatus.achievements.summary", doneVisible, total, hiddenDone, hiddenTotal), cx, cy, Holo.ACCENT, false);

        int listY = cy + 12;
        int rowH = 30;
        int visible = (ch - 12) / rowH;
        int count = rows.size() + (hiddenDone < hiddenTotal ? 1 : 0);
        scroll = Mth.clamp(scroll, 0, Math.max(0, count - visible));
        g.enableScissor(cx, listY, cx + cw, cy + ch);
        int y = listY;
        for (int i = scroll; i < count && y < cy + ch; i++) {
            if (i >= rows.size()) {
                Holo.box(g, cx, y, cw, rowH - 3, 0x80C08CFF);
                g.drawString(font, Component.translatable("solostatus.achievements.hidden", hiddenTotal - hiddenDone), cx + 24, y + 4, Holo.PURPLE, false);
                g.drawString(font, Component.translatable("solostatus.achievements.hidden.desc"), cx + 24, y + 14, Holo.DIM, false);
                g.drawString(font, "?", cx + 9, y + 9, Holo.PURPLE, false);
                y += rowH;
                continue;
            }
            AchievementDef def = rows.get(i);
            boolean done = d.achievements.contains(def.id());
            Holo.box(g, cx, y, cw, rowH - 3, done ? (def.hidden() ? Holo.PURPLE : Holo.ACCENT) : 0x604A5A6A);
            g.renderItem(new ItemStack(def.icon()), cx + 4, y + 5);
            if (!done) g.fill(cx + 4, y + 5, cx + 20, y + 21, 0x90000000);
            g.drawString(font, def.name(), cx + 24, y + 3, done ? Holo.TEXT : Holo.DIM, false);
            g.drawString(font, def.description(), cx + 24, y + 13, Holo.DIM, false);
            if (!done) {
                double prog = 0;
                for (Requirement r : def.requirements()) prog += r.progress(d);
                prog /= Math.max(1, def.requirements().size());
                Holo.bar(g, cx + 24, y + 23, 140, 2, prog, 0xFF3FB4FF);
            }
            Component reward = rewardText(def);
            g.drawString(font, reward, cx + cw - font.width(reward) - 6, y + 3, Holo.GOLD, false);
            if (newAchievements.contains(def.id())) {
                g.fill(cx + cw - 34, y + 13, cx + cw - 6, y + 23, 0xC0FFD36B);
                g.drawCenteredString(font, Component.translatable("solostatus.achievements.new"), cx + cw - 20, y + 14, 0xFF061424);
            }
            if (def.perk() && done) hoverTip(mx, my, cx, y, cw, rowH - 3, List.of(def.name(), def.perkText()));
            y += rowH;
        }
        g.disableScissor();
    }

    private Component rewardText(AchievementDef def) {
        MutableComponent c = Component.empty();
        if (def.xp() > 0) c.append(Component.translatable("solostatus.achievements.xp", def.xp()));
        if (def.sp() > 0) c.append(" ").append(Component.translatable("solostatus.achievements.sp", def.sp()));
        if (def.orbs() > 0) c.append(" ").append(Component.translatable("solostatus.achievements.orb", def.orbs()));
        return c;
    }

    // =====================================================================
    // Ввод
    // =====================================================================

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (openAnim() < 1f || button != 0) return super.mouseClicked(mx, my, button);
        for (int i = hotspots.size() - 1; i >= 0; i--) {
            Hotspot h = hotspots.get(i);
            if (Holo.inside(mx, my, h.x(), h.y(), h.w(), h.h())) {
                Holo.click();
                h.action().run();
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (sy != 0) {
            scroll = Math.max(0, scroll - (int) Math.signum(sy));
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (Keys.OPEN_STATUS.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        if (tab == Tab.SKILLS && !d().unseenSkills.isEmpty()) PacketDistributor.sendToServer(new Payloads.MarkSeen(1));
        super.onClose();
    }
}
