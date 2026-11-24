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

package org.gradle.integtests.tooling.r910

import org.gradle.integtests.tooling.fixture.ProblemsApiGroovyScriptUtils
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.tooling.events.ProgressEvent
import org.gradle.tooling.events.ProgressListener
import org.gradle.tooling.events.problems.SingleProblemEvent

/**
 * Builds that report a problem, shared by the specs for each client and Gradle version pairing,
 * so that all of them look at the same occurrence.
 */
abstract class AbstractDetectionTraceCrossVersionSpec extends ToolingApiSpecification {

    static final String PROBLEM_ID = "detection-trace-problem"

    def setup() {
        // The trace must cross a daemon connection, not stay in the test process
        toolingApi.requireDaemons()
    }

    String getReportingTaskClass() {
        """
            import org.gradle.api.problems.Problems
            import javax.inject.Inject

            abstract class ProblemReportingTask extends DefaultTask {
                @Inject
                abstract Problems getProblems()

                @TaskAction
                void run() {
                    getProblems().${ProblemsApiGroovyScriptUtils.report(targetVersion, PROBLEM_ID, PROBLEM_ID)} {}
                }
            }
        """
    }

    void taskReportsProblem() {
        buildFile """
            $reportingTaskClass

            tasks.register("reportProblem", ProblemReportingTask)
        """
    }

    void taskOfIncludedBuildReportsProblem() {
        settingsFile << """
            includeBuild "lib"
        """
        buildFile << """
            tasks.register("reportProblem") {
                dependsOn gradle.includedBuild("lib").task(":core:reportProblem")
            }
        """
        file("lib/settings.gradle") << """
            rootProject.name = "lib"
            include "core"
        """
        file("lib/core/build.gradle") << """
            $reportingTaskClass

            tasks.register("reportProblem", ProblemReportingTask)
        """
    }

    void pluginReportsProblemWhenApplied() {
        buildFile """
            import org.gradle.api.problems.Problems
            import javax.inject.Inject

            abstract class ProblemReportingPlugin implements Plugin<Project> {
                @Inject
                abstract Problems getProblems()

                void apply(Project project) {
                    getProblems().${ProblemsApiGroovyScriptUtils.report(targetVersion, PROBLEM_ID, PROBLEM_ID)} {}
                }
            }

            apply plugin: ProblemReportingPlugin

            tasks.register("reportProblem")
        """
    }

    /**
     * A plugin with an id, built in buildSrc, reports the problem as it is applied. Uses the Problems API of Gradle 9.10.
     */
    void pluginWithIdReportsProblemWhenApplied() {
        file("buildSrc/build.gradle") << """
            plugins {
                id "java-gradle-plugin"
            }
            gradlePlugin {
                plugins {
                    foo {
                        id = "com.acme.foo"
                        implementationClass = "com.acme.FooPlugin"
                    }
                }
            }
        """
        file("buildSrc/src/main/java/com/acme/FooPlugin.java") << """
            package com.acme;

            import org.gradle.api.Plugin;
            import org.gradle.api.Project;
            import org.gradle.api.problems.Problems;
            import javax.inject.Inject;

            public abstract class FooPlugin implements Plugin<Project> {
                @Inject
                protected abstract Problems getProblems();

                @Override
                public void apply(Project project) {
                    getProblems().getReporter().report(getProblems().getGroups().getOthers().group("Generic").problemId("$PROBLEM_ID"), spec -> {});
                }
            }
        """
        buildFile """
            plugins {
                id "com.acme.foo"
            }

            tasks.register("reportProblem")
        """
    }

    List<?> runReportProblem() {
        def listener = new ProblemListener()
        withConnection { connection ->
            connection.newBuild()
                .forTasks("reportProblem")
                .addProgressListener(listener)
                .run()
        }
        listener.problems
    }

    static class ProblemListener implements ProgressListener {

        List<?> problems = []

        @Override
        void statusChanged(ProgressEvent event) {
            if (event instanceof SingleProblemEvent && event.problem.definition.id.name == PROBLEM_ID) {
                problems.add(event.problem)
            }
        }
    }
}
