#!/usr/bin/env bash
sha=$(git rev-parse --short HEAD)
./gradlew assembleDebug -PGIT_SHA="$sha"
