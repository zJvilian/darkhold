#!/bin/sh
if [ "$(id -u)" = 0 ]; then
    # Volumes created by older root images are root-owned: fix them, then drop root.
    chown -R kahvoid:kahvoid /app/logs /app/question-images /app/user-photos || exit 1
    exec setpriv --reuid=kahvoid --regid=kahvoid --init-groups java ${JAVA_OPTS:-} -jar /app/app.jar "$@"
fi
exec java ${JAVA_OPTS:-} -jar /app/app.jar "$@"
