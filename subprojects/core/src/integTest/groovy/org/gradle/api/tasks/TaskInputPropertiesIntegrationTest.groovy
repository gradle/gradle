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

package org.gradle.api.tasks

import org.gradle.api.file.FileCollection
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.internal.Actions
import spock.lang.Issue
import spock.lang.Unroll

class TaskInputPropertiesIntegrationTest extends AbstractIntegrationSpec {

    def "reports which properties are not serializable"() {
        buildFile << """
            task foo {
                inputs.property "a", "hello"
                inputs.property "b", new Foo()
                outputs.file "foo.txt"
                doLast { file("foo.txt") << "" }
            }

            class Foo {
                int x
                String toString() { "xxx" }
            }
        """

        when: fails "foo"
        then: failure.assertHasDescription("Unable to store input properties for task ':foo'. Property 'b' with value 'xxx' cannot be serialized.")
    }

    def "deals gracefully with not serializable contents of GStrings"() {
        buildFile << """
            task foo {
                inputs.property "a", "hello \${new Foo()}"
                outputs.file "foo.txt"
                doLast { file("foo.txt") << "" }
            }

            class Foo {
                int x
                String toString() { "xxx" }
            }
        """

        expect:
        run("foo").assertTaskNotSkipped(":foo")
        run("foo").assertTaskSkipped(":foo")
    }

    @Issue("https://issues.gradle.org/browse/GRADLE-3435")
    def "task is not up-to-date after file moved between input properties"() {
        (1..3).each {
            file("input${it}.txt").createNewFile()
        }
        file("buildSrc/src/main/groovy/TaskWithTwoFileCollectionInputs.groovy") << """
            import org.gradle.api.*
            import org.gradle.api.file.*
            import org.gradle.api.tasks.*

            class TaskWithTwoFileCollectionInputs extends DefaultTask {
                @InputFiles FileCollection inputs1
                @InputFiles FileCollection inputs2

                @OutputDirectory File output = project.buildDir

                @TaskAction void action() {}
            }
        """

        buildFile << """
            task test(type: TaskWithTwoFileCollectionInputs) {
                inputs1 = files("input1.txt", "input2.txt")
                inputs2 = files("input3.txt")
            }
        """

        when:
        succeeds "test"

        then:
        executedAndNotSkipped ':test'

        when:
        succeeds "test"

        then:
        skipped ':test'

        // Keep the same files, but move one of them to the other property
        buildFile << """
            test {
                inputs1 = files("input1.txt")
                inputs2 = files("input2.txt", "input3.txt")
            }
        """

        when:
        succeeds "test", "--info"

        then:
        executedAndNotSkipped ':test'
        outputContains "Input property 'inputs1' file ${file("input2.txt")} has been removed."
        outputContains "Input property 'inputs2' file ${file("input2.txt")} has been added."

        when:
        succeeds "test"

        then:
        skipped ':test'
    }

    @Issue("https://issues.gradle.org/browse/GRADLE-3435")
    def "task is not up-to-date after swapping directories between output properties"() {
        file("buildSrc/src/main/groovy/TaskWithTwoOutputDirectoriesProperties.groovy") << """
            import org.gradle.api.*
            import org.gradle.api.tasks.*

            class TaskWithTwoOutputDirectoriesProperties extends DefaultTask {
                @InputFiles def inputFiles = project.files()

                @OutputDirectory File outputs1
                @OutputDirectory File outputs2

                @TaskAction void action() {}
            }
        """

        buildFile << """
            task test(type: TaskWithTwoOutputDirectoriesProperties) {
                outputs1 = file("\$buildDir/output1")
                outputs2 = file("\$buildDir/output2")
            }
        """

        when:
        succeeds "test"

        then:
        executedAndNotSkipped ':test'

        when:
        succeeds "test"

        then:
        skipped ':test'

        // Keep the same files, but move one of them to the other property
        buildFile.delete()
        buildFile << """
            task test(type: TaskWithTwoOutputDirectoriesProperties) {
                outputs1 = file("\$buildDir/output2")
                outputs2 = file("\$buildDir/output1")
            }
        """

        when:
        succeeds "test", "--info"

        then:
        executedAndNotSkipped ':test'
        outputContains "Output property 'outputs1' file ${file("build/output1")} has been removed."
        outputContains "Output property 'outputs2' file ${file("build/output2")} has been removed."

        when:
        succeeds "test"

        then:
        skipped ':test'
    }

