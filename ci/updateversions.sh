#!/bin/bash

VERSION=1.2.13
PREVIOUS_VERSION=1.2.12

# update version numbers
echo "Updating version numbers to $VERSION"
#mvn versions:set -DnewVersion=$VERSION
sed "s/version>${PREVIOUS_VERSION}/version>${VERSION}/" -i pom.xml
sed "s/VERSION = \"${PREVIOUS_VERSION}\";/VERSION = \"${VERSION}\";/" -i src/main/java/com/red5pro/ice/Agent.java
