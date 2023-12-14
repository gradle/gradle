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

import groovy.json.JsonSlurper
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.BuildException
import org.gradle.tooling.events.ProgressEvent
import org.gradle.tooling.events.ProgressListener
import org.gradle.tooling.events.problems.BaseProblemDescriptor
import org.gradle.tooling.events.problems.FileLocation
import org.gradle.tooling.events.problems.LineInFileLocation
import org.gradle.tooling.events.problems.OffsetInFileLocation
import org.gradle.tooling.events.problems.ProblemAggregationDescriptor
import org.gradle.tooling.events.problems.ProblemDescriptor
import org.gradle.tooling.events.problems.ProblemEvent
import org.gradle.tooling.events.problems.Severity
import org.gradle.tooling.events.problems.TaskPathLocation
import org.gradle.tooling.events.problems.internal.DefaultProblemsOperationDescriptor

@ToolingApiVersion(">=8.5")
@TargetGradleVersion(">=8.6")
class ProblemProgressEventCrossVersionTest extends ToolingApiSpecification {

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

    @ToolingApiVersion("=8.5")
    def "Gradle 8.5 exposes problem events via JSON strings"() {
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
        def problems = listener.problems.collect { new JsonSlurper().parseText(it.json) }
        problems.size() == 2
    }

    @ToolingApiVersion(">=8.6")
    def "Problems expose details via Tooling API events"() {
        given:
        buildFile """
            import org.gradle.api.problems.Problems
            import org.gradle.api.problems.Severity
            import org.gradle.internal.deprecation.Documentation

            abstract class ProblemReportingTask extends DefaultTask {
                @Inject
                protected abstract Problems getProblems();

                @TaskAction
                void run() {
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
                }
            }

            tasks.register("reportProblem", ProblemReportingTask)
        """
        ProblemProgressListener listener = new ProblemProgressListener()

        when:
        withConnection { connection ->
            connection.newBuild().forTasks('reportProblem')
                .addProgressListener(listener)
                .run()
        }
        def problems = listener.problems.collect {  (ProblemDescriptor) it}

        then:
        problems.size() == 1
        problems[0].category.namespace == 'org.example.plugin'
        problems[0].category.category == 'main'
        problems[0].category.subCategories == ['sub', 'id']
        ((DefaultProblemsOperationDescriptor) problems[0]).additionalData.asMap == ['aKey': 'aValue']
        problems[0].label.label == 'shortProblemMessage'
        problems[0].details.details == expectedDetails
        problems[0].severity == Severity.WARNING
        problems[0].locations.size() == 2
        problems[0].locations[0] instanceof LineInFileLocation
        (problems[0].locations[0] as LineInFileLocation).path == '/tmp/foo'
        (problems[0].locations[0] as LineInFileLocation).line == 1
        (problems[0].locations[0] as LineInFileLocation).column == 2
        (problems[0].locations[0] as LineInFileLocation).length == 3
        problems[0].locations[1] instanceof TaskPathLocation
        (problems[0].locations[1] as TaskPathLocation).buildTreePath == ':reportProblem'
        problems[0].documentationLink.url == expecteDocumentation
        problems[0].solutions.size() == 1
        problems[0].solutions[0].solution == 'try this instead'
        problems[0].exception.exception == null

        where:
        detailsConfig              | expectedDetails | documentationConfig                         | expecteDocumentation
        '.details("long message")' | "long message"  | '.documentedAt("https://docs.example.org")' | 'https://docs.example.org'
        ''                         | null            | ''                                          | null
    }

    @ToolingApiVersion(">=8.6")
    def "Problems expose file locations with file path only"() {
        given:
        buildFile """
            import org.gradle.api.problems.Problems
            import org.gradle.api.problems.Severity
            import org.gradle.internal.deprecation.Documentation

            abstract class ProblemReportingTask extends DefaultTask {
                @Inject
                protected abstract Problems getProblems();

                @TaskAction
                void run() {
                    getProblems().forNamespace("org.example.plugin").reporting {
                        it.label("shortProblemMessage")
                        .category("main", "sub", "id")
                        .fileLocation("/tmp/foo")
                    }
                }
            }

            tasks.register("reportProblem", ProblemReportingTask)
        """
        ProblemProgressListener listener = new ProblemProgressListener()

        when:
        withConnection { connection ->
            connection.newBuild().forTasks('reportProblem')
                .addProgressListener(listener)
                .run()
        }
        def problems = listener.problems.collect { (ProblemDescriptor) it }

        then:
        problems.size() == 1
        FileLocation location = (FileLocation) problems[0].locations.find { it instanceof FileLocation }
        location.path == '/tmp/foo'
    }

    @ToolingApiVersion(">=8.6")
    def "Problems expose file locations with path and line"() {
        given:
        buildFile """
            import org.gradle.api.problems.Problems
            import org.gradle.api.problems.Severity
            import org.gradle.internal.deprecation.Documentation

            abstract class ProblemReportingTask extends DefaultTask {
                @Inject
                protected abstract Problems getProblems();

                @TaskAction
                void run() {
                    getProblems().forNamespace("org.example.plugin").reporting {
                        it.label("shortProblemMessage")
                        .category("main", "sub", "id")
                        .lineInFileLocation("/tmp/foo", 1)
                    }
                }
            }

            tasks.register("reportProblem", ProblemReportingTask)
        """
        ProblemProgressListener listener = new ProblemProgressListener()

        when:
        withConnection { connection ->
            connection.newBuild().forTasks('reportProblem')
                .addProgressListener(listener)
                .run()
        }
        def problems = listener.problems.collect { (ProblemDescriptor) it }

        then:
        problems.size() == 1
        LineInFileLocation location = (LineInFileLocation) problems[0].locations.find { it instanceof LineInFileLocation }
        location.path == '/tmp/foo'
        location.line == 1
        location.column < 1
        location.length < 0
    }

