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
import org.gradle.integtests.fixtures.modes.ToBeFixedForConfigurationCache
import org.gradle.util.internal.ToBeImplemented
import spock.lang.Issue

class TaskDependencyInferenceIntegrationTest extends AbstractIntegrationSpec implements TasksWithInputsAndOutputs {
    def "dependency declared using task provider implies dependency on task"() {
        buildFile << """
            // verify that eager and lazy providers work
            def providerA = tasks.register("a")
            tasks.create("b")
            def providerB = tasks.named("b")
            tasks.register("c") {
                dependsOn providerA
                dependsOn providerB
            }
        """

        when:
        run("c")

        then:
        result.assertTasksScheduled(":a", ":b", ":c")
    }

    def "dependency declared using mapped task provider implies dependency on task and does not run mapping function"() {
        buildFile << """
            def providerA = tasks.register("a")
            tasks.create("b")
            def providerB = tasks.named("b")
            tasks.register("c") {
                dependsOn providerA.map { throw new RuntimeException() }
                dependsOn providerB.map { throw new RuntimeException() }
            }
        """

        when:
        run("c")

        then:
        result.assertTasksScheduled(":a", ":b", ":c")
    }

    def "dependency declared using provider that returns task implies dependency on task"() {
        buildFile << """
            def a = tasks.create("a")
            def provider = provider { a }
            tasks.register("b") {
                dependsOn provider
            }
        """

        when:
        run("b")

        then:
        result.assertTasksScheduled(":a", ":b")
    }

    def "dependency declared using provider mapping that returns task implies dependency on task"() {
        buildFile << """
            def a = tasks.create("a")
            def provider = provider { "a" }
            tasks.register("b") {
                dependsOn provider.map { tasks.getByName(it) }
            }
        """

        when:
        run("b")

        then:
        result.assertTasksScheduled(":a", ":b")
    }

    def "dependency declared using task output file property implies dependency on task"() {
        taskTypeWithOutputFileProperty()
        buildFile << """
            def task = tasks.create("a", FileProducer) {
                output = file("a.txt")
            }
            tasks.register("b") {
                dependsOn task.output
            }
        """

        when:
        run("b")

        then:
        result.assertTasksScheduled(":a", ":b")
    }

    def "dependency declared using mapped task output file property implies dependency on task and does not run mapping function"() {
        taskTypeWithOutputFileProperty()
        buildFile << """
            def task = tasks.create("a", FileProducer) {
                output = file("a.txt")
            }
            tasks.register("b") {
                dependsOn task.output.map { throw new RuntimeException() }
            }
        """

        when:
        run("b")

        then:
        result.assertTasksScheduled(":a", ":b")
    }

    def "dependency declared using property whose value is a task output provider implies dependency on task"() {
        taskTypeWithOutputFileProperty()
        buildFile << """
            def task = tasks.create("a", FileProducer) {
                output = file("a.txt")
            }
            def property = objects.fileProperty()
            property.set(task.output)
            tasks.register("b") {
                dependsOn property
            }
        """

        when:
        run("b")

        then:
        result.assertTasksScheduled(":a", ":b")
    }

    def "dependency declared using flat map provider whose value is a task output property implies dependency on task"() {
        taskTypeWithOutputFileProperty()
        buildFile << """
            def provider = tasks.register("a", FileProducer) {
                output = file("a.txt")
            }
            tasks.register("b") {
                dependsOn provider.flatMap { it.output }
            }
        """

        when:
        run("b")

        then:
        result.assertTasksScheduled(":a", ":b")
    }

    def "dependency declared using property whose value is a mapped task output provider implies dependency on task and does not run mapping function"() {
        taskTypeWithOutputFileProperty()
        buildFile << """
            def task = tasks.create("a", FileProducer) {
                output = file("a.txt")
            }
            def property = objects.fileProperty()
            property.set(task.output.map { throw new RuntimeException() })
            tasks.register("b") {
                dependsOn property
            }
        """

        when:
        run("b")

        then:
        result.assertTasksScheduled(":a", ":b")
    }

    def "dependency declared using orElse provider whose original value is task output file property implies dependency on task"() {
        taskTypeWithOutputFileProperty()
        buildFile << """
            def taskA = tasks.create("a", FileProducer) {
                output = file("a.txt")
            }
            def taskB = tasks.create("b", FileProducer) {
                output = file("b.txt")
            }
            tasks.register("c") {
                dependsOn taskA.output.orElse(taskB.output)
            }
        """

        when:
        run("c")

        then:
        result.assertTasksScheduled(":a", ":c")
    }

