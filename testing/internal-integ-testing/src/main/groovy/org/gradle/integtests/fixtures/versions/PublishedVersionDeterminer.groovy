/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.integtests.fixtures.versions

import groovy.json.JsonSlurper
import org.gradle.api.GradleException

class PublishedVersionDeterminer {
    private static String latestNightlyVersion
    private static String latestReleasedVersion

    private static String commonUrl = "https://services.gradle.org/versions"

    static String getLatestNightlyVersion() {
        if (latestNightlyVersion == null) {
            latestNightlyVersion = fetchVersion("$commonUrl/nightly").version
        }
        latestNightlyVersion
    }

    static String getLatestReleasedVersion() {
        if (latestReleasedVersion == null) {
            latestReleasedVersion = fetchVersion("$commonUrl/current").version
        }
        latestReleasedVersion
    }

    private static Map fetchVersion(String url) {
        try {
            def response = new URL(url).text
            def version = new JsonSlurper().parseText(response)
            if (!version || !version.version) {
                throw new GradleException("Unexpected response format from $url: $response")
            }
            return version
        } catch (IOException e) {
            throw new GradleException("Failed to fetch version from $url. Check your internet connection.", e)
        }
    }
}
