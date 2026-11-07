package com.andrei1058.bedwars.api.events.player;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import com.andrei1058.bedwars.api.arena.IArena;
import com.andrei1058.bedwars.api.arena.team.ITeam;

/**
 * 编译存根：PlayerFirstSpawnEvent。
 */
public class PlayerFirstSpawnEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    public PlayerFirstSpawnEvent(Player player, IArena arena, ITeam team) {
    }

    public IArena getArena() {
        throw new UnsupportedOperationException("compile-only stub");
    }

    public ITeam getTeam() {
        throw new UnsupportedOperationException("compile-only stub");
    }

    public Player getPlayer() {
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