    def "dependency declared using orElse provider whose original value is task output file property and alternative value is constant implies dependency on task"() {
        taskTypeWithOutputFileProperty()
        buildFile << """
            def taskA = tasks.create("a", FileProducer) {
                output = file("a.txt")
            }
            tasks.register("c") {
                dependsOn taskA.output.orElse([])
            }
        """

        when:
        run("c")

        then:
        result.assertTasksScheduled(":a", ":c")
    }

    def "dependency declared using orElse provider whose original value is missing and alternative value is task output file property implies dependency on task"() {
        taskTypeWithOutputFileProperty()
        buildFile << """
            def taskA = tasks.create("a", FileProducer) {
                // no output value
            }
            def taskB = tasks.create("b", FileProducer) {
                output = file("b.txt")
            }
            tasks.register("c") {
                dependsOn taskA.output.orElse(taskB.output)
            }
        """

        when:
        run("c")

        then:
        result.assertTasksScheduled(":b", ":c")
    }

    def "dependency declared using orElse provider whose original value is missing and alternative value is missing task output file property doesn't imply dependency on task"() {
        taskTypeWithOutputFileProperty()
        buildFile << """
            def taskA = tasks.create("a", FileProducer) {
                // no output value
            }
            def taskB = tasks.create("b", FileProducer) {
                // no output value
            }
            tasks.register("c") {
                dependsOn taskA.output.orElse(taskB.output)
            }
        """

        when:
        run("c")

        then:
        result.assertTasksScheduled(":c")
    }

    def "dependency declared using orElse provider whose original value is missing and alternative value is constant does not imply task dependency"() {
        taskTypeWithOutputFileProperty()
        buildFile << """
            def taskA = tasks.create("a", FileProducer) {
                // no output value
            }
            tasks.register("b") {
                dependsOn taskA.output.orElse([])
            }
        """

        when:
        run("b")

        then:
        result.assertTasksScheduled(":b")
    }

    def "dependency declared using provider that returns task name implies dependency on task"() {
        buildFile << """
            def a = tasks.create("a")
            def provider = provider { "a" }
            tasks.register("b") {
                dependsOn provider
            }
        """

        when:
        run("b")

        then:
        result.assertTasksScheduled(":a", ":b")
    }

    def "dependency declared using mapped provider that returns task name implies dependency on task"() {
        buildFile << """
            def a = tasks.create("a")
            def provider = provider { a }.map { it.name }
            tasks.register("b") {
                dependsOn provider
            }
        """

        when:
        run("b")

        then:
        result.assertTasksScheduled(":a", ":b")
    }

    def "dependency declared using #value fails"() {
        buildFile << """
            tasks.register("b") {
                dependsOn($value)
            }
        """

        when:
        fails("b")

        then:
        failure.assertHasDescription("Could not determine the dependencies of task ':b'.")
        failure.assertHasCause("""Cannot convert ${displayName} to a task.
The following types/formats are supported:
  - A String or CharSequence task name or path
  - A Task instance
  - A TaskReference instance
  - A Buildable instance
  - A TaskDependency instance
  - A Provider that represents a task output
  - A Provider instance that returns any of these types
  - A Closure instance that returns any of these types
  - A Callable instance that returns any of these types
  - An Iterable, Collection, Map or array instance that contains any of these types""")

        where:
        value     | displayName
        "12"      | "12"
        "false"   | "false"
        "[false]" | "false"
    }

    def "dependency declared using file #value fails"() {
        buildFile << """
            tasks.register("b") {
                dependsOn($value)
            }
        """

        when:
        fails("b")
        false

        then:
        failure.assertHasDescription("Could not determine the dependencies of task ':b'.")
        failure.assertHasCause("""Cannot convert ${file(path)} to a task.
The following types/formats are supported:
  - A String or CharSequence task name or path
  - A Task instance
  - A TaskReference instance
  - A Buildable instance
  - A TaskDependency instance
  - A Provider that represents a task output
  - A Provider instance that returns any of these types
  - A Closure instance that returns any of these types
  - A Callable instance that returns any of these types
  - An Iterable, Collection, Map or array instance that contains any of these types""")

        where:
        value                                             | path
        "file('123')"                                     | '123'
        "file('123').toPath()"                            | '123'
        "layout.projectDirectory"                         | '.'
        "layout.projectDirectory.file('123')"             | '123'
        "layout.projectDirectory.dir('123')"              | '123'
        "layout.projectDirectory.file(provider { '123'})" | '123'
        "layout.projectDirectory.dir(provider { '123'})"  | '123'
        "layout.buildDirectory"                           | 'build'
        "layout.buildDirectory.file('123')"               | 'build/123'
        "layout.buildDirectory.dir('123')"                | 'build/123'
    }

