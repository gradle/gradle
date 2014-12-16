/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.model

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Ignore

class TaskCreationIntegrationTest extends AbstractIntegrationSpec {

    def "can create tasks from model"() {
        given:
        buildScript """
            import org.gradle.model.*
            import org.gradle.model.collection.*

            class MyModel {
                List<String> tasks = []
            }

            class MyPlugin {
                @RuleSource
                static class Rules {
                    @Model
                    MyModel myModel() {
                        new MyModel()
                    }

                    @Mutate
                    void addTasks(CollectionBuilder<Task> tasks, MyModel myModel) {
                        myModel.tasks.each { n ->
                            tasks.create(n) {
                              description = "task \$n"
                            }
                        }
                    }
                }
            }

            apply type: MyPlugin

            model {
                myModel {
                    tasks << "a" << "b"
                }
            }
        """

        when:
        succeeds "tasks"

        then:
        output.contains "a - task a"
        output.contains "b - task b"
    }

    def "can configure generated tasks"() {
        given:
        buildScript """
            import org.gradle.model.*
            import org.gradle.model.collection.*

            class MyTasks {
                List<String> tasks = []
            }

            class MyMessage {
                String message
            }

            class MessageTask extends DefaultTask {
                String message = "default"

                @TaskAction
                void printMessages() {
                    println "\$name message: \$message"
                }
            }

            class MyPlugin {
                @RuleSource
                static class Rules {
                    @Model
                    MyTasks myTasks() {
                        new MyTasks()
                    }

                    @Model
                    MyMessage myMessage() {
                        new MyMessage()
                    }

                    @Mutate
                    void addTasks(CollectionBuilder<Task> tasks, MyTasks myTasks) {
                        myTasks.tasks.each { n ->
                            tasks.create(n, MessageTask) {
                              description = "task \$n"
                            }
                        }
                    }

                    @Mutate
                    void configureFoo(@Path("tasks.foo") MessageTask task, MyMessage myMessage) {
                        task.message = myMessage.message
                    }
                }
            }

            apply type: MyPlugin

            model {
                tasks.bar {
                    message = "custom message!"
                }
                myTasks {
                    tasks << "foo" << "bar"
                }
                myMessage {
                    message = "model message!"
                }
            }
        """

        when:
        succeeds "foo", "bar"

        then:
        output.contains "foo message: model message!"
        output.contains "bar message: custom message!"
    }

    def "can configure dependencies between generated tasks using task name"() {
        given:
        buildScript """
            import org.gradle.model.*
            import org.gradle.model.collection.*

            class MyPlugin {
                @RuleSource
                static class Rules {
                    @Mutate
                    void addTasks(CollectionBuilder<Task> tasks) {
                        tasks.create("foo")
                        tasks.create("bar")
                    }
                }
            }

            apply type: MyPlugin

            model {
                tasks.bar {
                    dependsOn "foo"
                }
            }
        """

        when:
        succeeds "bar"

        then:
        executedTasks == [":foo", ":bar"]
    }

    def "two rules attempt to create task"() {
        given:
        buildScript """
            import org.gradle.model.*
            import org.gradle.model.collection.*

            class MyModel {
                List<String> tasks = []
            }

            class MyPlugin {
                @RuleSource
                static class Rules {
                    @Model
                    MyModel myModel() {
                        new MyModel()
                    }

                    @Mutate
                    void addTasks1(CollectionBuilder<Task> tasks, MyModel myModel) {
                        myModel.tasks.each { n ->
                            tasks.create(n) {
                              description = "task \$n"
                            }
                        }
                    }

                    @Mutate
                    void addTasks2(CollectionBuilder<Task> tasks, MyModel myModel) {
                        myModel.tasks.each { n ->
                            tasks.create(n) {
                              description = "task \$n"
                            }
                        }
                    }
                }
            }

            apply type: MyPlugin

            model {
                myModel {
                    tasks << "a" << "b"
                }
            }
        """

        when:
        fails "tasks"

        then:
        failure.assertHasCause("Exception thrown while executing model rule: MyPlugin\$Rules#addTasks2(org.gradle.model.collection.CollectionBuilder<org.gradle.api.Task>, MyModel)")
        failure.assertHasCause("Cannot create 'tasks.a' as it was already created by: MyPlugin\$Rules#addTasks1(org.gradle.model.collection.CollectionBuilder<org.gradle.api.Task>, MyModel) > create(a)")
    }

