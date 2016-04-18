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
     * @param player Player's username
     * @return Server the player is on or null if offline
     */
    public static Server getServerPlayerIsOn(String player) {
        ServerRepository repository = MineCloud.instance().mongo().repositoryBy(Server.class);
        List<Server> servers = repository.find(repository.createQuery()
                .field("port").notEqual(-1)
                .field("ramUsage").notEqual(-1))
                .asList();
        for (Server server : servers) {
            if (server.playerBy(player) != null) {
                return server;
            }
        }
        return null;
    }

}
