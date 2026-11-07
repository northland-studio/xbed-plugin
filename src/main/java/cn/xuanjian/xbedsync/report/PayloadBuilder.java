package cn.xuanjian.xbedsync.report;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import cn.xuanjian.xbedsync.match.Match;
import cn.xuanjian.xbedsync.match.MatchEvent;
import cn.xuanjian.xbedsync.match.MatchPlayer;
import cn.xuanjian.xbedsync.util.TimeUtil;

/**
 * 把 {@link Match} 序列化为 XBed 上报契约（schemaVersion=1）的 JSON 请求体。
 *
 * <p>契约见 docs/API.md（与网站仓库 xbed-website/docs/API.md「服务器上报（HMAC）」保持一致）。</p>
 *
 * <p>设计原则：无法可靠获得的数据输出 null，不补 0、不编造队伍名。</p>
 */
public final class PayloadBuilder {

    /** 当前上报契约版本。 */
    public static final int SCHEMA_VERSION = 1;

    private static final Comparator<MatchPlayer> PLAYER_ORDER = new Comparator<MatchPlayer>() {
        @Override
        public int compare(MatchPlayer a, MatchPlayer b) {
            Integer pa = a.getPlacement();
            Integer pb = b.getPlacement();
            if (pa != null && pb != null) {
                int cmp = pa.intValue() - pb.intValue();
                if (cmp != 0) {
                    return cmp;
                }
            } else if (pa != null) {
                return -1;
            } else if (pb != null) {
                return 1;
            }
            String ga = a.getGameId() == null ? "" : a.getGameId();
            String gb = b.getGameId() == null ? "" : b.getGameId();
            return ga.compareToIgnoreCase(gb);
        }
    };

    private PayloadBuilder() {
    }

    /**
     * 正常上报体。
     */
    public static String build(Match match) {
        return build(match, false, null, null);
    }

    /**
     * 进行中比赛的崩溃恢复快照：提前把 abnormal / server_crash 写入，
     * 服务器重启后由 QueueManager 直接把快照升级为待上报数据。
     */
    public static String buildCrashSnapshot(Match match, Instant snapshotAt) {
        return build(match, true, Match.END_SERVER_CRASH, snapshotAt);
    }

    private static String build(Match match, boolean forceAbnormal, String forcedEndReason, Instant forcedEndedAt) {
        List<MatchPlayer> playerList = new ArrayList<MatchPlayer>(match.getPlayers());
        Collections.sort(playerList, PLAYER_ORDER);

        List<MatchEvent> eventList = new ArrayList<MatchEvent>(match.getEvents());
        Collections.sort(eventList, new Comparator<MatchEvent>() {
            @Override
            public int compare(MatchEvent a, MatchEvent b) {
                if (a.getAt() == null && b.getAt() == null) {
                    return 0;
                }
                if (a.getAt() == null) {
                    return -1;
                }
                if (b.getAt() == null) {
                    return 1;
                }
                return a.getAt().compareTo(b.getAt());
            }
        });

        Instant endedAt = forcedEndedAt != null ? forcedEndedAt : match.getEndedAt();
        String endReason = forcedEndReason != null ? forcedEndReason : match.getEndReason();
        boolean abnormal = forceAbnormal || match.isAbnormal();
        String abnormalReason = forceAbnormal ? Match.END_SERVER_CRASH : match.getAbnormalReason();

        Json.Obj root = Json.object();
        root.put("schemaVersion", SCHEMA_VERSION);
        root.put("matchId", match.getMatchId() == null ? null : match.getMatchId().toString());
        root.put("arena", match.getArenaName());
        root.put("arenaDisplay", match.getArenaDisplay());
        root.put("mode", match.getMode());
        root.put("startedAt", TimeUtil.iso(match.getStartedAt()));
        root.put("endedAt", TimeUtil.iso(endedAt));
        root.put("endReason", endReason);
        root.put("winnerTeam", match.getWinnerTeam());
        root.put("isDraw", match.isDraw());
        root.put("abnormal", abnormal);
        root.put("abnormalReason", abnormalReason);

        Json.Arr players = Json.array();
        for (MatchPlayer player : playerList) {
            Json.Obj p = Json.object();
            p.put("uuid", player.getUuid() == null ? null : player.getUuid().toString());
            p.put("gameId", player.getGameId());
            p.put("team", player.getTeam());
            p.put("isWinner", player.isWinner());
            p.put("kills", player.getKills());
            p.put("finalKills", player.getFinalKills());
            p.put("deaths", player.getDeaths());
            p.put("finalDeaths", player.getFinalDeaths());
            p.put("bedsBroken", player.getBedsBroken());
            p.put("placement", player.getPlacement());
            players.addRaw(p.end());
        }
        root.putRaw("players", players.end());

        Json.Arr events = Json.array();
        for (MatchEvent event : eventList) {
            Json.Obj e = Json.object();
            e.put("type", event.getType());
            e.put("at", TimeUtil.iso(event.getAt()));
            e.put("actor", event.getActor());
            e.put("target", event.getTarget());
            e.put("team", event.getTeam());
            events.addRaw(e.end());
        }
        root.putRaw("events", events.end());

        return root.end();
    }
}
