package cn.xuanjian.xbedsync.report;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import cn.xuanjian.xbedsync.XBedSync;
import cn.xuanjian.xbedsync.config.PluginConfig;

/**
 * 异步上报器。
 *
 * <p>工作方式：</p>
 * <ol>
 *   <li>后台任务每 {@code retry.scanIntervalSeconds} 秒扫描一次本地队列；</li>
 *   <li>每次只发送一场比赛，避免弱网下同时堆积连接；</li>
 *   <li>失败后按指数退避重试：1s → 2s → 4s ... 上限 {@code retry.maxDelaySeconds}（默认 300s）；</li>
 *   <li>收到 2xx 或响应体 {@code duplicate:true} 才删除队列文件；</li>
 *   <li>所有网络 IO 都在异步线程执行，不阻塞服务器主线程。</li>
 * </ol>
 */
public final class ReportSender {

    private static final Pattern DUPLICATE_PATTERN = Pattern.compile("\"duplicate\"\\s*:\\s*true");
    private static final int MAX_RESPONSE_BYTES = 16 * 1024;
    private static final int MAX_RESPONSE_CHARS = 2048;

    private final XBedSync plugin;
    private final PluginConfig config;
    private final QueueManager queue;
    private final Map<String, RetryState> retryStates = new ConcurrentHashMap<String, RetryState>();

    private volatile BukkitTask scanTask;
    private volatile boolean running;

    private volatile long lastSuccessAt;
    private volatile long lastFailureAt;
    private volatile int lastHttpCode = -1;
    private volatile String lastError = "";
    private volatile String lastSuccessMatchId = "";
    private volatile String lastFailureMatchId = "";

    public ReportSender(XBedSync plugin, PluginConfig config, QueueManager queue) {
        this.plugin = plugin;
        this.config = config;
        this.queue = queue;
    }

