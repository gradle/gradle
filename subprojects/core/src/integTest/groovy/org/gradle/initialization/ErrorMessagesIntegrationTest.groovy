/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.initialization

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class ErrorMessagesIntegrationTest extends AbstractIntegrationSpec {

    def "Error message due to bad permissions on project's gradle cache directory is not scary"() {
        given:
        buildScript """
            task hello() {
                doLast {
                    println "Hello Gradle!"
                }
            }
        """
        def localGradleCache = file('.gradle')
        localGradleCache.mkdir()
        localGradleCache.setPermissions("r-xr-xr-x")

        when:
        fails 'hello'

        then:
        errorOutput.contains("FAILURE: Build failed with an exception.\n" +
            "\n" +
            "* What went wrong:\n" +
            "Gradle could not start your build.\n" +
            "> Gradle encountered an internal problem.")
    }
}
