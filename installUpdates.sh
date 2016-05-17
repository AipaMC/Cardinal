#!/bin/bash

echo "Updating Repo..."
git pull

echo "Building from source..."
mvn clean package
echo "Build complete... applying updates"

echo "Updating daemon..."
/etc/init.d/minecloud-daemon stop
cp daemon-bash/target/daemon-bash-1.0.jar /opt/minecloud/daemon/bin/daemon.jar
/etc/init.d/minecloud-daemon start

echo "Updating controller..."
/etc/init.d/minecloud-controller stop
cp controller/target/controller-1.0.jar /opt/minecloud/controller/bin/controller.jar
/etc/init.d/minecloud-controller start

echo "Updating cli..."
cp cli/target/cli-1.0.jar /opt/minecloud/cli/bin/cli.jar

echo "Updating plugins..."
cp bukkit/target/bukkit-1.0.jar /mnt/minecloud/plugins/minecloud-bukkit/latest/minecloud-bukkit.jar
cp bungee/target/bungee-1.0.jar /mnt/minecloud/plugins/minecloud-bungee/latest/minecloud-bungee.jar

echo "Update complete!"
