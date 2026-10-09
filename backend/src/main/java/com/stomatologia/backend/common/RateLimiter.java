package com.stomatologia.backend.common;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ограничение частоты запросов в памяти: не больше {@code limit} запросов за окно на ключ (например, IP-адрес).
 * Счётчики живут в одном экземпляре сервера — для одного сервера клиники этого достаточно.
 */
public class RateLimiter {

    private record Window(long startedAt, int count) {
    }

    private static final int CLEANUP_THRESHOLD = 10_000;

    private final int limit;
    private final long windowMillis;
    private final Clock clock;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public RateLimiter(int limit, Duration window) {
        this(limit, window, Clock.systemUTC());
    }

    RateLimiter(int limit, Duration window, Clock clock) {
        this.limit = limit;
        this.windowMillis = window.toMillis();
        this.clock = clock;
    }

    /** true — запрос разрешён и учтён; false — лимит на текущее окно исчерпан. */
    public boolean tryAcquire(String key) {
        long now = clock.millis();
        if (windows.size() > CLEANUP_THRESHOLD) {
            windows.values().removeIf(w -> now - w.startedAt() >= windowMillis);
        }
        Window w = windows.compute(key, (k, old) -> old == null || now - old.startedAt() >= windowMillis
                ? new Window(now, 1)
                : new Window(old.startedAt(), old.count() + 1));
        return w.count() <= limit;
    }

    /** Через сколько секунд у ключа начнётся новое окно. */
    public long retryAfterSeconds(String key) {
        Window w = windows.get(key);
        if (w == null) {
            return 0;
        }
        return Math.max(1, (w.startedAt() + windowMillis - clock.millis() + 999) / 1000);
    }
}
