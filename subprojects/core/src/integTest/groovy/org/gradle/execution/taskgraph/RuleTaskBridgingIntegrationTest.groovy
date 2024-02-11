/*
 * Copyright 2015 the original author or authors.
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

import groovy.test.NotYetImplemented
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.util.internal.TextUtil

import static org.gradle.integtests.fixtures.executer.TaskOrderSpecs.any

class RuleTaskBridgingIntegrationTest extends AbstractIntegrationSpec implements WithRuleBasedTasks {

    def "can view task container as various view types"() {
        given:
        buildFile << '''
            class MyPlugin extends RuleSource {
                @Mutate
                void applyMessages(ModelMap<Task> tasks) {
                    println "as map: $tasks"
                }
                @Mutate
                void applyMessages(TaskContainer tasks) {
                    println "as container: $tasks"
                }
                @Mutate
                void applyMessages(@Path("tasks") ModelElement tasks) {
                    println "as model element: $tasks"
                    println "name: $tasks.name"
                }
            }

            apply type: MyPlugin
        '''

        when:
        run()

        then:
        output.contains "as map: ModelMap<Task> 'tasks'"
        (
            // testing against full distribution
            output.contains("as container: [task ':buildEnvironment', task ':components', task ':dependencies', task ':dependencyInsight', task ':dependentComponents', task ':help', task ':init', task ':javaToolchains', task ':model', task ':outgoingVariants', task ':prepareKotlinBuildScriptModel', task ':projects', task ':properties', task ':resolvableConfigurations', task ':tasks', task ':wrapper']")
            // testing against reduced distribution
            || output.contains("as container: [task ':buildEnvironment', task ':components', task ':dependencies', task ':dependencyInsight', task ':dependentComponents', task ':help', task ':javaToolchains', task ':model', task ':outgoingVariants', task ':prepareKotlinBuildScriptModel', task ':projects', task ':properties', task ':resolvableConfigurations', task ':tasks']")
        )
        output.contains "as model element: ModelMap<Task> 'tasks'"
        output.contains "name: tasks"
    }

    def "can view tasks as various view types"() {
        given:
        buildFile << """
            ${ruleBasedTasks()}

            class MyPlugin extends RuleSource {
                @Mutate
                void tasks(ModelMap<EchoTask> tasks) {
                    tasks.create("test")
                }
                @Mutate
                void applyMessages(@Path("tasks.test") Task task) {
                    println "as task: \$task"
                }
                @Mutate
                void applyMessages(@Path("tasks.test") EchoTask task) {
                    println "as task subtype: \$task"
                }
                @Mutate
                void applyMessages(@Path("tasks.test") ModelElement task) {
                    println "as model element: \$task"
                    println "name: \$task.name"
                }
            }

            apply type: MyPlugin
        """

        when:
        run("test")

        then:
        output.contains "as task: task ':test'"
        output.contains "as task subtype: task ':test'"
        output.contains "as model element: EchoTask 'tasks.test'"
        output.contains "name: test"
    }

    def "mutate rules are applied to tasks created using legacy DSL when the task is added to the task graph"() {
        given:
        buildFile << """
            ${ruleBasedTasks()}

            class MyPlugin extends RuleSource {
                @Mutate
                void applyMessages(ModelMap<EchoTask> tasks) {
                    tasks.afterEach {
                        message += " message!"
                    }
                }
            }

            apply type: MyPlugin

            task foo(type: EchoTask) { message = 'custom' }
            task bar(type: EchoTask)
            task dep { dependsOn foo, bar }
        """

        when:
        succeeds "foo", "bar"

        then:
        output.contains "foo: custom message!"
        output.contains "bar: default message!"

        when:
        succeeds "dep"

        then:
        output.contains "foo: custom message!"
        output.contains "bar: default message!"
    }

    def "mutate rules are not applied to tasks created using legacy DSL when the task is not added to the task graph"() {
        given:
        buildFile << """
            ${ruleBasedTasks()}

            model {
                tasks.foo {
                    message += " message!"
                }
                tasks.bar {
                    throw new RuntimeException()
                }
            }

            task foo(type: EchoTask) { message = 'custom' }
            task bar(type: EchoTask)
            task dep {
                dependsOn foo
                shouldRunAfter bar
                mustRunAfter bar
            }
        """

        when:
        succeeds "foo"

        then:
        output.contains "foo: custom message!"

        when:
        succeeds "dep"

        then:
        output.contains "foo: custom message!"
    }

    def "mutate rules are applied to task created using legacy DSL after task is configured from legacy DSL"() {
        given:
        buildFile << """
            ${ruleBasedTasks()}

            model {
                tasks.foo {
                    message += " message!"
                }
            }

            task foo(type: EchoTask)
            assert foo.message == 'default'
            foo.message = 'custom'
        """

        when:
        succeeds "foo"

        then:
        output.contains "foo: custom message!"
    }

    @NotYetImplemented
    def "mutate rules are applied to task created using rules after task is configured from legacy DSL"() {
        given:
        buildFile << """
            ${ruleBasedTasks()}

            model {
                tasks {
                    foo(EchoTask) {
                        message = 'rules'
                    }
                }
                tasks.foo {
                    message += " message!"
                }
            }

            assert foo.message == 'rules'
            foo.message = 'custom'
        """

        when:
        succeeds "foo"

        then:
        output.contains "foo: custom message!"
    }

    def "task initializer defined by rule is invoked before actions defined through legacy task container DSL"() {
        given:
        buildFile << """
            ${ruleBasedTasks()}

            class MyPlugin extends RuleSource {
                @Mutate
                void addTasks(ModelMap<EchoTask> tasks) {
                    tasks.create("foo") {
                        message = "foo message"
                    }
                }
            }

            apply type: MyPlugin

            tasks.withType(EchoTask).all {
                message = "task \$message"
            }
        """

        when:
        succeeds "foo"

        then:
        output.contains "foo: task foo message"
    }

    def "task created in afterEvaluate() is visible to rules"() {
        when:
        buildFile << '''
            class MyPlugin extends RuleSource {
                @Mutate
                void fromAfterEvaluateTaskAvailable(ModelMap<Task> tasks) {
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
        failure.assertHasCause("Cannot create 'tasks.foo' using creation rule 'MyPlugin#addTask(ModelMap<Task>) > create(foo)' as the rule 'Project.<init>.tasks.foo()' is already registered to create this model element.")
    }

    def "registering creation rules to create a task using legacy container DSL that is already defined using container DSL"() {
        when:
        buildFile << """
            class MyPlugin extends RuleSource {
                @Mutate
                void addTaskInContainer(TaskContainer tasks) {
                    println("create task in container")
                    tasks.create("foo") {
                        doLast {
                            println("created on TaskContainer")
                        }
                    }
                }

                @Mutate
                void addTask(ModelMap<Task> tasks) {
                    println("create task in model map")
                    tasks.create("foo") {
                        doLast {
                            println("created on ModelMap")
                        }
                    }
                }
            }

            apply type: MyPlugin
        """

        then:
        fails "foo"

        and:
        failure.assertHasCause("Cannot add task 'foo' as a task with that name already exists.")
    }

    def "a non-rule-source task can depend on a rule-source task"() {
        given:
        buildFile << """
        ${ruleBasedTasks()}

        class Rules extends RuleSource {
            @Mutate
            void addTasks(ModelMap<Task> tasks) {
                tasks.create("climbTask", ClimbTask) { }
            }
        }
        apply type: Rules

        task customTask
        customTask.dependsOn tasks.withType(ClimbTask)
        """

        when:
        succeeds('customTask')

        then:
        result.assertTasksExecutedInOrder(':climbTask', ':customTask')
    }

    def "a non-rule-source task can depend on one or more task of types created via both rule sources and old world container"() {
        given:
        buildFile << """
        ${ruleBasedTasks()}

        class Rules extends RuleSource {
            @Mutate
            void addTasks(ModelMap<Task> tasks) {
                tasks.create("climbTask", ClimbTask) { }
            }
        }
        apply type: Rules

        task oldClimber(type: ClimbTask) { }
        task customTask

        customTask.dependsOn tasks.withType(ClimbTask)
        """

        when:
        succeeds('customTask')

        then:
        result.assertTasksExecutedInOrder(any(':climbTask', ':oldClimber'),  ':customTask')
    }

    def "can depend on a rule-source task in a project which has already evaluated"() {
        given:
        createDirs("sub1", "sub2")
        settingsFile << 'include "sub1", "sub2"'
        buildFile << """
        ${ruleBasedTasks()}

        class Rules extends RuleSource {
            @Mutate
            void addTasks(ModelMap<Task> tasks) {
                tasks.create("climbTask", ClimbTask) { }
            }
        }

        project("sub1") {
            apply type: Rules
        }

        project("sub2") {
            evaluationDependsOn ':sub1'

            task customTask  {
                dependsOn project(":sub1").tasks.withType(ClimbTask)
            }
        }
        """

        when:
        succeeds('sub2:customTask')

        then:
        result.assertTasksExecutedInOrder(':sub1:climbTask', ':sub2:customTask')
    }

    def "can depend on a rule-source task after a project has been evaluated"() {
        given:
        buildFile << """
        ${ruleBasedTasks()}

        class Rules extends RuleSource {
            @Mutate
            void addTasks(ModelMap<Task> tasks) {
                tasks.create("climbTask", ClimbTask) { }
            }
        }
        apply type: Rules

        task customTask

        afterEvaluate {
            customTask.dependsOn tasks.withType(ClimbTask)
        }
        """

        when:
        succeeds('customTask')

        then:
        result.assertTasksExecutedInOrder(':climbTask', ':customTask')
    }

    def "a build failure occurs when depending on a rule task with failing configuration"() {
        given:
        buildFile << """
        ${ruleBasedTasks()}

        class Rules extends RuleSource {
            @Mutate
            void addTasks(ModelMap<Task> tasks) {
                tasks.create("climbTask", ClimbTask) {
                    throw new GradleException("Bang")
                }
            }
        }
        apply type: Rules

        task customTask { doLast {} }

        customTask.dependsOn tasks.withType(ClimbTask)
        """

        when:
        fails('customTask')

        then:
        failure.assertHasDescription("Could not determine the dependencies of task ':customTask'.")
        failure.assertHasCause('Exception thrown while executing model rule: Rules#addTasks(ModelMap<Task>) > create(climbTask)')
        failure.assertHasCause('Bang')
    }

    def "can not depend on a general Task"() {
        given:
        buildFile << """
        task customTask
        customTask.dependsOn tasks.withType(Task)
        """

        when:
        fails('customTask')

        then:
        failure.assertHasDescription(TextUtil.normaliseLineSeparators("""Circular dependency between the following tasks:
:customTask
\\--- :customTask (*)"""))
    }

    def "a non-rule-source task can depend on a rule-source task through another task collection"() {
        given:
        buildFile << """
        ${ruleBasedTasks()}

        class Rules extends RuleSource {
            @Mutate
            void addTasks(ModelMap<Task> tasks) {
                tasks.create("climbTask", ClimbTask) { }
            }
        }
        apply type: Rules

        task customTask
        def t = tasks.withType(Task).withType(ClimbTask)
        customTask.dependsOn t
        """

        when:
        succeeds('customTask')

        then:
        result.assertTasksExecutedInOrder(':climbTask', ':customTask')
    }

    def "a non-rule-source task can depend on a rule-source task with matching criteria"() {
        given:
        buildFile << """
        ${ruleBasedTasks()}

        class Rules extends RuleSource {
            @Mutate
            void addTasks(ModelMap<Task> tasks) {
                tasks.create("climbTask", ClimbTask) { }
            }
        }
        apply type: Rules

        task customTask
        customTask.dependsOn tasks.withType(ClimbTask).matching { true }
        """

        when:
        succeeds('customTask')

        then:
        result.assertTasksExecutedInOrder(':climbTask', ':customTask')
    }

    def "a non-rule-source task can not depend on both realizable and default task collections"() {
        given:
        buildFile << """
        ${ruleBasedTasks()}

        class Rules extends RuleSource {
            @Mutate
            void addTasks(ModelMap<Task> tasks) {
                tasks.create("climbTask", ClimbTask) { }
            }
        }
        apply type: Rules

        task foo
        task customTask
        customTask.dependsOn tasks.withType(ClimbTask) + [tasks.foo]
        """

        when:
        succeeds('customTask')

        then:
        result.assertTasksExecutedInOrder(':foo', ':customTask')
    }

    @NotYetImplemented
    def "a non-rule-source task can depend on combined task collections"() {
        given:
        buildFile << """
        ${ruleBasedTasks()}

        class Rules extends RuleSource {
            @Mutate
            void addTasks(ModelMap<Task> tasks) {
                tasks.create("climbTask", ClimbTask)
                tasks.create("jumpTask", JumpTask)
            }
        }
        apply type: Rules

        task customTask
        customTask.dependsOn tasks.withType(ClimbTask) + tasks.withType(JumpTask)
        """

        when:
        succeeds('customTask')

        then:
        result.assertTasksExecutedInOrder(':customTask', ':climbTask', ':jumpTask')
    }

    @NotYetImplemented
    def "actions are applied to a rule-source task using all task action constructs"() {
        given:
        buildFile << """
        class OverruleTask extends EchoTask {}
        ${ruleBasedTasks()}

        class Rules extends RuleSource {
            @Mutate
            void addTasks(ModelMap<Task> tasks) {
                tasks.create("actionMan", EchoTask) {
                    text = 'This is your commander speaking'
                }
                tasks.create("climbTask", ClimbTask) {}
                tasks.create("jumpTask", JumpTask) {}
                tasks.create("overruleTask", OverruleTask) {}
            }
        }
        apply type: Rules
        tasks.withType(OverruleTask) { it.text = "actionWoman I'm the real commander" }
        tasks.withType(ClimbTask).all { it.steps = 14 }
        tasks.matching { it.name.contains('jump') }.all { it.height = 7 }


        //It should be possible to reference the tasks without having to do tasks.realize()
        assert overruleTask.text == "actionWoman I'm the real commander"
        assert jumpTask.height == 7
        assert climbTask.steps == 14
        """

        when:
        succeeds('actionMan')

        then:
        output.contains("actionMan This is your commander speaking")
    }

    @NotYetImplemented
    def "rule sources can have a task with some action applied to it as a rule subject"() {
        given:
        buildFile << """
        ${ruleBasedTasks()}
        class Rules extends RuleSource {
            @Mutate
            void addTasks(ModelMap<Task> tasks) {
                tasks.create("climbTask", ClimbTask) {}
            }

            @Mutate
            void plusOne(@Path("tasks.climbTask") ClimbTask climbTask){
                climbTask.steps += 1
            }
        }
        apply type: Rules
        tasks.withType(ClimbTask).all { it.steps = 2 }

        //It should be possible to reference the tasks without having to do tasks.realize()
        assert climbTask.steps == 3
        """

        expect:
        succeeds('help')
    }

    def "only tasks of specified type are created when tasks with type are declared as dependency"() {
        when:
        buildScript """
            ${ruleBasedTasks()}

            model {
              tasks {
                create("climbTask", ClimbTask)
                create("jumpTask", BrokenTask)
              }
            }

            task customTask {
              dependsOn tasks.withType(ClimbTask)
            }
        """

        and:
        run "customTask"

        then:
        result.assertTasksExecutedInOrder(':climbTask', ':customTask')
    }
}
