package cn.xuanjian.xbedsync.match;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 一场 BedWars 比赛的聚合数据。
 *
 * <p>本类刻意保持「纯 Java」：不引用任何 Bukkit / BedWars1058 类型，便于单元测试。
 * 所有字段的可空性即上报契约中的可空性：无法可靠获取的字段保持 null，绝不伪造。</p>
 *
 * <p>线程模型：比赛数据只在主线程（Bukkit 事件线程）修改；
 * 序列化与文件写由主线程生成字符串后交给异步任务执行。</p>
 */
public final class Match {

    /** 正常结束：最后一支队伍存活（BedWars1058 默认胜负机制）。 */
    public static final String END_TEAM_ELIMINATED = "team_eliminated";
    /** 异常结束：竞技场重启 / 状态被打断。 */
    public static final String END_ARENA_RESTART = "arena_restart";
    /** 异常结束：竞技场被禁用。 */
    public static final String END_ARENA_DISABLED = "arena_disabled";
    /** 异常结束：服务器正常关服（插件 onDisable 时比赛仍在进行）。 */
    public static final String END_SERVER_SHUTDOWN = "server_shutdown";
    /** 异常结束：服务器崩溃（由本地快照恢复）。 */
    public static final String END_SERVER_CRASH = "server_crash";
    /** 异常结束：结束但无法确定胜者（数据冲突/生命周期不完整）。 */
    public static final String END_NO_WINNER = "no_winner";

    private final UUID matchId;
    private final String arenaName;
    private final String arenaDisplay;
    private final String mode;
    private final int teamCount;
    private final Instant startedAt;

    private final Map<UUID, MatchPlayer> players = new LinkedHashMap<UUID, MatchPlayer>();
    private final List<MatchEvent> events = new ArrayList<MatchEvent>();
    private final Set<UUID> winnerUuids = new HashSet<UUID>();
    private final List<String> eliminationOrder = new ArrayList<String>();

    private Instant endedAt;
    private String endReason;
    private String winnerTeam;
    private boolean draw;
    private boolean abnormal;
    private String abnormalReason;

    /** 是否还在记录事件/统计；结束流程开始后置 false，避免把赛后的离场事件混进来。 */
    private boolean recording = true;
    /** 是否已经收到 GameEndEvent。 */
    private boolean endCaptured;
    /** 是否已经写出最终 JSON。 */
    private boolean finalized;

    public Match(UUID matchId, String arenaName, String arenaDisplay, String mode,
                 Instant startedAt, int teamCount) {
        this.matchId = matchId;
        this.arenaName = arenaName;
        this.arenaDisplay = arenaDisplay;
        this.mode = mode;
        this.startedAt = startedAt;
        this.teamCount = teamCount;
    }

    public UUID getMatchId() {
        return matchId;
    }

    public String getArenaName() {
        return arenaName;
    }

    public String getArenaDisplay() {
        return arenaDisplay;
    }

    public String getMode() {
        return mode;
    }

    public int getTeamCount() {
        return teamCount;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public void setEndedAt(Instant endedAt) {
        this.endedAt = endedAt;
    }

    public String getEndReason() {
        return endReason;
    }

    public void setEndReason(String endReason) {
        this.endReason = endReason;
    }

    public String getWinnerTeam() {
        return winnerTeam;
    }

    public void setWinnerTeam(String winnerTeam) {
        this.winnerTeam = winnerTeam;
    }

    public boolean isDraw() {
        return draw;
    }

    public void setDraw(boolean draw) {
        this.draw = draw;
    }

    public boolean isAbnormal() {
        return abnormal;
    }

    public void setAbnormal(boolean abnormal) {
        this.abnormal = abnormal;
    }

    public String getAbnormalReason() {
        return abnormalReason;
    }

    public void setAbnormalReason(String abnormalReason) {
        this.abnormalReason = abnormalReason;
    }

    public boolean isRecording() {
        return recording;
    }

    public void setRecording(boolean recording) {
        this.recording = recording;
    }

    public boolean isEndCaptured() {
        return endCaptured;
    }

    public void setEndCaptured(boolean endCaptured) {
        this.endCaptured = endCaptured;
    }

    public boolean isFinalized() {
        return finalized;
    }

    public void setFinalized(boolean finalized) {
        this.finalized = finalized;
    }

    public MatchPlayer getPlayer(UUID uuid) {
        return players.get(uuid);
    }

    public boolean hasPlayer(UUID uuid) {
        return players.containsKey(uuid);
    }

    /**
     * 加入玩家（已存在则返回已有记录，不覆盖已有统计）。
     */
    public MatchPlayer addPlayer(UUID uuid, String gameId, String team) {
        MatchPlayer existing = players.get(uuid);
        if (existing != null) {
            if ((existing.getTeam() == null || existing.getTeam().isEmpty()) && team != null) {
                existing.setTeam(team);
            }
            return existing;
        }
        MatchPlayer player = new MatchPlayer(uuid, gameId, team);
        players.put(uuid, player);
        return player;
    }

    public Collection<MatchPlayer> getPlayers() {
        return players.values();
    }

    public int getPlayerCount() {
        return players.size();
    }

    public void addEvent(MatchEvent event) {
        if (event != null) {
            events.add(event);
        }
    }

    public List<MatchEvent> getEvents() {
        return events;
    }

    public void addWinnerUuid(UUID uuid) {
        if (uuid != null) {
            winnerUuids.add(uuid);
        }
    }

    public Set<UUID> getWinnerUuids() {
        return winnerUuids;
    }

    public void addEliminatedTeam(String teamName) {
        if (teamName != null && !teamName.isEmpty()) {
            eliminationOrder.add(teamName);
        }
    }

    public List<String> getEliminationOrder() {
        return eliminationOrder;
    }

    /**
     * 根据「胜者 + 队伍淘汰顺序」计算玩家名次。
     *
     * <p>BedWars1058 只提供队伍级别的淘汰顺序，玩家名次 = 其队伍名次。
     * 无法确定的记为 null（不伪造）。</p>
     */
    public void computePlacements() {
        Map<String, Integer> teamPlacement = new HashMap<String, Integer>();
        if (winnerTeam != null) {
            teamPlacement.put(winnerTeam, Integer.valueOf(1));
        }
        int rank = teamCount > 0 ? teamCount : eliminationOrder.size() + 1;
        for (String team : eliminationOrder) {
            if (team == null || teamPlacement.containsKey(team)) {
                continue;
            }
            teamPlacement.put(team, Integer.valueOf(Math.max(2, rank)));
            rank--;
        }
        for (MatchPlayer player : players.values()) {
            boolean isWinner = winnerUuids.contains(player.getUuid());
            if (!isWinner && winnerUuids.isEmpty() && winnerTeam != null
                    && winnerTeam.equals(player.getTeam())) {
                // GameEndEvent#getWinners() 为空时，用胜者队伍兜底（仍来自事件数据，不伪造）
                isWinner = true;
            }
            player.setWinner(isWinner);
            if (isWinner) {
                player.setPlacement(Integer.valueOf(1));
                continue;
            }
            Integer placement = player.getTeam() == null ? null : teamPlacement.get(player.getTeam());
            player.setPlacement(placement);
        }
    }
}
