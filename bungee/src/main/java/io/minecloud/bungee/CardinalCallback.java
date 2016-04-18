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

import java.io.IOException;
import java.util.HashMap;

import io.minecloud.db.redis.msg.Message;
import io.minecloud.db.redis.msg.MessageType;
import io.minecloud.db.redis.msg.binary.MessageInputStream;
import io.minecloud.db.redis.pubsub.ChannelCallback;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.chat.ComponentSerializer;

public class CardinalCallback implements ChannelCallback {
    
    private HashMap<String, SubChannel> channels;
    
    public CardinalCallback() {
        channels = new HashMap<>();
        
        channels.put("message", new MessageSubChannel());
    }

    @Override
    public void call(Message message) throws IOException {
        if (message.type() != MessageType.BINARY) {
            return;
        }

        MessageInputStream stream = message.contents();
        SubChannel chan = channels.get(stream.readString());
        if (chan != null) {
            chan.call(stream);
        }
    }
    
    private interface SubChannel {
        public void call(MessageInputStream stream);
    }
    
    private class MessageSubChannel implements SubChannel {
        @Override
        public void call(MessageInputStream stream) {
            try {
                String name = stream.readString();
                String message = stream.readString();
                
                ProxiedPlayer player = ProxyServer.getInstance().getPlayer(name);
                if (player != null) {
                    player.sendMessage(ComponentSerializer.parse(message));
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

}
