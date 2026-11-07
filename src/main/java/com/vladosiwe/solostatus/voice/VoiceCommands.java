package com.vladosiwe.solostatus.voice;

import java.util.Locale;

/**
 * Распознавание командных фраз системы. Используется текстовым вводом в чат и любым будущим
 * адаптером распознавания речи (Speech-to-Text).
 *
 * Simple Voice Chat передаёт только звук и сам речь не распознаёт. Безопасная схема голосовой команды
 * описана в docs/INTEGRATIONS.md: локальный распознаватель (например, Vosk) на клиенте, без отправки звука
 * внешним сервисам; результат распознавания передаётся сюда в виде текста.
 */
public final class VoiceCommands {
    public enum Command { OPEN_STATUS, NONE }

    private VoiceCommands() {}

    public static Command match(String phrase, String configuredPhrase) {
        String p = normalize(phrase);
        if (p.isEmpty()) return Command.NONE;
        if (p.equals(normalize(configuredPhrase))) return Command.OPEN_STATUS;
        // Распространённые варианты
        if (p.equals("система покажи статус") || p.equals("system show status") || p.equals("status window")
                || p.equals("открыть статус") || p.equals("окно статуса")) {
            return Command.OPEN_STATUS;
        }
        return Command.NONE;
    }

    private static String normalize(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N} ]", " ").replaceAll("\\s+", " ").trim();
    }
}
