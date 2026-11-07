package com.andrei1058.bedwars.api.events.team;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import com.andrei1058.bedwars.api.arena.IArena;
import com.andrei1058.bedwars.api.arena.team.ITeam;

/**
 * 编译存根：TeamEliminatedEvent。
 */
public class TeamEliminatedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    public TeamEliminatedEvent(IArena arena, ITeam eliminated) {
    }

    public IArena getArena() {
        throw new UnsupportedOperationException("compile-only stub");
    }

    public ITeam getTeam() {
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
