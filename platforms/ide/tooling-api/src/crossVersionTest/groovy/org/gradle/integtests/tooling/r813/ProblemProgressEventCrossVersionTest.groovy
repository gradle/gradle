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
import org.gradle.test.fixtures.file.TestFile
import org.gradle.tooling.BuildException
import org.gradle.tooling.Failure
import org.gradle.tooling.events.ProgressEvent
import org.gradle.tooling.events.ProgressListener
import org.gradle.tooling.events.problems.Problem
import org.gradle.tooling.events.problems.SingleProblemEvent
import org.gradle.tooling.events.problems.internal.DefaultAdditionalData

import static org.gradle.integtests.tooling.r86.ProblemProgressEventCrossVersionTest.getProblemReportTaskString

@ToolingApiVersion(">=8.13")
@TargetGradleVersion(">=8.13")
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

    def "Problems expose details via Tooling API events with problem definition"() {
        given:
        buildFile """
            import org.gradle.api.problems.Severity
            import org.gradle.api.problems.AdditionalData

            public interface SomeData extends AdditionalData {
                String getName();
                void setName(String name);
            }

            abstract class ProblemReportingTask extends DefaultTask {
                @Inject
                protected abstract Problems getProblems();

                @TaskAction
                void run() {
                    getProblems().${ProblemsApiGroovyScriptUtils.report(targetVersion)} {
                        it.${ProblemsApiGroovyScriptUtils.id(targetVersion, 'id', 'shortProblemMessage')}
                        $documentationConfig
                        .lineInFileLocation("/tmp/foo", 1, 2, 3)
                        $detailsConfig
                        .additionalData(SomeData, data -> data.setName("someData"))
                        .severity(Severity.WARNING)
                        .solution("try this instead")
                    }
                }
            }

            tasks.register("reportProblem", ProblemReportingTask)
        """

        when:
        def problems = runTask()

        then:
        problems.size() == 1
        (problems.get(0).getAdditionalData() as DefaultAdditionalData).get(MyType).getName() == "someData"

        where:
        detailsConfig              | expectedDetails | documentationConfig                         | expecteDocumentation
        '.details("long message")' | "long message"  | '.documentedAt("https://docs.example.org")' | 'https://docs.example.org'
        ''                         | null            | ''                                          | null
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
        (listener.problems[0].additionalData as DefaultAdditionalData).asMap['typeName'] == 'MyTask'
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

    interface MyType {
        String getName()
    }
}
