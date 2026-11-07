package cn.xuanjian.xbedsync.report;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * HMAC-SHA256 请求签名器。
 *
 * <p>签名串（canonical string）：</p>
 * <pre>
 * {METHOD}\n{PATH}\n{TIMESTAMP}\n{NONCE}\n{SHA256_HEX(body)}
 * </pre>
 *
 * <ul>
 *   <li>METHOD 大写，如 {@code POST}</li>
 *   <li>PATH 为不含域名的请求路径，含 {@code /api} 前缀，如 {@code /api/ingest/match}</li>
 *   <li>TIMESTAMP 为秒级 Unix 时间戳</li>
 *   <li>NONCE 为随机 hex（本插件生成 16 字节 = 32 位 hex）</li>
 *   <li>body 为请求体原始字节；GET 无请求体时使用空字节数组，
 *       其 SHA256 为 {@code e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855}</li>
 * </ul>
 *
 * <p>签名 = HMAC-SHA256(secret, canonical) 的小写 hex。</p>
 */
public final class HmacSigner {

    /** 空请求体的 SHA256（小写 hex），用于 GET /ingest/ping。 */
    public static final String EMPTY_BODY_SHA256 =
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

    private static final char[] HEX = "0123456789abcdef".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private HmacSigner() {
    }

    public static String newNonce() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return toHex(bytes);
    }

    public static String sha256Hex(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return toHex(digest.digest(data == null ? new byte[0] : data));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /**
     * 计算签名。
     *
     * @param secret    HMAC 共享密钥（不能为空）
     * @param method    HTTP 方法，内部会转为大写
     * @param path      不含域名的路径（含 /api 前缀）
     * @param timestamp 秒级时间戳字符串
     * @param nonce     随机串
     * @param body      请求体，可为 null（视为空）
     */
    public static String sign(String secret, String method, String path, String timestamp, String nonce, byte[] body) {
        if (secret == null || secret.isEmpty()) {
            throw new IllegalArgumentException("hmacSecret is empty");
        }
        String canonical = method.toUpperCase(java.util.Locale.ROOT)
                + "\n" + path
                + "\n" + timestamp
                + "\n" + nonce
                + "\n" + sha256Hex(body);
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
            return toHex(mac.doFinal(canonical.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 not available", e);
        }
    }

    public static String toHex(byte[] bytes) {
        char[] out = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xff;
            out[i * 2] = HEX[v >>> 4];
            out[i * 2 + 1] = HEX[v & 0x0f];
        }
        return new String(out);
    }
}
