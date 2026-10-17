#!/usr/bin/env bash
set -u

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
APP_DIR="$(dirname -- "$SCRIPT_DIR")"
JAVA_CMD="${JAVA_CMD:-}"
JAR="${APP_JAR:-}"

if [ -z "$JAVA_CMD" ]; then
    if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
        JAVA_CMD="$JAVA_HOME/bin/java"
    else
        JAVA_CMD="$(command -v java 2>/dev/null || true)"
    fi
fi

if [ -z "$JAVA_CMD" ] || [ ! -x "$JAVA_CMD" ]; then
    printf '%s\n' "Error: no se encuentra Java 21. Instala Java 21 o define JAVA_HOME." >&2
    exit 1
fi

if ! "$JAVA_CMD" -version 2>&1 | grep -q 'version "21\|openjdk version "21'; then
    printf '%s\n' "Error: se requiere Java 21 para iniciar la aplicación." >&2
    exit 1
fi

if [ -z "$JAR" ]; then
    JAR="$(find "$APP_DIR/target" -maxdepth 1 -type f -name 'app-*.jar' ! -name '*-sources.jar' ! -name '*-javadoc.jar' -print -quit 2>/dev/null || true)"
fi
if [ -z "$JAR" ] || [ ! -f "$JAR" ]; then
    printf '%s\n' "Error: no se encuentra el JAR de la aplicación en $APP_DIR/target." >&2
    exit 1
fi

LOG_FILE="${BACKGROUND_LOG:-$APP_DIR/target/application.log}"
mkdir -p "$(dirname -- "$LOG_FILE")"
nohup "$JAVA_CMD" -jar "$JAR" "$@" >>"$LOG_FILE" 2>&1 </dev/null &
printf 'Aplicación iniciada en segundo plano (PID %s).\n' "$!"
exit 0
