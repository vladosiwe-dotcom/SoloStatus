package com.vladosiwe.solostatus.client.hud;

import com.vladosiwe.solostatus.client.ClientState;
import com.vladosiwe.solostatus.client.ui.SkillIcons;
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
        int baseX = sw / 2 + 91 + 28; // правее индикатора атаки / второй руки
        int y = sh - 22;
        long gameTime = mc.level != null ? mc.level.getGameTime() : 0;
        for (int i = 0; i < PlayerData.SKILL_SLOTS; i++) {
            int x = baseX + i * 22;
            if (x + 20 > sw) break;
            g.fill(x, y, x + 20, y + 20, 0xB0061424);
            g.renderOutline(x, y, 20, 20, Holo.BORDER);
            SkillDef def = Skills.get(d.slots[i]);
            if (def != null) {
                SkillIcons.draw(g, def, x + 2, y + 2, 16, true, 1f);
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
            Holo.bar(g, baseX, y - 13, bw, 3, d.energy / Math.max(1, max), StatFormulas.usesMana(d) ? 0xFF3FB4FF : 0xFF7BD86A);
        }
        // Блок охотника слева от хотбара: уровень, ранг и СВОЯ полоска опыта системы.
        // Ванильная зелёная полоска остаётся для зачарования и не связана с уровнем охотника.
        int bw2 = 72;
        int bx = sw / 2 - 91 - 29 - bw2 - 4; // левее слота второй руки
        int by = sh - 24;
        if (bx > 2) {
            g.fill(bx, by, bx + bw2, by + 22, 0xB0061424);
            g.renderOutline(bx, by, bw2, 22, 0x803FB4FF);
            g.drawString(font, "Lv." + d.level, bx + 4, by + 3, Holo.ACCENT, true);
            int rx = bx + bw2 - 15;
            g.fill(rx, by + 2, rx + 12, by + 13, 0xC0061424);
            g.renderOutline(rx, by + 2, 12, 11, d.rank.color);
            g.drawCenteredString(font, d.rank.letter, rx + 6, by + 4, d.rank.color);
            double frac;
            int color;
            if (d.level < PlayerData.MAX_LEVEL) {
                frac = d.xp / (double) com.vladosiwe.solostatus.level.LevelSystem.xpToNext(d.level);
                color = 0xFFFFD36B;
            } else {
                frac = d.overflowXp / (double) com.vladosiwe.solostatus.level.LevelSystem.overflowNeed(d.overflowAwakenings);
                color = 0xFFFF9B3F;
            }
            // Подсветка полоски сразу после получения опыта
            boolean fresh = !ClientState.XP_POPUPS.isEmpty()
                    && System.currentTimeMillis() - ClientState.XP_POPUPS.get(ClientState.XP_POPUPS.size() - 1).start < 600;
            Holo.bar(g, bx + 4, by + 15, bw2 - 8, 3, frac, fresh ? 0xFFFFFFFF : color);
            renderXpPopups(g, font, bx + bw2, by - 2);
        }
    }

    /** Всплывающие «+N опыта» над блоком охотника; поднимаются и тают за 2 секунды. */
    private static void renderXpPopups(GuiGraphics g, Font font, int right, int bottom) {
        long now = System.currentTimeMillis();
        ClientState.XP_POPUPS.removeIf(p -> now - p.start > 2000);
        int i = 0;
        for (int k = ClientState.XP_POPUPS.size() - 1; k >= 0; k--) {
            ClientState.XpPopup p = ClientState.XP_POPUPS.get(k);
            float t = (now - p.start) / 2000f;
            int alpha = Mth.clamp((int) ((t < 0.75f ? 1f : (1 - t) / 0.25f) * 255), 8, 255);
            int rise = (int) (t * 8);
            var reason = com.vladosiwe.solostatus.level.XpReason.byIndex(p.reason);
            Component text = Component.translatable("solostatus.xp.gain", p.amount, Component.translatable(reason.key()));
            int color = switch (reason) {
                case BOSS, TRIAL, RANK -> 0xFF5050;
                case ACHIEVEMENT, MASTERY -> 0xC08CFF;
                case DISCOVERY -> 0x7DFF9B;
                default -> 0xFFD36B;
            };
            g.drawString(font, text, right - font.width(text), bottom - 9 - i * 10 - rise, (alpha << 24) | color, true);
            i++;
        }
    }

    private static void renderNotices(GuiGraphics g, Font font, int sw) {
        long now = System.currentTimeMillis();
        int y = 24;
        for (ClientState.Notice n : ClientState.NOTICES) {
            float p = n.progress(now);
            float a = p < 0.08f ? p / 0.08f : p > 0.85f ? (1 - p) / 0.15f : 1f;
            a = Mth.clamp(a, 0f, 1f);
            boolean big = n.kind() == 2 || n.kind() == 3 || n.kind() == 4 || n.kind() == 5 || n.kind() == 7;
            int titleColor = switch (n.kind()) {
                case 1, 3 -> Holo.GOLD;
                case 2, 7 -> Holo.ACCENT;
                case 4 -> Holo.PURPLE;
                case 5 -> 0xFFFF5050;
                case 6 -> Holo.RED;
                default -> Holo.TEXT;
            };
            Component header = Component.translatable(switch (n.kind()) {
                case 6 -> "solostatus.hud.alert";
                case 3 -> "solostatus.hud.achievement";
                case 4 -> "solostatus.hud.hidden_achievement";
                default -> "solostatus.hud.system";
            });
            boolean hasIcon = !n.icon().isEmpty();
            int iconSize = big ? 32 : 16;
            int iconPad = hasIcon ? iconSize + 14 : 0;
            int textW = Math.max(Math.max(font.width(n.title()) * (big ? 5 : 4) / 4, font.width(n.body())), font.width(header));
            int w = Math.max(textW + 24 + iconPad, big ? 240 : 160);
            w = Math.min(w, sw - 20);
            int h = big ? 50 : 40;
            int x = sw / 2 - w / 2;
            int cxText = x + iconPad + (w - iconPad) / 2; // центр текстовой части
            float slide = p < 0.08f ? (1 - p / 0.08f) * 10 : 0;
            g.pose().pushPose();
            g.pose().translate(0, -slide, 300);
            Holo.panel(g, x, y, w, h, a);
            if (a > 0.1f) {
                int ta = Math.max(5, (int) (a * 255)) << 24;
                // Картинка: иконка в светящейся рамке, с лёгкой пульсацией
                if (hasIcon) {
                    int ix = x + 8;
                    int iy = y + (h - iconSize) / 2;
                    int glow = (int) (40 + 40 * Math.sin(now / 250.0));
                    g.fill(ix - 3, iy - 3, ix + iconSize + 3, iy + iconSize + 3, (glow << 24) | (titleColor & 0xFFFFFF));
                    g.renderOutline(ix - 3, iy - 3, iconSize + 6, iconSize + 6, (titleColor & 0xFFFFFF) | ta);
                    g.pose().pushPose();
                    g.pose().translate(ix, iy, 0);
                    float s = iconSize / 16f;
                    g.pose().scale(s, s, 1);
                    g.renderItem(n.icon(), 0, 0);
                    g.pose().popPose();
                }
                g.drawCenteredString(font, header, cxText, y + 4, (Holo.DIM & 0xFFFFFF) | ta);
                if (big) {
                    g.pose().pushPose();
                    g.pose().translate(cxText, y + 16, 50);
                    g.pose().scale(1.25f, 1.25f, 1);
                    g.drawCenteredString(font, n.title(), 0, 0, (titleColor & 0xFFFFFF) | ta);
                    g.pose().popPose();
                    g.drawCenteredString(font, n.body(), cxText, y + 34, (Holo.TEXT & 0xFFFFFF) | ta);
                } else {
                    g.drawCenteredString(font, n.title(), cxText, y + 15, (titleColor & 0xFFFFFF) | ta);
                    g.drawCenteredString(font, n.body(), cxText, y + 27, (Holo.TEXT & 0xFFFFFF) | ta);
                }
            }
            g.pose().popPose();
            y += h + 6;
        }
    }
}
