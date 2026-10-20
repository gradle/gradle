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
# Diagnostics only: inventories the dependency cache in the Gradle user home right after the
# agent's artifact-cache restore (pre) and right before its store (post), and writes the result
# to artifact-cache-report/diagnostics, which TeamCity already publishes. Never fails the build.
#
# Usage: inventory-gradle-home.sh (pre|post)

set -uo pipefail

PHASE="${1:-}"
GUH="${GRADLE_USER_HOME:-${HOME}/.gradle}"
M2="${GUH}/caches/modules-2"
OUT="$(pwd)/artifact-cache-report/diagnostics"
R="${OUT}/${PHASE}"
mkdir -p "${R}"

say() { echo "[ac-diag:${PHASE}] $*"; }

{
  echo "date=$(date -u +%FT%TZ) host=$(hostname) user=$(id -un)"
  echo "GRADLE_USER_HOME=${GUH}"
  echo "BUILD_TYPE_ID=${BUILD_TYPE_ID:-} TEAMCITY_BUILD_FINISHED_STATUS=${TEAMCITY_BUILD_FINISHED_STATUS:-}"
  for v in DEVELOCITY_ARTIFACT_CACHE_BASE_BRANCH_NAME DEVELOCITY_ARTIFACT_CACHE_INCLUDE_GRADLE_CONTENT \
           DV_ARTIFACT_CACHE_RESTORE_SUCCESS DV_ARTIFACT_CACHE_RESTORE_MISSING DV_ARTIFACT_CACHE_RESTORE_ERROR \
           GRADLE_RO_DEP_CACHE; do
    echo "${v}=${!v:-<unset>}"
  done
  echo "--- env var names mentioning ARTIFACT_CACHE"
  env | cut -d= -f1 | grep -i artifact_cache | sort
  echo "--- restore metrics.properties"
  cat "${GUH}/.develocity/artifact-cache/restore/metrics.properties" 2>&1
} > "${R}/context.txt" 2>&1

{
  for d in "${GUH}" "${GUH}/caches" "${M2}" "${GUH}/caches/journal-1"; do
    echo "===== ${d}"
    ls -la --time-style=full-iso "${d}" 2>&1
  done
  for d in "${M2}"/metadata-2.*; do
    [ -d "${d}" ] || { echo "===== no metadata-2.* directory"; continue; }
    echo "===== ${d}"
    ls -la --time-style=full-iso "${d}"
    echo "descriptor files: $(find "${d}/descriptors" -type f 2>/dev/null | wc -l)"
    echo "descriptor dirs:  $(find "${d}/descriptors" -mindepth 2 -maxdepth 2 -type d 2>/dev/null | wc -l)"
  done
  echo "===== lock and gc files under modules-2"
  find "${M2}" -maxdepth 2 \( -name '*.lock' -o -name '*.lck' -o -name 'gc.properties' \) -printf '%s\t%TY-%Tm-%Td %TT\t%P\n' 2>&1
  if [ -f "${M2}/modules-2.lock" ]; then
    echo "modules-2.lock bytes:"
    od -An -tx1 "${M2}/modules-2.lock"
  fi
  echo "===== files-2.1"
  echo "groups:          $(find "${M2}/files-2.1" -mindepth 1 -maxdepth 1 -type d 2>/dev/null | wc -l)"
  echo "module versions: $(find "${M2}/files-2.1" -mindepth 3 -maxdepth 3 -type d 2>/dev/null | wc -l)"
  echo "files:           $(find "${M2}/files-2.1" -type f 2>/dev/null | wc -l)"
  echo "===== resources-2.1"
  echo "files:           $(find "${M2}/resources-2.1" -type f 2>/dev/null | wc -l)"
} > "${R}/layout.txt" 2>&1

