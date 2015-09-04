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

import org.gradle.api.reporting.model.ModelReportOutput
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.util.TextUtil

class TaskCreationIntegrationTest extends AbstractIntegrationSpec {
    def setup() {
        buildFile << """
            class MessageTask extends DefaultTask {
                String message = "default"

                @TaskAction
                void printMessages() {
                    println "\$name message: \$message"
                }
            }
"""
    }

    def "can use rule method to create tasks from model"() {
        given:
        buildFile << """
            class MyModel {
                List<String> tasks = []
            }

            class MyPlugin extends RuleSource {
                @Model
                MyModel myModel() {
                    new MyModel()
                }

                @Mutate
                void addTasks(ModelMap<Task> tasks, MyModel myModel) {
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

    def "can use rule DSL to create tasks"() {
        given:
        buildFile << """
            model {
                tasks {
                    a {
                        description = 'task a'
                    }
                    a(MessageTask)
                    b(MessageTask) {
                        description = 'task b'
                    }
                    c(Task) {
                        description = 'task c'
                    }
                }
            }
        """

        when:
        succeeds "tasks"

        then:
        output.contains "a - task a"
        output.contains "b - task b"
        output.contains "c - task c"
    }

    def "can configure tasks using rule DSL"() {
        given:
        buildFile << """
            class MyMessage {
                String message
            }

            class MyPlugin extends RuleSource {
                @Model
                MyMessage myMessage() {
                    new MyMessage()
                }

                @Mutate
                void addTasks(ModelMap<Task> tasks, MyMessage myMessage) {
                    ['foo', 'bar'].each { n ->
                        tasks.create(n, MessageTask) {
                            message = "\${myMessage.message} \${name}: "
                        }
                    }
                }
            }

            apply type: MyPlugin

            model {
                tasks.bar {
                    message += "bar message!"
                }
                tasks {
                    foo {
                        message += 'foo message!'
                    }
                }
                myMessage {
                    message = "task"
                }
            }
        """

        when:
        succeeds "foo", "bar"

        then:
        output.contains "foo message: task foo: foo message!"
        output.contains "bar message: task bar: bar message!"
    }

    def "can configure tasks using rule methods taking some input"() {
        given:
        buildFile << """
            class MyMessage {
                String message
            }

            class MyPlugin extends RuleSource {
                @Model
                MyMessage myMessage() {
                    new MyMessage()
                }

                @Mutate
                void customMessage(@Path('tasks.bar') MessageTask task) {
                    task.message += ' from'
                }

                @Defaults
                void prepareMessage(@Path('tasks.bar') MessageTask task) {
                    task.message = "task bar: "
                }

                @Finalize
                void tweakCustomMessage(@Path('tasks.bar') MessageTask task) {
                    task.message += " \$task.name"
                }

                @Mutate
                void addTasks(ModelMap<MessageTask> tasks, MyMessage myMessage) {
                    tasks.create('bar') {
                        message += myMessage.message
                    }
                    tasks.create('foo') {
                        message = 'foo'
                    }
                }
            }

            apply type: MyPlugin

            model {
                myMessage {
                    message = "hi"
                }
            }
        """

        when:
        succeeds "foo", "bar"

        then:
        output.contains "foo message: foo"
        output.contains "bar message: task bar: hi from bar"
    }

    def "can validate tasks using rule methods"() {
        given:
        buildFile << """
            class MyPlugin extends RuleSource {
                @Validate
                void checkTask(@Path('tasks.bar') MessageTask task) {
                    throw new RuntimeException("task is invalid!")
                }

                @Mutate
                void addTasks(ModelMap<Task> tasks) {
                    ['foo', 'bar'].each { n ->
                        tasks.create(n, MessageTask)
                    }
                }
            }

            apply type: MyPlugin
        """

        when:
        fails "bar"

        then:
        failure.assertHasCause('Exception thrown while executing model rule: MyPlugin#checkTask')
        failure.assertHasCause('task is invalid!')
    }

    def "can use ModelMap API from a method rule to apply rules to tasks"() {
        given:
        buildFile << """
            class MyMessage {
                String message
            }

            class MyPlugin extends RuleSource {
                @Model
                MyMessage myMessage() {
                    new MyMessage()
                }

                @Mutate
                void addTasks(ModelMap<MessageTask> tasks) {
                    ['foo', 'bar'].each { n ->
                        tasks.create(n, MessageTask) {
                            message = "\$message \$name"
                        }
                    }
                }

                @Defaults
                void applyMessages(ModelMap<MessageTask> tasks, MyMessage myMessage) {
                    tasks.beforeEach {
                        message = myMessage.message
                    }
                    tasks.all {
                        message += " with"
                    }
                    tasks.afterEach {
                        message += " message!"
                    }
                }

                @Mutate
                void cleanupMessages(ModelMap<MessageTask> tasks) {
                    tasks.named('bar') {
                        message = "[\$message]"
                    }
                }
            }

            apply type: MyPlugin

            model {
                myMessage {
                    message = "task"
                }
            }
        """

        when:
        succeeds "foo", "bar"

        then:
        output.contains "foo message: task foo with message!"
        output.contains "bar message: [task bar with] message!"
    }

    def "can use rule DSL to apply rules to all tasks"() {
        given:
        buildFile << """
            class MyPlugin extends RuleSource {
                @Mutate
                void addTasks(ModelMap<MessageTask> tasks) {
                    ['foo', 'bar'].each { n ->
                        tasks.create(n, MessageTask) {
                            message = "\$message \$name"
                        }
                    }

                }
            }

            apply type: MyPlugin

            model {
                tasks {
                    def messageTasks = withType(MessageTask)
                    messageTasks.beforeEach {
                        message = "task"
                    }
                    messageTasks.all {
                        message += " with"
                    }
                    messageTasks.afterEach {
                        message += " message"
                    }
                }
            }
        """

        when:
        succeeds "foo", "bar"

        then:
        output.contains "foo message: task foo with message"
        output.contains "bar message: task bar with message"
    }

    def "tasks created using legacy DSL are visible to rules"() {
        given:
        buildFile << """
            class MyPlugin extends RuleSource {
                @Mutate
                void applyMessages(ModelMap<MessageTask> tasks) {
                    tasks.afterEach {
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
            class MyPlugin extends RuleSource {
                @Mutate
                void addTasks(ModelMap<MessageTask> tasks) {
                    tasks.create("foo") {
                        message = "foo message"
                    }
                }
            }

            apply type: MyPlugin

            tasks.withType(MessageTask).all {
                message = "task \$message"
            }
        """

        when:
        succeeds "foo"

        then:
        output.contains "foo message: task foo message"
    }

    def "can configure dependencies between tasks using task name"() {
        given:
        buildFile << """
            class MyPlugin extends RuleSource {
                @Mutate
                void addTasks(ModelMap<Task> tasks) {
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

    def "task instantiation and configuration is deferred until required"() {
        given:
        buildFile << """
            class SomeTask extends DefaultTask {
                SomeTask() { println "\$name created" }
            }

            class MyPlugin extends RuleSource {
                @Mutate
                void addTasks(ModelMap<SomeTask> tasks) {
                    tasks.create("foo") {
                        println "\$name configured"
                    }
                    tasks.create("bar") {
                        println "\$name configured"
                    }
                    tasks.beforeEach {
                        println "\$name initialized"
                    }
                    println "tasks defined"
                }
            }

            apply type: MyPlugin
        """

        when:
        succeeds "bar", "foo"

        then:
        output.contains(TextUtil.toPlatformLineSeparators("""tasks defined
bar created
bar initialized
bar configured
foo created
foo initialized
foo configured
"""))
    }

    def "two rules attempt to create task"() {
        given:
        buildFile << """
            class MyModel {
                List<String> tasks = []
            }

            class MyPlugin extends RuleSource {
                @Model
                MyModel myModel() {
                    new MyModel()
                }

                @Mutate
                void addTasks1(ModelMap<Task> tasks, MyModel myModel) {
                    myModel.tasks.each { n ->
                        tasks.create(n) {
                          description = "task \$n"
                        }
                    }
                }

                @Mutate
                void addTasks2(ModelMap<Task> tasks, MyModel myModel) {
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
        failure.assertHasCause("Exception thrown while executing model rule: MyPlugin#addTasks2")
        failure.assertHasCause("Cannot create 'tasks.a' using creation rule 'MyPlugin#addTasks2 > create(a)' as the rule 'MyPlugin#addTasks1 > create(a)' is already registered to create this model element.")
    }

    def "cannot create tasks during config of task"() {
        given:
        buildFile << """
            class MyPlugin extends RuleSource {
                @Mutate
                void addTasks(ModelMap<Task> tasks) {
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
        failure.assertHasCause("Exception thrown while executing model rule: MyPlugin#addTasks > create(foo)")
        failure.assertHasCause("Attempt to mutate closed view of model of type 'org.gradle.model.ModelMap<org.gradle.api.Task>' given to rule 'MyPlugin#addTasks'")
    }

    def "failure during task instantiation is reasonably reported"() {
        given:
        buildFile << """
            class Faulty extends DefaultTask {
                Faulty() {
                    throw new RuntimeException("!")
                }
            }

            class MyPlugin extends RuleSource {
                @Mutate
                void addTasks(ModelMap<Task> tasks) {
                    tasks.create("foo", Faulty)
                }
            }

            apply type: MyPlugin
        """

        when:
        fails "tasks"

        then:
        failure.assertHasCause("Exception thrown while executing model rule: MyPlugin#addTasks > create(foo)")
        failure.assertHasCause("Could not create task of type 'Faulty'")
    }

    def "failure during task initial configuration is reasonably reported"() {
        given:
        buildFile << """
            class MyPlugin extends RuleSource {
                @Mutate
                void addTasks(ModelMap<Task> tasks) {
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
        failure.assertHasCause("Exception thrown while executing model rule: MyPlugin#addTasks")
        failure.assertHasCause("config failure")
    }

    def "failure during task configuration is reasonably reported"() {
        given:
        buildFile << """
            class MyPlugin extends RuleSource {
                @Mutate
                void addTasks(ModelMap<Task> tasks) {
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
        failure.assertHasLineNumber(22)
    }

    def "task created in afterEvaluate() is visible to rules"() {
        when:
        buildFile << '''
            class MyPlugin extends RuleSource {
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

    def "registering a creation rule for a task that is already defined using legacy DSL"() {
        when:
        buildFile << """
            class MyPlugin extends RuleSource {
                @Mutate
                void addTask(ModelMap<Task> tasks) {
                    tasks.create("foo")
                }
            }

            apply type: MyPlugin

            task foo {}
        """

        then:
        fails "foo"

        and:
        failure.assertHasCause("Cannot create 'tasks.foo' using creation rule 'MyPlugin#addTask > create(foo)' as the rule 'Project.<init>.tasks.foo()' is already registered to create this model element.")
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

    def "proxy classes are never used as task instance nodes types"(){
        given:
        buildFile << """
tasks.create(name: 'taskContainerTask', type: DefaultTask) { }

task standardTask << {}

model {
  tasks {
    newModelTask(Task) {}
  }
}

class MyPlugin extends RuleSource {
    @Mutate
    void addTasks(ModelMap<Task> tasks) {
        tasks.create('modelMapTask') {}
    }
}
apply type: MyPlugin
"""

        when:
        succeeds("model")

        then:
        def tasksNode = ModelReportOutput.from(output).modelNode.tasks
        tasksNode.taskContainerTask.@type[0] == 'org.gradle.api.DefaultTask'
        tasksNode.standardTask.@type[0] == 'org.gradle.api.DefaultTask'
        tasksNode.newModelTask.@type[0] == 'org.gradle.api.Task'
        tasksNode.modelMapTask.@type[0] == 'org.gradle.api.Task'
        tasksNode.model.@type[0] == 'org.gradle.api.reporting.model.ModelReport' //Placeholder task
    }
}
