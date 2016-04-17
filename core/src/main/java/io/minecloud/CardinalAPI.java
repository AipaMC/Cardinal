package io.minecloud;

import java.util.List;

import io.minecloud.models.server.Server;
import io.minecloud.models.server.ServerRepository;

/**
 * Adds various functions that are useful for other
 * plugins in interacting with Cardinal
 * @author Aipa
 */
public class CardinalAPI {
    
    /**
     * Gets the server instance a player is currently on
     * @param player Player's username
     * @return Server the player is on or null if offline
     */
    public static Server getServerPlayerIsOn(String player) {
        ServerRepository repository = MineCloud.instance().mongo().repositoryBy(Server.class);
        List<Server> servers = repository.find(repository.createQuery()
                .field("port").notEqual(-1)
                .field("ramUsage").notEqual(-1))
                .asList();
        for (Server server : servers) {
            if (server.playerBy(player) != null) {
                return server;
            }
        }
        return null;
    }

}
