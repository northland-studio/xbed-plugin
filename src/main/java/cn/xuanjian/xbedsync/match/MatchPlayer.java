package cn.xuanjian.xbedsync.match;

import java.util.UUID;

/**
 * 单场比赛中一名玩家的统计数据。
 *
 * <p>所有计数均为「本场」数据：来自监听 BedWars1058 事件累计，
 * 比赛结束时可用 {@code IArena} 的单场计数器校准（见 docs/DATA-MAPPING.md）。</p>
 */
public final class MatchPlayer {

    private final UUID uuid;
    private final String gameId;
    private String team;
    private int kills;
    private int finalKills;
    private int deaths;
    private int finalDeaths;
    private int bedsBroken;
    private boolean winner;
    private Integer placement;

    public MatchPlayer(UUID uuid, String gameId, String team) {
        this.uuid = uuid;
        this.gameId = gameId;
        this.team = team;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getGameId() {
        return gameId;
    }

    public String getTeam() {
        return team;
    }

    public void setTeam(String team) {
        this.team = team;
    }

    public int getKills() {
        return kills;
    }

    public void addKill() {
        this.kills++;
    }

    /** 用核心插件单场计数器校准（仅当计数器值更大时采用，避免玩家下线导致丢失）。 */
    public void raiseKills(int value) {
        if (value > this.kills) {
            this.kills = value;
        }
    }

    public int getFinalKills() {
        return finalKills;
    }

    public void addFinalKill() {
        this.finalKills++;
    }

    public void raiseFinalKills(int value) {
        if (value > this.finalKills) {
            this.finalKills = value;
        }
    }

    public int getDeaths() {
        return deaths;
    }

    public void addDeath() {
        this.deaths++;
    }

    public void raiseDeaths(int value) {
        if (value > this.deaths) {
            this.deaths = value;
        }
    }

    public int getFinalDeaths() {
        return finalDeaths;
    }

    public void addFinalDeath() {
        this.finalDeaths++;
    }

    public void raiseFinalDeaths(int value) {
        if (value > this.finalDeaths) {
            this.finalDeaths = value;
        }
    }

    public int getBedsBroken() {
        return bedsBroken;
    }

    public void addBedBreak() {
        this.bedsBroken++;
    }

    public void raiseBedsBroken(int value) {
        if (value > this.bedsBroken) {
            this.bedsBroken = value;
        }
    }

    public boolean isWinner() {
        return winner;
    }

    public void setWinner(boolean winner) {
        this.winner = winner;
    }

    /** null = 无法可靠确定（不伪造）；1 = 冠军。 */
    public Integer getPlacement() {
        return placement;
    }

    public void setPlacement(Integer placement) {
        this.placement = placement;
    }
}
