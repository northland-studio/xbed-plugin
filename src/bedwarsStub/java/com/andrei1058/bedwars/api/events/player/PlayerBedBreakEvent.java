package com.andrei1058.bedwars.api.events.player;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import com.andrei1058.bedwars.api.arena.IArena;
import com.andrei1058.bedwars.api.arena.team.ITeam;

/**
 * 编译存根：PlayerBedBreakEvent。
 */
public class PlayerBedBreakEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    public PlayerBedBreakEvent(Player player, ITeam playerTeam, ITeam victimTeam, IArena arena,
                               java.util.function.Function<Player, String> message,
                               java.util.function.Function<Player, String> title,
                               java.util.function.Function<Player, String> subTitle) {
    }

    public ITeam getPlayerTeam() {
        throw new UnsupportedOperationException("compile-only stub");
    }

    public ITeam getVictimTeam() {
        throw new UnsupportedOperationException("compile-only stub");
    }

    public IArena getArena() {
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
