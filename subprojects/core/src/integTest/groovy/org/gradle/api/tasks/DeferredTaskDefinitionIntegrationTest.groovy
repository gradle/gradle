/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.tasks


import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Ignore
import spock.lang.Issue
import spock.lang.Unroll

class DeferredTaskDefinitionIntegrationTest extends AbstractIntegrationSpec {
    private static final String CUSTOM_TASK_WITH_CONSTRUCTOR_ARGS = """
        import javax.inject.Inject

        class CustomTask extends DefaultTask {
            @Internal
            final String message
            @Internal
            final int number

            @Inject
            CustomTask(String message, int number) {
                this.message = message
                this.number = number
            }

            @TaskAction
            void printIt() {
                println("\$message \$number")
            }
        }
    """

    def setup() {
        buildFile << '''
            class SomeTask extends DefaultTask {
                SomeTask() {
                    println("Create ${path}")
                }
            }
            class SomeSubTask extends SomeTask {
                SomeSubTask() {
                    println("Create subtask ${path}")
                }
            }
            class SomeOtherTask extends DefaultTask {
                SomeOtherTask() {
                    println("Create ${path}")
                }
            }
        '''
        settingsFile << """
            rootProject.name = 'root'
        """
    }

    def "task is created and configured when included directly in task graph"() {
        buildFile << '''
            tasks.register("task1", SomeTask) {
                println "Configure ${path}"
            }
            tasks.register("task2", SomeTask) {
                println "Configure ${path}"
            }
            tasks.register("task3", SomeTask)
        '''

        when:
        run("task1")

        then:
        outputContains("Create :task1")
        outputContains("Configure :task1")
        result.assertNotOutput(":task2")
        result.assertNotOutput(":task3")

        when:
        run("task2")

        then:
        outputContains("Create :task2")
        outputContains("Configure :task2")
        result.assertNotOutput(":task1")
        result.assertNotOutput(":task3")

        when:
        run("task3")

        then:
        outputContains("Create :task3")
        result.assertNotOutput(":task1")
        result.assertNotOutput(":task2")
    }

    def "task is created and configured when referenced as a task dependency"() {
        buildFile << '''
            tasks.register("task1", SomeTask) {
                println "Configure ${path}"
            }
            tasks.register("task2", SomeTask) {
                println "Configure ${path}"
                dependsOn task1
            }
            tasks.create("other")
        '''

        when:
        run("other")

        then:
        result.assertNotOutput("task1")
        result.assertNotOutput("task2")

        when:
        run("task2")

        then:
        outputContains("Create :task1")
        outputContains("Configure :task1")
        outputContains("Create :task2")
        outputContains("Configure :task2")
    }

    def "task is created and configured when referenced as task dependency via task provider"() {
        buildFile << '''
            def t1 = tasks.register("task1", SomeTask) {
                println "Configure ${path}"
            }
            tasks.create("task2", SomeTask) {
                println "Configure ${path}"
                dependsOn t1
            }
            tasks.create("other")
        '''

        when:
        run("other")

        then:
        result.assertNotOutput("task1")
        outputContains("Create :task2")
        outputContains("Configure :task2")

        when:
        run("task2")

        then:
        outputContains("Create :task1")
        outputContains("Configure :task1")
        outputContains("Create :task2")
        outputContains("Configure :task2")
    }

    def "task is created and configured when referenced during configuration"() {
        buildFile << '''
            tasks.register("task1", SomeTask) {
                println "Configure ${path}"
            }
            // Eager
            tasks.create("task2", SomeTask) {
                println "Configure ${path}"
                dependsOn task1
            }
            tasks.create("other")
        '''

        when:
        run("other")

        then:
        outputContains("Create :task1")
        outputContains("Configure :task1")
        outputContains("Create :task2")
        outputContains("Configure :task2")
    }