    def "can annotate Map property with @OutputDirectories and @OutputFiles"() {
        file("buildSrc/src/main/groovy/TaskWithOutputFilesProperty.groovy") << """
            import org.gradle.api.*
            import org.gradle.api.tasks.*

            class TaskWithValidOutputFilesAndOutputDirectoriesProperty extends DefaultTask {
                @InputFiles def inputFiles = project.files()
                @OutputFiles Map<String, File> outputFiles = [:]
                @OutputDirectories Map<String, File> outputDirs = [:]
                @TaskAction void action() {}
            }
        """

        buildFile << """
            task test(type: TaskWithValidOutputFilesAndOutputDirectoriesProperty) {
            }
        """

        expect:
        succeeds "test"
    }

    @Unroll
    def "fails when inputs calls are chained (#method)"() {
        buildFile << """
            task test {
                inputs.${call}.${call}
            }
        """

        expect:
        fails "test"
        failureCauseContains "Chaining of the TaskInputs.$method method is not supported since Gradle 4.0."

        where:
        method              | call
        "file(Object)"      | 'file("a")'
        "dir(Object)"       | 'dir("a")'
        "files(Object...)"  | 'files("a", "b")'
    }

    @Unroll
    def "fails when outputs calls are chained (#method)"() {
        buildFile << """
            task test {
                outputs.${call}.${call}
            }
        """

        expect:
        fails "test"
        failureCauseContains "Chaining of the TaskOutputs.$method method is not supported since Gradle 4.0."

        where:
        method             | call
        "file(Object)"     | 'file("a")'
        "dir(Object)"      | 'dir("a")'
        "files(Object...)" | 'files("a", "b")'
    }

    def "task depends on other task whose outputs are its inputs"() {
        buildFile << """
            task a {
                outputs.file 'a.txt'
                doLast {
                    file('a.txt') << "Data"
                }
            }

            task b {
                inputs.files tasks.a.outputs.files
            }
        """

        expect:
        succeeds "b" assertTasksExecutedInOrder ":a", ":b"
    }

    def "task is out of date when property added"() {
        buildFile << """
task someTask {
    inputs.property("a", "value1")
    outputs.file "out"
    doLast org.gradle.internal.Actions.doNothing() // attach an action that is not defined by the build script
}
"""
        given:
        succeeds "someTask"

        when:
        run "someTask"

        then:
        skipped(":someTask")

        // add property
        when:
        buildFile << """
someTask.inputs.property("b", 12)
"""
        and:
        executer.withArgument("-i")
        run "someTask"

        then:
        executedAndNotSkipped(":someTask")
        outputContains("Input property 'b' has been added for task ':someTask'")

        when:
        run "someTask"

        then:
        skipped(":someTask")
    }

    def "task is out of date when property removed"() {
        buildFile << """
task someTask {
    inputs.property("a", "value1")
    inputs.property("b", "value2")
    outputs.file "out"
    doLast ${Actions.name}.doNothing() // attach an action that is not defined by the build script
}
"""
        given:
        succeeds "someTask"

        when:
        run "someTask"

        then:
        skipped(":someTask")

        // add property
        when:
        buildFile.text = """
task someTask {
    inputs.property("b", "value2")
    outputs.file "out"
    doLast ${Actions.name}.doNothing()
}
"""
        and:
        executer.withArgument("-i")
        run "someTask"

        then:
        executedAndNotSkipped(":someTask")
        outputContains("Input property 'a' has been removed for task ':someTask'")

        when:
        run "someTask"

        then:
        skipped(":someTask")
    }

