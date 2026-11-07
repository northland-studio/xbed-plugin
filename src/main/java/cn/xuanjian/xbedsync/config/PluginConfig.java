package cn.xuanjian.xbedsync.config;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * 插件配置（config.yml）的强类型视图。
 *
 * <p>敏感字段（hmacSecret）只在内存中使用，任何日志/命令输出都必须脱敏。</p>
 */
public final class PluginConfig {

    private final String apiBaseUrl;
    private final String serverKey;
    private final String hmacSecret;
    private final String season;
    private final File queueDir;
    private final Set<String> modeAllowlist;
    private final Set<String> arenaAllowlist;
    private final boolean preferArenaCounters;
    private final boolean includeDespawnableKills;
    private final int retryInitialDelaySeconds;
    private final int retryMaxDelaySeconds;
    private final int httpTimeoutSeconds;
    private final int scanIntervalSeconds;
    private final int snapshotIntervalSeconds;
    private final boolean debug;

    private PluginConfig(Builder builder) {
        this.apiBaseUrl = builder.apiBaseUrl;
        this.serverKey = builder.serverKey;
        this.hmacSecret = builder.hmacSecret;
        this.season = builder.season;
        this.queueDir = builder.queueDir;
        this.modeAllowlist = builder.modeAllowlist;
        this.arenaAllowlist = builder.arenaAllowlist;
        this.preferArenaCounters = builder.preferArenaCounters;
        this.includeDespawnableKills = builder.includeDespawnableKills;
        this.retryInitialDelaySeconds = builder.retryInitialDelaySeconds;
        this.retryMaxDelaySeconds = builder.retryMaxDelaySeconds;
        this.httpTimeoutSeconds = builder.httpTimeoutSeconds;
        this.scanIntervalSeconds = builder.scanIntervalSeconds;
        this.snapshotIntervalSeconds = builder.snapshotIntervalSeconds;
        this.debug = builder.debug;
    }

    /**
     * 从 Bukkit 配置读取。
     *
     * @param fileConfig 配置
     * @param dataFolder 插件数据目录（queueDir 相对路径基于此）
     */
    public static PluginConfig load(FileConfiguration fileConfig, File dataFolder) {
        Builder builder = new Builder();
        builder.apiBaseUrl = normalizeBaseUrl(fileConfig.getString("apiBaseUrl", ""));
        builder.serverKey = trimToEmpty(fileConfig.getString("serverKey", ""));
        builder.hmacSecret = trimToEmpty(fileConfig.getString("hmacSecret", ""));
        builder.season = trimToEmpty(fileConfig.getString("season", "S1"));
        builder.queueDir = resolveDir(fileConfig.getString("queueDir", "queue"), dataFolder);
        builder.modeAllowlist = normalizeSet(fileConfig.getStringList("modeAllowlist"));
        builder.arenaAllowlist = normalizeSet(fileConfig.getStringList("arenaAllowlist"));
        builder.preferArenaCounters = fileConfig.getBoolean("stats.preferArenaCounters", true);
        builder.includeDespawnableKills = fileConfig.getBoolean("stats.includeDespawnableKills", true);
        builder.retryInitialDelaySeconds = clamp(fileConfig.getInt("retry.initialDelaySeconds", 1), 1, 3600);
        builder.retryMaxDelaySeconds = clamp(fileConfig.getInt("retry.maxDelaySeconds", 300), 1, 86400);
        builder.httpTimeoutSeconds = clamp(fileConfig.getInt("retry.httpTimeoutSeconds", 10), 1, 120);
        builder.scanIntervalSeconds = clamp(fileConfig.getInt("retry.scanIntervalSeconds", 1), 1, 60);
        builder.snapshotIntervalSeconds = clamp(fileConfig.getInt("snapshotIntervalSeconds", 10), 0, 3600);
        builder.debug = fileConfig.getBoolean("debug", false);
        if (builder.retryMaxDelaySeconds < builder.retryInitialDelaySeconds) {
            builder.retryMaxDelaySeconds = builder.retryInitialDelaySeconds;
        }
        return new PluginConfig(builder);
    }

