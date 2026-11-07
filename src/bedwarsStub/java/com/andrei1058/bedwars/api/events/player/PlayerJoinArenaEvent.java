package com.andrei1058.bedwars.api.events.player;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import com.andrei1058.bedwars.api.arena.IArena;

/**
 * 编译存根：PlayerJoinArenaEvent。
 */
public class PlayerJoinArenaEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    public PlayerJoinArenaEvent(IArena arena, Player player, boolean spectator) {
    }

    public IArena getArena() {
        throw new UnsupportedOperationException("compile-only stub");
    }

    public Player getPlayer() {
        throw new UnsupportedOperationException("compile-only stub");
    }

    public boolean isSpectator() {
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