    def "task is created and configured eagerly when referenced using withType(type, action)"() {
        buildFile << '''
            tasks.register("task1", SomeTask) {
                println "Configure ${path}"
            }
            tasks.register("task2", SomeOtherTask) {
                println "Configure ${path}"
            }
            tasks.create("other")
            tasks.withType(SomeTask) {
                println "Matched ${path}"
            }
        '''

        when:
        run("other")

        then:
        outputContains("Create :task1")
        outputContains("Configure :task1")
        outputContains("Matched :task1")
        result.assertNotOutput("task2")
    }

    def "build logic can configure each task only when required"() {
        buildFile << '''
            tasks.register("task1", SomeTask).configure {
                println "Configure ${path}"
            }
            tasks.named("task1").configure {
                println "Configure again ${path}"
            }
            tasks.register("task2", SomeOtherTask) {
                println "Configure ${path}"
            }
            tasks.register("task3")
            tasks.configureEach {
                println "Received ${path}"
            }
            tasks.create("other") {
                dependsOn "task3"
            }
        '''

        when:
        run("other")

        then:
        outputContains("Received :other")
        outputContains("Received :task3")
        result.assertNotOutput("task1")
        result.assertNotOutput("task2")

        when:
        run("task1")

        then:
        outputContains("Received :other")
        outputContains("Create :task1")
        outputContains("Configure :task1")
        outputContains("Configure again :task1")
        outputContains("Received :task1")
        result.assertNotOutput("task2")
        result.assertNotOutput("task3")
    }

    @Issue("https://github.com/gradle/gradle-native/issues/707")
    def "task is created and configured eagerly when referenced using all { action }"() {
        buildFile << """
            def configureCount = 0
            tasks.register("task1", SomeTask) {
                configureCount++
                println "Configure \${path} " + configureCount
            }
            
            def tasksAllCount = 0
            tasks.all {
                tasksAllCount++
                println "Action " + path + " " + tasksAllCount
            }
            
            gradle.buildFinished {
                assert configureCount == 1
                assert tasksAllCount == 15 // built in tasks + task1
            }
        """

        expect:
        succeeds("help")
        result.output.count("Create :task1") == 1
        result.output.count("Configure :task1") == 1
        result.output.count("Action :task1") == 1
    }

    def "build logic can configure each task of a given type only when required"() {
        buildFile << '''
            tasks.register("task1", SomeTask).configure {
                println "Configure ${path}"
            }
            tasks.named("task1").configure {
                println "Configure again ${path}"
            }
            tasks.register("task2", SomeOtherTask) {
                println "Configure ${path}"
            }
            tasks.register("task3", SomeOtherTask)

            tasks.withType(SomeTask).configureEach {
                println "Received ${path}"
            }
            tasks.create("other") {
                dependsOn "task3"
            }
        '''

        when:
        run("other")

        then:
        result.assertNotOutput("Received :")
        result.assertNotOutput("task1")
        result.assertNotOutput("task2")

        when:
        run("task1")

        then:
        outputContains("Create :task1")
        outputContains("Configure :task1")
        outputContains("Configure again :task1")
        outputContains("Received :task1")
        result.assertNotOutput("task2")
    }

    def "reports failure in task constructor when task realized"() {
        settingsFile << """
            include "child"
        """
        file("child/build.gradle") << """
            class Broken extends DefaultTask {
                Broken() {
                    throw new RuntimeException("broken task")
                }
            }
            tasks.register("broken", Broken)
        """

        expect:
        fails("broken")
        failure.assertHasDescription("A problem occurred configuring project ':child'.")
        failure.assertHasCause("Could not create task ':child:broken'.")
        failure.assertHasCause("Could not create task of type 'Broken'.")
        failure.assertHasCause("broken task")
    }

