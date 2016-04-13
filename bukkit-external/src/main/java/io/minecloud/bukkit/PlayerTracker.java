/*
 * Copyright (c) 2015, Mazen Kotb <email@mazenmc.io>
 *
 * Permission to use, copy, modify, and/or distribute this software for any
 * purpose with or without fee is hereby granted, provided that the above
 * copyright notice and this permission notice appear in all copies.
 *
 * THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
 * WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR
 * ANY SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
 * WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
 * ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF
 * OR IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.
 */
package io.minecloud.bukkit;

import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import io.minecloud.models.external.ExternalServer;
import io.minecloud.models.player.PlayerData;

public class PlayerTracker implements Listener {
    private MineCloudPlugin plugin;

    public PlayerTracker(MineCloudPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void playerJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            ExternalServer server = plugin.server();
            List<PlayerData> onlinePlayers = server.onlinePlayers();
            Player player = event.getPlayer();
            PlayerData data = new PlayerData();

            data.setHealth(player.getHealth());
            data.setMaxHealth(player.getMaxHealth());
            data.setName(player.getName());
            data.setId(player.getUniqueId().toString());

            onlinePlayers.add(data);

            server.setOnlinePlayers(onlinePlayers);
            plugin.updatePlayers(server);
            plugin.mongo().repositoryBy(ExternalServer.class).save(server);
        });
    }

    @EventHandler
    public void playerQuit(PlayerQuitEvent event) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            ExternalServer server = plugin.server();

            server.removePlayer(event.getPlayer().getUniqueId());
            plugin.updatePlayers(server);
            plugin.mongo().repositoryBy(ExternalServer.class).save(server);
        });
    }
}
