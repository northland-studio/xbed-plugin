package com.andrei1058.bedwars.api.events.gameplay;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import com.andrei1058.bedwars.api.arena.GameState;
import com.andrei1058.bedwars.api.arena.IArena;

/**
 * 编译存根：GameStateChangeEvent。
 */
public class GameStateChangeEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    public GameStateChangeEvent(IArena arena, GameState oldState, GameState newState) {
    }

    public IArena getArena() {
        throw new UnsupportedOperationException("compile-only stub");
    }

    public GameState getOldState() {
        throw new UnsupportedOperationException("compile-only stub");
    }

    public GameState getNewState() {
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