    @Ignore("Not deferring creation of tasks right now, which means that the inner create can succeed")
    def "cannot create tasks during config of task"() {
        given:
        buildScript """
            import org.gradle.model.*
            import org.gradle.model.collection.*

            class MyPlugin {
                @RuleSource
                static class Rules {
                    @Mutate
                    void addTasks(CollectionBuilder<Task> tasks) {
                        tasks.create("foo") {
                          tasks.create("bar")
                        }
                    }
                }
            }

            apply type: MyPlugin
        """

        when:
        fails "tasks"

        then:
        failure.assertHasCause("Exception thrown while executing model rule: MyPlugin\$Rules#addTasks(org.gradle.model.collection.CollectionBuilder<org.gradle.api.Task>) > create(foo)")
        failure.assertHasCause("Attempt to mutate closed view of model 'tasks' of type 'org.gradle.model.collection.CollectionBuilder<org.gradle.api.Task>' given to rule 'MyPlugin\$Rules#addTasks(org.gradle.model.collection.CollectionBuilder<org.gradle.api.Task>)'")
    }

    def "failure during task instantiation is reasonably reported"() {
        given:
        buildScript """
            import org.gradle.model.*
            import org.gradle.model.collection.*

            class Faulty extends DefaultTask {
                Faulty() {
                    throw new RuntimeException("!")
                }
            }

            class MyPlugin {
                @RuleSource
                static class Rules {
                    @Mutate
                    void addTasks(CollectionBuilder<Task> tasks) {
                        tasks.create("foo", Faulty)
                    }
                }
            }

            apply type: MyPlugin
        """

        when:
        fails "tasks"

        then:
        failure.assertHasCause("Exception thrown while executing model rule: MyPlugin\$Rules#addTasks(org.gradle.model.collection.CollectionBuilder<org.gradle.api.Task>)")
        failure.assertHasCause("Could not create task of type 'Faulty'")
    }

    def "failure during task initial configuration is reasonably reported"() {
        given:
        buildScript """
            import org.gradle.model.*
            import org.gradle.model.collection.*

            class MyPlugin {
                @RuleSource
                static class Rules {
                    @Mutate
                    void addTasks(CollectionBuilder<Task> tasks) {
                        tasks.create("foo") {
                            throw new RuntimeException("config failure")
                        }
                    }
                }
            }

            apply type: MyPlugin
        """

        when:
        fails "tasks"

        then:
        failure.assertHasCause("Exception thrown while executing model rule: MyPlugin\$Rules#addTasks(org.gradle.model.collection.CollectionBuilder<org.gradle.api.Task>)")
        failure.assertHasCause("config failure")
    }

    def "failure during task configuration is reasonably reported"() {
        given:
        buildScript """
            import org.gradle.model.*
            import org.gradle.model.collection.*

            class MyPlugin {
                @RuleSource
                static class Rules {
                    @Mutate
                    void addTasks(CollectionBuilder<Task> tasks) {
                        tasks.create("foo")
                    }
                }
            }

            apply type: MyPlugin

            model {
                tasks.foo {
                    throw new RuntimeException("config failure")
                }
            }
        """

        when:
        fails "tasks"

        then:
        failure.assertHasCause("Exception thrown while executing model rule: model.tasks.foo")
        failure.assertHasCause("config failure")
        failure.assertHasLineNumber(19)
    }

    def "task created in afterEvaluate() is visible to a rule taking TaskContainer as input"() {
        when:
        buildScript '''
            import org.gradle.model.*

            class MyPlugin {
                @RuleSource
                static class Rules {
                    @Mutate
                    void fromAfterEvaluateTaskAvailable(TaskContainer tasks) {
                        tasks.fromAfterEvaluate.value = "from plugin"
                    }
                }
            }

            apply type: MyPlugin

            project.afterEvaluate {
                project.tasks.create("fromAfterEvaluate") {
                    ext.value = "from after evaluate"
                    doLast {
                        println "value: $value"
                    }
                }
            }
        '''

        then:
        succeeds "fromAfterEvaluate"

        and:
        output.contains "value: from plugin"
    }

    def "registering a creation rule for a task that already exists"() {
        when:
        buildScript """
            import org.gradle.model.*
            import org.gradle.model.collection.*

            class MyPlugin {
                @RuleSource
                static class Rules {
                    @Mutate
                    void addTask(CollectionBuilder<Task> tasks) {
                        tasks.create("foo")
                    }
                }
            }

            apply type: MyPlugin

            task foo {}
        """

        then:
        fails "foo"

        and:
        failure.assertHasCause("Cannot create 'tasks.foo' as it was already created by: Project.<init>.tasks.foo()")
    }

    def "can create task with invalid model space name"() {
        when:
        buildFile << """
            tasks.create(".").doFirst {}
        """

        run "."

        then:
        ":." in executedTasks
    }
}