    private static String normalizeBaseUrl(String raw) {
        String value = trimToEmpty(raw);
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private static File resolveDir(String raw, File dataFolder) {
        String value = trimToEmpty(raw);
        if (value.isEmpty()) {
            return new File(dataFolder, "queue");
        }
        File file = new File(value);
        if (file.isAbsolute()) {
            return file;
        }
        return new File(dataFolder, value);
    }

    private static Set<String> normalizeSet(List<String> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> result = new LinkedHashSet<String>();
        for (String value : values) {
            String normalized = trimToEmpty(value).toLowerCase(Locale.ROOT);
            if (!normalized.isEmpty()) {
                result.add(normalized);
            }
        }
        return Collections.unmodifiableSet(result);
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * 启动时的配置校验。
     *
     * @return 错误列表；非空时插件应当拒绝启用（避免用错误配置上报脏数据）
     */
    public List<String> validate() {
        List<String> errors = new ArrayList<String>();
        if (apiBaseUrl.isEmpty()) {
            errors.add("apiBaseUrl 未配置");
        } else {
            try {
                URI uri = new URI(apiBaseUrl);
                String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
                String host = uri.getHost() == null ? "" : uri.getHost();
                if (!"https".equals(scheme) && !"http".equals(scheme)) {
                    errors.add("apiBaseUrl 协议必须是 https（或本地调试 http://127.0.0.1）");
                } else if ("http".equals(scheme) && !isLoopbackHost(host)) {
                    errors.add("apiBaseUrl 使用 http 但主机不是 localhost/127.0.0.1；XBed 要求生产环境强制 HTTPS");
                }
                if (host.isEmpty()) {
                    errors.add("apiBaseUrl 缺少主机名");
                }
            } catch (URISyntaxException e) {
                errors.add("apiBaseUrl 不是合法 URL: " + e.getMessage());
            }
        }
        if (serverKey.isEmpty()) {
            errors.add("serverKey 未配置（S1 固定使用 s1）");
        }
        if (hmacSecret.isEmpty()) {
            errors.add("hmacSecret 未配置");
        } else if (hmacSecret.length() < 16) {
            errors.add("hmacSecret 长度不足 16 字符，请使用网站后台生成的密钥");
        }
        return errors;
    }

    /**
     * 是否为本地回环地址（仅本地联调允许 http）。
     */
    public static boolean isLoopbackHost(String host) {
        if (host == null) {
            return false;
        }
        String value = host.toLowerCase(Locale.ROOT);
        return "localhost".equals(value) || "127.0.0.1".equals(value) || "::1".equals(value)
                || "0:0:0:0:0:0:0:1".equals(value);
    }

    public boolean isInsecureHttp() {
        return apiBaseUrl.toLowerCase(Locale.ROOT).startsWith("http://");
    }

    public String getApiBaseUrl() {
        return apiBaseUrl;
    }

    public String getServerKey() {
        return serverKey;
    }

    public String getHmacSecret() {
        return hmacSecret;
    }

    public String getSeason() {
        return season;
    }

    public File getQueueDir() {
        return queueDir;
    }

    public boolean isModeAllowed(String mode) {
        if (modeAllowlist.isEmpty()) {
            return true;
        }
        return mode != null && modeAllowlist.contains(mode.trim().toLowerCase(Locale.ROOT));
    }

    public boolean isArenaAllowed(String arenaName) {
        if (arenaAllowlist.isEmpty()) {
            return true;
        }
        return arenaName != null && arenaAllowlist.contains(arenaName.trim().toLowerCase(Locale.ROOT));
    }

    public boolean isPreferArenaCounters() {
        return preferArenaCounters;
    }

    public boolean isIncludeDespawnableKills() {
        return includeDespawnableKills;
    }

    public int getRetryInitialDelaySeconds() {
        return retryInitialDelaySeconds;
    }

    public int getRetryMaxDelaySeconds() {
        return retryMaxDelaySeconds;
    }

    public int getHttpTimeoutSeconds() {
        return httpTimeoutSeconds;
    }

    public int getScanIntervalSeconds() {
        return scanIntervalSeconds;
    }

    public int getSnapshotIntervalSeconds() {
        return snapshotIntervalSeconds;
    }

    public boolean isDebug() {
        return debug;
    }

    /**
     * 只用于日志/状态展示的脱敏密钥，永不返回明文。
     */
    public String getMaskedSecret() {
        if (hmacSecret.isEmpty()) {
            return "(未配置)";
        }
        if (hmacSecret.length() <= 4) {
            return "****";
        }
        return hmacSecret.substring(0, 2) + "****" + hmacSecret.substring(hmacSecret.length() - 2);
    }

    private static final class Builder {
        private String apiBaseUrl;
        private String serverKey;
        private String hmacSecret;
        private String season;
        private File queueDir;
        private Set<String> modeAllowlist;
        private Set<String> arenaAllowlist;
        private boolean preferArenaCounters;
        private boolean includeDespawnableKills;
        private int retryInitialDelaySeconds;
        private int retryMaxDelaySeconds;
        private int httpTimeoutSeconds;
        private int scanIntervalSeconds;
        private int snapshotIntervalSeconds;
        private boolean debug;
    }
}