    @ToolingApiVersion(">=8.6")
    def "Problems expose file locations with path, line and column"() {
        given:
        buildFile """
            import org.gradle.api.problems.Problems
            import org.gradle.api.problems.Severity
            import org.gradle.internal.deprecation.Documentation

            abstract class ProblemReportingTask extends DefaultTask {
                @Inject
                protected abstract Problems getProblems();

                @TaskAction
                void run() {
                    getProblems().forNamespace("org.example.plugin").reporting {
                        it.label("shortProblemMessage")
                        .category("main", "sub", "id")
                        .lineInFileLocation("/tmp/foo", 1, 2)
                    }
                }
            }

            tasks.register("reportProblem", ProblemReportingTask)
        """
        ProblemProgressListener listener = new ProblemProgressListener()

        when:
        withConnection { connection ->
            connection.newBuild().forTasks('reportProblem')
                .addProgressListener(listener)
                .run()
        }
        def problems = listener.problems.collect { (ProblemDescriptor) it }

        then:
        problems.size() == 1
        LineInFileLocation location = (LineInFileLocation) problems[0].locations.find { it instanceof LineInFileLocation }
        location.path == '/tmp/foo'
        location.line == 1
        location.column == 2
        location.length < 0
    }

    @ToolingApiVersion(">=8.6")
    def "Problems expose file locations with path, line, column and length"() {
        given:
        buildFile """
            import org.gradle.api.problems.Problems
            import org.gradle.api.problems.Severity
            import org.gradle.internal.deprecation.Documentation

            abstract class ProblemReportingTask extends DefaultTask {
                @Inject
                protected abstract Problems getProblems();

                @TaskAction
                void run() {
                    getProblems().forNamespace("org.example.plugin").reporting {
                        it.label("shortProblemMessage")
                        .category("main", "sub", "id")
                        .lineInFileLocation("/tmp/foo", 1, 2, 3)
                    }
                }
            }

            tasks.register("reportProblem", ProblemReportingTask)
        """
        ProblemProgressListener listener = new ProblemProgressListener()

        when:
        withConnection { connection ->
            connection.newBuild().forTasks('reportProblem')
                .addProgressListener(listener)
                .run()
        }
        def problems = listener.problems.collect { (ProblemDescriptor) it }

        then:
        problems.size() == 1
        LineInFileLocation location = (LineInFileLocation) problems[0].locations.find { it instanceof LineInFileLocation }
        location.path == '/tmp/foo'
        location.line == 1
        location.column == 2
        location.length == 3
    }

    @ToolingApiVersion(">=8.6")
    def "Problems expose file locations with offset and length"() {
        given:
        buildFile """
            import org.gradle.api.problems.Problems
            import org.gradle.api.problems.Severity
            import org.gradle.internal.deprecation.Documentation

            abstract class ProblemReportingTask extends DefaultTask {
                @Inject
                protected abstract Problems getProblems();

                @TaskAction
                void run() {
                    getProblems().forNamespace("org.example.plugin").reporting {
                        it.label("shortProblemMessage")
                        .category("main", "sub", "id")
                        .offsetInFileLocation("/tmp/foo", 20, 10)
                    }
                }
            }

            tasks.register("reportProblem", ProblemReportingTask)
        """
        ProblemProgressListener listener = new ProblemProgressListener()

        when:
        withConnection { connection ->
            connection.newBuild().forTasks('reportProblem')
                .addProgressListener(listener)
                .run()
        }
        def problems = listener.problems.collect { (ProblemDescriptor) it }

        then:
        problems.size() == 1
        OffsetInFileLocation location = (OffsetInFileLocation) problems[0].locations.find { it instanceof OffsetInFileLocation }
        location.path == '/tmp/foo'
        location.offset == 20
        location.length == 10
    }

    @ToolingApiVersion(">=8.6")
    def "Problems expose summary Tooling API events"() {
        given:
        buildFile """
            import org.gradle.api.problems.internal.Problem
            import org.gradle.api.problems.Severity
            import org.gradle.internal.deprecation.Documentation

            abstract class ProblemReportingTask extends DefaultTask {
                @Inject
                protected abstract Problems getProblems();

                @TaskAction
                void run() {
                    for(int i = 0; i < 10; i++) {
                        problems.forNamespace("org.example.plugin").reporting{
                            it.label("The 'standard-plugin' is deprecated")
                                .category("deprecation", "plugin")
                                .severity(Severity.WARNING)
                                .solution("Please use 'standard-plugin-2' instead of this plugin")
                            }
                    }
                 }
            }

            tasks.register("reportProblem", ProblemReportingTask)
        """
        ProblemProgressListener listener = new ProblemProgressListener()

        when:
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

    class ProblemProgressListener implements ProgressListener {

        List<BaseProblemDescriptor> problems = []

        @Override
        void statusChanged(ProgressEvent event) {
            if (event instanceof ProblemEvent) {
                this.problems.addAll(event.getDescriptor())
            }
        }
    }
}
