#!/bin/bash
set -euo pipefail
#
# Copyright 2024 the original author or authors.
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

# Source common functions
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

# In case of running on EC2
# - we add a TeamCity tag to the build with an instance type of the EC2 instance.
# - we set GRADLE_RO_DEP_CACHE to '/opt/gradle-cache' if the folder exists.

exit_if_not_on_ec2_instance

# Execute pre-build script based on BUILD_TYPE_ID
if [[ "${BUILD_TYPE_ID:-}" == Gradle_Xperimental* ]]; then
  execute_build_script_from_env "${XPERIMENTAL_EC2_PRE_BUILD_SCRIPT:-}"
elif [[ "${BUILD_TYPE_ID:-}" == Gradle_Master* ]]; then
  execute_build_script_from_env "${MASTER_EC2_PRE_BUILD_SCRIPT:-}"
fi

# TAG
EC2_INSTANCE_TYPE=$(curl -s "http://169.254.169.254/latest/meta-data/instance-type")
echo "##teamcity[addBuildTag 'ec2-instance-type=$EC2_INSTANCE_TYPE']"

AWS_REGION=$(curl -s "http://169.254.169.254/latest/meta-data/placement/region")

if [[ "$AWS_REGION" == us-* ]]; then
  echo "For $AWS_REGION switching to user teamcityus access token"
  echo "##teamcity[setParameter name='env.DEVELOCITY_ACCESS_KEY' value='%ge.gradle.org.access.key.us%;%gbt-td.grdev.net.access.key%']"
fi

# READ-ONLY DEPENDENCY CACHE
if [ -d "/opt/gradle-cache" ]; then
  echo "##teamcity[setParameter name='env.GRADLE_RO_DEP_CACHE' value='/opt/gradle-cache']"
  echo "Setting READ_ONLY Gradle cache via env.GRADLE_RO_DEP_CACHE to use /opt/gradle-cache"
fi

# Print details of volumes to help us understand https://github.com/gradle/gradle-private/issues/4642
df -T
