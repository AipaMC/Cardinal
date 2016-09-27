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
package io.minecloud.bungee;

import io.minecloud.models.bungee.Bungee;
import io.minecloud.models.external.ExternalServer;
import io.minecloud.models.external.ExternalServerRepository;
import io.minecloud.models.server.Server;
import io.minecloud.models.server.ServerRepository;
import lombok.RequiredArgsConstructor;
import net.md_5.bungee.api.AbstractReconnectHandler;
import net.md_5.bungee.api.ServerPing;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.event.ProxyPingEvent;
import net.md_5.bungee.api.event.ServerKickEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

@RequiredArgsConstructor
public class MineCloudListener implements Listener {
    
    private final MineCloudPlugin plugin;
    
    private final ConcurrentHashMap<String, String> motd = new ConcurrentHashMap<>();
    private long lastUpdated = 0;
    private int onlinePlayers = -1;
    private int maxOnline = -1;

    @EventHandler
    public void onPing(ProxyPingEvent event) {
        ServerPing ping = event.getResponse();

        if (ping == null) {
            ping = new ServerPing();
        }
        
        ServerInfo forced = AbstractReconnectHandler.getForcedHost(event.getConnection());
        
        if (onlinePlayers == -1 || (System.currentTimeMillis() - lastUpdated) >= 5000L) {
            Bungee bungee = plugin.bungee();

            if (bungee == null)
                return;

            ServerRepository repository = plugin.mongo.repositoryBy(Server.class);
            Collection<Server> servers = repository.find(repository.createQuery().field("network").equal(bungee.network()))
                    .asList();
            
            ExternalServerRepository externalRepo = plugin.mongo.repositoryBy(ExternalServer.class);
            Collection<ExternalServer> externalServers = externalRepo.find(externalRepo.createQuery()
                    .field("network").equal(bungee.network()))
                    .asList();

            int online = 0;
            for (Server server : servers) {
                online += server.onlinePlayers().size();
            }
            
            for (ExternalServer server : externalServers) {
                online += server.onlinePlayers().size();
                //Cache MOTDs
                motd.put(server.name(), server.type().motd());
            }

            onlinePlayers = online;
            maxOnline = bungee.network().pingCap();
            lastUpdated = System.currentTimeMillis();
        }

        ping.setPlayers(new ServerPing.Players(maxOnline, onlinePlayers, ping.getPlayers().getSample()));
        //MOTD
        if (forced != null && motd.containsKey(forced.getName())) {
            ping.setDescription(motd.get(forced.getName()));
        }

        event.setResponse(ping);
    }

    @EventHandler
    public void serverKick(ServerKickEvent event) {
        String reason = TextComponent.toLegacyText(event.getKickReasonComponent());

        if (reason.contains("kick") || reason.contains("ban") || reason.contains("pack")) {
            event.getPlayer().disconnect(event.getKickReasonComponent());
            event.setCancelled(false);
            return;
        }

        ServerInfo server = plugin.getProxy().getReconnectHandler().getServer(event.getPlayer());

        if (server != null) {
            BaseComponent[] message = TextComponent.fromLegacyText(MineCloudPlugin.PREFIX
                    + TextComponent.toLegacyText(event.getKickReasonComponent()));
            event.getPlayer().sendMessage(message);
        } else {
            return;
        }

        //The method that calls this event (see ServerConnector) already sends the player
        //to the canceled server when the event is cancelled. No need to do it twice
        //event.getPlayer().connect(server);
        event.setCancelServer(server);
        event.setCancelled(true);
    }
}
