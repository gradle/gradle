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

# Reports Maven state left in the user home as a build problem unless CHECK_CLEAN_USER_HOME=false.
# A failing post-build hook does not fail the build, so this emits a buildProblem service message.

set -uo pipefail

# Case-insensitive like the .bat counterpart (`if /I`); macOS bash 3.2 has no ${var,,}.
check_clean_user_home="$(printf '%s' "${CHECK_CLEAN_USER_HOME:-true}" | tr '[:upper:]' '[:lower:]')"

for dir in \
  "${HOME}/.m2/repository" \
  "${HOME}/.m2/.gradle-enterprise" \
  "${HOME}/.m2/.develocity"; do
  if [[ -n "$(ls -A "${dir}" 2>/dev/null)" ]]; then
    tree "${dir}" || ls -laR "${dir}"
    echo "${dir} was polluted during the build"
    if [[ "${check_clean_user_home}" != "false" ]]; then
      echo "##teamcity[buildProblem description='${dir} was polluted during the build']"
    fi
  fi
done
