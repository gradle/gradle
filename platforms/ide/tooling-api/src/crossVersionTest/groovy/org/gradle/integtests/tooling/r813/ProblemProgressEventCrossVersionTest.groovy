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
import org.gradle.workers.fixtures.WorkerExecutorFixture
import spock.lang.IgnoreRest

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

    def setupBuild() {
        file('buildSrc/build.gradle') << """
            plugins {
                id 'java'
            }

            dependencies {
                implementation(gradleApi())
            }
        """
        file('buildSrc/src/main/java/org/gradle/test/ProblemsWorkerTaskParameter.java') << """
            package org.gradle.test;

            import org.gradle.workers.WorkParameters;

            public interface ProblemsWorkerTaskParameter extends WorkParameters { }
        """
        file('buildSrc/src/main/java/org/gradle/test/SomeData.java') << """
            package org.gradle.test;

            import org.gradle.api.problems.AdditionalData;
            import org.gradle.api.provider.Property;

            public interface SomeData extends AdditionalData {
//                String getName();
//                void setName(String name);
                Property<String> getName();
//                void setValues(List<String> values);
//                List<String> getValues();
            }

        """
        file('buildSrc/src/main/java/org/gradle/test/ProblemWorkerTask.java') << """
            package org.gradle.test;

            import java.io.File;
            import java.io.FileWriter;
            import org.gradle.api.problems.Problems;
            import org.gradle.api.problems.ProblemId;
            import org.gradle.api.problems.ProblemGroup;
            import org.gradle.internal.operations.CurrentBuildOperationRef;

            import org.gradle.workers.WorkAction;

            import javax.inject.Inject;

            public abstract class ProblemWorkerTask implements WorkAction<ProblemsWorkerTaskParameter> {

                @Inject
                public abstract Problems getProblems();

                @Override
                public void execute() {
                    Exception wrappedException = new Exception("Wrapped cause");
                    // Create and report a problem
                    // This needs to be Java 6 compatible, as we are in a worker
                    ProblemId problemId = ProblemId.create("type", "label", ProblemGroup.create("generic", "Generic"));
                    getProblems().getReporter().report(problemId, problem -> problem
                            .stackLocation()
                            .additionalData(SomeData.class, d -> d.getName().set("someData"))
                            .withException(new RuntimeException("Exception message", wrappedException))
                    );
                }
            }
        """
    }

    interface SomeDataView {
        void setName(String name);

        String getName();
    }

    @IgnoreRest
    @ToolingApiVersion(">=8.14")
    @TargetGradleVersion(">=8.14")
    def "problems are emitted correctly from a worker when using #isolationMode"() {
        setupBuild()

        given:
        buildFile << """
            import javax.inject.Inject
            import org.gradle.test.ProblemWorkerTask


            abstract class ProblemTask extends DefaultTask {
                @Inject
                abstract WorkerExecutor getWorkerExecutor();

                @TaskAction
                void executeTask() {
                    getWorkerExecutor().${isolationMode}().submit(ProblemWorkerTask.class) {}
                }
            }

            tasks.register("reportProblem", ProblemTask)
        """

        when:
        def listener = new ProblemProgressListener()
        withConnection { connection ->
            connection.newBuild()
                .forTasks("reportProblem")
                .addProgressListener(listener)
                .setStandardError(System.err)
                .setStandardOutput(System.out)
                .addArguments("--info")
                .run()
        }

        then:
        listener.problems.size() == 1
        listener.problems[0].additionalData.get(SomeDataView).name == "someData"

        where:
//        isolationMode << WorkerExecutorFixture.ISOLATION_MODES
        isolationMode << [WorkerExecutorFixture.IsolationMode.PROCESS_ISOLATION.method]
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
