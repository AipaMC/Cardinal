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
package io.minecloud;

import java.util.List;
import java.util.UUID;

import io.minecloud.models.player.PlayerData;
import io.minecloud.models.server.Server;
import io.minecloud.models.server.ServerRepository;

/**
 * Adds various functions that are useful for other
 * plugins in interacting with Cardinal
 * @author Aipa
 */
public class CardinalAPI {
    
    /**
     * Gets the server instance a player is currently on
     * @param username Player's username
     * @return Server the player is on or null if offline
     */
    public static Server getServerPlayerIsOn(String username) {
        ServerRepository repository = MineCloud.instance().mongo().repositoryBy(Server.class);
        List<Server> servers = repository.find(repository.createQuery()
                .field("port").notEqual(-1)
                .field("ramUsage").notEqual(-1))
                .asList();
        for (Server server : servers) {
            if (server.playerBy(username) != null) {
                return server;
            }
        }
        return null;
    }
    
    /**
     * Gets an instance of an online player
     * @param username Player's username
     * @return PlayerData that represents the online player or null if offline
     */
    public static PlayerData getPlayer(String username) {
        ServerRepository repository = MineCloud.instance().mongo().repositoryBy(Server.class);
        List<Server> servers = repository.find(repository.createQuery()
                .field("port").notEqual(-1)
                .field("ramUsage").notEqual(-1))
                .asList();
        for (Server server : servers) {
            PlayerData data = server.playerBy(username);
            if (data != null) {
                return data;
            }
        }
        return null;
    }
    
    /**
     * Gets an instance of an online player
     * @param uuid Player's UUID
     * @return PlayerData that represents the online player or null if offline
     */
    public static PlayerData getPlayer(UUID uuid) {
        ServerRepository repository = MineCloud.instance().mongo().repositoryBy(Server.class);
        List<Server> servers = repository.find(repository.createQuery()
                .field("port").notEqual(-1)
                .field("ramUsage").notEqual(-1))
                .asList();
        for (Server server : servers) {
            PlayerData data = server.playerBy(uuid);
            if (data != null) {
                return data;
            }
        }
        return null;
    }

}
