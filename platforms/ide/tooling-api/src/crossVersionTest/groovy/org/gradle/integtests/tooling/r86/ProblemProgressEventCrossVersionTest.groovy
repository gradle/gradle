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

import org.gradle.integtests.fixtures.GroovyBuildScriptLanguage
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.integtests.tooling.r85.ProblemProgressEventCrossVersionTest.ProblemProgressListener
import org.gradle.tooling.BuildException
import org.gradle.util.GradleVersion

@ToolingApiVersion("=8.6")
@TargetGradleVersion(">=8.6")
class ProblemProgressEventCrossVersionTest extends ToolingApiSpecification {

    def withReportProblemTask(@GroovyBuildScriptLanguage String taskActionMethodBody) {
        buildFile getProblemReportTaskString(taskActionMethodBody)
        // TODO using the following code breaks the test, but it should be possible to use it
        //  buildFile getProblemReportingScript(taskActionMethodBody)
        //  issue https://github.com/gradle/gradle/issues/27484
    }

    static String getProblemReportTaskString(String taskActionMethodBody) {
        """
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

    def runTask() {
        def listener = new ProblemProgressListener()
        withConnection { connection ->
            connection.newBuild().forTasks('reportProblem')
                .addProgressListener(listener)
                .run()
        }
        return listener.problems.collect { it.descriptor }
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
        def listener = new ProblemProgressListener()
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

    def "Problems expose summary Tooling API events"() {
        given:
        withReportProblemTask """
            for(int i = 0; i < 10; i++) {
                problems.forNamespace("org.example.plugin").reporting {
                    it.${targetVersion < GradleVersion.version("8.8") ? 'label("The \'standard-plugin\' is deprecated").category("deprecation", "plugin")' : 'id("adhoc-deprecation", "The \'standard-plugin\' is deprecated")' }
                        .severity(Severity.WARNING)
                        .solution("Please use 'standard-plugin-2' instead of this plugin")
                    }
            }
        """

        when:
        def listener = new ProblemProgressListener()
        withConnection { connection ->
            connection.newBuild().forTasks('reportProblem')
                .addProgressListener(listener)
                .run()
        }

        then:
        def problems = listener.problems
        problems.size() == 0
    }

    @TargetGradleVersion(">=8.6 <8.9")
    def "Problems expose details via Tooling API events"() {
        given:
        withReportProblemTask """
            getProblems().forNamespace("org.example.plugin").reporting {
                it.${targetVersion < GradleVersion.version("8.8") ? 'label("shortProblemMessage").category("main", "sub", "id")' : 'id("id", "shortProblemMessage")' }
                $documentationConfig
                .lineInFileLocation("/tmp/foo", 1, 2, 3)
                $detailsConfig
                .additionalData("aKey", "aValue")
                .severity(Severity.WARNING)
                .solution("try this instead")
            }
        """

        when:

        def problems = runTask()

        then:
        problems.size() == 0

        where:
        detailsConfig              | expectedDetails | documentationConfig                         | expecteDocumentation
        '.details("long message")' | "long message"  | '.documentedAt("https://docs.example.org")' | 'https://docs.example.org'
        ''                         | null            | ''                                          | null
    }

    def "Problems expose file locations with file path only"() {
        given:
        withReportProblemTask """
            getProblems().forNamespace("org.example.plugin").reporting {
                it.${targetVersion < GradleVersion.version("8.8") ? 'label("shortProblemMessage").category("main", "sub", "id")' : 'id("id", "shortProblemMessage")' }
                .fileLocation("/tmp/foo")
            }
        """

        when:
        def problems = runTask()

        then:
        problems.size() == 0
    }

    def "Problems expose file locations with path and line"() {
        given:
        withReportProblemTask """
            getProblems().forNamespace("org.example.plugin").reporting {
                it.${targetVersion < GradleVersion.version("8.8") ? 'label("shortProblemMessage").category("main", "sub", "id")' : 'id("id", "shortProblemMessage")' }
                .lineInFileLocation("/tmp/foo", 1)
            }
        """

        when:

        def problems = runTask()

        then:
        problems.size() == 0
    }

    def "Problems expose file locations with path, line and column"() {
        given:
        withReportProblemTask """
                getProblems().forNamespace("org.example.plugin").reporting {
                    it.${targetVersion < GradleVersion.version("8.8") ? 'label("shortProblemMessage").category("main", "sub", "id")' : 'id("id", "shortProblemMessage")' }
                    .lineInFileLocation("/tmp/foo", 1, 2)
                }
        """

        when:
        def problems = runTask()

        then:
        problems.size() == 0
    }

    def "Problems expose file locations with path, line, column and length"() {
        given:
        withReportProblemTask """
            getProblems().forNamespace("org.example.plugin").reporting {
                it.${targetVersion < GradleVersion.version("8.8") ? 'label("shortProblemMessage").category("main", "sub", "id")' : 'id("id", "shortProblemMessage")' }
                .lineInFileLocation("/tmp/foo", 1, 2, 3)
            }
        """

        when:
        def problems = runTask()

        then:
        problems.size() == 0
    }

    def "Problems expose file locations with offset and length"() {
        given:
        withReportProblemTask """
            getProblems().forNamespace("org.example.plugin").reporting {
                it.${targetVersion < GradleVersion.version("8.8") ? 'label("shortProblemMessage").category("main", "sub", "id")' : 'id("id", "shortProblemMessage")' }
                .offsetInFileLocation("/tmp/foo", 20, 10)
            }
        """

        when:
        def problems = runTask()

        then:
        problems.size() == 0
    }
}
