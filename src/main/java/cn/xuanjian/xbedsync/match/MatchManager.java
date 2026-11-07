package cn.xuanjian.xbedsync.match;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import com.andrei1058.bedwars.api.arena.GameState;
import com.andrei1058.bedwars.api.arena.IArena;
import com.andrei1058.bedwars.api.arena.team.ITeam;
import com.andrei1058.bedwars.api.events.gameplay.GameEndEvent;
import com.andrei1058.bedwars.api.events.gameplay.GameStateChangeEvent;
import com.andrei1058.bedwars.api.events.gameplay.TeamAssignEvent;
import com.andrei1058.bedwars.api.events.player.PlayerBedBreakEvent;
import com.andrei1058.bedwars.api.events.player.PlayerFirstSpawnEvent;
import com.andrei1058.bedwars.api.events.player.PlayerJoinArenaEvent;
import com.andrei1058.bedwars.api.events.player.PlayerKillEvent;
import com.andrei1058.bedwars.api.events.player.PlayerLeaveArenaEvent;
import com.andrei1058.bedwars.api.events.player.PlayerReJoinEvent;
import com.andrei1058.bedwars.api.events.team.TeamEliminatedEvent;
import com.andrei1058.bedwars.api.language.Language;

import cn.xuanjian.xbedsync.XBedSync;
import cn.xuanjian.xbedsync.config.PluginConfig;
import cn.xuanjian.xbedsync.report.PayloadBuilder;
import cn.xuanjian.xbedsync.report.QueueManager;

/**
 * 比赛生命周期管理：把 BedWars1058 事件聚合成单场比赛数据，并写入本地队列。
 *
 * <p>生命周期判定（基于 23.1.1 实际字节码行为，见 docs/DATA-MAPPING.md）：</p>
 * <ul>
 *   <li>开始：{@code GameStateChangeEvent} 新状态 = {@code playing}</li>
 *   <li>结束：{@code GameEndEvent}（由 {@code Arena#checkWinner()} 在状态切到 restarting 之后触发）</li>
 *   <li>兜底：状态切到 {@code restarting} 后等待 2 秒仍未收到 GameEndEvent，
 *       判定为异常结束（竞技场重启/生命周期不完整）</li>
 * </ul>
 *
 * <p>线程模型：所有方法均在 Bukkit 主线程调用；文件写操作异步执行。</p>
 */
public final class MatchManager {

    /** 状态切到 restarting 后等待 GameEndEvent 的宽限 tick（2 秒）。 */
    private static final long END_FALLBACK_DELAY_TICKS = 40L;
    /** 竞技场被禁用/重启时，异常结束的宽限 tick。 */
    private static final long ABNORMAL_FINALIZE_DELAY_TICKS = 20L;

    private final XBedSync plugin;
    private final PluginConfig config;
    private final QueueManager queue;

    private final Map<String, Match> activeMatches = new HashMap<String, Match>();
    private final Map<UUID, IArena> arenaByMatch = new HashMap<UUID, IArena>();
    private final Map<UUID, BukkitTask> fallbackTasks = new HashMap<UUID, BukkitTask>();

    public MatchManager(XBedSync plugin, PluginConfig config, QueueManager queue) {
        this.plugin = plugin;
        this.config = config;
        this.queue = queue;
    }

    // ------------------------------------------------------------------
    // 生命周期
    // ------------------------------------------------------------------

