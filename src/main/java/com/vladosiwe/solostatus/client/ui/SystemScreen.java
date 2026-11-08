package com.vladosiwe.solostatus.client.ui;

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
import com.vladosiwe.solostatus.registry.ModItems;
import com.vladosiwe.solostatus.registry.ModSounds;
import com.vladosiwe.solostatus.skill.*;
import com.vladosiwe.solostatus.stats.Stat;
import com.vladosiwe.solostatus.stats.StatFormulas;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;

import static com.vladosiwe.solostatus.client.ui.SysDraw.*;
import static com.vladosiwe.solostatus.client.ui.SystemFonts.*;

/**
 * Окно Системы.
 *
 * Композиция: мир сменяется белым пространством; слева стоит охотник (настоящая модель игрока со скином,
 * с бронёй или без), справа парит окно в стиле аниме. Окна разделены по смыслу: STATUS — только
 * характеристики, SKILLS — навыки, CLASS, MASTERY, ACHIEVEMENTS — отдельные окна. Вложенные окна
 * (навык, титулы, звуки, подтверждение) открываются поверх основного.
 *
 * Анимация: белое пространство проявляется 350 мс → сетка пола → окно раскрывается из линии (220 мс)
 * → строки проявляются по очереди (шаг 35 мс). Цифры характеристик плавно докручиваются при росте.
 *
 * Окно только показывает данные сервера и отправляет запросы; все изменения проверяет сервер.
 */
public class SystemScreen extends Screen {
    public enum Win { CLASS, STATUS, SKILLS, MASTERY, ACHIEVEMENTS }

    private enum Overlay { NONE, SKILL, TITLES, SOUNDS, CONFIRM }

    private record Hot(int x, int y, int w, int h, Runnable left, @Nullable Runnable right) {}

    private static boolean showArmor = true;
    private static Win lastWin = Win.STATUS;

    private Win win;
    private Overlay overlay = Overlay.NONE;
    private final long openAt = System.currentTimeMillis();
    private long winAt = System.currentTimeMillis();
    private long overlayAt;
    private boolean announced;

    // окно
    private int ww, wh, wx, wy, cx0, cw;
    private int playerX, floorY, horizon;
    // ввод
    private final List<Hot> hots = new ArrayList<>();
    private final List<Hot> overlayHots = new ArrayList<>();
    private List<Component> tooltip;
    private ItemStack tooltipStack = ItemStack.EMPTY;
    private int scroll;
    private float yaw = -25f;
    private boolean draggingPlayer;
    private double dragStartX;
    private float dragStartYaw;
    // состояние
    @Nullable private String selectedSkill;
    private final float[] shownStats = new float[Stat.VALUES.length];
    @Nullable private Component confirmTitle, confirmText;
    @Nullable private Runnable confirmAction;
    @Nullable private DisplayPlayer noArmor;
    private final Set<String> newAchievements = new HashSet<>();

    public SystemScreen() {
        this(null);
    }

    public SystemScreen(@Nullable Win start) {
        super(Component.translatable("solostatus.screen.title"));
        PlayerData d = ClientState.data;
        this.win = !d.hasClass() ? Win.CLASS : start != null ? start : lastWin;
        for (int i = 0; i < shownStats.length; i++) shownStats[i] = d.stats[i];
        if (win == Win.ACHIEVEMENTS) markAchievementsSeen();
    }

    private PlayerData d() {
        return ClientState.data;
    }

    // ============================================================ жизненный цикл

    @Override
    protected void init() {
        ww = Mth.clamp((int) (width * 0.42f), 250, 330);
        wh = Mth.clamp((int) (height * 0.80f), 214, 290);
        wx = Math.min((int) (width * 0.52f), width - ww - 12);
        wy = (height - wh) / 2 + 6;
        cx0 = wx + 14;
        cw = ww - 28;
        playerX = Math.max(70, (int) (width * 0.27f));
        horizon = (int) (height * 0.60f);
        floorY = (int) (height * 0.80f);
        if (!announced) {
            announced = true;
            PacketDistributor.sendToServer(new Payloads.SystemOpen(true));
            play(ModSounds.OPEN.get(), 1f);
        }
    }

