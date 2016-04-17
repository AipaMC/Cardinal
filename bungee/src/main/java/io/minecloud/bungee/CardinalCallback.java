package io.minecloud.bungee;

import java.io.IOException;
import java.util.HashMap;

import io.minecloud.db.redis.msg.Message;
import io.minecloud.db.redis.msg.MessageType;
import io.minecloud.db.redis.msg.binary.MessageInputStream;
import io.minecloud.db.redis.pubsub.ChannelCallback;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;

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
                    player.sendMessage(TextComponent.fromLegacyText(message));
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

}
