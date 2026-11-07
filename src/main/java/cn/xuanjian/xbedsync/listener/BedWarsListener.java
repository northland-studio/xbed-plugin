package cn.xuanjian.xbedsync.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import com.andrei1058.bedwars.api.events.gameplay.GameEndEvent;
import com.andrei1058.bedwars.api.events.gameplay.GameStateChangeEvent;
import com.andrei1058.bedwars.api.events.gameplay.TeamAssignEvent;
import com.andrei1058.bedwars.api.events.player.PlayerBedBreakEvent;
import com.andrei1058.bedwars.api.events.player.PlayerFirstSpawnEvent;
import com.andrei1058.bedwars.api.events.player.PlayerJoinArenaEvent;
import com.andrei1058.bedwars.api.events.player.PlayerKillEvent;
import com.andrei1058.bedwars.api.events.player.PlayerLeaveArenaEvent;
import com.andrei1058.bedwars.api.events.player.PlayerReJoinEvent;
import com.andrei1058.bedwars.api.events.server.ArenaDisableEvent;
import com.andrei1058.bedwars.api.events.server.ArenaRestartEvent;
import com.andrei1058.bedwars.api.events.team.TeamEliminatedEvent;

import cn.xuanjian.xbedsync.match.Match;
import cn.xuanjian.xbedsync.match.MatchManager;

/**
 * BedWars1058 事件监听器。
 *
 * <p>统一使用 {@link EventPriority#MONITOR}：只观测、不修改比赛行为，
 * 且保证在其它插件（统计/计分板等）处理之后再读取状态。</p>
 *
 * <p>所有事件类的真实签名均已用 javap 对 Bedwars-plugin-23.1.1.jar 核对，
 * 详见 docs/DATA-MAPPING.md。</p>
 */
public final class BedWarsListener implements Listener {

    private final MatchManager matchManager;

    public BedWarsListener(MatchManager matchManager) {
        this.matchManager = matchManager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onGameStateChange(GameStateChangeEvent event) {
        matchManager.onStateChange(event.getArena(), event.getOldState(), event.getNewState());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onGameEnd(GameEndEvent event) {
        matchManager.onGameEnd(event);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerKill(PlayerKillEvent event) {
        matchManager.onKill(event);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerBedBreak(PlayerBedBreakEvent event) {
        matchManager.onBedBreak(event);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onTeamEliminated(TeamEliminatedEvent event) {
        matchManager.onTeamEliminated(event);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoinArena(PlayerJoinArenaEvent event) {
        matchManager.onJoin(event);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerLeaveArena(PlayerLeaveArenaEvent event) {
        matchManager.onLeave(event);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerReJoin(PlayerReJoinEvent event) {
        matchManager.onRejoin(event);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerFirstSpawn(PlayerFirstSpawnEvent event) {
        matchManager.onFirstSpawn(event);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onTeamAssign(TeamAssignEvent event) {
        matchManager.onTeamAssign(event);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onArenaDisable(ArenaDisableEvent event) {
        matchManager.onArenaUnavailable(event.getArenaName(), Match.END_ARENA_DISABLED);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onArenaRestart(ArenaRestartEvent event) {
        matchManager.onArenaUnavailable(event.getArenaName(), Match.END_ARENA_RESTART);
    }
}
