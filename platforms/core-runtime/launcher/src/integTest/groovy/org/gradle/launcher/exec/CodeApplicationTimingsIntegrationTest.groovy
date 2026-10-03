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
import org.gradle.configuration.ApplyScriptPluginBuildOperationType
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.internal.code.operations.CodeApplicationsProgressDetails
import org.gradle.internal.operations.trace.BuildOperationRecord
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.TestExecutionPreconditions

import static org.gradle.internal.time.SimulatedWork.simulateWork
import static org.gradle.internal.time.SimulatedWork.workNanos

/**
 * Tests the user code application timings reported by the
 * {@link CodeApplicationsProgressDetails} progress event.
 */
class CodeApplicationTimingsIntegrationTest extends AbstractIntegrationSpec {

    def operations = new BuildOperationsFixture(executer, temporaryFolder)

    def setup() {
        settingsFile("""
            rootProject.name = "root"
        """)
    }

    def "reports timings for each code application"() {
        given:
        settingsFile("""
            ${simulateWork()}
        """)

        file("script.gradle") << """
            ${simulateWork()}
        """

        buildFile("""
            apply from: "script.gradle"
            apply plugin: SomePlugin

            ${simulateWork()}

            class SomePlugin implements Plugin<Project> {
                void apply(Project p) {
                    ${simulateWork()}
                }
            }
        """)

        when:
        succeeds("help")

        then:
        // Every application of the build is reported, and nothing else.
        codeApplications().keySet() == appliedApplicationIds()

        and:
        // Application only run code of their own, and no listeners or callbacks are executed.
        timingsFor("Apply settings file 'settings.gradle' to settings '${testDirectory.name}'").keySet() == ["MAIN"] as Set
        timingsFor("Apply build file 'build.gradle' to root project 'root'").keySet() == ["MAIN"] as Set
        timingsFor("Apply script 'script.gradle' to root project 'root'").keySet() == ["MAIN"] as Set
        timingsFor("Apply plugin SomePlugin to root project 'root'").keySet() == ["MAIN"] as Set

        and:
        // All applications have some timings.
        codeApplications().every { id, application -> !(application["timings"] as Map).isEmpty() }
    }

    def "reports timings for applications that are not applied to a project"() {
        given:
        initScriptFile("""
            ${simulateWork()}
        """)

        when:
        succeeds("help", "-I", "init.gradle")

        then:
        def timings = timingsFor("Apply initialization script 'init.gradle' to build ':'")
        timings["MAIN"] >= workNanos()
    }

    def "attributes time spent applying a plugin to that plugin's application"() {
        given:
        buildFile("""
            apply plugin: SomePlugin

            class SomePlugin implements Plugin<Project> {
                void apply(Project p) {
                    ${simulateWork()}
                }
            }
        """)

        when:
        succeeds("help")

        then:
        def timings = timingsFor("Apply plugin SomePlugin to root project 'root'")
        timings["MAIN"] >= workNanos()
        timings.keySet() == ["MAIN"] as Set
    }

    def "attributes time spent in collection callbacks separately from other code"() {
        given:
        buildFile("""
            apply plugin: SomePlugin

            configurations {
                foo
            }

            class SomePlugin implements Plugin<Project> {
                void apply(Project p) {
                    ${simulateWork()}

                    p.configurations.all {
                        if (name == "foo") {
                            ${simulateWork()}
                        }
                    }
                }
            }
        """)

        when:
        succeeds("help")

        then:
        def timings = timingsFor("Apply plugin SomePlugin to root project 'root'")
        timings["COLLECTION_CALLBACK"] >= workNanos()
        timings.keySet() == ["MAIN", "COLLECTION_CALLBACK"] as Set
    }

    def "attributes time spent in listener callbacks separately from other code"() {
        given:
        buildFile("""
            apply plugin: SomePlugin

            class SomePlugin implements Plugin<Project> {
                void apply(Project p) {
                    ${simulateWork()}

                    p.afterEvaluate {
                        ${simulateWork()}
                    }
                }
            }
        """)

        when:
        succeeds("help")

        then:
        def timings = timingsFor("Apply plugin SomePlugin to root project 'root'")
        timings["LISTENER"] >= workNanos()
        timings.keySet() == ["MAIN", "LISTENER"] as Set
    }

