package cn.xuanjian.xbedsync;

import java.util.List;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import cn.xuanjian.xbedsync.command.XBedSyncCommand;
import cn.xuanjian.xbedsync.config.PluginConfig;
import cn.xuanjian.xbedsync.listener.BedWarsListener;
import cn.xuanjian.xbedsync.match.MatchManager;
import cn.xuanjian.xbedsync.report.QueueManager;
import cn.xuanjian.xbedsync.report.ReportSender;

/**
 * XBedSync 插件主类。
 *
 * <p>职责：读取配置 → 恢复本地队列（含崩溃快照）→ 注册 BedWars1058 监听 →
 * 启动异步上报任务与定时快照任务。</p>
 *
 * <p>安全约束：</p>
 * <ul>
 *   <li>配置校验失败时拒绝启用（宁可不采集，也不上报脏数据）</li>
 *   <li>日志/命令输出永不打印完整 hmacSecret</li>
 *   <li>生产强制 HTTPS，http 仅允许 localhost/127.0.0.1</li>
 * </ul>
 */
public final class XBedSync extends JavaPlugin {

    private PluginConfig pluginConfig;
    private QueueManager queue;
    private ReportSender sender;
    private MatchManager matchManager;
    private BukkitTask snapshotTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        pluginConfig = PluginConfig.load(getConfig(), getDataFolder());
        List<String> errors = pluginConfig.validate();
        if (!errors.isEmpty()) {
            getLogger().severe("==================== XBedSync 配置错误 ====================");
            for (String error : errors) {
                getLogger().severe(" - " + error);
            }
            getLogger().severe("请修正 plugins/XBedSync/config.yml（apiBaseUrl / serverKey / hmacSecret）后重启服务器。");
            getLogger().severe("为避免向 XBed 发送错误数据，插件已停止启用。");
            getLogger().severe("==========================================================");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        if (pluginConfig.isInsecureHttp()) {
            getLogger().warning("[XBedSync] apiBaseUrl 使用 http，仅允许本地联调使用；生产环境请改回 https。");
        }

        try {
            queue = new QueueManager(pluginConfig.getQueueDir());
        } catch (RuntimeException e) {
            getLogger().severe("[XBedSync] 无法初始化本地队列目录: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        int promoted = queue.promoteAllPartials();
        if (promoted > 0) {
            getLogger().warning("[XBedSync] 发现 " + promoted + " 个服务器崩溃/异常关服留下的比赛快照，"
                    + "已转为待上报数据（abnormal=true, endReason=server_crash）。");
        }

        sender = new ReportSender(this, pluginConfig, queue);
        matchManager = new MatchManager(this, pluginConfig, queue);

        getServer().getPluginManager().registerEvents(new BedWarsListener(matchManager), this);

        PluginCommand command = getCommand("xbedsync");
        if (command != null) {
            XBedSyncCommand executor = new XBedSyncCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        } else {
            getLogger().warning("[XBedSync] plugin.yml 中未找到 xbedsync 命令定义。");
        }

        startSnapshotTask();
        sender.start();

        getLogger().info("[XBedSync] 已启用。serverKey=" + pluginConfig.getServerKey()
                + " season=" + pluginConfig.getSeason()
                + " queue=" + queue.getDir().getAbsolutePath()
                + " pending=" + queue.pendingCount()
                + " modeAllowlist=" + (pluginConfig.isModeAllowed(null) ? "ALL" : "配置限制")
                + " debug=" + pluginConfig.isDebug());
    }

    @Override
    public void onDisable() {
        if (snapshotTask != null) {
            snapshotTask.cancel();
            snapshotTask = null;
        }
        if (sender != null) {
            sender.stop();
        }
        if (matchManager != null) {
            // 关服时仍在进行的比赛按 server_shutdown 异常保存，同步写文件
            matchManager.shutdown();
        }
        int pending = queue == null ? 0 : queue.pendingCount();
        getLogger().info("[XBedSync] 已停用。本地待上报队列剩余 " + pending + " 场，下次启动会自动补发。");
    }

    private void startSnapshotTask() {
        int interval = pluginConfig.getSnapshotIntervalSeconds();
        if (interval <= 0) {
            getLogger().warning("[XBedSync] snapshotIntervalSeconds=0，已关闭崩溃恢复快照（不推荐）。");
            return;
        }
        final long ticks = interval * 20L;
        snapshotTask = getServer().getScheduler().runTaskTimer(this, new Runnable() {
            @Override
            public void run() {
                if (matchManager != null) {
                    matchManager.snapshotAll();
                }
            }
        }, ticks, ticks);
    }

    /**
     * 调试日志（受 config.yml debug 控制）。
     */
    public void debug(String message) {
        if (pluginConfig != null && pluginConfig.isDebug()) {
            getLogger().info("[XBedSync][debug] " + message);
        }
    }

    public PluginConfig getPluginConfig() {
        return pluginConfig;
    }

    public QueueManager getQueue() {
        return queue;
    }

    public ReportSender getSender() {
        return sender;
    }

    public MatchManager getMatchManager() {
        return matchManager;
    }
}
