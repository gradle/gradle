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

import org.gradle.api.internal.AbstractTask
import org.gradle.api.internal.TaskInternal
import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class TaskDefinitionIntegrationTest extends AbstractIntegrationSpec {
    private static final String CUSTOM_TASK_WITH_CONSTRUCTOR_ARGS = """
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

    def "can define tasks using task keyword and identifier"() {
        buildFile << """
            task nothing
            task withAction { doLast {} }
            task emptyOptions()
            task task
            task withOptions(dependsOn: [nothing, withAction, emptyOptions, task])
            task withOptionsAndAction(dependsOn: withOptions) { doLast {} }
        """

        expect:
        succeeds ":emptyOptions", ":nothing", ":task", ":withAction", ":withOptions", ":withOptionsAndAction"
    }

    def "can define tasks using task keyword and GString"() {
        buildFile << """
            ext.v = 'Task'
            task "nothing\$v"
            task "withAction\$v" { doLast {} }
            task "emptyOptions\$v"()
            task "withOptions\$v"(dependsOn: [nothingTask, withActionTask, emptyOptionsTask])
            task "withOptionsAndAction\$v"(dependsOn: withOptionsTask) { doLast {} }
        """

        expect:
        succeeds ":emptyOptionsTask", ":nothingTask", ":withActionTask", ":withOptionsTask", ":withOptionsAndActionTask"
    }

    def "can define tasks using task keyword and String"() {
        buildFile << """
            task 'nothing'
            task 'withAction' { doLast {} }
            task 'emptyOptions'()
            task 'withOptions'(dependsOn: [nothing, withAction, emptyOptions])
            task 'withOptionsAndAction'(dependsOn: withOptions) { doLast {} }
        """

        expect:
        succeeds ":emptyOptions", ":nothing", ":withAction", ":withOptions", ":withOptionsAndAction"
    }

    def "can define tasks in nested blocks"() {
        buildFile << """
            2.times { task "dynamic\$it" }
            if (dynamic0) { task inBlock }
            def task() { task inMethod }
            task()
            def cl = { -> task inClosure }
            cl()
            task all(dependsOn: [dynamic0, dynamic1, inBlock, inMethod, inClosure])
        """

        expect:
        succeeds ":dynamic0", ":dynamic1", ":inBlock", ":inClosure", ":inMethod", ":all"
    }

    def "can define tasks using task method expression"() {
        buildFile << """
            ext.a = 'a' == 'b' ? null: task(withAction) { doLast {} }
            a = task(nothing)
            a = task(emptyOptions())
            ext.taskName = 'dynamic'
            a = task("\$taskName") { doLast {} }
            a = task('string')
            a = task('stringWithAction') { doLast {} }
            a = task('stringWithOptions', description: 'description')
            a = task('stringWithOptionsAndAction', description: 'description') { doLast {} }
            a = task(withOptions, description: 'description')
            a = task(withOptionsAndAction, description: 'description') { doLast {} }
            a = task(anotherWithAction).doFirst\n{}
            task all(dependsOn: [":anotherWithAction", ":dynamic", ":emptyOptions",
            ":nothing", ":string", ":stringWithAction", ":stringWithOptions", ":stringWithOptionsAndAction",
            ":withAction", ":withOptions", ":withOptionsAndAction"])
        """

        expect:
        succeeds ":anotherWithAction", ":dynamic", ":emptyOptions",
            ":nothing", ":string", ":stringWithAction", ":stringWithOptions", ":stringWithOptionsAndAction",
            ":withAction", ":withOptions", ":withOptionsAndAction", ":all"
    }

    def "can configure tasks when they are defined"() {
        buildFile << """
            task withDescription { description = 'value' }
            task(asMethod)\n{ description = 'value' }
            task asStatement(type: TestTask) { property = 'value' }
            task "dynamic"(type: TestTask) { property = 'value' }
            ext.v = task(asExpression, type: TestTask) { property = 'value' }
            task(postConfigure, type: TestTask).configure { property = 'value' }
            [asStatement, dynamic, asExpression, postConfigure].each {
                assert 'value' == it.property
            }
            [withDescription, asMethod].each {
                assert 'value' == it.description
            }
            task all(dependsOn: ["withDescription", "asMethod", "asStatement", "dynamic", "asExpression", "postConfigure"])
            class TestTask extends DefaultTask { @Input String property }
        """

        expect:
        succeeds "all"
    }

    def "can define task using type Task"() {
        buildFile << """
            task thing(type: Task) { t ->
                assert t instanceof DefaultTask
                doFirst { println("thing") }
            }
        """

        expect:
        succeeds("thing")
    }

    def "creating a task of type AbstractTask is not supported"() {
        buildFile << """
            task thing(type: ${AbstractTask.name}) { t ->
                assert t instanceof DefaultTask
                doFirst { println("thing") }
            }
        """

        expect:
        fails("thing")
        failureHasCause("Cannot create task ':thing' of type 'AbstractTask' as this type is not supported for task registration.")
    }

    def "creating a task of type TaskInternal is not supported"() {
        buildFile << """
            task thing(type: ${TaskInternal.name}) { t ->
                assert t instanceof DefaultTask
                doFirst { println("thing") }
            }
        """

        expect:
        fails("thing")
        failureHasCause("Cannot create task ':thing' of type 'TaskInternal' as this type is not supported for task registration.")
    }

    def "creating a task that is a subtype of AbstractTask is not supported"() {
        buildFile << """
            class CustomTask extends ${AbstractTask.name} {
            }
            task thing(type: CustomTask) { t ->
                doFirst { println("thing") }
            }
        """

        expect:
        fails("thing")
        failureHasCause("Cannot create task ':thing' of type 'CustomTask' as directly extending AbstractTask is not supported.")
    }

    def "does not hide local methods and variables"() {
        buildFile << """
            String name = 'a'; task name
            def taskNameMethod(String name = 'c') { name }
            task taskNameMethod('d')
            def addTaskMethod(String methodParam) { task methodParam }
            addTaskMethod('e')
            def addTaskWithClosure(String methodParam) { task(methodParam) { ext.property = 'value' } }
            addTaskWithClosure('f')
            def addTaskWithMap(String methodParam) { task(methodParam, description: 'description') }
            addTaskWithMap('g')
            ext.cl = { String taskNameParam -> task taskNameParam }
            cl.call('h')
            cl = { String taskNameParam -> task(taskNameParam) { ext.property = 'value' } }
            cl.call('i')
            assert 'value' == f.property
            assert 'value' == i.property
            task all(dependsOn: [":a", ":d", ":e", ":f", ":g", ":h", ":i"])
        """

        expect:
        succeeds ":a", ":d", ":e", ":f", ":g", ":h", ":i", ":all"
    }

    def "reports failure in task constructor when task created"() {
        settingsFile << """
            include "child"
        """
        file("child/build.gradle") << """
            class Broken extends DefaultTask {
                Broken() {
                    throw new RuntimeException("broken task")
                }
            }
            tasks.create("broken", Broken)
        """

        expect:
        fails()
        failure.assertHasLineNumber(4)
        failure.assertHasDescription("A problem occurred evaluating project ':child'.")
        failure.assertHasCause("Could not create task ':child:broken'.")
        failure.assertHasCause("Could not create task of type 'Broken'.")
        failure.assertHasCause("broken task")
    }

    def "reports failure in task configuration block when task created"() {
        settingsFile << """
            include "child"
        """
        file("child/build.gradle") << """
            tasks.create("broken") {
                throw new RuntimeException("broken task")
            }
        """

        expect:
        fails()
        failure.assertHasLineNumber(3)
        failure.assertHasDescription("A problem occurred evaluating project ':child'.")
        failure.assertHasCause("Could not create task ':child:broken'.")
        failure.assertHasCause("broken task")
    }

    def "reports failure in all block when task created"() {
        settingsFile << """
            include "child"
        """
        file("child/build.gradle") << """
            tasks.configureEach {
                throw new RuntimeException("broken task")
            }
            tasks.create("broken")
        """

        expect:
        fails()
        failure.assertHasLineNumber(3)
        failure.assertHasDescription("A problem occurred evaluating project ':child'.")
        failure.assertHasCause("Could not create task ':child:broken'.")
        failure.assertHasCause("broken task")
    }

    def "reports failure in task constructor when task replaced"() {
        settingsFile << """
            include "child"
        """
        file("child/build.gradle") << """
            class Broken extends DefaultTask {
                Broken() {
                    throw new RuntimeException("broken task")
                }
            }
            tasks.create("broken")
            tasks.replace("broken", Broken)
        """

        expect:
        fails()
        failure.assertHasLineNumber(4)
        failure.assertHasDescription("A problem occurred evaluating project ':child'.")
        failure.assertHasCause("Could not create task ':child:broken'.")
        failure.assertHasCause("Could not create task of type 'Broken'.")
        failure.assertHasCause("broken task")
    }

    def "unsupported task parameter fails with decent error message"() {
        buildFile << "task a(Type:Copy)"
        when:
        fails 'a'
        then:
        failure.assertHasCause("Could not create task 'a': Unknown argument(s) in task definition: [Type]")
    }

    def "can construct a custom task without constructor arguments"() {
        given:
        buildFile << """
            class CustomTask extends DefaultTask {
                @TaskAction
                void printIt() {
                    println("hello world")
                }
            }

            task myTask(type: CustomTask)
        """

        when:
        run 'myTask'

        then:
        outputContains('hello world')
    }

    def "can construct a custom task with constructor arguments"() {
        given:
        buildFile << CUSTOM_TASK_WITH_CONSTRUCTOR_ARGS
        buildFile << "tasks.create('myTask', CustomTask, 'hello', 42)"

        when:
        run 'myTask'

        then:
        outputContains("hello 42")
    }

    def "can construct a custom task with constructor arguments as #description via Map"() {
        given:
        buildFile << CUSTOM_TASK_WITH_CONSTRUCTOR_ARGS
        buildFile << "task myTask(type: CustomTask, constructorArgs: ${constructorArgs})"

        when:
        run 'myTask'

        then:
        outputContains("hello 42")

        where:
        description | constructorArgs
        'List'      | "['hello', 42]"
        'Object[]'  | "(['hello', 42] as Object[])"
    }

    def "task constructor can use identity properties of task"() {
        given:
        buildFile << """
            class LoggingTask extends DefaultTask {
                LoggingTask() {
                    println("name = " + name)
                    println("path = " + path)
                    println("toString() = " + this)
                }
            }

            task one(type: LoggingTask)
        """

        when:
        run("one")

        then:
        outputContains("name = one")
        outputContains("path = :one")
        outputContains("toString() = task ':one'")
    }

    def "fails to create custom task using #description if constructor arguments are missing"() {
        given:
        buildFile << CUSTOM_TASK_WITH_CONSTRUCTOR_ARGS
        buildFile << script

        when:
        fails 'myTask'

        then:
        failure.assertHasCause("Could not create task ':myTask'.")
        failure.assertHasCause("Could not create task of type 'CustomTask'.")
        failure.assertHasCause("Unable to determine constructor argument #2: missing parameter of type int, or no service of type int")

        where:
        description   | script
        'Map'         | "task myTask(type: CustomTask, constructorArgs: ['hello'])"
        'direct call' | "tasks.create('myTask', CustomTask, 'hello')"
    }

    def "fails to create custom task using #description if all constructor arguments missing"() {
        given:
        buildFile << CUSTOM_TASK_WITH_CONSTRUCTOR_ARGS
        buildFile << script

        when:
        fails 'myTask'

        then:
        failure.assertHasCause("Could not create task ':myTask'.")
        failure.assertHasCause("Could not create task of type 'CustomTask'.")
        failure.assertHasCause("Unable to determine constructor argument #1: missing parameter of type String, or no service of type String")

        where:
        description   | script
        'Map'         | "task myTask(type: CustomTask)"
        'Map (null)'  | "task myTask(type: CustomTask, constructorArgs: null)"
        'direct call' | "tasks.create('myTask', CustomTask)"
    }

    def "fails when constructorArgs not list or Object[], but #description"() {
        given:
        buildFile << CUSTOM_TASK_WITH_CONSTRUCTOR_ARGS
        buildFile << "task myTask(type: CustomTask, constructorArgs: ${constructorArgs})"

        when:
        fails 'myTask'

        then:
        failure.assertHasCause("Could not create task ':myTask'.")
        failure.assertHasCause("constructorArgs must be a List or Object[]")

        where:
        description | constructorArgs
        'Set'       | '[1, 2, 1] as Set'
        'Map'       | '[a: 1, b: 2]'
        'String'    | '"abc"'
        'primitive' | '123'
    }

    def "fails when #description constructor argument is wrong type"() {
        given:
        buildFile << CUSTOM_TASK_WITH_CONSTRUCTOR_ARGS
        buildFile << "tasks.create('myTask', CustomTask, $constructorArgs)"

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

    def "fails to create via #description when null passed as a constructor argument value at #position"() {
        given:
        buildFile << CUSTOM_TASK_WITH_CONSTRUCTOR_ARGS
        buildFile << script

        when:
        fails 'myTask'

        then:
        failure.assertHasCause("Could not create task ':myTask'.")
        failure.assertHasCause("Received null for CustomTask constructor argument #$position")

        where:
        description   | position | script
        'Map'         | 1        | "task myTask(type: CustomTask, constructorArgs: [null, 1])"
        'direct call' | 1        | "tasks.create('myTask', CustomTask, null, 1)"
        'Map'         | 2        | "task myTask(type: CustomTask, constructorArgs: ['abc', null])"
        'direct call' | 2        | "tasks.create('myTask', CustomTask, 'abc', null)"
    }

    def "reports failure when non-static inner class used"() {
        given:
        buildFile << """
            class MyPlugin implements Plugin<Project> {
                class MyTask extends DefaultTask {
                }

                void apply(Project p) {
                    p.tasks.register("myTask", MyTask)
                }
            }

            apply plugin: MyPlugin
        """

        when:
        fails 'myTask'

        then:
        failure.assertHasCause("Could not create task ':myTask'.")
        failure.assertHasCause("Could not create task of type 'MyTask'.")
        failure.assertHasCause("Class MyPlugin.MyTask is a non-static inner class.")
    }

    def 'can run custom task with constructor arguments via Kotlin friendly DSL'() {
        given:
        settingsFile << "rootProject.buildFileName = 'build.gradle.kts'"
        file("build.gradle.kts") << """
            import org.gradle.api.*
            import org.gradle.api.tasks.*

            open class CustomTask @Inject constructor(private val message: String, private val number: Int) : DefaultTask() {
                @TaskAction fun run() = println("\$message \$number")
            }

            tasks.create<CustomTask>("myTask", "hello", 42)
        """

        when:
        run 'myTask'

        then:
        outputContains('hello 42')
    }

    def "throws exception when adding a pre-created task to the task container"() {
        given:
        buildFile << """
            Task foo = tasks.create("foo")

            tasks.add(new Bar("bar", foo))

            class Bar implements Task {
                String name

                @Delegate
                Task delegate

                Bar(String name, Task delegate) {
                    this.name = name
                    this.delegate = delegate
                }

                String getName() {
                    return name
                }
            }
        """

        when:
        fails("help")

        then:
        failure.assertHasCause("Adding a task directly to the task container is not supported.  Use register() instead.")
    }

    def "cannot add a pre-created task provider to the task container"() {
        given:
        buildFile << """
            Task foo = tasks.create("foo")

            tasks.addLater(provider { foo })
        """

        when:
        fails("help")

        then:
        failure.assertHasCause("Adding a task provider directly to the task container is not supported.")
    }

    def "can extract schema from task container"() {
        given:
        buildFile << """
            class Foo extends DefaultTask {}
            tasks.create("foo", Foo)
            tasks.register("bar", Foo) {
                assert false : "This should not be realized"
            }
            tasks.create("builtInTask", Copy)
            tasks.register("defaultTask") {
                assert false : "This should not be realized"
            }

            def schema = tasks.collectionSchema.elements.collectEntries { e ->
                [ e.name, e.publicType.simpleName ]
            }
            
            // check some built-in tasks
            assert schema["help"] == "Help"
            assert schema["projects"] == "ProjectReportTask"
            assert schema["tasks"] == "TaskReportTask"
            assert schema["properties"] == "PropertyReportTask"

            // check some tasks from the project itself
            assert schema["foo"] == "Foo"
            assert schema["bar"] == "Foo"
            assert schema["builtInTask"] == "Copy"
            assert schema["defaultTask"] == "DefaultTask"
        """
        expect:
        succeeds("help")
    }

    def "cannot add a pre-created provider of tasks to the task container"() {
        given:
        buildFile << """
            Task foo = tasks.create("foo")
            Task bar = tasks.create("bar")

            tasks.addAllLater(provider { [foo, bar] })
        """

        when:
        fails("help")

        then:
        failure.assertHasCause("Adding a task provider directly to the task container is not supported.")
    }

    def "can override description and group without @Internal annotation"() {
        buildFile << """
            class CustomTask extends DefaultTask {
                @Override
                public String getDescription() {
                    return "My custom task description.";
                }
                @Override
                public String getGroup() {
                    return "custom group";
                }
                @TaskAction
                void doWork() {
                    println("Hello from CustomTask")
                }
            }
            tasks.register("customTask", CustomTask)
        """

        when:
        succeeds("tasks")

        then:
        outputContains("""
Custom group tasks
------------------
customTask - My custom task description.
""")

        when:
        succeeds("customTask")

        then:
        outputContains("Hello from CustomTask")
    }
}
