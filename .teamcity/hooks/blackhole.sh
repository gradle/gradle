#!/bin/bash
#
# Copyright 2026 the original author or authors.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
# Repository build hook that makes Maven Central and the Gradle Plugin Portal unresolvable
# when BLACKHOLE_MAVEN_CENTRAL=true, so every build or test path that bypasses the repository
# mirrors fails fast. On Linux it compiles blackhole/maven-central-blackhole.c into the build
# temp dir, checks that getent and the JVM honour it, and only then sets env.LD_PRELOAD for
# the build steps. The hook never fails the build: when any of this is not possible it warns
# and does nothing.
#
# Usage: blackhole.sh   -- invoked by the thin pre-build shim.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BLOCKED_HOST="repo.maven.apache.org"
ALLOWED_HOST="repo.grdev.net"

warn() {
  echo "##teamcity[message text='Blackhole Maven Central: $1' status='WARNING']"
}

build_temp_dir() {
  local dir=""
  if [[ -f "${TEAMCITY_BUILD_PROPERTIES_FILE:-}" ]]; then
    dir="$(sed -n 's/^teamcity\.build\.tempDir=//p' "${TEAMCITY_BUILD_PROPERTIES_FILE}" | sed 's/\\\(.\)/\1/g')"
  fi
  if [[ -z "${dir}" || ! -d "${dir}" ]]; then
    dir="$(mktemp -d)"
  fi
  echo "${dir}"
}

if [[ "${BLACKHOLE_MAVEN_CENTRAL:-}" != "true" ]]; then
  echo "Skipping Maven Central blackhole: BLACKHOLE_MAVEN_CENTRAL='${BLACKHOLE_MAVEN_CENTRAL:-}' is not 'true'."
  exit 0
fi

if [[ "$(uname -s)" != "Linux" ]]; then
  warn "only supported on Linux, not $(uname -s), skipping"
  exit 0
fi

if ! command -v cc >/dev/null; then
  warn "no C compiler (cc) on this agent, skipping"
  exit 0
fi

SHIM="$(build_temp_dir)/maven-central-blackhole.so"
if ! cc -shared -fPIC -O2 -o "${SHIM}" "${SCRIPT_DIR}/blackhole/maven-central-blackhole.c" -ldl; then
  warn "compiling the resolver shim failed, skipping"
  exit 0
fi
echo "Compiled resolver shim ${SHIM}."

if ! getent ahosts "${ALLOWED_HOST}" >/dev/null; then
  warn "${ALLOWED_HOST} does not resolve even without the shim, skipping"
  exit 0
fi
if LD_PRELOAD="${SHIM}" getent ahosts "${BLOCKED_HOST}" >/dev/null; then
  warn "getent still resolves ${BLOCKED_HOST} with the shim, skipping"
  exit 0
fi
if ! LD_PRELOAD="${SHIM}" getent ahosts "${ALLOWED_HOST}" >/dev/null; then
  warn "getent cannot resolve ${ALLOWED_HOST} with the shim, skipping"
  exit 0
fi
echo "Verified getent cannot resolve ${BLOCKED_HOST} but resolves ${ALLOWED_HOST}."

JAVA="${JAVA_HOME:+${JAVA_HOME}/bin/}java"
if ! command -v "${JAVA}" >/dev/null; then
  warn "no java found to check the shim with, skipping"
  exit 0
fi
if ! LD_PRELOAD="${SHIM}" "${JAVA}" "${SCRIPT_DIR}/blackhole/ResolveCheck.java" "${BLOCKED_HOST}" "${ALLOWED_HOST}"; then
  warn "the JVM check with ${JAVA} failed, skipping"
  exit 0
fi

echo "##teamcity[setParameter name='env.LD_PRELOAD' value='${SHIM}']"
echo "Maven Central blackhole enabled for the build steps via LD_PRELOAD=${SHIM}."
