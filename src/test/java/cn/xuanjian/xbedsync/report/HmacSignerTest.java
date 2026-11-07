package cn.xuanjian.xbedsync.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class HmacSignerTest {

    /** 用于联调文档与回归的固定向量。 */
    private static final String SECRET = "demo-secret";
    private static final String TIMESTAMP = "1730000000";
    private static final String NONCE = "0123456789abcdef0123456789abcdef";
    private static final String BODY = "{\"schemaVersion\":1,\"matchId\":\"3f2c8a1e-6c2a-4a1e-9f3e-1a2b3c4d5e6f\","
            + "\"arena\":\"castle\",\"arenaDisplay\":\"Castle\",\"mode\":\"4v4v4v4\","
            + "\"startedAt\":\"2026-11-08T12:00:00.000Z\",\"endedAt\":\"2026-11-08T12:18:00.000Z\","
            + "\"endReason\":\"team_eliminated\",\"winnerTeam\":\"红队\",\"isDraw\":false,"
            + "\"abnormal\":false,\"abnormalReason\":null,\"players\":[],\"events\":[]}";

    @Test
    void hashesEmptyBodyAsContract() {
        assertEquals(HmacSigner.EMPTY_BODY_SHA256, HmacSigner.sha256Hex(new byte[0]));
    }

    @Test
    void signsKnownVector() {
        String signature = HmacSigner.sign(SECRET, "POST", "/api/ingest/match", TIMESTAMP, NONCE,
                BODY.getBytes(StandardCharsets.UTF_8));
        assertEquals("0390927d3d557d5e6f8b7707a76b0d86a13ea14dca5e472420b4f8f453f444bb", signature);
    }

    @Test
    void methodIsCaseInsensitive() {
        String lower = HmacSigner.sign(SECRET, "post", "/api/ingest/match", TIMESTAMP, NONCE,
                BODY.getBytes(StandardCharsets.UTF_8));
        String upper = HmacSigner.sign(SECRET, "POST", "/api/ingest/match", TIMESTAMP, NONCE,
                BODY.getBytes(StandardCharsets.UTF_8));
        assertEquals(upper, lower);
    }

    @Test
    void rejectsEmptySecret() {
        assertThrows(IllegalArgumentException.class,
                () -> HmacSigner.sign("", "GET", "/api/ingest/ping", TIMESTAMP, NONCE, new byte[0]));
    }
}
