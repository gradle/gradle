/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.workers.internal

import com.google.common.collect.Iterables
import org.gradle.api.problems.Severity
import org.gradle.api.problems.internal.TaskLocation
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.operations.problems.ProblemUsageProgressDetails
import org.gradle.workers.fixtures.WorkerExecutorFixture

class WorkerExecutorProblemsApiIntegrationTest extends AbstractIntegrationSpec {

    // Worker-written file containing the build operation id
    // We will use this to verify if the problem was reported in the correct build operation
    def buildOperationIdFile = file('build-operation-id.txt')

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

        file('buildSrc/src/main/java/org/gradle/test/SomeOtherData.java') << """
            package org.gradle.test;

            public interface SomeOtherData {
                String getOtherName();
                void setOtherName(String name);
            }
        """

        file('buildSrc/src/main/java/org/gradle/test/SomeData.java') << """
            package org.gradle.test;

            import org.gradle.api.problems.AdditionalData;
            import org.gradle.api.provider.Property;

            import java.util.List;

            public interface SomeData extends AdditionalData {
                  Property<String> getSome();
                  String getName();
                  void setName(String name);

                  List<String> getNames();
                  void setNames(List<String> names);

                  SomeOtherData getOtherData();
                  void setOtherData(SomeOtherData otherData);
            }
        """
        file('buildSrc/src/main/java/org/gradle/test/ProblemWorkerTask.java') << """
            package org.gradle.test;

            import java.io.File;
            import java.io.FileWriter;
            import java.util.Collections;
            import org.gradle.api.problems.Problems;
            import org.gradle.api.problems.ProblemId;
            import org.gradle.api.problems.ProblemGroup;
            import org.gradle.api.model.ObjectFactory;
            import org.gradle.internal.operations.CurrentBuildOperationRef;

            import org.gradle.workers.WorkAction;

            import javax.inject.Inject;

            public abstract class ProblemWorkerTask implements WorkAction<ProblemsWorkerTaskParameter> {

                @Inject
                public abstract Problems getProblems();

                @Inject
                public abstract ObjectFactory getObjectFactory();

                @Override
                public void execute() {
                    Exception wrappedException = new Exception("Wrapped cause");
                    ProblemId problemId = ProblemId.create("type", "label", ProblemGroup.create("generic", "Generic"));
                    getProblems().getReporter().report(problemId, problem -> problem
                            .stackLocation()
                            .additionalData(SomeData.class, d -> {
                                    d.getSome().set("some");
                                    d.setName("someData");
                                    d.setNames(Collections.singletonList("someMoreData"));
                                    SomeOtherData sod = getObjectFactory().newInstance(SomeOtherData.class);
                                    sod.setOtherName("otherName");
                                    d.setOtherData(sod);
                                })
                            .withException(new RuntimeException("Exception message", wrappedException))
                    );

                    // Write the current build operation id to a file
                    // This needs to be Java 6 compatible, as we are in a worker
                    // Backslashes need to be escaped, so test works on Windows
                    File buildOperationIdFile = new File("${buildOperationIdFile.absolutePath.replace('\\', '\\\\')}");
                    try(FileWriter writer = new FileWriter(buildOperationIdFile)) {
                        writer.write(CurrentBuildOperationRef.instance().get().getId().toString());
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        """
    }

    def "problems are emitted correctly from a worker when using #isolationMode"() {
        setupBuild()
        enableProblemsApiCheck()

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
        run("reportProblem")

        then:
        verifyAll(receivedProblem) {
            operationId == Long.parseLong(buildOperationIdFile.text)
            exception.message == "Exception message"
            exception.stacktrace.contains("Caused by: java.lang.Exception: Wrapped cause")
            contextualLocations.size() == 1
            (contextualLocations[0] as TaskLocation).buildTreePath == ":reportProblem"
        }

        def problem = Iterables.getOnlyElement(filteredProblemDetails(buildOperationsFixture))
        with(problem) {
            with(definition) {
                name == 'type'
                displayName == 'label'
                with(group) {
                    displayName == 'Generic'
                    name == 'generic'
                    parent == null
                }
                documentationLink == null
            }
            severity == Severity.WARNING.name()
            contextualLabel == null
            solutions == []
            details == null
            contextualLocations.empty
            if (isolationMode == "'${WorkerExecutorFixture.IsolationMode.PROCESS_ISOLATION.method}'") {
                // TODO: Should have the stack location for all isolation modes
                assert originLocations.empty
            } else {
                assert originLocations.size() == 1
                with(originLocations[0]) {
                    // TODO: Should have the file location for all isolation modes
                    fileLocation == null
                    stackTrace.find { it.className == 'org.gradle.test.ProblemWorkerTask' && it.methodName == 'execute' && it.fileName == 'ProblemWorkerTask.java' }
                }
            }
            failure != null
        }

        where:
        isolationMode << WorkerExecutorFixture.ISOLATION_MODES
    }

    static Collection<Map<String, ?>> filteredProblemDetails(BuildOperationsFixture buildOperations) {
        List<Map<String, ?>> details = buildOperations.progress(ProblemUsageProgressDetails).details
        details
            .findAll { it.definition.name != 'executing-gradle-on-jvm-versions-and-lower'}
    }

}
