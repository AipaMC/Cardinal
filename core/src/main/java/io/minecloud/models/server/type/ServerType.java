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
package io.minecloud.models.server.type;

import io.minecloud.MineCloud;
import io.minecloud.MineCloudException;
import io.minecloud.db.mongo.model.MongoEntity;
import io.minecloud.db.redis.RedisDatabase;
import io.minecloud.db.redis.msg.binary.MessageOutputStream;
import io.minecloud.db.redis.pubsub.SimpleRedisChannel;
import io.minecloud.models.nodes.type.NodeType;
import io.minecloud.models.plugins.Plugin;
import io.minecloud.models.server.World;
import lombok.Setter;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Reference;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Entity(value = "server-types", noClassnameStored = true)
public class ServerType extends MongoEntity {
    @Setter
    private int dedicatedRam;
    @Setter
    private int maxPlayers;
    @Reference(lazy = true)
    @Setter
    private NodeType preferredNode;
    @Setter
    private String mod;
    @Setter
    private boolean defaultServer;
    @Setter
    private List<Plugin> plugins;
    @Setter
    private World defaultWorld;
    @Setter
    private List<World> worlds;
    @Setter
    private int timeOut = 45; // 45 seconds is the default timeout, allowed for overwriting
    /** If true, each time the server starts we will choose a random world to use as default */
    @Setter
    private boolean randomDefaultWorld = false; //False is the default value
    /** Determines when this server type needs more instances */
    @Setter
    private ServerLaunchType launchType = ServerLaunchType.PLAYERS; //Default value
    /** Simple abbreviation for the server type to be used in display */
    @Setter
    private String abbreviation = "";

    public String name() {
        return entityId();
    }
    
    public String nameAbv() {
        return abbreviation;
    }

    public int maxPlayers() {
        return maxPlayers;
    }

    public NodeType preferredNode() {
        return preferredNode;
    }

    public String mod() {
        return mod;
    }

    public int dedicatedRam() {
        return dedicatedRam;
    }

    public boolean defaultServer() {
        return defaultServer;
    }
    
    public boolean randomDefaultWorld() {
        return randomDefaultWorld;
    }
    
    public ServerLaunchType launchType() {
        return launchType;
    }

    public List<Plugin> plugins() {
        if (plugins == null) {
            plugins = new ArrayList<>();
        }

        return plugins;
    }

    public World defaultWorld() {
        //If random default worlds is on, choose one of the worlds registered
        //at random
        if (randomDefaultWorld) {
            int rand = new Random().nextInt(1 + worlds().size());
            if (rand == 0) {
                return defaultWorld;
            } else {
                return worlds().get(rand - 1);
            }
        } else  {
            return defaultWorld;    
        }
    }

    public List<World> worlds() {
        if (worlds == null) {
            worlds = new ArrayList<>();
        }

        return worlds;
    }

    public int timeOut() {
        return timeOut;
    }

    public void setName(String name) {
        setId(name);
    }

    public void teleport(String player) {
        RedisDatabase redis = MineCloud.instance().redis();

        if (redis.channelBy("teleport-type") == null) {
            redis.addChannel(SimpleRedisChannel.create("teleport-type", redis));
        }

        try (MessageOutputStream mos = new MessageOutputStream()){
            mos.writeString(player);
            mos.writeString(name());
            redis.channelBy("teleport-type").publish(mos.toMessage());
        } catch (IOException ex) {
            throw new MineCloudException("Could not encode teleport message", ex);
        }
    }
}
