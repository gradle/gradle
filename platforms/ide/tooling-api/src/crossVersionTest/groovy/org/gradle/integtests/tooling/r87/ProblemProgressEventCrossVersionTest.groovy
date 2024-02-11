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

package org.gradle.integtests.tooling.r87


import org.gradle.integtests.fixtures.GroovyBuildScriptLanguage
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.integtests.tooling.r85.CustomModel
import org.gradle.integtests.tooling.r85.ProblemProgressEventCrossVersionTest.ProblemProgressListener
import org.gradle.tooling.BuildException
import org.gradle.tooling.events.problems.ProblemDescriptor

import static org.gradle.integtests.fixtures.AvailableJavaHomes.getJdk17
import static org.gradle.integtests.tooling.r86.ProblemProgressEventCrossVersionTest.assertProblemDetailsForTAPIProblemEvent
import static org.gradle.integtests.tooling.r86.ProblemProgressEventCrossVersionTest.getProblemReportTaskString

@ToolingApiVersion(">=8.7")
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
        return listener.problems.collect { (ProblemDescriptor) it }
    }

    @TargetGradleVersion(">=8.5")
    def "Failing executions produce problems"() {
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
        listener.problems.size() == 2
    }

    @TargetGradleVersion(">=8.6")
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
        problems[0].failure == null

        where:
        detailsConfig              | expectedDetails | documentationConfig                         | expecteDocumentation
        '.details("long message")' | "long message"  | '.documentedAt("https://docs.example.org")' | 'https://docs.example.org'
        ''                         | null            | ''                                          | null
    }

    @TargetGradleVersion("=8.5")
    def "No problem for exceptions in 8.5"() {
        // serialization of exceptions is not working in 8.5 (Gson().toJson() fails)
        withReportProblemTask """
            throw new RuntimeException("boom")
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
        listener.problems.size() == 0
    }

    @TargetGradleVersion(">=8.7")
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
        def problems = listener.problems.collect { it as ProblemDescriptor }
        problems.size() == 1
        problems[0].label.label == "Could not compile build file '$buildFile.absolutePath'."
        problems[0].category.category == 'compilation'
        problems[0].failure.failure.message == "Could not compile build file '$buildFile.absolutePath'."
    }
}
