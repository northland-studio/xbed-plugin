package cn.xuanjian.xbedsync.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import cn.xuanjian.xbedsync.XBedSync;
import cn.xuanjian.xbedsync.config.PluginConfig;
import cn.xuanjian.xbedsync.match.Match;
import cn.xuanjian.xbedsync.report.QueueManager;
import cn.xuanjian.xbedsync.report.ReportResult;
import cn.xuanjian.xbedsync.report.ReportSender;
import cn.xuanjian.xbedsync.util.TimeUtil;

/**
 * /xbedsync 管理命令：
 * <ul>
 *   <li>{@code /xbedsync status} 队列长度、进行中比赛、最近成功/失败</li>
 *   <li>{@code /xbedsync retry} 立即重发（清空退避计时）</li>
 *   <li>{@code /xbedsync ping} 调用 GET /api/ingest/ping 联调</li>
 * </ul>
 */
public final class XBedSyncCommand implements CommandExecutor, TabCompleter {

    private static final String PERMISSION = "xbedsync.admin";
    private static final String PREFIX = ChatColor.AQUA + "[XBedSync] " + ChatColor.RESET;

    private final XBedSync plugin;

    public XBedSyncCommand(XBedSync plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(PREFIX + ChatColor.RED + "你没有权限使用该命令。");
            return true;
        }
        if (args == null || args.length == 0) {
            sendUsage(sender, label);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if ("status".equals(sub)) {
            status(sender, label);
        } else if ("retry".equals(sub)) {
            retry(sender);
        } else if ("ping".equals(sub)) {
            ping(sender);
        } else if ("help".equals(sub)) {
            sendUsage(sender, label);
        } else {
            sender.sendMessage(PREFIX + ChatColor.RED + "未知子命令：" + args[0]);
            sendUsage(sender, label);
        }
        return true;
    }

    private void sendUsage(CommandSender sender, String label) {
        sender.sendMessage(PREFIX + ChatColor.GRAY + "用法：");
        sender.sendMessage(ChatColor.GRAY + "  /" + label + " status  " + ChatColor.DARK_GRAY + "- 队列与上报状态");
        sender.sendMessage(ChatColor.GRAY + "  /" + label + " retry   " + ChatColor.DARK_GRAY + "- 立即重发本地队列");
        sender.sendMessage(ChatColor.GRAY + "  /" + label + " ping    " + ChatColor.DARK_GRAY + "- 调用 /api/ingest/ping 联调");
    }

    private void status(CommandSender sender, String label) {
        PluginConfig config = plugin.getPluginConfig();
        QueueManager queue = plugin.getQueue();
        ReportSender reportSender = plugin.getSender();

        sender.sendMessage(PREFIX + ChatColor.GOLD + "XBedSync 状态");
        sender.sendMessage(ChatColor.GRAY + "  API 地址: " + ChatColor.WHITE + config.getApiBaseUrl());
        sender.sendMessage(ChatColor.GRAY + "  serverKey: " + ChatColor.WHITE + config.getServerKey()
                + ChatColor.GRAY + "  赛季: " + ChatColor.WHITE + config.getSeason());
        sender.sendMessage(ChatColor.GRAY + "  hmacSecret: " + ChatColor.WHITE + config.getMaskedSecret());
        if (config.isInsecureHttp()) {
            sender.sendMessage(ChatColor.YELLOW + "  注意：当前使用 http（仅本地调试允许，生产必须 https）");
        }

        sender.sendMessage(ChatColor.GRAY + "  待上报队列: " + ChatColor.WHITE + queue.pendingCount() + " 场"
                + ChatColor.GRAY + "  崩溃快照: " + ChatColor.WHITE + queue.partialCount() + " 个"
                + ChatColor.GRAY + "  退避中: " + ChatColor.WHITE + reportSender.getBackoffCount() + " 场");
        long nextRetry = reportSender.getNextRetryInSeconds();
        if (nextRetry >= 0L) {
            sender.sendMessage(ChatColor.GRAY + "  下次重试: " + ChatColor.WHITE + nextRetry + " 秒后");
        }

        if (reportSender.getLastSuccessAt() > 0L) {
            sender.sendMessage(ChatColor.GRAY + "  最近成功: " + ChatColor.GREEN + TimeUtil.ago(reportSender.getLastSuccessAt())
                    + ChatColor.GRAY + "  matchId=" + ChatColor.WHITE + reportSender.getLastSuccessMatchId());
        } else {
            sender.sendMessage(ChatColor.GRAY + "  最近成功: " + ChatColor.WHITE + "尚未成功上报过");
        }
        if (reportSender.getLastFailureAt() > 0L) {
            sender.sendMessage(ChatColor.GRAY + "  最近失败: " + ChatColor.RED + TimeUtil.ago(reportSender.getLastFailureAt())
                    + ChatColor.GRAY + "  matchId=" + ChatColor.WHITE + reportSender.getLastFailureMatchId()
                    + ChatColor.GRAY + "  HTTP " + ChatColor.WHITE + reportSender.getLastHttpCode());
            sender.sendMessage(ChatColor.GRAY + "    原因: " + ChatColor.WHITE + reportSender.getLastError());
        }

        int active = plugin.getMatchManager().getActiveCount();
        sender.sendMessage(ChatColor.GRAY + "  进行中比赛: " + ChatColor.WHITE + active + " 场");
        for (Match match : plugin.getMatchManager().getActiveMatches()) {
            sender.sendMessage(ChatColor.DARK_GRAY + "    - " + match.getArenaName() + " / " + match.getMode()
                    + " / " + match.getMatchId() + " / 玩家 " + match.getPlayerCount());
        }

        if (queue.pendingCount() > 0) {
            sender.sendMessage(ChatColor.GRAY + "  提示: 长期失败请检查 " + ChatColor.WHITE + queue.getDir().getAbsolutePath());
            sender.sendMessage(ChatColor.GRAY + "        以及 /" + label + " retry 立即重试。");
        }
    }

    private void retry(CommandSender sender) {
        int pending = plugin.getSender().retryNow();
        if (pending == 0) {
            sender.sendMessage(PREFIX + ChatColor.GREEN + "队列为空，没有需要重发的比赛。");
            return;
        }
        sender.sendMessage(PREFIX + ChatColor.GREEN + "已清空退避计时，本地队列共 " + pending
                + " 场，将在下一次扫描（≤" + plugin.getPluginConfig().getScanIntervalSeconds() + " 秒）开始发送。");
    }

    private void ping(final CommandSender sender) {
        sender.sendMessage(PREFIX + ChatColor.GRAY + "正在请求 " + plugin.getPluginConfig().getApiBaseUrl() + "/ingest/ping ...");
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                final ReportResult result = plugin.getSender().ping();
                plugin.getServer().getScheduler().runTask(plugin, new Runnable() {
                    @Override
                    public void run() {
                        if (result.isSuccess()) {
                            sender.sendMessage(PREFIX + ChatColor.GREEN + "ping 成功: " + result.describe());
                        } else {
                            sender.sendMessage(PREFIX + ChatColor.RED + "ping 失败: " + result.describe());
                            if (result.getHttpCode() == 401) {
                                sender.sendMessage(ChatColor.GRAY + "  401 常见原因：serverKey/hmacSecret 不匹配，或服务器时间偏差超过 300 秒。");
                            }
                        }
                    }
                });
            }
        });
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args == null || args.length != 1) {
            return new ArrayList<String>();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<String>();
        for (String candidate : Arrays.asList("status", "retry", "ping", "help")) {
            if (candidate.startsWith(prefix)) {
                result.add(candidate);
            }
        }
        return result;
    }
}
