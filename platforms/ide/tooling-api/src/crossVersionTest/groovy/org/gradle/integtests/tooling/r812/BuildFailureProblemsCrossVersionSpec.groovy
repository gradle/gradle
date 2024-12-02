/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.integtests.tooling.r812

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.Failure
import org.gradle.tooling.GradleConnectionException

@ToolingApiVersion('>=8.12')
@TargetGradleVersion('>=8.12')
class BuildFailureProblemsCrossVersionSpec extends ToolingApiSpecification {

    def setup() {
        settingsFile << """
            rootProject.name = 'root'
        """
        buildFile << """
            plugins {
                id 'java-library'
            }
        """
    }

    @TargetGradleVersion('<8.12')
    def "clients won't receive problems associated to build failures if they are not subscribed to problems"() {
        when:
        withConnection { connection ->
            connection.newBuild()
                .forTasks('doesNotExist')
                .withDetailedFailure()
                .run()
        }

        then:
        GradleConnectionException exception = thrown(GradleConnectionException)
        exception.failures.isEmpty()
    }

    def "build failure contains single problem report"() {
        when:
        withConnection { connection ->
            connection.newBuild()
                .forTasks('doesNotExist')
                .withDetailedFailure()
                .run()
        }

        then:
        GradleConnectionException e = thrown(GradleConnectionException)
        e.failures.size() == 1
        e.failures[0] instanceof Failure
        (e.failures[0].causes[0]).problems[0].contextualLabel.contextualLabel == "Task 'doesNotExist' not found in root project 'root'."
        (e.failures[0].causes[0]).problems[0].definition.id.displayName == 'Selection failed'
    }

    def "failure does not contains report from the previous build"() {
        when:
        Exception firstBuildFailure = null
        withConnection { connection ->
            try {
                connection.newBuild()
                    .forTasks('doesNotExist1')
                    .withDetailedFailure()
                    .run()

            } catch (GradleConnectionException e) {
                firstBuildFailure = e
            }
            assert firstBuildFailure != null
            connection.newBuild()
                .forTasks('doesNotExist2')
                .withDetailedFailure()
                .run()
        }

        then:
        GradleConnectionException e = thrown(GradleConnectionException)
        e.failures.size() == 1
        e.failures[0] instanceof Failure
        e.failures[0].causes[0].problems[0].contextualLabel.contextualLabel == "Task 'doesNotExist2' not found in root project 'root'."
        e.failures[0].causes[0].problems[0].definition.id.displayName == 'Selection failed'
    }

    def "failure from worker using process isolation"() {
        setup:
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
                    Exception wrappedException = new Exception("Wrapped cause");
                     getProblems().getReporter().throwing(problem -> problem
                            .id("type", "label")
                            .stackLocation()
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

        when:

        withConnection { connection ->
            connection.newBuild()
                .forTasks('reportProblem')
                .withDetailedFailure()
                .run()
        }

        then:
        GradleConnectionException e = thrown(GradleConnectionException)
        def problem = e.failures[0]?.causes[0]?.causes[0]?.problems[0]
        problem != null
        problem.definition.id.name == 'type'
        problem.definition.id.displayName == 'label'
        problem.failure.message == 'Exception message'
    }
}
