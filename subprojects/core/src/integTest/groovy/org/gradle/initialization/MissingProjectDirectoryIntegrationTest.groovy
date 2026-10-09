/*
 * Copyright 2026 the original author or authors.
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

class MissingProjectDirectoryIntegrationTest extends AbstractIntegrationSpec {

    def "reports a project without an existing directory"() {
        given:
        enableProblemsApiCheck()
        settingsFile """
            include("missing")
        """

        when:
        fails('help')

        then:
        failureDescriptionContains("Configuring project with invalid directory")
        failureDescriptionContains("Configuring project ':missing' without an existing directory is not allowed.")
        verifyAll(receivedProblem) {
            fqid == 'Gradle:Build Definition:Configuring project with invalid directory'
            solutions == ["Make sure the project directory exists and is writable."]
        }
    }
}
