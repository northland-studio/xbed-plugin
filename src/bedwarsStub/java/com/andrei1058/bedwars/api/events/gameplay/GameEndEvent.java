package com.andrei1058.bedwars.api.events.gameplay;

import java.util.List;
import java.util.UUID;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import com.andrei1058.bedwars.api.arena.IArena;
import com.andrei1058.bedwars.api.arena.team.ITeam;

/**
 * 编译存根：GameEndEvent（23.1.1 中不提供结束原因字段）。
 */
public class GameEndEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    public GameEndEvent(IArena arena, List<UUID> winners, List<UUID> losers, ITeam teamWinner,
                        List<UUID> aliveWinners) {
    }

    public List<UUID> getWinners() {
        throw new UnsupportedOperationException("compile-only stub");
    }

    public ITeam getTeamWinner() {
        throw new UnsupportedOperationException("compile-only stub");
    }

    public List<UUID> getLosers() {
        throw new UnsupportedOperationException("compile-only stub");
    }

    public IArena getArena() {
        throw new UnsupportedOperationException("compile-only stub");
    }

    public List<UUID> getAliveWinners() {
        throw new UnsupportedOperationException("compile-only stub");
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
