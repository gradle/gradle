#!/usr/bin/env bash
# Deletes the recorded task duration history of every test build in this directory, including nested builds
set -euo pipefail
cd "$(dirname "$0")"
find . -path '*/.gradle/task-durations.txt*' -print -delete
