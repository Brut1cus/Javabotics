#!/usr/bin/env bash
# Builds dist/Javabotics.jar with plain JDK 21 tools (no Maven/Gradle needed).
set -euo pipefail
cd "$(dirname "$0")"
rm -rf build
mkdir -p build/classes dist
javac --release 21 -Xlint:all,-serial -d build/classes $(find src -name '*.java')
jar --create --file dist/Javabotics.jar --main-class javabotics.Main -C build/classes .
echo "Built dist/Javabotics.jar  (run: java -jar dist/Javabotics.jar [seed])"