    @Override
    public void removed() {
        PacketDistributor.sendToServer(new Payloads.SystemOpen(false));
        if (win == Win.SKILLS && !d().unseenSkills.isEmpty()) PacketDistributor.sendToServer(new Payloads.MarkSeen(1));
        play(ModSounds.CLOSE.get(), 1f);
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static void play(SoundEvent sound, float pitch) {
        net.minecraft.client.Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, 0.8f));
    }

    private float since(long t) {
        return System.currentTimeMillis() - t;
    }

    /** Прозрачность строки по её номеру: строки проявляются по очереди после раскрытия окна. */
    private float row(int index) {
        return Mth.clamp((since(winAt) - 230 - index * 35) / 140f, 0f, 1f);
    }

    // ============================================================ отрисовка

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        float t = easeOut(Mth.clamp(since(openAt) / 350f, 0f, 1f));
        whiteSpace(g, width, height, t, playerX, horizon);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        renderBackground(g, mx, my, partialTick);
        hots.clear();
        overlayHots.clear();
        tooltip = null;
        tooltipStack = ItemStack.EMPTY;
        for (int i = 0; i < shownStats.length; i++) shownStats[i] += (d().stats[i] - shownStats[i]) * 0.18f;

        renderPlayer(g, mx, my);
        if (d().hasClass()) renderDock(g, mx, my);

        float unfold = (since(winAt) - (since(openAt) < 450 ? 450 - since(openAt) : 0)) / 220f;
        int vis = window(g, wx, wy, ww, wh, unfold, 1f);
        if (vis >= wh - 1) {
            switch (win) {
                case CLASS -> renderClass(g, mx, my);
                case STATUS -> renderStatus(g, mx, my);
                case SKILLS -> renderSkills(g, mx, my);
                case MASTERY -> renderMastery(g, mx, my);
                case ACHIEVEMENTS -> renderAchievements(g, mx, my);
            }
            titlePlate(g, font, title(Component.translatable("solostatus.win." + win.name().toLowerCase())), wx + ww / 2, wy - 9, 1f);
        }
        renderHints(g);
        if (overlay != Overlay.NONE) renderOverlay(g, mx, my);

        if (!tooltipStack.isEmpty()) g.renderTooltip(font, tooltipStack, mx, my);
        else if (tooltip != null) g.renderComponentTooltip(font, tooltip, mx, my);
    }

    // ------------------------------------------------------------ охотник

    private void renderPlayer(GuiGraphics g, int mx, int my) {
        if (minecraft == null || minecraft.player == null) return;
        float appear = easeOut(Mth.clamp((since(openAt) - 250) / 450f, 0f, 1f));
        if (appear <= 0) return;
        shadow(g, playerX, floorY, 46, appear);
        LivingEntity entity = minecraft.player;
        if (!showArmor) {
            if (noArmor == null && minecraft.level != null) noArmor = new DisplayPlayer(minecraft.level, (AbstractClientPlayer) minecraft.player);
            if (noArmor != null) entity = noArmor;
        }
        int scale = (int) ((floorY - height * 0.16f) / 2.15f);
        float bodyH = entity.getBbHeight();
        float centerY = floorY - scale * bodyH / 2f + (1 - appear) * 12;
        if (!draggingPlayer) yaw += 0.12f;
        float headTurn = (float) Math.atan((playerX - mx) / 60f) * 25f;
        float pitch = (float) Math.atan((centerY - scale * bodyH * 0.35f - my) / 60f) * 15f;
        drawEntity(g, playerX, centerY, scale * appear, yaw, headTurn, pitch, entity);

        // переключатель брони
        int bw = 120;
        Component label = trBold(showArmor ? "solostatus.ui.armor_hide" : "solostatus.ui.armor_show");
        boolean hover = inside(mx, my, playerX - bw / 2, floorY + 10, bw, 14);
        g.fill(playerX - bw / 2, floorY + 10, playerX + bw / 2, floorY + 24, a(hover ? 0xFFFFFFFF : 0xE6FFFFFF, appear));
        g.renderOutline(playerX - bw / 2, floorY + 10, bw, 14, a(hover ? 0xFF1E8EF0 : 0xFF9CC8F2, appear));
        textCenter(g, font, label, playerX, floorY + 13, 0xFF0A5CB8, appear);
        hots.add(new Hot(playerX - bw / 2, floorY + 10, bw, 14, () -> {
            showArmor = !showArmor;
            play(ModSounds.CLICK.get(), 1.2f);
        }, null));
    }

    /** Модель игрока с поворотом от перетаскивания мышью (по образцу InventoryScreen). */
    private static void drawEntity(GuiGraphics g, float x, float y, float scale, float yawDeg, float headTurn, float pitchDeg, LivingEntity e) {
        Quaternionf pose = new Quaternionf().rotateZ((float) Math.PI);
        Quaternionf cam = new Quaternionf().rotateX(pitchDeg * Mth.DEG_TO_RAD);
        pose.mul(cam);
        float bodyRot = e.yBodyRot, yRot = e.getYRot(), xRot = e.getXRot(), headO = e.yHeadRotO, head = e.yHeadRot;
        e.yBodyRot = 180f + yawDeg;
        e.setYRot(180f + yawDeg + headTurn);
        e.setXRot(-pitchDeg);
        e.yHeadRot = e.getYRot();
        e.yHeadRotO = e.getYRot();
        float s = e.getScale();
        Vector3f translate = new Vector3f(0f, e.getBbHeight() / 2f + 0.0625f * s, 0f);
        InventoryScreen.renderEntityInInventory(g, x, y, scale / s, translate, pose, cam, e);
        e.yBodyRot = bodyRot;
        e.setYRot(yRot);
        e.setXRot(xRot);
        e.yHeadRotO = headO;
        e.yHeadRot = head;
    }

    // ------------------------------------------------------------ переключатель окон

    private void renderDock(GuiGraphics g, int mx, int my) {
        float a = Mth.clamp((since(openAt) - 500) / 250f, 0f, 1f);
        Win[] wins = {Win.STATUS, Win.SKILLS, Win.CLASS, Win.MASTERY, Win.ACHIEVEMENTS};
        int bw = 88, bh = 16; // «ACHIEVEMENTS» шрифтом system_bold ≈ 76 единиц + отступы
        int x = wx - bw - 12;
        int y = wy + 18;
        for (Win w : wins) {
            boolean sel = w == win;
            boolean hover = inside(mx, my, x, y, bw, bh);
            g.fill(x, y, x + bw, y + bh, a(sel ? 0xF0123A60 : hover ? 0xE00E2A48 : 0xD8081426, a));
            g.renderOutline(x, y, bw, bh, a(sel ? 0xFFFFFFFF : 0x9955C8FF, a));
            if (sel) g.fill(x + bw - 2, y + 2, x + bw, y + bh - 2, a(EDGE, a));
            Component label = trBold("solostatus.win." + w.name().toLowerCase());
            textRight(g, font, label, x + bw - 7, y + 4, sel ? 0xFFFFFFFF : DIM, a);
            boolean badge = (w == Win.STATUS && d().freeStatPoints > 0) || (w == Win.SKILLS && !d().unseenSkills.isEmpty())
                    || (w == Win.ACHIEVEMENTS && !d().unseenAchievements.isEmpty()) || (w == Win.CLASS && canPickSpec());
            if (badge) g.fill(x + 4, y + 6, x + 8, y + 10, a(GOLD, a));
            Win target = w;
            hots.add(new Hot(x, y, bw, bh, () -> switchWin(target), null));
            y += bh + 4;
        }
    }

    private void switchWin(Win w) {
        if (w == win || !d().hasClass()) return;
        if (win == Win.SKILLS && !d().unseenSkills.isEmpty()) PacketDistributor.sendToServer(new Payloads.MarkSeen(1));
        win = w;
        lastWin = w;
        winAt = System.currentTimeMillis();
        scroll = 0;
        overlay = Overlay.NONE;
        if (w == Win.ACHIEVEMENTS) markAchievementsSeen();
        play(ModSounds.CLICK.get(), 1.0f);
    }

    private void markAchievementsSeen() {
        newAchievements.clear();
        newAchievements.addAll(d().unseenAchievements);
        if (!newAchievements.isEmpty()) PacketDistributor.sendToServer(new Payloads.MarkSeen(0));
    }

    private void renderHints(GuiGraphics g) {
        float a = Mth.clamp((since(openAt) - 700) / 300f, 0f, 1f);
        Component hint = tr("solostatus.ui.hints", Keys.OPEN_STATUS.getTranslatedKeyMessage());
        textRight(g, font, hint, width - 10, height - 14, 0xFF4F6F8F, a);
    }

    // ============================================================ STATUS

    private void renderStatus(GuiGraphics g, int mx, int my) {
        PlayerData d = d();
        int y = wy + 18;
        int half = cw / 2;
        String name = minecraft != null && minecraft.player != null ? minecraft.player.getGameProfile().getName() : "";

        // ранг
        int rx = wx + ww - 14 - 26;
        float ra = row(0);
        g.renderOutline(rx, wy + 16, 26, 26, a(d.rank.color, ra));
        g.fill(rx + 1, wy + 17, rx + 25, wy + 41, a(0x60061424, ra));
        textCenter(g, font, title(d.rank.letter), rx + 13, wy + 21, d.rank.color, ra);
        hover(mx, my, rx, wy + 16, 26, 26, List.of(d.rank.displayName(),
                Component.translatable("solostatus.status.rank_score", d.rankScore), Component.translatable("solostatus.status.rank_hint")));

        label(g, "solostatus.ui.name", cx0, y, row(0));
        text(g, font, bold(name), cx0, y + 9, INK, row(0));
        label(g, "solostatus.ui.level", cx0 + half, y, row(0));
        text(g, font, title(String.valueOf(d.level)), cx0 + half, y + 8, 0xFFFFFFFF, row(0));
        y += 26;

        label(g, "solostatus.ui.class", cx0, y, row(1));
        Component cls = d.hunterClass == null ? Component.translatable("solostatus.class.none")
                : d.specialization == null ? d.hunterClass.displayName()
                : Component.empty().append(d.hunterClass.displayName()).append(" · ").append(d.advanced ? d.specialization.advancedName() : d.specialization.displayName());
        text(g, font, bold(cls), cx0, y + 9, INK, row(1));
        label(g, "solostatus.ui.title", cx0 + half, y, row(1));
        AchievementDef titleDef = d.title.isEmpty() ? null : Achievements.get(d.title);
        Component titleText = titleDef != null ? titleDef.name() : Component.translatable("solostatus.ui.no_title");
        boolean titleHover = inside(mx, my, cx0 + half, y + 8, half, 11);
        text(g, font, bold(titleText), cx0 + half, y + 9, titleDef != null ? VIOLET : DIM, row(1));
        if (titleHover) g.fill(cx0 + half, y + 19, cx0 + half + font.width(bold(titleText)), y + 20, a(VIOLET, row(1)));
        hots.add(new Hot(cx0 + half, y + 8, half, 12, () -> openOverlay(Overlay.TITLES), null));
        y += 26;

        divider(g, cx0, y, cw, row(2));
        y += 6;
        // полосы
        float maxHp = minecraft != null && minecraft.player != null ? minecraft.player.getMaxHealth() : 20;
        float hp = minecraft != null && minecraft.player != null ? minecraft.player.getHealth() : 20;
        float maxE = (float) StatFormulas.maxEnergy(d);
        long need = d.level < PlayerData.MAX_LEVEL ? LevelSystem.xpToNext(d.level) : LevelSystem.overflowNeed(d.overflowAwakenings);
        long have = d.level < PlayerData.MAX_LEVEL ? d.xp : d.overflowXp;
        barRow(g, "HP", hp / Math.max(1, maxHp), Math.round(hp) + " / " + Math.round(maxHp), 0xFFFF6B6B, y, row(3));
        barRow(g, "MP", d.energy / Math.max(1, maxE), Math.round(d.energy) + " / " + Math.round(maxE), 0xFF4FA8FF, y + 12, row(4));
        barRow(g, "EXP", have / (double) Math.max(1, need), have + " / " + need, 0xFFE8B83A, y + 24, row(5));
        y += 40;
        divider(g, cx0, y, cw, row(6));
        y += 7;

        // характеристики: 3 строки × 2 колонки, как в окне STATUS из аниме
        Stat[] order = {Stat.STR, Stat.VIT, Stat.AGI, Stat.INT, Stat.PER, Stat.LUCK};
        int max = maxStat();
        for (int i = 0; i < order.length; i++) {
            Stat s = order[i];
            int col = i % 2, line = i / 2;
            int sx = cx0 + col * (half + 4);
            int sy = y + line * 17;
            float ra2 = row(7 + line);
            int colW = half - 4;
            boolean hov = inside(mx, my, sx - 2, sy - 2, colW, 15);
            if (hov) g.fill(sx - 2, sy - 2, sx - 2 + colW, sy + 13, a(0x2055C8FF, ra2));
            g.fill(sx - 2, sy + 13, sx - 2 + colW, sy + 14, a(0x2255C8FF, ra2));
            text(g, font, trBold("solostatus.stat." + s.key + ".full").append(":"), sx, sy + 1, INK, ra2);
            int value = Math.round(shownStats[s.ordinal()]);
            boolean rolling = Math.abs(shownStats[s.ordinal()] - d.stats[s.ordinal()]) > 0.05f;
            textRight(g, font, bold(String.valueOf(value)), sx + colW - 22, sy + 1, rolling ? GOLD : 0xFFFFFFFF, ra2);
            boolean can = d.freeStatPoints > 0 && d.stat(s) < max;
            boolean ph = can && inside(mx, my, sx + colW - 18, sy - 1, 14, 12);
            g.fill(sx + colW - 18, sy - 1, sx + colW - 4, sy + 11, a(ph ? 0x9055C8FF : can ? 0x3055C8FF : 0x10000000, ra2));
            g.renderOutline(sx + colW - 18, sy - 1, 14, 12, a(can ? EDGE : LOCK, ra2));
            textCenter(g, font, bold("+"), sx + colW - 11, sy + 1, can ? 0xFFFFFFFF : LOCK, ra2);
            if (can) {
                hots.add(new Hot(sx + colW - 18, sy - 1, 14, 12, () -> {
                    int amount = hasShiftDown() ? 5 : 1;
                    PacketDistributor.sendToServer(new Payloads.AllocateStat(s.ordinal(), amount));
                    play(ModSounds.STAT_UP.get(), 1f + s.ordinal() * 0.05f);
                }, null));
            }
            if (hov && !ph) tooltip = List.of(s.fullName().copy().withColor(s.color), s.description(), statEffect(d, s),
                    Component.translatable("solostatus.status.soft_cap", softCap()));
        }
        y += 3 * 17 + 3;

        // очки
        float pa = row(10);
        text(g, font, tr("solostatus.ui.free_points"), cx0, y + 1, DIM, pa);
        textRight(g, font, title(String.valueOf(d.freeStatPoints)), cx0 + cw, y - 2, d.freeStatPoints > 0 ? GOLD : DIM, pa);
        y += 16;

        // системные подсказки
        if (canPickSpec()) {
            Component spec = trBold("solostatus.ui.spec_available");
            boolean h = inside(mx, my, cx0, y, cw, 11);
            text(g, font, spec, cx0, y, h ? 0xFFFFFFFF : GOLD, row(11));
            hots.add(new Hot(cx0, y, cw, 11, () -> switchWin(Win.CLASS), null));
        }

        // нижняя строка
        int by = wy + wh - 17;
        float fa = row(12);
        int orbs = minecraft != null && minecraft.player != null ? minecraft.player.getInventory().countItem(ModItems.RESET_ORB.get()) : 0;
        boolean creative = minecraft != null && minecraft.player != null && minecraft.player.isCreative();
        footerLink(g, mx, my, tr("solostatus.ui.sounds"), cx0, by, fa, () -> openOverlay(Overlay.SOUNDS));
        int mid = cx0 + cw / 2 - 40;
        if (orbs > 0 || creative) {
            footerLink(g, mx, my, tr("solostatus.ui.respec", orbs), mid, by, fa, () -> askRespec());
        }
        Component priv = tr(d.privateProfile ? "solostatus.ui.private_on" : "solostatus.ui.private_off");
        footerLink(g, mx, my, priv, cx0 + cw - font.width(priv), by, fa,
                () -> PacketDistributor.sendToServer(new Payloads.SetPrivacy(!d.privateProfile)));
    }

    private void label(GuiGraphics g, String key, int x, int y, float alpha) {
        text(g, font, trBold(key), x, y, DIM, alpha);
    }

    private void barRow(GuiGraphics g, String name, double frac, String value, int color, int y, float alpha) {
        text(g, font, bold(name), cx0, y, DIM, alpha);
        int vw = font.width(body(value));
        bar(g, cx0 + 28, y + 2, cw - 28 - vw - 10, 5, frac, color, alpha);
        textRight(g, font, body(value), cx0 + cw, y, DIM, alpha);
    }

    private void footerLink(GuiGraphics g, int mx, int my, Component c, int x, int y, float alpha, Runnable action) {
        int w = font.width(c);
        boolean h = inside(mx, my, x, y - 1, w, 11);
        text(g, font, c, x, y, h ? 0xFFFFFFFF : DIM, alpha);
        if (h) g.fill(x, y + 9, x + w, y + 10, a(EDGE, alpha));
        hots.add(new Hot(x, y - 1, w, 11, action, null));
    }

    private void hover(int mx, int my, int x, int y, int w, int h, List<Component> lines) {
        if (inside(mx, my, x, y, w, h)) tooltip = lines;
    }

    private Component statEffect(PlayerData d, Stat s) {
        return switch (s) {
            case STR -> Component.translatable("solostatus.effect.str", pct(StatFormulas.physicalDamage(d)));
            case AGI -> Component.translatable("solostatus.effect.agi", pct(StatFormulas.moveSpeed(d)), pct(StatFormulas.dodgeChance(d)));
            case PER -> Component.translatable("solostatus.effect.per", pct(StatFormulas.critChance(d)), pct(StatFormulas.projectileDamage(d)));
            case VIT -> Component.translatable("solostatus.effect.vit", Math.round(StatFormulas.bonusHealth(d) * 10) / 10.0, pct(StatFormulas.damageReduction(d)));
            case INT -> Component.translatable("solostatus.effect.int", pct(StatFormulas.magicPower(d)), Math.round(StatFormulas.maxEnergy(d)));
            case LUCK -> Component.translatable("solostatus.effect.luck", pct(StatFormulas.extraDropChance(d)), pct(StatFormulas.qualityBonus(d)));
        };
    }

    private static String pct(double v) {
        return (v >= 0 ? "+" : "") + Math.round(v * 1000) / 10.0 + "%";
    }

    private int maxStat() {
        try { return SoloConfig.MAX_STAT_VALUE.get(); } catch (Exception e) { return 150; }
    }

    private int softCap() {
        try { return SoloConfig.STAT_SOFT_CAP.get(); } catch (Exception e) { return 40; }
    }

    // ============================================================ SKILLS

    private record SkillRow(@Nullable String section, @Nullable SkillDef def) {}

    /** Фильтр списка навыков по происхождению. Цвет фильтра совпадает с цветом иконок этого происхождения. */
    private enum SkillFilter {
        ALL, CLASS, SPEC, AWAKENING, HIDDEN;

        boolean accepts(SkillDef def) {
            return switch (this) {
                case ALL -> true;
                case CLASS -> def.source == SkillSource.CLASS_START || def.source == SkillSource.CLASS || def.source == SkillSource.ULTIMATE;
                case SPEC -> def.source == SkillSource.SPEC;
                case AWAKENING -> def.source == SkillSource.AWAKENING;
                case HIDDEN -> def.source == SkillSource.HIDDEN;
            };
        }

        int color() {
            return switch (this) {
                case ALL -> INK;
                case CLASS -> SkillIcons.CLASS;
                case SPEC -> SkillIcons.SPEC;
                case AWAKENING -> SkillIcons.AWAKENING;
                case HIDDEN -> SkillIcons.HIDDEN;
            };
        }
    }

    private SkillFilter skillFilter = SkillFilter.ALL;

    /** Карточка навыка встроена в окно под списком; на невысоких экранах она открывается поверх, как раньше. */
    private boolean skillsSplit() {
        return wh >= 250;
    }

    private List<SkillRow> skillRows() {
        PlayerData d = d();
        List<SkillDef> active = new ArrayList<>(), passive = new ArrayList<>(), learnable = new ArrayList<>(), locked = new ArrayList<>();
        for (SkillDef def : Skills.all()) {
            if (!def.availableFor(d) && !d.hasSkill(def.id)) continue;
            if (!skillFilter.accepts(def)) continue;
            if (d.hasSkill(def.id)) (def.isActive() ? active : passive).add(def);
            else if (SkillSystem.canAdvance(d, def) == null) learnable.add(def);
            else locked.add(def);
        }
        List<SkillRow> rows = new ArrayList<>();
        addSection(rows, "solostatus.ui.sec_learnable", learnable);
        addSection(rows, "solostatus.ui.sec_active", active);
        addSection(rows, "solostatus.ui.sec_passive", passive);
        addSection(rows, "solostatus.ui.sec_locked", locked);
        return rows;
    }

    private static void addSection(List<SkillRow> rows, String key, List<SkillDef> defs) {
        if (defs.isEmpty()) return;
        rows.add(new SkillRow(key, null));
        for (SkillDef d : defs) rows.add(new SkillRow(null, d));
    }

    private void renderSkills(GuiGraphics g, int mx, int my) {
        PlayerData d = d();
        boolean split = skillsSplit();
        int y = wy + 16;

        // слоты Z X C V слева, очки навыков справа — одной строкой
        for (int i = 0; i < PlayerData.SKILL_SLOTS; i++) {
            int sx = cx0 + i * 24;
            SkillDef def = Skills.get(d.slots[i]);
            g.fill(sx, y, sx + 20, y + 20, a(0x90061424, row(0)));
            g.renderOutline(sx, y, 20, 20, a(def != null ? SkillIcons.color(def.source) : 0x6655C8FF, row(0)));
            if (def != null) SkillIcons.draw(g, def, sx + 2, y + 2, 16, true, row(0));
            text(g, font, bold(Keys.SKILLS[i].getTranslatedKeyMessage().getString()), sx + 1, y + 21, DIM, row(0));
            if (def != null) {
                int slot = i;
                hover(mx, my, sx, y, 20, 20, List.of(def.name(), Component.translatable("solostatus.skills.slot_clear")));
                hots.add(new Hot(sx, y, 20, 20, () -> openSkill(def.id), () -> PacketDistributor.sendToServer(new Payloads.EquipSkill(slot, ""))));
            }
        }
        textRight(g, font, title(String.valueOf(d.skillPoints)), cx0 + cw, y + 1, d.skillPoints > 0 ? GOLD : DIM, row(0));
        textRight(g, font, tr("solostatus.ui.skill_points"), cx0 + cw, y + 18, DIM, row(0));
        hover(mx, my, cx0, y, 4 * 24, 30, List.of(Component.translatable("solostatus.ui.slots_hint")));
        y += 32;

        // фильтры по происхождению
        boolean wide = true;
        int total = 0;
        for (SkillFilter f : SkillFilter.values()) total += font.width(trBold("solostatus.ui.filter_" + f.name().toLowerCase())) + 10;
        if (total > cw) wide = false;
        int tx = cx0;
        for (SkillFilter f : SkillFilter.values()) {
            Component label = wide ? trBold("solostatus.ui.filter_" + f.name().toLowerCase()) : tr("solostatus.ui.filter_" + f.name().toLowerCase());
            int tw = font.width(label) + 8;
            boolean sel = f == skillFilter, hov = inside(mx, my, tx, y, tw, 12);
            if (sel) g.fill(tx, y, tx + tw, y + 12, a(0x2A55C8FF, row(1)));
            g.fill(tx, y + 11, tx + tw, y + 12, a(sel ? f.color() : 0x3355C8FF, row(1)));
            text(g, font, label, tx + 4, y + 2, sel || hov ? f.color() : DIM, row(1));
            SkillFilter target = f;
            hots.add(new Hot(tx, y, tw, 12, () -> {
                if (skillFilter == target) return;
                skillFilter = target;
                scroll = 0;
                play(ModSounds.CLICK.get(), 1.1f);
            }, null));
            tx += tw + 2;
        }
        y += 16;

        List<SkillRow> rows = skillRows();
        // выбранный навык должен быть в списке: иначе выбираем первый
        if (split && rows.stream().noneMatch(r -> r.def() != null && r.def().id.equals(selectedSkill))) {
            selectedSkill = rows.stream().filter(r -> r.def() != null).map(r -> r.def().id).findFirst().orElse(null);
        }
        int paneH = 118;
        int listTop = y, listBottom = split ? wy + wh - 8 - paneH - 6 : wy + wh - 10;
        int rowH = 15, secH = 14;
        int maxScroll = 0;
        for (int i = rows.size() - 1, used = 0; i >= 0; i--) { // сколько строк можно прокрутить, чтобы конец списка оставался у низа
            used += rows.get(i).section() != null ? secH : rowH;
            if (used > listBottom - listTop) { maxScroll = i + 1; break; }
        }
        scroll = Mth.clamp(scroll, 0, maxScroll);
        if (rows.isEmpty()) wrap(g, tr("solostatus.ui.no_skills_filter"), cx0, listTop + 4, cw, DIM, row(2));
        g.enableScissor(wx + 4, listTop, wx + ww - 4, listBottom);
        int yy = listTop;
        for (int i = scroll; i < rows.size() && yy < listBottom; i++) {
            SkillRow r = rows.get(i);
            float ra = row(2 + (i - scroll));
            if (r.section() != null) {
                // заголовок раздела: отступ сверху, подпись и тонкая линия до края — не путается со строкой навыка
                Component sec = trBold(r.section());
                int ty = yy + (i == scroll ? 2 : 5);
                text(g, font, sec, cx0, ty, r.section().endsWith("learnable") ? GOLD : DIM, ra);
                g.fill(cx0 + font.width(sec) + 6, ty + 4, cx0 + cw, ty + 5, a(0x3355C8FF, ra));
                yy += secH;
                continue;
            }
            SkillDef def = r.def();
            boolean learned = d.hasSkill(def.id);
            boolean hiddenLocked = def.hidden() && !learned;
            boolean sel = def.id.equals(selectedSkill) && split;
            boolean hov = inside(mx, my, cx0 - 4, yy, cw + 8, rowH) && my < listBottom;
            if (sel || hov) g.fill(cx0 - 4, yy, cx0 + cw + 4, yy + rowH, a(sel ? 0x3355C8FF : 0x1A55C8FF, ra));
            g.fill(cx0 - 4, yy + 2, cx0 - 2, yy + rowH - 2, a(SkillIcons.color(def.source), ra * (learned ? 1f : 0.45f)));
            if (hiddenLocked) textCenter(g, font, bold("?"), cx0 + 6, yy + 4, VIOLET, ra);
            else SkillIcons.draw(g, def, cx0, yy + 1, 13, learned, ra);
            Component nm = hiddenLocked ? body("???") : body(def.name());
            int nameColor = hiddenLocked ? VIOLET : learned ? INK : SkillSystem.canAdvance(d, def) == null ? GOLD : DIM;
            text(g, font, nm, cx0 + 18, yy + 4, nameColor, ra);
            if (d.unseenSkills.contains(def.id)) text(g, font, bold("NEW"), cx0 + 22 + font.width(nm), yy + 4, GOLD, ra);
            if (learned) {
                int slot = slotOf(def.id);
                if (def.isActive()) keyBadge(g, font, bold(slot >= 0 ? Keys.SKILLS[slot].getTranslatedKeyMessage().getString() : "—"), cx0 + cw, yy + 2, slot >= 0, ra);
                textRight(g, font, tr("solostatus.ui.lv", d.skillRank(def.id)), cx0 + cw - (def.isActive() ? 22 : 0), yy + 4, GOLD, ra);
            } else if (!hiddenLocked) {
                textRight(g, font, body(Math.round(def.unlockProgress(d) * 100) + "%"), cx0 + cw, yy + 4, DIM, ra);
            }
            String id = def.id;
            if (yy + rowH <= listBottom + 1) hots.add(new Hot(cx0 - 4, yy, cw + 8, Math.min(rowH, listBottom - yy), () -> openSkill(id), () -> {
                if (learned && def.isActive()) assignNextSlot(id);
                else openSkill(id);
            }));
            yy += rowH;
        }
        g.disableScissor();
        if (maxScroll > 0) {
            int track = listBottom - listTop;
            int barH = Math.max(12, track * track / Math.max(track, track + maxScroll * rowH));
            int barY = listTop + (track - barH) * scroll / maxScroll;
            g.fill(wx + ww - 7, barY, wx + ww - 5, barY + barH, a(EDGE, 0.6f));
        }

        if (split) {
            int py = listBottom + 6;
            divider(g, cx0, py - 3, cw, row(3));
            renderSkillPane(g, mx, my, cx0, py, cw, wy + wh - 8 - py, row(4));
        }
    }

    /** Карточка выбранного навыка внутри окна: что делает, сколько стоит, путь развития, что нужно и кнопки. */
    private void renderSkillPane(GuiGraphics g, int mx, int my, int x, int y, int w, int h, float alpha) {
        PlayerData d = d();
        SkillDef def = Skills.get(selectedSkill);
        if (def == null) {
            wrap(g, tr("solostatus.ui.pick_skill"), x, y + 4, w, DIM, alpha);
            return;
        }
        int bottom = y + h;
        boolean learned = d.hasSkill(def.id);
        boolean hiddenLocked = def.hidden() && !learned;
        int rank = d.skillRank(def.id);
        int src = SkillIcons.color(def.source);

        // шапка: иконка, имя, происхождение и ранги
        g.renderOutline(x, y, 22, 22, a(learned ? src : 0x6655C8FF, alpha));
        if (hiddenLocked) textCenter(g, font, bold("?"), x + 11, y + 7, VIOLET, alpha);
        else SkillIcons.draw(g, def, x + 3, y + 3, 16, learned ? src : SkillIcons.LOCKED, alpha);
        text(g, font, bold(hiddenLocked ? Component.literal("???") : def.name()), x + 28, y + 2, hiddenLocked ? VIOLET : 0xFFFFFFFF, alpha);
        text(g, font, body(Component.empty().append(def.category.displayName()).append(" · ")
                .append(Component.translatable("solostatus.source." + def.source.name().toLowerCase()))), x + 28, y + 13, src, alpha * 0.85f);
        for (int i = 0; i < def.maxRank; i++) {
            int px = x + w - (def.maxRank - i) * 9;
            g.fill(px, y + 4, px + 7, y + 7, a(i < rank ? src : 0x30FFFFFF, alpha));
        }
        y += 27;
        if (hiddenLocked) {
            wrap(g, body(def.hint()), x, y, w, DIM, alpha);
            return;
        }

        y += wrapMax(g, body(def.description()), x, y, w, INK, alpha, 3) + 2;

        if (def.isActive()) {
            int cur = Math.max(1, rank);
            String cd = String.format(Locale.ROOT, "%.1f", def.cooldownFor(cur) / 20.0)
                    + (rank > 0 && rank < def.maxRank ? " → " + String.format(Locale.ROOT, "%.1f", def.cooldownFor(rank + 1) / 20.0) : "");
            Component line = Component.empty().append(tr("solostatus.skills.cooldown", cd)).append("   ")
                    .append(tr("solostatus.skills.energy", Math.round(SkillSystem.energyCost(d, def))));
            text(g, font, line, x, y, DIM, alpha);
            y += 11;
        }

        y = skillPath(g, mx, my, def, x, y, w, alpha);

        // что нужно: только невыполненные условия, не больше двух
        int by = bottom - 14;
        if (!learned || rank < def.maxRank) {
            List<Requirement> need = new ArrayList<>();
            if (!def.availableFor(d)) {
                Component who = def.spec != null ? def.spec.displayName() : def.hunterClass != null ? def.hunterClass.displayName() : Component.empty();
                y += wrap(g, tr("solostatus.skills.only_for", who), x, y, w, RED, alpha);
            }
            Requirement lvl = Req.level(learned ? def.levelForRank(rank + 1) : def.baseLevel);
            if (!lvl.met(d)) need.add(lvl);
            if (!learned) for (Requirement r : def.requirements) if (!r.met(d)) need.add(r);
            int shown = 0;
            for (Requirement r : need) {
                if (shown == 2 || y + 14 > by - 2) break;
                y = requirement(g, r, x, y, w, alpha);
                shown++;
            }
            if (need.size() > shown && y + 10 <= by - 2) {
                text(g, font, tr("solostatus.ui.more_requirements", need.size() - shown), x, y, DIM, alpha);
                y += 10;
            }
            if (need.isEmpty() && !learned && def.source.autoAwaken() && y + 10 <= by - 2) {
                y += wrapMax(g, tr("solostatus.skills.awaken_hint"), x, y, w, DIM, alpha, 1);
            }
        }

        // кнопки: слоты (для изученного активного навыка) и изучение / улучшение
        int bx = x;
        if (def.isActive() && learned) {
            for (int i = 0; i < PlayerData.SKILL_SLOTS; i++) {
                int slot = i;
                boolean in = def.id.equals(d.slots[i]);
                Component key = bold(Keys.SKILLS[i].getTranslatedKeyMessage().getString());
                button(g, font, in ? Component.literal("[").append(key).append("]") : key, bx, by, 18, 14, mx, my, true, alpha);
                hots.add(new Hot(bx, by, 18, 14, () -> {
                    PacketDistributor.sendToServer(new Payloads.EquipSkill(slot, def.id));
                    play(ModSounds.CLICK.get(), 1.3f);
                }, null));
                bx += 20;
            }
            bx += 4;
        }
        boolean manual = (!def.source.autoAwaken() && def.source != SkillSource.CLASS_START) || learned;
        if (manual && rank < def.maxRank) {
            String err = SkillSystem.canAdvance(d, def);
            Component label = trBold(learned ? "solostatus.skills.upgrade" : "solostatus.skills.learn", def.costForRank(rank + 1));
            int bw = x + w - bx;
            button(g, font, label, bx, by, bw, 14, mx, my, err == null, alpha);
            if (err == null) hots.add(new Hot(bx, by, bw, 14, () -> {
                PacketDistributor.sendToServer(new Payloads.LearnSkill(def.id));
                play(ModSounds.STAT_UP.get(), 1.4f);
            }, null));
            else if (inside(mx, my, bx, by, bw, 14)) tooltip = List.of(Component.translatable(err));
        } else if (learned && rank >= def.maxRank) {
            textRight(g, font, trBold("solostatus.ui.max_rank"), x + w, by + 3, GOLD, alpha);
        }
    }

    /** Путь развития: предки навыка, сам навык и следующие за ним. Клик по иконке выбирает навык. */
    private int skillPath(GuiGraphics g, int mx, int my, SkillDef def, int x, int y, int w, float alpha) {
        List<SkillDef> chain = new ArrayList<>();
        SkillDef p = def.parent != null ? Skills.get(def.parent) : null;
        while (p != null && chain.size() < 3) {
            chain.add(0, p);
            p = p.parent != null ? Skills.get(p.parent) : null;
        }
        int self = chain.size();
        chain.add(def);
        for (SkillDef s : Skills.all()) if (def.id.equals(s.parent) && chain.size() < self + 3) chain.add(s);
        if (chain.size() < 2) return y;
        PlayerData d = d();
        Component label = tr("solostatus.ui.path");
        text(g, font, label, x, y + 3, DIM, alpha);
        int ix = x + font.width(label) + 4;
        for (int i = 0; i < chain.size() && ix + 14 <= x + w; i++) {
            SkillDef s = chain.get(i);
            boolean has = d.hasSkill(s.id);
            boolean hiddenLocked = s.hidden() && !has;
            if (i == self) g.renderOutline(ix - 1, y - 1, 14, 14, a(0xFFFFFFFF, alpha));
            if (hiddenLocked) textCenter(g, font, bold("?"), ix + 6, y + 3, VIOLET, alpha);
            else SkillIcons.draw(g, s, ix, y, 12, has, alpha);
            if (i != self) {
                hover(mx, my, ix, y, 12, 12, List.of(hiddenLocked ? Component.literal("???") : s.name()));
                String id = s.id;
                hots.add(new Hot(ix, y, 12, 12, () -> openSkill(id), null));
            }
            ix += 14;
            if (i < chain.size() - 1 && ix + 20 <= x + w) {
                text(g, font, body("›"), ix + 1, y + 2, DIM, alpha);
                ix += 8;
            }
        }
        return y + 15;
    }

    private int slotOf(String id) {
        for (int i = 0; i < PlayerData.SKILL_SLOTS; i++) if (id.equals(d().slots[i])) return i;
        return -1;
    }

    private void assignNextSlot(String id) {
        int target = -1;
        for (int i = 0; i < PlayerData.SKILL_SLOTS; i++) if (d().slots[i] == null) { target = i; break; }
        if (target < 0) target = (slotOf(id) + 1 + PlayerData.SKILL_SLOTS) % PlayerData.SKILL_SLOTS;
        PacketDistributor.sendToServer(new Payloads.EquipSkill(target, id));
        play(ModSounds.CLICK.get(), 1.3f);
    }

    private void openSkill(String id) {
        selectedSkill = id;
        if (win == Win.SKILLS && skillsSplit()) play(ModSounds.CLICK.get(), 1.15f); // карточка уже в окне
        else openOverlay(Overlay.SKILL);
    }

    // ============================================================ CLASS

    private boolean canPickSpec() {
        int lvl;
        try { lvl = SoloConfig.SPECIALIZATION_LEVEL.get(); } catch (Exception e) { lvl = 5; }
        return d().hasClass() && d().specialization == null && d().level >= lvl;
    }

    private void renderClass(GuiGraphics g, int mx, int my) {
        PlayerData d = d();
        int y = wy + 18;
        if (!d.hasClass()) {
            text(g, font, trBold("solostatus.class.choose_title"), cx0, y, 0xFFFFFFFF, row(0));
            text(g, font, tr("solostatus.class.choose_sub"), cx0, y + 11, DIM, row(0));
            y += 26;
            int cardW = (cw - 6) / 2, cardH = (wy + wh - 12 - y - 12) / 3;
            for (int i = 0; i < HunterClass.VALUES.length; i++) {
                HunterClass hc = HunterClass.VALUES[i];
                int x = cx0 + (i % 2) * (cardW + 6), cy = y + (i / 2) * (cardH + 6);
                float ra = row(1 + i);
                boolean h = inside(mx, my, x, cy, cardW, cardH);
                g.fill(x, cy, x + cardW, cy + cardH, a(h ? 0xD0123A60 : 0xA0081828, ra));
                g.renderOutline(x, cy, cardW, cardH, a(h ? 0xFFFFFFFF : hc.color, ra));
                if (ra > 0.5f) g.renderItem(new ItemStack(hc.icon()), x + 4, cy + 4);
                text(g, font, bold(hc.displayName()), x + 24, cy + 5, hc.color, ra);
                text(g, font, body(Component.empty().append(hc.primary.shortName()).append(" +3  ").append(hc.secondary.shortName()).append(" +2")), x + 24, cy + 15, DIM, ra);
                hover(mx, my, x, cy, cardW, cardH, List.of(hc.displayName(), hc.description()));
                hots.add(new Hot(x, cy, cardW, cardH, () -> confirm(Component.translatable("solostatus.class.confirm", hc.displayName()),
                        Component.translatable("solostatus.class.confirm.desc"),
                        () -> PacketDistributor.sendToServer(new Payloads.ChooseClass(hc.ordinal()))), null));
            }
            return;
        }
        HunterClass hc = d.hunterClass;
        if (row(0) > 0.5f) g.renderItem(new ItemStack(hc.icon()), cx0, y);
        text(g, font, title(hc.displayName()), cx0 + 20, y + 1, hc.color, row(0));
        y += 20;
        y += wrap(g, body(hc.description()), cx0, y, cw, INK, row(1)) + 6;
        divider(g, cx0, y, cw, row(2));
        y += 6;
        text(g, font, trBold("solostatus.class.spec_title"), cx0, y, 0xFFFFFFFF, row(3));
        y += 12;
        int specLevel;
        try { specLevel = SoloConfig.SPECIALIZATION_LEVEL.get(); } catch (Exception e) { specLevel = 5; }
        if (d.specialization == null) {
            boolean can = d.level >= specLevel;
            text(g, font, can ? tr("solostatus.class.spec_choose") : tr("solostatus.class.spec_locked", specLevel), cx0, y, can ? GOLD : DIM, row(4));
            y += 12;
            int cardW = (cw - 6) / 2, cardH = 54;
            Specialization[] specs = hc.specializations();
            for (int i = 0; i < specs.length; i++) {
                Specialization s = specs[i];
                int x = cx0 + i * (cardW + 6);
                float ra = row(5 + i);
                boolean h = can && inside(mx, my, x, y, cardW, cardH);
                g.fill(x, y, x + cardW, y + cardH, a(h ? 0xD0123A60 : 0xA0081828, ra));
                g.renderOutline(x, y, cardW, cardH, a(h ? 0xFFFFFFFF : can ? EDGE : LOCK, ra));
                text(g, font, bold(s.displayName()), x + 5, y + 4, can ? INK : DIM, ra);
                wrap(g, body(s.description()), x + 5, y + 15, cardW - 10, DIM, ra);
                if (can) hots.add(new Hot(x, y, cardW, cardH, () -> confirm(Component.translatable("solostatus.spec.confirm", s.displayName()),
                        Component.translatable("solostatus.spec.confirm.desc"),
                        () -> PacketDistributor.sendToServer(new Payloads.ChooseSpec(s.ordinal()))), null));
            }
            return;
        }
        Specialization s = d.specialization;
        text(g, font, bold(d.advanced ? s.advancedName() : s.displayName()), cx0, y, GOLD, row(4));
        y += 11;
        y += wrap(g, body(s.description()), cx0, y, cw, INK, row(5)) + 6;
        text(g, font, trBold("solostatus.class.trial"), cx0, y, 0xFFFFFFFF, row(6));
        y += 12;
        if (d.advanced) {
            wrap(g, tr("solostatus.class.trial_done"), cx0, y, cw, GREEN, row(7));
        } else {
            int adv;
            try { adv = SoloConfig.ADVANCEMENT_LEVEL.get(); } catch (Exception e) { adv = 40; }
            if (d.level < adv) {
                text(g, font, tr("solostatus.class.trial_locked", adv), cx0, y, DIM, row(7));
                y += 11;
            }
            int i = 0;
            for (Requirement r : ClassSystem.trialRequirements(hc)) y = requirement(g, r, cx0, y, cw, row(8 + i++));
        }
    }

    // ============================================================ MASTERY

    private void renderMastery(GuiGraphics g, int mx, int my) {
        PlayerData d = d();
        List<String> keys = new ArrayList<>();
        for (WeaponType t : WeaponType.VALUES) if (t.hasMastery()) keys.add(t.key);
        d.mastery.keySet().stream().filter(k -> k.startsWith("magic:")).sorted().forEach(keys::add);
        int y = wy + 18, rowH = 22;
        int visible = (wy + wh - 10 - y) / rowH;
        scroll = Mth.clamp(scroll, 0, Math.max(0, keys.size() - visible));
        for (int i = scroll; i < keys.size() && i - scroll < visible; i++) {
            String key = keys.get(i);
            double xp = d.masteryXp(key);
            int lvl = MasteryMath.level(xp);
            boolean magic = key.startsWith("magic:");
            Component name = magic ? MasterySystem.schoolName(key.substring(6)) : WeaponType.byKey(key).displayName();
            float ra = row(i - scroll);
            if (ra > 0.5f) g.renderItem(new ItemStack(magic ? Items.ENCHANTED_BOOK : WeaponType.byKey(key).icon), cx0, y + 2);
            text(g, font, bold(name), cx0 + 20, y + 1, lvl > 0 ? INK : DIM, ra);
            textRight(g, font, bold(lvl + " / " + MasteryMath.MAX_LEVEL), cx0 + cw, y + 1, GOLD, ra);
            bar(g, cx0 + 20, y + 12, cw - 20 - 4 * 15, 4, MasteryMath.progress(xp), 0xFF4FA8FF, ra);
            int[] marks = {10, 25, 40, 50};
            for (int m = 0; m < 4; m++) {
                int bx = cx0 + cw - (4 - m) * 15 + 2;
                boolean on = lvl >= marks[m];
                g.fill(bx, y + 11, bx + 12, y + 17, a(on ? 0xFF2A6FA8 : 0x40102030, ra));
                hover(mx, my, bx, y + 10, 12, 8, List.of(Component.translatable("solostatus.notice.mastery_milestone." + marks[m])));
            }
            y += rowH;
        }
    }

    // ============================================================ ACHIEVEMENTS

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
        rows.sort(Comparator.comparingInt((AchievementDef a) -> newAchievements.contains(a.id()) ? 0 : d.achievements.contains(a.id()) ? 1 : 2));
        long done = rows.stream().filter(a -> !a.hidden() && d.achievements.contains(a.id())).count();
        int y = wy + 18;
        text(g, font, tr("solostatus.achievements.summary", done, total, hiddenDone, hiddenTotal), cx0, y, DIM, row(0));
        Component tl = trBold("solostatus.ui.titles_button");
        int tw = font.width(tl) + 12;
        button(g, font, tl, cx0 + cw - tw, y - 3, tw, 13, mx, my, true, row(0));
        hots.add(new Hot(cx0 + cw - tw, y - 3, tw, 13, () -> openOverlay(Overlay.TITLES), null));
        y += 14;
        int rowH = 26;
        int count = rows.size() + (hiddenDone < hiddenTotal ? 1 : 0);
        int visible = (wy + wh - 8 - y) / rowH;
        scroll = Mth.clamp(scroll, 0, Math.max(0, count - visible));
        for (int i = scroll; i < count && i - scroll < visible; i++) {
            float ra = row(1 + i - scroll);
            if (i >= rows.size()) {
                g.renderOutline(cx0 - 2, y, cw + 4, rowH - 3, a(0x80C79BFF, ra));
                text(g, font, trBold("solostatus.achievements.hidden", hiddenTotal - hiddenDone), cx0 + 20, y + 3, VIOLET, ra);
                text(g, font, tr("solostatus.achievements.hidden.desc"), cx0 + 20, y + 13, DIM, ra);
                text(g, font, bold("?"), cx0 + 5, y + 7, VIOLET, ra);
                y += rowH;
                continue;
            }
            AchievementDef def = rows.get(i);
            boolean got = d.achievements.contains(def.id());
            g.fill(cx0 - 2, y, cx0 + cw + 2, y + rowH - 3, a(got ? 0x2055C8FF : 0x10000000, ra));
            if (got) g.fill(cx0 - 2, y, cx0, y + rowH - 3, a(def.hidden() ? VIOLET : EDGE, ra));
            if (ra > 0.5f) g.renderItem(new ItemStack(def.icon()), cx0 + 1, y + 3);
            if (!got) g.fill(cx0 + 1, y + 3, cx0 + 17, y + 19, a(0x90000000, ra));
            Component name = bold(def.name());
            text(g, font, name, cx0 + 20, y + 2, got ? INK : DIM, ra);
            if (newAchievements.contains(def.id())) text(g, font, bold("NEW"), cx0 + 24 + font.width(name), y + 2, GOLD, ra);
            text(g, font, body(def.description()), cx0 + 20, y + 12, DIM, ra);
            Component reward = def.title() ? trBold("solostatus.ui.reward_title") : def.rewardSkill() != null ? trBold("solostatus.ui.reward_skill")
                    : def.sp() > 0 ? trBold("solostatus.achievements.sp", def.sp()) : def.xp() > 0 ? trBold("solostatus.achievements.xp", def.xp()) : Component.empty();
            textRight(g, font, reward, cx0 + cw, y + 2, def.title() ? VIOLET : GOLD, ra);
            if (!got) {
                double p = 0;
                for (Requirement r : def.requirements()) p += r.progress(d);
                p /= Math.max(1, def.requirements().size());
                bar(g, cx0 + cw - 50, y + 14, 50, 3, p, 0xFF4FA8FF, ra);
            }
            if (def.title()) hover(mx, my, cx0, y, cw, rowH - 3, List.of(def.name(), def.titleEffect()));
            y += rowH;
        }
    }

    // ============================================================ вложенные окна

    private void openOverlay(Overlay o) {
        overlay = o;
        overlayAt = System.currentTimeMillis();
        play(ModSounds.CLICK.get(), o == Overlay.CONFIRM ? 0.8f : 1.1f);
    }

    private void confirm(Component title, Component text, Runnable action) {
        confirmTitle = title;
        confirmText = text;
        confirmAction = action;
        openOverlay(Overlay.CONFIRM);
    }

    private void askRespec() {
        confirm(Component.translatable("solostatus.ui.respec_title"), Component.translatable("solostatus.ui.respec_text"), null);
    }

    private void renderOverlay(GuiGraphics g, int mx, int my) {
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        g.fill(0, 0, width, height, 0x400D2238);
        float u = since(overlayAt) / 180f;
        int ow = Math.min(210, width - 20), oh;
        int ox, oy;
        switch (overlay) {
            case SKILL -> {
                oh = Math.min(230, height - 20);
                ox = Math.min(wx + ww - 60, width - ow - 8);
                oy = Math.max(8, wy + 30);
                if (oy + oh > height - 8) oy = height - 8 - oh;
                if (window(g, ox, oy, ow, oh, u, 1f) >= oh - 1) renderSkillDetail(g, mx, my, ox, oy, ow, oh);
            }
            case TITLES -> {
                oh = Math.min(240, height - 20);
                ox = (width - ow) / 2 + 60;
                oy = (height - oh) / 2;
                if (window(g, ox, oy, ow, oh, u, 1f) >= oh - 1) renderTitles(g, mx, my, ox, oy, ow, oh);
            }
            case SOUNDS -> {
                oh = Math.min(15 * ModSounds.ALL.size() + 50, height - 20);
                ox = (width - ow) / 2 + 60;
                oy = (height - oh) / 2;
                if (window(g, ox, oy, ow, oh, u, 1f) >= oh - 1) renderSounds(g, mx, my, ox, oy, ow, oh);
            }
            case CONFIRM -> {
                oh = 96;
                ox = (width - ow) / 2 + 60;
                oy = (height - oh) / 2;
                if (window(g, ox, oy, ow, oh, u, 1f) >= oh - 1) renderConfirm(g, mx, my, ox, oy, ow, oh);
            }
            default -> { }
        }
        g.pose().popPose();
    }

    private void renderSkillDetail(GuiGraphics g, int mx, int my, int ox, int oy, int ow, int oh) {
        PlayerData d = d();
        SkillDef def = Skills.get(selectedSkill);
        if (def == null) return;
        int x = ox + 10, w = ow - 20, y = oy + 10;
        boolean learned = d.hasSkill(def.id);
        boolean hiddenLocked = def.hidden() && !learned;
        int rank = d.skillRank(def.id);
        text(g, font, bold(hiddenLocked ? Component.literal("???") : def.name()), x, y, hiddenLocked ? VIOLET : 0xFFFFFFFF, 1f);
        y += 11;
        text(g, font, body(Component.empty().append(def.category.displayName()).append(" · ")
                .append(Component.translatable("solostatus.source." + def.source.name().toLowerCase()))), x, y, DIM, 1f);
        y += 11;
        for (int i = 0; i < def.maxRank; i++) g.fill(x + i * 14, y, x + i * 14 + 11, y + 3, i < rank ? EDGE : 0x30FFFFFF);
        y += 8;
        if (hiddenLocked) {
            wrap(g, body(def.hint()), x, y, w, DIM, 1f);
            return;
        }
        y += wrap(g, body(def.description()), x, y, w, INK, 1f) + 4;
        if (def.isActive()) {
            int cur = Math.max(1, rank);
            String cd = String.format(Locale.ROOT, "%.1f", def.cooldownFor(cur) / 20.0)
                    + (rank > 0 && rank < def.maxRank ? " → " + String.format(Locale.ROOT, "%.1f", def.cooldownFor(rank + 1) / 20.0) : "");
            text(g, font, tr("solostatus.skills.cooldown", cd), x, y, DIM, 1f);
            y += 10;
            text(g, font, tr("solostatus.skills.energy", Math.round(SkillSystem.energyCost(d, def))), x, y, DIM, 1f);
            y += 12;
        }
        if (!learned) {
            text(g, font, trBold("solostatus.skills.requirements"), x, y, GOLD, 1f);
            y += 11;
            if (!def.availableFor(d)) {
                Component who = def.spec != null ? def.spec.displayName() : def.hunterClass != null ? def.hunterClass.displayName() : Component.empty();
                y += wrap(g, tr("solostatus.skills.only_for", who), x, y, w, RED, 1f);
            }
            y = requirement(g, Req.level(def.baseLevel), x, y, w, 1f);
            for (Requirement r : def.requirements) y = requirement(g, r, x, y, w, 1f);
            if (def.source.autoAwaken()) y += wrap(g, tr("solostatus.skills.awaken_hint"), x, y + 2, w, DIM, 1f);
        } else if (rank < def.maxRank) {
            y = requirement(g, Req.level(def.levelForRank(rank + 1)), x, y, w, 1f);
        }
        // кнопки
        int by = oy + oh - 22;
        if (def.isActive() && learned) {
            int bw = (w - 6) / 4;
            for (int i = 0; i < PlayerData.SKILL_SLOTS; i++) {
                int slot = i;
                boolean in = def.id.equals(d.slots[i]);
                Component key = bold(Keys.SKILLS[i].getTranslatedKeyMessage().getString());
                button(g, font, in ? Component.literal("[").append(key).append("]") : key, x + i * (bw + 2), by - 16, bw, 13, mx, my, true, 1f);
                overlayHots.add(new Hot(x + i * (bw + 2), by - 16, bw, 13, () -> {
                    PacketDistributor.sendToServer(new Payloads.EquipSkill(slot, def.id));
                    play(ModSounds.CLICK.get(), 1.3f);
                }, null));
            }
        }
        boolean manual = (!def.source.autoAwaken() && def.source != SkillSource.CLASS_START) || learned;
        if (manual && rank < def.maxRank) {
            String err = SkillSystem.canAdvance(d, def);
            Component label = trBold(learned ? "solostatus.skills.upgrade" : "solostatus.skills.learn", def.costForRank(rank + 1));
            button(g, font, label, x, by, w, 14, mx, my, err == null, 1f);
            if (err == null) overlayHots.add(new Hot(x, by, w, 14, () -> {
                PacketDistributor.sendToServer(new Payloads.LearnSkill(def.id));
                play(ModSounds.STAT_UP.get(), 1.4f);
            }, null));
            else if (inside(mx, my, x, by, w, 14)) tooltip = List.of(Component.translatable(err));
        }
    }

    private void renderTitles(GuiGraphics g, int mx, int my, int ox, int oy, int ow, int oh) {
        PlayerData d = d();
        int x = ox + 10, w = ow - 20, y = oy + 10;
        text(g, font, trBold("solostatus.ui.titles"), x, y, 0xFFFFFFFF, 1f);
        y += 11;
        y += wrap(g, tr("solostatus.ui.titles_desc"), x, y, w, DIM, 1f) + 4;
        boolean none = d.title.isEmpty();
        boolean nh = inside(mx, my, x - 2, y, w + 4, 12);
        text(g, font, tr("solostatus.ui.no_title"), x, y + 2, none ? GOLD : nh ? INK : DIM, 1f);
        overlayHots.add(new Hot(x - 2, y, w + 4, 12, () -> PacketDistributor.sendToServer(new Payloads.SetTitle("")), null));
        y += 14;
        int lockedHidden = 0;
        for (AchievementDef def : Achievements.titles()) {
            boolean got = d.achievements.contains(def.id());
            if (!got && def.hidden()) { lockedHidden++; continue; }
            if (y + 22 > oy + oh - 8) break;
            boolean eq = d.titleIs(def.id());
            boolean h = got && inside(mx, my, x - 2, y, w + 4, 21);
            if (eq || h) g.fill(x - 2, y, x + w + 2, y + 21, eq ? 0x40C79BFF : 0x20C79BFF);
            text(g, font, bold(def.name()), x, y + 1, got ? (eq ? 0xFFFFFFFF : VIOLET) : DIM, 1f);
            if (eq) textRight(g, font, trBold("solostatus.ui.equipped"), x + w, y + 1, GOLD, 1f);
            text(g, font, body(got ? def.titleEffect() : def.description()), x, y + 11, DIM, 1f);
            if (got) overlayHots.add(new Hot(x - 2, y, w + 4, 21, () -> PacketDistributor.sendToServer(new Payloads.SetTitle(def.id())), null));
            y += 23;
        }
        if (lockedHidden > 0 && y + 10 < oy + oh - 6) text(g, font, tr("solostatus.ui.titles_hidden", lockedHidden), x, y + 2, VIOLET, 1f);
    }

    private void renderSounds(GuiGraphics g, int mx, int my, int ox, int oy, int ow, int oh) {
        int x = ox + 10, w = ow - 20, y = oy + 10;
        text(g, font, trBold("solostatus.ui.sounds_title"), x, y, 0xFFFFFFFF, 1f);
        y += 12;
        for (Map.Entry<String, net.neoforged.neoforge.registries.DeferredHolder<SoundEvent, SoundEvent>> e : ModSounds.ALL.entrySet()) {
            if (y + 14 > oy + oh - 18) break;
            boolean h = inside(mx, my, x - 2, y, w + 4, 13);
            if (h) g.fill(x - 2, y, x + w + 2, y + 13, 0x2055C8FF);
            text(g, font, bold("▶"), x, y + 3, h ? 0xFFFFFFFF : EDGE, 1f);
            text(g, font, tr("solostatus.sound." + e.getKey().replace("system.", "")), x + 12, y + 3, INK, 1f);
            Holder<SoundEvent> holder = e.getValue();
            overlayHots.add(new Hot(x - 2, y, w + 4, 13, () -> play(holder.value(), 1f), null));
            y += 14;
        }
        wrap(g, tr("solostatus.ui.sounds_note"), x, oy + oh - 16, w, DIM, 1f);
    }

    private void renderConfirm(GuiGraphics g, int mx, int my, int ox, int oy, int ow, int oh) {
        int x = ox + 10, w = ow - 20, y = oy + 10;
        if (confirmTitle != null) text(g, font, bold(confirmTitle), x, y, 0xFFFFFFFF, 1f);
        y += 13;
        if (confirmText != null) wrap(g, body(confirmText), x, y, w, DIM, 1f);
        int by = oy + oh - 20;
        int bw = (w - 6) / 2;
        if (confirmAction == null) {
            // перераспределение: два варианта
            button(g, font, trBold("solostatus.respec.skills"), x, by, bw, 14, mx, my, true, 1f);
            button(g, font, trBold("solostatus.respec.stats"), x + bw + 6, by, bw, 14, mx, my, true, 1f);
            overlayHots.add(new Hot(x, by, bw, 14, () -> { PacketDistributor.sendToServer(new Payloads.Respec(0)); overlay = Overlay.NONE; }, null));
            overlayHots.add(new Hot(x + bw + 6, by, bw, 14, () -> { PacketDistributor.sendToServer(new Payloads.Respec(1)); overlay = Overlay.NONE; }, null));
        } else {
            button(g, font, trBold("solostatus.ui.confirm"), x, by, bw, 14, mx, my, true, 1f);
            button(g, font, trBold("solostatus.ui.cancel"), x + bw + 6, by, bw, 14, mx, my, true, 1f);
            Runnable act = confirmAction;
            overlayHots.add(new Hot(x, by, bw, 14, () -> {
                act.run();
                overlay = Overlay.NONE;
                winAt = System.currentTimeMillis();
                play(ModSounds.AWAKENING.get(), 1f);
            }, null));
            overlayHots.add(new Hot(x + bw + 6, by, bw, 14, () -> overlay = Overlay.NONE, null));
        }
    }

    // ============================================================ общие

    private int requirement(GuiGraphics g, Requirement r, int x, int y, int w, float alpha) {
        PlayerData d = d();
        boolean met = r.met(d);
        int h = wrap(g, body(Component.literal(met ? "✔ " : "• ").append(r.describe(d))), x, y, w, met ? GREEN : DIM, alpha);
        if (!met) {
            bar(g, x + 2, y + h, w - 4, 2, r.progress(d), 0xFF4FA8FF, alpha);
            h += 4;
        }
        return y + h + 1;
    }

    /** Как wrap, но не больше maxLines строк. */
    private int wrapMax(GuiGraphics g, Component text, int x, int y, int width, int color, float alpha, int maxLines) {
        int yy = y, n = 0;
        for (FormattedCharSequence line : font.split(text, width)) {
            if (n++ >= maxLines) break;
            if (alpha >= 0.03f) g.drawString(font, line, x, yy, a(color, alpha), false);
            yy += 10;
        }
        return yy - y;
    }

    private int wrap(GuiGraphics g, Component text, int x, int y, int width, int color, float alpha) {
        int yy = y;
        for (FormattedCharSequence line : font.split(text, width)) {
            if (alpha >= 0.03f) g.drawString(font, line, x, yy, a(color, alpha), false);
            yy += 10;
        }
        return yy - y;
    }

    // ============================================================ ввод

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (since(openAt) < 450) return true;
        if (overlay != Overlay.NONE) {
            for (int i = overlayHots.size() - 1; i >= 0; i--) {
                Hot h = overlayHots.get(i);
                if (inside(mx, my, h.x(), h.y(), h.w(), h.h())) {
                    if (button == 0) { play(ModSounds.CLICK.get(), 1.2f); h.left().run(); }
                    return true;
                }
            }
            overlay = Overlay.NONE; // клик вне вложенного окна закрывает его
            return true;
        }
        for (int i = hots.size() - 1; i >= 0; i--) {
            Hot h = hots.get(i);
            if (inside(mx, my, h.x(), h.y(), h.w(), h.h())) {
                if (button == 0) h.left().run();
                else if (button == 1 && h.right() != null) h.right().run();
                return true;
            }
        }
        if (button == 0 && Math.abs(mx - playerX) < 70 && my > height * 0.12 && my < floorY + 6) {
            draggingPlayer = true;
            dragStartX = mx;
            dragStartYaw = yaw;
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (draggingPlayer) {
            yaw = dragStartYaw + (float) (mx - dragStartX) * 1.2f;
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        draggingPlayer = false;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (sy != 0 && overlay == Overlay.NONE) {
            scroll = Math.max(0, scroll - (int) Math.signum(sy));
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256 && overlay != Overlay.NONE) { // Esc закрывает вложенное окно
            overlay = Overlay.NONE;
            return true;
        }
        if (Keys.OPEN_STATUS.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        if (keyCode == 258 && d().hasClass()) { // Tab — следующее окно
            Win[] order = {Win.STATUS, Win.SKILLS, Win.CLASS, Win.MASTERY, Win.ACHIEVEMENTS};
            int idx = Arrays.asList(order).indexOf(win);
            int next = hasShiftDown() ? (idx - 1 + order.length) % order.length : (idx + 1) % order.length;
            switchWin(order[next]);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
