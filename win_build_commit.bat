@echo off
powershell -NoProfile -Command ^
"$sha = git rev-parse --short HEAD; ./gradlew assembleDebug \"-PGIT_SHA=$sha\""
