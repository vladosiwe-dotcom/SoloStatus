package com.vladosiwe.solostatus.voice;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Командные фразы системы. Общие для текстового ввода в чат и распознавания речи (Vosk).
 * Из этих же фраз строится грамматика распознавателя — так Vosk ищет только команды,
 * что повышает точность и снижает ложные срабатывания на обычную речь.
 */
public final class VoiceCommands {
    public enum Command { TOGGLE_STATUS, OPEN_STATUS, OPEN_SKILLS, CLOSE_STATUS, INSPECT, SKILL_1, SKILL_2, SKILL_3, SKILL_4, NONE }

    /** Минимальная уверенность распознавания для команды из одного слова и для фраз. */
    public static final double MIN_CONF_SINGLE = 0.80;
    public static final double MIN_CONF_PHRASE = 0.60;

    private static final Map<String, Command> RU = new LinkedHashMap<>();
    private static final Map<String, Command> EN = new LinkedHashMap<>();

    static {
        // Одно слово: открыть/закрыть окно. Срабатывает, только если слово сказано отдельно.
        RU.put("система", Command.TOGGLE_STATUS);
        RU.put("статус", Command.TOGGLE_STATUS);
        RU.put("система покажи статус", Command.OPEN_STATUS);
        RU.put("система открой статус", Command.OPEN_STATUS);
        RU.put("система статус", Command.OPEN_STATUS);
        RU.put("покажи статус", Command.OPEN_STATUS);
        RU.put("открыть статус", Command.OPEN_STATUS);
        RU.put("окно статуса", Command.OPEN_STATUS);
        RU.put("система навыки", Command.OPEN_SKILLS);
        RU.put("система покажи навыки", Command.OPEN_SKILLS);
        RU.put("система закрой", Command.CLOSE_STATUS);
        RU.put("система закрой статус", Command.CLOSE_STATUS);
        RU.put("закрой статус", Command.CLOSE_STATUS);
        RU.put("система осмотр", Command.INSPECT);
        RU.put("система оценка", Command.INSPECT);
        RU.put("навык один", Command.SKILL_1);
        RU.put("навык два", Command.SKILL_2);
        RU.put("навык три", Command.SKILL_3);
        RU.put("навык четыре", Command.SKILL_4);

        EN.put("system", Command.TOGGLE_STATUS);
        EN.put("status", Command.TOGGLE_STATUS);
        EN.put("system show status", Command.OPEN_STATUS);
        EN.put("system open status", Command.OPEN_STATUS);
        EN.put("system status", Command.OPEN_STATUS);
        EN.put("show status", Command.OPEN_STATUS);
        EN.put("status window", Command.OPEN_STATUS);
        EN.put("system skills", Command.OPEN_SKILLS);
        EN.put("system show skills", Command.OPEN_SKILLS);
        EN.put("system close", Command.CLOSE_STATUS);
        EN.put("close status", Command.CLOSE_STATUS);
        EN.put("system inspect", Command.INSPECT);
        EN.put("system appraisal", Command.INSPECT);
        EN.put("skill one", Command.SKILL_1);
        EN.put("skill two", Command.SKILL_2);
        EN.put("skill three", Command.SKILL_3);
        EN.put("skill four", Command.SKILL_4);
    }

    private VoiceCommands() {}

    /** Фразы для языка распознавания ("ru" или "en") плюс пользовательская фраза открытия статуса. */
    public static Map<String, Command> phrases(String language, String customOpenPhrase) {
        Map<String, Command> map = new LinkedHashMap<>("en".equals(language) ? EN : RU);
        String custom = normalize(customOpenPhrase);
        if (!custom.isEmpty()) map.putIfAbsent(custom, Command.OPEN_STATUS);
        return map;
    }

    /**
     * Распознанный голосом текст → команда.
     *
     * @param raw        результат Vosk как есть, с маркерами [unk] (речь вне списка команд)
     * @param confidence средняя уверенность распознанных слов (0..1)
     *
     * Защита от ложных срабатываний в обычной речи и голосовом чате:
     *  - команда принимается только полной фразой;
     *  - команда из ОДНОГО слова («Система», «Статус») — только если сказана отдельно: если вокруг
     *    прозвучали другие слова ([unk]), это часть разговора, а не команда;
     *  - нужна уверенность распознавания: ≥ 80% для одного слова, ≥ 60% для фраз.
     */
    public static Command matchVoice(String raw, String language, String customOpenPhrase, double confidence) {
        if (raw == null) return Command.NONE;
        boolean hasOtherSpeech = raw.contains("[unk]");
        String cleaned = normalize(raw.replace("[unk]", " "));
        Command cmd = phrases(language, customOpenPhrase).getOrDefault(cleaned, Command.NONE);
        if (cmd == Command.NONE) return Command.NONE;
        boolean singleWord = !cleaned.contains(" ");
        if (singleWord) {
            if (hasOtherSpeech || confidence < MIN_CONF_SINGLE) return Command.NONE;
        } else if (confidence < MIN_CONF_PHRASE) {
            return Command.NONE;
        }
        return cmd;
    }

    /** Упрощённый вариант для тестов и текстового ввода: без маркеров и с полной уверенностью. */
    public static Command matchVoice(String text, String language, String customOpenPhrase) {
        return matchVoice(text, language, customOpenPhrase, 1.0);
    }

    /** Текст в чате: перехватывается только открытие статуса, чтобы не мешать обычному общению. */
    public static Command match(String phrase, String customOpenPhrase) {
        String p = normalize(phrase);
        if (p.isEmpty()) return Command.NONE;
        if (p.equals(normalize(customOpenPhrase))) return Command.OPEN_STATUS;
        if (RU.get(p) == Command.OPEN_STATUS && !p.equals("система статус") || EN.get(p) == Command.OPEN_STATUS) {
            return Command.OPEN_STATUS;
        }
        return Command.NONE;
    }

    /**
     * Грамматика Vosk: JSON-массив фраз и "[unk]" для всего остального.
     * Поддерживается малыми моделями (small); большие модели грамматику игнорируют.
     */
    public static String grammarJson(String language, String customOpenPhrase) {
        StringBuilder sb = new StringBuilder("[");
        for (String phrase : phrases(language, customOpenPhrase).keySet()) {
            sb.append('"').append(phrase.replace("\"", "")).append("\",");
        }
        sb.append("\"[unk]\"]");
        return sb.toString();
    }

    public static String normalize(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replace('ё', 'е')
                .replaceAll("[^\\p{L}\\p{N} ]", " ").replaceAll("\\s+", " ").trim();
    }
}
