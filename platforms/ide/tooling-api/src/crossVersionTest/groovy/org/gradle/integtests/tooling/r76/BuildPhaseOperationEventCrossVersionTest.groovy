/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.integtests.tooling.r76

import org.gradle.integtests.tooling.fixture.ProgressEvents
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.GradleConnectionException
import org.gradle.tooling.events.FailureResult
import org.gradle.tooling.events.OperationType
import org.gradle.tooling.events.ProgressEvent
import org.gradle.tooling.events.SuccessResult
import org.gradle.tooling.events.lifecycle.BuildPhaseFinishEvent
import org.gradle.tooling.events.lifecycle.BuildPhaseOperationDescriptor
import org.gradle.tooling.events.lifecycle.BuildPhaseStartEvent

@ToolingApiVersion('>=7.6')
@TargetGradleVersion('>=7.6')
class BuildPhaseOperationEventCrossVersionTest extends ToolingApiSpecification {

    def "generates build phase events for task #taskName and expects #expectedReportedTasksCount run tasks"() {
        setupProject()

        when:
        def events = ProgressEvents.create()
        withConnection {
            it.newBuild().forTasks(taskName)
                .addProgressListener(events, OperationType.BUILD_PHASE)
                .run()
        }

        then:
        def progressEvents = events.getAll()
        progressEvents.size() == 6

        // We have 4 projects (root, a, b, c)
        assertStartEventHas(progressEvents[0], "CONFIGURE_ROOT_BUILD", 4)
        assertSuccessfulFinishEventHas(progressEvents[1], "CONFIGURE_ROOT_BUILD")

        assertStartEventHas(progressEvents[2], "RUN_MAIN_TASKS", 0)
        assertSuccessfulFinishEventHas(progressEvents[5], "RUN_MAIN_TASKS")

        assertStartEventHas(progressEvents[3], "RUN_WORK", expectedReportedTasksCount)
        assertSuccessfulFinishEventHas(progressEvents[4], "RUN_WORK")

        where:
        taskName   | expectedReportedTasksCount
        ":a:taskA" | 1
        ":a:taskB" | 2
        ":a:taskC" | 3
        ":a:taskD" | 1
    }

    def "reports failure if build fails in configuration phase"() {
        setupProject()
        file("a/build.gradle") << """
            throw new RuntimeException("taskD failed")
        """

        when:
        def events = ProgressEvents.create()
        withConnection {
            it.newBuild().forTasks(":a:taskC")
                .addProgressListener(events, OperationType.BUILD_PHASE)
                .run()
        }

        then:
        thrown(GradleConnectionException.class)
        def progressEvents = events.getAll()
        progressEvents.size() == 2

        assertStartEventHas(progressEvents[0], "CONFIGURE_ROOT_BUILD", 4)
        assertFailedFinishEventHas(progressEvents[1], "CONFIGURE_ROOT_BUILD")
    }

    def "reports failure if build fails in build phase"() {
        setupProject()
        file("a/build.gradle") << """
            tasks.register("taskE") {
                doLast {
                    throw new RuntimeException("taskD failed")
                }
            }
        """

        when:
        def events = ProgressEvents.create()
        withConnection {
            it.newBuild().forTasks(":a:taskE")
                .addProgressListener(events, OperationType.BUILD_PHASE)
                .run()
        }

        then:
        thrown(GradleConnectionException.class)
        def progressEvents = events.getAll()
        progressEvents.size() == 6

        // We have 4 projects
        assertStartEventHas(progressEvents[0], "CONFIGURE_ROOT_BUILD", 4)
        assertSuccessfulFinishEventHas(progressEvents[1], "CONFIGURE_ROOT_BUILD")

        assertStartEventHas(progressEvents[2], "RUN_MAIN_TASKS", 0)
        assertSuccessfulFinishEventHas(progressEvents[5], "RUN_MAIN_TASKS")

        // We will run just 1 task
        assertStartEventHas(progressEvents[3], "RUN_WORK", 1)
        assertFailedFinishEventHas(progressEvents[4], "RUN_WORK")
    }

    def "generates build phase events for task composite build for task #taskName and expects #expectedReportedTasksCount run tasks"() {
        setupCompositeBuildProject()

        when:
        def events = ProgressEvents.create()
        withConnection {
            it.newBuild().forTasks(taskName)
                .addProgressListener(events, OperationType.BUILD_PHASE)
                .run()
        }

        then:
        def progressEvents = events.getAll()
        progressEvents.size() == 8

        // Root project configuration, we have 3 projects in root (root, a, b)
        assertStartEventHas(progressEvents[0], "CONFIGURE_ROOT_BUILD", 3)
        // We then configure included build c
        assertStartEventHas(progressEvents[1], "CONFIGURE_BUILD", 1)
        // End of included build
        assertSuccessfulFinishEventHas(progressEvents[2], "CONFIGURE_BUILD")
        // End of root build
        assertSuccessfulFinishEventHas(progressEvents[3], "CONFIGURE_ROOT_BUILD")

        assertStartEventHas(progressEvents[4], "RUN_MAIN_TASKS", 0)
        assertSuccessfulFinishEventHas(progressEvents[7], "RUN_MAIN_TASKS")

        assertStartEventHas(progressEvents[5], "RUN_WORK", expectedReportedTasksCount)
        assertSuccessfulFinishEventHas(progressEvents[6], "RUN_WORK")

        where:
        taskName   | expectedReportedTasksCount
        ":a:taskA" | 1
        ":a:taskB" | 2
        ":a:taskC" | 3
        ":a:taskD" | 1
    }

