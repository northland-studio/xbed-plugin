package cn.xuanjian.xbedsync.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import cn.xuanjian.xbedsync.match.Match;
import cn.xuanjian.xbedsync.match.MatchEvent;
import cn.xuanjian.xbedsync.match.MatchPlayer;

class PayloadBuilderTest {

    private static final UUID MATCH_ID = UUID.fromString("3f2c8a1e-6c2a-4a1e-9f3e-1a2b3c4d5e6f");
    private static final UUID STEVE_ID = UUID.fromString("aaaaaaaa-1111-2222-3333-444444444444");
    private static final UUID ALEX_ID = UUID.fromString("bbbbbbbb-1111-2222-3333-444444444444");
    private static final Instant START = Instant.parse("2026-11-08T12:00:00Z");
    private static final Instant END = Instant.parse("2026-11-08T12:18:00Z");

    private static Match sampleMatch() {
        Match match = new Match(MATCH_ID, "castle", "Castle", "4v4v4v4", START, 4);
        MatchPlayer steve = match.addPlayer(STEVE_ID, "Steve", "红队");
        MatchPlayer alex = match.addPlayer(ALEX_ID, "Alex", "蓝队");

        steve.addKill();
        steve.addKill();
        steve.addFinalKill();
        steve.addDeath();
        steve.addFinalDeath();
        steve.addBedBreak();
        steve.raiseKills(8);
        steve.raiseFinalKills(2);
        steve.raiseDeaths(3);
        steve.raiseFinalDeaths(1);

        alex.addKill();
        alex.addKill();
        alex.addDeath();
        alex.addDeath();
        alex.addFinalDeath();
        alex.raiseKills(2);
        alex.raiseDeaths(5);
        alex.raiseFinalDeaths(2);

        match.addEliminatedTeam("蓝队");
        match.addEliminatedTeam("绿队");
        match.addEliminatedTeam("黄队");
        match.addWinnerUuid(STEVE_ID);
        match.setWinnerTeam("红队");
        match.setEndedAt(END);
        match.setEndReason(Match.END_TEAM_ELIMINATED);
        match.addEvent(new MatchEvent(MatchEvent.TYPE_KILL, Instant.parse("2026-11-08T12:05:00Z"),
                "Steve", "Alex", "红队"));
        match.computePlacements();
        return match;
    }

    @Test
    void buildsContractPayload() {
        String json = PayloadBuilder.build(sampleMatch());
        String expected = "{\"schemaVersion\":1,"
                + "\"matchId\":\"3f2c8a1e-6c2a-4a1e-9f3e-1a2b3c4d5e6f\","
                + "\"arena\":\"castle\",\"arenaDisplay\":\"Castle\",\"mode\":\"4v4v4v4\","
                + "\"startedAt\":\"2026-11-08T12:00:00.000Z\","
                + "\"endedAt\":\"2026-11-08T12:18:00.000Z\","
                + "\"endReason\":\"team_eliminated\",\"winnerTeam\":\"红队\","
                + "\"isDraw\":false,\"abnormal\":false,\"abnormalReason\":null,"
                + "\"players\":["
                + "{\"uuid\":\"aaaaaaaa-1111-2222-3333-444444444444\",\"gameId\":\"Steve\",\"team\":\"红队\","
                + "\"isWinner\":true,\"kills\":8,\"finalKills\":2,\"deaths\":3,\"finalDeaths\":1,"
                + "\"bedsBroken\":1,\"placement\":1},"
                + "{\"uuid\":\"bbbbbbbb-1111-2222-3333-444444444444\",\"gameId\":\"Alex\",\"team\":\"蓝队\","
                + "\"isWinner\":false,\"kills\":2,\"finalKills\":0,\"deaths\":5,\"finalDeaths\":2,"
                + "\"bedsBroken\":0,\"placement\":4}],"
                + "\"events\":[{\"type\":\"kill\",\"at\":\"2026-11-08T12:05:00.000Z\","
                + "\"actor\":\"Steve\",\"target\":\"Alex\",\"team\":\"红队\"}]}";
        assertEquals(expected, json);
    }

    @Test
    void crashSnapshotIsMarkedAbnormal() {
        Instant snapshotAt = Instant.parse("2026-11-08T12:10:00Z");
        String json = PayloadBuilder.buildCrashSnapshot(sampleMatch(), snapshotAt);
        assertTrue(json.contains("\"abnormal\":true"));
        assertTrue(json.contains("\"abnormalReason\":\"server_crash\""));
        assertTrue(json.contains("\"endReason\":\"server_crash\""));
        assertTrue(json.contains("\"endedAt\":\"2026-11-08T12:10:00.000Z\""));
    }

    @Test
    void unknownPlacementStaysNull() {
        Match match = new Match(MATCH_ID, "castle", "Castle", "4v4v4v4", START, 4);
        match.addPlayer(ALEX_ID, "Alex", "蓝队");
        match.setEndedAt(END);
        match.setEndReason(Match.END_ARENA_RESTART);
        match.setAbnormal(true);
        match.setAbnormalReason(Match.END_ARENA_RESTART);
        match.computePlacements();
        String json = PayloadBuilder.build(match);
        assertTrue(json.contains("\"placement\":null"));
        assertTrue(json.contains("\"winnerTeam\":null"));
    }
}
