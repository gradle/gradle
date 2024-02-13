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

package org.gradle.integtests.tooling.r88

import org.gradle.integtests.fixtures.GroovyBuildScriptLanguage
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.integtests.tooling.r85.CustomModel
import org.gradle.tooling.BuildException
import org.gradle.tooling.events.ProgressEvent
import org.gradle.tooling.events.ProgressListener
import org.gradle.tooling.events.problems.ProblemEvent

import static org.gradle.integtests.fixtures.AvailableJavaHomes.getJdk17
import static org.gradle.integtests.tooling.r86.ProblemProgressEventCrossVersionTest.assertProblemDetailsForTAPIProblemEvent
import static org.gradle.integtests.tooling.r86.ProblemProgressEventCrossVersionTest.getProblemReportTaskString

@ToolingApiVersion(">=8.8")
class ProblemProgressEventCrossVersionTest extends ToolingApiSpecification {

    def withReportProblemTask(@GroovyBuildScriptLanguage String taskActionMethodBody) {
        buildFile getProblemReportTaskString(taskActionMethodBody)
        // TODO using the following code breaks the test, but it should be possible to use it
        //  buildFile getProblemReportingScript(taskActionMethodBody)
        //  issue https://github.com/gradle/gradle/issues/27484
    }

    def runTask() {
        def listener = new ProblemProgressListener()
        withConnection { connection ->
            connection.newBuild().forTasks('reportProblem')
                .addProgressListener(listener)
                .run()
        }
        return listener.problems
    }

    @TargetGradleVersion(">=6.9.4 <=8.5")
    def "Problems not exposed in target version 8.5 and lower"() {
        given:

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
            connection.newBuild().forTasks('ba')
                .addProgressListener(listener)
                .run()
        }

        then:
        thrown(BuildException)
        listener.problems.size() == 0
    }

    @TargetGradleVersion(">=8.5")
    def "Problems expose details via Tooling API events with failure"() {
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
        def location = problems[0].locations[1]
        problems[0].failure == null

        where:
        detailsConfig              | expectedDetails | documentationConfig                         | expecteDocumentation
        '.details("long message")' | "long message"  | '.documentedAt("https://docs.example.org")' | 'https://docs.example.org'
        ''                         | null            | ''                                          | null
    }

    @TargetGradleVersion(">=8.8")
    def "Can serialize groovy compilation error"() {
        buildFile """
            tasks.register("foo) {
        """

        given:
        def listener = new ProblemProgressListener()

        when:
        withConnection {
            it.model(CustomModel)
                .setJavaHome(jdk17.javaHome)
                .addProgressListener(listener)
                .get()
        }

        then:
        thrown(BuildException)
        def problems = listener.problems
        org.gradle.integtests.tooling.r87.ProblemProgressEventCrossVersionTest.validateCompilationProblem(problems, buildFile)
        problems[0].failure.failure.message == "Could not compile build file '$buildFile.absolutePath'."
    }

    @TargetGradleVersion(">=8.6")
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

        def firstProblem = problems[0]
        firstProblem.label.label == "The 'standard-plugin' is deprecated"
        firstProblem.details.details == null

        def aggregatedProblems = problems[1]

        def aggregations =  aggregatedProblems.aggregations
        aggregations.size() == 1
        aggregations[0].label.label == "The 'standard-plugin' is deprecated"
        aggregations[0].problemDescriptors.size() == 10
    }

//    @TargetGradleVersion(">=8.8")
//    def "Can serialize groovy compilation error"() {
//        buildFile """
//            tasks.register("foo) {
//        """
//
//        given:
//        def listener = new ProblemProgressListener()
//
//        when:
//        withConnection {
//            it.model(CustomModel)
//                .setJavaHome(jdk17.javaHome)
//                .addProgressListener(listener)
//                .get()
//        }
//
//        then:
//        thrown(BuildException)
//        def problems = listener.problems
//        validateCompilationProblem(problems)
//        problems[0].failure.failure.message == "Could not compile build file '$buildFile.absolutePath'."
//    }

//    void validateCompilationProblem(List<ProblemEvent> problems) {
//        problems.size() == 1
//        problems[0].label.label == "Could not compile build file '$buildFile.absolutePath'."
//        problems[0].category.category == 'compilation'
//    }
//
//    @TargetGradleVersion("=8.6")
//    def "8.6 version doesn't send failure"() {
//        buildFile """
//            tasks.register("foo) {
//        """
//
//        given:
//        def listener = new ProblemProgressListener()
//
//        when:
//        withConnection {
//            it.model(CustomModel)
//                .setJavaHome(jdk17.javaHome)
//                .addProgressListener(listener)
//                .get()
//        }
//
//        then:
//        thrown(BuildException)
//        def problems = listener.problems
//        validateCompilationProblem(problems)
//        problems[0].failure == null
//    }

    class ProblemProgressListener implements ProgressListener {

        List<?> problems = []

        @Override
        void statusChanged(ProgressEvent event) {
            if (event instanceof ProblemEvent) {
                this.problems.add(event)
            }
        }
    }

}
