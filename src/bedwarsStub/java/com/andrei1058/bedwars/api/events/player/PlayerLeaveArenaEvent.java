package com.andrei1058.bedwars.api.events.player;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import com.andrei1058.bedwars.api.arena.IArena;

/**
 * 编译存根：PlayerLeaveArenaEvent。
 */
public class PlayerLeaveArenaEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    public PlayerLeaveArenaEvent(Player player, IArena arena, Player lastDamager) {
    }

    public PlayerLeaveArenaEvent(Player player, IArena arena) {
    }

    public Player getPlayer() {
        throw new UnsupportedOperationException("compile-only stub");
    }

    public IArena getArena() {
        throw new UnsupportedOperationException("compile-only stub");
    }

    public boolean isSpectator() {
        throw new UnsupportedOperationException("compile-only stub");
    }

    public Player getLastDamager() {
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
