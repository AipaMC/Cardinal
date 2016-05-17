#!/bin/bash

echo "Updating Repo..."
git pull

echo "Building from source..."
mvn clean package
echo "Build complete... applying updates"

echo "Updating daemon..."
service minecloud-daemon stop
cp daemon-bash/target/daemon-bash-1.0.jar /opt/minecloud/daemon/bin/daemon.jar
service minecloud-daemon start

echo "Updating cli..."
cp cli/target/cli-1.0.jar /opt/minecloud/cli/bin/cli.jar

echo "Update complete!"
