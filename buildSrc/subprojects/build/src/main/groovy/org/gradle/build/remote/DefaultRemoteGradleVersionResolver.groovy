/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.build.remote

import groovy.json.JsonSlurper
import org.gradle.api.GradleException
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging

class DefaultRemoteGradleVersionResolver implements RemoteGradleVersionResolver {

    private static final Logger LOGGER = Logging.getLogger(DefaultRemoteGradleVersionResolver)
    public static final String BASE_URL = 'https://services.gradle.org/versions'

    Object getVersionAsJson(VersionType versionType) {
        URL url = new URL("$BASE_URL/$versionType.type")
        LOGGER.info("Retrieving Gradle versions via $url")

        def version = new JsonSlurper().parseText(new URL("$BASE_URL/$versionType.type").text)

        if (version.empty) {
            throw new GradleException("Cannot resolve version of type $versionType.type")
        }

        version
    }
}
