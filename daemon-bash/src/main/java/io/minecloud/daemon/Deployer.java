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
package io.minecloud.daemon;

import io.minecloud.MineCloud;
import io.minecloud.MineCloudException;
import io.minecloud.db.Credentials;
import io.minecloud.models.bungee.Bungee;
import io.minecloud.models.bungee.BungeeRepository;
import io.minecloud.models.bungee.type.BungeeType;
import io.minecloud.models.network.Network;
import io.minecloud.models.nodes.Node;
import io.minecloud.models.server.Server;
import io.minecloud.models.server.ServerMetadata;
import io.minecloud.models.server.ServerRepository;
import io.minecloud.models.server.type.ServerType;
import redis.clients.jedis.Jedis;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

public final class Deployer extends Thread {
    public static final AtomicInteger PORT_COUNTER = new AtomicInteger(32812);
    
    private static final Deployer instance = new Deployer();
    
    private final Queue<LaunchData> launchQueue = new ConcurrentLinkedQueue<>();
    
    private static class LaunchData {
    	private Network network;
    	private ServerType type;
    	private List<ServerMetadata> metadata;
    	public LaunchData(Network network, ServerType type, List<ServerMetadata> metadata) {
    		this.network = network;
    		this.type = type;
    		this.metadata = metadata;
    	}
    }

    private Deployer() {
    }
    
    public static void startThread() {
    	instance.start();
    }
    