    def "dependency declared using provider that returns #value fails"() {
        buildFile << """
            def provider = provider { $value }
            tasks.register("b") {
                dependsOn(provider)
            }
        """

        when:
        fails("b")

        then:
        failure.assertHasDescription("Could not determine the dependencies of task ':b'.")
        failure.assertHasCause("""Cannot convert ${displayName} to a task.
The following types/formats are supported:
  - A String or CharSequence task name or path
  - A Task instance
  - A TaskReference instance
  - A Buildable instance
  - A TaskDependency instance
  - A Provider that represents a task output
  - A Provider instance that returns any of these types
  - A Closure instance that returns any of these types
  - A Callable instance that returns any of these types
  - An Iterable, Collection, Map or array instance that contains any of these types""")

        where:
        value     | displayName
        "12"      | "12"
        "false"   | "false"
        "[false]" | "false"
    }

    def "dependency declared using file provider with value #value fails"() {
        buildFile << """
            def provider = provider { $value }
            tasks.register("b") {
                dependsOn(provider)
            }
        """


        when:
        fails("b")

        then:
        failure.assertHasDescription("Could not determine the dependencies of task ':b'.")
        failure.assertHasCause("""Cannot convert ${file(path)} to a task.
The following types/formats are supported:
  - A String or CharSequence task name or path
  - A Task instance
  - A TaskReference instance
  - A Buildable instance
  - A TaskDependency instance
  - A Provider that represents a task output
  - A Provider instance that returns any of these types
  - A Closure instance that returns any of these types
  - A Callable instance that returns any of these types
  - An Iterable, Collection, Map or array instance that contains any of these types""")

        where:
        value                                             | path
        "file('123')"                                     | '123'
        "file('123').toPath()"                            | '123'
        "layout.projectDirectory"                         | '.'
        "layout.projectDirectory.file('123')"             | '123'
        "layout.projectDirectory.dir('123')"              | '123'
        "layout.projectDirectory.file(provider { '123'})" | '123'
        "layout.projectDirectory.dir(provider { '123'})"  | '123'
        "layout.buildDirectory"                           | 'build'
        "layout.buildDirectory.file('123')"               | 'build/123'
        "layout.buildDirectory.dir('123')"                | 'build/123'
    }

    def "dependency declared using file collection implies no task dependencies"() {
        buildFile << """
            tasks.register("b") {
                dependsOn files(file('123'))
            }
        """

        when:
        run("b")

        then:
        result.assertTasksScheduled(":b")
    }

    def "produces reasonable error message when task dependency closure throws exception"() {
        buildFile << """
    task a
    a.dependsOn {
        throw new RuntimeException('broken')
    }
"""
        when:
        fails "a"

        then:
        failure.assertHasDescription("Could not determine the dependencies of task ':a'.")
            .assertHasCause('broken')
            .assertHasFileName("Build file '$buildFile'")
            .assertHasLineNumber(4)
    }

    def "dependency declared using provider with no value fails"() {
        buildFile << """
            def provider = objects.property(String)
            tasks.register("a") {
                dependsOn provider
            }
        """

        when:
        fails("a")

        then:
        failure.assertHasDescription("Could not determine the dependencies of task ':a'.")
        failure.assertHasCause("Cannot query the value of this property because it has no value available.")
    }

    def "input file collection containing task provider implies dependency on all outputs of the task"() {
        taskTypeWithMultipleOutputFiles()
        taskTypeWithInputFileCollection()
        buildFile << """
            def provider = tasks.register("a", OutputFilesTask) {
                out1 = file("file1.txt")
                out2 = file("file2.txt")
            }
            tasks.register("b", InputFilesTask) {
                inFiles.from(provider)
                outFile = file("out.txt")
            }
        """

        when:
        run("b")

        then:
        result.assertTasksScheduled(":a", ":b")
        file("out.txt").text == "1,2"
    }

