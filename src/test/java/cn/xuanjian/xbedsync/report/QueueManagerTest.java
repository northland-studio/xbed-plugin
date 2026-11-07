package cn.xuanjian.xbedsync.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class QueueManagerTest {

    private static final String MATCH_A = "3f2c8a1e-6c2a-4a1e-9f3e-1a2b3c4d5e6f";
    private static final String MATCH_B = "4a3d9b2f-7d3b-4b2f-8f4f-2b3c4d5e6f70";

    @TempDir
    Path tempDir;

    private QueueManager newQueue() {
        return new QueueManager(new File(tempDir.toFile(), "queue"));
    }

    @Test
    void writesReadsAndDeletesPending() throws Exception {
        QueueManager queue = newQueue();
        queue.writePending(MATCH_A, "{\"a\":1}");
        assertEquals(1, queue.pendingCount());
        assertEquals(0, queue.partialCount());
        assertEquals("{\"a\":1}", queue.readPending(MATCH_A));

        queue.deletePending(MATCH_A);
        assertEquals(0, queue.pendingCount());
        assertFalse(queue.pendingFile(MATCH_A).exists());
    }

    @Test
    void pendingListExcludesPartialAndTmpFiles() throws Exception {
        QueueManager queue = newQueue();
        queue.writePending(MATCH_A, "{}");
        queue.writePartial(MATCH_B, "{\"partial\":true}");

        List<String> pending = queue.pendingMatchIds();
        assertEquals(1, pending.size());
        assertEquals(MATCH_A, pending.get(0));
        assertEquals(1, queue.partialCount());
    }

    @Test
    void promotesPartialToPending() throws Exception {
        QueueManager queue = newQueue();
        queue.writePartial(MATCH_B, "{\"abnormal\":true}");
        queue.promotePartial(MATCH_B);

        assertTrue(queue.pendingFile(MATCH_B).exists());
        assertFalse(queue.partialFile(MATCH_B).exists());
        String content = new String(Files.readAllBytes(queue.pendingFile(MATCH_B).toPath()),
                StandardCharsets.UTF_8);
        assertEquals("{\"abnormal\":true}", content);
    }

    @Test
    void promoteAllPartialsReturnsCount() {
        QueueManager queue = newQueue();
        try {
            queue.writePartial(MATCH_A, "{}");
            queue.writePartial(MATCH_B, "{}");
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        assertEquals(2, queue.promoteAllPartials());
        assertEquals(2, queue.pendingCount());
        assertEquals(0, queue.partialCount());
    }

    @Test
    void fifoOrderByModifiedTime() throws Exception {
        QueueManager queue = newQueue();
        queue.writePending(MATCH_A, "{}");
        Thread.sleep(20L);
        queue.writePending(MATCH_B, "{}");
        assertEquals(MATCH_A, queue.pendingMatchIds().get(0));
        assertEquals(MATCH_B, queue.pendingMatchIds().get(1));
    }
}