    def "reports failure in task configuration block when task created"() {
        settingsFile << """
            include "child"
        """
        file("child/build.gradle") << """
            tasks.register("broken") {
                throw new RuntimeException("broken task")
            }
        """

        expect:
        fails("broken")
        failure.assertHasDescription("A problem occurred configuring project ':child'.")
        failure.assertHasCause("Could not create task ':child:broken'.")
        failure.assertHasCause("broken task")
    }

    def "reports failure in configure block when task created"() {
        settingsFile << """
            include "child"
        """
        file("child/build.gradle") << """
            tasks.configureEach {
                throw new RuntimeException("broken task")
            }
            tasks.register("broken")
        """

        expect:
        fails("broken")
        failure.assertHasDescription("A problem occurred configuring project ':child'.")
        failure.assertHasCause("Could not create task ':child:broken'.")
        failure.assertHasCause("broken task")
    }

    @Issue("https://github.com/gradle/gradle/issues/5148")
    def "can get a task by name with a filtered collection"() {
        buildFile << '''
            tasks.register("task1", SomeTask) {
                println "Configure ${path}"
            }
            
            tasks.create("other") {
                dependsOn tasks.withType(SomeTask).getByName("task1")
            }
        '''

        when:
        run "other"

        then:
        outputContains("Create :task1")
    }

    def "fails to get a task by name when it does not match the filtered type"() {
        buildFile << '''
            tasks.register("task1", SomeTask) {
                println "Configure ${path}"
            }
            
            tasks.create("other") {
                dependsOn tasks.withType(SomeOtherTask).getByName("task1")
            }
        '''

        when:
        fails "other"

        then:
        outputDoesNotContain("Create :task1")
        outputDoesNotContain("Configure :task1")
        failure.assertHasCause("Task with name 'task1' not found")
    }

    def "fails to get a task by name when it does not match the collection filter"() {
        buildFile << '''
            tasks.register("task1", SomeTask) {
                println "Configure ${path}"
            }
            
            tasks.create("other") {
                dependsOn tasks.matching { it.name.contains("foo") }.getByName("task1")
            }
        '''

        when:
        fails "other"

        then:
        outputContains("Create :task1")
        outputContains("Configure :task1")
        failure.assertHasCause("Task with name 'task1' not found")
    }

    @Issue("https://github.com/gradle/gradle-native/issues/661")
    def "executes each configuration actions once when realizing a task"() {
        buildFile << '''
            def actionExecutionCount = [:].withDefault { 0 }

            class A extends DefaultTask {}

            tasks.withType(A).configureEach {
                actionExecutionCount.a1++
            }

            tasks.withType(A).configureEach {
                actionExecutionCount.a2++
            }

            def a = tasks.register("a", A) {
                actionExecutionCount.a3++
            }

            a.configure {
                actionExecutionCount.a4++
            }

            tasks.withType(A).configureEach {
                actionExecutionCount.a5++
            }

            a.configure {
                actionExecutionCount.a6++
            }

            task assertActionExecutionCount {
                dependsOn a
                doLast {
                    assert actionExecutionCount.size() == 6
                    assert actionExecutionCount.values().every { it == 1 }
                }
            }
        '''

        expect:
        succeeds 'assertActionExecutionCount'
    }

