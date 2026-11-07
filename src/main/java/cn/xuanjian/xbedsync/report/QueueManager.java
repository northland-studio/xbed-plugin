package cn.xuanjian.xbedsync.report;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 本地持久化上报队列。
 *
 * <p>目录结构（queueDir）：</p>
 * <pre>
 * queue/
 *   &lt;matchId&gt;.json          待上报的完整比赛数据（原子写）
 *   &lt;matchId&gt;.partial.json  进行中比赛的崩溃恢复快照（原子写）
 * </pre>
 *
 * <p>原子写：先写入同目录临时文件并 fsync，再 move 覆盖目标文件，避免服务器崩溃时留下半截 JSON。
 * 只有 XBed 返回 2xx 或 duplicate=true 后，调用方才会删除 {@code <matchId>.json}。</p>
 */
public final class QueueManager {

    public static final String SUFFIX = ".json";
    public static final String PARTIAL_SUFFIX = ".partial.json";
    private static final String TMP_SUFFIX = ".tmp";

    private final File dir;

    public QueueManager(File dir) {
        this.dir = dir;
        if (!dir.exists() && !dir.mkdirs() && !dir.exists()) {
            throw new IllegalStateException("无法创建队列目录: " + dir.getAbsolutePath());
        }
    }

    public File getDir() {
        return dir;
    }

    public File pendingFile(String matchId) {
        return new File(dir, matchId + SUFFIX);
    }

    public File partialFile(String matchId) {
        return new File(dir, matchId + PARTIAL_SUFFIX);
    }

    /**
     * 写入待上报数据（原子写）。
     */
    public void writePending(String matchId, String json) throws IOException {
        atomicWrite(pendingFile(matchId), json);
    }

    /**
     * 写入进行中比赛的崩溃恢复快照（原子写）。
     */
    public void writePartial(String matchId, String json) throws IOException {
        atomicWrite(partialFile(matchId), json);
    }

    public String readPending(String matchId) throws IOException {
        byte[] bytes = Files.readAllBytes(pendingFile(matchId).toPath());
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public void deletePending(String matchId) {
        File file = pendingFile(matchId);
        if (file.exists() && !file.delete()) {
            // 删除失败会在下一次扫描时再次尝试上报；服务端 duplicate 逻辑保证幂等
            file.deleteOnExit();
        }
    }

    /**
     * 删除崩溃恢复快照（最终数据写出成功后调用）。
     */
    public void deletePartial(String matchId) {
        File file = partialFile(matchId);
        if (file.exists() && !file.delete()) {
            file.deleteOnExit();
        }
    }

    /**
     * 待上报的 matchId 列表，按文件修改时间升序（先进先出）。
     */
    public List<String> pendingMatchIds() {
        return listIds(SUFFIX, false);
    }

    /**
     * 崩溃恢复快照的 matchId 列表。
     */
    public List<String> partialMatchIds() {
        return listIds(PARTIAL_SUFFIX, true);
    }

    public int pendingCount() {
        return pendingMatchIds().size();
    }

    public int partialCount() {
        return partialMatchIds().size();
    }

    /**
     * 把崩溃快照升级为待上报数据（原子 move）。快照本身已经是 abnormal=true 的完整 payload。
     */
    public void promotePartial(String matchId) throws IOException {
        Path source = partialFile(matchId).toPath();
        Path target = pendingFile(matchId).toPath();
        move(source, target);
    }

    public int promoteAllPartials() {
        int promoted = 0;
        for (String matchId : partialMatchIds()) {
            try {
                promotePartial(matchId);
                promoted++;
            } catch (IOException ignored) {
                // 无法恢复的快照保留原样，由人工排查
            }
        }
        return promoted;
    }

    private List<String> listIds(final String suffix, final boolean partial) {
        File[] files = dir.listFiles();
        if (files == null || files.length == 0) {
            return Collections.emptyList();
        }
        List<File> matched = new ArrayList<File>();
        for (File file : files) {
            String name = file.getName();
            if (!file.isFile() || !name.endsWith(suffix)) {
                continue;
            }
            if (!partial && name.endsWith(PARTIAL_SUFFIX)) {
                continue;
            }
            if (name.endsWith(TMP_SUFFIX)) {
                continue;
            }
            matched.add(file);
        }
        Collections.sort(matched, new Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                long diff = a.lastModified() - b.lastModified();
                if (diff != 0L) {
                    return diff < 0L ? -1 : 1;
                }
                return a.getName().compareTo(b.getName());
            }
        });
        List<String> ids = new ArrayList<String>(matched.size());
        for (File file : matched) {
            String name = file.getName();
            ids.add(name.substring(0, name.length() - suffix.length()));
        }
        return ids;
    }

    private static void atomicWrite(File target, String content) throws IOException {
        File parent = target.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs() && !parent.exists()) {
            throw new IOException("无法创建目录: " + parent.getAbsolutePath());
        }
        File tmp = new File(parent, target.getName() + TMP_SUFFIX);
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(tmp);
            out.write(content.getBytes(StandardCharsets.UTF_8));
            out.flush();
            out.getFD().sync();
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException ignored) {
                    // 忽略关闭异常，下面的 move 会暴露真实问题
                }
            }
        }
        move(tmp.toPath(), target.toPath());
    }

    private static void move(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            // 部分文件系统（如某些网络盘）不支持原子 move，退化为普通 move
            if (Files.exists(source)) {
                Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
            } else {
                throw e;
            }
        }
    }
}
