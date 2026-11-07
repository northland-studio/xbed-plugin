package com.andrei1058.bedwars.api.events.player;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import com.andrei1058.bedwars.api.arena.IArena;

/**
 * 编译存根：PlayerReJoinEvent。
 */
public class PlayerReJoinEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    public PlayerReJoinEvent(Player player, IArena arena, int respawnTime) {
    }

    public IArena getArena() {
        throw new UnsupportedOperationException("compile-only stub");
    }

    public Player getPlayer() {
        throw new UnsupportedOperationException("compile-only stub");
    }

    public void setRespawnTime(int respawnTime) {
        throw new UnsupportedOperationException("compile-only stub");
    }

    public int getRespawnTime() {
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
