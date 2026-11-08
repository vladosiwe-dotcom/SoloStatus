package com.vladosiwe.solostatus.client.voice;

import com.mojang.brigadier.CommandDispatcher;
import com.vladosiwe.solostatus.SoloStatus;
import com.vladosiwe.solostatus.client.ClientState;
import com.vladosiwe.solostatus.client.Keys;
import com.vladosiwe.solostatus.client.screen.InspectScreen;
import com.vladosiwe.solostatus.client.ui.SystemScreen;
import com.vladosiwe.solostatus.config.SoloConfig;
import com.vladosiwe.solostatus.network.Payloads;
import com.vladosiwe.solostatus.voice.VoiceCommands;
import com.vladosiwe.solostatus.voice.VoiceMode;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.network.PacketDistributor;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/**
 * Голосовые команды на клиенте: запуск Vosk, режимы (всегда / по клавише / выкл.), выполнение команд,
 * индикатор на экране и команда /systemvoice. Распознавание полностью локальное.
 */
public final class VoiceClient {
    private static volatile VoskEngine engine;
    private static volatile VoskEngine.State state = VoskEngine.State.STOPPED;
    private static volatile boolean downloading;
    private static String lastHeard = "";
    private static long lastHeardAt;
    private static boolean readyAnnounced;
    private static boolean modelHintShown;

    private VoiceClient() {}

    // ======================== Настройки ========================

    public static VoiceMode mode() {
        try {
            return SoloConfig.VOICE_MODE.get();
        } catch (Exception e) {
            return VoiceMode.OFF;
        }
    }

    /** Язык распознавания: из конфига или по языку игры. */
    public static String language() {
        String lang;
        try {
            lang = SoloConfig.VOICE_LANGUAGE.get();
        } catch (Exception e) {
            lang = "auto";
        }
        if ("ru".equals(lang) || "en".equals(lang)) return lang;
        String game = Minecraft.getInstance().getLanguageManager().getSelected();
        return game != null && game.startsWith("ru") ? "ru" : "en";
    }

    private static String customPhrase() {
        try {
            return SoloConfig.CHAT_PHRASE.get();
        } catch (Exception e) {
            return "";
        }
    }

    public static Path baseDir() {
        return FMLPaths.CONFIGDIR.get().resolve(SoloStatus.MODID).resolve("vosk");
    }

    /** Папка модели: путь из конфига, стандартная модель языка или любая найденная модель. */
    public static Path modelDir() {
        try {
            String custom = SoloConfig.VOICE_MODEL_PATH.get();
            if (custom != null && !custom.isBlank()) {
                Path p = Path.of(custom);
                return ModelDownloader.isModel(p) ? p : null;
            }
        } catch (Exception ignored) {
        }
        Path standard = baseDir().resolve(ModelDownloader.forLanguage(language()).name());
        if (ModelDownloader.isModel(standard)) return standard;
        return ModelDownloader.findAnyModel(baseDir());
    }

    /**
     * Путь для Vosk. На Windows библиотека открывает файлы в кодировке ANSI, поэтому путь с кириллицей
     * (например, имя пользователя «Владислав») не откроется. Если модель лежит внутри папки игры,
     * передаём относительный путь — он состоит только из латиницы.
     */
    static String nativePath(Path dir) {
        Path abs = dir.toAbsolutePath().normalize();
        Path cwd = Path.of("").toAbsolutePath().normalize();
        if (abs.startsWith(cwd)) {
            String rel = cwd.relativize(abs).toString();
            if (StandardCharsets.US_ASCII.newEncoder().canEncode(rel)) return rel.isEmpty() ? "." : rel;
        }
        String s = abs.toString();
        if (!StandardCharsets.US_ASCII.newEncoder().canEncode(s)) {
            SoloStatus.LOGGER.warn("Путь к модели Vosk содержит не-латинские символы и может не открыться: {}", s);
        }
        return s;
    }

    // ======================== Жизненный цикл ========================