    def "input file collection containing mapped task provider implies dependency on a specific output of the task"() {
        taskTypeWithMultipleOutputFiles()
        taskTypeWithInputFileCollection()
        buildFile << """
            def provider = tasks.register("a", OutputFilesTask) {
                out1 = file("file1.txt")
                out2 = file("file2.txt")
            }
            tasks.register("b", InputFilesTask) {
                inFiles.from provider.map { it.out1 }
                outFile = file("out.txt")
            }
        """

        when:
        run("b")

        then:
        result.assertTasksScheduled(":a", ":b")
        file("out.txt").text == "1"
    }

    def "input file collection containing filtered tree of task output implies dependency on the task"() {
        taskTypeWithOutputDirectoryProperty()
        taskTypeWithInputFileCollection()
        buildFile << """
            def task = tasks.create("a", DirProducer) {
                output = layout.buildDirectory.dir('dir')
                names = ['a.txt', 'b.txt', 'c.txt']
            }
            tasks.register("b", InputFilesTask) {
                inFiles.from task.output.map { it.asFileTree.matching { include 'a.*'; include 'c.txt' } }
                outFile = file("out.txt")
            }
        """

        when:
        run("b")

        then:
        result.assertTasksScheduled(":a", ":b")
        file("out.txt").text == "content,content"
    }

    def "input file collection containing filtered tree containing task output implies dependency on the task"() {
        taskTypeWithOutputDirectoryProperty()
        taskTypeWithInputFileCollection()
        buildFile << """
            def task = tasks.create("a", DirProducer) {
                output = layout.buildDirectory.dir('dir')
                names = ['a.txt', 'b.txt', 'c.txt']
            }
            def tree = project.files(task.output).asFileTree
            tasks.register("b", InputFilesTask) {
                inFiles.from tree.matching { include 'a.*'; include 'c.txt' }
                outFile = file("out.txt")
            }
        """

        when:
        run("b")

        then:
        result.assertTasksScheduled(":a", ":b")
        file("out.txt").text == "content,content"
    }

    def "input file property with value of mapped task provider implies dependency on a specific output of the task"() {
        taskTypeWithMultipleOutputFiles()
        taskTypeWithInputFileProperty()
        buildFile << """
            def provider = tasks.register("a", OutputFilesTask) {
                out1 = file("file1.txt")
                out2 = file("file2.txt")
            }
            tasks.register("b", InputFileTask) {
                def projectLayout = project.layout
                inFile = provider.map { projectLayout.projectDir.file(it.out1.absolutePath) }
                outFile = file("out.txt")
            }
        """

        when:
        run("b")

        then:
        result.assertTasksScheduled(":a", ":b")
        file("out.txt").text == "1"
    }

    def "input file property with value of orElse provider whose original value is task output file property implies dependency on task"() {
        taskTypeWithOutputFileProperty()
        taskTypeWithInputFileProperty()
        buildFile << """
            def taskA = tasks.create("a", FileProducer) {
                output = file("a.txt")
                content = "a"
            }
            def taskB = tasks.create("b", FileProducer) {
                output = file("b.txt")
                content = "b"
            }
            tasks.register("c", InputFileTask) {
                inFile = taskA.output.orElse(taskB.output)
                outFile = file("out.txt")
            }
        """

        when:
        run("c")

        then:
        result.assertTasksScheduled(":a", ":c")
        file("out.txt").text == "a"
    }

    def "input file property with value of orElse provider whose original value is task output file property and alternative value is constant implies dependency on task"() {
        taskTypeWithOutputFileProperty()
        taskTypeWithInputFileProperty()
        buildFile << """
            def taskA = tasks.create("a", FileProducer) {
                output = file("a.txt")
                content = "a"
            }
            tasks.register("c", InputFileTask) {
                inFile = taskA.output.orElse(file("b.txt"))
                outFile = file("out.txt")
            }
        """

        when:
        run("c")

        then:
        result.assertTasksScheduled(":a", ":c")
        file("out.txt").text == "a"
    }

    def "input file property with value of orElse provider whose original value is missing and alternative value is task output file property implies dependency on task"() {
        taskTypeWithOutputFileProperty()
        taskTypeWithInputFileProperty()
        buildFile << """
            def taskA = tasks.create("a", FileProducer) {
                // No output defined
            }
            def taskB = tasks.create("b", FileProducer) {
                output = file("b.txt")
                content = "b"
            }
            tasks.register("c", InputFileTask) {
                inFile = taskA.output.orElse(taskB.output)
                outFile = file("out.txt")
            }
        """

        when:
        run("c")

        then:
        result.assertTasksScheduled(":b", ":c")
        file("out.txt").text == "b"
    }

