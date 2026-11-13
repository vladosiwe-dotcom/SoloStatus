package com.vladosiwe.solostatus.client.voice;

import com.sun.jna.Function;
import com.sun.jna.Library;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Platform;
import com.sun.jna.Pointer;
import org.vosk.LibVosk;
import org.vosk.LogLevel;
import org.vosk.Model;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Локальное распознавание речи на Vosk. Не зависит от классов Minecraft.
 *
 * Звук берётся с микрофона по умолчанию (16 кГц, 16 бит, моно), сразу передаётся в Vosk и нигде
 * не сохраняется и не отправляется. Работает в отдельном фоновом потоке-демоне.
 * Пока «затвор» закрыт (push-to-talk без нажатой клавиши), звук читается и сразу отбрасывается.
 *
 * Почему распознаватель вызывается напрямую через JNA, а не через org.vosk.Recognizer:
 * стандартная обёртка передаёт строки в системной кодировке JNA. На русской Windows это cp1251,
 * а Vosk ожидает UTF-8 — русская грамматика и результаты были бы испорчены. Здесь грамматика
 * передаётся как UTF-8 байты, а результаты читаются как UTF-8. Модель загружается штатным
 * org.vosk.Model (путь передаётся относительным ASCII-путём, см. VoiceClient).
 */
public final class VoskEngine {
    public enum State { LOADING, LISTENING, ERROR, STOPPED }

    public interface Listener {
        /**
         * Распознанная фраза как есть (с маркерами [unk]) и средняя уверенность слов 0..1.
         * Вызывается из фонового потока.
         */
        void onText(String text, double confidence);

        /** Смена состояния; detail — ключ причины ошибки (no_mic, mic_busy, model, native) или null. */
        void onState(State state, String detail);
    }

    private static final float SAMPLE_RATE = 16000f;
    private static final Pattern TEXT = Pattern.compile("\"text\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern WORD = Pattern.compile("\"conf\"\\s*:\\s*([0-9.]+)[^}]*?\"word\"\\s*:\\s*\"([^\"]*)\"");

    private final String modelPath;
    private final String grammar;
    private final Listener listener;
    private volatile boolean running;
    private volatile boolean gate = true;
    private Thread thread;

    /**
     * @param modelPath путь к папке модели (желательно относительный и только из ASCII-символов)
     * @param grammar   JSON-массив фраз или null для свободного распознавания
     */
    public VoskEngine(String modelPath, String grammar, Listener listener) {
        this.modelPath = modelPath;
        this.grammar = grammar;
        this.listener = listener;
    }

    public VoskEngine(Path modelDir, String grammar, Listener listener) {
        this(modelDir.toString(), grammar, listener);
    }

    public synchronized void start() {
        if (running) return;
        running = true;
        thread = new Thread(this::run, "SoloStatus-Voice");
        thread.setDaemon(true);
        thread.start();
    }