    def "attributes time spent executing a task action to the application that registered the task"() {
        given:
        buildFile("""
            apply plugin: SomePlugin

            class SomePlugin implements Plugin<Project> {
                void apply(Project p) {
                    ${simulateWork()}

                    p.tasks.create("work") {
                        doLast {
                            ${simulateWork()}
                        }
                    }
                }
            }
        """)

        when:
        succeeds("work")

        then:
        def timings = timingsFor("Apply plugin SomePlugin to root project 'root'")
        timings["TASK_ACTION"] >= workNanos()
        timings.keySet() == ["MAIN", "TASK_ACTION"] as Set
    }

    @Requires(value = TestExecutionPreconditions.IsConfigCached, reason = "Asserts that the CC hit restores its applications from the entry the first build stored")
    def "reports timings for an application restored from the configuration cache"() {
        given:
        buildFile("""
            apply plugin: SomePlugin

            configurations {
                foo
            }

            class SomePlugin implements Plugin<Project> {
                void apply(Project p) {
                    ${simulateWork()}

                    p.configurations.all {
                        if (name == "foo") {
                            ${simulateWork()}
                        }
                    }

                    p.afterEvaluate {
                        ${simulateWork()}
                    }

                    p.tasks.register("work") {
                        doLast {
                            ${simulateWork()}
                        }
                    }
                }
            }
        """)

        when:
        succeeds("work")

        then:
        def applicationId = operations.only("Apply plugin SomePlugin to root project 'root'").details.applicationId
        def stored = timingsForId(applicationId)
        stored["MAIN"] >= workNanos()
        stored["COLLECTION_CALLBACK"] >= workNanos()
        stored["LISTENER"] >= workNanos()
        stored["TASK_ACTION"] >= workNanos()

        when:
        succeeds("work")

        then:
        // The plugin is never applied in the CC hit build
        operations.none("Apply plugin SomePlugin to root project 'root'")

        and:
        timingsForId(applicationId).keySet() == ["TASK_ACTION"] as Set
        timingsForId(applicationId)["TASK_ACTION"] >= workNanos()
    }

    @Requires(value = TestExecutionPreconditions.NotIsolatedProjects, reason = "Intentionally uses allprojects to execute code against another project from the root project")
    def "reports a single result for an application that runs code for several projects"() {
        given:
        createDirs("a", "b")
        settingsFile("""
            include("a")
            include("b")
        """)

        buildFile("""
            allprojects {
                ${simulateWork()}
            }
        """)

        when:
        succeeds("help")

        then:
        // The build script is a single application, even though it runs code for three projects.
        def buildScriptApplicationId = operations.only("Apply build file 'build.gradle' to root project 'root'").details.applicationId
        codeApplications().count { id, application -> id == buildScriptApplicationId.toString() } == 1
        timingsFor("Apply build file 'build.gradle' to root project 'root'")["MAIN"] >= 3 * workNanos()
    }

    private Map<String, Long> timingsFor(String applicationOperationName) {
        BuildOperationRecord application = operations.only(applicationOperationName)
        def applicationId = application.details.applicationId
        assert applicationId != null, "Operation '${application.displayName}' has no application ID"
        return timingsForId(applicationId)
    }

    private Map<String, Long> timingsForId(Object applicationId) {
        def application = codeApplications()[applicationId.toString()] as Map<String, Object>
        assert application != null, "No code application reported for application ID $applicationId"
        return application["timings"] as Map<String, Long>
    }

    /**
     * The IDs of every application made by the build.
     */
    private Set<String> appliedApplicationIds() {
        def applyOperations = operations.all(ApplyPluginBuildOperationType) + operations.all(ApplyScriptPluginBuildOperationType)
        return applyOperations.collect { it.details.applicationId.toString() } as Set
    }

    /**
     * The code applications reported by the build, keyed by application ID.
     */
    private Map<String, Object> codeApplications() {
        def events = operations.progress(CodeApplicationsProgressDetails)
        assert events.size() == 1, "Expected one timings event, found ${events.size()}"
        return events.first().details["codeApplications"] as Map<String, Object>
    }

}
