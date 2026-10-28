#!/usr/bin/env bash
cd "$(dirname "$0")"
[ -f bin/campusmesh/web/CampusMeshWebRunner.class ] || ./build.sh || exit 1
exec java -cp "bin:lib/*" campusmesh.web.CampusMeshWebRunner 8080 "${1:-LAPTOP_LINUX}" --public