    public static synchronized void start() {
        stop();
        if (mode() == VoiceMode.OFF) return;
        Path model = modelDir();
        if (model == null) {
            if (!modelHintShown) {
                modelHintShown = true;
                Minecraft.getInstance().execute(VoiceClient::showModelHint);
            }
            return;
        }
        String grammar = VoiceCommands.grammarJson(language(), customPhrase());
        VoskEngine[] self = new VoskEngine[1];
        self[0] = new VoskEngine(nativePath(model), grammar, new VoskEngine.Listener() {
            @Override
            public void onText(String text) {
                Minecraft.getInstance().execute(() -> {
                    if (engine == self[0]) handle(text);
                });
            }

            @Override
            public void onState(VoskEngine.State s, String detail) {
                if (engine != self[0]) return; // сообщение от уже остановленного движка
                state = s;
                Minecraft.getInstance().execute(() -> onStateChanged(s, detail));
            }
        });
        engine = self[0];
        engine.setGate(mode() == VoiceMode.ALWAYS);
        engine.start();
        SoloStatus.LOGGER.info("Голосовые команды: модель {}, язык {}, режим {}", model, language(), mode());
    }

    public static synchronized void stop() {
        if (engine != null) engine.stop();
        engine = null;
        state = VoskEngine.State.STOPPED;
    }

    /** Каждый клиентский тик: управление «затвором» по клавише в режиме push-to-talk. */
    public static void tick(Minecraft mc) {
        VoskEngine e = engine;
        if (e == null) return;
        e.setGate(mode() == VoiceMode.ALWAYS || (mode() == VoiceMode.PUSH_TO_TALK && Keys.VOICE.isDown()));
    }