    @Unroll
    def "task is out of date when property type changes"() {
        buildFile << """
task someTask {
    inputs.property("a", $oldValue)
    outputs.file "out"
    doLast ${Actions.name}.doNothing() // attach an action that is not defined by the build script
}
"""
        given:
        succeeds "someTask"

        when:
        run "someTask"

        then:
        skipped(":someTask")

        // change property type
        when:
        buildFile.replace(oldValue, newValue)

        and:
        executer.withArgument("-i")
        run "someTask"

        then:
        executedAndNotSkipped(":someTask")
        outputContains("Value of input property 'a' has changed for task ':someTask'")

        when:
        run "someTask"

        then:
        skipped(":someTask")

        where:
        oldValue             | newValue
        "'value1'"           | "['value1']"
        "'value1'"           | "null"
        "null"               | "123"
        "[123]"              | "123"
        "false"              | "12.3"
        "[345, 'hi'] as Set" | "[345, 'hi']"
        "file('1')"          | "files('1')"
        "123"                | "123L"
        "123"                | "123 as short"
    }

    @Unroll
    def "task can use property of type #type"() {
        file("buildSrc/src/main/java/SomeTask.java") << """
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.Optional;
import java.io.File;

public class SomeTask extends DefaultTask {
    public $type v;
    @Input
    public $type getV() { return v; }

    public File d;
    @OutputDirectory
    public File getD() { return d; }
    
    @TaskAction
    public void go() { }
}
"""

        buildFile << """
task someTask(type: SomeTask) {
    v = $initialValue
    d = file("build/out")
}
"""
        given:
        succeeds "someTask"

        when:
        run "someTask"

        then:
        skipped(":someTask")

        when:
        buildFile.replace("v = $initialValue", "v = $newValue")
        executer.withArgument("-i")
        run "someTask"

        then:
        executedAndNotSkipped(":someTask")
        outputContains("Value of input property 'v' has changed for task ':someTask'")

        when:
        run "someTask"

        then:
        skipped(":someTask")

        where:
        type                             | initialValue                    | newValue
        "String"                         | "'value 1'"                     | "'value 2'"
        "java.io.File"                   | "file('file1')"                 | "file('file2')"
        "boolean"                        | "true"                          | "false"
        "Boolean"                        | "Boolean.TRUE"                  | "Boolean.FALSE"
        "int"                            | "123"                           | "-45"
        "Integer"                        | "123"                           | "-45"
        "long"                           | "123"                           | "-45"
        "Long"                           | "123"                           | "-45"
        "short"                          | "123"                           | "-45"
        "Short"                          | "123"                           | "-45"
        "java.math.BigDecimal"           | "12.3"                          | "-45.432"
        "java.math.BigInteger"           | "12"                            | "-45"
        "java.util.List<String>"         | "['value1', 'value2']"          | "['value1']"
        "java.util.List<String>"         | "[]"                            | "['value1', null, false, 123, 12.4, ['abc'], [true] as Set]"
        "String[]"                       | "new String[0]"                 | "['abc'] as String[]"
        "Object[]"                       | "[123, 'abc'] as Object[]"      | "['abc'] as String[]"
        "java.util.Collection<String>"   | "['value1', 'value2']"          | "['value1'] as SortedSet"
        "java.util.Set<String>"          | "['value1', 'value2'] as Set"   | "['value1'] as Set"
        "Iterable<java.io.File>"         | "[file('1'), file('2')] as Set" | "files('1')"
        FileCollection.name              | "files('1', '2')"               | "configurations.create('empty')"
        "java.util.Map<String, Boolean>" | "[a: true, b: false]"           | "[a: true, b: true]"
    }
}
