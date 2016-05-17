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
package io.minecloud.models.network;

import io.minecloud.MineCloud;
import io.minecloud.db.mongo.model.MongoEntity;
import io.minecloud.db.redis.msg.binary.MessageOutputStream;
import io.minecloud.models.bungee.Bungee;
import io.minecloud.models.bungee.BungeeRepository;
import io.minecloud.models.bungee.type.BungeeType;
import io.minecloud.models.bungee.type.BungeeTypeRepository;
import io.minecloud.models.external.ExternalServer;
import io.minecloud.models.external.ExternalServerRepository;
import io.minecloud.models.external.ExternalServerType;
import io.minecloud.models.network.server.ServerNetworkMetadata;
import io.minecloud.models.nodes.Node;
import io.minecloud.models.nodes.NodeRepository;
import io.minecloud.models.server.Server;
import io.minecloud.models.server.ServerMetadata;
import io.minecloud.models.server.ServerRepository;
import io.minecloud.models.server.type.ServerType;
import lombok.Setter;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Reference;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

@Entity(value = "networks", noClassnameStored = true)
public class Network extends MongoEntity {
    @Setter
    private List<ServerNetworkMetadata> serverMetadata;
    
    /** Any linked external servers on this network */
    @Setter
    @Reference(lazy = true)
    private List<ExternalServerType> externalServers;
    
    private Map<String, Integer> bungees;
    @Setter
    @Reference(lazy = true)
    private List<Node> nodes;

    public void deployBungee(BungeeType type, Node node) {
        if (!bungees.containsKey(type.name())) {
            throw new IllegalArgumentException("Cannot deploy " + type + " on network; is not a valid network bungee type!");
        }

        try (MessageOutputStream os = new MessageOutputStream()) {
            os.writeString(node.name());
            os.writeString(name());
            os.writeString(type.name());
            MineCloud.instance().redis().channelBy("bungee-create").publish(os.toMessage());
        } catch (IOException e) {
            MineCloud.logger().log(Level.SEVERE, "Encountered an odd exception whilst encoding a message", e);
            return;
        }
    }

    public Node deployServer(ServerType type, ServerMetadata... metadata) {
        NodeRepository nodeRepo = MineCloud.instance().mongo().repositoryBy(Node.class);

        Node node = nodeRepo.findNode(this, type.preferredNode(), type.dedicatedRam());

        try (MessageOutputStream os = new MessageOutputStream()) {
            os.writeString(node.name());
            os.writeString(name());
            os.writeString(type.name());
            os.writeVarInt32(metadata.length);

            for (ServerMetadata md : metadata) {
                os.writeString(md.key());
                os.writeString(md.value());
            }
            MineCloud.instance().redis().channelBy("server-create").publish(os.toMessage());
        } catch (IOException e) {
            MineCloud.logger().log(Level.SEVERE, "Encountered an odd exception whilst encoding a message", e);
        }
        return node;
    }
    
    /**
     * Sets up database entries for an external server. This
     * 1. Creates a Mongo entry for the server
     * 2. Sends a server start notification to the bungees
     * @param type Type of server to launch
     */
    public void setupExternalServer(ExternalServerType type) {
        //Create new entry for this server
        ExternalServerRepository repository = MineCloud.instance().mongo().repositoryBy(ExternalServer.class);
        ExternalServer server = new ExternalServer();
        
        server.setType(type);
        
        server.setNetwork(this);
        server.setOnlinePlayers(new ArrayList<>());
        server.setRamUsage(-1);
        server.setId(server.type().name());
        server.setMetadata(new ArrayList<>());
        server.setAddress(type.address());
        server.setPort(type.port());
        server.setStartTime(System.currentTimeMillis());
        
        repository.save(server);
        
        //Send redis start notification (for Bungee)
        try (MessageOutputStream os = new MessageOutputStream()) {
            os.writeString(server.entityId());

            MineCloud.instance().redis().channelBy("external-server-add").publish(os.toMessage());
        } catch (IOException e) {
            MineCloud.logger().log(Level.SEVERE, "Unable to publish server create message", e);
        }
    }

    public String name() {
        return entityId();
    }

    public List<ServerNetworkMetadata> serverMetadata() {
        return serverMetadata;
    }
    
    public List<ExternalServerType> externalServerTypes() {
        return externalServers;
    }

    public Map<BungeeType, Integer> bungeeMetadata() {
        Map<BungeeType, Integer> metadata = new HashMap<>();
        BungeeTypeRepository repository = MineCloud.instance().mongo().repositoryBy(BungeeType.class);

        if (bungees == null) {
            return metadata;
        }

        for (Map.Entry<String, Integer> entry : bungees.entrySet()) {
            metadata.put(repository.findFirst(entry.getKey()), entry.getValue());
        }

        return metadata;
    }

    public List<Server> servers() {
        return ((ServerRepository) MineCloud.instance().mongo().repositoryBy(Server.class)).serversFor(this);
    }
    
    public List<ExternalServer> externalServers() {
        return ((ExternalServerRepository) MineCloud.instance().mongo().repositoryBy(ExternalServer.class)).serversFor(this);
    }
    
    public List<Bungee> bungees() {
        return ((BungeeRepository) MineCloud.instance().mongo().repositoryBy(Bungee.class)).serversFor(this);
    }

    public int serversOnline() {
        return (int) MineCloud.instance().mongo().repositoryBy(Server.class)
                .count("network", this);
    }

    public int serversOnline(ServerType type) {
        ServerRepository repository = MineCloud.instance().mongo().repositoryBy(Server.class);

        return (int) repository.count(repository.createQuery()
                .field("network").equal(this)
                .field("type").equal(type));
    }
    
    public boolean isExternalServerOnline(ExternalServerType type) {
        ExternalServerRepository repository = MineCloud.instance().mongo().repositoryBy(ExternalServer.class);

        return repository.count(repository.createQuery()
                .field("network").equal(this)
                .field("type").equal(type)) > 0;
    }
    
    public int serversAvailable(ServerType type) {
        ServerRepository repository = MineCloud.instance().mongo().repositoryBy(Server.class);

        return (int) repository.count(repository.createQuery()
                .field("network").equal(this)
                .field("type").equal(type)
                .field("joinable").equal(true));
    }

    public int bungeesOnline() {
        return (int) MineCloud.instance().mongo().repositoryBy(Bungee.class)
                .count("network", this);
    }

    public int bungeesOnline(BungeeType type) {
        BungeeRepository repository = MineCloud.instance().mongo().repositoryBy(Bungee.class);

        return (int) repository.count(repository.createQuery()
                .field("network").equal(this)
                .field("type").equal(type));
    }

    public List<Node> nodes() {
        return nodes;
    }

    public void setName(String name) {
        setId(name);
    }

    public void setBungees(Map<BungeeType, Integer> map) {
        bungees = new HashMap<>();

        for (Map.Entry<BungeeType, Integer> entry : map.entrySet()) {
            bungees.put(entry.getKey().name(), entry.getValue());
        }
    }
}
