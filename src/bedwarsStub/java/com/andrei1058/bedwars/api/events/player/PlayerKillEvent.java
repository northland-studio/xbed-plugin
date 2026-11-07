package com.andrei1058.bedwars.api.events.player;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import com.andrei1058.bedwars.api.arena.IArena;

/**
 * 编译存根：PlayerKillEvent + 嵌套 PlayerKillCause。
 */
public class PlayerKillEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    public PlayerKillEvent(IArena arena, Player victim, Player killer,
                           java.util.function.Function<Player, String> message, PlayerKillCause cause) {
    }

    public Player getKiller() {
        throw new UnsupportedOperationException("compile-only stub");
    }

    public PlayerKillCause getCause() {
        throw new UnsupportedOperationException("compile-only stub");
    }

    public IArena getArena() {
        throw new UnsupportedOperationException("compile-only stub");
    }

    public Player getVictim() {
        throw new UnsupportedOperationException("compile-only stub");
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    /**
     * 编译存根：击杀原因枚举。
     */
    public enum PlayerKillCause {
        UNKNOWN,
        UNKNOWN_FINAL_KILL,
        PVP,
        PVP_FINAL_KILL,
        VOID,
        VOID_FINAL_KILL,
        PLAYER_DISCONNECT,
        PLAYER_DISCONNECT_FINAL;

        public boolean isFinalKill() {
            throw new UnsupportedOperationException("compile-only stub");
        }

        public boolean isDespawnable() {
            throw new UnsupportedOperationException("compile-only stub");
        }

        public boolean isPvpLogOut() {
            throw new UnsupportedOperationException("compile-only stub");
        }
    }
}
