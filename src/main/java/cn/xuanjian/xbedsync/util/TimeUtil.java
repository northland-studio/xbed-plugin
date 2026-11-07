package cn.xuanjian.xbedsync.util;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * 时间工具：统一输出 UTC ISO8601（毫秒精度），与 XBed API 契约一致。
 */
public final class TimeUtil {

    private static final DateTimeFormatter ISO_MS = DateTimeFormatter
            .ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT)
            .withZone(ZoneOffset.UTC);

    private TimeUtil() {
    }

    /**
     * @return 例：2026-11-08T12:00:00.000Z；instant 为 null 时返回 null
     */
    public static String iso(Instant instant) {
        return instant == null ? null : ISO_MS.format(instant);
    }

    /**
     * 人类可读的时间差，用于 /xbedsync status。
     *
     * @param epochMillis 过去某个时间点；0 表示从未发生
     */
    public static String ago(long epochMillis) {
        if (epochMillis <= 0L) {
            return "从未";
        }
        long diffSeconds = Math.max(0L, (System.currentTimeMillis() - epochMillis) / 1000L);
        if (diffSeconds < 60L) {
            return diffSeconds + " 秒前";
        }
        long minutes = diffSeconds / 60L;
        if (minutes < 60L) {
            return minutes + " 分钟前";
        }
        long hours = minutes / 60L;
        if (hours < 24L) {
            return hours + " 小时前";
        }
        return (hours / 24L) + " 天前";
    }
}
