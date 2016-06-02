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

import asg.cliche.Command;
import asg.cliche.Param;
import io.minecloud.MineCloud;
import io.minecloud.models.external.ExternalServerType;

public class ExternalServerTypeHandler extends AbstractHandler {
    
    private ExternalServerType type;

    public ExternalServerTypeHandler(String name) {
        super();

        type = MineCloud.instance().mongo()
                .repositoryBy(ExternalServerType.class)
                .findFirst(name);

        if (type == null) {
            System.out.println("Could not find type in database; creating new one...");
            type = new ExternalServerType();

            type.setName(name);
        }
    }

    @Command
    public String maxPlayers(@Param(name = "max-players") int max) {
        if (max < 0) {
            return "Invalid max players!";
        }

        type.setMaxPlayers(max);
        return "Set maximum amount of players to " + max + " successfully";
    }

    @Command
    public String address(@Param(name = "address") String address) {
        type.setAddress(address);
        return "Set address to " + address;
    }

    @Command
    public String port(@Param(name = "port") int port) {
        type.setPort(port);
        return "Set port to " + port;
    }
    
    @Command
    public String motd(@Param(name = "motd") String motd) {
        type.setMotd(motd);
        return "Set motd to " + motd;
    }

    @Command
    public String push() {
        if (type.port() == 0 ||
                type.address() == null ||
                type.maxPlayers() == 0) {
            return "Required fields (address, port, maxplayers) have not been set by the user! " +
                    "Unable to push modifications";
        }

        MineCloud.instance().mongo()
                .repositoryBy(ExternalServerType.class)
                .save(type);
        return "Successfully pushed modifications to database!";
    }

    @Command(name = "!show")
    public List<String> show() {
        List<String> list = new ArrayList<>();
        list.add("Currently Modeling [External Server Type] (" + type.name() + ")");
        list.add("===========================================");
        list.add("Listing Specifications...");
        list.add("- Maximum Amount of Players: " + type.maxPlayers());
        list.add("- Address: " + type.address());
        list.add("- Port: " + type.port());
        list.add("===========================================");
        list.add("If you're ready to go, type 'push'.");
        return list;
    }
}
