#!/bin/bash
# Copyright 2024 The Project Authors
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
set -euo pipefail

# Source common functions
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

exit_if_not_on_ec2_instance

if [[ "${BUILD_TYPE_ID:-}" == Gradle_Xperimental* ]]; then
  execute_build_script_from_env "${XPERIMENTAL_EC2_POST_BUILD_SCRIPT:-}"
elif [[ "${BUILD_TYPE_ID:-}" == Gradle_Master* ]]; then
  execute_build_script_from_env "${MASTER_EC2_POST_BUILD_SCRIPT:-}"
fi