    /**
     * 比赛开始（GameStateChangeEvent -> playing）。
     */
    public void startMatch(IArena arena) {
        if (arena == null || arena.getArenaName() == null) {
            return;
        }
        String arenaName = arena.getArenaName();
        Match stale = activeMatches.get(arenaName);
        if (stale != null) {
            plugin.getLogger().warning("[XBedSync] 竞技场 " + arenaName + " 存在未完成的上一次比赛记录（matchId="
                    + stale.getMatchId() + "），先按异常结束处理。");
            if (!stale.isFinalized()) {
                if (!stale.isEndCaptured()) {
                    markAbnormal(stale, Match.END_ARENA_RESTART, Match.END_ARENA_RESTART);
                }
                finalizeMatch(stale, true);
            }
        }

        String mode = arena.getGroup();
        if (!config.isModeAllowed(mode)) {
            debug("跳过竞技场 " + arenaName + "：模式 " + mode + " 不在 modeAllowlist 内");
            return;
        }
        if (!config.isArenaAllowed(arenaName)) {
            debug("跳过竞技场 " + arenaName + "：不在 arenaAllowlist 内");
            return;
        }

        int teamCount = arena.getTeams() == null ? 0 : arena.getTeams().size();
        Match match = new Match(UUID.randomUUID(), arenaName, displayName(arena), mode(mode),
                Instant.now(), teamCount);

        if (arena.getPlayers() != null) {
            for (Player player : arena.getPlayers()) {
                if (player == null) {
                    continue;
                }
                match.addPlayer(player.getUniqueId(), player.getName(), teamName(arena.getTeam(player)));
            }
        }

        activeMatches.put(arenaName, match);
        arenaByMatch.put(match.getMatchId(), arena);
        plugin.getLogger().info("[XBedSync] 开始采集比赛 matchId=" + match.getMatchId()
                + " arena=" + arenaName + " mode=" + mode + " players=" + match.getPlayerCount());
        writePartialSnapshot(match);
    }