find "${M2}" -path "${M2}/files-2.1" -prune -o -type f -printf '%P\t%s\t%T@\n' 2>/dev/null | sort > "${R}/metadata-manifest.tsv"
find "${M2}" -maxdepth 3 -name '*.bin' -type f -exec sha256sum {} + 2>/dev/null | sort -k2 > "${R}/bin-sha256.txt"
find "${M2}/files-2.1" -type f -printf '%P\n' 2>/dev/null | sort | gzip > "${R}/files-2.1.txt.gz"

{
  for coords in \
    org.sonatype.oss/oss-parent/7 \
    org.jboss/jboss-parent/43 \
    org.jboss/jboss-parent/44 \
    net.java/jvnet-parent/5 \
    com.gradle.develocity/com.gradle.develocity.gradle.plugin/4.6.0 \
    org.gradle.kotlin.kotlin-dsl/org.gradle.kotlin.kotlin-dsl.gradle.plugin/6.7.11 \
    org.gradle.toolchains.foojay-resolver-convention/org.gradle.toolchains.foojay-resolver-convention.gradle.plugin/1.0.0 \
    com.gradleup.shadow/com.gradleup.shadow.gradle.plugin/9.5.1; do
    echo "===== ${coords}"
    find "${M2}/files-2.1/${coords}" -type f -printf '%P\n' 2>&1
    for d in "${M2}"/metadata-2.*/descriptors/"${coords}"; do
      [ -d "${d}" ] && find "${d}" -type f -printf 'descriptor: %P\n'
    done
  done
} > "${R}/probe-recurring-asc-lookups.txt" 2>&1

if [ "${PHASE}" = "post" ] && [ -f "${OUT}/pre/metadata-manifest.tsv" ]; then
  awk -F'\t' '
    NR == FNR { pre[$1] = $2; next }
    { post[$1] = $2 }
    END {
      for (p in post) if (!(p in pre)) print "added\t" p "\t" post[p]
      for (p in pre) if (!(p in post)) print "removed\t" p "\t" pre[p]
      for (p in post) if ((p in pre) && pre[p] != post[p]) print "resized\t" p "\t" pre[p] " -> " post[p]
    }' "${OUT}/pre/metadata-manifest.tsv" "${R}/metadata-manifest.tsv" | sort > "${OUT}/metadata-diff.tsv"
  diff "${OUT}/pre/bin-sha256.txt" "${R}/bin-sha256.txt" > "${OUT}/bin-sha256.diff" 2>&1
  grep -rhi -E "not unlocked cleanly|invalidat|rebuild|corrupt|discard|cache.*(reset|recreat)" \
    "${GUH}"/daemon/*/daemon-*.log 2>/dev/null | head -200 > "${OUT}/daemon-log-cache-messages.txt"
fi

say "Gradle user home: ${GUH}"
for d in "${M2}"/metadata-2.*; do
  [ -d "${d}" ] || { say "NO metadata-2.* directory under ${M2}"; continue; }
  say "$(basename "${d}"): $(find "${d}/descriptors" -type f 2>/dev/null | wc -l) descriptor files"
  for b in "${d}"/*.bin; do
    [ -f "${b}" ] && say "  $(basename "${b}") $(stat -c '%s bytes, modified %y' "${b}")"
  done
done
say "files-2.1: $(find "${M2}/files-2.1" -type f 2>/dev/null | wc -l) files; resources-2.1: $(find "${M2}/resources-2.1" -type f 2>/dev/null | wc -l) files"
say "lock files: $(find "${M2}" -maxdepth 2 \( -name '*.lock' -o -name '*.lck' \) -printf '%P ' 2>/dev/null)"
if [ "${PHASE}" = "post" ] && [ -f "${OUT}/metadata-diff.tsv" ]; then
  say "metadata changes during build: $(cut -f1 "${OUT}/metadata-diff.tsv" | sort | uniq -c | tr '\n' ' ')"
  say "index .bin changes: $(grep -c '^[<>]' "${OUT}/bin-sha256.diff" 2>/dev/null) diff lines"
  say "daemon log cache messages: $(wc -l < "${OUT}/daemon-log-cache-messages.txt")"
fi
say "details in artifact-cache-report/diagnostics/${PHASE}"
exit 0
