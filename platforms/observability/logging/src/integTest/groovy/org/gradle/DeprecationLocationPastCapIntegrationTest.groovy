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

import org.gradle.api.logging.configuration.WarningMode
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.internal.featurelifecycle.DeprecatedUsageProgressDetails
import spock.lang.Issue

import static org.gradle.util.internal.TextUtil.normaliseFileSeparators

/**
 * Locations for deprecations past the stack-capture cap come from the cheap bounded caller-stack capture.
 * These tests run in a capping warning mode ({@code summary}) so the cap actually applies and the bounded
 * path is exercised, {@code --warning-mode=all} would lift the cap and capture full stacks instead.
 */
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
        executer.noDeprecationChecks().withWarningMode(WarningMode.Summary)
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
        // Flood one site well past the cap, then 3 distinct lines; the later sites must still each resolve to their own line.
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
        executer.noDeprecationChecks().withWarningMode(WarningMode.Summary)
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

    def "same-named Groovy build files in different projects each resolve to their own file"() {
        given:
        settingsFile "rootProject.name = 'root'\ninclude 'a', 'b'"
        // Each project loops the same deprecation from the same line; combined they pass the cap, so both go
        // through the bounded capture. The build files share a name, so the per-project location must survive:
        // each capture resolves independently to its own script frame.
        def flood = '(1..60).each { org.gradle.internal.deprecation.DeprecationLogger.deprecate("Shared").willBeRemovedInGradle10().withUserManual("feature_lifecycle", "sec:deprecated").nagUser() }'
        file('a/build.gradle') << flood
        file('b/build.gradle') << flood

        when:
        executer.noDeprecationChecks().withWarningMode(WarningMode.Summary)
        succeeds 'help'

        then:
        def stacks = operations.records
            .collectMany { it.progress ?: [] }
            .findAll { it.hasDetailsOfType(DeprecatedUsageProgressDetails) && it.details['deprecation'].summary.contains('Shared') }
            .collect { normaliseFileSeparators(it.details['deprecation'].stackTrace as String) }
        stacks.size() == 120

        and:
        // Each project's deprecations resolve to its own build file.
        stacks.count { it.contains('a/build.gradle') } == 60
        stacks.count { it.contains('b/build.gradle') } == 60
    }

    @Issue("https://github.com/gradle/gradle/issues/11952")
    def "Kotlin DSL deprecations past the cap resolve to the build script"() {
        given:
        // Kotlin stack frames carry only the simple file name (gradle/gradle#11952), so distinguishing
        // same-named scripts across projects is not possible (that is the Groovy case above). Here we just
        // verify the location resolves to the script (build.gradle.kts:line) past the cap.
        settingsFile "rootProject.name = 'root'"
        file('build.gradle.kts') << '(1..120).forEach { org.gradle.internal.deprecation.DeprecationLogger.deprecate("Looped " + it).willBeRemovedInGradle10().withUserManual("feature_lifecycle", "sec:deprecated").nagUser() }'

        when:
        executer.noDeprecationChecks().withWarningMode(WarningMode.Summary)
        succeeds 'help'

        then:
        def stacks = operations.records
            .collectMany { it.progress ?: [] }
            .findAll { it.hasDetailsOfType(DeprecatedUsageProgressDetails) && it.details['deprecation'].summary.contains('Looped') }
            .collect { it.details['deprecation'].stackTrace as String }
        stacks.size() == 120

        and:
        stacks.every { it =~ /build\.gradle\.kts:\d+/ }
    }
}