    @Issue("https://github.com/gradle/gradle-native/issues/662")
    def "runs the lazy configuration actions in the same order as the eager configuration actions"() {
        buildFile << '''
            def actionExecutionOrderForTaskA = []

            class A extends DefaultTask {}

            tasks.withType(A).configureEach {
                actionExecutionOrderForTaskA << "1"
            }

            tasks.withType(A).configureEach {
                actionExecutionOrderForTaskA << "2"
            }

            def a = tasks.register("a", A) {
                actionExecutionOrderForTaskA << "3"
            }

            a.configure {
                actionExecutionOrderForTaskA << "4"
            }

            tasks.withType(A).configureEach {
                actionExecutionOrderForTaskA << "5"
            }

            a.configure {
                actionExecutionOrderForTaskA << "6"
            }

            def actionExecutionOrderForTaskB = []

            class B extends DefaultTask {}

            tasks.withType(B) {
                actionExecutionOrderForTaskB << "1"
            }

            tasks.withType(B) {
                actionExecutionOrderForTaskB << "2"
            }

            def b = tasks.create("b", B) {
                actionExecutionOrderForTaskB << "3"
            }

            b.configure {
                actionExecutionOrderForTaskB << "4"
            }

            tasks.withType(B) {
                actionExecutionOrderForTaskB << "5"
            }

            b.configure {
                actionExecutionOrderForTaskB << "6"
            }

            task assertActionExecutionOrder {
                dependsOn a, b
                doLast {
                    assert actionExecutionOrderForTaskA.size() == 6
                    assert actionExecutionOrderForTaskA == actionExecutionOrderForTaskB
                }
            }
        '''

        expect:
        succeeds 'assertActionExecutionOrder'
    }

    def "can overwrite a lazy task creation with a eager task of the same type executing all lazy rules"() {
        buildFile << '''
            class MyTask extends DefaultTask {}
            def myTask = tasks.register("myTask", SomeTask) {
                println "Lazy 1 ${path}"
            }
            myTask.configure {
                println "Lazy 2 ${path}"
            }

            tasks.create(name: "myTask", type: SomeTask, overwrite: true) {
               println "Configure ${path}"
            }
        '''

        expect:
        succeeds "help"

        result.output.count("Create :myTask") == 1
        result.output.count("Lazy 1 :myTask") == 1
        result.output.count("Lazy 2 :myTask") == 1
        result.output.count("Configure :myTask") == 1
    }

    def "can overwrite a lazy task creation with a eager task with subtype executing all lazy rules"() {
        buildFile << '''
            class MyTask extends DefaultTask {}
            def myTask = tasks.register("myTask", SomeTask) {
                println "Lazy 1 ${path}"
            }
            myTask.configure {
                println "Lazy 2 ${path}"
            }

            tasks.create(name: "myTask", type: SomeSubTask, overwrite: true) {
               println "Configure ${path}"
            }
        '''

        expect:
        succeeds "help"

        result.output.count("Create subtask :myTask") == 1
        result.output.count("Lazy 1 :myTask") == 1
        result.output.count("Lazy 2 :myTask") == 1
        result.output.count("Configure :myTask") == 1
    }

    def "cannot overwrite a lazy task creation with a eager task creation with a different type"() {
        buildFile << '''
            class MyTask extends DefaultTask {}
            def myTask = tasks.register("myTask", SomeTask) {
                assert false, "This task is overwritten before been realized"
            }
            myTask.configure {
                assert false, "This task is overwritten before been realized"
            }

            tasks.create(name: "myTask", type: SomeOtherTask, overwrite: true) {
               println "Configure ${path}"
            }
        '''

        expect:
        fails "help"

        and:
        failure.assertHasCause("Replacing an existing task with an incompatible type is not supported.  Use a different name for this task ('myTask') or use a compatible type (SomeOtherTask)")
    }

    def "cannot overwrite a lazy task creation with a eager task creation after the lazy task has been realized"() {
        buildFile << '''
            class MyTask extends DefaultTask {}
            def myTask = tasks.register("myTask", SomeTask).get()

            tasks.create(name: "myTask", type: SomeOtherTask, overwrite: true) {
               println "Configure ${path}"
            }
        '''

        expect:
        fails "help"

        and:
        failure.assertHasCause("Replacing an existing task that may have already been used by other plugins is not supported.  Use a different name for this task ('myTask').")
    }