    private static void onStateChanged(VoskEngine.State s, String detail) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (s == VoskEngine.State.ERROR) {
            chat(Component.translatable("solostatus.voice.error." + detail).withStyle(ChatFormatting.RED));
        } else if (s == VoskEngine.State.LISTENING && !readyAnnounced) {
            readyAnnounced = true;
            String phrase = "ru".equals(language()) ? "Система, покажи статус" : "System, show status";
            chat(mode() == VoiceMode.PUSH_TO_TALK
                    ? Component.translatable("solostatus.voice.ready.ptt", Keys.VOICE.getTranslatedKeyMessage(), phrase)
                    : Component.translatable("solostatus.voice.ready", phrase));
        }
    }

    // ======================== Команды голосом ========================

    private static void handle(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        VoiceCommands.Command cmd = VoiceCommands.matchVoice(text, language(), customPhrase());
        if (cmd == VoiceCommands.Command.NONE) return;
        lastHeard = text;
        lastHeardAt = System.currentTimeMillis();
        switch (cmd) {
            case OPEN_STATUS -> {
                if (mc.screen == null || mc.screen instanceof InspectScreen) mc.setScreen(new SystemScreen(SystemScreen.Win.STATUS));
            }
            case CLOSE_STATUS -> {
                if (mc.screen instanceof SystemScreen || mc.screen instanceof InspectScreen) mc.screen.onClose();
            }
            case OPEN_SKILLS -> {
                if (mc.screen == null || mc.screen instanceof SystemScreen || mc.screen instanceof InspectScreen) {
                    mc.setScreen(new SystemScreen(SystemScreen.Win.SKILLS));
                }
            }
            case INSPECT -> {
                if (mc.screen == null && mc.crosshairPickEntity instanceof Player target && target != mc.player) {
                    PacketDistributor.sendToServer(new Payloads.InspectRequest(target.getId()));
                }
            }
            case SKILL_1, SKILL_2, SKILL_3, SKILL_4 -> {
                int slot = cmd.ordinal() - VoiceCommands.Command.SKILL_1.ordinal();
                if (mc.screen == null && ClientState.data.slots[slot] != null) {
                    PacketDistributor.sendToServer(new Payloads.CastSkill(slot));
                }
            }
            default -> {
            }
        }
    }

    // ======================== Индикатор ========================

    public static VoskEngine.State state() {
        return state;
    }

    public static boolean capturing() {
        return state == VoskEngine.State.LISTENING
                && (mode() == VoiceMode.ALWAYS || (mode() == VoiceMode.PUSH_TO_TALK && Keys.VOICE.isDown()));
    }

    public static String lastHeard() {
        return System.currentTimeMillis() - lastHeardAt < 2500 ? lastHeard : "";
    }

    // ======================== Модель и /systemvoice ========================

    private static void showModelHint() {
        ModelDownloader.ModelInfo info = ModelDownloader.forLanguage(language());
        MutableComponent link = Component.translatable("solostatus.voice.no_model", info.name(), info.sizeMb())
                .withStyle(s -> s.withColor(ChatFormatting.AQUA).withUnderlined(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/systemvoice download"))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("solostatus.voice.no_model.hover"))));
        chat(link);
    }

    public static void download() {
        if (downloading) {
            chat(Component.translatable("solostatus.voice.download.busy"));
            return;
        }
        downloading = true;
        ModelDownloader.ModelInfo info = ModelDownloader.forLanguage(language());
        chat(Component.translatable("solostatus.voice.download.start", info.name(), info.sizeMb()));
        Minecraft mc = Minecraft.getInstance();
        CompletableFuture.runAsync(() -> {
            try {
                ModelDownloader.download(info, baseDir(), pct -> mc.execute(() -> {
                    if (mc.player != null && pct >= 0) {
                        mc.player.displayClientMessage(Component.translatable("solostatus.voice.download.progress", pct), true);
                    }
                }));
                mc.execute(() -> {
                    chat(Component.translatable("solostatus.voice.download.done").withStyle(ChatFormatting.GREEN));
                    readyAnnounced = false;
                    start();
                });
            } catch (Exception e) {
                SoloStatus.LOGGER.error("Не удалось скачать модель Vosk", e);
                mc.execute(() -> chat(Component.translatable("solostatus.voice.download.fail", String.valueOf(e.getMessage()),
                        info.url(), baseDir().toString()).withStyle(ChatFormatting.RED)));
            } finally {
                downloading = false;
            }
        });
    }

    private static void setMode(VoiceMode m) {
        try {
            SoloConfig.VOICE_MODE.set(m);
            SoloConfig.VOICE_MODE.save();
        } catch (Exception e) {
            SoloStatus.LOGGER.warn("Не удалось сохранить режим голоса", e);
        }
        chat(Component.translatable("solostatus.voice.mode.set", Component.translatable("solostatus.voice.mode." + m.name().toLowerCase())));
        readyAnnounced = false;
        start();
    }

    /** Клиентская команда /systemvoice: status | download | restart | off | ptt | always. */
    public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("systemvoice")
                .executes(c -> status())
                .then(Commands.literal("status").executes(c -> status()))
                .then(Commands.literal("download").executes(c -> { download(); return 1; }))
                .then(Commands.literal("restart").executes(c -> { readyAnnounced = false; start(); return 1; }))
                .then(Commands.literal("off").executes(c -> { setMode(VoiceMode.OFF); return 1; }))
                .then(Commands.literal("ptt").executes(c -> { setMode(VoiceMode.PUSH_TO_TALK); return 1; }))
                .then(Commands.literal("always").executes(c -> { setMode(VoiceMode.ALWAYS); return 1; })));
    }

    private static int status() {
        Path model = modelDir();
        chat(Component.translatable("solostatus.voice.status",
                Component.translatable("solostatus.voice.mode." + mode().name().toLowerCase()),
                language(),
                Component.translatable("solostatus.voice.state." + state.name().toLowerCase()),
                model == null ? Component.translatable("solostatus.voice.model.none") : Component.literal(model.getFileName().toString())));
        return 1;
    }

    private static void chat(Component c) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.displayClientMessage(c, false);
    }

    /** Сброс подсказок при выходе из мира. */
    public static void onLogout() {
        stop();
        readyAnnounced = false;
        modelHintShown = false;
    }
}
