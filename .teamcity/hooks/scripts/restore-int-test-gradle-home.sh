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

# Restores the integration-test Gradle user homes (intTestHomeDir) of smoke tests from the
# artifact cache. The CI agent publishes the artifact-cache contract's path as
# GRADLE_UNIVERSAL_CACHE_CLI_PATH; without it (local builds, non-EC2 agents) this does nothing.
# The CLI also writes ~/.m2/.develocity; it is removed at the end so the post-build check
# does not report it as pollution.

set -euo pipefail

# Only smoke tests share the integration-test Gradle user home, so only they benefit here.
if [[ "${BUILD_TYPE_ID:-}" != *SmokeTest* ]]; then
  echo "Skipping artifact cache: BUILD_TYPE_ID='${BUILD_TYPE_ID:-}' is not a SmokeTest build."
  exit 0
fi

# The CI agent provides the artifact-cache contract; without it (local/non-EC2) do nothing.
if [[ ! -x "${GRADLE_UNIVERSAL_CACHE_CLI_PATH:-}" ]]; then
  echo "Skipping artifact cache: GRADLE_UNIVERSAL_CACHE_CLI_PATH is not set or not executable."
  exit 0
fi

INT_TEST_HOME_DIR="$(pwd)/intTestHomeDir"
# Homes to restore.
INT_TEST_DISTRIBUTIONS="${INT_TEST_DISTRIBUTIONS:-full jvm basics native}"

# Image name, stable across builds of the same base branch (release vs master).
if [[ "${BUILD_TYPE_ID:-}" == Gradle_Release* ]]; then
  baseBranch="release"
else
  baseBranch="master"
fi

for dist in ${INT_TEST_DISTRIBUTIONS}; do
  gradleHome="${INT_TEST_HOME_DIR}/distributions-${dist}"
  imageName="gradle-${BUILD_TYPE_ID}-${baseBranch}-distributions-${dist}"

  mkdir -p "${gradleHome}"
  echo "Restoring integration-test Gradle user home '${gradleHome}' from image '${imageName}'..."
  # Best-effort: a miss or error just means tests download as before.
  "${GRADLE_UNIVERSAL_CACHE_CLI_PATH}" --restore "${imageName}" "${gradleHome}" || true

  # The Artifact Cache CLI unconditionally injects an init script
  # (init.d/develocity-inject-restore-metrics.gradle) into every restored Gradle home to
  # report cache metrics to the build scan; there is no CLI flag to disable it. Smoke tests
  # reuse this home for their *nested* builds, where that script runs too. It is incompatible
  # with the older Develocity plugin versions the smoke tests exercise (it NPEs on a null
  # buildScan), and its stack trace pollutes the nested build output, which the smoke-test
  # runner rejects. We only want the dependency cache here, not the metrics instrumentation,
  # so remove the injected init script after restore.
  rm -f "${gradleHome}/init.d/develocity-inject-restore-metrics.gradle"
done

# Remove the ~/.m2/.develocity the CLI wrote, so it is not reported as pollution post-build.
rm -rf "${HOME}/.m2/.develocity"
