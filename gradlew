#!/bin/sh

#
# Gradle start-up script (minimal POSIX wrapper) for Chemopunk RPG.
#
# Requires JAVA_HOME or `java` on PATH, and gradle/wrapper/gradle-wrapper.jar.
# If the jar is missing, regenerate the wrapper once with a local Gradle
# installation (`gradle wrapper --gradle-version 9.6.0`) or open the project
# in Android Studio, which restores the wrapper automatically. See README.md.
#

APP_HOME=$(cd "$(dirname "$0")" && pwd)
WRAPPER_JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

if [ -n "$JAVA_HOME" ]; then
  JAVACMD="$JAVA_HOME/bin/java"
else
  JAVACMD=$(command -v java)
fi

if [ -z "$JAVACMD" ] || [ ! -x "$JAVACMD" ]; then
  echo "ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH." >&2
  exit 1
fi

if [ ! -f "$WRAPPER_JAR" ]; then
  echo "ERROR: gradle-wrapper.jar not found at $WRAPPER_JAR" >&2
  echo "Regenerate the wrapper once with a local Gradle installation:" >&2
  echo "    gradle wrapper --gradle-version 9.6.0" >&2
  exit 1
fi

CLASSPATH="$WRAPPER_JAR"

# Git Bash / MSYS: convert to a Windows path that java.exe understands.
if [ -n "$MSYSTEM" ] && command -v cygpath >/dev/null 2>&1; then
  CLASSPATH=$(cygpath -w "$CLASSPATH")
fi

exec "$JAVACMD" \
  $GRADLE_OPTS \
  $JAVA_OPTS \
  -Dorg.gradle.appname=gradlew \
  -classpath "$CLASSPATH" \
  org.gradle.wrapper.GradleWrapperMain \
  "$@"