    def "executes configureEach rule only for eager overwritten task"() {
        buildFile << '''
            class MyTask extends DefaultTask {}
            def configureEachRuleExecutionCount = 0
            tasks.withType(SomeTask).configureEach {
                configureEachRuleExecutionCount++
            }

            def myTask = tasks.register("myTask", SomeTask)
            
            tasks.create(name: "myTask", type: SomeTask, overwrite: true) {
               println "Configure ${path}"
            }

            assert configureEachRuleExecutionCount == 1, "The configureEach rule should execute only for the overwritten eager task"
        '''

        expect:
        succeeds "help"

        result.output.count("Create :myTask") == 1
        result.output.count("Configure :myTask") == 1
    }

    def "can construct a custom task with constructor arguments"() {
        given:
        buildFile << CUSTOM_TASK_WITH_CONSTRUCTOR_ARGS
        buildFile << "tasks.register('myTask', CustomTask, 'hello', 42)"

        when:
        run 'myTask'

        then:
        outputContains("hello 42")
    }

    def "fails to create custom task if constructor arguments are missing"() {
        given:
        buildFile << CUSTOM_TASK_WITH_CONSTRUCTOR_ARGS
        buildFile << "tasks.register('myTask', CustomTask, 'hello')"

        when:
        fails 'myTask'

        then:
        failure.assertHasCause("Could not create task ':myTask'.")
        failure.assertHasCause("Could not create task of type 'CustomTask'.")
        failure.assertHasCause("Unable to determine constructor argument #2: missing parameter of type int, or no service of type int")
    }

    def "fails to create custom task if all constructor arguments missing"() {
        given:
        buildFile << CUSTOM_TASK_WITH_CONSTRUCTOR_ARGS
        buildFile << "tasks.register('myTask', CustomTask)"

        when:
        fails 'myTask'

        then:
        failure.assertHasCause("Could not create task ':myTask'.")
        failure.assertHasCause("Could not create task of type 'CustomTask'.")
        failure.assertHasCause("Unable to determine constructor argument #1: missing parameter of type String, or no service of type String")
    }

    @Unroll
    def "fails when #description constructor argument is wrong type"() {
        given:
        buildFile << CUSTOM_TASK_WITH_CONSTRUCTOR_ARGS
        buildFile << "tasks.register('myTask', CustomTask, $constructorArgs)"

        when:
        fails 'myTask'

        then:
        failure.assertHasCause("Could not create task ':myTask'.")
        failure.assertHasCause("Could not create task of type 'CustomTask'.")

        where:
        description | constructorArgs | argumentNumber | outputType
        'first'     | '123, 234'      | 1              | 'class java.lang.String'
        'last'      | '"abc", "123"'  | 2              | 'int'
    }

    @Unroll
    def "fails to create when null passed as a constructor argument value at #position"() {
        given:
        buildFile << CUSTOM_TASK_WITH_CONSTRUCTOR_ARGS
        buildFile << script

        when:
        fails 'myTask'

        then:
        failure.assertHasCause("Could not create task ':myTask'.")

        where:
        position | script
        1        | "tasks.register('myTask', CustomTask, null, 1)"
        2        | "tasks.register('myTask', CustomTask, 'abc', null)"
    }

    def "can construct a task with @Inject services"() {
        given:
        buildFile << """
            import org.gradle.workers.WorkerExecutor
            import javax.inject.Inject

            class CustomTask extends DefaultTask {
                private final WorkerExecutor executor

                @Inject
                CustomTask(WorkerExecutor executor) {
                    this.executor = executor
                }

                @TaskAction
                void printIt() {
                    println(executor != null ? "got it" : "NOT IT")
                }
            }

            tasks.register('myTask', CustomTask)
        """

        when:
        run 'myTask'

        then:
        outputContains("got it")
    }

    def "can construct a task with @Inject services and constructor args"() {
        given:
        buildFile << """
            import org.gradle.workers.WorkerExecutor
            import javax.inject.Inject

            class CustomTask extends DefaultTask {
                private final int number
                private final WorkerExecutor executor

                @Inject
                CustomTask(int number, WorkerExecutor executor) {
                    this.number = number
                    this.executor = executor
                }

                @TaskAction
                void printIt() {
                    println(executor != null ? "got it \$number" : "\$number NOT IT")
                }
            }

            tasks.register('myTask', CustomTask, 15)
        """

        when:
        run 'myTask'

        then:
        outputContains("got it 15")
    }

