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

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.Failure
import org.gradle.tooling.GradleConnectionException
import org.gradle.tooling.events.problems.BuildFrame
import org.gradle.tooling.events.problems.Locality
import org.gradle.tooling.events.problems.PluginApplicationFrame
import org.gradle.tooling.events.problems.ProjectFrame
import org.gradle.tooling.events.problems.TaskFrame
import org.gradle.tooling.events.problems.TaskPathLocation

@ToolingApiVersion(">=9.10.0")
@TargetGradleVersion(">=9.10.0")
class DetectionTraceCrossVersionSpec extends AbstractDetectionTraceCrossVersionSpec {

    def "problem reported by a task carries the task, its project and the build, leaf first"() {
        given:
        taskReportsProblem()

        when:
        def problems = runReportProblem()

        then:
        problems.size() == 1
        def frames = problems[0].detectionTrace.frames
        frames*.displayName == ["task :reportProblem", "project :", "build :"]
        frames[0] instanceof TaskFrame
        frames[0].buildTreePath == ":reportProblem"
        frames[1] instanceof ProjectFrame
        frames[1].buildTreePath == ":"
        frames[2] instanceof BuildFrame
        frames[2].buildPath == ":"
        and: "the task was registered by the build script; project and build are the user's"
        frames*.locality == [Locality.LOCAL, Locality.LOCAL, Locality.LOCAL]

        and: "the flat list is still filled"
        problems[0].contextualLocations.findAll { it instanceof TaskPathLocation }*.buildTreePath == [":reportProblem"]
    }

    def "problem reported by a task of an included build carries both builds"() {
        given:
        taskOfIncludedBuildReportsProblem()

        when:
        def problems = runReportProblem()

        then:
        problems.size() == 1
        def frames = problems[0].detectionTrace.frames
        frames*.displayName == ["task :lib:core:reportProblem", "project :lib:core", "build :lib", "build :"]
        frames[0] instanceof TaskFrame
        frames[0].buildTreePath == ":lib:core:reportProblem"
        frames[1] instanceof ProjectFrame
        frames[1].buildTreePath == ":lib:core"
        frames[2] instanceof BuildFrame
        frames[2].buildPath == ":lib"
        frames[3] instanceof BuildFrame
        frames[3].buildPath == ":"
    }

    def "problem reported by a plugin applied by its class carries the plugin application without an id"() {
        given:
        pluginReportsProblemWhenApplied()

        when:
        def problems = runReportProblem()

        then:
        problems.size() == 1
        def frames = problems[0].detectionTrace.frames
        frames*.displayName == ["plugin application class ProblemReportingPlugin", "project :", "build :"]
        frames[0] instanceof PluginApplicationFrame
        frames[0].pluginId == null
        frames[0].className == "ProblemReportingPlugin"
        frames[1] instanceof ProjectFrame
        frames[2] instanceof BuildFrame
    }

    def "problem reported by a plugin with an id carries the plugin application"() {
        given:
        pluginWithIdReportsProblemWhenApplied()

        when:
        def problems = runReportProblem()

        then:
        problems.size() == 1
        def frames = problems[0].detectionTrace.frames
        frames*.displayName == ["plugin application com.acme.foo", "project :", "build :"]
        frames[0] instanceof PluginApplicationFrame
        frames[0].pluginId == "com.acme.foo"
        frames[0].className == "com.acme.FooPlugin"
    }

    def "the problem a build failure carries has the same traces as the problem event"() {
        given:
        buildFile """
            import org.gradle.api.problems.Problems
            import javax.inject.Inject

            abstract class FailingTask extends DefaultTask {
                @Inject
                abstract Problems getProblems()

                @TaskAction
                void run() {
                    def id = getProblems().getGroups().getOthers().group("Generic").problemId("$PROBLEM_ID")
                    throw getProblems().getReporter().throwing(new RuntimeException("boom"), id) {
                        it.lineInFileLocation(project.file("src/Foo.java").absolutePath, 3)
                    }
                }
            }

            tasks.register("reportProblem", FailingTask)
        """
        def listener = new ProblemListener()

        when:
        withConnection { connection ->
            connection.newBuild()
                .forTasks("reportProblem")
                .addProgressListener(listener)
                .withDetailedFailure()
                .run()
        }

        then:
        GradleConnectionException e = thrown(GradleConnectionException)
        def onFailure = problemsOf(e.failures[0]).find { it.definition.id.name == PROBLEM_ID }
        onFailure != null
        onFailure.detectionTrace.frames*.displayName == ["task :reportProblem", "project :", "build :"]
        onFailure.originTraces*.frames*.displayName == [[file("src/Foo.java").absolutePath + ":3"]]
        onFailure.originTraces[0].frames[0].locality == Locality.LOCAL

        and: "as the event has"
        listener.problems.size() == 1
        listener.problems[0].detectionTrace.frames*.displayName == onFailure.detectionTrace.frames*.displayName
    }

    def "the problem a build failure carries has its traces when it was thrown in a worker #isolation"() {
        given:
        file("buildSrc/build.gradle") << """
            plugins {
                id "java"
            }
            dependencies {
                implementation(gradleApi())
            }
        """
        file("buildSrc/src/main/java/org/gradle/test/FailingWork.java") << """
            package org.gradle.test;

            import org.gradle.api.problems.Problems;
            import org.gradle.workers.WorkAction;
            import org.gradle.workers.WorkParameters;
            import javax.inject.Inject;

            public abstract class FailingWork implements WorkAction<WorkParameters.None> {
                @Inject
                public abstract Problems getProblems();

                @Override
                public void execute() {
                    throw getProblems().getReporter().throwing(new RuntimeException("boom"),
                        getProblems().getGroups().getOthers().group("Generic").problemId("$PROBLEM_ID"), spec -> {});
                }
            }
        """
        buildFile """
            import javax.inject.Inject

            abstract class FailingTask extends DefaultTask {
                @Inject
                abstract WorkerExecutor getWorkerExecutor()

                @TaskAction
                void run() {
                    getWorkerExecutor().${isolation}().submit(org.gradle.test.FailingWork) {}
                }
            }

            tasks.register("reportProblem", FailingTask)
        """

        when:
        withConnection { connection ->
            connection.newBuild()
                .forTasks("reportProblem")
                .withDetailedFailure()
                .run()
        }

        then:
        GradleConnectionException e = thrown(GradleConnectionException)
        def onFailure = problemsOf(e.failures[0]).find { it.definition.id.name == PROBLEM_ID }
        onFailure != null
        onFailure.detectionTrace?.frames*.displayName == ["task :reportProblem", "project :", "build :"]

        where:
        isolation << ["noIsolation", "classLoaderIsolation", "processIsolation"]
    }

    private static List<?> problemsOf(Failure failure) {
        failure.problems + failure.causes.collectMany { problemsOf(it) }
    }
}
