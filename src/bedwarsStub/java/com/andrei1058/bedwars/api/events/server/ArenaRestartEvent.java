package com.andrei1058.bedwars.api.events.server;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * 编译存根：ArenaRestartEvent。
 */
public class ArenaRestartEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    public ArenaRestartEvent(String arenaName, String worldName) {
    }

    public String getArenaName() {
        throw new UnsupportedOperationException("compile-only stub");
    }

    public String getWorldName() {
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