    def "input file property with value of orElse provider whose original value is missing and alternative value is constant does not imply dependency on task"() {
        taskTypeWithOutputFileProperty()
        taskTypeWithInputFileProperty()
        buildFile << """
            def taskA = tasks.create("a", FileProducer) {
                // No output defined
            }
            tasks.register("c", InputFileTask) {
                inFile = taskA.output.orElse(layout.projectDir.file("b.txt"))
                outFile = file("out.txt")
            }
        """
        file("b.txt").text = "b"

        when:
        run("c")

        then:
        result.assertTasksScheduled(":c")
        file("out.txt").text == "b"
    }

    def "input file collection containing mapped task output property implies dependency on a specific output of the task"() {
        taskTypeWithMultipleOutputFileProperties()
        taskTypeWithInputFileCollection()
        buildFile << """
            def a = tasks.create("a", OutputFilesTask) {
                out1 = file("file1.txt")
                out2 = file("file2.txt")
            }
            tasks.register("b", InputFilesTask) {
                inFiles.from a.out1.map { it }
                outFile = file("out.txt")
            }
        """

        when:
        run("b")

        then:
        result.assertTasksScheduled(":a", ":b")
        file("out.txt").text == "1"
    }

    def "input file collection containing task output property implies dependency on a specific output of the task"() {
        taskTypeWithMultipleOutputFileProperties()
        taskTypeWithInputFileCollection()
        buildFile << """
            def a = tasks.create("a", OutputFilesTask) {
                out1 = file("file1.txt")
                out2 = file("file2.txt")
            }
            tasks.register("b", InputFilesTask) {
                inFiles.from a.out1
                outFile = file("out.txt")
            }
        """

        when:
        run("b")

        then:
        result.assertTasksScheduled(":a", ":b")
        file("out.txt").text == "1"
    }

    def "input file collection containing collection property implies dependency on a specific output of the task"() {
        taskTypeWithMultipleOutputFileProperties()
        taskTypeWithInputFileCollection()
        buildFile << """
            def a = tasks.create("a", OutputFilesTask) {
                out1 = file("a-1.txt")
                out2 = file("a-2.txt")
            }
            def b = tasks.create("b", OutputFilesTask) {
                out1 = file("b-1.txt")
                out2 = file("b-2.txt")
            }
            def files = objects.setProperty(RegularFile)
            files.add(a.out1)
            files.add(b.out2)
            tasks.register("c", InputFilesTask) {
                inFiles.from files
                outFile = file("out.txt")
            }
        """

        when:
        run("c")

        then:
        result.assertTasksScheduled(":a", ":b", ":c")
        file("out.txt").text == "1,2"
    }

    def "input file property with value of flat map task provider implies dependency on a specific output of the task"() {
        taskTypeWithMultipleOutputFileProperties()
        taskTypeWithInputFileProperty()
        buildFile << """
            def provider = tasks.register("a", OutputFilesTask) {
                out1 = file("file1.txt")
                out2 = file("file2.txt")
            }
            tasks.register("b", InputFileTask) {
                inFile = provider.flatMap { it.out1 }
                outFile = file("out.txt")
            }
        """

        when:
        run("b")

        then:
        result.assertTasksScheduled(":a", ":b")
        file("out.txt").text == "1"
    }

    def "input file collection containing flat map task provider implies dependency on a specific output of the task"() {
        taskTypeWithMultipleOutputFileProperties()
        taskTypeWithInputFileCollection()
        buildFile << """
            def a = tasks.register("a", OutputFilesTask) {
                out1 = file("file1.txt")
                out2 = file("file2.txt")
            }
            tasks.register("b", InputFilesTask) {
                inFiles.from a.flatMap { it.out1 }
                outFile = file("out.txt")
            }
        """

        when:
        run("b")

        then:
        result.assertTasksScheduled(":a", ":b")
        file("out.txt").text == "1"
    }

    def "input file collection containing container element provider implies dependency on task"() {
        taskTypeWithOutputFileProperty()
        taskTypeWithInputFileCollection()
        buildFile << """
            def a = tasks.create("a", FileProducer) {
                output = file("file1.txt")
            }
            configurations { thing }
            dependencies { thing a.outputs.files }

            tasks.register("b", InputFilesTask) {
                inFiles.from configurations.named('thing')
                outFile = file("out.txt")
            }
        """

        when:
        run("b")

        then:
        result.assertTasksScheduled(":a", ":b")
        file("out.txt").text == "content"
    }

