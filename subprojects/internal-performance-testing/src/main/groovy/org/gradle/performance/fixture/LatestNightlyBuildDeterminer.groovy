/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.performance.fixture

import groovy.json.JsonSlurper
import org.gradle.api.GradleException

class LatestNightlyBuildDeterminer {
    private static String latestNightlyVersion

    static String getLatestNightlyVersion() {
        if (latestNightlyVersion == null) {
            def version = new JsonSlurper().parseText(new URL("https://services.gradle.org/versions/nightly").text)
            if (version.empty) {
                throw new GradleException("Cannot determine latest nightly wrapper!")
            }
            latestNightlyVersion = version.version
        }
        latestNightlyVersion
    }
}
