/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.integtests.tooling.r86


import org.gradle.integtests.tooling.fixture.ProblemEventListener
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.events.problems.Severity

@ToolingApiVersion(">=8.7")
@TargetGradleVersion(">=8.7")
class DeprecationLoggingProblemEventsCrossVersionTest extends ToolingApiSpecification {

    def "problems are emitted when a deprecation is logged"() {
        buildFile "org.gradle.internal.deprecation.DeprecationLogger.deprecate('Plugin script').willBeRemovedInGradle9().undocumented().nagUser()"

        when:
        def listener = new ProblemEventListener()
        withConnection { connection ->
            connection.newBuild()
                .forTasks(":help")
                .addProgressListener(listener)
                .setStandardError(System.err)
                .setStandardOutput(System.out)
                .addArguments("--warning-mode", "all")
                .run()
        }

        then:
        def problems = listener.problems
        problems.size() == 1
        def problem = problems[0]
        problem.label.label == 'Plugin script has been deprecated.'
        problem.severity.severity == Severity.WARNING.severity
    }

}
