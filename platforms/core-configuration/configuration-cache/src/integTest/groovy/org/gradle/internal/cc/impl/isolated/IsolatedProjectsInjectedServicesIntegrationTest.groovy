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

package org.gradle.internal.cc.impl.isolated

class IsolatedProjectsInjectedServicesIntegrationTest extends AbstractIsolatedProjectsIntegrationTest {

    def "reports a problem on access to mutable state of `#service` injected into a task"() {
        createDirs("a")
        settingsFile """
            include("a")
        """
        buildFile """
            import javax.inject.Inject
            import org.gradle.api.internal.GradleInternal
            import org.gradle.execution.taskgraph.TaskExecutionGraphInternal

            abstract class MyTask extends DefaultTask {
                @Inject
                abstract $service getService()

                @TaskAction
                void run() {}
            }

            tasks.register("myTask", MyTask) {
                it.service.$prohibitedInvocation
            }
        """

        when:
        isolatedProjectsFailsUsing mode, ":myTask"

        then:
        fixture.assertIsolatedProjectsProblems(mode) {
            projectsConfigured(":", ":a")
            problem("Build file 'build.gradle': line 15: $expectedProblem")
        }

        where:
        service                      | prohibitedInvocation                   | expectedProblem
        "Gradle"                     | "plugins"                              | "Project ':' cannot access Gradle.getPlugins"
        "GradleInternal"             | "plugins"                              | "Project ':' cannot access Gradle.getPlugins"
        "TaskExecutionGraph"         | "whenReady { g -> g.hasTask(':a:x') }" | "Project ':' cannot access the tasks in the task graph that were created by other projects"
        "TaskExecutionGraphInternal" | "whenReady { g -> g.hasTask(':a:x') }" | "Project ':' cannot access the tasks in the task graph that were created by other projects"

        combined:
        mode << ALL_MODES
    }

    def "reports a problem on access to mutable state of `#service` injected into a project plugin"() {
        createDirs("a")
        settingsFile """
            include("a")
        """
        buildFile """
            import javax.inject.Inject
            import org.gradle.api.internal.GradleInternal
            import org.gradle.execution.taskgraph.TaskExecutionGraphInternal

            class MyPlugin implements Plugin<Project> {
                private final $service service

                @Inject
                MyPlugin($service service) {
                    this.service = service
                }

                void apply(Project project) {
                    service.$prohibitedInvocation
                }
            }

            apply plugin: MyPlugin
        """

        when:
        isolatedProjectsFailsUsing mode, "help"

        then:
        fixture.assertIsolatedProjectsProblems(mode) {
            projectsConfigured(":", ":a")
            problem("Build file 'build.gradle': line 15: $expectedProblem")
        }

        where:
        service                      | prohibitedInvocation                   | expectedProblem
        "Gradle"                     | "plugins"                              | "Project ':' cannot access Gradle.getPlugins"
        "GradleInternal"             | "plugins"                              | "Project ':' cannot access Gradle.getPlugins"
        "TaskExecutionGraph"         | "whenReady { g -> g.hasTask(':a:x') }" | "Project ':' cannot access the tasks in the task graph that were created by other projects"
        "TaskExecutionGraphInternal" | "whenReady { g -> g.hasTask(':a:x') }" | "Project ':' cannot access the tasks in the task graph that were created by other projects"

        combined:
        mode << ALL_MODES
    }

    def "reports a problem on access to mutable state of `#service` injected into an object created with ObjectFactory"() {
        createDirs("a")
        settingsFile """
            include("a")
        """
        buildFile """
            import javax.inject.Inject
            import org.gradle.api.internal.GradleInternal
            import org.gradle.execution.taskgraph.TaskExecutionGraphInternal

            abstract class MyBean {
                @Inject
                abstract $service getService()
            }

            objects.newInstance(MyBean).service.$prohibitedInvocation
        """

        when:
        isolatedProjectsFailsUsing mode, "help"

        then:
        fixture.assertIsolatedProjectsProblems(mode) {
            projectsConfigured(":", ":a")
            problem("Build file 'build.gradle': line 11: $expectedProblem")
        }

        where:
        service                      | prohibitedInvocation                   | expectedProblem
        "Gradle"                     | "plugins"                              | "Project ':' cannot access Gradle.getPlugins"
        "GradleInternal"             | "plugins"                              | "Project ':' cannot access Gradle.getPlugins"
        "TaskExecutionGraph"         | "whenReady { g -> g.hasTask(':a:x') }" | "Project ':' cannot access the tasks in the task graph that were created by other projects"
        "TaskExecutionGraphInternal" | "whenReady { g -> g.hasTask(':a:x') }" | "Project ':' cannot access the tasks in the task graph that were created by other projects"

        combined:
        mode << ALL_MODES
    }
}
