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

import com.google.common.io.Files;
import com.mongodb.BasicDBObject;
import io.minecloud.Cached;
import io.minecloud.MineCloud;
import io.minecloud.MineCloudException;
import io.minecloud.bungee.cardinal.CardinalCallback;
import io.minecloud.bungee.cardinal.ServerStartEvent;
import io.minecloud.db.mongo.MongoDatabase;
import io.minecloud.db.redis.RedisDatabase;
import io.minecloud.db.redis.msg.MessageType;
import io.minecloud.db.redis.msg.binary.MessageInputStream;
import io.minecloud.db.redis.pubsub.SimpleRedisChannel;
import io.minecloud.models.bungee.Bungee;
import io.minecloud.models.bungee.type.BungeeType;
import io.minecloud.models.external.ExternalServer;
import io.minecloud.models.external.ExternalServerRepository;
import io.minecloud.models.plugins.PluginType;
import io.minecloud.models.server.Server;
import io.minecloud.models.server.ServerRepository;
import io.minecloud.models.server.type.ServerType;
import lombok.Getter;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.plugin.PluginManager;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class MineCloudPlugin extends Plugin {
    
    public static final String PREFIX = "" + ChatColor.BLUE + ChatColor.BOLD + "Cardinal> " 
            + ChatColor.AQUA;
    
    @Getter
    private static MineCloudPlugin instance;
    
    Cached<Bungee> bungee;
    MongoDatabase mongo;
    RedisDatabase redis;
    
    @Getter
    private CardinalCallback cardinalExtension;

    @Override
    public void onEnable() {
        instance = this;
        
        MineCloud.environmentSetup();

        mongo = MineCloud.instance().mongo();
        redis = MineCloud.instance().redis();

        try {
            Files.write(ManagementFactory.getRuntimeMXBean().getName().split("@")[0].getBytes(Charset.defaultCharset()),
                    new File("app.pid"));
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        
        redis.addChannel(SimpleRedisChannel.create("server-start-notif", redis)
                .addCallback((message) ->
                    getProxy().getScheduler().schedule(this, () -> {
                        if (message.type() != MessageType.BINARY) {
                            return;
                        }

                        try {
                            MessageInputStream stream = message.contents();
                            Server server = mongo.repositoryBy(Server.class).findFirst(stream.readString());

                            addServer(server);
                            
                            ServerStartEvent event = new ServerStartEvent(server);
                            getProxy().getPluginManager().callEvent(event);
                        } catch (IOException ignored) {
                        }
                    }, 1, TimeUnit.SECONDS)));
        
        redis.addChannel(SimpleRedisChannel.create("external-server-add", redis)
                .addCallback((message) ->
                    getProxy().getScheduler().schedule(this, () -> {
                        if (message.type() != MessageType.BINARY) {
                            return;
                        }

                        try {
                            MessageInputStream stream = message.contents();
                            ExternalServer server = mongo.repositoryBy(ExternalServer.class).findFirst(stream.readString());

                            addServer(server);
                        } catch (IOException ignored) {
                        }
                    }, 1, TimeUnit.SECONDS)));        

        redis.addChannel(SimpleRedisChannel.create("server-shutdown-notif", redis)
                .addCallback((message) -> {
                    if (message.type() != MessageType.BINARY) {
                        return;
                    }

                    MessageInputStream stream = message.contents();

                    removeServer(stream.readString());
                }));

        redis.addChannel(SimpleRedisChannel.create("teleport", redis)
                .addCallback((message) -> {
                    if (message.type() != MessageType.BINARY) {
                        return;
                    }

                    MessageInputStream stream = message.contents();
                    ProxiedPlayer player = getProxy().getPlayer(stream.readString());

                    if (player == null) {
                        return;
                    }

                    String name = stream.readString();
                    ServerInfo info = getProxy().getServerInfo(name);

                    if (info == null) {
                        ServerRepository repository = mongo.repositoryBy(Server.class);
                        Server server = repository.findOne("_id", name);

                        if (server != null) {
                            addServer(server);
                            info = getProxy().getServerInfo(name);
                        }
                    }
                    player.sendMessage(TextComponent.fromLegacyText(PREFIX + "Sending you to " + name));
                    player.connect(info);
                }));

        redis.addChannel(SimpleRedisChannel.create("teleport-type", redis)
                .addCallback((message) -> {
                    if (message.type() != MessageType.BINARY) {
                        return;
                    }

                    MessageInputStream stream = message.contents();
                    ProxiedPlayer player = getProxy().getPlayer(stream.readString());

                    if (player == null) {
                        return;
                    }

                    ServerType type = mongo.repositoryBy(ServerType.class)
                            .findFirst(stream.readString());

                    if (type == null) {
                        getLogger().log(Level.SEVERE, "Received teleport message with invalid server type");
                        return;
                    }

                    ServerRepository repository = mongo.repositoryBy(Server.class);
                    List<Server> servers = repository.find(repository.createQuery()
                            .field("network").equal(bungee().network())
                            .field("type").equal(type)
                            .field("port").notEqual(-1)
                            .field("ramUsage").notEqual(-1))
                            .asList();

                    Collections.sort(servers, (a, b) -> a.onlinePlayers().size() - b.onlinePlayers().size());

                    if (servers.size() == 0) {
                        player.sendMessage(TextComponent.fromLegacyText(PREFIX + "Cannot find a server to move you to"));
                        return;
                    }
                    //Players can only be teleported to joinable servers
                    servers = servers.stream()
                            .filter(server -> server.isJoinable())
                            .collect(Collectors.toList());
                    if (servers.size() == 0) {
                        player.sendMessage(TextComponent.fromLegacyText(PREFIX + "All servers are current being used. "
                                + "Please wait for new servers to become available."));
                        return;
                    }
                    
                    Server server = servers.get(0);
                    ServerInfo info = getProxy().getServerInfo(server.name());

                    if (info == null) {
                        getLogger().warning("Cannot find " + server.name() + " in ServerInfo store, adding.");
                        addServer(server);
                        info = getProxy().getServerInfo(server.name());
                    }
                    
                    player.sendMessage(TextComponent.fromLegacyText(PREFIX + "Sending you to " + server.name()));
                    player.connect(info);
                }));
        
        //Handles various subchannels. Save resources by using one master redis channel
        cardinalExtension = new CardinalCallback();
        redis.addChannel(SimpleRedisChannel.create("cardinal", redis).addCallback(cardinalExtension));

        getProxy().getScheduler().schedule(this, () -> getProxy().getScheduler().runAsync(this, () -> {
            if (mongo.db().getCollection("bungees").count(new BasicDBObject("_id", System.getenv("bungee_id"))) != 0) {
                return;
            }

            getLogger().info("Bungee removed from database, going down...");
            getProxy().stop(); // bye bye
        }), 2, 2, TimeUnit.SECONDS);

        BungeeType type = bungee().type();

        File nContainer = new File("nplugins/");
        nContainer.mkdirs();

        type.plugins().forEach((plugin) -> {
            String version = plugin.version();
            PluginType pluginType = plugin.type();
            File pluginsContainer = new File("/mnt/minecloud/plugins/",
                    pluginType.name() + "/" + version);
            List<File> plugins = new ArrayList<>();

            getLogger().info("Loading " + pluginType.name() + "...");

            if (validateFolder(pluginsContainer, pluginType, version))
                return;

            for (File f : pluginsContainer.listFiles()) {
                if (f.isDirectory())
                    continue; // ignore directories
                File pl = new File(nContainer, f.getName());

                try {
                    Files.copy(f, pl);
                } catch (IOException ex) {
                    getLogger().log(Level.SEVERE, "Could not load " + pluginType.name() +
                            ", printing stacktrace...");
                    ex.printStackTrace();
                    return;
                }

                plugins.add(pl);
            }

            File configs = new File("/mnt/minecloud/configs/",
                    pluginType.name() + "/" + (plugin.config() == null ? version : plugin.config()));
            File configContainer = new File(nContainer, pluginType.name());

            File defaultPluginContainer = new File("plugins/" + pluginType.name());
            
            if (!validateFolder(configs, pluginType, version)) {
                 copyFolder(configs, configContainer);
                 copyFolder(configs, defaultPluginContainer);
            }
        });

        getProxy().getScheduler().schedule(this, () -> {
            this.redis.connected(); //Checks for Redis death, if it's dead it will reconnect.

            ServerRepository repository = mongo.repositoryBy(Server.class);
            List<Server> servers = repository.find(repository.createQuery()
                    .field("network").equal(bungee().network()))
                    .asList();

            servers.removeIf((s) -> s.port() == -1);
            servers.forEach(this::addServer);
            
            //Now do external servers
            ExternalServerRepository externalRepo = mongo.repositoryBy(ExternalServer.class);
            List<ExternalServer> externalServers = externalRepo.find(externalRepo.createQuery()
                    .field("network").equal(bungee().network()))
                    .asList();
            externalServers.removeIf((s) -> s.port() == -1);
            externalServers.forEach(this::addServer);

            getProxy().setReconnectHandler(new CustomReconnectHandler(this));
            getProxy().getPluginManager().registerListener(this, new MineCloudListener(this));

            // release plugin manager lock
            try {
                Field f = PluginManager.class.getDeclaredField("toLoad");

                f.setAccessible(true);
                f.set(getProxy().getPluginManager(), new HashMap<>());
            } catch (NoSuchFieldException | IllegalAccessException ignored) {
            }

            getProxy().getPluginManager().detectPlugins(nContainer);
            getProxy().getPluginManager().loadPlugins();
            getProxy().getPluginManager().getPlugins().stream()
                    .filter((p) -> !p.getDescription().getName().equals("MineCloud-Bungee"))
                    .forEach(Plugin::onEnable);
        }, 0, TimeUnit.SECONDS);
    }

    @Override
    public void onDisable() {
        mongo.repositoryBy(Bungee.class).deleteById(System.getenv("bungee_id"));
    }

    private boolean validateFolder(File file, PluginType pluginType, String version) {
        if (!file.exists()) {
            getLogger().info(file.getPath() + " does not exist! Cannot load " + pluginType.name());
            return true;
        }

        if (!(file.isDirectory()) || file.listFiles() == null
                || file.listFiles().length < 1) {
            getLogger().info(pluginType.name() + " " + version +
                    " has either no files or has an invalid setup");
            return true;
        }

        return false;
    }

    private void copyFolder(File folder, File folderContainer) {
        folderContainer.mkdirs();

        for (File f : folder.listFiles()) {
            if (f.isDirectory()) {
                File newContainer = new File(folderContainer, f.getName());
                copyFolder(f, newContainer);
            } else {
                try {
                    Files.copy(f, new File(folderContainer, f.getName()));
                } catch (IOException ex) {
                    throw new MineCloudException(ex);
                }   
            }
        }
    }

    public void addServer(Server server) {
        addServer(server.name(), server.node().privateIp(), server.port(), "");
    }
    
    public void addServer(ExternalServer server) {
        addServer(server.name(), server.address(), server.port(), server.type().motd());
    }
    
    private void addServer(String name, String ip, int port, String motd) {
        ServerInfo info = getProxy().constructServerInfo(name,
                new InetSocketAddress(ip, port),
                motd, false);

        getProxy().getServers().put(name, info);
        getLogger().info("Added " + name + " to server list, " + ip +
                ":" + port);
    }

    public void removeServer(String server) {
        getProxy().getServers().remove(server);
    }

    public Bungee bungee() {
        if (bungee == null) {
            this.bungee = Cached.create(() -> mongo.repositoryBy(Bungee.class).findFirst(System.getenv("bungee_id")));
        }

        return bungee.get();
    }
}
