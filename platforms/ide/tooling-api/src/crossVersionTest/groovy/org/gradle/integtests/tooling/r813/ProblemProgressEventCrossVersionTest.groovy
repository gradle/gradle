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

package org.gradle.integtests.tooling.r813

import org.gradle.integtests.fixtures.GroovyBuildScriptLanguage
import org.gradle.integtests.tooling.fixture.ProblemsApiGroovyScriptUtils
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.integtests.tooling.r85.CustomModel
import org.gradle.test.fixtures.file.TestFile
import org.gradle.tooling.BuildException
import org.gradle.tooling.Failure
import org.gradle.tooling.events.ProgressEvent
import org.gradle.tooling.events.ProgressListener
import org.gradle.tooling.events.problems.LineInFileLocation
import org.gradle.tooling.events.problems.Problem
import org.gradle.tooling.events.problems.Severity
import org.gradle.tooling.events.problems.SingleProblemEvent
import org.gradle.tooling.events.problems.TaskPathLocation
import org.gradle.tooling.events.problems.internal.GeneralData
import org.gradle.util.GradleVersion
import org.junit.Assume

import static org.gradle.integtests.fixtures.AvailableJavaHomes.getJdk17
import static org.gradle.integtests.fixtures.AvailableJavaHomes.getJdk21
import static org.gradle.integtests.fixtures.AvailableJavaHomes.getJdk8
import static org.gradle.integtests.tooling.r86.ProblemProgressEventCrossVersionTest.getProblemReportTaskString
import static org.gradle.integtests.tooling.r86.ProblemsServiceModelBuilderCrossVersionTest.getBuildScriptSampleContent

@ToolingApiVersion(">=8.13")
@TargetGradleVersion(">=8.9")
class ProblemProgressEventCrossVersionTest extends ToolingApiSpecification {

