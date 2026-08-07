#!/bin/sh

PRG="$0"
while [ -h "$PRG" ]; do
  ls=$(ls -ld "$PRG")
  link=$(expr "$ls" : '.*-> \(.*\)$')
  if expr "$link" : '/.*' > /dev/null; then
    PRG="$link"
  else
    PRG=$(dirname "$PRG")/$link
  fi
done
SAVED="$(pwd)"
cd "$(dirname "$PRG")/" >/dev/null
APP_HOME="$(pwd -P)"
cd "$SAVED" >/dev/null

APP_NAME="Gradle"
APP_BASE_NAME=$(basename "$0")

DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'

MAX_FD="maximum"

warn () {
  echo "$*"
} >&2

die () {
  echo
  echo "$*"
  echo
  exit 1
} >&2

OS_NAME=$(uname -s)
case "$OS_NAME" in
  CYGWIN* | MINGW* | MSYS* )
    IS_WINDOWS=true
    ;;
esac

CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

if [ -n "$JAVA_HOME" ]; then
  JAVA_EXEC="$JAVA_HOME/bin/java"
else
  JAVA_EXEC="java"
fi

if [ ! -x "$JAVA_EXEC" ]; then
  die "ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH."
fi

if [ ! -f "$CLASSPATH" ]; then
  die "ERROR: Could not find gradle-wrapper.jar. Please run 'gradle wrapper' to generate it."
fi

exec "$JAVA_EXEC" \
  $DEFAULT_JVM_OPTS \
  $JAVA_OPTS \
  $GRADLE_OPTS \
  "-Dorg.gradle.appname=$APP_BASE_NAME" \
  -classpath "$CLASSPATH" \
  org.gradle.wrapper.GradleWrapperMain \
  "$@"