    def "input file collection containing provider that returns #value does not imply task dependency"() {
        taskTypeWithInputFileCollection()
        buildFile << """
            tasks.register("b", InputFilesTask) {
                inFiles.from provider { ${value} }
                outFile = file("out.txt")
            }
        """
        file("in.txt") << "1"

        when:
        run("b")

        then:
        result.assertTasksScheduled(":b")
        file("out.txt").text == "1"

        where:
        value                                                 | _
        "file('in.txt')"                                      | _
        "file('in.txt').toPath()"                             | _
        "layout.projectDirectory.file('in.txt')"              | _
        "layout.projectDirectory.file(provider { 'in.txt' })" | _
        "layout.buildDirectory.file('../in.txt')"             | _
        "layout.buildDirectory.file(provider {'../in.txt' })" | _
    }

    def "input property with value of mapped task output implies dependency on the task"() {
        taskTypeWithOutputFileProperty()
        taskTypeWithIntInputProperty()
        buildFile << """
            def task = tasks.create("a", FileProducer) {
                output = file("file.txt")
                content = "12"
            }
            tasks.register("b", InputTask) {
                inValue = task.output.map { it.asFile.text as Integer }
                outFile = file("out.txt")
            }
        """

        when:
        run("b")

        then:
        result.assertTasksScheduled(":a", ":b")
        file("out.txt").text == "22"
    }

    @Issue("https://github.com/gradle/gradle/issues/19252")
    def "input property with value of mapped task provider output implies dependency on the task"() {
        taskTypeWithOutputFileProperty()
        taskTypeWithIntInputProperty()
        buildFile << """
            def taskProvider = tasks.register("a", FileProducer) {
                output = file("file.txt")
                content = "12"
            }
            tasks.register("b", InputTask) {
                inValue = taskProvider.flatMap { it.output.map { it.asFile.text as Integer } }
                outFile = file("out.txt")
            }
        """

        when:
        run("b")

        then:
        result.assertTasksScheduled(":a", ":b")
        file("out.txt").text == "22"
    }

    @Issue("https://github.com/gradle/gradle/issues/13590")
    def "nested property with value of mapped task output implies dependency on the task"() {
        taskTypeWithOutputFileProperty()
        buildFile << """
            class Bean {
                @Input
                final String value
                Bean(String value) { this.value = value }
            }
            abstract class NestedTask extends DefaultTask {
                @Nested
                abstract Property<Bean> getBean()
                @OutputFile
                abstract RegularFileProperty getOutFile()
                @TaskAction
                def go() {
                    outFile.get().asFile.text = bean.get().value
                }
            }
            def task = tasks.create("a", FileProducer) {
                output = file("file.txt")
                content = "12"
            }
            tasks.register("b", NestedTask) {
                bean = task.output.map { new Bean(it.asFile.text) }
                outFile = file("out.txt")
            }
        """

        when:
        run("b")

        then:
        result.assertTasksScheduled(":a", ":b")
        file("out.txt").text == "12"
    }

    @Issue("https://github.com/gradle/gradle/issues/13590")
    def "nested list property with value of flat mapped task provider output implies dependency on the task"() {
        buildFile << """
            abstract class FilesProducer extends DefaultTask {
                @OutputFiles
                abstract ListProperty<File> getOutputFiles()
                @TaskAction
                def go() {
                    outputFiles.get().each { it.text = it.name }
                }
            }
            class Bean {
                @Input
                final String value
                Bean(String value) { this.value = value }
            }
            abstract class NestedTask extends DefaultTask {
                @Nested
                abstract ListProperty<Bean> getBeans()
                @OutputFile
                abstract RegularFileProperty getOutFile()
                @TaskAction
                def go() {
                    outFile.get().asFile.text = beans.get().collect { it.value }.join(",")
                }
            }
            def taskProvider = tasks.register("a", FilesProducer) {
                outputFiles.add(file("file1.txt"))
                outputFiles.add(file("file2.txt"))
            }
            tasks.register("b", NestedTask) {
                beans.addAll(taskProvider.flatMap { it.outputFiles }.map { files -> files.collect { new Bean(it.text) } })
                outFile = file("out.txt")
            }
        """

        when:
        run("b")

        then:
        result.assertTasksScheduled(":a", ":b")
        file("out.txt").text == "file1.txt,file2.txt"
    }

