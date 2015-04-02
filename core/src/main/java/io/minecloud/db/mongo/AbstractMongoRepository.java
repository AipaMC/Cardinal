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
package io.minecloud.db.mongo;

import com.mongodb.*;
import io.minecloud.db.mongo.model.ModelTranslator;
import io.minecloud.db.mongo.model.ModelWrapper;
import io.minecloud.db.mongo.model.MongoModel;
import org.bson.types.ObjectId;

import java.lang.reflect.ParameterizedType;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public abstract class AbstractMongoRepository<T extends MongoModel> implements MongoRepository<T> {
    protected final DBCollection collection;
    protected final MongoDatabase database;
    private final String collectionName;

    protected AbstractMongoRepository(String collectionName, MongoDatabase database) {
        this.collectionName = collectionName;
        this.database = database;
        this.collection = database.db().getCollection(collectionName);
    }

    @Override
    public String collectionName() {
        return collectionName;
    }

    @Override
    public boolean insert(T model) {
        WriteResult result = collection.insert(ModelTranslator.translate(ModelWrapper.wrapperFrom(model)));

        return result.getN() == 1;
    }

    public Class<T> modelClass() {
        return (Class<T>) ((ParameterizedType) getClass().getGenericSuperclass()).getActualTypeArguments()[0];
    }

    @Override
    public T findFirst(BasicDBObject query) {
        return findFirst((ObjectId) collection.findOne(query).get("_id"));
    }

    public T findFirst(ObjectId id) {
        return findFirst((model) ->
                ModelWrapper.wrapperFrom(model).objectId().equals(id));
    }

    @Override
    public void remove(T model) {
        collection.remove(ModelTranslator.translate(ModelWrapper.wrapperFrom(model)));
    }

    @Override
    public boolean update(ObjectId id, T model) {
        return update(new BasicDBObject("_id", id), model);
    }

    @Override
    public boolean update(DBObject query, T model) {
        WriteResult result = collection.update(query,
                ModelTranslator.translate(ModelWrapper.wrapperFrom(model)));

        return result.getN() != 0;
    }

    @Override
    public void update(T model) {
        ModelWrapper wrapper = ModelWrapper.wrapperFrom(model);

        collection.update(new BasicDBObject("_id", wrapper.objectId()),
                ModelTranslator.translate(wrapper));
    }

    @Override
    public DBCollection collection() {
        return collection;
    }

    @Override
    public Collection<T> models() {
        Set<T> models = new HashSet<>();
        DBCursor cursor = collection.find();

        while (cursor.hasNext()) {
            models.add((T) ModelTranslator.translate((BasicDBObject) cursor.next(), modelClass())
                    .model());
        }

        return models;
    }
}