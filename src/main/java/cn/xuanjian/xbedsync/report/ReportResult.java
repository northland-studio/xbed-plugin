package cn.xuanjian.xbedsync.report;

/**
 * 一次 HTTP 上报的结果。不可变。
 */
public final class ReportResult {

    private final boolean success;
    private final boolean duplicate;
    private final boolean networkError;
    private final int httpCode;
    private final String message;
    private final long elapsedMillis;

    private ReportResult(boolean success, boolean duplicate, boolean networkError,
                         int httpCode, String message, long elapsedMillis) {
        this.success = success;
        this.duplicate = duplicate;
        this.networkError = networkError;
        this.httpCode = httpCode;
        this.message = message;
        this.elapsedMillis = elapsedMillis;
    }

    public static ReportResult ok(int httpCode, boolean duplicate, String message, long elapsedMillis) {
        return new ReportResult(true, duplicate, false, httpCode, message, elapsedMillis);
    }

    public static ReportResult httpFailure(int httpCode, String message, long elapsedMillis) {
        return new ReportResult(false, false, false, httpCode, message, elapsedMillis);
    }

    public static ReportResult networkFailure(String message, long elapsedMillis) {
        return new ReportResult(false, false, true, -1, message, elapsedMillis);
    }

    public boolean isSuccess() {
        return success;
    }

    public boolean isDuplicate() {
        return duplicate;
    }

    public boolean isNetworkError() {
        return networkError;
    }

    public int getHttpCode() {
        return httpCode;
    }

    public String getMessage() {
        return message;
    }

    public long getElapsedMillis() {
        return elapsedMillis;
    }

    /**
     * 供命令/日志展示的简短描述。
     */
    public String describe() {
        if (networkError) {
            return "网络错误: " + message + " (" + elapsedMillis + "ms)";
        }
        return "HTTP " + httpCode + (duplicate ? " duplicate" : "") + ": " + message + " (" + elapsedMillis + "ms)";
    }
}
