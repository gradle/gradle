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
import org.gradle.tooling.events.problems.FileLocation
import org.gradle.tooling.events.problems.ProblemDescriptor
import org.gradle.tooling.events.problems.ProblemEvent
import org.gradle.tooling.events.problems.Severity
import org.gradle.tooling.events.problems.TaskPathLocation

@ToolingApiVersion(">=8.5")
@TargetGradleVersion(">=8.6")
class ProblemProgressEventCrossVersionTest extends ToolingApiSpecification {

    @TargetGradleVersion("=8.3")
    def "Older Gradle versions do not report problems"() {
        setup:
        buildFile << """
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
        listener.problems.size() == 0
    }

    @ToolingApiVersion("=8.5")
    def "Gradle 8.5 exposes problem events via JSON strings"() {
        setup:
        buildFile << """
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
        def problems = listener.problems.collect {new JsonSlurper().parseText(it.json) }
        problems.size() == 2
    }

    @ToolingApiVersion(">=8.6")
    def "Problems expose details via Tooling API events"() {
        given:
        buildFile << """
            import org.gradle.api.problems.Problem
            import org.gradle.api.problems.Severity
            import org.gradle.internal.deprecation.Documentation


            class TestDocLink implements DocLink {

                private final String url;

                public TestDocLink(String url) {
                    this.url = url
                }

                @Override
                String getUrl() {
                    return url;
                }

                @Override
                String getConsultDocumentationMessage() {
                    return "consult " + url;
                }
            }

            abstract class ProblemReportingTask extends DefaultTask {
                @Inject
                protected abstract Problems getProblems();

                @TaskAction
                void run() {
                    problems.withPluginNamespace("org.example.plugin").create {
                        it.label("shortProblemMessage")
                        $documentationConfig
                        .fileLocation("/tmp/foo", 1, 2, 3)
                        .category("main", "sub", "id")
                        $detailsConfig
                        .additionalData("aKey", "aValue")
                        .severity(Severity.WARNING)
                        .solution("try this instead")
                    }.report()
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
        def problems = listener.problems

        then:
        problems.size() == 1
        problems[0].category.namespace == 'gradle-plugin:org.example.plugin'
        problems[0].category.category == 'main'
        problems[0].category.subCategories == ['sub','id']
        problems[0].additionalData.asMap == ['aKey' : 'aValue']
        problems[0].label.label == 'shortProblemMessage'
        problems[0].details.details == expectedDetails
        problems[0].severity == Severity.WARNING
        problems[0].locations.size() == 2
        problems[0].locations[0] instanceof FileLocation
        (problems[0].locations[0] as FileLocation).path == '/tmp/foo'
        (problems[0].locations[0] as FileLocation).line == 1
        (problems[0].locations[0] as FileLocation).column == 2
        (problems[0].locations[0] as FileLocation).length == 3
        problems[0].locations[1] instanceof TaskPathLocation
        (problems[0].locations[1] as TaskPathLocation).identityPath == ':reportProblem'
        problems[0].documentationLink.url == expecteDocumentation // TODO https://github.com/gradle/gradle/issues/27124
        problems[0].solutions.size() == 1
        problems[0].solutions[0].solution == 'try this instead'
        problems[0].exception.exception == null

        where:
        detailsConfig              | expectedDetails | documentationConfig                                          | expecteDocumentation
        '.details("long message")' | "long message"  | '.documentedAt(new TestDocLink("https://docs.example.org"))' | 'https://docs.example.org'
        ''                         | null            | '.undocumented()'                                            | null
    }

    class ProblemProgressListener implements ProgressListener {

        List<ProblemDescriptor> problems = []

        @Override
        void statusChanged(ProgressEvent event) {
            if (event instanceof ProblemEvent) {
                this.problems.addAll(event.getDescriptor())
            }
        }
    }
}