    def "realizes only the task of the given type when depending on a filtered task collection"() {
        buildFile << '''
            def defaultTaskRealizedCount = 0
            (1..100).each {
                tasks.register("aDefaultTask_$it") {
                    defaultTaskRealizedCount++
                }
            }
            def zipTaskRealizedCount = 0
            tasks.register("aZipTask", Zip) {
                destinationDirectory = buildDir
                zipTaskRealizedCount++
            }

            task foo {
                dependsOn tasks.withType(Zip)
                doLast {
                    assert defaultTaskRealizedCount == 0, "All DefaultTask shouldn't be realized"
                    assert zipTaskRealizedCount == 1, "All Zip task should be realized"
                }
            }
        '''

        expect:
        succeeds "foo"
    }

    def "realizes only the task of the given type when verifying if a filtered task collection is empty"() {
        buildFile << '''
            def defaultTaskRealizedCount = 0
            (1..100).each {
                tasks.register("aDefaultTask_$it") {
                    defaultTaskRealizedCount++
                }
            }
            def zipTaskRealizedCount = 0
            tasks.register("aZipTask", Zip) {
                zipTaskRealizedCount++
            }

            task foo {
                def hasZipTask = tasks.withType(Zip).empty
                doLast {
                    assert defaultTaskRealizedCount == 0, "All DefaultTask shouldn't be realized"
                    assert zipTaskRealizedCount == 1, "All Zip task should be realized"
                }
            }
        '''

        expect:
        succeeds "foo"
    }

    private static final def INVALID_CALL_FROM_LAZY_CONFIGURATION = [
        ["Project#afterEvaluate(Closure)"   , "afterEvaluate {}"],
        ["Project#afterEvaluate(Action)"    , "afterEvaluate new Action<Project>() { void execute(Project p) {} }"],
        ["Project#beforeEvaluate(Closure)"  , "beforeEvaluate {}"],
        ["Project#beforeEvaluate(Action)"   , "beforeEvaluate new Action<Project>() { void execute(Project p) {} }"],
        ["Gradle#beforeProject(Closure)"    , "gradle.beforeProject {}"],
        ["Gradle#beforeProject(Action)"     , "gradle.beforeProject new Action<Project>() { void execute(Project p) {} }"],
        ["Gradle#afterProject(Closure)"     , "gradle.afterProject {}"],
        ["Gradle#afterProject(Action)"      , "gradle.afterProject new Action<Project>() { void execute(Project p) {} }"],
        ["Gradle#projectsLoaded(Closure)"   , "gradle.projectsLoaded {}"],
        ["Gradle#projectsLoaded(Action)"    , "gradle.projectsLoaded new Action<Gradle>() { void execute(Gradle g) {} }"],
        ["Gradle#projectsEvaluated(Closure)", "gradle.projectsEvaluated {}"],
        ["Gradle#projectsEvaluated(Action)" , "gradle.projectsEvaluated new Action<Gradle>() { void execute(Gradle g) {} }"]
    ]

    String mutationExceptionFor(description) {
        def target
        if (description.startsWith("Project")) {
            target = "root project 'root'"
        } else if (description.startsWith("Gradle")) {
            target = "build 'root'"
        } else {
            throw new IllegalArgumentException("Can't determine the exception text for '${description}'")
        }

        return "$description on ${target} cannot be executed in the current context."
    }

    @Unroll
    def "cannot execute #description during lazy task creation action execution"() {
        settingsFile << "include 'nested'"
        buildFile << """
            tasks.register("foo") {
                ${code}
            }
        """

        expect:
        fails "foo"
        failure.assertHasCause("Could not create task ':foo'.")
        failure.assertHasCause(mutationExceptionFor(description))

        where:
        [description, code] << INVALID_CALL_FROM_LAZY_CONFIGURATION
    }

