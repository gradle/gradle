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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
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
        then: failure.assertHasDescription("Unable to hash task input properties. Property 'b' with value 'xxx' cannot be serialized.")
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

    def "no deprecation warning printed when @OutputDirectories or @OutputFiles is used on Map property"() {
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

    @Unroll("deprecation warning printed when TaskInputs.#method is called")
    def "deprecation warning printed when deprecated source method is used"() {
        buildFile << """
            task test {
                inputs.${call}
            }
        """
        executer.expectDeprecationWarning()
        executer.requireGradleDistribution()

        expect:
        succeeds "test"
        outputContains "The TaskInputs.${method} method has been deprecated and is scheduled to be removed in Gradle 4.0. " +
            "Please use TaskInputs.${replacementMethod}.skipWhenEmpty() instead."

        where:
        method              | replacementMethod  | call
        "source(Object)"    | "file(Object)"     | 'source("a")'
        "sourceDir(Object)" | "dir(Object)"      | 'sourceDir("a")'
        "source(Object...)" | "files(Object...)" | 'source("a", "b")'
    }

    @Unroll
    def "deprecation warning printed when deprecated order sensitivity is set via #method"() {
        buildFile << """
            task test {
                inputs.files([]).${call}
            }
        """
        executer.expectDeprecationWarning()
        executer.requireGradleDistribution()

        expect:
        succeeds "test"
        outputContains "The TaskInputFilePropertyBuilder.${method} method has been deprecated and is scheduled to be removed in Gradle 4.0."

        where:
        method                    | call
        "orderSensitive()"        | "orderSensitive()"
        "orderSensitive(boolean)" | "orderSensitive(true)"
    }

    def "deprecation warning printed when deprecated @OrderSensitivity annotation is used"() {
        buildFile << """
            class TaskWithOrderSensitiveProperty extends DefaultTask {
                @OrderSensitive @InputFiles def inputFiles = project.files()
                @TaskAction void action() {}
            }

            task test(type: TaskWithOrderSensitiveProperty) {
            }
        """

        executer.expectDeprecationWarning()
        // TODO:RG Temporary fix: it seems deprecation logs are not always forwarded correctly
        executer.requireGradleDistribution()

        when:
        succeeds "test"
        then:
        outputContains "The @OrderSensitive annotation has been deprecated and is scheduled to be removed in Gradle 4.0. For classpath properties, use the @Classpath annotation instead."
    }

    def "no deprecation warning printed when @Classpath annotation is used"() {
        buildFile << """
            class TaskWithClasspathProperty extends DefaultTask {
                @Classpath @InputFiles def classpath = project.files()
                @TaskAction void action() {}
            }

            task test(type: TaskWithClasspathProperty) {
            }
        """

        expect:
        succeeds "test"
    }

    @Unroll
    def "deprecation warning printed when inputs calls are chained"() {
        buildFile << """
            task test {
                ${what}.${call}.${call}
            }
        """
        executer.expectDeprecationWarning()
        executer.requireGradleDistribution()

        expect:
        succeeds "test"
        outputContains "The chaining of the ${method} method has been deprecated and is scheduled to be removed in Gradle 4.0. " +
            "Please use the ${method} method on Task${what.capitalize()} directly instead."

        where:
        what     | method              | call
        "inputs" | "file(Object)"      | 'file("a")'
        "inputs" | "dir(Object)"       | 'dir("a")'
        "inputs" | "files(Object...)"  | 'files("a", "b")'
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
        succeeds "b" assertTasksExecuted ":a", ":b"
    }

    @Unroll("can use Enum from buildSrc as input property - flushCaches: #flushCaches taskType: #taskType")
    @Issue("GRADLE-3537")
    def "can use Enum from buildSrc as input property"() {
        given:
        file("buildSrc/src/main/java/org/gradle/MessageType.java") << """
            package org.gradle;

            public enum MessageType {
                HELLO_WORLD
            }
        """
        file("buildSrc/src/main/java/org/gradle/MyTask.java") << """
            package org.gradle;

            public class MyTask extends org.gradle.api.DefaultTask {

            }
        """

        buildFile << """
            import org.gradle.MessageType
            import org.gradle.api.internal.changedetection.state.InMemoryTaskArtifactCache

            if(project.hasProperty('flushCaches')) {
                println "Flushing InMemoryTaskArtifactCache"
                gradle.taskGraph.whenReady {
                    gradle.services.get(InMemoryTaskArtifactCache).invalidateAll()
                }
            }
"""
        def taskDefinitionPart = """
            task createFile(type: $taskType) {
                ext.messageType = MessageType.HELLO_WORLD
                ext.outputFile = file('output.txt')
                inputs.property('messageType', messageType)
                outputs.file(outputFile)

                doLast {
                    outputFile << messageType
                }
            }
"""
        if (taskType == 'MyScriptPluginTask') {
            buildFile << """
apply from:'scriptPlugin.gradle'
"""
            file("scriptPlugin.gradle") << taskDefinitionPart
            file("scriptPlugin.gradle") << "class $taskType extends DefaultTask {}\n"
        } else {
            buildFile << taskDefinitionPart
            if (taskType == 'MyBuildScriptTask') {
                buildFile << "class $taskType extends DefaultTask {}\n"
            }
        }

        when:
        succeeds 'createFile'

        then:
        executedTasks == [':createFile']
        skippedTasks.empty

        when:
        if (flushCaches) {
            executer.withArgument('-PflushCaches')
        }
        succeeds 'createFile'

        then:
        executedTasks == [':createFile']
        skippedTasks == [':createFile'] as Set

        where:
        [flushCaches, taskType] << [[false, true], ['DefaultTask', 'org.gradle.MyTask', 'MyBuildScriptTask', 'MyScriptPluginTask']].combinations()
    }


    @Unroll
    @Issue("gradle/gradle#784")
    def "can use a custom Serializable type from build script as input property in a never custom Task"() {
        given:
        buildFile << """
            import org.gradle.api.internal.changedetection.state.InMemoryTaskArtifactCache

            gradle.taskGraph.whenReady {
                gradle.services.get(InMemoryTaskArtifactCache).invalidateAll()
            }

            $fooTypeDefinition

            class MyTask extends DefaultTask {
                @Input
                FooType foo

                @OutputFile
                File outputFile

                @TaskAction
                def doAction() {
                    outputFile << foo
                }
            }

            task createFile(type: MyTask) {
                outputFile = file('output.txt')
                foo = FooType.instance()
            }
"""

        when:
        succeeds 'createFile'

        then:
        executedTasks == [':createFile']
        skippedTasks.empty

        when:
        succeeds 'createFile'

        then:
        executedTasks == [':createFile']
        skippedTasks == [':createFile'] as Set

        where:
        fooTypeDefinition << [
            createFooTypeDefinitionAsEnum(),
            createFooTypeDefinitionAsSerializableClass(),
            createFooTypeDefinitionAsSerializableClassWithCustomEqualsReturning(true)
        ]
    }

    @Unroll
    @Issue("gradle/gradle#919")
    def "can use a custom Serializable type from init script as input property in this taskType: #taskType"() {
        def initScript = file("init.gradle")

        given:
        initScript << """
            $fooTypeDefinition

            rootProject {
                tasks.matching { it.name == "createFile" }.all {
                    ext.fooType = FooType.instance()
                    inputs.property('fooType', fooType)
                }
            }
"""

        buildFile << """
            import org.gradle.api.internal.changedetection.state.InMemoryTaskArtifactCache

            gradle.taskGraph.whenReady {
                gradle.services.get(InMemoryTaskArtifactCache).invalidateAll()
            }

            class MyTask extends DefaultTask {}

            task createFile(type: $taskType) {
                ext.outputFile = file('output.txt')
                outputs.file(outputFile)

                doLast {
                    outputFile << "some data"
                }
            }
"""

        when:
        args "-I", initScript.absolutePath
        succeeds 'createFile'

        then:
        executedTasks == [':createFile']
        skippedTasks.empty

        when:
        args "-I", initScript.absolutePath
        succeeds 'createFile'

        then:
        executedTasks == [':createFile']
        skippedTasks == [':createFile'] as Set

        where:
        [taskType, fooTypeDefinition] << [['DefaultTask', 'MyTask'],[
                createFooTypeDefinitionAsEnum(),
                createFooTypeDefinitionAsSerializableClass(),
                createFooTypeDefinitionAsSerializableClassWithCustomEqualsReturning(true)
            ]].combinations()
    }

    @Issue("gradle/gradle#919")
    def "deprecation warning printed when using a custom Serializable type with diverging behavior between equals and object hash"() {
        given:
        buildFile << """
            ${createFooTypeDefinitionAsSerializableClassWithCustomEqualsReturning(false)}

            task createFile {
                ext.fooType = FooType.instance()
                inputs.property('fooType', fooType)
                ext.outputFile = file('output.txt')
                outputs.file(outputFile)

                doLast {
                    outputFile << "some data"
                }
            }
"""

        when:
        succeeds 'createFile'

        then:
        executedTasks == [':createFile']
        skippedTasks.empty

        when:
        executer.expectDeprecationWarning()
        succeeds 'createFile'

        then:
        executedTasks == [':createFile']
        skippedTasks.empty  // The equals implementation for custom type always return false
        outputContains "Custom equals implementation on task input properties has been deprecated and is scheduled to be removed in Gradle 4.0. " +
            "In the future, Gradle will be hashing the input property object and comparing this hash"
    }

    // Helper function that generate the FooType definition that can be used in the exact same way
    private static String createFooTypeDefinitionAsEnum() {
        return """
            |enum FooType {
            |   FOO
            |   static FooType instance() {
            |       return FOO
            |   }
            |}""".stripMargin()
    }
    private static String createFooTypeDefinitionAsSerializableClass() {
        return """
            |class FooType implements Serializable {
            |   static FooType instance() {
            |       return new FooType()
            |   }
            |}""".stripMargin()
    }
    private static String createFooTypeDefinitionAsSerializableClassWithCustomEqualsReturning(boolean equalsReturnValue) {
        return """
            |class FooType implements Serializable {
            |   static FooType instance() {
            |       return new FooType()
            |   }
            |
            |   @Override
            |   boolean equals(Object obj) {
            |       return $equalsReturnValue
            |   }
            |}""".stripMargin()
    }
}
