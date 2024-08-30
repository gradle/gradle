/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.integtests.tooling.r811

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.GradleConnectionException
import org.gradle.tooling.ResultHandler
import org.gradle.tooling.events.ProgressEvent
import org.gradle.tooling.events.ProgressListener
import org.gradle.tooling.events.problems.ProblemEvent
import org.gradle.tooling.internal.consumer.ClientProblemException
import spock.lang.IgnoreRest

@ToolingApiVersion('>=8.11')
@TargetGradleVersion('>=8.11')
class ProblemWithBuildFailureBuildLauncherCrossVersionSpec extends ToolingApiSpecification {

    @IgnoreRest
    def "bar"() {
        setup:
        file('buildSrc/src/main/java/org/gradle/test/ProblemsWorkerTaskParameter.java') << """
            package org.gradle.test;

            import org.gradle.workers.WorkParameters;

            public interface ProblemsWorkerTaskParameter extends WorkParameters { }
        """
        file('buildSrc/src/main/java/org/gradle/test/ProblemWorkerTask.java') << """
            package org.gradle.test;

            import java.io.File;
            import java.io.FileWriter;
            import org.gradle.api.problems.Problems;
            import org.gradle.internal.operations.CurrentBuildOperationRef;

            import org.gradle.workers.WorkAction;

            import javax.inject.Inject;

            public abstract class ProblemWorkerTask implements WorkAction<ProblemsWorkerTaskParameter> {

                @Inject
                public abstract Problems getProblems();

                @Override
                public void execute() {
                    Exception wrappedException = new Exception("Wrapped problemCauseException");
                     getProblems().forNamespace("org.example.plugin").throwing(problem -> problem
                            .id("type", "label")
                            .contextualLabel("There's a problem in the worker task")
                            .withException(new RuntimeException("Exception message", wrappedException))
                    );
                }
            }
        """
        buildFile << """
            import javax.inject.Inject
            import org.gradle.test.ProblemWorkerTask

            abstract class ProblemTask extends DefaultTask {
                @Inject
                abstract WorkerExecutor getWorkerExecutor();

                @TaskAction
                void executeTask() {
                    getWorkerExecutor().processIsolation().submit(ProblemWorkerTask.class) {}
                }
            }

            tasks.register("reportProblem", ProblemTask)
        """
        def resultHandler = new FailureCollectingResultHandler()

        when:
        withConnection { connection ->
            connection.newBuild()
                .addJvmArguments("-agentlib:jdwp=transport=dt_socket,server=n,address=*:5008,suspend=y")
                .addArguments("--stacktrace")
                .setStandardOutput(System.out)
                .setStandardError(System.err)
                .forTasks("reportProblem")
                .run(resultHandler)
        }

        then:
        problemCauseException(resultHandler.exception).problem.contextualLabel.contextualLabel == "There's a problem in the worker task"
    }

    def "baz"() {
        file('buildSrc/src/main/java/org/gradle/test/ProblemTask.java') << """
            package org.gradle.test;

            import java.io.File;
            import java.io.FileWriter;
            import org.gradle.api.problems.Problems;
            import org.gradle.internal.operations.CurrentBuildOperationRef;

            import org.gradle.workers.WorkAction;

            import javax.inject.Inject;

            public abstract class ProblemTask extends $DefaultTask.name {

                @Inject
                public abstract Problems getProblems();

                @$TaskAction.name
                public void execute() {
                    Exception wrappedException = new Exception("Wrapped problemCauseException");
                     getProblems().forNamespace("org.example.plugin").throwing(problem -> problem
                            .id("type", "label")
                            .stackLocation()
                            .withException(new RuntimeException("Exception message", wrappedException))
                    );
                }
            }
        """
        buildFile << """
            import javax.inject.Inject

            tasks.register("reportProblem", org.gradle.test.ProblemTask)
        """
        def resultHandler = new FailureCollectingResultHandler()

        when:
        withConnection { connection ->
            connection.newBuild()
                .addJvmArguments("-agentlib:jdwp=transport=dt_socket,server=n,address=172.20.10.9:5008,suspend=y")
                .addArguments("--stacktrace")
                .setStandardOutput(System.out)
                .setStandardError(System.err)
                .forTasks("reportProblem")
                .run(resultHandler)
        }

        then:
        resultHandler.failure != null
    }

    def "foo"() {
        given:
        buildFile << """
            plugins {
                id 'java-library'
            }

            tasks {
                compileJava {
                    options.compilerArgs += ["-Xlint:all"]
                }
            }
        """
        file('src/main/java/Foo.java').text = """
            public class Foo {
                public void foo() {
                    new Bar().bar();
                }
            }
        """
        def resultHandler = new FailureCollectingResultHandler()

        when:
        withConnection { connection ->
            connection.newBuild()
                .addJvmArguments("-agentlib:jdwp=transport=dt_socket,server=n,address=192.168.0.174:5005,suspend=y")
                .addArguments("--stacktrace")
                .setStandardOutput(System.out)
                .setStandardError(System.err)
                .forTasks("compileJava")
                .run(resultHandler)
        }

        then:
        problemCauseException(resultHandler.exception) != null
    }

    static ClientProblemException problemCauseException(Throwable t) {
        if (t == null) {
            return null
        } else if (t instanceof ClientProblemException) {
            return t
        } else {
            return problemCauseException(t.getCause())
        }
    }

    class FailureCollectingResultHandler implements ResultHandler<Void> {

        GradleConnectionException exception

        @Override
        void onComplete(Void result) {
        }

        @Override
        void onFailure(GradleConnectionException failure) {
            this.exception = failure
        }
    }

    class ProblemProgressListener implements ProgressListener {

        @Override
        void statusChanged(ProgressEvent event) {
            if (event instanceof ProblemEvent) {
            }
        }
    }
}
