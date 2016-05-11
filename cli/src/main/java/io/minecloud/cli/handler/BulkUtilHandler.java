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
package io.minecloud.cli.handler;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import asg.cliche.Command;
import asg.cliche.Param;
import io.minecloud.MineCloud;
import io.minecloud.models.network.Network;
import io.minecloud.models.network.server.ServerNetworkMetadata;
import io.minecloud.models.plugins.Plugin;
import io.minecloud.models.plugins.PluginType;
import io.minecloud.models.server.type.ServerType;

public class BulkUtilHandler extends AbstractHandler {
    Network network;

    BulkUtilHandler(String name) {
    	network = MineCloud.instance().mongo()
                .repositoryBy(Network.class)
                .findFirst(name);

        if (network == null) {
        	 System.out.println("Network not found. Please exit.");
        }
    }

    @Command
    public String addPlugin(@Param(name = "plugin-name") String pluginName, @Param(name = "version") String version) {
        PluginType pluginType = MineCloud.instance().mongo()
                .repositoryBy(PluginType.class)
                .findFirst(pluginName);

        if (pluginType == null) {
            return "No found plugin by the name of " + pluginName;
        }

        if (!pluginType.versions().contains(version)) {
            return "No version by the name of " + version + " was found for " + pluginName;
        }

        for (ServerNetworkMetadata meta : network.serverMetadata()) {
        	ServerType type = meta.type();
        	
            if (type.plugins() == null) {
                type.setPlugins(new ArrayList<>());
            }

            List<Plugin> plugins = type.plugins();
            
            //If the plugin is already added, skip
            for (Plugin plugin : plugins) {
            	if (plugin.name().equalsIgnoreCase(pluginName)) {
            		continue;
            	}
            }

            plugins.add(new Plugin(pluginType, version, null));
            type.setPlugins(plugins);
            MineCloud.instance().mongo()
            	.repositoryBy(ServerType.class)
            	.save(type);
            System.out.println("Successfully added " + pluginName + "v(" + version + ") to type " + type.name());
        }
        return "Bulk operation complete";
    }

    @Command
    public String setPluginConfig(@Param(name = "plugin-name") String pluginName, @Param(name = "config") String config) {
        PluginType pluginType = MineCloud.instance().mongo()
                .repositoryBy(PluginType.class)
                .findFirst(pluginName);

        if (pluginType == null) {
            return "No found plugin by the name of " + pluginName;
        }

        if (!pluginType.configs().contains(config)) {
            return "No configs by the name of " + config + " was found!";
        }
        
        for (ServerNetworkMetadata meta : network.serverMetadata()) {
        	ServerType type = meta.type();

            if (type.plugins() == null || !type.plugins().stream().anyMatch((p) -> p.name().equalsIgnoreCase(pluginName))) {
                continue;
            }

            type.plugins().stream()
                    .filter((p) -> p.name().equalsIgnoreCase(pluginName))
                    .findFirst().get()
                    .setConfig(config);
            
            MineCloud.instance().mongo()
            	.repositoryBy(ServerType.class)
            	.save(type);
            
            System.out.println("Successfully set config version to " + config + " for type: " + type.name());
        }
        return "Bulk operation complete";
    }

    @Command
    public String removePlugin(@Param(name = "plugin-name") String pluginName) {
        for (ServerNetworkMetadata meta : network.serverMetadata()) {
        	ServerType type = meta.type();
        	
            Optional<Plugin> optional = type.plugins().stream()
                    .filter((p) -> p.name().equalsIgnoreCase(pluginName))
                    .findFirst();

            if (!optional.isPresent()) {
            	continue;
            }

            type.plugins().remove(optional.get());
            
            MineCloud.instance().mongo()
            	.repositoryBy(ServerType.class)
            	.save(type);
            System.out.println("Removed plugin " + pluginName + " from type " + type.name());
        }
        return "Bulk operation complete";
    }
    
}