    @Unroll
    def "can execute #description during task creation action execution"() {
        settingsFile << "include 'nested'"
        buildFile << """
            tasks.create("foo") {
                ${code}
            }
        """

        expect:
        succeeds "foo"

        where:
        [description, code] << INVALID_CALL_FROM_LAZY_CONFIGURATION
    }

    @Unroll
    def "cannot execute #description during lazy task configuration action execution"() {
        settingsFile << "include 'nested'"
        buildFile << """
            tasks.register("foo").configure {
                ${code}
            }
        """

        expect:
        fails "foo"
        failure.assertHasCause("Could not create task ':foo'.")
        failure.assertHasCause(mutationExceptionFor(description))

        where:
        [description, code] << INVALID_CALL_FROM_LAZY_CONFIGURATION
    }

    @Unroll
    def "can execute #description during task configuration action execution"() {
        settingsFile << "include 'nested'"
        buildFile << """
            tasks.create("foo")
            tasks.getByName("foo") {
                ${code}
            }
        """

        expect:
        succeeds "foo"

        where:
        [description, code] << INVALID_CALL_FROM_LAZY_CONFIGURATION
    }

    @Unroll
    def "cannot execute #description on another project during lazy task creation action execution"() {
        settingsFile << "include 'nested', 'other'"
        buildFile << """
            project(":other") {
                tasks.register("foo") {
                    rootProject.${code}
                }
            }
        """

        expect:
        fails "foo"
        failure.assertHasCause("Could not create task ':other:foo'.")
        failure.assertHasCause(mutationExceptionFor(description))

        where:
        [description, code] << INVALID_CALL_FROM_LAZY_CONFIGURATION
    }

    @Unroll
    def "can execute #description on another project during task creation action execution"() {
        settingsFile << "include 'nested', 'other'"
        buildFile << """
            project(":other") {
                tasks.create("foo") {
                    rootProject.${code}
                }
            }
        """

        expect:
        succeeds "foo"

        where:
        [description, code] << INVALID_CALL_FROM_LAZY_CONFIGURATION
    }

    @Unroll
    def "cannot execute #description on another project during lazy task configuration action execution"() {
        settingsFile << "include 'nested', 'other'"
        buildFile << """
            project(":other") {
                tasks.register("foo").configure {
                    rootProject.${code}
                }
            }
        """

        expect:
        fails "foo"
        failure.assertHasCause("Could not create task ':other:foo'.")
        failure.assertHasCause(mutationExceptionFor(description))

        where:
        [description, code] << INVALID_CALL_FROM_LAZY_CONFIGURATION
    }

    @Unroll
    def "can execute #description on another project during task configuration action execution"() {
        settingsFile << "include 'nested', 'other'"
        buildFile << """
            project(":other") {
                tasks.create("foo")
                tasks.getByName("foo") {
                    rootProject.${code}
                }
            }
        """

        expect:
        succeeds "foo"

        where:
        [description, code] << INVALID_CALL_FROM_LAZY_CONFIGURATION
    }

    @Unroll
    def "can execute #description during eager configuration action with registered task"() {
        buildFile << """
            tasks.withType(SomeTask) {
                ${code}
            }
            tasks.register("foo", SomeTask)
        """

        expect:
        succeeds "foo"

        where:
        [description, code] << INVALID_CALL_FROM_LAZY_CONFIGURATION
    }

    def "can realize a task provider inside a configureEach action"() {
        buildFile << """
            def foo = tasks.create("foo", SomeTask)
            def bar = tasks.register("bar") { println "Create :bar" }
            def baz = tasks.create("baz", SomeTask)
            def fizz = tasks.create("fizz", SomeTask)
            def fuzz = tasks.create("fuzz", SomeTask)
           
            tasks.withType(SomeTask).configureEach { task ->
                println "Configuring " + task.name
                bar.get()
            }
            
            task some { dependsOn tasks.withType(SomeTask) }
        """

        expect:
        succeeds("some")

        and:
        executed ":foo", ":baz", ":fizz", ":fuzz", ":some"
    }