    def "generates build phase events for task composite build when task from included build is run"() {
        setupCompositeBuildProject()

        when:
        def events = ProgressEvents.create()
        withConnection {
            it.newBuild().forTasks(":c:taskA")
                .addProgressListener(events, OperationType.BUILD_PHASE)
                .run()
        }

        then:
        def progressEvents = events.getAll()
        progressEvents.size() == 10

        // NOTE This code is here to help debugging this flaky test -----------
        println "Progress events:"
        progressEvents.each { println it }
        // --------------------------------------------------------------------

        // Root project configuration, we have 3 projects in root (root, a, b)
        assertStartEventHas(progressEvents[0], "CONFIGURE_ROOT_BUILD", 3)
        // We then configure included build c
        assertStartEventHas(progressEvents[1], "CONFIGURE_BUILD", 1)
        // End of included build
        assertSuccessfulFinishEventHas(progressEvents[2], "CONFIGURE_BUILD")
        // End of root build
        assertSuccessfulFinishEventHas(progressEvents[3], "CONFIGURE_ROOT_BUILD")

        assertStartEventHas(progressEvents[4], "RUN_MAIN_TASKS", 0)
        // With included builds RUN_WORK event is not deterministic
        if (progressEvents[5].toString() == "Build phase: Run tasks started") {
            // Run root build tasks
            assertStartEventHas(progressEvents[5], "RUN_WORK", 0)
            // Run included build task
            assertStartEventHas(progressEvents[6], "RUN_WORK", 1)
        } else {
            // Run included build task
            assertStartEventHas(progressEvents[5], "RUN_WORK", 1)
            // Run root build tasks
            assertStartEventHas(progressEvents[6], "RUN_WORK", 0)
        }
        // End included build tasks
        assertSuccessfulFinishEventHas(progressEvents[7], "RUN_WORK")
        // End root build tasks
        assertSuccessfulFinishEventHas(progressEvents[8], "RUN_WORK")
        assertSuccessfulFinishEventHas(progressEvents[9], "RUN_MAIN_TASKS")
    }

    boolean assertStartEventHas(ProgressEvent event, String buildPhase, int buildItemsCount) {
        assert buildPhase && event instanceof BuildPhaseStartEvent
        assert buildPhase && (event.descriptor as BuildPhaseOperationDescriptor).buildPhase == buildPhase
        assert buildPhase && (event.descriptor as BuildPhaseOperationDescriptor).buildItemsCount == buildItemsCount
        return true
    }

    boolean assertSuccessfulFinishEventHas(ProgressEvent event, String buildPhase) {
        assert buildPhase && event instanceof BuildPhaseFinishEvent
        assert buildPhase && (event as BuildPhaseFinishEvent).result instanceof SuccessResult
        assert buildPhase && (event.descriptor as BuildPhaseOperationDescriptor).buildPhase == buildPhase
        return true
    }

    boolean assertFailedFinishEventHas(ProgressEvent event, String buildPhase) {
        assert buildPhase && event instanceof BuildPhaseFinishEvent
        assert buildPhase && (event as BuildPhaseFinishEvent).result instanceof FailureResult
        assert buildPhase && (event.descriptor as BuildPhaseOperationDescriptor).buildPhase == buildPhase
        return true
    }

    def setupProject() {
        settingsFile << """
            rootProject.name = 'root'
            include 'a', 'b', 'c'
        """
        file("a/build.gradle") << """
            tasks.register("taskA")
            tasks.register("taskB") {
                dependsOn 'taskA'
            }
            tasks.register("taskC") {
                dependsOn 'taskB'
            }
            tasks.register("taskD")
        """
        file("b/build.gradle") << """
            tasks.register("taskA")
        """
        file("c/build.gradle") << """
            tasks.register("taskA")
        """
    }

    def setupCompositeBuildProject() {
        settingsFile << """
            rootProject.name = 'root'
            include 'a', 'b'
            includeBuild 'c'
        """
        file("a/build.gradle") << """
            tasks.register("taskA")
            tasks.register("taskB") {
                dependsOn 'taskA'
            }
            tasks.register("taskC") {
                dependsOn 'taskB'
            }
            tasks.register("taskD")
        """
        file("b/build.gradle") << """
            tasks.register("taskA")
        """
        file("c/build.gradle") << """
            tasks.register("taskA")
        """
        file("c/settings.gradle") << """
            rootProject.name = 'c'
        """
    }
}
