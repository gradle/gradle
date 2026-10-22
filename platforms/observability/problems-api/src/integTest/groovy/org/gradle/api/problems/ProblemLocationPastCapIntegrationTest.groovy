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

package org.gradle.api.problems

import org.gradle.api.logging.configuration.WarningMode
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.operations.problems.ProblemUsageProgressDetails
import spock.lang.Issue

/**
 * Past the stack-capture cap, locations are inferred from a cheap bounded caller-stack capture rather than
 * a full stack. This pins that path end to end across warning modes: {@code summary} keeps the default
 * bounded budget, {@code all} lifts it, but both keep the full-stack cap at 50, so problems past it take the
 * bounded path either way. The location must resolve to the calling script by seeing through a build-logic
 * helper, keeping that helper frame as context.
 */
@Issue("https://github.com/gradle/gradle/issues/32362")
class ProblemLocationPastCapIntegrationTest extends AbstractIntegrationSpec {

    def buildOperations = new BuildOperationsFixture(executer, testDirectoryProvider)

    def "problems past the stack-capture cap resolve to the calling script and keep the build-logic frame for warning mode #warningMode"() {
        given:
        // A compiled helper (build logic, not a script) reports the problem. The bounded capture must see
        // through it to the calling script for the location, and keep the helper frame above it.
        file('buildSrc/src/main/java/org/example/ProblemEmitter.java') << '''
            package org.example;
            import org.gradle.api.problems.ProblemReporter;
            import org.gradle.api.problems.ProblemId;
            import org.gradle.api.problems.ProblemGroup;

            public class ProblemEmitter {
                public static void emit(ProblemReporter reporter, String id) {
                    reporter.report(ProblemId.create(id, id, ProblemGroup.create("demo", "demo group")), spec -> {});
                }
            }
        '''
        settingsFile "rootProject.name = 'root'"
        // Cap is 50; fire well past it with distinct problem ids, one call per line, so the summarizer keeps
        // them individual and each resolves to its own line.
        def calls = (1..120).collect { "org.example.ProblemEmitter.emit(reporter, \"issue-$it\")" }.join("\n")
        buildFile """
            def reporter = services.get(${Problems.name}).getReporter()
            $calls
        """

        when:
        // Both modes keep the full cap at 50; problems past it take the bounded path (all lifts only the bounded budget).
        executer.withWarningMode(warningMode)
        succeeds 'help'

        then:
        def problems = buildOperations.progress(ProblemUsageProgressDetails).details
            .findAll { it.definition.name.startsWith('issue-') }
        problems.size() == 120

        and:
        // The stack-trace location each problem resolved to (origin or contextual).
        def stackLocations = problems
            .collect { (it.originLocations + it.contextualLocations).find { loc -> loc.containsKey('stackTrace') } }
            .findAll { it != null }
        // The bounded captures are cut at the script: the deepest kept frame is the build file itself
        // (the frame carries the script's full path as its file name).
        def bounded = stackLocations.findAll { it.stackTrace && it.stackTrace[-1].fileName?.endsWith('build.gradle') }
        // We genuinely exceeded the 50 cap, so the bounded path was exercised.
        bounded.size() > 0

        and:
        bounded.every { loc ->
            // The location sees through the helper to the calling script.
            loc.fileLocation.path == buildFile.absolutePath &&
                // The build-logic helper frame is kept as context above the script.
                loc.stackTrace.any { it.className == 'org.example.ProblemEmitter' && it.methodName == 'emit' } &&
                // Nothing below the script is kept: the reduced stack never descends into the Gradle runtime.
                !loc.stackTrace.any { it.className == 'java.lang.Thread' }
        }

        and:
        // Each call site resolves to its own line; without a cache nothing can be conflated to one location.
        bounded.collect { it.fileLocation.line }.unique().size() == bounded.size()

        where:
        warningMode << [WarningMode.Summary, WarningMode.All]
    }
}
