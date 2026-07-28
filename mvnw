#!/bin/sh
# ----------------------------------------------------------------------------
# Maven Start Up script (self-downloading "only-script" wrapper).
# Requires only a JDK on PATH. First run downloads Maven and caches it under
# ~/.m2/wrapper. Useful if you build under Git Bash / WSL / macOS / Linux.
# ----------------------------------------------------------------------------
set -e

MAVEN_VERSION=3.9.9
WRAPPER_DIR="$HOME/.m2/wrapper/dists/apache-maven-$MAVEN_VERSION"
MVN_CMD="$WRAPPER_DIR/apache-maven-$MAVEN_VERSION/bin/mvn"

if [ ! -x "$MVN_CMD" ]; then
  echo "Downloading Apache Maven $MAVEN_VERSION (first run only)..."
  mkdir -p "$WRAPPER_DIR"
  ZIP="$WRAPPER_DIR/maven.zip"
  URL="https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/$MAVEN_VERSION/apache-maven-$MAVEN_VERSION-bin.zip"
  if command -v curl >/dev/null 2>&1; then
    curl -fsSL "$URL" -o "$ZIP"
  else
    wget -q "$URL" -O "$ZIP"
  fi
  unzip -q "$ZIP" -d "$WRAPPER_DIR"
  rm -f "$ZIP"
fi

exec "$MVN_CMD" "$@"
