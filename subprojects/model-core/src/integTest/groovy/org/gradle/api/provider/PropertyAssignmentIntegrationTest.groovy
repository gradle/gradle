/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.provider

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class PropertyAssignmentIntegrationTest extends AbstractIntegrationSpec {

    def "test Groovy object types assignment for #description"() {
        given:
        def buildFileDefinition = """
            enum MyEnum {
                YES, NO
            }

            class MyObject {
                private String value
                public MyObject(String value) {
                    this.value = value
                }
                public String toString() {
                    return value
                }
            }

            abstract class MyTask extends DefaultTask {
                @Internal
                {inputDeclaration}

                @TaskAction
                void run() {
                    if (input instanceof DirectoryProperty) {
                        println("Result: " + input.get().asFile.name)
                    } else if (input instanceof File) {
                       println("Result: " + input.name)
                    } else if (input instanceof Property) {
                        println("Result: " + input.get())
                    } else {
                        println("Result: " + input)
                    }
                }
            }

            tasks.register("myTask", MyTask) {
                input = {inputValue}
            }
        """

        when:
        buildFile.text = buildFileDefinition
            .replace("{inputDeclaration}", "$eagerInputType input")
            .replace("{inputValue}", inputValue)

        then:
        runAndAssert("myTask", eagerResult)

        when:
        buildFile.text = buildFileDefinition
            .replace("{inputDeclaration}", "abstract $lazyInputType getInput()")
            .replace("{inputValue}", inputValue)

        then:
        runAndAssert("myTask", lazyResult)

        where:
        description                                     | eagerInputType | lazyInputType       | inputValue                               | eagerResult                          | lazyResult
        "T = T"                                         | "String"       | "Property<String>"  | '"hello"'                                | "Result: hello"                      | "Result: hello"
        "T = Provider<T>"                               | "String"       | "Property<String>"  | 'provider { \"hello\" }'                 | "Result: provider(?)"                | "Result: hello"
        "String = Object"                               | "String"       | "Property<String>"  | 'new MyObject("hello")'                  | "Result: hello"                      | failsWithCause("Cannot set the value of task ':myTask' property 'input'")
        "Enum = String"                                 | "MyEnum"       | "Property<MyEnum>"  | '"YES"'                                  | "Result: YES"                        | failsWithCause("Cannot set the value of task ':myTask' property 'input'")
        "File = T extends FileSystemLocation"           | "File"         | "DirectoryProperty" | 'layout.buildDirectory.dir("out").get()' | failsWithCause("Cannot cast object") | "Result: out"
        "File = Provider<T extends FileSystemLocation>" | "File"         | "DirectoryProperty" | 'layout.buildDirectory.dir("out")'       | failsWithCause("Cannot cast object") | "Result: out"
        "File = File"                                   | "File"         | "DirectoryProperty" | 'file("$buildDir/out")'                  | "Result: out"                        | "Result: out"
        "File = Provider<File>"                         | "File"         | "DirectoryProperty" | 'provider { file("$buildDir/out") }'     | failsWithCause("Cannot cast object") | failsWithCause("Cannot get the value of task ':myTask' property 'input'")
        "File = Object"                                 | "File"         | "DirectoryProperty" | 'new MyObject("out")'                    | failsWithCause("Cannot cast object") | failsWithCause("Cannot set the value of task ':myTask' property 'input'")
    }

    def "test Kotlin object types assignment for #description"() {
        given:
        file("gradle.properties") << "\nsystemProp.org.gradle.unsafe.kotlin.assignment=true\n"
        def buildFileDefinition = """
            enum class MyEnum {
                YES, NO
            }

            class MyObject(val value: String) {
                override fun toString() = value
            }

            abstract class MyTask : DefaultTask() {
                @get:Internal
                {inputDeclaration}

                @TaskAction
                fun run() {
                    when (val anyInput = input as Any?) {
                        is DirectoryProperty -> println("Result: " + anyInput.get().asFile.name)
                        is File -> println("Result: " + anyInput.name)
                        is Property<*> -> println("Result: " + anyInput.get())
                        else -> println("Result: " + anyInput)
                    }
                }
            }

            tasks.register<MyTask>("myTask") {
                input = {inputValue}
            }
        """

        when:
        buildKotlinFile.text = buildFileDefinition
            .replace("{inputDeclaration}", "var input: $eagerInputType? = null")
            .replace("{inputValue}", inputValue)

        then:
        runAndAssert("myTask", eagerResult)

        when:
        buildKotlinFile.text = buildFileDefinition
            .replace("{inputDeclaration}", "abstract val input: $lazyInputType")
            .replace("{inputValue}", inputValue)

        then:
        runAndAssert("myTask", lazyResult)

        where:
        description                                     | eagerInputType | lazyInputType       | inputValue                               | eagerResult                           | lazyResult
        "T = T"                                         | "String"       | "Property<String>"  | '"hello"'                                | "Result: hello"                       | "Result: hello"
        "T = Provider<T>"                               | "String"       | "Property<String>"  | 'provider { \"hello\" }'                 | failsWithDescription("Type mismatch") | "Result: hello"
        "String = Object"                               | "String"       | "Property<String>"  | 'MyObject("hello")'                      | failsWithDescription("Type mismatch") | failsWithDescription("Type mismatch")
        "Enum = String"                                 | "MyEnum"       | "Property<MyEnum>"  | '"YES"'                                  | failsWithDescription("Type mismatch") | failsWithDescription("Type mismatch")
        "File = T extends FileSystemLocation"           | "File"         | "DirectoryProperty" | 'layout.buildDirectory.dir("out").get()' | failsWithDescription("Type mismatch") | "Result: out"
        "File = Provider<T extends FileSystemLocation>" | "File"         | "DirectoryProperty" | 'layout.buildDirectory.dir("out")'       | failsWithDescription("Type mismatch") | "Result: out"
        "File = File"                                   | "File"         | "DirectoryProperty" | 'file("$buildDir/out")'                  | "Result: out"                         | "Result: out"
        "File = Provider<File>"                         | "File"         | "DirectoryProperty" | 'provider { file("$buildDir/out") }'     | failsWithDescription("Type mismatch") | "Result: out"
        "File = Object"                                 | "File"         | "DirectoryProperty" | 'MyObject("out")'                        | failsWithDescription("Type mismatch") | failsWithDescription("Type mismatch")
    }

    def "test Groovy collection types assignment for #description"() {
        given:
        def buildFileDefinition = """
            abstract class MyTask extends DefaultTask {
                @Internal
                {inputDeclaration}

                @TaskAction
                void run() {
                    if (input instanceof Provider) {
                        println("Result: " + input.get())
                    } else {
                        println("Result: " + input)
                    }
                }
            }

            tasks.register("myTask", MyTask) {
                input $operation {inputValue}
            }
        """

        when:
        def defaultEagerInputValue = eagerInputType.contains("Map<") ? "[:]" : "[]"
        buildFile.text = buildFileDefinition
            .replace("{inputDeclaration}", "$eagerInputType input = $defaultEagerInputValue")
            .replace("{inputValue}", inputValue)

        then:
        runAndAssert("myTask", eagerResult)

        when:
        buildFile.text = buildFileDefinition
            .replace("{inputDeclaration}", "abstract $lazyInputType getInput()")
            .replace("{inputValue}", inputValue)

        then:
        runAndAssert("myTask", lazyResult)

        where:
        description                              | operation | eagerInputType        | lazyInputType                 | inputValue                               | eagerResult                              | lazyResult
        "Collection<T> = T[]"                    | "="       | "List<String>"        | "ListProperty<String>"        | '["a"] as String[]'                      | 'Result: [a]'                            | failsWithCause("Cannot set the value of a property of type java.util.List using an instance of type [Ljava.lang.String;")
        "Collection<T> = Iterable<T>"            | "="       | "List<String>"        | "ListProperty<String>"        | '["a"] as Iterable<String>'              | 'Result: [a]'                            | 'Result: [a]'
        "Collection<T> = Provider<Iterable<T>>"  | "="       | "List<String>"        | "ListProperty<String>"        | 'provider { ["a"] as Iterable<String> }' | failsWithCause("Cannot cast object")     | 'Result: [a]'
        "Collection<T> += T"                     | "+="      | "List<String>"        | "ListProperty<String>"        | '"a"'                                    | 'Result: [a]'                            | failsWithCause("No signature of method")
        "Collection<T> << T"                     | "<<"      | "List<String>"        | "ListProperty<String>"        | '"a"'                                    | 'Result: [a]'                            | failsWithCause("No signature of method")
        "Collection<T> += Provider<T>"           | "+="      | "List<String>"        | "ListProperty<String>"        | 'provider { "a" }'                       | 'Result: [provider(?)]'                  | failsWithCause("No signature of method")
        "Collection<T> << Provider<T>"           | "<<"      | "List<String>"        | "ListProperty<String>"        | 'provider { "a" }'                       | 'Result: [provider(?)]'                  | failsWithCause("No signature of method")
        "Collection<T> += T[]"                   | "+="      | "List<String>"        | "ListProperty<String>"        | '["a"] as String[]'                      | 'Result: [[a]]'                          | failsWithCause("No signature of method")
        "Collection<T> << T[]"                   | "<<"      | "List<String>"        | "ListProperty<String>"        | '["a"] as String[]'                      | 'Result: [[a]]'                          | failsWithCause("No signature of method")
        "Collection<T> += Iterable<T>"           | "+="      | "List<String>"        | "ListProperty<String>"        | '["a"] as Iterable<String>'              | 'Result: [a]'                            | failsWithCause("No signature of method")
        "Collection<T> << Iterable<T>"           | "<<"      | "List<String>"        | "ListProperty<String>"        | '["a"] as Iterable<String>'              | 'Result: [[a]]'                          | failsWithCause("No signature of method")
        "Collection<T> += Provider<Iterable<T>>" | "+="      | "List<String>"        | "ListProperty<String>"        | 'provider { ["a"] as Iterable<String> }' | 'Result: [provider(?)]'                  | failsWithCause("No signature of method")
        "Collection<T> << Provider<Iterable<T>>" | "<<"      | "List<String>"        | "ListProperty<String>"        | 'provider { ["a"] as Iterable<String> }' | 'Result: [provider(?)]'                  | failsWithCause("No signature of method")
        "Map<K, V> = Map<K, V>"                  | "="       | "Map<String, String>" | "MapProperty<String, String>" | '["a": "b"]'                             | 'Result: [a:b]'                          | 'Result: [a:b]'
        "Map<K, V> = Provider<Map<K, V>>"        | "="       | "Map<String, String>" | "MapProperty<String, String>" | 'provider { ["a": "b"] }'                | failsWithCause("Cannot cast object")     | 'Result: [a:b]'
        "Map<K, V> += Map<K, V>"                 | "+="      | "Map<String, String>" | "MapProperty<String, String>" | '["a": "b"]'                             | 'Result: [a:b]'                          | failsWithCause("No signature of method")
        "Map<K, V> << Map<K, V>"                 | "<<"      | "Map<String, String>" | "MapProperty<String, String>" | '["a": "b"]'                             | 'Result: [a:b]'                          | failsWithCause("No signature of method")
        "Map<K, V> += Provider<Map<K, V>>"       | "+="      | "Map<String, String>" | "MapProperty<String, String>" | 'provider { ["a": "b"] }'                | failsWithCause("No signature of method") | failsWithCause("No signature of method")
        "Map<K, V> << Provider<Map<K, V>>"       | "<<"      | "Map<String, String>" | "MapProperty<String, String>" | 'provider { ["a": "b"] }'                | failsWithCause("No signature of method") | failsWithCause("No signature of method")
    }

    def "test Kotlin collection types assignment for #description"() {
        given:
        file("gradle.properties") << "\nsystemProp.org.gradle.unsafe.kotlin.assignment=true\n"
        def buildFileDefinition = """
            abstract class MyTask : DefaultTask() {
                @get:Internal
                {inputDeclaration}

                @TaskAction
                fun run() {
                    when (val anyInput = input as Any?) {
                        is Provider<*> -> println("Result: " + anyInput.get())
                        else -> println("Result: " + anyInput)
                    }
                }
            }

            tasks.register<MyTask>("myTask") {
                input $operation {inputValue}
            }
        """

        when:
        def defaultEagerInputValue = eagerInputType.contains("Map<") ? "mutableMapOf<String, String>()" : "mutableListOf<String>()"
        buildKotlinFile.text = buildFileDefinition
            .replace("{inputDeclaration}", "var input: $eagerInputType = $defaultEagerInputValue")
            .replace("{inputValue}", inputValue)

        then:
        runAndAssert("myTask", eagerResult)

        when:
        buildKotlinFile.text = buildFileDefinition
            .replace("{inputDeclaration}", "abstract val input: $lazyInputType")
            .replace("{inputValue}", inputValue)

        then:
        runAndAssert("myTask", lazyResult)

        where:
        description                              | operation | eagerInputType               | lazyInputType                 | inputValue                                     | eagerResult                                   | lazyResult
        "Collection<T> = T[]"                    | "="       | "List<String>"               | "ListProperty<String>"        | 'arrayOf("a")'                                 | failsWithDescription("Type mismatch")         | failsWithDescription("No applicable 'assign' function found for '=' overload")
        "Collection<T> = Iterable<T>"            | "="       | "List<String>"               | "ListProperty<String>"        | 'listOf("a") as Iterable<String>'              | failsWithDescription("Type mismatch")         | 'Result: [a]'
        "Collection<T> = Provider<Iterable<T>>"  | "="       | "List<String>"               | "ListProperty<String>"        | 'provider { listOf("a") as Iterable<String> }' | failsWithDescription("Type mismatch")         | 'Result: [a]'
        "Collection<T> += T"                     | "+="      | "MutableList<String>"        | "ListProperty<String>"        | '"a"'                                          | 'Result: [a]'                                 | failsWithDescription("Unresolved reference. None of the following candidates is applicable because of receiver type mismatch:")
        "Collection<T> += Provider<T>"           | "+="      | "MutableList<String>"        | "ListProperty<String>"        | 'provider { "a" }'                             | failsWithDescription("Type mismatch")         | failsWithDescription("Unresolved reference. None of the following candidates is applicable because of receiver type mismatch:")
        "Collection<T> += T[]"                   | "+="      | "MutableList<String>"        | "ListProperty<String>"        | 'arrayOf("a")'                                 | 'Result: [a]'                                 | failsWithDescription("Unresolved reference. None of the following candidates is applicable because of receiver type mismatch:")
        "Collection<T> += Iterable<T>"           | "+="      | "MutableList<String>"        | "ListProperty<String>"        | 'listOf("a") as Iterable<String>'              | 'Result: [a]'                                 | failsWithDescription("Unresolved reference. None of the following candidates is applicable because of receiver type mismatch:")
        "Collection<T> += Provider<Iterable<T>>" | "+="      | "MutableList<String>"        | "ListProperty<String>"        | 'provider { listOf("a") as Iterable<String> }' | failsWithDescription("Type mismatch")         | failsWithDescription("Unresolved reference. None of the following candidates is applicable because of receiver type mismatch:")
        "Map<K, V> = Map<K, V>"                  | "="       | "Map<String, String>"        | "MapProperty<String, String>" | 'mapOf("a" to "b")'                            | 'Result: {a=b}'                               | 'Result: {a=b}'
        "Map<K, V> = Provider<Map<K, V>>"        | "="       | "Map<String, String>"        | "MapProperty<String, String>" | 'provider { mapOf("a" to "b") }'               | failsWithDescription("Type mismatch")         | 'Result: {a=b}'
        "Map<K, V> += Pair<K, V>"                | "+="      | "MutableMap<String, String>" | "MapProperty<String, String>" | '"a" to "b"'                                   | 'Result: {a=b}'                               | failsWithDescription("Unresolved reference. None of the following candidates is applicable because of receiver type mismatch:")
        "Map<K, V> += Provider<Pair<K, V>>"      | "+="      | "MutableMap<String, String>" | "MapProperty<String, String>" | 'provider { "a" to "b" }'                      | failsWithDescription("None of the following") | failsWithDescription("Unresolved reference. None of the following candidates is applicable because of receiver type mismatch:")
        "Map<K, V> += Map<K, V>"                 | "+="      | "MutableMap<String, String>" | "MapProperty<String, String>" | 'mapOf("a" to "b")'                            | 'Result: {a=b}'                               | failsWithDescription("Unresolved reference. None of the following candidates is applicable because of receiver type mismatch:")
        "Map<K, V> += Provider<Map<K, V>>"       | "+="      | "MutableMap<String, String>" | "MapProperty<String, String>" | 'provider { mapOf("a" to "b") }'               | failsWithDescription("None of the following") | failsWithDescription("Unresolved reference. None of the following candidates is applicable because of receiver type mismatch:")
    }

    def "test Groovy FileCollection types assignment for #description"() {
        given:
        def buildFileDefinition = """
            abstract class MyTask extends DefaultTask {
                @Internal
                {inputDeclaration}

                @TaskAction
                void run() {
                    println("Result: " + input.files.collect { it.name })
                }
            }

            tasks.register("myTask", MyTask) {
                input $operation {inputValue}
            }
        """

        when:
        buildFile.text = buildFileDefinition
            .replace("{inputDeclaration}", "$eagerInputType input = project.files() as $eagerInputType")
            .replace("{inputValue}", inputValue)

        then:
        runAndAssert("myTask", eagerResult)

        when:
        buildFile.text = buildFileDefinition
            .replace("{inputDeclaration}", "abstract $lazyInputType getInput()")
            .replace("{inputValue}", inputValue)

        then:
        runAndAssert("myTask", lazyResult)

        where:
        description                        | operation | eagerInputType   | lazyInputType                | inputValue        | eagerResult                              | lazyResult
        "FileCollection = FileCollection"  | "="       | "FileCollection" | "ConfigurableFileCollection" | 'files("a.txt")'  | 'Result: [a.txt]'                        | failsWithCause("Cannot set the value of read-only property 'input' for task ':myTask' of type MyTask.")
        "FileCollection = Object"          | "="       | "FileCollection" | "ConfigurableFileCollection" | '"a.txt"'         | failsWithCause("Cannot cast object")     | failsWithCause("Cannot set the value of read-only property 'input' for task ':myTask' of type MyTask.")
        "FileCollection = File"            | "="       | "FileCollection" | "ConfigurableFileCollection" | 'file("a.txt")'   | failsWithCause("Cannot cast object")     | failsWithCause("Cannot set the value of read-only property 'input' for task ':myTask' of type MyTask.")
        "FileCollection = Iterable<File>"  | "="       | "FileCollection" | "ConfigurableFileCollection" | '[file("a.txt")]' | failsWithCause("Cannot cast object")     | failsWithCause("Cannot set the value of read-only property 'input' for task ':myTask' of type MyTask.")
        "FileCollection += FileCollection" | "+="      | "FileCollection" | "ConfigurableFileCollection" | 'files("a.txt")'  | 'Result: [a.txt]'                        | failsWithCause("Cannot set the value of read-only property 'input' for task ':myTask' of type MyTask.")
        "FileCollection << FileCollection" | "<<"      | "FileCollection" | "ConfigurableFileCollection" | 'files("a.txt")'  | failsWithCause("No signature of method") | failsWithCause("No signature of method")
        "FileCollection += Object"         | "+="      | "FileCollection" | "ConfigurableFileCollection" | '"a.txt"'         | failsWithCause("Cannot cast object")     | failsWithCause("Cannot set the value of read-only property 'input' for task ':myTask' of type MyTask.")
        "FileCollection += File"           | "+="      | "FileCollection" | "ConfigurableFileCollection" | 'file("a.txt")'   | failsWithCause("Cannot cast object")     | failsWithCause("Cannot set the value of read-only property 'input' for task ':myTask' of type MyTask.")
        "FileCollection += Iterable<?>"    | "+="      | "FileCollection" | "ConfigurableFileCollection" | '["a.txt"]'       | failsWithCause("Cannot cast object")     | failsWithCause("Cannot set the value of read-only property 'input' for task ':myTask' of type MyTask.")
        "FileCollection += Iterable<File>" | "+="      | "FileCollection" | "ConfigurableFileCollection" | '[file("a.txt")]' | failsWithCause("Cannot cast object")     | failsWithCause("Cannot set the value of read-only property 'input' for task ':myTask' of type MyTask.")
    }

    def "test Kotlin FileCollection types assignment for #description"() {
        given:
        file("gradle.properties") << "\nsystemProp.org.gradle.unsafe.kotlin.assignment=true\n"
        def buildFileDefinition = """
            abstract class MyTask : DefaultTask() {
                @get:Internal
                {inputDeclaration}

                @TaskAction
                fun run() {
                    println("Result: " + input.files.map { it.name })
                }
            }

            tasks.register<MyTask>("myTask") {
                input $operation {inputValue}
            }
        """

        when:
        buildKotlinFile.text = buildFileDefinition
            .replace("{inputDeclaration}", "var input: $eagerInputType = project.files()")
            .replace("{inputValue}", inputValue)

        then:
        runAndAssert("myTask", eagerResult)

        when:
        buildKotlinFile.text = buildFileDefinition
            .replace("{inputDeclaration}", "abstract val input: $lazyInputType")
            .replace("{inputValue}", inputValue)

        then:
        runAndAssert("myTask", lazyResult)

        where:
        description                        | operation | eagerInputType   | lazyInputType                | inputValue                         | eagerResult                           | lazyResult
        "FileCollection = FileCollection"  | "="       | "FileCollection" | "ConfigurableFileCollection" | 'files("a.txt") as FileCollection' | 'Result: [a.txt]'                     | failsWithDescription("Val cannot be reassigned")
        "FileCollection = Object"          | "="       | "FileCollection" | "ConfigurableFileCollection" | '"a.txt"'                          | failsWithDescription("Type mismatch") | failsWithDescription("Val cannot be reassigned")
        "FileCollection = File"            | "="       | "FileCollection" | "ConfigurableFileCollection" | 'file("a.txt")'                    | failsWithDescription("Type mismatch") | failsWithDescription("Val cannot be reassigned")
        "FileCollection = Iterable<File>"  | "="       | "FileCollection" | "ConfigurableFileCollection" | 'listOf(file("a.txt"))'            | failsWithDescription("Type mismatch") | failsWithDescription("Val cannot be reassigned")
        "FileCollection += FileCollection" | "+="      | "FileCollection" | "ConfigurableFileCollection" | 'files("a.txt") as FileCollection' | 'Result: [a.txt]'                     | failsWithDescription("Val cannot be reassigned")
        "FileCollection += Object"         | "+="      | "FileCollection" | "ConfigurableFileCollection" | '"a.txt"'                          | failsWithDescription("Type mismatch") | failsWithDescription("Val cannot be reassigned")
        "FileCollection += File"           | "+="      | "FileCollection" | "ConfigurableFileCollection" | 'file("a.txt")'                    | failsWithDescription("Type mismatch") | failsWithDescription("Val cannot be reassigned")
        "FileCollection += Iterable<?>"    | "+="      | "FileCollection" | "ConfigurableFileCollection" | 'listOf("a.txt")'                  | failsWithDescription("Type mismatch") | failsWithDescription("Val cannot be reassigned")
        "FileCollection += Iterable<File>" | "+="      | "FileCollection" | "ConfigurableFileCollection" | 'listOf(file("a.txt"))'            | failsWithDescription("Type mismatch") | failsWithDescription("Val cannot be reassigned")
    }

    private void runAndAssert(String task, Object expectedResult) {
        if (expectedResult instanceof FailureWithCause) {
            def failure = runAndFail(task)
            failure.assertHasCause(expectedResult.failureCause)
        } else if (expectedResult instanceof FailureWithDescription) {
            def failure = runAndFail(task)
            failure.assertThatDescription(containsNormalizedString(expectedResult.failureDescription))
        } else {
            run(task)
            outputContains(expectedResult as String)
        }
    }

    private static FailureWithCause failsWithCause(String failureCause) {
        return new FailureWithCause(failureCause)
    }

    private static FailureWithDescription failsWithDescription(String error) {
        return new FailureWithDescription(error)
    }

    private static class FailureWithCause {
        final String failureCause

        FailureWithCause(String failureCause) {
            this.failureCause = failureCause
        }
    }

    private static class FailureWithDescription {
        final String failureDescription

        FailureWithDescription(String failureDescription) {
            this.failureDescription = failureDescription
        }
    }

}
