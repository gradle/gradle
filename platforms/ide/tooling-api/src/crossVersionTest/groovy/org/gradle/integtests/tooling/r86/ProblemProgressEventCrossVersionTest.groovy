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
import org.gradle.tooling.events.problems.FileLocation
import org.gradle.tooling.events.problems.LineInFileLocation
import org.gradle.tooling.events.problems.OffsetInFileLocation
import org.gradle.tooling.events.problems.ProblemAggregationDescriptor
import org.gradle.tooling.events.problems.ProblemDescriptor
import org.gradle.tooling.events.problems.Severity
import org.gradle.tooling.events.problems.TaskPathLocation
import org.gradle.tooling.events.problems.internal.DefaultProblemsOperationDescriptor

@ToolingApiVersion(">=8.6")
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
                problems.forNamespace("org.example.plugin").reporting{
                    it.label("The 'standard-plugin' is deprecated")
                        .category("deprecation", "plugin")
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
        problems.size() == 2


        def firstProblem = (ProblemDescriptor) problems[0]
        firstProblem.label.label == "The 'standard-plugin' is deprecated"
        firstProblem.details.details == null

        def aggregatedProblems = (ProblemAggregationDescriptor) problems[1]

        def aggregations = aggregatedProblems.aggregations
        aggregations.size() == 1
        aggregations[0].label.label == "The 'standard-plugin' is deprecated"
        aggregations[0].problemDescriptors.size() == 10
    }

    @TargetGradleVersion("=8.6")
    def "Problems expose details via Tooling API events"() {
        given:
        withReportProblemTask """
            getProblems().forNamespace("org.example.plugin").reporting {
                it.label("shortProblemMessage")
                $documentationConfig
                .lineInFileLocation("/tmp/foo", 1, 2, 3)
                .category("main", "sub", "id")
                $detailsConfig
                .additionalData("aKey", "aValue")
                .severity(Severity.WARNING)
                .solution("try this instead")
            }
        """

        when:

        def problems = runTask()

        then:
        assertProblemDetailsForTAPIProblemEvent(problems, expectedDetails, expecteDocumentation)

        where:
        detailsConfig              | expectedDetails | documentationConfig                         | expecteDocumentation
        '.details("long message")' | "long message"  | '.documentedAt("https://docs.example.org")' | 'https://docs.example.org'
        ''                         | null            | ''                                          | null
    }

    static def assertProblemDetailsForTAPIProblemEvent(List<ProblemDescriptor> problems, String expectedDetails = null, String expecteDocumentation = null) {
        problems.size() == 1
        problems[0].category.namespace == 'org.example.plugin'
        problems[0].category.category == 'main'
        problems[0].category.subcategories == ['sub', 'id']
        ((DefaultProblemsOperationDescriptor) problems[0]).additionalData.asMap == ['aKey': 'aValue']
        problems[0].label.label == 'shortProblemMessage'
        problems[0].details.details == expectedDetails
        problems[0].severity == Severity.WARNING
            problems[0].locations.size() == 2
        problems[0].locations[0] instanceof LineInFileLocation
        def lineInFileLocation = problems[0].locations[0] as LineInFileLocation
        lineInFileLocation.path == '/tmp/foo'
        lineInFileLocation.line == 1
        lineInFileLocation.column == 2
        lineInFileLocation.length == 3
        problems[0].locations[1] instanceof TaskPathLocation
        problems[0].documentationLink.url == expecteDocumentation
        problems[0].solutions.size() == 1
        problems[0].solutions[0].solution == 'try this instead'

    }


    def runTask() {
        def listener = new ProblemProgressListener()
        withConnection { connection ->
            connection.newBuild().forTasks('reportProblem')
                .addProgressListener(listener)
                .run()
        }
        return listener.problems.collect { (ProblemDescriptor) it }
    }

    def "Problems expose file locations with file path only"() {
        given:
        withReportProblemTask """
            getProblems().forNamespace("org.example.plugin").reporting {
                        it.label("shortProblemMessage")
                        .category("main", "sub", "id")
                        .fileLocation("/tmp/foo")
                    }
        """

        when:
        def problems = runTask()

        then:
        problems.size() == 1
        FileLocation location = (FileLocation) problems[0].locations.find { it instanceof FileLocation }
        location.path == '/tmp/foo'
    }

    def "Problems expose file locations with path and line"() {
        given:
        withReportProblemTask """
            getProblems().forNamespace("org.example.plugin").reporting {
                it.label("shortProblemMessage")
                .category("main", "sub", "id")
                .lineInFileLocation("/tmp/foo", 1)
            }
        """

        when:

        def problems = runTask()

        then:
        problems.size() == 1
        LineInFileLocation location = (LineInFileLocation) problems[0].locations.find { it instanceof LineInFileLocation }
        location.path == '/tmp/foo'
        location.line == 1
        location.column < 1
        location.length < 0
    }

    def "Problems expose file locations with path, line and column"() {
        given:
        withReportProblemTask """
                getProblems().forNamespace("org.example.plugin").reporting {
                    it.label("shortProblemMessage")
                    .category("main", "sub", "id")
                    .lineInFileLocation("/tmp/foo", 1, 2)
                }
        """

        when:
        def problems = runTask()

        then:
        problems.size() == 1
        LineInFileLocation location = (LineInFileLocation) problems[0].locations.find { it instanceof LineInFileLocation }
        location.path == '/tmp/foo'
        location.line == 1
        location.column == 2
        location.length < 0
    }

    def "Problems expose file locations with path, line, column and length"() {
        given:
        withReportProblemTask """
            getProblems().forNamespace("org.example.plugin").reporting {
                it.label("shortProblemMessage")
                .category("main", "sub", "id")
                .lineInFileLocation("/tmp/foo", 1, 2, 3)
            }
        """

        when:
        def problems = runTask()

        then:
        problems.size() == 1
        LineInFileLocation location = (LineInFileLocation) problems[0].locations.find { it instanceof LineInFileLocation }
        location.path == '/tmp/foo'
        location.line == 1
        location.column == 2
        location.length == 3
    }

    def "Problems expose file locations with offset and length"() {
        given:
        withReportProblemTask """
            getProblems().forNamespace("org.example.plugin").reporting {
                it.label("shortProblemMessage")
                .category("main", "sub", "id")
                .offsetInFileLocation("/tmp/foo", 20, 10)
            }
        """

        when:
        def problems = runTask()

        then:
        problems.size() == 1
        OffsetInFileLocation location = (OffsetInFileLocation) problems[0].locations.find { it instanceof OffsetInFileLocation }
        location.path == '/tmp/foo'
        location.offset == 20
        location.length == 10
    }
}
