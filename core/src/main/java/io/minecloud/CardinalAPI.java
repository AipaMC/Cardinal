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

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import io.minecloud.db.redis.RedisDatabase;
import io.minecloud.db.redis.msg.binary.MessageOutputStream;
import io.minecloud.db.redis.pubsub.SimpleRedisChannel;
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
    
    /**
     * Kick a player back to the lobby
     * @param uuid UUID of player to kick
     * @param reasonMessage Message to send to the kicked player
     */
    public static void kickPlayer(UUID uuid, String reasonMessage) {
        RedisDatabase redis = MineCloud.instance().redis();

        if (redis.channelBy("cardinal") == null) {
            redis.addChannel(SimpleRedisChannel.create("cardinal", redis));
        }

        try (MessageOutputStream mos = new MessageOutputStream()) {
            mos.writeString("kick");
            mos.writeString(uuid.toString());
            mos.writeString(reasonMessage);
            redis.channelBy("cardinal").publish(mos.toMessage());
        } catch (IOException ex) {
            throw new MineCloudException("Could not encode kick message", ex);
        }
    }

}