    /** Останавливает распознавание и ждёт (до 0.5 с), пока поток освободит микрофон. */
    public void stop() {
        Thread t;
        synchronized (this) {
            running = false;
            t = thread;
            thread = null;
        }
        if (t != null && t != Thread.currentThread()) {
            try {
                t.join(500);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public boolean isRunning() {
        return running;
    }

    /** Открыт ли «затвор»: передавать ли звук распознавателю. */
    public void setGate(boolean open) {
        this.gate = open;
    }

    private void run() {
        listener.onState(State.LOADING, null);
        NativeVosk vosk;
        try {
            LibVosk.setLogLevel(LogLevel.WARNINGS); // загружает нативную библиотеку
            vosk = new NativeVosk();
        } catch (Throwable t) {
            fail("native"); // нет нативной библиотеки для этой платформы/архитектуры
            return;
        }
        AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
        if (!AudioSystem.isLineSupported(info)) {
            fail("no_mic");
            return;
        }
        Pointer recognizer = null;
        try (Model model = new Model(modelPath)) {
            recognizer = vosk.newRecognizer(model.getPointer(), SAMPLE_RATE, grammar);
            if (recognizer == null) throw new IOException("recognizer");
            vosk.setWords(recognizer, true); // уверенность по словам — для защиты от ложных срабатываний
            TargetDataLine line = (TargetDataLine) AudioSystem.getLine(info);
            line.open(format, 8192);
            line.start();
            listener.onState(State.LISTENING, null);
            byte[] buffer = new byte[3200]; // 100 мс звука
            boolean fed = false;
            try {
                while (running) {
                    int n = line.read(buffer, 0, buffer.length);
                    if (n <= 0) continue;
                    if (gate) {
                        if (vosk.accept(recognizer, buffer, n)) emit(vosk.result(recognizer));
                        fed = true;
                    } else if (fed) {
                        // Клавиша отпущена — дораспознаём сказанное и начинаем заново
                        emit(vosk.finalResult(recognizer));
                        vosk.reset(recognizer);
                        fed = false;
                    }
                }
            } finally {
                line.stop();
                line.close();
            }
            listener.onState(State.STOPPED, null);
        } catch (LineUnavailableException e) {
            fail("mic_busy");
        } catch (IOException e) {
            fail("model");
        } catch (Throwable t) {
            fail("native");
        } finally {
            if (recognizer != null) vosk.free(recognizer);
            running = false;
        }
    }

    private void fail(String reason) {
        running = false;
        listener.onState(State.ERROR, reason);
    }

    private void emit(String json) {
        String text = extractText(json);
        if (text.isEmpty() || text.replace("[unk]", "").isBlank()) return;
        listener.onText(text, confidence(json));
    }

    /** Поле "text" из JSON-ответа Vosk как есть (маркеры [unk] сохраняются для проверки команды). */
    static String extractText(String json) {
        if (json == null) return "";
        Matcher m = TEXT.matcher(json);
        return m.find() ? m.group(1).trim() : "";
    }

    /** Средняя уверенность распознанных слов (без [unk]); 1.0, если Vosk не вернул уверенность. */
    static double confidence(String json) {
        if (json == null) return 1.0;
        Matcher m = WORD.matcher(json);
        double sum = 0;
        int n = 0;
        while (m.find()) {
            if ("[unk]".equals(m.group(2))) continue;
            try {
                sum += Double.parseDouble(m.group(1));
                n++;
            } catch (NumberFormatException ignored) {
            }
        }
        return n == 0 ? 1.0 : sum / n;
    }

    /** Прямые вызовы функций libvosk с явной кодировкой UTF-8. */
    static final class NativeVosk {
        private final Function newRec, newRecGrm, accept, result, finalResult, reset, free, setWords;

        NativeVosk() {
            // Та же библиотека, что уже загрузил org.vosk.LibVosk (JNA кэширует экземпляр по имени).
            NativeLibrary lib = NativeLibrary.getInstance(Platform.isWindows() ? "libvosk" : "vosk",
                    Map.of(Library.OPTION_CLASSLOADER, LibVosk.class.getClassLoader()));
            newRec = lib.getFunction("vosk_recognizer_new");
            newRecGrm = lib.getFunction("vosk_recognizer_new_grm");
            accept = lib.getFunction("vosk_recognizer_accept_waveform");
            result = lib.getFunction("vosk_recognizer_result");
            finalResult = lib.getFunction("vosk_recognizer_final_result");
            reset = lib.getFunction("vosk_recognizer_reset");
            free = lib.getFunction("vosk_recognizer_free");
            setWords = lib.getFunction("vosk_recognizer_set_words");
        }

        Pointer newRecognizer(Pointer model, float rate, String grammar) {
            if (grammar == null) return newRec.invokePointer(new Object[]{model, rate});
            return newRecGrm.invokePointer(new Object[]{model, rate, utf8z(grammar)});
        }

        boolean accept(Pointer rec, byte[] data, int length) {
            return accept.invokeInt(new Object[]{rec, data, length}) != 0;
        }

        String result(Pointer rec) {
            Pointer p = result.invokePointer(new Object[]{rec});
            return p == null ? "" : p.getString(0, StandardCharsets.UTF_8.name());
        }

        String finalResult(Pointer rec) {
            Pointer p = finalResult.invokePointer(new Object[]{rec});
            return p == null ? "" : p.getString(0, StandardCharsets.UTF_8.name());
        }

        void setWords(Pointer rec, boolean on) {
            setWords.invokeVoid(new Object[]{rec, on ? 1 : 0});
        }

        void reset(Pointer rec) {
            reset.invokeVoid(new Object[]{rec});
        }

        void free(Pointer rec) {
            free.invokeVoid(new Object[]{rec});
        }

        /** Строка как нуль-терминированный массив UTF-8. */
        static byte[] utf8z(String s) {
            byte[] b = s.getBytes(StandardCharsets.UTF_8);
            byte[] out = new byte[b.length + 1];
            System.arraycopy(b, 0, out, 0, b.length);
            return out;
        }
    }
}