    /**
     * 正常结束（GameEndEvent）。
     */
    public void onGameEnd(GameEndEvent event) {
        if (event == null || event.getArena() == null) {
            return;
        }
        IArena arena = event.getArena();
        Match match = activeMatches.get(arena.getArenaName());
        if (match == null || match.isEndCaptured() || match.isFinalized()) {
            return;
        }
        match.setRecording(false);
        match.setEndCaptured(true);
        match.setEndedAt(Instant.now());
        match.setEndReason(Match.END_TEAM_ELIMINATED);

        ITeam winner = event.getTeamWinner();
        match.setWinnerTeam(teamName(winner));
        if (event.getWinners() != null) {
            for (UUID uuid : event.getWinners()) {
                match.addWinnerUuid(uuid);
            }
        }
        boolean hasWinner = match.getWinnerTeam() != null
                || (event.getWinners() != null && !event.getWinners().isEmpty());
        if (!hasWinner) {
            // 生命周期完整但无法确定胜者：标记异常，不伪造 winnerTeam
            match.setAbnormal(true);
            match.setAbnormalReason(Match.END_NO_WINNER);
            match.setEndReason(Match.END_NO_WINNER);
        }
        match.setDraw(false); // BedWars1058 23.1.1 无平局机制；无法确定胜者时走 abnormal，不虚构平局

        captureArenaCounters(match, arena);
        match.computePlacements();
        cancelFallback(match.getMatchId());

        // 下一 tick 再写队列：让本 tick 内其它插件的 GameEndEvent 监听先执行完
        Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
            @Override
            public void run() {
                finalizeMatch(match, true);
            }
        }, 1L);
    }

    /**
     * 状态变化：开始 or 进入结束兜底流程。
     */
    public void onStateChange(IArena arena, GameState oldState, GameState newState) {
        if (arena == null || newState == null) {
            return;
        }
        if (newState == GameState.playing) {
            startMatch(arena);
            return;
        }
        if (newState == GameState.restarting) {
            Match match = activeMatches.get(arena.getArenaName());
            if (match == null || match.isFinalized()) {
                return;
            }
            // checkWinner() 会先 changeStatus(restarting) 再 callEvent(GameEndEvent)，
            // 因此这里只停止记录并等待；2 秒内仍无 GameEndEvent 才判为异常。
            match.setRecording(false);
            if (!match.isEndCaptured()) {
                scheduleFallbackFinalize(match, arena, END_FALLBACK_DELAY_TICKS,
                        Match.END_ARENA_RESTART, Match.END_ARENA_RESTART);
            }
        }
    }

    /**
     * 竞技场被禁用 / 重启（尚未结束的比赛按异常处理）。
     */
    public void onArenaUnavailable(String arenaName, String reason) {
        Match match = activeMatches.get(arenaName);
        if (match == null || match.isFinalized() || match.isEndCaptured()) {
            return;
        }
        match.setRecording(false);
        scheduleFallbackFinalize(match, arenaByMatch.get(match.getMatchId()),
                ABNORMAL_FINALIZE_DELAY_TICKS, reason, reason);
    }

    // ------------------------------------------------------------------
    // 玩家 / 战斗事件
    // ------------------------------------------------------------------

    public void onKill(PlayerKillEvent event) {
        IArena arena = event == null ? null : event.getArena();
        Match match = active(arena);
        if (match == null || !match.isRecording()) {
            return;
        }
        Player victim = event.getVictim();
        if (victim == null) {
            return;
        }
        MatchPlayer victimStats = ensurePlayer(match, arena, victim);
        if (victimStats == null) {
            return;
        }
        victimStats.addDeath();

        PlayerKillEvent.PlayerKillCause cause = event.getCause();
        boolean finalKill = cause != null && cause.isFinalKill();
        if (finalKill) {
            victimStats.addFinalDeath();
        }

        Player killer = event.getKiller();
        String killerTeamName = null;
        if (killer != null && !killer.getUniqueId().equals(victim.getUniqueId())) {
            MatchPlayer killerStats = ensurePlayer(match, arena, killer);
            if (killerStats != null) {
                boolean despawnable = cause != null && cause.isDespawnable();
                if (config.isIncludeDespawnableKills() || !despawnable) {
                    killerStats.addKill();
                    if (finalKill) {
                        killerStats.addFinalKill();
                    }
                }
                killerTeamName = killerStats.getTeam();
            }
        }

        Instant now = Instant.now();
        match.addEvent(new MatchEvent(MatchEvent.TYPE_DEATH, now, victim.getName(),
                killer == null ? null : killer.getName(), victimStats.getTeam()));
        if (killer != null && !killer.getUniqueId().equals(victim.getUniqueId())) {
            match.addEvent(new MatchEvent(MatchEvent.TYPE_KILL, now, killer.getName(),
                    victim.getName(), killerTeamName));
        }
    }

    public void onBedBreak(PlayerBedBreakEvent event) {
        IArena arena = event == null ? null : event.getArena();
        Match match = active(arena);
        if (match == null || !match.isRecording()) {
            return;
        }
        Player player = event.getPlayer();
        if (player == null) {
            return;
        }
        MatchPlayer stats = ensurePlayer(match, arena, player);
        if (stats == null) {
            return;
        }
        stats.addBedBreak();
        match.addEvent(new MatchEvent(MatchEvent.TYPE_BED_BREAK, Instant.now(), player.getName(),
                teamName(event.getVictimTeam()), teamName(event.getPlayerTeam())));
    }

    public void onTeamEliminated(TeamEliminatedEvent event) {
        IArena arena = event == null ? null : event.getArena();
        Match match = active(arena);
        if (match == null || !match.isRecording()) {
            return;
        }
        String eliminatedTeam = teamName(event.getTeam());
        match.addEliminatedTeam(eliminatedTeam);
        match.addEvent(new MatchEvent(MatchEvent.TYPE_TEAM_ELIMINATED, Instant.now(),
                null, null, eliminatedTeam));
    }

    public void onJoin(PlayerJoinArenaEvent event) {
        if (event == null || event.isCancelled() || event.isSpectator()) {
            return;
        }
        IArena arena = event.getArena();
        Match match = active(arena);
        if (match == null || !match.isRecording()) {
            return;
        }
        Player player = event.getPlayer();
        if (player == null) {
            return;
        }
        MatchPlayer stats = ensurePlayer(match, arena, player);
        if (stats == null) {
            return;
        }
        match.addEvent(new MatchEvent(MatchEvent.TYPE_JOIN, Instant.now(), player.getName(),
                null, stats.getTeam()));
    }

    public void onLeave(PlayerLeaveArenaEvent event) {
        IArena arena = event == null ? null : event.getArena();
        Match match = active(arena);
        if (match == null || !match.isRecording()) {
            return;
        }
        Player player = event.getPlayer();
        if (player == null || !match.hasPlayer(player.getUniqueId())) {
            return;
        }
        match.addEvent(new MatchEvent(MatchEvent.TYPE_LEAVE, Instant.now(), player.getName(),
                null, match.getPlayer(player.getUniqueId()).getTeam()));
    }

    public void onRejoin(PlayerReJoinEvent event) {
        if (event == null || event.isCancelled()) {
            return;
        }
        IArena arena = event.getArena();
        Match match = active(arena);
        if (match == null || !match.isRecording()) {
            return;
        }
        Player player = event.getPlayer();
        if (player == null) {
            return;
        }
        MatchPlayer stats = ensurePlayer(match, arena, player);
        if (stats == null) {
            return;
        }
        match.addEvent(new MatchEvent(MatchEvent.TYPE_REJOIN, Instant.now(), player.getName(),
                null, stats.getTeam()));
    }

    public void onFirstSpawn(PlayerFirstSpawnEvent event) {
        if (event == null) {
            return;
        }
        IArena arena = event.getArena();
        Match match = active(arena);
        if (match == null || !match.isRecording()) {
            return;
        }
        Player player = event.getPlayer();
        if (player == null) {
            return;
        }
        MatchPlayer stats = ensurePlayer(match, arena, player);
        if (stats != null) {
            stats.setTeam(teamName(event.getTeam()));
        }
    }

    public void onTeamAssign(TeamAssignEvent event) {
        if (event == null || event.isCancelled()) {
            return;
        }
        IArena arena = event.getArena();
        Match match = active(arena);
        if (match == null || !match.isRecording()) {
            return;
        }
        Player player = event.getPlayer();
        if (player == null) {
            return;
        }
        MatchPlayer stats = ensurePlayer(match, arena, player);
        if (stats != null) {
            stats.setTeam(teamName(event.getTeam()));
        }
    }

    // ------------------------------------------------------------------
    // 快照 / 收尾
    // ------------------------------------------------------------------

    /**
     * 定时快照（主线程调用）：进行中比赛写入崩溃恢复快照。
     */
    public void snapshotAll() {
        for (Match match : new ArrayList<Match>(activeMatches.values())) {
            if (match.isFinalized() || match.isEndCaptured()) {
                continue;
            }
            writePartialSnapshot(match);
        }
    }

    /**
     * 关服收尾：把仍在进行的比赛按异常（server_shutdown）写出，同步写文件，尽力保证不丢数据。
     */
    public void shutdown() {
        for (Match match : new ArrayList<Match>(activeMatches.values())) {
            if (match.isFinalized()) {
                continue;
            }
            if (!match.isEndCaptured()) {
                markAbnormal(match, Match.END_SERVER_SHUTDOWN, Match.END_SERVER_SHUTDOWN);
                captureArenaCounters(match, arenaByMatch.get(match.getMatchId()));
                match.computePlacements();
            }
            finalizeMatch(match, false);
        }
    }

    public int getActiveCount() {
        return activeMatches.size();
    }

    public Collection<Match> getActiveMatches() {
        return new ArrayList<Match>(activeMatches.values());
    }

    public boolean hasActiveMatch(String arenaName) {
        return activeMatches.containsKey(arenaName);
    }

    // ------------------------------------------------------------------
    // 内部实现
    // ------------------------------------------------------------------

    private void scheduleFallbackFinalize(final Match match, final IArena arena, long delayTicks,
                                          final String endReason, final String abnormalReason) {
        cancelFallback(match.getMatchId());
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
            @Override
            public void run() {
                fallbackTasks.remove(match.getMatchId());
                if (match.isFinalized() || match.isEndCaptured()) {
                    return;
                }
                plugin.getLogger().warning("[XBedSync] 比赛 matchId=" + match.getMatchId()
                        + " 未收到 GameEndEvent，按异常结束处理（" + abnormalReason + "）");
                markAbnormal(match, endReason, abnormalReason);
                captureArenaCounters(match, arena);
                match.computePlacements();
                finalizeMatch(match, true);
            }
        }, Math.max(1L, delayTicks));
        fallbackTasks.put(match.getMatchId(), task);
    }

    private void markAbnormal(Match match, String endReason, String abnormalReason) {
        match.setRecording(false);
        match.setEndCaptured(true);
        match.setAbnormal(true);
        match.setAbnormalReason(abnormalReason);
        match.setEndReason(endReason);
        if (match.getEndedAt() == null) {
            match.setEndedAt(Instant.now());
        }
    }

    private void cancelFallback(UUID matchId) {
        BukkitTask task = fallbackTasks.remove(matchId);
        if (task != null) {
            task.cancel();
        }
    }

    private void writePartialSnapshot(final Match match) {
        if (match == null || match.isFinalized()) {
            return;
        }
        final String matchId = match.getMatchId().toString();
        final String json;
        try {
            json = PayloadBuilder.buildCrashSnapshot(match, Instant.now());
        } catch (RuntimeException e) {
            plugin.getLogger().warning("[XBedSync] 生成比赛快照失败 matchId=" + matchId + ": " + e);
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                try {
                    queue.writePartial(matchId, json);
                    debug("已写入崩溃恢复快照 matchId=" + matchId);
                } catch (IOException e) {
                    plugin.getLogger().warning("[XBedSync] 写入比赛快照失败 matchId=" + matchId + ": " + e.getMessage());
                }
            }
        });
    }

    private void finalizeMatch(final Match match, boolean async) {
        if (match == null || match.isFinalized()) {
            return;
        }
        match.setFinalized(true);
        if (match.getEndedAt() == null) {
            match.setEndedAt(Instant.now());
        }
        activeMatches.remove(match.getArenaName());
        arenaByMatch.remove(match.getMatchId());
        cancelFallback(match.getMatchId());

        final String matchId = match.getMatchId().toString();
        final String json;
        try {
            json = PayloadBuilder.build(match);
        } catch (RuntimeException e) {
            plugin.getLogger().severe("[XBedSync] 序列化比赛数据失败 matchId=" + matchId + ": " + e);
            return;
        }
        Runnable writeTask = new Runnable() {
            @Override
            public void run() {
                try {
                    queue.writePending(matchId, json);
                    queue.deletePartial(matchId);
                    plugin.getLogger().info("[XBedSync] 比赛数据已写入本地队列 matchId=" + matchId
                            + " abnormal=" + match.isAbnormal()
                            + " players=" + match.getPlayerCount()
                            + " events=" + match.getEvents().size());
                } catch (IOException e) {
                    plugin.getLogger().severe("[XBedSync] 写入本地队列失败 matchId=" + matchId
                            + ": " + e.getMessage() + "（比赛数据仅存在于内存，可能丢失，请检查磁盘）");
                }
            }
        };
        if (async && plugin.isEnabled()) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, writeTask);
        } else {
            writeTask.run();
        }
    }

    /**
     * 用 BedWars1058 的单场计数器校准事件累计值（仅在核心计数更大时采用）。
     */
    private void captureArenaCounters(Match match, IArena arena) {
        if (arena == null || !config.isPreferArenaCounters()) {
            return;
        }
        for (MatchPlayer stats : match.getPlayers()) {
            Player player = Bukkit.getPlayer(stats.getUuid());
            if (player == null || !player.isOnline()) {
                continue;
            }
            try {
                int kills = arena.getPlayerKills(player, false);
                int finalKills = arena.getPlayerKills(player, true);
                int deaths = arena.getPlayerDeaths(player, false);
                int finalDeaths = arena.getPlayerDeaths(player, true);
                int beds = arena.getPlayerBedsDestroyed(player);
                if (config.isDebug() && (kills != stats.getKills() || finalKills != stats.getFinalKills()
                        || deaths != stats.getDeaths() || finalDeaths != stats.getFinalDeaths()
                        || beds != stats.getBedsBroken())) {
                    debug("统计口径差异 " + stats.getGameId()
                            + " kills 事件=" + stats.getKills() + " 核心=" + kills
                            + ", finalKills 事件=" + stats.getFinalKills() + " 核心=" + finalKills
                            + ", deaths 事件=" + stats.getDeaths() + " 核心=" + deaths
                            + ", finalDeaths 事件=" + stats.getFinalDeaths() + " 核心=" + finalDeaths
                            + ", beds 事件=" + stats.getBedsBroken() + " 核心=" + beds);
                }
                stats.raiseKills(kills);
                stats.raiseFinalKills(finalKills);
                stats.raiseDeaths(deaths);
                stats.raiseFinalDeaths(finalDeaths);
                stats.raiseBedsBroken(beds);
            } catch (RuntimeException e) {
                debug("读取核心单场统计失败（player=" + player.getName() + "）: " + e);
            }
        }
    }

    private Match active(IArena arena) {
        if (arena == null || arena.getArenaName() == null) {
            return null;
        }
        return activeMatches.get(arena.getArenaName());
    }

    private MatchPlayer ensurePlayer(Match match, IArena arena, Player player) {
        if (match == null || player == null) {
            return null;
        }
        MatchPlayer stats = match.getPlayer(player.getUniqueId());
        if (stats == null) {
            stats = match.addPlayer(player.getUniqueId(), player.getName(),
                    arena == null ? null : teamName(arena.getTeam(player)));
        } else if (stats.getTeam() == null && arena != null) {
            stats.setTeam(teamName(arena.getTeam(player)));
        }
        return stats;
    }

    /**
     * 解析队伍显示名。优先 BedWars1058 语言文件中的本地化名称（如「红队」），
     * 缺失时回退到配置中的队伍键名（如 Red）。
     */
    private String teamName(ITeam team) {
        if (team == null) {
            return null;
        }
        String resolved = null;
        try {
            Language language = Language.getDefaultLanguage();
            if (language != null) {
                resolved = team.getDisplayName(language);
            }
        } catch (Throwable ignored) {
            // 语言模块异常不应影响比赛数据采集
        }
        resolved = clean(resolved);
        if (resolved != null && !looksLikeMissingLanguageKey(resolved)) {
            return resolved;
        }
        String fallback = clean(team.getName());
        if (fallback == null || looksLikeMissingLanguageKey(fallback)) {
            return null;
        }
        return fallback;
    }

    private String displayName(IArena arena) {
        String display = clean(arena.getDisplayName());
        return display != null ? display : clean(arena.getArenaName());
    }

    private String mode(String group) {
        String cleaned = clean(group);
        return cleaned == null ? null : cleaned;
    }

    private static boolean looksLikeMissingLanguageKey(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.startsWith("team-name-") || lower.startsWith("display-group-") || value.indexOf('{') >= 0;
    }

    private static String clean(String raw) {
        if (raw == null) {
            return null;
        }
        String stripped = ChatColor.stripColor(raw);
        if (stripped == null) {
            return null;
        }
        stripped = stripped.trim();
        return stripped.isEmpty() ? null : stripped;
    }

    private void debug(String message) {
        if (config.isDebug()) {
            plugin.getLogger().info("[XBedSync][debug] " + message);
        }
    }
}
