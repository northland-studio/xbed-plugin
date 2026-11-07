package cn.xuanjian.xbedsync.match;

import java.time.Instant;

import cn.xuanjian.xbedsync.util.TimeUtil;

/**
 * 比赛事件时间线中的一条记录（对应上报契约 events[]）。
 *
 * <p>字段说明：</p>
 * <ul>
 *   <li>actor：动作发起者（玩家名或队伍显示名）</li>
 *   <li>target：动作目标（玩家名或队伍显示名，没有则为 null）</li>
 *   <li>team：actor 所属队伍显示名（没有则为 null）</li>
 * </ul>
 */
public final class MatchEvent {

    public static final String TYPE_KILL = "kill";
    public static final String TYPE_DEATH = "death";
    public static final String TYPE_BED_BREAK = "bed_break";
    public static final String TYPE_JOIN = "join";
    public static final String TYPE_LEAVE = "leave";
    public static final String TYPE_REJOIN = "rejoin";
    public static final String TYPE_TEAM_ELIMINATED = "team_eliminated";

    private final String type;
    private final Instant at;
    private final String actor;
    private final String target;
    private final String team;

    public MatchEvent(String type, Instant at, String actor, String target, String team) {
        this.type = type;
        this.at = at;
        this.actor = actor;
        this.target = target;
        this.team = team;
    }

    public String getType() {
        return type;
    }

    public Instant getAt() {
        return at;
    }

    public String getAtIso() {
        return TimeUtil.iso(at);
    }

    public String getActor() {
        return actor;
    }

    public String getTarget() {
        return target;
    }

    public String getTeam() {
        return team;
    }
}
