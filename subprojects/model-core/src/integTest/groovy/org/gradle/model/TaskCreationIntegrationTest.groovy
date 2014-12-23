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
import org.gradle.util.TextUtil
import spock.lang.Ignore

class TaskCreationIntegrationTest extends AbstractIntegrationSpec {
    def setup() {
        buildFile << """
            import org.gradle.model.*
            import org.gradle.model.collection.*

            class MessageTask extends DefaultTask {
                String message = "default"

                @TaskAction
                void printMessages() {
                    println "\$name message: \$message"
                }
            }
"""
    }

    def "can create tasks from model"() {
        given:
        buildFile << """
            class MyModel {
                List<String> tasks = []
            }

            @RuleSource
            class MyPlugin {
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

    def "can configure tasks using rule DSL"() {
        given:
        buildFile << """
            class MyMessage {
                String message
            }

            @RuleSource
            class MyPlugin {
                @Model
                MyMessage myMessage() {
                    new MyMessage()
                }

                @Mutate
                void addTasks(CollectionBuilder<Task> tasks, MyMessage myMessage) {
                    ['foo', 'bar'].each { n ->
                        tasks.create(n, MessageTask) {
                            message = myMessage.message
                        }
                    }
                }
            }

            apply type: MyPlugin

            model {
                tasks.bar {
                    message = "custom message!"
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

    def "can configure tasks using mutate rule method"() {
        given:
        buildFile << """
            class MyMessage {
                String message
            }

            @RuleSource
            class MyPlugin {
                @Model
                MyMessage myMessage() {
                    new MyMessage()
                }

                @Mutate
                void customMessage(@Path('tasks.bar') MessageTask task) {
                    task.message = 'custom message!'
                }

                @Mutate
                void addTasks(CollectionBuilder<Task> tasks, MyMessage myMessage) {
                    ['foo', 'bar'].each { n ->
                        tasks.create(n, MessageTask) {
                            message = myMessage.message
                        }
                    }
                }
            }

            apply type: MyPlugin

            model {
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

    def "can apply mutate rule to all tasks"() {
        given:
        buildFile << """
            class MyMessage {
                String message
            }

            @RuleSource
            class MyPlugin {
                @Model
                MyMessage myMessage() {
                    new MyMessage()
                }

                @Mutate
                void addTasks(CollectionBuilder<MessageTask> tasks) {
                    ['foo', 'bar'].each { n ->
                        tasks.create(n, MessageTask)
                    }

                }

                @Mutate
                void applyMessages(CollectionBuilder<MessageTask> tasks, MyMessage myMessage) {
                    tasks.all {
                        message = myMessage.message
                    }
                }

                @Mutate
                void cleanupMessages(CollectionBuilder<MessageTask> tasks) {
                    tasks.finalizeAll {
                        message += " message!"
                    }
                }
            }

            apply type: MyPlugin

            model {
                myMessage {
                    message = "model"
                }
            }
        """

        when:
        succeeds "foo", "bar"

        then:
        output.contains "foo message: model message!"
        output.contains "bar message: model message!"
    }

    def "tasks created using legacy DSL are visible to rules"() {
        given:
        buildFile << """
            @RuleSource
            class MyPlugin {
                @Mutate
                void applyMessages(CollectionBuilder<MessageTask> tasks) {
                    tasks.all {
                        message += " message!"
                    }
                }
            }

            apply type: MyPlugin

            task foo(type: MessageTask) { message = 'custom' }
            task bar(type: MessageTask)
        """

        when:
        succeeds "foo", "bar"

        then:
        output.contains "foo message: custom message!"
        output.contains "bar message: default message!"
    }

    def "task initializer defined by rule is invoked before actions defined through legacy task container DSL"() {
        given:
        buildFile << """
            @RuleSource
            class MyPlugin {
                @Mutate
                void addTasks(CollectionBuilder<Task> tasks) {
                    tasks.create("foo") {
                        doLast { println "from rule" }
                    }
                }
            }

            apply type: MyPlugin

            tasks.all {
                doLast { println "from all closure" }
            }
        """

        when:
        succeeds "foo"

        then:
        output.contains TextUtil.toPlatformLineSeparators("""from rule
from all closure
""")
    }

    def "can configure dependencies between tasks using task name"() {
        given:
        buildFile << """
            @RuleSource
            class MyPlugin {
                @Mutate
                void addTasks(CollectionBuilder<Task> tasks) {
                    tasks.create("foo")
                    tasks.create("bar")
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

    @Ignore
    def "task instantiation and configuration is deferred until required"() {
        given:
        buildFile << """
            class SomeTask extends DefaultTask {
                SomeTask() { println "\$name created" }
            }

            @RuleSource
            class MyPlugin {
                @Mutate
                void addTasks(CollectionBuilder<SomeTask> tasks) {
                    tasks.create("foo") {
                        println "\$name configured"
                    }
                    tasks.create("bar") {
                        println "\$name configured"
                    }
                    println "tasks defined"
                }
            }

            apply type: MyPlugin
        """

        when:
        succeeds "foo", "bar"

        then:
        output.contains("""tasks defined
bar created
bar configured
foo created
foo configured
""")
    }

    def "two rules attempt to create task"() {
        given:
        buildFile << """
            class MyModel {
                List<String> tasks = []
            }

            @RuleSource
            class MyPlugin {
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
        failure.assertHasCause("Exception thrown while executing model rule: MyPlugin#addTasks2(org.gradle.model.collection.CollectionBuilder<org.gradle.api.Task>, MyModel)")
        failure.assertHasCause("Cannot create 'tasks.a' as it was already created by: MyPlugin#addTasks1(org.gradle.model.collection.CollectionBuilder<org.gradle.api.Task>, MyModel) > create(a)")
    }

    @Ignore
    def "cannot create tasks during config of task"() {
        given:
        buildFile << """
            @RuleSource
            class MyPlugin {
                @Mutate
                void addTasks(CollectionBuilder<Task> tasks) {
                    tasks.create("foo") {
                      tasks.create("bar")
                    }
                }
            }

            apply type: MyPlugin
        """

        when:
        fails "tasks"

        then:
        failure.assertHasCause("Exception thrown while executing model rule: MyPlugin#addTasks(org.gradle.model.collection.CollectionBuilder<org.gradle.api.Task>) > create(foo)")
        failure.assertHasCause("Attempt to mutate closed view of model of type 'org.gradle.model.collection.CollectionBuilder<org.gradle.api.Task>' given to rule 'MyPlugin#addTasks(org.gradle.model.collection.CollectionBuilder<org.gradle.api.Task>)'")
    }

    def "failure during task instantiation is reasonably reported"() {
        given:
        buildFile << """
            class Faulty extends DefaultTask {
                Faulty() {
                    throw new RuntimeException("!")
                }
            }

            @RuleSource
            class MyPlugin {
                @Mutate
                void addTasks(CollectionBuilder<Task> tasks) {
                    tasks.create("foo", Faulty)
                }
            }

            apply type: MyPlugin
        """

        when:
        fails "tasks"

        then:
        failure.assertHasCause("Exception thrown while executing model rule: MyPlugin#addTasks(org.gradle.model.collection.CollectionBuilder<org.gradle.api.Task>)")
        failure.assertHasCause("Could not create task of type 'Faulty'")
    }

    def "failure during task initial configuration is reasonably reported"() {
        given:
        buildFile << """
            @RuleSource
            class MyPlugin {
                @Mutate
                void addTasks(CollectionBuilder<Task> tasks) {
                    tasks.create("foo") {
                        throw new RuntimeException("config failure")
                    }
                }
            }

            apply type: MyPlugin
        """

        when:
        fails "tasks"

        then:
        failure.assertHasCause("Exception thrown while executing model rule: MyPlugin#addTasks(org.gradle.model.collection.CollectionBuilder<org.gradle.api.Task>)")
        failure.assertHasCause("config failure")
    }

    def "failure during task configuration is reasonably reported"() {
        given:
        buildFile << """
            @RuleSource
            class MyPlugin {
                @Mutate
                void addTasks(CollectionBuilder<Task> tasks) {
                    tasks.create("foo")
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
        failure.assertHasLineNumber(26)
    }

    def "task created in afterEvaluate() is visible to rules"() {
        when:
        buildFile << '''
            @RuleSource
            class MyPlugin {
                @Mutate
                void fromAfterEvaluateTaskAvailable(TaskContainer tasks) {
                    tasks.fromAfterEvaluate.value += " and from container rule"
                }
                @Mutate
                void fromAfterEvaluateTaskAvailable(@Path("tasks.fromAfterEvaluate") Task task) {
                    task.value += " and from rule"
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
        output.contains "value: from after evaluate and from container rule and from rule"
    }

    def "registering a creation rule for a task that already exists"() {
        when:
        buildFile << """
            @RuleSource
            class MyPlugin {
                @Mutate
                void addTask(CollectionBuilder<Task> tasks) {
                    tasks.create("foo")
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