    def "nested property with fixed value whose input file property has value of task output implies dependency on the task"() {
        taskTypeWithOutputFileProperty()
        buildFile << """
            abstract class Bean {
                @InputFile
                abstract RegularFileProperty getInputFile()
            }
            abstract class NestedTask extends DefaultTask {
                @Nested
                abstract Property<Bean> getBean()
                @OutputFile
                abstract RegularFileProperty getOutFile()
                @TaskAction
                def go() {
                    outFile.get().asFile.text = bean.get().inputFile.get().asFile.text
                }
            }
            def task = tasks.create("a", FileProducer) {
                output = file("file.txt")
                content = "12"
            }
            tasks.register("b", NestedTask) {
                def value = objects.newInstance(Bean)
                value.inputFile = task.output
                bean = value
                outFile = file("out.txt")
            }
        """

        when:
        run("b")

        then:
        result.assertTasksScheduled(":a", ":b")
        file("out.txt").text == "12"
    }

    @ToBeImplemented("Leaf dependencies of fixed elements are not discovered while the collection also holds an element that cannot be computed before its producer ran")
    def "nested list property with fixed and mapped elements does not yet imply dependency of the fixed element"() {
        taskTypeWithOutputFileProperty()
        buildFile << """
            interface Bean {}
            abstract class LazyBean implements Bean {
                @InputFile
                abstract RegularFileProperty getInputFile()
            }
            class PlainBean implements Bean {
                @Input
                final String value
                PlainBean(String value) { this.value = value }
            }
            abstract class NestedTask extends DefaultTask {
                @Nested
                abstract ListProperty<Bean> getBeans()
                @OutputFile
                abstract RegularFileProperty getOutFile()
                @TaskAction
                def go() {
                    outFile.get().asFile.text = beans.get().collect { it instanceof LazyBean ? it.inputFile.get().asFile.text : it.value }.join(",")
                }
            }
            def taskA = tasks.create("a", FileProducer) {
                output = file("a.txt")
                content = "a"
            }
            def taskB = tasks.register("b", FileProducer) {
                output = file("b.txt")
                content = "b"
            }
            tasks.register("c", NestedTask) {
                def eager = objects.newInstance(LazyBean)
                eager.inputFile = taskA.output
                beans.add(eager)
                beans.add(taskB.flatMap { it.output }.map { new PlainBean(it.asFile.text) })
                outFile = file("out.txt")
            }
        """

        when:
        run("c", "--dry-run")

        then:
        // Unpacking the list fails while task b has not run, so the input file of the fixed element is never visited.
        // The producer carried by the list itself is still discovered.
        result.assertTasksScheduled(":b", ":c")
    }

    @ToBeFixedForConfigurationCache(because = "BiProvider.calculateExecutionTimeValue() ignores changing content, so the combiner runs at store time and reads files that do not exist yet")
    def "nested property with value of zipped task outputs implies dependency on both tasks"() {
        taskTypeWithOutputFileProperty()
        buildFile << """
            class Bean {
                @Input
                final String value
                Bean(String value) { this.value = value }
            }
            abstract class NestedTask extends DefaultTask {
                @Nested
                abstract Property<Bean> getBean()
                @OutputFile
                abstract RegularFileProperty getOutFile()
                @TaskAction
                def go() {
                    outFile.get().asFile.text = bean.get().value
                }
            }
            def taskA = tasks.create("a", FileProducer) {
                output = file("a.txt")
                content = "a"
            }
            def taskB = tasks.create("b", FileProducer) {
                output = file("b.txt")
                content = "b"
            }
            tasks.register("c", NestedTask) {
                bean = taskA.output.zip(taskB.output) { x, y -> new Bean(x.asFile.text + y.asFile.text) }
                outFile = file("out.txt")
            }
        """

        when:
        run("c")

        then:
        result.assertTasksScheduled(":a", ":b", ":c")
        file("out.txt").text == "ab"
    }

    def "nested property with value of mapped task provider implies dependency on the task"() {
        taskTypeWithOutputFileProperty()
        buildFile << """
            class Bean {
                @Input
                final String value
                Bean(String value) { this.value = value }
            }
            abstract class NestedTask extends DefaultTask {
                @Nested
                abstract Property<Bean> getBean()
                @OutputFile
                abstract RegularFileProperty getOutFile()
                @TaskAction
                def go() {
                    outFile.get().asFile.text = bean.get().value
                }
            }
            def taskProvider = tasks.register("a", FileProducer) {
                output = file("a.txt")
                content = "12"
            }
            tasks.register("b", NestedTask) {
                bean = taskProvider.map { new Bean(it.content.get()) }
                outFile = file("out.txt")
            }
        """

        when:
        run("b")

        then:
        result.assertTasksScheduled(":a", ":b")
        file("out.txt").text == "12"
    }