    def withReportProblemTask(@GroovyBuildScriptLanguage String taskActionMethodBody) {
        buildFile getProblemReportTaskString(taskActionMethodBody)
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

    def "Problems expose details via Tooling API events with failure"() {
        given:
        withReportProblemTask """
            getProblems().${ProblemsApiGroovyScriptUtils.report(targetVersion)} {
              it.${ProblemsApiGroovyScriptUtils.id(targetVersion, 'id', 'shortProblemMessage')}
                $documentationConfig
                .lineInFileLocation("/tmp/foo", 1, 2, 3)
                $detailsConfig
                .additionalData(org.gradle.api.problems.internal.GeneralDataSpec, data -> data.put("aKey", "aValue"))
                .severity(Severity.WARNING)
                .solution("try this instead")
            }
        """
        when:

        def problems = runTask()

        then:
        problems.size() == 1
        verifyAll(problems[0]) {
            details?.details == expectedDetails
            definition.documentationLink?.url == expectedDocumentation
            (originLocations[0] as LineInFileLocation).path == '/tmp/foo'
            (originLocations[1] as LineInFileLocation).path == "build file '$buildFile.path'"
            if (targetVersion >= GradleVersion.version("8.13")) {
                assert (contextualLocations[0] as TaskPathLocation).buildTreePath == ':reportProblem'
            }
            definition.severity == Severity.WARNING
            solutions.size() == 1
            solutions[0].solution == 'try this instead'
        }

        where:
        detailsConfig              | expectedDetails | documentationConfig                         | expectedDocumentation
        '.details("long message")' | "long message"  | '.documentedAt("https://docs.example.org")' | 'https://docs.example.org'
        ''                         | null            | ''                                          | null
    }

    def "Problems expose details via Tooling API events with problem definition"() {
        given:
        withReportProblemTask """
            getProblems().${ProblemsApiGroovyScriptUtils.report(targetVersion)} {
                it.${ProblemsApiGroovyScriptUtils.id(targetVersion, 'id', 'shortProblemMessage')}
                $documentationConfig
                .lineInFileLocation("/tmp/foo", 1, 2, 3)
                $detailsConfig
                .additionalData(org.gradle.api.problems.internal.GeneralDataSpec, data -> data.put("aKey", "aValue"))
                .severity(Severity.WARNING)
                .solution("try this instead")
            }
        """

        when:

        def problems = runTask()

        then:
        problems.size() == 1
        verifyAll(problems[0]) {
            definition.id.name == 'id'
            definition.id.displayName == 'shortProblemMessage'
            definition.id.group.name == 'generic'
            definition.id.group.displayName == 'Generic'
            definition.id.group.parent == null
            definition.severity == Severity.WARNING
            definition.documentationLink?.url == expecteDocumentation
            details?.details == expectedDetails
        }

        where:
        detailsConfig              | expectedDetails | documentationConfig                         | expecteDocumentation
        '.details("long message")' | "long message"  | '.documentedAt("https://docs.example.org")' | 'https://docs.example.org'
        ''                         | null            | ''                                          | null
    }

    def "Can serialize groovy compilation error"() {
        buildFile """
            tasks.register("foo) {
        """

        given:
        def listener = new ProblemProgressListener()

        when:
        withConnection {
            it.model(CustomModel)
                .addProgressListener(listener)
                .get()
        }

        then:
        thrown(BuildException)
        def problems = listener.problems
        validateCompilationProblem(problems, buildFile)
        failureMessage(problems[0].failure) == "Could not compile build file '$buildFile.absolutePath'."
    }

    def "Can use problems service in model builder and get failure objects"() {
        given:
        Assume.assumeTrue(javaHome != null)
        buildFile getBuildScriptSampleContent(false, false, targetVersion)
        org.gradle.integtests.tooling.r87.ProblemProgressEventCrossVersionTest.ProblemProgressListener listener
        listener = new org.gradle.integtests.tooling.r87.ProblemProgressEventCrossVersionTest.ProblemProgressListener()


        when:
        withConnection {
            it.model(CustomModel)
                .setJavaHome(javaHome.javaHome)
                .addProgressListener(listener)
                .get()
        }
        def problems = listener.problems.findAll { it instanceof SingleProblemEvent }

        then:
        problems.size() == 1
        problems[0].problem.definition.id.displayName == 'label'
        problems[0].problem.definition.id.group.displayName == 'Generic'
        failureMessage(problems[0].problem.failure) == 'test'

        where:
        javaHome << [
            jdk8,
            jdk17,
            jdk21
        ]
    }

    static void validateCompilationProblem(List<SingleProblemEvent> problems, TestFile buildFile) {
        problems.size() == 1
        problems[0].definition.id.displayName == "Could not compile build file '$buildFile.absolutePath'."
        problems[0].definition.id.group.name == 'compilation'
    }

    def "Property validation failure should produce problem report with domain-specific additional data"() {
        setup:
        file('buildSrc/src/main/java/MyTask.java') << '''
            import org.gradle.api.*;
            import org.gradle.api.tasks.*;
            import org.gradle.work.*;
            @DisableCachingByDefault(because = "test task")
            public class MyTask extends DefaultTask {
                @Optional @Input
                boolean getPrimitive() {
                    return true;
                }
                @TaskAction public void execute() {}
            }
        '''
        buildFile << '''
            tasks.register('myTask', MyTask)
        '''

        when:
        def listener = new ProblemProgressListener()
        withConnection { connection ->
            connection.newBuild()
                .forTasks("myTask")
                .addProgressListener(listener)
                .setStandardError(System.err)
                .setStandardOutput(System.out)
                .addArguments("--info")

                .run()
        }

        then:
        thrown(BuildException)
        listener.problems.size() == 1
        (listener.problems[0].additionalData as GeneralData).asMap['typeName']== 'MyTask'
    }

    @TargetGradleVersion("=8.6")
    def "8.6 version doesn't send failure"() {
        buildFile """
            tasks.register("foo) {
        """

        given:
        def listener = new ProblemProgressListener()

        when:
        withConnection {
            it.model(CustomModel)
                .addProgressListener(listener)
                .get()
        }

        then:
        thrown(BuildException)
        def problems = listener.problems
        validateCompilationProblem(problems, buildFile)
        problems[0].failure == null
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
            it.newBuild()
                .forTasks(":reportProblem")
                .setJavaHome(jdk17.javaHome)
                .addProgressListener(listener)
                .run()
        }

        then:
        thrown(BuildException)
        listener.problems.size() == 0
    }

    class ProblemProgressListener implements ProgressListener {

        List<Problem> problems = []

        @Override
        void statusChanged(ProgressEvent event) {
            if (event instanceof SingleProblemEvent) {
                def singleProblem = event as SingleProblemEvent

                // Ignore problems caused by the minimum JVM version deprecation.
                // These are emitted intermittently depending on the version of Java used to run the test.
                if (singleProblem.problem.definition.id.name == "executing-gradle-on-jvm-versions-and-lower") {
                    return
                }

                this.problems.add(event.problem)
            }
        }
    }

    def failureMessage(failure) {
        failure instanceof Failure ? failure.message : failure.failure.message
    }
}
