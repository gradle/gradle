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

package org.gradle.launcher.exec

import org.gradle.api.internal.plugins.ApplyPluginBuildOperationType
import org.gradle.integtests.fixtures.AbstractContinuousIntegrationTest
import org.gradle.integtests.fixtures.BuildOperationTreeFixture
import org.gradle.integtests.fixtures.BuildOperationTreeQueries
import org.gradle.internal.operations.trace.BuildOperationRecord
import org.gradle.internal.code.operations.CodeApplicationsProgressDetails
import org.gradle.internal.operations.trace.BuildOperationTrace
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.TestExecutionPreconditions
import org.jspecify.annotations.Nullable

import static org.gradle.internal.time.SimulatedWork.simulateWork
import static org.gradle.internal.time.SimulatedWork.workNanos

/**
 * Tests the user code application timings reported by each build of a continuous invocation.
 */
class CodeApplicationTimingsContinuousIntegrationTest extends AbstractContinuousIntegrationTest {

    private static final String SOME_PLUGIN = "Apply plugin SomePlugin to root project 'root'"

    def setup() {
        // The trace is read directly rather than through BuildOperationsFixture, which only
        // refreshes the tree it serves when the executer runs a build.
        executer.beforeExecute {
            executer.withArgument("-D${BuildOperationTrace.SYSPROP}=${tracePath}")
            executer.withArgument("-D${BuildOperationTrace.TREE_SYSPROP}=false")
        }

        settingsFile("""
            rootProject.name = "root"
        """)
        file("input.txt").text = "one"
    }

    @Requires(value = TestExecutionPreconditions.NotConfigCached, reason = "CC skips repeated configuration, which this test verifies")
    def "reports timings for every build of the invocation"() {
        given:
        buildFile("""
            apply plugin: SomePlugin

            class SomePlugin implements Plugin<Project> {
                void apply(Project p) {
                    ${simulateWork()}

                    def inputFile = p.file("input.txt")
                    def outputFile = p.file("build/out.txt")
                    p.tasks.register("work") {
                        inputs.file(inputFile)
                        outputs.file(outputFile)
                        doLast {
                            outputFile.text = inputFile.text
                        }
                    }
                }
            }
        """)

        when:
        succeeds("work")

        then:
        builds().size() == 1
        builds()[0].timingsFor(SOME_PLUGIN)["MAIN"] >= workNanos()

        when:
        file("input.txt").text = "two"
        buildTriggeredAndSucceeded()

        then:
        builds()[0].timingsFor(SOME_PLUGIN)["MAIN"] >= workNanos()
        builds()[1].timingsFor(SOME_PLUGIN)["MAIN"] >= workNanos()
    }

    @Requires(value = TestExecutionPreconditions.NotConfigCached, reason = "CC skips repeated configuration, which this test verifies")
    def "assigns application ids per build rather than per invocation"() {
        given:
        buildFile("""
            apply plugin: SomePlugin

            class SomePlugin implements Plugin<Project> {
                void apply(Project p) {
                    def inputFile = p.file("input.txt")
                    def outputFile = p.file("build/out.txt")
                    p.tasks.register("work") {
                        inputs.file(inputFile)
                        outputs.file(outputFile)
                        doLast {
                            outputFile.text = inputFile.text
                        }
                    }
                }
            }
        """)

        when:
        succeeds("work")
        file("input.txt").text = "two"
        buildTriggeredAndSucceeded()

        then:
        // IDs are assigned from scratch by each build, so the same ID identifies a different
        // application in each build of the invocation and only means anything within its own build
        builds()[0].pluginApplicationId(SOME_PLUGIN) == builds()[1].pluginApplicationId(SOME_PLUGIN)
    }

    @Requires(value = TestExecutionPreconditions.IsConfigCached, reason = "Asserts that a triggered build restores its applications from the entry the first build stored")
    def "reports timings for applications restored from the configuration cache"() {
        given:
        buildFile("""
            apply plugin: SomePlugin

            class SomePlugin implements Plugin<Project> {
                void apply(Project p) {
                    p.tasks.register("work") {
                        def inputFile = p.file("input.txt")
                        def outputFile = p.file("build/out.txt")
                        inputs.file(inputFile)
                        outputs.file(outputFile)
                        doLast {
                            ${simulateWork()}
                            outputFile.text = inputFile.text
                        }
                    }
                }
            }
        """)

        when:
        succeeds("work")

        then:
        def firstBuild = builds()[0]
        def applicationId = firstBuild.pluginApplicationId(SOME_PLUGIN)
        firstBuild.timingsFor(SOME_PLUGIN)["TASK_ACTION"] >= workNanos()

        when:
        // Changing a task input does not invalidate the entry, so the triggered build restores the
        // applications rather than applying them
        file("input.txt").text = "two"
        buildTriggeredAndSucceeded()

        then:
        builds().size() == 2
        def secondBuild = builds()[1]
        secondBuild.pluginApplicationId(SOME_PLUGIN) == null

        and:
        // The task action still belongs to the plugin that registered it, so the time it spends is
        // reported against the application the first build made and this one restored
        secondBuild.timingsFor(applicationId).keySet() == ["TASK_ACTION"] as Set
        secondBuild.timingsFor(applicationId)["TASK_ACTION"] >= workNanos()
    }

    private List<SingleBuild> builds() {
        def operations = new BuildOperationTreeFixture(BuildOperationTrace.readTree(tracePath))
        return operations.typed(RunBuildBuildOperationType).collect { new SingleBuild(operations, it) }
    }

    private static class SingleBuild {

        private final BuildOperationTreeQueries operations
        private final BuildOperationRecord runBuild

        SingleBuild(BuildOperationTreeQueries operations, BuildOperationRecord runBuild) {
            this.operations = operations
            this.runBuild = runBuild
        }

        @Nullable Long pluginApplicationId(String pluginDisplayName) {
            def applications = operations.search(runBuild, ApplyPluginBuildOperationType) {
                it.displayName == pluginDisplayName
            }
            assert applications.size() <= 1
            return applications.empty ? null : applications.first().details.applicationId as Long
        }

        Map<String, Long> timingsFor(String pluginDisplayName) {
            def applicationId = pluginApplicationId(pluginDisplayName)
            assert applicationId != null
            return timingsFor(applicationId)
        }

        Map<String, Long> timingsFor(long applicationId) {
            def events = runBuild.progress(CodeApplicationsProgressDetails)
            assert events.size() == 1, "Expected one timings event per build, found ${events.size()}"
            def applicationsById = events.first().details["codeApplications"] as Map<String, Object>
            def application = applicationsById[applicationId.toString()] as Map<String, Object>
            assert application != null, "No code application reported for $applicationId, reported ${applicationsById.keySet()}"
            return application["timings"] as Map<String, Long>
        }

    }

    private String getTracePath() {
        return file("operations").absolutePath
    }

}
