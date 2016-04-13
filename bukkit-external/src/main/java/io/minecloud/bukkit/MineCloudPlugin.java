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

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import io.minecloud.Cached;
import io.minecloud.MineCloud;
import io.minecloud.db.Credentials;
import io.minecloud.db.mongo.MongoDatabase;
import io.minecloud.models.external.ExternalServer;
import io.minecloud.models.external.ExternalServerRepository;
import io.minecloud.models.external.ExternalServerType;
import io.minecloud.models.player.PlayerData;

public class MineCloudPlugin extends JavaPlugin {
    
    private Cached<ExternalServer> server;
    private MongoDatabase mongo;
    
    private ExternalServerType type;

    @Override
    public void onEnable() {
        FileConfiguration config = this.getConfig();
        Credentials creds = new Credentials(config.getString("mongo_host").split(";"), 
                config.getString("monog_username"), config.getString("mongo_password").toCharArray(), 
                config.getString("mongo_database"));
        MineCloud.instance().initiateMongo(creds);

        type = mongo.repositoryBy(ExternalServerType.class)
                .findFirst(config.getString("server_type"));

        if (type == null) {
            getLogger().log(Level.SEVERE, "Invalid server type");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        
        mongo = MineCloud.instance().mongo();

        //Update task
        new BukkitRunnable() {
            @Override
            public void run() {
                ExternalServer server = getServerNoCache();
                Runtime runtime = Runtime.getRuntime();

                server.setRamUsage((int) ((runtime.totalMemory() - runtime.freeMemory()) / 1048576));
                server.setTps(fetchTps());
                updatePlayers(server);

                mongo.repositoryBy(ExternalServer.class).save(server);
            }
        }.runTaskTimerAsynchronously(this, 40, 200);

        getServer().getPluginManager().registerEvents(new PlayerTracker(this), this);
    }

    public double fetchTps() {
        try {
            org.bukkit.Server server = Bukkit.getServer();
            Object minecraftServer = server.getClass().getDeclaredMethod("getServer").invoke(server);
            Field tps = minecraftServer.getClass().getField("recentTps");

            return ((double[]) tps.get(minecraftServer))[0];
        } catch (Exception ex) {
            getLogger().log(Level.SEVERE, "Could not fetch TPS", ex);
            return 21;
        }
    }

    @Override
    public void onDisable() {
    }

    public void updatePlayers(ExternalServer server) {
        List<PlayerData> onlinePlayers = new ArrayList<>();

        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerData data = new PlayerData();

            data.setHealth(player.getHealth());
            data.setMaxHealth(player.getMaxHealth());
            data.setName(player.getName());
            data.setId(player.getUniqueId().toString());

            onlinePlayers.add(data);
        }

        server.setOnlinePlayers(onlinePlayers);
    }
    
    private ExternalServer getServerNoCache() {
        ExternalServerRepository repository = mongo.repositoryBy(ExternalServer.class);
        List<ExternalServer> servers = repository.find(repository.createQuery()
                .field("type").equal(type)
                .field("port").notEqual(-1)
                .field("ramUsage").notEqual(-1))
                .asList();
        return servers.get(0);
    }

    public ExternalServer server() {
        if (server == null) {
            server = Cached.create(25_000, () -> getServerNoCache());
        }

        return server.get();
    }

    public MongoDatabase mongo() {
        return mongo;
    }
}
