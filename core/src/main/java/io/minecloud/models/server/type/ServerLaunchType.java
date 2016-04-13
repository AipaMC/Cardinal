package io.minecloud.models.server.type;

/**
 * Determines when the controller should launch new
 * servers
 */
public enum ServerLaunchType {
	/** 
	 * Spawn new servers when a player count 
	 * threshold has been met (right now, 75%) 
	 */
	PLAYERS,
	/** 
	 * Spawn new servers when there aren't any
	 * tagged as "joinable"
	 */
	AVAILABLE;
}
