package com.vladosiwe.solostatus.client.hud;

import com.vladosiwe.solostatus.client.ClientState;
import com.vladosiwe.solostatus.client.Keys;
import com.vladosiwe.solostatus.client.screen.Holo;
import com.vladosiwe.solostatus.config.SoloConfig;
import com.vladosiwe.solostatus.data.PlayerData;
import com.vladosiwe.solostatus.skill.SkillDef;
import com.vladosiwe.solostatus.skill.Skills;
import com.vladosiwe.solostatus.stats.StatFormulas;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/** HUD: уровень и ранг, энергия, панель активных навыков с перезарядкой и системные уведомления. */
public final class SoloHud {
    private SoloHud() {}

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        boolean show;
        try {
            show = SoloConfig.SHOW_HUD.get();
        } catch (Exception e) {
            show = true;
        }
        PlayerData d = ClientState.data;
        Font font = mc.font;
        int sw = g.guiWidth();
        int sh = g.guiHeight();

        if (show && ClientState.synced && mc.screen == null) {
            if (!d.hasClass()) {
                Component hint = Component.translatable("solostatus.hud.awaken", Keys.OPEN_STATUS.getTranslatedKeyMessage());
                int w = font.width(hint) + 12;
                int x = sw / 2 - w / 2;
                int y = 6;
                g.fill(x, y, x + w, y + 14, 0xB0061424);
                g.renderOutline(x, y, w, 14, (System.currentTimeMillis() / 500) % 2 == 0 ? Holo.BORDER : Holo.ACCENT);
                g.drawCenteredString(font, hint, sw / 2, y + 3, Holo.TEXT);
            } else if (!mc.player.isSpectator()) {
                renderBar(g, mc, font, d, sw, sh);
            }
        }
        renderVoice(g, font, sw);
        renderNotices(g, font, sw);
    }

    /** Индикатор микрофона: игрок всегда видит, когда распознавание слушает. */
    private static void renderVoice(GuiGraphics g, Font font, int sw) {
        boolean indicator;
        try {
            indicator = SoloConfig.VOICE_INDICATOR.get();
        } catch (Exception e) {
            indicator = true;
        }
        var state = com.vladosiwe.solostatus.client.voice.VoiceClient.state();
        if (!indicator || state == com.vladosiwe.solostatus.client.voice.VoskEngine.State.STOPPED
                || state == com.vladosiwe.solostatus.client.voice.VoskEngine.State.ERROR) return;
        boolean capturing = com.vladosiwe.solostatus.client.voice.VoiceClient.capturing();
        boolean loading = state == com.vladosiwe.solostatus.client.voice.VoskEngine.State.LOADING;
        Component label = Component.translatable(loading ? "solostatus.hud.voice.loading" : capturing ? "solostatus.hud.voice.active" : "solostatus.hud.voice");
        int w = font.width(label) + 8;
        int x = sw - w - 4;
        int y = 4;
        g.fill(x, y, x + w, y + 12, 0xA0061424);
        int color = loading ? Holo.DIM : capturing ? ((System.currentTimeMillis() / 600) % 2 == 0 ? Holo.ACCENT : 0xFF3FB4FF) : Holo.DIM;
        g.renderOutline(x, y, w, 12, color);
        g.drawString(font, label, x + 4, y + 2, color, false);
        String heard = com.vladosiwe.solostatus.client.voice.VoiceClient.lastHeard();
        if (!heard.isEmpty()) {
            Component h = Component.translatable("solostatus.voice.heard", heard);
            g.drawString(font, h, sw - font.width(h) - 6, y + 16, Holo.TEXT, true);
        }
    }

    private static void renderBar(GuiGraphics g, Minecraft mc, Font font, PlayerData d, int sw, int sh) {
        int baseX = sw / 2 + 98;
        int y = sh - 22;
        long gameTime = mc.level != null ? mc.level.getGameTime() : 0;
        for (int i = 0; i < PlayerData.SKILL_SLOTS; i++) {
            int x = baseX + i * 22;
            if (x + 20 > sw) break;
            g.fill(x, y, x + 20, y + 20, 0xB0061424);
            g.renderOutline(x, y, 20, 20, Holo.BORDER);
            SkillDef def = Skills.get(d.slots[i]);
            if (def != null) {
                g.renderItem(new ItemStack(def.icon), x + 2, y + 2);
                Long ready = d.cooldowns.get(def.id);
                if (ready != null && ready > gameTime) {
                    int total = def.cooldownFor(Math.max(1, d.skillRank(def.id)));
                    float frac = Mth.clamp((ready - gameTime) / (float) total, 0, 1);
                    int h = (int) (18 * frac);
                    g.pose().pushPose();
                    g.pose().translate(0, 0, 200);
                    g.fill(x + 1, y + 19 - h, x + 19, y + 19, 0xB0000000);
                    String secs = String.valueOf((ready - gameTime + 19) / 20);
                    g.drawCenteredString(font, secs, x + 10, y + 6, Holo.TEXT);
                    g.pose().popPose();
                }
            }
            g.pose().pushPose();
            g.pose().translate(0, 0, 210);
            String key = Keys.SKILLS[i].getTranslatedKeyMessage().getString();
            if (key.length() > 2) key = key.substring(0, 2);
            g.drawString(font, key, x + 1, y - 8, Holo.DIM, true);
            g.pose().popPose();
        }
        // энергия
        float max = (float) StatFormulas.maxEnergy(d);
        int bw = PlayerData.SKILL_SLOTS * 22 - 2;
        if (baseX + bw <= sw) {
            Holo.bar(g, baseX, y - 13, bw, 3, d.energy / Math.max(1, max), 0xFF3FB4FF);
        }
        // уровень и ранг слева от хотбара
        String lvl = "Lv." + d.level;
        int lx = sw / 2 - 98 - font.width(lvl) - 18;
        if (lx > 2) {
            g.drawString(font, lvl, lx, y + 6, Holo.ACCENT, true);
            int rx = lx + font.width(lvl) + 3;
            g.fill(rx, y + 4, rx + 12, y + 16, 0xC0061424);
            g.renderOutline(rx, y + 4, 12, 12, d.rank.color);
            g.drawCenteredString(font, d.rank.letter, rx + 6, y + 6, d.rank.color);
        }
    }

    private static void renderNotices(GuiGraphics g, Font font, int sw) {
        long now = System.currentTimeMillis();
        int y = 24;
        for (ClientState.Notice n : ClientState.NOTICES) {
            float p = n.progress(now);
            float a = p < 0.08f ? p / 0.08f : p > 0.85f ? (1 - p) / 0.15f : 1f;
            a = Mth.clamp(a, 0f, 1f);
            boolean big = n.kind() == 2 || n.kind() == 4 || n.kind() == 5 || n.kind() == 7;
            int titleColor = switch (n.kind()) {
                case 1 -> Holo.GOLD;
                case 2, 7 -> Holo.ACCENT;
                case 4 -> Holo.PURPLE;
                case 5 -> 0xFFFF5050;
                case 6 -> Holo.RED;
                default -> Holo.TEXT;
            };
            Component header = Component.translatable(n.kind() == 6 ? "solostatus.hud.alert" : "solostatus.hud.system");
            int w = Math.max(Math.max(font.width(n.title()), font.width(n.body())) + 24, big ? 220 : 160);
            w = Math.min(w, sw - 20);
            int h = big ? 50 : 40;
            int x = sw / 2 - w / 2;
            float slide = p < 0.08f ? (1 - p / 0.08f) * 10 : 0;
            g.pose().pushPose();
            g.pose().translate(0, -slide, 300);
            Holo.panel(g, x, y, w, h, a);
            if (a > 0.1f) {
                int ta = Math.max(5, (int) (a * 255)) << 24;
                g.drawCenteredString(font, header, sw / 2, y + 4, (Holo.DIM & 0xFFFFFF) | ta);
                if (big) {
                    g.pose().pushPose();
                    g.pose().translate(sw / 2f, y + 16, 0);
                    g.pose().scale(1.25f, 1.25f, 1);
                    g.drawCenteredString(font, n.title(), 0, 0, (titleColor & 0xFFFFFF) | ta);
                    g.pose().popPose();
                    g.drawCenteredString(font, n.body(), sw / 2, y + 34, (Holo.TEXT & 0xFFFFFF) | ta);
                } else {
                    g.drawCenteredString(font, n.title(), sw / 2, y + 15, (titleColor & 0xFFFFFF) | ta);
                    g.drawCenteredString(font, n.body(), sw / 2, y + 27, (Holo.TEXT & 0xFFFFFF) | ta);
                }
            }
            g.pose().popPose();
            y += h + 6;
        }
    }
}
