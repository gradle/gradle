/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.problems

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.internal.code.Locality

/**
 * A problem reported from a work action. The action's class comes from the worker's class loader, or runs in another process,
 * so what is known about it is its name on the stack.
 */
class OriginTraceWorkerIntegrationTest extends AbstractIntegrationSpec {

    private static final String PROBLEM_ID = "problem from spawned thread"

    def setup() {
        enableProblemsApiCheck()
    }

    def "a line in a work action is the origin leaf and takes the locality of the plugin that registered the task, with #isolation"() {
        given:
        workPluginThatReportsFrom(isolation)

        when:
        run("work")

        then:
        def problem = findReceivedProblem { it.fqid == "Others:Generic:$PROBLEM_ID" }
        problem.detectionTrace.frames*.displayName == [
            "task :work",
            "project :",
            "build :"
        ]

        and: "the leaf is the line in the work action"
        problem.originTraces*.frames*.displayName == [["ReportingWork.java:" + reportingLine()]]

        and: "its class is found by name in the jar of the plugin that registered the task, so it is the build's own"
        problem.originTraces[0].frames[0].locality == Locality.LOCAL

        where:
        isolation << ["noIsolation", "classLoaderIsolation"]
    }

    def "a problem reported from a worker process has no origin yet"() {
        given:
        workPluginThatReportsFrom("processIsolation")

        when:
        run("work")

        then: "the daemon still knows which task the work belongs to"
        def problem = findReceivedProblem { it.fqid == "Others:Generic:$PROBLEM_ID" }
        problem.detectionTrace.frames*.displayName == [
            "task :work",
            "project :",
            "build :"
        ]

        and: "no stack location arrives from the worker process, so there is no leaf to pick"
        problem.originTraces.empty
    }

    private int reportingLine() {
        file("buildSrc/src/main/java/com/acme/ReportingWork.java").readLines().findIndexOf { it.contains("getReporter().report(") } + 1
    }

    private void workPluginThatReportsFrom(String isolation) {
        file("buildSrc/build.gradle") << """
            plugins {
                id "java-gradle-plugin"
            }
            gradlePlugin {
                plugins {
                    work {
                        id = "com.acme.work"
                        implementationClass = "com.acme.WorkPlugin"
                    }
                }
            }
        """
        file("buildSrc/src/main/java/com/acme/WorkPlugin.java") << """
            package com.acme;

            import org.gradle.api.Plugin;
            import org.gradle.api.Project;

            public class WorkPlugin implements Plugin<Project> {
                @Override
                public void apply(Project project) {
                    project.getTasks().register("work", WorkTask.class);
                }
            }
        """
        file("buildSrc/src/main/java/com/acme/WorkTask.java") << """
            package com.acme;

            import org.gradle.api.DefaultTask;
            import org.gradle.api.tasks.TaskAction;
            import org.gradle.workers.WorkerExecutor;
            import javax.inject.Inject;

            public abstract class WorkTask extends DefaultTask {
                @Inject
                protected abstract WorkerExecutor getWorkerExecutor();

                @TaskAction
                public void run() {
                    getWorkerExecutor().${isolation}().submit(ReportingWork.class, parameters -> {});
                }
            }
        """
        file("buildSrc/src/main/java/com/acme/ReportingWork.java") << """
            package com.acme;

            import org.gradle.api.problems.Problems;
            import org.gradle.workers.WorkAction;
            import org.gradle.workers.WorkParameters;
            import javax.inject.Inject;

            public abstract class ReportingWork implements WorkAction<WorkParameters.None> {
                @Inject
                public abstract Problems getProblems();

                @Override
                public void execute() {
                    getProblems().getReporter().report(
                        getProblems().getGroups().getOthers().group("Generic").problemId("$PROBLEM_ID"),
                        spec -> spec.callerLocation(Problems.class));
                }
            }
        """
        buildFile """
            plugins {
                id "com.acme.work"
            }
        """
    }
}
