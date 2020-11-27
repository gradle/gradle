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
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.SetProperty
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.TestBuildCache
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.internal.Actions
import spock.lang.Issue
import spock.lang.Unroll

class TaskParametersIntegrationTest extends AbstractIntegrationSpec {

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

        when:
        fails "foo"
        then:
        failure.assertHasDescription("Execution failed for task ':foo'.")
        failure.assertHasCause("Unable to store input properties for task ':foo'. Property 'b' with value 'xxx' cannot be serialized.")
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
                @InputFiles def inputFiles = project.layout.files()

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
                @InputFiles def inputFiles = project.layout.files()
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

    def "fails when input file calls are chained (properties(Map))"() {
        buildFile << """
            task test {
                inputs.properties(input: 1).properties(input2: 2)
            }
        """

        expect:
        fails "test"
        failureCauseContains "Chaining of the TaskInputs.properties(Map) method is not supported since Gradle 5.0."
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
    def "task is out of date when property type changes #oldValue -> #newValue"() {
        buildFile << """
task someTask {
    inputs.property("a", $oldValue).optional(true)
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

    def "invalid task causes VFS to drop"() {
        buildFile << """
            class InvalidTask extends DefaultTask {
                @Optional @Input File inputFile

                @TaskAction void execute() {
                    println "Executed"
                }
            }

            task invalid(type: InvalidTask)
        """

        executer.expectDocumentedDeprecationWarning("Property 'inputFile' has @Input annotation used on property of type 'File'. " +
            "This behaviour has been deprecated and is scheduled to be removed in Gradle 7.0. " +
            "See https://docs.gradle.org/current/userguide/more_about_tasks.html#sec:up_to_date_checks for more details.")

        when:
        run "invalid", "--info"
        then:
        executedAndNotSkipped(":invalid")
        outputContains("Invalidating VFS because task ':invalid' failed validation")
    }

    @Unroll
    def "task can use input property of type #type"() {
        file("buildSrc/src/main/java/SomeTask.java") << """
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.Optional;
import java.io.File;

public class SomeTask extends DefaultTask {
    private $type v;
    @Input
    public $type getV() { return v; }
    void setV($type v) { this.v = v; }

    File d;
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
        if (expectedValidationProblem) {
            executer.beforeExecute { executer.expectDocumentedDeprecationWarning(expectedValidationProblem) }
        }

        given:
        succeeds "someTask"

        when:
        run "someTask"

        then:
        if (expectedValidationProblem) {
            executedAndNotSkipped(":someTask")
        } else {
            skipped(":someTask")
        }

        when:
        buildFile.replace("v = $initialValue", "v = $newValue")
        executer.withArgument("--info")
        run "someTask"

        then:
        executedAndNotSkipped(":someTask")
        if (expectedValidationProblem) {
            outputContains("Validation failed.")
        } else {
            outputContains("Value of input property 'v' has changed for task ':someTask'")
        }

        when:
        run "someTask"

        then:
        if (expectedValidationProblem) {
            executedAndNotSkipped(":someTask")
        } else {
            skipped(":someTask")
        }

        where:
        type                                  | initialValue                                          | newValue                                                     | expectedValidationProblem
        "String"                              | "'value 1'"                                           | "'value 2'"                                                  | null
        "java.io.File"                        | "file('file1')"                                       | "file('file2')"                                              | "Property 'v' has @Input annotation used on property of type 'File'. This behaviour has been deprecated and is scheduled to be removed in Gradle 7.0. See https://docs.gradle.org/current/userguide/more_about_tasks.html#sec:up_to_date_checks for more details."
        "boolean"                             | "true"                                                | "false"                                                      | null
        "Boolean"                             | "Boolean.TRUE"                                        | "Boolean.FALSE"                                              | null
        "int"                                 | "123"                                                 | "-45"                                                        | null
        "Integer"                             | "123"                                                 | "-45"                                                        | null
        "long"                                | "123"                                                 | "-45"                                                        | null
        "Long"                                | "123"                                                 | "-45"                                                        | null
        "short"                               | "123"                                                 | "-45"                                                        | null
        "Short"                               | "123"                                                 | "-45"                                                        | null
        "java.math.BigDecimal"                | "12.3"                                                | "-45.432"                                                    | null
        "java.math.BigInteger"                | "12"                                                  | "-45"                                                        | null
        "java.util.List<String>"              | "['value1', 'value2']"                                | "['value1']"                                                 | null
        "java.util.List<String>"              | "[]"                                                  | "['value1', null, false, 123, 12.4, ['abc'], [true] as Set]" | null
        "String[]"                            | "new String[0]"                                       | "['abc'] as String[]"                                        | null
        "Object[]"                            | "[123, 'abc'] as Object[]"                            | "['abc'] as String[]"                                        | null
        "java.util.Collection<String>"        | "['value1', 'value2']"                                | "['value1'] as SortedSet"                                    | null
        "java.util.Set<String>"               | "['value1', 'value2'] as Set"                         | "['value1'] as Set"                                          | null
        "Iterable<java.io.File>"              | "[file('1'), file('2')] as Set"                       | "files('1')"                                                 | null
        FileCollection.name                   | "files('1', '2')"                                     | "configurations.create('empty')"                             | "Property 'v' has @Input annotation used on property of type 'FileCollection'. This behaviour has been deprecated and is scheduled to be removed in Gradle 7.0. See https://docs.gradle.org/current/userguide/more_about_tasks.html#sec:up_to_date_checks for more details."
        "java.util.Map<String, Boolean>"      | "[a: true, b: false]"                                 | "[a: true, b: true]"                                         | null
        "${Provider.name}<String>"            | "providers.provider { 'a' }"                          | "providers.provider { 'b' }"                                 | null
        "${Property.name}<String>"            | "objects.property(String); v.set('abc')"              | "objects.property(String); v.set('123')"                     | "Property 'v' of mutable type 'org.gradle.api.provider.Property' is writable. Properties of this type should be read-only and mutated via the value itself. This behaviour has been deprecated and is scheduled to be removed in Gradle 7.0. See https://docs.gradle.org/current/userguide/more_about_tasks.html#sec:up_to_date_checks for more details."
        "${ListProperty.name}<String>"        | "objects.listProperty(String); v.set(['abc'])"        | "objects.listProperty(String); v.set(['123'])"               | null
        "${SetProperty.name}<String>"         | "objects.setProperty(String); v.set(['abc'])"         | "objects.setProperty(String); v.set(['123'])"                | null
        "${MapProperty.name}<String, Number>" | "objects.mapProperty(String, Number); v.set([a: 12])" | "objects.mapProperty(String, Number); v.set([a: 10])"        | null
    }

    def "null input properties registered via TaskInputs.property are not allowed"() {
        buildFile << """
            task test {
                inputs.property("input", { null })
                doLast {}
            }
        """
        expect:
        fails "test"
        failure.assertHasDescription("A problem was found with the configuration of task ':test' (type 'DefaultTask').")
        failure.assertHasCause("No value has been specified for property 'input'.")
    }

    def "optional null input properties registered via TaskInputs.property are allowed"() {
        buildFile << """
            task test {
                inputs.property("input", { null }).optional(true)
                doLast {}
            }
        """
        expect:
        succeeds "test"
    }

    @Unroll
    def "null input files registered via TaskInputs.#method are not allowed"() {
        buildFile << """
            task test {
                inputs.${method}({ null }) withPropertyName "input"
                doLast {}
            }
        """
        expect:
        fails "test"
        failure.assertHasDescription("A problem was found with the configuration of task ':test' (type 'DefaultTask').")
        failure.assertHasCause("No value has been specified for property 'input'.")

        where:
        method << ["file", "files", "dir"]
    }

    @Unroll
    def "optional null input files registered via TaskInputs.#method are allowed"() {
        buildFile << """
            task test {
                inputs.${method}({ null }) withPropertyName "input" optional(true)
                doLast {}
            }
        """
        expect:
        succeeds "test"

        where:
        method << ["file", "files", "dir"]
    }

    @Unroll
    def "null output files registered via TaskOutputs.#method are not allowed"() {
        buildFile << """
            task test {
                outputs.${method}({ null }) withPropertyName "output"
                doLast {}
            }
        """
        expect:
        fails "test"
        failure.assertHasDescription("A problem was found with the configuration of task ':test' (type 'DefaultTask').")
        failure.assertHasCause("No value has been specified for property 'output'.")

        where:
        method << ["file", "files", "dir", "dirs"]
    }

    @Unroll
    def "optional null output files registered via TaskOutputs.#method are allowed"() {
        buildFile << """
            task test {
                outputs.${method}({ null }) withPropertyName "output" optional(true)
                doLast {}
            }
        """
        expect:
        succeeds "test"

        where:
        method << ["file", "files", "dir", "dirs"]
    }

    @Unroll
    def "missing input files registered via TaskInputs.#method are not allowed"() {
        buildFile << """
            task test {
                inputs.${method}({ "missing" }) withPropertyName "input"
                doLast {}
            }
        """

        expect:
        fails "test"
        failure.assertHasDescription("A problem was found with the configuration of task ':test' (type 'DefaultTask').")
        failure.assertHasCause("$type '${file("missing")}' specified for property 'input' does not exist.")

        where:
        method | type
        "file" | "File"
        "dir"  | "Directory"
    }

    @Unroll
    def "wrong input file type registered via TaskInputs.#method is not allowed"() {
        file("input-file.txt").touch()
        file("input-dir").createDir()
        buildFile << """
            task test {
                inputs.${method}({ "$path" }) withPropertyName "input"
                doLast {}
            }
        """

        expect:
        fails "test"
        failure.assertHasDescription("A problem was found with the configuration of task ':test' (type 'DefaultTask').")
        failure.assertHasCause("${type.capitalize()} '${file(path)}' specified for property 'input' is not a $type.")

        where:
        method | path             | type
        "file" | "input-dir"      | "file"
        "dir"  | "input-file.txt" | "directory"
    }

    @Unroll
    def "wrong output file type registered via TaskOutputs.#method is not allowed"() {
        file("output-file.txt").touch()
        file("output-dir").createDir()
        buildFile << """
            task test {
                outputs.${method}({ "$path" }) withPropertyName "output"
                doLast {}
            }
        """

        expect:
        fails "test"
        failure.assertHasCause(message.replace("<PATH>", file(path).absolutePath))

        where:
        method  | path              | message
        "file"  | "output-dir"      | "Cannot write to file '<PATH>' specified for property 'output' as it is a directory."
        "files" | "output-dir"      | "Cannot write to file '<PATH>' specified for property 'output' as it is a directory."
        "dir"   | "output-file.txt" | "Directory '<PATH>' specified for property 'output' is not a directory."
        "dirs"  | "output-file.txt" | "Directory '<PATH>' specified for property 'output' is not a directory."
    }

    def "can specify null as an input property in ad-hoc task"() {
        buildFile << """
            task foo {
                inputs.property("a", null).optional(true)
                doLast {}
            }
        """

        expect:
        succeeds "foo"
    }

    @Issue("https://github.com/gradle/gradle/issues/3366")
    def "can specify null as an input property in Java task"() {
        file("buildSrc/src/main/java/Foo.java") << """
            import org.gradle.api.DefaultTask;
            import org.gradle.api.tasks.TaskAction;

            public class Foo extends DefaultTask {
                public Foo() {
                    getInputs().property("a", null).optional(true);
                }

                @TaskAction
                public void doSomething() {}
            }
        """

        buildFile << """
            task foo(type: Foo)
        """

        expect:
        succeeds "foo"
    }

    def "input and output properties are not evaluated too often"() {
        buildFile << """
            import org.gradle.api.services.BuildServiceParameters

            abstract class EvaluationCountBuildService implements BuildService<BuildServiceParameters.None> {
                int outputFileCount = 0
                int inputFileCount = 0
                int inputValueCount = 0
                int nestedInputCount = 0
                int nestedInputValueCount = 0

                void outputFile() {
                    outputFileCount++
                }

                void inputFile() {
                    inputFileCount++
                }

                void inputValue() {
                    inputValueCount++
                }

                void nestedInput() {
                    nestedInputCount++
                }

                void nestedInputValue() {
                    nestedInputValueCount++
                }
            }
            def evaluationCount = project.getGradle().getSharedServices().registerIfAbsent("evaluationCount", EvaluationCountBuildService) {}

            @CacheableTask
            abstract class CustomTask extends DefaultTask {

                @Inject
                abstract ProjectLayout getLayout()

                @Internal
                abstract Property<EvaluationCountBuildService> getEvaluationCountService()

                private NestedBean bean = new NestedBean()

                @OutputFile
                File getOutputFile() {
                    count("outputFile")
                    return layout.buildDirectory.file("foo.bar").get().asFile
                }

                @InputFile
                @PathSensitive(PathSensitivity.NONE)
                File getInputFile() {
                    count("inputFile")
                    return layout.projectDirectory.file("input.txt").asFile
                }

                @Input
                String getInput() {
                    count("inputValue")
                    return "Input"
                }

                @Nested
                Object getBean() {
                    count("nestedInput")
                    return bean
                }

                @TaskAction
                void doStuff() {
                    outputFile.text = inputFile.text
                }

                void count(String name) {
                    def service = evaluationCountService.get()
                    service."\${name}"()
                    def currentValue = service."\${name}Count"
                    println "Evaluating \${name} \${currentValue}"
                }

                class NestedBean {
                    @Input getFirst() {
                        count("nestedInputValue")
                        return "first"
                    }

                    @Input getSecond() {
                        return "second"
                    }
                }
            }

            task myTask(type: CustomTask) {
                evaluationCountService = evaluationCount
            }

            task assertInputCounts {
                dependsOn myTask
                def propertyNames = ['outputFileCount', 'inputFileCount', 'inputValueCount', 'nestedInputCount', 'nestedInputValueCount']
                def gradleProperties = propertyNames.collectEntries { [(it) : providers.gradleProperty(it)]}
                doLast {
                    ['outputFileCount', 'inputFileCount', 'inputValueCount', 'nestedInputCount', 'nestedInputValueCount'].each { name ->
                        assert evaluationCount.get()."\$name" == gradleProperties[name].get() as Integer
                    }
                }
            }
        """
        def inputFile = file('input.txt')
        inputFile.text = "input"
        def expectedCounts = [inputFile: 3, outputFile: 2, nestedInput: 2, inputValue: 1, nestedInputValue: 1]
        def expectedIncrementalCounts = GradleContextualExecuter.configCache ?
            [inputFile: 2, outputFile: 2, nestedInput: 1, inputValue: 1, nestedInputValue: 1]
            : expectedCounts
        def expectedUpToDateCounts = GradleContextualExecuter.configCache ?
            [inputFile: 1, outputFile: 1, nestedInput: 1, inputValue: 1, nestedInputValue: 1]
            : [inputFile: 2, outputFile: 1, nestedInput: 2, inputValue: 1, nestedInputValue: 1]
        def arguments = ["assertInputCounts"] + expectedCounts.collect { name, count -> "-P${name}Count=${count}" }
        def incrementalBuildArguments = ["assertInputCounts"] + expectedIncrementalCounts.collect { name, count -> "-P${name}Count=${count}" }
        def upToDateArguments = ["assertInputCounts"] + expectedUpToDateCounts.collect { name, count -> "-P${name}Count=${count}" }
        def localCache = new TestBuildCache(file('cache-dir'))
        settingsFile << localCache.localCacheConfiguration()

        expect:
        succeeds(*arguments)
        executedAndNotSkipped(':myTask')

        when:
        inputFile.text = "changed"
        then:
        withBuildCache().succeeds(*incrementalBuildArguments)
        executedAndNotSkipped(':myTask')
        and:
        succeeds(*upToDateArguments)
        skipped(':myTask')

        when:
        file('build').deleteDir()
        then:
        withBuildCache().succeeds(*upToDateArguments)
        skipped(':myTask')
    }
}
