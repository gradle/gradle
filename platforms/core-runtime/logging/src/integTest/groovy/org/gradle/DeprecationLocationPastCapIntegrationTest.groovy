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

package org.gradle

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.internal.featurelifecycle.DeprecatedUsageProgressDetails

class DeprecationLocationPastCapIntegrationTest extends AbstractIntegrationSpec {

    def operations = new BuildOperationsFixture(executer, temporaryFolder)

    def "deprecations past the stacktrace capture cap still get a location"() {
        given:
        settingsFile "rootProject.name = 'root'"
        // The default-mode capture cap is 50; fire well past it from the build script.
        buildFile """
            (1..120).each { n ->
                org.gradle.internal.deprecation.DeprecationLogger.deprecate("Looped deprecation " + n)
                    .willBeRemovedInGradle10()
                    .withUserManual("feature_lifecycle", "sec:deprecated")
                    .nagUser()
            }
        """

        when:
        executer.noDeprecationChecks()
        succeeds 'help'

        then:
        def deprecations = operations.only("Apply build file 'build.gradle' to root project 'root'")
            .progress.findAll { it.hasDetailsOfType(DeprecatedUsageProgressDetails) }
        deprecations.size() == 120

        and:
        // Before the bounded-walk change, deprecations past the cap had an empty stack and no location.
        // Now every one, including those past the cap, resolves to the build script.
        deprecations.every { (it.details['deprecation'].stackTrace as String).contains('build.gradle') }
    }
}
