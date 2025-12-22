#!/bin/bash
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

# Common functions for EC2 build scripts

# This scripts detects builds running on EC2 by accessing the special ip 169.254.169.254 exposed by AWS instances.
# https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/ec2-instance-metadata.html
exit_if_not_on_ec2_instance() {
  curl -m 1 -s "http://169.254.169.254/latest/meta-data/instance-id"
  IS_EC2_INSTANCE=$?
  if [ $IS_EC2_INSTANCE -ne 0 ]; then
    echo "Not running on an EC2 instance, skipping the configuration"
    exit 0
  fi
}

# Function to write and execute a build script from environment variable content
execute_build_script_from_env() {
  local script_content="$1"
  echo "${script_content}" > "${TMPDIR}/pre-or-post-build.sh"
  bash -x "${TMPDIR}/pre-or-post-build.sh"
}
