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

package org.gradle.api.tasks;

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Unroll;

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
        result.assertTasksExecuted(":a", ":b", ":c")
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
        result.assertTasksExecuted(":a", ":b", ":c")
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
        result.assertTasksExecuted(":a", ":b")
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
        result.assertTasksExecuted(":a", ":b")
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
        result.assertTasksExecuted(":a", ":b")
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
        result.assertTasksExecuted(":a", ":b")
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
        result.assertTasksExecuted(":a", ":b")
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
        result.assertTasksExecuted(":a", ":b")
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
        result.assertTasksExecuted(":a", ":b")
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
        result.assertTasksExecuted(":a", ":b")
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
        result.assertTasksExecuted(":a", ":b")
    }

    @Unroll
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

    @Unroll
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

    @Unroll
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

    @Unroll
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
        result.assertTasksExecuted(":b")
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
        failure.assertHasCause("No value has been specified for this provider.")
    }

    def "input file collection containing task provider implies dependency on all outputs of the task"() {
        taskTypeWithMultipleOutputFiles()
        taskTypeWithInputFilesProperty()
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
        result.assertTasksExecuted(":a", ":b")
        file("out.txt").text == "1,2"
    }

    def "input file collection containing mapped task provider implies dependency on a specific output of the task"() {
        taskTypeWithMultipleOutputFiles()
        taskTypeWithInputFilesProperty()
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
        result.assertTasksExecuted(":a", ":b")
        file("out.txt").text == "1"
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
                inFile = provider.map { project.layout.projectDir.file(it.out1.absolutePath) }
                outFile = file("out.txt")
            }
        """

        when:
        run("b")

        then:
        result.assertTasksExecuted(":a", ":b")
        file("out.txt").text == "1"
    }

    def "input file collection containing mapped task output property implies dependency on a specific output of the task"() {
        taskTypeWithMultipleOutputFileProperties()
        taskTypeWithInputFilesProperty()
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
        result.assertTasksExecuted(":a", ":b")
        file("out.txt").text == "1"
    }

    def "input file collection containing task output property implies dependency on a specific output of the task"() {
        taskTypeWithMultipleOutputFileProperties()
        taskTypeWithInputFilesProperty()
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
        result.assertTasksExecuted(":a", ":b")
        file("out.txt").text == "1"
    }

    def "input file collection containing collection property implies dependency on a specific output of the task"() {
        taskTypeWithMultipleOutputFileProperties()
        taskTypeWithInputFilesProperty()
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
        result.assertTasksExecuted(":a", ":b", ":c")
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
        result.assertTasksExecuted(":a", ":b")
        file("out.txt").text == "1"
    }

    def "input file collection containing flat map task provider implies dependency on a specific output of the task"() {
        taskTypeWithMultipleOutputFileProperties()
        taskTypeWithInputFilesProperty()
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
        result.assertTasksExecuted(":a", ":b")
        file("out.txt").text == "1"
    }

    def "input file collection containing container element provider implies dependency on task"() {
        taskTypeWithOutputFileProperty()
        taskTypeWithInputFilesProperty()
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
        result.assertTasksExecuted(":a", ":b")
        file("out.txt").text == "content"
    }

    @Unroll
    def "input file collection containing provider that returns #value does not imply task dependency"() {
        taskTypeWithInputFilesProperty()
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
        result.assertTasksExecuted(":b")
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
        taskTypeWithInputProperty()
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
        result.assertTasksExecuted(":a", ":b")
        file("out.txt").text == "22"
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
        result.assertTasksExecuted(":a", ":b")
    }

    def "input property with value of mapped task output location does not imply dependency on the task"() {
        taskTypeWithOutputFileProperty()
        taskTypeWithInputProperty()
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
        result.assertTasksExecuted(":b")
        file("out.txt").text == "18"
    }

    def "input property can have value of mapped output property of same task"() {
        taskTypeWithInputProperty()
        buildFile << """
            tasks.register("b", InputTask) {
                inValue = outFile.locationOnly.map { it.asFile.name.length() }
                outFile = file("out.txt")
            }
        """

        when:
        run("b")

        then:
        result.assertTasksExecuted(":b")
        file("out.txt").text == "17"
    }

}
