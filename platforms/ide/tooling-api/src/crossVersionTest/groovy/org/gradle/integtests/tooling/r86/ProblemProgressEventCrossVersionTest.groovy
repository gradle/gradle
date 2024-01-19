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


import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.BuildException

@ToolingApiVersion(">=8.5")
@TargetGradleVersion(">=8.6")
class ProblemProgressEventCrossVersionTest extends ToolingApiSpecification {

    static String getProblemReportTaskString(String taskActionMethodBody) {
        """
            import org.gradle.api.problems.internal.Problem
            import org.gradle.api.problems.Severity

            abstract class ProblemReportingTask extends DefaultTask {
                @Inject
                protected abstract Problems getProblems();

                @TaskAction
                void run() {
                    $taskActionMethodBody
                }
            }

            tasks.register("reportProblem", ProblemReportingTask)
        """
    }

    @TargetGradleVersion("=8.3")
    def "Older Gradle versions do not report problems"() {
        setup:
        buildFile """
            plugins {
              id 'java-library'
            }
            repositories.jcenter()
            task bar {}
            task baz {}
        """

        when:
        def listener = new org.gradle.integtests.tooling.r85.ProblemsServiceModelBuilderCrossVersionTest.ProblemProgressListener()
        withConnection { connection ->
            connection.newBuild()
                .forTasks(":ba")
                .addProgressListener(listener)
                .setStandardError(System.err)
                .setStandardOutput(System.out)
                .addArguments("--info")
                .run()
        }

        then:
        thrown(BuildException)
        listener.problems.isEmpty()
    }
}