    def "can lookup task created by rules"() {
        buildFile << """
            tasks.addRule("create some tasks") { taskName ->
                if (taskName == "bar") {
                    tasks.register("bar")
                } else if (taskName == "baz") {
                    tasks.create("baz")
                } else if (taskName == "notByRule") {
                    tasks.register("notByRule") {
                        throw new Exception("This should not be called")
                    }
                }
            }
            tasks.register("notByRule")
            
            task foo {
                dependsOn tasks.named("bar")
                dependsOn tasks.named("baz")
                dependsOn "notByRule"
            }
            
        """
        expect:
        succeeds("foo")
        result.assertTasksExecuted(":notByRule", ":bar", ":baz", ":foo")
    }

    @Issue("https://github.com/gradle/gradle/issues/6319")
    def "can use getTasksByName from a lazy configuration action"() {
        settingsFile << """
            include "sub"
        """
        buildFile << """
            plugins {
                id 'base'
            }
            tasks.whenTaskAdded {
                // force realization of all tasks
            }
            tasks.register("foo") {
                dependsOn(project.getTasksByName("clean", true))
            }
        """
        file("sub/build.gradle") << """
            plugins {
                id 'base'
            }
            afterEvaluate {
                tasks.register("foo")
            }
        """
        expect:
        succeeds("help")
    }

    @Ignore
    @Issue("https://github.com/gradle/gradle/issues/6558")
    def "can register tasks in multi-project build that iterates over allprojects and tasks in task action"() {
        settingsFile << """
            include 'a', 'b', 'c', 'd'
        """
        buildFile << """
            class MyTask extends DefaultTask {
                @TaskAction
                void doIt() {
                    for (Project subproject : project.rootProject.getAllprojects()) {
                        for (MyTask myTask : subproject.tasks.withType(MyTask)) {
                            println "Looking at " + myTask.path
                        }
                    }
                }
            }
            
            allprojects {
                (1..10).each {
                    def mytask = tasks.register("mytask" + it, MyTask)
                }
            }
        """
        expect:
        succeeds("mytask0", "--parallel")
    }

    @Issue("https://github.com/gradle/gradle-native/issues/814")
    def "can locate task by name and type with named"() {
        buildFile << """
            class CustomTask extends DefaultTask {
                @Internal
                String message
                @Internal
                int number
                
                @TaskAction
                void print() {
                    println message + " " + number
                }
            }
            task foo(type: CustomTask)
            tasks.register("bar", CustomTask)
            
            tasks.named("foo", CustomTask).configure {
                message = "foo named(String, Class)"
            }
            tasks.named("foo", CustomTask) {
                number = 100
            }
            tasks.named("foo") {
                number = number * 2
            }
            tasks.named("bar", CustomTask) {
                message = "bar named(String, Class, Action)"
            }
            tasks.named("bar", CustomTask).configure {
                number = 12345
            }
        """
        expect:
        succeeds("foo", "bar")
        outputContains("foo named(String, Class) 200")
        outputContains("bar named(String, Class, Action) 12345")
    }

    @Unroll
    def "gets useful message when using improper type for named using #api"() {
        buildFile << """
            class CustomTask extends DefaultTask {
            }
            class AnotherTask extends DefaultTask {
            }

            tasks.${api}("foo", CustomTask)
            
            tasks.named("foo", AnotherTask) // should fail
        """
        expect:
        fails("help")
        failure.assertHasCause("The task 'foo' (CustomTask) is not a subclass of the given type (AnotherTask).")

        where:
        api << ["create", "register"]
    }
}
