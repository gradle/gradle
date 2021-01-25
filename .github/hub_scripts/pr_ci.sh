#!/usr/bin/env sh

#
# Copyright 2021 the original author or authors.
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

#
# Simple shell script for launching CI jobs using the @bot-gradle GitHub comment listener.
# Uses the [hub](https://hub.github.com/) CLI command to issue API requests to GitHub.
# This script must be executed from the branch associated with the PR to issue the command on.
#

PR_NUMBER=$(hub pr show -f '%I')
hub api repos/gradle/gradle/issues/"$PR_NUMBER"/comments -f body="@bot-gradle test $1" | python -m json.tool