    def "nested property with value produced by a task does not imply dependencies of the produced value"() {
        taskTypeWithOutputFileProperty()
        buildFile << """
            class Bean {
                @Input
                final String value
                @InputFile
                final Provider<RegularFile> extra
                Bean(String value, Provider<RegularFile> extra) {
                    this.value = value
                    this.extra = extra
                }
            }
            abstract class NestedTask extends DefaultTask {
                @Nested
                abstract Property<Bean> getBean()
                @OutputFile
                abstract RegularFileProperty getOutFile()
                @TaskAction
                def go() {
                    outFile.get().asFile.text = bean.get().value + bean.get().extra.get().asFile.text
                }
            }
            def taskA = tasks.register("a", FileProducer) {
                output = file("a.txt")
                content = "a"
            }
            def other = tasks.create("other", FileProducer) {
                output = file("other.txt")
                content = "other"
            }
            def otherOutput = other.output
            tasks.register("b", NestedTask) {
                // The bean only exists once task a has run, so the dependency carried by its 'extra' property is not visible when building the task graph
                bean = taskA.flatMap { it.output }.map { new Bean(it.asFile.text, otherOutput) }
                outFile = file("out.txt")
            }
        """

        when:
        run("b", "--dry-run")

        then:
        result.assertTasksScheduled(":a", ":b")
    }

    def "ad hoc input property with value of mapped task output implies dependency on the task"() {
        taskTypeWithOutputFileProperty()
        buildFile << """
            def task = tasks.create("a", FileProducer) {
                output = file("file.txt")
                content = "12"
            }
            tasks.register("b") {
                inputs.property("value", task.output.map { it.asFile.text as Integer })
            }
        """

        when:
        run("b")

        then:
        result.assertTasksScheduled(":a", ":b")
    }

    def "input property with value of mapped task output location does not imply dependency on the task"() {
        taskTypeWithOutputFileProperty()
        taskTypeWithIntInputProperty()
        buildFile << """
            def task = tasks.create("a", FileProducer) {
                output = file("file.txt")
                content = "12"
            }
            tasks.register("b", InputTask) {
                inValue = task.output.locationOnly.map { it.asFile.name.length() }
                outFile = file("out.txt")
            }
        """

        when:
        run("b")

        then:
        result.assertTasksScheduled(":b")
        file("out.txt").text == "18"
    }

    def "input property can have value of mapped output property of same task"() {
        taskTypeWithIntInputProperty()
        buildFile << """
            tasks.register("b", InputTask) {
                inValue = outFile.locationOnly.map { it.asFile.name.length() }
                outFile = file("out.txt")
            }
        """

        when:
        run("b")

        then:
        result.assertTasksScheduled(":b")
        file("out.txt").text == "17"
    }

    def "collection input property containing value of mapped task output implies dependency on the task"() {
        taskTypeWithOutputFileProperty()
        taskTypeWithInputListProperty()
        buildFile << """
            def a = tasks.create("a", FileProducer) {
                output = file("a.txt")
                content = "12"
            }
            def b = tasks.create("b", FileProducer) {
                output = file("b.txt")
                content = "0"
            }
            tasks.register("c", InputTask) {
                inValue = a.output.map { it.asFile.text as Integer }.map { [it, it+3] }
                inValue.add(b.output.map { it.asFile.text as Integer })
                outFile = file("out.txt")
            }
        """

        when:
        run("c")

        then:
        result.assertTasksScheduled(":a", ":b", ":c")
        file("out.txt").text == "22,25,10"
    }

    def "map input property containing value of mapped task output implies dependency on the task"() {
        taskTypeWithOutputFileProperty()
        taskTypeWithInputMapProperty()
        buildFile << """
            def a = tasks.create("a", FileProducer) {
                output = file("a.txt")
                content = "12"
            }
            def b = tasks.create("b", FileProducer) {
                output = file("b.txt")
                content = "0"
            }
            tasks.register("c", InputTask) {
                inValue = a.output.map { it.asFile.text as Integer }.map { [a1: it, a2: it+3] }
                inValue.put("b", b.output.map { it.asFile.text as Integer })
                outFile = file("out.txt")
            }
        """

        when:
        run("c")

        then:
        result.assertTasksScheduled(":a", ":b", ":c")
        file("out.txt").text == "a1=22,a2=25,b=10"
    }
}