    public void start() {
        running = true;
        long period = Math.max(1, config.getScanIntervalSeconds()) * 20L;
        scanTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                scanOnce();
            }
        }, 20L, period);
    }

    public void stop() {
        running = false;
        BukkitTask task = scanTask;
        if (task != null) {
            task.cancel();
        }
    }

    /**
     * 立即重发：清空所有退避计时（下一次扫描即尝试）。
     *
     * @return 当前待上报场次数量
     */
    public int retryNow() {
        for (RetryState state : retryStates.values()) {
            state.nextAttemptAt = 0L;
        }
        return queue.pendingCount();
    }

    private void scanOnce() {
        if (!running) {
            return;
        }
        try {
            List<String> pending = queue.pendingMatchIds();
            pruneRetryStates(pending);
            long now = System.currentTimeMillis();
            for (String matchId : pending) {
                RetryState state = retryStates.get(matchId);
                if (state == null) {
                    state = new RetryState();
                    retryStates.put(matchId, state);
                }
                if (state.nextAttemptAt > now) {
                    continue;
                }
                attempt(matchId, state);
                // 每秒只发一场，后续场次下一轮继续
                return;
            }
        } catch (RuntimeException e) {
            plugin.getLogger().warning("[XBedSync] 上报扫描异常: " + e);
            if (config.isDebug()) {
                e.printStackTrace();
            }
        }
    }

    private void pruneRetryStates(List<String> pending) {
        if (pending.isEmpty()) {
            if (!retryStates.isEmpty()) {
                retryStates.clear();
            }
            return;
        }
        Set<String> alive = new HashSet<String>(pending);
        retryStates.keySet().retainAll(alive);
    }

    private void attempt(String matchId, RetryState state) {
        String body;
        try {
            body = queue.readPending(matchId);
        } catch (IOException e) {
            plugin.getLogger().warning("[XBedSync] 读取队列文件失败 matchId=" + matchId + ": " + e.getMessage());
            return;
        }
        long now = System.currentTimeMillis();
        ReportResult result = send("POST", "/ingest/match", body.getBytes(StandardCharsets.UTF_8));
        if (result.isSuccess()) {
            queue.deletePending(matchId);
            retryStates.remove(matchId);
            lastSuccessAt = now;
            lastSuccessMatchId = matchId;
            lastHttpCode = result.getHttpCode();
            lastError = "";
            plugin.getLogger().info("[XBedSync] 比赛数据上报成功 matchId=" + matchId
                    + (result.isDuplicate() ? "（服务端判定为重复，按成功处理）" : "")
                    + " " + result.describe());
            return;
        }
        state.attempts++;
        state.nextAttemptAt = now + computeBackoffSeconds(state.attempts) * 1000L;
        lastFailureAt = now;
        lastFailureMatchId = matchId;
        lastHttpCode = result.getHttpCode();
        lastError = result.describe();
        plugin.getLogger().warning("[XBedSync] 比赛数据上报失败 matchId=" + matchId
                + "（第 " + state.attempts + " 次）: " + result.describe()
                + "；" + computeBackoffSeconds(state.attempts) + " 秒后重试");
        if (result.getHttpCode() == 401 && config.isDebug()) {
            plugin.getLogger().warning("[XBedSync] 401 通常表示 serverKey/hmacSecret 与服务端不一致，或服务器时钟偏差超过 300 秒。");
        }
    }

    private long computeBackoffSeconds(int attempts) {
        long value = Math.max(1, config.getRetryInitialDelaySeconds());
        long max = Math.max(value, config.getRetryMaxDelaySeconds());
        for (int i = 1; i < attempts && value < max; i++) {
            value *= 2L;
        }
        return Math.min(value, max);
    }

    /**
     * 发送一次 HMAC 签名请求（同步，调用方必须位于异步线程）。
     */
    public ReportResult send(String method, String endpoint, byte[] body) {
        long begin = System.currentTimeMillis();
        HttpURLConnection connection = null;
        try {
            String requestUrl = config.getApiBaseUrl() + endpoint;
            URL url = new URL(requestUrl);
            String path = url.getPath();
            if (path == null || path.isEmpty()) {
                path = "/";
            }
            String timestamp = String.valueOf(System.currentTimeMillis() / 1000L);
            String nonce = HmacSigner.newNonce();
            String signature = HmacSigner.sign(config.getHmacSecret(), method, path, timestamp, nonce, body);

            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(config.getHttpTimeoutSeconds() * 1000);
            connection.setReadTimeout(config.getHttpTimeoutSeconds() * 1000);
            connection.setRequestMethod(method.toUpperCase(Locale.ROOT));
            connection.setRequestProperty("X-XBed-Server", config.getServerKey());
            connection.setRequestProperty("X-XBed-Timestamp", timestamp);
            connection.setRequestProperty("X-XBed-Nonce", nonce);
            connection.setRequestProperty("X-XBed-Signature", signature);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "XBedSync/" + plugin.getDescription().getVersion());
            connection.setUseCaches(false);

            if (body != null && body.length > 0) {
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                OutputStream out = connection.getOutputStream();
                try {
                    out.write(body);
                    out.flush();
                } finally {
                    out.close();
                }
            }

            int code = connection.getResponseCode();
            InputStream stream = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
            String response = read(stream);
            long elapsed = System.currentTimeMillis() - begin;
            if (code >= 200 && code < 300) {
                boolean duplicate = response != null && DUPLICATE_PATTERN.matcher(response).find();
                return ReportResult.ok(code, duplicate, shorten(response), elapsed);
            }
            return ReportResult.httpFailure(code, shorten(response), elapsed);
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - begin;
            String message = e.getClass().getSimpleName();
            if (e.getMessage() != null) {
                message = message + ": " + e.getMessage();
            }
            return ReportResult.networkFailure(message, elapsed);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * 联调用：GET /api/ingest/ping（同样带 HMAC 签名）。必须在异步线程调用。
     */
    public ReportResult ping() {
        return send("GET", "/ingest/ping", new byte[0]);
    }

    private static String read(InputStream stream) {
        if (stream == null) {
            return "";
        }
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[2048];
            int total = 0;
            int read;
            while ((read = stream.read(chunk)) != -1 && total < MAX_RESPONSE_BYTES) {
                int writable = Math.min(read, MAX_RESPONSE_BYTES - total);
                buffer.write(chunk, 0, writable);
                total += writable;
            }
            return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        } finally {
            try {
                stream.close();
            } catch (IOException ignored) {
                // 忽略
            }
        }
    }

    private static String shorten(String response) {
        if (response == null) {
            return "";
        }
        String singleLine = response.replace('\n', ' ').replace('\r', ' ').trim();
        if (singleLine.length() > MAX_RESPONSE_CHARS) {
            return singleLine.substring(0, MAX_RESPONSE_CHARS) + "...";
        }
        return singleLine;
    }

    public long getLastSuccessAt() {
        return lastSuccessAt;
    }

    public long getLastFailureAt() {
        return lastFailureAt;
    }

    public int getLastHttpCode() {
        return lastHttpCode;
    }

    public String getLastError() {
        return lastError;
    }

    public String getLastSuccessMatchId() {
        return lastSuccessMatchId;
    }

    public String getLastFailureMatchId() {
        return lastFailureMatchId;
    }

    /**
     * @return 距离下一次重试的秒数；-1 表示没有等待中的重试
     */
    public long getNextRetryInSeconds() {
        long now = System.currentTimeMillis();
        long next = Long.MAX_VALUE;
        for (RetryState state : retryStates.values()) {
            if (state.nextAttemptAt > 0L) {
                next = Math.min(next, state.nextAttemptAt);
            }
        }
        if (next == Long.MAX_VALUE) {
            return -1L;
        }
        long diff = next - now;
        return diff <= 0L ? 0L : (diff + 999L) / 1000L;
    }

    /**
     * @return 当前处于退避等待的场次数量
     */
    public int getBackoffCount() {
        long now = System.currentTimeMillis();
        int count = 0;
        for (RetryState state : retryStates.values()) {
            if (state.nextAttemptAt > now) {
                count++;
            }
        }
        return count;
    }

    private static final class RetryState {
        private int attempts;
        private volatile long nextAttemptAt;
    }
}
