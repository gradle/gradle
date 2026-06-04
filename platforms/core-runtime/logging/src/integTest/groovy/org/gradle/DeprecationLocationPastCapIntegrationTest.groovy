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
        // Cap is 50; fire well past it.
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
        // Every one, even past the cap, resolves to build.gradle.
        deprecations.every { (it.details['deprecation'].stackTrace as String).contains('build.gradle') }
    }

    def "distinct call sites fired past the cap each resolve to their own line"() {
        given:
        settingsFile "rootProject.name = 'root'"
        // Flood one site past the cap, then 3 distinct lines. Flood must not starve them.
        buildFile """
            (1..80).each { n ->
                org.gradle.internal.deprecation.DeprecationLogger.deprecate("Flooded site " + n)
                    .willBeRemovedInGradle10().withUserManual("feature_lifecycle", "sec:deprecated").nagUser()
            }
            org.gradle.internal.deprecation.DeprecationLogger.deprecate("Site A").willBeRemovedInGradle10().withUserManual("feature_lifecycle", "sec:deprecated").nagUser()
            org.gradle.internal.deprecation.DeprecationLogger.deprecate("Site B").willBeRemovedInGradle10().withUserManual("feature_lifecycle", "sec:deprecated").nagUser()
            org.gradle.internal.deprecation.DeprecationLogger.deprecate("Site C").willBeRemovedInGradle10().withUserManual("feature_lifecycle", "sec:deprecated").nagUser()
        """

        when:
        executer.noDeprecationChecks()
        succeeds 'help'

        then:
        def deprecations = operations.only("Apply build file 'build.gradle' to root project 'root'")
            .progress.findAll { it.hasDetailsOfType(DeprecatedUsageProgressDetails) }
        def lineOf = { label ->
            def stack = deprecations.find { it.details['deprecation'].summary.contains(label) }.details['deprecation'].stackTrace as String
            def matcher = stack =~ /build\.gradle:(\d+)/
            matcher ? matcher[0][1] as int : null
        }

        and:
        // 3 distinct sites, 3 distinct lines.
        ['Site A', 'Site B', 'Site C'].collect { lineOf(it) }.findAll { it != null }.unique().size() == 3
    }
}