    @Override
    public void run() {
    	while (true) {
    		if (!launchQueue.isEmpty()) {
    			deployServer(launchQueue.poll());
    			try {
					Thread.sleep(7500);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
    		}
    	}
    }
    
    public static void deployServer(Network network, ServerType type, List<ServerMetadata> metadata) {
    	instance.launchQueue.add(new LaunchData(network, type, metadata));
    }

    private static void deployServer(LaunchData data) {
        Credentials mongoCreds = MineCloud.instance().mongo().credentials();
        Credentials redisCreds = MineCloud.instance().redis().credentials();
        ServerRepository repository = MineCloud.instance().mongo().repositoryBy(Server.class);
        Server server = new Server();

        server.setType(data.type);
        server.setNumber(repository.nextNumberFor(data.type));
        server.setNetwork(data.network);
        server.setNode(MineCloudDaemon.instance().node());
        server.setOnlinePlayers(new ArrayList<>());
        server.setRamUsage(-1);
        server.setId(server.type().name() + server.number());
        server.setMetadata(data.metadata);
        if (PORT_COUNTER.get() >= 65535) {
        	PORT_COUNTER.set(32812);
        }
        server.setPort(PORT_COUNTER.incrementAndGet());
        server.setContainerId("null");
        server.setStartTime(System.currentTimeMillis());

        try {
            if (isRunning(server.name())) {
                return;
            }
        } catch (IOException | InterruptedException ignored) {
        }

        Map<String, String> env = new HashMap<String, String>() {{
            put("mongo_hosts", mongoCreds.formattedHosts());
            put("mongo_username", mongoCreds.username());
            put("mongo_password", new String(mongoCreds.password()));
            put("mongo_database", mongoCreds.database());

            put("redis_host", redisCreds.hosts()[0]);
            put("redis_password", new String(redisCreds.password()));
            put("SERVER_MOD", server.type().mod());
            put("DEDICATED_RAM", String.valueOf(server.type().dedicatedRam()));
            put("MAX_PLAYERS", String.valueOf(server.type().maxPlayers()));

            put("server_id", server.entityId());
            put("DEFAULT_WORLD", data.type.defaultWorld().name());
            put("DEFAULT_WORLD_VERSION", data.type.defaultWorld().version());

            put("PORT", String.valueOf(server.port()));
            put("PRIVATE_IP", server.node().privateIp());
        }};

        startApplication(processScript("/mnt/minecloud/server/bukkit/" + server.type().mod() + "/init.sh", env), server.name(), env);
        repository.save(server);
        MineCloud.logger().info("Started server " + server.name() + " with container id " + server.containerId());
    }

    public static void deployBungee(Network network, BungeeType type) {
        BungeeRepository repository = MineCloud.instance().mongo().repositoryBy(Bungee.class);
        Node node = MineCloudDaemon.instance().node();
        Bungee bungee = new Bungee();

        if (repository.count("_id", node.publicIp()) > 0) {
            MineCloud.logger().log(Level.WARNING, "Did not create bungee on this node; public ip is already in use");
            return;
        }

        bungee.setId(node.publicIp());
        bungee.setType(type);

        Credentials mongoCreds = MineCloud.instance().mongo().credentials();
        Credentials redisCreds = MineCloud.instance().redis().credentials();
        Map<String, String> env = new HashMap<String, String>() {{
            put("mongo_hosts", mongoCreds.formattedHosts());
            put("mongo_username", mongoCreds.username());
            put("mongo_password", new String(mongoCreds.password()));
            put("mongo_database", mongoCreds.database());

            put("redis_host", redisCreds.hosts()[0]);
            put("redis_password", new String(redisCreds.password()));
            put("DEDICATED_RAM", String.valueOf(type.dedicatedRam()));

            put("bungee_id", node.publicIp());
        }};

        startApplication(processScript("/mnt/minecloud/scripts/bungee-init.sh", env), "bungee", env);

        bungee.setNetwork(network);
        bungee.setNode(node);
        bungee.setPublicIp(node.publicIp());

        repository.save(bungee);
        MineCloud.logger().info("Started bungee " + bungee.name() + " with id " + bungee.containerId());
    }

    public static int pidOf(String app) throws IOException {
        return Integer.parseInt(Files.readAllLines(Paths.get("/var/minecloud/" + app + "/app.pid")).get(0));
    }

    public static long timeStarted(String app) throws IOException {
        return Long.parseLong(Files.readAllLines(Paths.get("/var/minecloud/" + app + "/started.ts")).get(0));
    }

    public static void killServer(String name) {
        try (Jedis jedis = MineCloudDaemon.instance().redis().grabResource()) {
            jedis.hdel("server:" + name, "heartbeat");
        }

        try {
            int pid = Deployer.pidOf(name);
            new ProcessBuilder().command("/usr/bin/kill", "-9", String.valueOf(pid)).start();
            MineCloud.logger().info("Killed pid " + pid + " belonging to " + name);
            Deployer.runExit(name);
            MineCloud.logger().info("Executed exit for " + name + " successfully");
        } catch (IOException ignored) {
        }

        try {
            Runtime.getRuntime().exec(("/usr/bin/rm -rf " + new File("/var/minecloud/" + name)).split(" "));
            MineCloud.logger().info("Deleted folder of dead server " + name);
        } catch (IOException ignored) {
        }
    }

    public static void runExit(String app) throws IOException {
        File file = new File("/var/minecloud/" + app + "/exit.sh");

        if (!file.exists()) {
            return;
        }

        new ProcessBuilder()
                .directory(new File("/var/minecloud/" + app))
                .redirectErrorStream(true)
                .command("sh", "exit.sh", app)
                .start();
    }

    public static boolean isRunning(String app) throws InterruptedException, IOException {
        Process process = Runtime.getRuntime().exec("ps -p " + pidOf(app));

        process.waitFor();
        return process.exitValue() == 0;
    }

    private static List<String> processScript(String file, Map<String, String> env) {
        List<String> script;

        try {
            script = Files.readAllLines(Paths.get(file));
        } catch (IOException ex) {
            throw new MineCloudException(ex);
        }

        script.replaceAll((s) -> {
            Container<String> container = new Container<>(s);

            env.forEach((find, replace) -> container.set(container.get().replace("]" + find, replace)));

            return container.get();
        });

        return script;
    }

    private static void startApplication(List<String> startScript, String name, Map<String, String> env) {
        File runDir = new File("/var/minecloud/" + name);

        if (runDir.exists()) {
            runDir.delete();
        }

        runDir.mkdirs();

        try {
            Files.write(Paths.get(runDir.getAbsolutePath(), "start.sh"), startScript);
            Files.write(Paths.get(runDir.getAbsolutePath(), "started.ts"), Arrays.asList(String.valueOf(System.currentTimeMillis())));
            new File(runDir, "start.sh").setExecutable(true);

            ProcessBuilder process = new ProcessBuilder();
            env.forEach((key, value) -> {
            process.environment().put(key, env.get(key));
            });
            process
                    .directory(runDir)
                    .redirectErrorStream(true)
                    .command("/usr/bin/screen", "-dm", "-S", name, "sh", "start.sh")
                    .start();
        } catch (IOException ex) {
            throw new MineCloudException(ex);
        }
    }

    private static class Container<T> {
        private T value;

        public Container(T value) {
            this.value = value;
        }

        public Container() {
            this(null);
        }

        public T get() {
            return value;
        }

        public void set(T value) {
            this.value = value;
        }
    }
}
