package com.andrei1058.bedwars.api.arena;

import java.util.List;

import org.bukkit.entity.Player;

import com.andrei1058.bedwars.api.arena.team.ITeam;

/**
 * 编译存根：com.andrei1058.bedwars.api.arena.IArena（仅 XBedSync 使用到的方法）。
 */
public interface IArena {

    String getArenaName();

    String getDisplayName();

    String getGroup();

    GameState getStatus();

    List<Player> getPlayers();

    List<ITeam> getTeams();

    ITeam getTeam(Player player);

    int getPlayerKills(Player player, boolean finalKills);

    int getPlayerDeaths(Player player, boolean finalDeaths);

    int getPlayerBedsDestroyed(Player player);
}
