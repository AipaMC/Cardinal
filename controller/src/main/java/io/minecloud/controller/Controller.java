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
package io.minecloud.controller;

import io.minecloud.MineCloud;
import io.minecloud.db.Credentials;
import io.minecloud.db.mongo.MongoDatabase;
import io.minecloud.db.redis.RedisDatabase;
import io.minecloud.db.redis.pubsub.SimpleRedisChannel;
import io.minecloud.models.bungee.Bungee;
import io.minecloud.models.bungee.BungeeRepository;
import io.minecloud.models.bungee.type.BungeeType;
import io.minecloud.models.external.ExternalServerType;
import io.minecloud.models.network.Network;
import io.minecloud.models.network.server.ServerNetworkMetadata;
import io.minecloud.models.nodes.Node;
import io.minecloud.models.server.Server;
import io.minecloud.models.server.ServerRepository;
import io.minecloud.models.server.type.ServerRole;
import io.minecloud.models.server.type.ServerType;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.stream.IntStream;

public class Controller {
    private static Controller instance;

    private final List<String> nodesUsed = new ArrayList<>();
    private final RedisDatabase redis;
    private final MongoDatabase mongo;

    private Controller() {
        instance = this;

        this.redis = MineCloud.instance().redis();
        this.mongo = MineCloud.instance().mongo();

        redis.addChannel(SimpleRedisChannel.create("bungee-create", redis));
        redis.addChannel(SimpleRedisChannel.create("server-create", redis));

        while (!Thread.currentThread().isInterrupted()) {
            this.redis.connected(); //Checks for Redis death, if it's dead it will reconnect.

            nodesUsed.clear();

            for (Network network : mongo.repositoryBy(Network.class).models()) {
                //Ensure bungees are deployed
                network.bungeeMetadata().forEach((type, amount) -> {
                    int difference = amount - network.bungeesOnline(type);

                    if (difference > 0) {
                        IntStream.range(0, difference)
                                .forEach((i) -> deployBungee(network, type));
                    }
                });

                //Remove invalid servers
                network.servers().stream()
                        .filter((server) -> server.ramUsage() != -1 && server.port() == -1)
                        .forEach((server) -> mongo.repositoryBy(Server.class).delete(server));
                
                for (Server server : network.servers()) {
                    boolean found = false;
                    for (ServerNetworkMetadata data : network.serverMetadata()) {
                        if (data.type().name().equals(server.type().name())) {
                            found = true;
                        }
                    }
                    if (found == false) {
                        mongo.repositoryBy(Server.class).delete(server);
                    }
                }
                
                //Check external servers
                for (ExternalServerType type : network.externalServerTypes()) {
                    if (!network.isExternalServerOnline(type)) {
                        network.setupExternalServer(type);
                    }
                }
                
                for (ServerNetworkMetadata metadata : network.serverMetadata()) {
                    ServerRepository repository = mongo.repositoryBy(Server.class);
                    List<Server> servers = repository.find(repository.createQuery()
                            .field("type").equal(metadata.type())
                            .field("network").equal(network))
                            .asList();
                    
                    int serversOnline = network.serversOnline(metadata.type());
                    
                    int neededServers = 0;
                    //Calculate needed servers from players online
                    if (metadata.type().serverRole() == ServerRole.LOBBY) {
                        int space = metadata.type().maxPlayers() * serversOnline;
                        int onlinePlayers = servers.stream()
                                .flatMapToInt((s) -> IntStream.of(s.onlinePlayers().size()))
                                .sum();
                        //If we are at > 75% of current capacity, launch more servers
                        int scaledServers = onlinePlayers > (space * 0.75) ?
                                (int) Math.floor(onlinePlayers / (space * 0.75)) + 1 : 0;
                        int requiredServers = metadata.minimumAmount() - serversOnline;

                        if (requiredServers < 0) {
                            requiredServers = 0;
                        }
                        
                        neededServers = requiredServers + scaledServers;
                    //Or from the amount of servers that are available for play
                    } else if (metadata.type().serverRole() == ServerRole.GAME) {
                        int availableServers = network.serversAvailable(metadata.type());
                        //No servers available? Launch one!
                        neededServers = Math.max(0, metadata.minimumAmount() - availableServers);
                    }
                    
                    //Don't go over the maximum server count
                    if ((neededServers + servers.size()) > metadata.maximumAmount()) {
                        neededServers = metadata.maximumAmount() - servers.size();
                    }

                    if (neededServers > 0) {
                        for (int i=0; i < neededServers; i++) {
                            try {
                                Thread.sleep(200L);
                            } catch (InterruptedException ignored) {}
                            
                            ServerType type = metadata.type();
                            MineCloud.logger().info("Sent deploy message to " + network.deployServer(type).name() +
                                    " for server type " + type.name() + " on " + network.name());
                        }
                    } else if (metadata.type().defaultServer() && serversOnline > metadata.minimumAmount()){
                        //Check for empty default servers to remove
                        for (Server server : servers) {
                            //Make sure we don't go below the minimum amount
                            if (serversOnline >= metadata.minimumAmount() 
                                    && server.onlinePlayers().size() == 0) {
                                mongo.repositoryBy(Server.class).delete(server);
                                serversOnline--;
                            }
                        }
                    }
                }
            }

            try {
                Thread.sleep(10000L);
            } catch (InterruptedException ignored) {
                // I don't care
            }
        }
    }

    public static void main(String[] args) throws Exception {
        Properties properties = new Properties();
        File configFolder = new File("/etc/minecloud/controller/");
        File file = new File(configFolder, "details.properties");

        if (!configFolder.exists()) {
            configFolder.mkdirs();
        }

        if (!file.exists()) {
            file.createNewFile();
        }

        properties.load(new FileInputStream(file));

        if (!properties.containsKey("mongo-hosts")) {
            MineCloud.runSetup(properties, file);
            new Controller();
        }

        Credentials mongo = new Credentials(properties.getProperty("mongo-hosts").split(";"),
                properties.getProperty("mongo-username"),
                properties.getProperty("mongo-password").toCharArray(),
                properties.getProperty("mongo-database"));
        Credentials redis = new Credentials(new String[] {properties.getProperty("redis-host")},
                "",
                properties.getProperty("redis-password").toCharArray());

        MineCloud.instance().initiateMongo(mongo);
        MineCloud.instance().initiateRedis(redis);

        new Controller();
    }

    public static Controller instance() {
        return instance;
    }

    public void deployBungee(Network network, BungeeType type) {
        BungeeRepository bungeeRepo = mongo.repositoryBy(Bungee.class);
        Node node = null;

        for (Node nextNode : network.nodes()) {
            if (bungeeRepo.find(bungeeRepo.createQuery()
                    .field("node").equal(nextNode)).get() == null &&
                    !nodesUsed.contains(nextNode.name())) {
                node = nextNode;
                break;
            }
        }

        if (node == null) {
            MineCloud.logger().info("Not deploying bungee, no node to deploy to");
            return;
        }

        nodesUsed.add(node.name());
        network.deployBungee(type, node);
    }
}
