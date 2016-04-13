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
package io.minecloud.models.external;

import java.util.List;
import java.util.UUID;

import org.mongodb.morphia.Datastore;

import com.mongodb.BasicDBObject;

import io.minecloud.db.mongo.AbstractMongoRepository;
import io.minecloud.models.network.Network;
import io.minecloud.models.server.type.ServerType;

public class ExternalServerRepository extends AbstractMongoRepository<ExternalServer> {
    
    private ExternalServerRepository(Datastore datastore) {
        super(ExternalServer.class, datastore);
    }

    public static ExternalServerRepository create(Datastore datastore) {
        return new ExternalServerRepository(datastore);
    }

    public List<ExternalServer> serverBy(ServerType type) {
        return find(createQuery().field("type").equal(type))
                .asList();
    }

    public List<ExternalServer> serversFor(Network network) {
        return find(createQuery().field("network").equal(network))
                .asList();
    }

    public ExternalServer serverFor(UUID id) {
        return find(createQuery().field("onlinePlayers").hasThisElement(new BasicDBObject("id", id.toString())))
                .get();
    }

    public ExternalServer serverFor(String name) {
        return find(createQuery().field("onlinePlayers").hasThisElement(new BasicDBObject("name", name)))
                .get();
    }
}
