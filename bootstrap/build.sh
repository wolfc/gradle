#!/bin/sh
. /usr/share/java-utils/java-functions
set_jvm

LOCALCLASSPATH="$(/usr/bin/build-classpath ant ant-launcher)"

# Explicitly add javac path to classpath, assume JAVA_HOME set
# properly in rpm mode
LOCALCLASSPATH="$LOCALCLASSPATH:$JAVA_HOME/lib/tools.jar"

# pass bypassAndD to the build script to guard proper invocation
LOCALCLASSPATH="$LOCALCLASSPATH" exec /usr/bin/ant -DbypassAntD=true --noconfig $*
