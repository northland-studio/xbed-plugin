package com.andrei1058.bedwars.api.events.gameplay;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import com.andrei1058.bedwars.api.arena.IArena;
import com.andrei1058.bedwars.api.arena.team.ITeam;

/**
 * 编译存根：TeamAssignEvent。
 */
public class TeamAssignEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    public TeamAssignEvent(Player player, ITeam team, IArena arena) {
    }

    public ITeam getTeam() {
        throw new UnsupportedOperationException("compile-only stub");
    }

    public Player getPlayer() {
        throw new UnsupportedOperationException("compile-only stub");
    }

    public IArena getArena() {
        throw new UnsupportedOperationException("compile-only stub");
    }

    @Override
    public boolean isCancelled() {
        throw new UnsupportedOperationException("compile-only stub");
    }

    @Override
    public void setCancelled(boolean cancel) {
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
