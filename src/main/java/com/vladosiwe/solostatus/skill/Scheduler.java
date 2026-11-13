package com.vladosiwe.solostatus.skill;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Простой серверный планировщик для многоударных навыков (Шквал, Дождь стрел, Метеор).
 * Задачи выполняются в серверном тике, поэтому никаких потоков и гонок.
 */
public final class Scheduler {
    private static final List<Task> TASKS = new ArrayList<>();
    private static final List<Task> PENDING = new ArrayList<>();

    private Scheduler() {}

    public static void later(int ticks, Runnable action) {
        PENDING.add(new Task(Math.max(1, ticks), action));
    }

    /** Повторить action count раз с интервалом interval тиков (первый вызов — через interval). */
    public static void repeat(int interval, int count, java.util.function.IntConsumer action) {
        for (int i = 1; i <= count; i++) {
            final int index = i - 1;
            later(interval * i, () -> action.accept(index));
        }
    }

    public static void tick() {
        if (!PENDING.isEmpty()) {
            TASKS.addAll(PENDING);
            PENDING.clear();
        }
        Iterator<Task> it = TASKS.iterator();
        while (it.hasNext()) {
            Task t = it.next();
            if (--t.remaining <= 0) {
                it.remove();
                try {
                    t.action.run();
                } catch (Exception e) {
                    com.vladosiwe.solostatus.SoloStatus.LOGGER.error("Ошибка в отложенном эффекте навыка", e);
                }
            }
        }
    }

    public static void clear() {
        TASKS.clear();
        PENDING.clear();
    }

    private static final class Task {
        int remaining;
        final Runnable action;

        Task(int remaining, Runnable action) {
            this.remaining = remaining;
            this.action = action;
        }
    }
}
