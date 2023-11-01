/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.execution.taskgraph

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.UnsupportedWithConfigurationCache
import org.gradle.model.internal.core.ModelNode

class RuleTaskExecutionIntegrationTest extends AbstractIntegrationSpec implements WithRuleBasedTasks {

    def setup() {
        buildFile << """
            def tasksFile = file("tasks.txt")
            tasksFile.text = ''
            gradle.taskGraph.whenReady {
                allprojects {
                    tasks.matching { it.group == "mygroup" }.all {
                        tasksFile << path + '\\n'
                    }
                }
            }
        """
    }

    List<String> getCreatedTasks() {
        file("tasks.txt").readLines().sort()
    }

    List<String> createdTasksFor(String... tasks) {
        succeeds(tasks)
        createdTasks
    }

    def "does not create rule based tasks in projects without required tasks"() {
        when:
        createDirs("a", "b", "c")
        settingsFile << "include 'a', 'b', 'c'"
        buildFile << """
            allprojects {
                model {
                    tasks {
                        create("t1") {
                            group = "mygroup"
                        }
                    }
                }
            }
        """

        then:
        createdTasksFor(":a:t1") == [":a:t1"]
        createdTasksFor(":a:t1", "b:t1") == [":a:t1", ":b:t1"]
        createdTasksFor(":t1") == [":t1"]
        createdTasksFor("t1") == [":a:t1", ":b:t1", ":c:t1", ":t1"]
    }

    def "rule based tasks that are not requested on the command line are not created"() {
        when:
        buildFile << """
            ${ruleBasedTasks()}
            model {
                tasks {
                    create("t1") {
                        group = "mygroup"
                    }
                    create("t2", BrokenTask) {
                        group = "mygroup"
                    }
                }
            }
        """

        then:
        createdTasksFor("t1") == [":t1"]
    }

    @UnsupportedWithConfigurationCache
    def "task container is self closed by task selection and can be later graph closed"() {
        when:
        buildFile << '''
            import org.gradle.model.internal.core.*

            model {
                tasks {
                    create("t1")
                    create("t2")
                }
            }
            def tasksPath = ModelPath.path("tasks")
            def registry = project.modelRegistry
            gradle.taskGraph.whenReady {
                println "task container node state when task graph ready: ${registry.state(tasksPath)}"
            }
            gradle.buildFinished {
                registry.atState(tasksPath, ModelNode.State.GraphClosed)
                println "task container node state after graph closing: ${registry.state(tasksPath)}"
            }
        '''

        then:
        succeeds "t1"

        and:
        output.contains "task container node state when task graph ready: ${ModelNode.State.SelfClosed}"
        output.contains "task container node state after graph closing: ${ModelNode.State.GraphClosed}"
    }

    def "tasks added via task container and not explicitly required but executed are self closed"() {
        given:
        buildScript """
            ${ruleBasedTasks()}

            class Rules extends RuleSource {
                @Mutate
                void configureDependencyTask(@Path("tasks.dependency") EchoTask task) {
                    task.message = "configured"
                }

                @Mutate
                void configureFinalizerTask(@Path("tasks.finalizer") EchoTask task) {
                    task.message = "configured"
                }

                @Mutate
                void addTasks(ModelMap<Task> tasks) {
                    tasks.create("requested") {
                        dependsOn "dependency"
                        finalizedBy "finalizer"
                    }
                }
            }

            apply type: Rules

            tasks.create("dependency", EchoTask)
            tasks.create("finalizer", EchoTask)
        """

        when:
        succeeds "requested"

        then:
        output.contains "dependency: configured"
        output.contains "finalizer: configured"
    }

    def "task container is self closed for projects of which any tasks are being executed"() {
        createDirs("a", "b")
        settingsFile << "include 'a', 'b'"

        buildScript """
            project(':a') {
                apply type: ProjectARules
            }

            project(':b') {
                apply type: ProjectBRules
            }

            class ProjectARules extends RuleSource {
                @Mutate
                void addTasks(ModelMap<Task> tasks) {
                    tasks.create("executed") {
                        dependsOn ":b:dependency"
                    }
                }
            }

            class ProjectBRules extends RuleSource {
                @Mutate
                void addTasks(ModelMap<Task> tasks) {
                    tasks.create("dependency")
                }
            }
        """

        when:
        succeeds ":a:executed"

        then:
        executed(":b:dependency")
    }

    def "can use getTasksByName() to get task defined in rules only script plugin after configuration"() {
        when:
        buildScript """
            apply from: "fooTask.gradle"
            task check {
                doLast {
                    assert getTasksByName("foo", false).toList().first().name == "foo"
                }
            }
        """

        file("fooTask.gradle") << """
            model {
                tasks { create("foo") }
            }
        """

        then:
        succeeds "check", "foo"
    }

    def "can use getTasksByName() to get task defined in rules only script plugin during configuration"() {
        when:
        buildScript """
            apply from: "fooTask.gradle"
            task check {
              def fooTasks = getTasksByName("foo", false).size()
              doFirst {
                assert fooTasks == 1
              }
            }
        """

        file("fooTask.gradle") << """
            model {
                tasks { create("foo") }
            }
        """

        then:
        succeeds "check", "foo"
    }

}
