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

    private static final String GROOVY_BUILD_FILE_TEMPLATE = """
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
                    } else if (input instanceof Provider) {
                        println("Result: " + input.get())
                    } else if (input instanceof FileCollection) {
                        println("Result: " + input.files.collect { it.name })
                    } else {
                        println("Result: " + input)
                    }
                }
            }

            tasks.register("myTask", MyTask) {
                input {operation} {inputValue}
            }
    """

    private static final String KOTLIN_BUILD_FILE_TEMPLATE = """
            enum class MyEnum {
                YES, NO
            }

            class MyObject(val value: String) {
                override fun toString(): String = value
            }

            abstract class MyTask: DefaultTask() {
                @get:Internal
                {inputDeclaration}

                @TaskAction
                fun run() {
                    when (val anyInput = input as Any?) {
                       is DirectoryProperty -> println("Result: " + anyInput.get().asFile.name)
                       is File -> println("Result: " + anyInput.name)
                       is Provider<*> -> println("Result: " + anyInput.get())
                       is FileCollection -> println("Result: " + anyInput.files.map { it.name })
                       else -> println("Result: " + anyInput)
                    }
                }
            }

            tasks.register<MyTask>("myTask") {
                input {operation} {inputValue}
            }
    """

    def "test Groovy eager object types assignment for #description"() {
        buildFile.text = GROOVY_BUILD_FILE_TEMPLATE
            .replace("{inputDeclaration}", "$inputType input")
            .replace("{inputValue}", inputValue)
            .replace("{operation}", "=")

        expect:
        runAndAssert("myTask", expectedResult)

        where:
        description                                     | inputType  | inputValue                               | expectedResult
        "T = T"                                         | "MyObject" | 'new MyObject("hello")'                  | "Result: hello"
        "T = Provider<T>"                               | "MyObject" | 'provider { new MyObject("hello") }'     | failsWithCause("Cannot cast object")
        "String = Object"                               | "String"   | 'new MyObject("hello")'                  | "Result: hello"
        "Enum = String"                                 | "MyEnum"   | '"YES"'                                  | "Result: YES"
        "File = T extends FileSystemLocation"           | "File"     | 'layout.buildDirectory.dir("out").get()' | failsWithCause("Cannot cast object")
        "File = Provider<T extends FileSystemLocation>" | "File"     | 'layout.buildDirectory.dir("out")'       | failsWithCause("Cannot cast object")
        "File = File"                                   | "File"     | 'file("$buildDir/out")'                  | "Result: out"
        "File = Provider<File>"                         | "File"     | 'provider { file("$buildDir/out") }'     | failsWithCause("Cannot cast object")
        "File = Object"                                 | "File"     | 'new MyObject("out")'                    | failsWithCause("Cannot cast object")
    }

    def "test Groovy lazy object types assignment for #description"() {
        buildFile.text = GROOVY_BUILD_FILE_TEMPLATE
            .replace("{inputDeclaration}", "abstract $inputType getInput()")
            .replace("{inputValue}", inputValue)
            .replace("{operation}", "=")

        expect:
        runAndAssert("myTask", expectedResult)

        where:
        description                                     | inputType            | inputValue                               | expectedResult
        "T = T"                                         | "Property<MyObject>" | 'new MyObject("hello")'                  | "Result: hello"
        "T = Provider<T>"                               | "Property<MyObject>" | 'provider { new MyObject("hello") }'     | "Result: hello"
        "String = Object"                               | "Property<String>"   | 'new MyObject("hello")'                  | failsWithCause("Cannot set the value of task ':myTask' property 'input'")
        "Enum = String"                                 | "Property<MyEnum>"   | '"YES"'                                  | failsWithCause("Cannot set the value of task ':myTask' property 'input'")
        "File = T extends FileSystemLocation"           | "DirectoryProperty"  | 'layout.buildDirectory.dir("out").get()' | "Result: out"
        "File = Provider<T extends FileSystemLocation>" | "DirectoryProperty"  | 'layout.buildDirectory.dir("out")'       | "Result: out"
        "File = File"                                   | "DirectoryProperty"  | 'file("$buildDir/out")'                  | "Result: out"
        "File = Provider<File>"                         | "DirectoryProperty"  | 'provider { file("$buildDir/out") }'     | failsWithCause("Cannot get the value of task ':myTask' property 'input'")
        "File = Object"                                 | "DirectoryProperty"  | 'new MyObject("out")'                    | failsWithCause("Cannot set the value of task ':myTask' property 'input'")
    }

    def "test Kotlin eager object types assignment for #description"() {
        file("gradle.properties") << "\nsystemProp.org.gradle.unsafe.kotlin.assignment=true\n"
        buildKotlinFile.text = KOTLIN_BUILD_FILE_TEMPLATE
            .replace("{inputDeclaration}", "var input: $inputType? = null")
            .replace("{inputValue}", inputValue)
            .replace("{operation}", "=")

        expect:
        runAndAssert("myTask", expectedResult)

        where:
        description                                     | inputType  | inputValue                               | expectedResult
        "T = T"                                         | "MyObject" | 'MyObject("hello")'                      | "Result: hello"
        "T = Provider<T>"                               | "MyObject" | 'provider { MyObject("hello") }'         | failsWithDescription("Type mismatch")
        "String = Object"                               | "String"   | 'MyObject("hello")'                      | failsWithDescription("Type mismatch")
        "Enum = String"                                 | "MyEnum"   | '"YES"'                                  | failsWithDescription("Type mismatch")
        "File = T extends FileSystemLocation"           | "File"     | 'layout.buildDirectory.dir("out").get()' | failsWithDescription("Type mismatch")
        "File = Provider<T extends FileSystemLocation>" | "File"     | 'layout.buildDirectory.dir("out")'       | failsWithDescription("Type mismatch")
        "File = File"                                   | "File"     | 'file("$buildDir/out")'                  | "Result: out"
        "File = Provider<File>"                         | "File"     | 'provider { file("$buildDir/out") }'     | failsWithDescription("Type mismatch")
        "File = Object"                                 | "File"     | 'MyObject("out")'                        | failsWithDescription("Type mismatch")
    }

    def "test Kotlin lazy object types assignment for #description"() {
        file("gradle.properties") << "\nsystemProp.org.gradle.unsafe.kotlin.assignment=true\n"
        buildKotlinFile.text = KOTLIN_BUILD_FILE_TEMPLATE
            .replace("{inputDeclaration}", "abstract val input: $inputType")
            .replace("{inputValue}", inputValue)
            .replace("{operation}", "=")

        expect:
        runAndAssert("myTask", expectedResult)

        where:
        description                                     | inputType            | inputValue                               | expectedResult
        "T = T"                                         | "Property<MyObject>" | 'MyObject("hello")'                      | "Result: hello"
        "T = Provider<T>"                               | "Property<MyObject>" | 'provider { MyObject("hello") }'         | "Result: hello"
        "String = Object"                               | "Property<String>"   | 'MyObject("hello")'                      | failsWithDescription("Type mismatch")
        "Enum = String"                                 | "Property<MyEnum>"   | '"YES"'                                  | failsWithDescription("Type mismatch")
        "File = T extends FileSystemLocation"           | "DirectoryProperty"  | 'layout.buildDirectory.dir("out").get()' | "Result: out"
        "File = Provider<T extends FileSystemLocation>" | "DirectoryProperty"  | 'layout.buildDirectory.dir("out")'       | "Result: out"
        "File = File"                                   | "DirectoryProperty"  | 'file("$buildDir/out")'                  | "Result: out"
        "File = Provider<File>"                         | "DirectoryProperty"  | 'provider { file("$buildDir/out") }'     | "Result: out"
        "File = Object"                                 | "DirectoryProperty"  | 'MyObject("out")'                        | failsWithDescription("Type mismatch")
    }

    def "test Groovy eager collection types assignment for #description"() {
        given:
        def initValue = inputType.contains("Map<") ? "[:]" : "[]"
        buildFile.text = GROOVY_BUILD_FILE_TEMPLATE
            .replace("{inputDeclaration}", "$inputType input = $initValue")
            .replace("{inputValue}", inputValue)
            .replace("{operation}", operation)

        expect:
        runAndAssert("myTask", expectedResult)

        where:
        description                              | operation | inputType             | inputValue                               | expectedResult
        "Collection<T> = T[]"                    | "="       | "List<String>"        | '["a"] as String[]'                      | 'Result: [a]'
        "Collection<T> = Iterable<T>"            | "="       | "List<String>"        | '["a"] as Iterable<String>'              | 'Result: [a]'
        "Collection<T> = Provider<Iterable<T>>"  | "="       | "List<String>"        | 'provider { ["a"] as Iterable<String> }' | failsWithCause("Cannot cast object")
        "Collection<T> += T"                     | "+="      | "List<String>"        | '"a"'                                    | 'Result: [a]'
        "Collection<T> << T"                     | "<<"      | "List<String>"        | '"a"'                                    | 'Result: [a]'
        "Collection<T> += Provider<T>"           | "+="      | "List<String>"        | 'provider { "a" }'                       | 'Result: [provider(?)]'
        "Collection<T> << Provider<T>"           | "<<"      | "List<String>"        | 'provider { "a" }'                       | 'Result: [provider(?)]'
        "Collection<T> += T[]"                   | "+="      | "List<String>"        | '["a"] as String[]'                      | 'Result: [[a]]'
        "Collection<T> << T[]"                   | "<<"      | "List<String>"        | '["a"] as String[]'                      | 'Result: [[a]]'
        "Collection<T> += Iterable<T>"           | "+="      | "List<String>"        | '["a"] as Iterable<String>'              | 'Result: [a]'
        "Collection<T> << Iterable<T>"           | "<<"      | "List<String>"        | '["a"] as Iterable<String>'              | 'Result: [[a]]'
        "Collection<T> += Provider<Iterable<T>>" | "+="      | "List<String>"        | 'provider { ["a"] as Iterable<String> }' | 'Result: [provider(?)]'
        "Collection<T> << Provider<Iterable<T>>" | "<<"      | "List<String>"        | 'provider { ["a"] as Iterable<String> }' | 'Result: [provider(?)]'
        "Map<K, V> = Map<K, V>"                  | "="       | "Map<String, String>" | '["a": "b"]'                             | 'Result: [a:b]'
        "Map<K, V> = Provider<Map<K, V>>"        | "="       | "Map<String, String>" | 'provider { ["a": "b"] }'                | failsWithCause("Cannot cast object")
        "Map<K, V> += Map<K, V>"                 | "+="      | "Map<String, String>" | '["a": "b"]'                             | 'Result: [a:b]'
        "Map<K, V> << Map<K, V>"                 | "<<"      | "Map<String, String>" | '["a": "b"]'                             | 'Result: [a:b]'
        "Map<K, V> += Provider<Map<K, V>>"       | "+="      | "Map<String, String>" | 'provider { ["a": "b"] }'                | failsWithCause("No signature of method")
        "Map<K, V> << Provider<Map<K, V>>"       | "<<"      | "Map<String, String>" | 'provider { ["a": "b"] }'                | failsWithCause("No signature of method")
    }

    def "test Groovy lazy collection types assignment for #description"() {
        given:
        buildFile.text = GROOVY_BUILD_FILE_TEMPLATE
            .replace("{inputDeclaration}", "abstract $inputType getInput()")
            .replace("{inputValue}", inputValue)
            .replace("{operation}", operation)

        expect:
        runAndAssert("myTask", expectedResult)

        where:
        description                              | operation | inputType                     | inputValue                               | expectedResult
        "Collection<T> = T[]"                    | "="       | "ListProperty<String>"        | '["a"] as String[]'                      | failsWithCause("Cannot set the value of a property of type java.util.List using an instance of type [Ljava.lang.String;")
        "Collection<T> = Iterable<T>"            | "="       | "ListProperty<String>"        | '["a"] as Iterable<String>'              | 'Result: [a]'
        "Collection<T> = Provider<Iterable<T>>"  | "="       | "ListProperty<String>"        | 'provider { ["a"] as Iterable<String> }' | 'Result: [a]'
        "Collection<T> += T"                     | "+="      | "ListProperty<String>"        | '"a"'                                    | failsWithCause("No signature of method")
        "Collection<T> << T"                     | "<<"      | "ListProperty<String>"        | '"a"'                                    | failsWithCause("No signature of method")
        "Collection<T> += Provider<T>"           | "+="      | "ListProperty<String>"        | 'provider { "a" }'                       | failsWithCause("No signature of method")
        "Collection<T> << Provider<T>"           | "<<"      | "ListProperty<String>"        | 'provider { "a" }'                       | failsWithCause("No signature of method")
        "Collection<T> += T[]"                   | "+="      | "ListProperty<String>"        | '["a"] as String[]'                      | failsWithCause("No signature of method")
        "Collection<T> << T[]"                   | "<<"      | "ListProperty<String>"        | '["a"] as String[]'                      | failsWithCause("No signature of method")
        "Collection<T> += Iterable<T>"           | "+="      | "ListProperty<String>"        | '["a"] as Iterable<String>'              | failsWithCause("No signature of method")
        "Collection<T> << Iterable<T>"           | "<<"      | "ListProperty<String>"        | '["a"] as Iterable<String>'              | failsWithCause("No signature of method")
        "Collection<T> += Provider<Iterable<T>>" | "+="      | "ListProperty<String>"        | 'provider { ["a"] as Iterable<String> }' | failsWithCause("No signature of method")
        "Collection<T> << Provider<Iterable<T>>" | "<<"      | "ListProperty<String>"        | 'provider { ["a"] as Iterable<String> }' | failsWithCause("No signature of method")
        "Map<K, V> = Map<K, V>"                  | "="       | "MapProperty<String, String>" | '["a": "b"]'                             | 'Result: [a:b]'
        "Map<K, V> = Provider<Map<K, V>>"        | "="       | "MapProperty<String, String>" | 'provider { ["a": "b"] }'                | 'Result: [a:b]'
        "Map<K, V> += Map<K, V>"                 | "+="      | "MapProperty<String, String>" | '["a": "b"]'                             | failsWithCause("No signature of method")
        "Map<K, V> << Map<K, V>"                 | "<<"      | "MapProperty<String, String>" | '["a": "b"]'                             | failsWithCause("No signature of method")
        "Map<K, V> += Provider<Map<K, V>>"       | "+="      | "MapProperty<String, String>" | 'provider { ["a": "b"] }'                | failsWithCause("No signature of method")
        "Map<K, V> << Provider<Map<K, V>>"       | "<<"      | "MapProperty<String, String>" | 'provider { ["a": "b"] }'                | failsWithCause("No signature of method")
    }

    def "test Kotlin eager collection types assignment for #description"() {
        file("gradle.properties") << "\nsystemProp.org.gradle.unsafe.kotlin.assignment=true\n"
        def initValue = inputType.contains("Map<") ? "mutableMapOf<String, String>()" : "mutableListOf<String>()"
        buildKotlinFile.text = KOTLIN_BUILD_FILE_TEMPLATE
            .replace("{inputDeclaration}", "var input: $inputType = $initValue")
            .replace("{inputValue}", inputValue)
            .replace("{operation}", operation)

        expect:
        runAndAssert("myTask", expectedResult)

        where:
        description                              | operation | inputType                    | inputValue                                     | expectedResult
        "Collection<T> = T[]"                    | "="       | "List<String>"               | 'arrayOf("a")'                                 | failsWithDescription("Type mismatch")
        "Collection<T> = Iterable<T>"            | "="       | "List<String>"               | 'listOf("a") as Iterable<String>'              | failsWithDescription("Type mismatch")
        "Collection<T> = Provider<Iterable<T>>"  | "="       | "List<String>"               | 'provider { listOf("a") as Iterable<String> }' | failsWithDescription("Type mismatch")
        "Collection<T> += T"                     | "+="      | "MutableList<String>"        | '"a"'                                          | 'Result: [a]'
        "Collection<T> += Provider<T>"           | "+="      | "MutableList<String>"        | 'provider { "a" }'                             | failsWithDescription("Type mismatch")
        "Collection<T> += T[]"                   | "+="      | "MutableList<String>"        | 'arrayOf("a")'                                 | 'Result: [a]'
        "Collection<T> += Iterable<T>"           | "+="      | "MutableList<String>"        | 'listOf("a") as Iterable<String>'              | 'Result: [a]'
        "Collection<T> += Provider<Iterable<T>>" | "+="      | "MutableList<String>"        | 'provider { listOf("a") as Iterable<String> }' | failsWithDescription("Type mismatch")
        "Map<K, V> = Map<K, V>"                  | "="       | "Map<String, String>"        | 'mapOf("a" to "b")'                            | 'Result: {a=b}'
        "Map<K, V> = Provider<Map<K, V>>"        | "="       | "Map<String, String>"        | 'provider { mapOf("a" to "b") }'               | failsWithDescription("Type mismatch")
        "Map<K, V> += Pair<K, V>"                | "+="      | "MutableMap<String, String>" | '"a" to "b"'                                   | 'Result: {a=b}'
        "Map<K, V> += Provider<Pair<K, V>>"      | "+="      | "MutableMap<String, String>" | 'provider { "a" to "b" }'                      | failsWithDescription("None of the following")
        "Map<K, V> += Map<K, V>"                 | "+="      | "MutableMap<String, String>" | 'mapOf("a" to "b")'                            | 'Result: {a=b}'
        "Map<K, V> += Provider<Map<K, V>>"       | "+="      | "MutableMap<String, String>" | 'provider { mapOf("a" to "b") }'               | failsWithDescription("None of the following")
    }

    def "test Kotlin lazy collection types assignment for #description"() {
        file("gradle.properties") << "\nsystemProp.org.gradle.unsafe.kotlin.assignment=true\n"
        buildKotlinFile.text = KOTLIN_BUILD_FILE_TEMPLATE
            .replace("{inputDeclaration}", "abstract val input: $inputType")
            .replace("{inputValue}", inputValue)
            .replace("{operation}", operation)

        expect:
        runAndAssert("myTask", expectedResult)

        where:
        description                              | operation | inputType                     | inputValue                                     | expectedResult
        "Collection<T> = T[]"                    | "="       | "ListProperty<String>"        | 'arrayOf("a")'                                 | failsWithDescription("No applicable 'assign' function found for '=' overload")
        "Collection<T> = Iterable<T>"            | "="       | "ListProperty<String>"        | 'listOf("a") as Iterable<String>'              | 'Result: [a]'
        "Collection<T> = Provider<Iterable<T>>"  | "="       | "ListProperty<String>"        | 'provider { listOf("a") as Iterable<String> }' | 'Result: [a]'
        "Collection<T> += T"                     | "+="      | "ListProperty<String>"        | '"a"'                                          | failsWithDescription("Unresolved reference. None of the following candidates is applicable because of receiver type mismatch:")
        "Collection<T> += Provider<T>"           | "+="      | "ListProperty<String>"        | 'provider { "a" }'                             | failsWithDescription("Unresolved reference. None of the following candidates is applicable because of receiver type mismatch:")
        "Collection<T> += T[]"                   | "+="      | "ListProperty<String>"        | 'arrayOf("a")'                                 | failsWithDescription("Unresolved reference. None of the following candidates is applicable because of receiver type mismatch:")
        "Collection<T> += Iterable<T>"           | "+="      | "ListProperty<String>"        | 'listOf("a") as Iterable<String>'              | failsWithDescription("Unresolved reference. None of the following candidates is applicable because of receiver type mismatch:")
        "Collection<T> += Provider<Iterable<T>>" | "+="      | "ListProperty<String>"        | 'provider { listOf("a") as Iterable<String> }' | failsWithDescription("Unresolved reference. None of the following candidates is applicable because of receiver type mismatch:")
        "Map<K, V> = Map<K, V>"                  | "="       | "MapProperty<String, String>" | 'mapOf("a" to "b")'                            | 'Result: {a=b}'
        "Map<K, V> = Provider<Map<K, V>>"        | "="       | "MapProperty<String, String>" | 'provider { mapOf("a" to "b") }'               | 'Result: {a=b}'
        "Map<K, V> += Pair<K, V>"                | "+="      | "MapProperty<String, String>" | '"a" to "b"'                                   | failsWithDescription("Unresolved reference. None of the following candidates is applicable because of receiver type mismatch:")
        "Map<K, V> += Provider<Pair<K, V>>"      | "+="      | "MapProperty<String, String>" | 'provider { "a" to "b" }'                      | failsWithDescription("Unresolved reference. None of the following candidates is applicable because of receiver type mismatch:")
        "Map<K, V> += Map<K, V>"                 | "+="      | "MapProperty<String, String>" | 'mapOf("a" to "b")'                            | failsWithDescription("Unresolved reference. None of the following candidates is applicable because of receiver type mismatch:")
        "Map<K, V> += Provider<Map<K, V>>"       | "+="      | "MapProperty<String, String>" | 'provider { mapOf("a" to "b") }'               | failsWithDescription("Unresolved reference. None of the following candidates is applicable because of receiver type mismatch:")
    }

    def "test Groovy eager FileCollection types assignment for #description"() {
        buildFile.text = GROOVY_BUILD_FILE_TEMPLATE
            .replace("{inputDeclaration}", "$inputType input = project.files() as $inputType")
            .replace("{inputValue}", inputValue)
            .replace("{operation}", operation)

        expect:
        runAndAssert("myTask", expectedResult)

        where:
        description                        | operation | inputType        | inputValue        | expectedResult
        "FileCollection = FileCollection"  | "="       | "FileCollection" | 'files("a.txt")'  | 'Result: [a.txt]'
        "FileCollection = Object"          | "="       | "FileCollection" | '"a.txt"'         | failsWithCause("Cannot cast object")
        "FileCollection = File"            | "="       | "FileCollection" | 'file("a.txt")'   | failsWithCause("Cannot cast object")
        "FileCollection = Iterable<File>"  | "="       | "FileCollection" | '[file("a.txt")]' | failsWithCause("Cannot cast object")
        "FileCollection += FileCollection" | "+="      | "FileCollection" | 'files("a.txt")'  | 'Result: [a.txt]'
        "FileCollection << FileCollection" | "<<"      | "FileCollection" | 'files("a.txt")'  | failsWithCause("No signature of method")
        "FileCollection += Object"         | "+="      | "FileCollection" | '"a.txt"'         | failsWithCause("Cannot cast object")
        "FileCollection += File"           | "+="      | "FileCollection" | 'file("a.txt")'   | failsWithCause("Cannot cast object")
        "FileCollection += Iterable<?>"    | "+="      | "FileCollection" | '["a.txt"]'       | failsWithCause("Cannot cast object")
        "FileCollection += Iterable<File>" | "+="      | "FileCollection" | '[file("a.txt")]' | failsWithCause("Cannot cast object")
    }

    def "test Groovy lazy FileCollection types assignment for #description"() {
        buildFile.text = GROOVY_BUILD_FILE_TEMPLATE
            .replace("{inputDeclaration}", "abstract $inputType getInput()")
            .replace("{inputValue}", inputValue)
            .replace("{operation}", operation)

        expect:
        runAndAssert("myTask", expectedResult)

        where:
        description                        | operation | inputType                    | inputValue        | expectedResult
        "FileCollection = FileCollection"  | "="       | "ConfigurableFileCollection" | 'files("a.txt")'  | failsWithCause("Cannot set the value of read-only property 'input' for task ':myTask' of type MyTask.")
        "FileCollection = Object"          | "="       | "ConfigurableFileCollection" | '"a.txt"'         | failsWithCause("Cannot set the value of read-only property 'input' for task ':myTask' of type MyTask.")
        "FileCollection = File"            | "="       | "ConfigurableFileCollection" | 'file("a.txt")'   | failsWithCause("Cannot set the value of read-only property 'input' for task ':myTask' of type MyTask.")
        "FileCollection = Iterable<File>"  | "="       | "ConfigurableFileCollection" | '[file("a.txt")]' | failsWithCause("Cannot set the value of read-only property 'input' for task ':myTask' of type MyTask.")
        "FileCollection += FileCollection" | "+="      | "ConfigurableFileCollection" | 'files("a.txt")'  | failsWithCause("Cannot set the value of read-only property 'input' for task ':myTask' of type MyTask.")
        "FileCollection << FileCollection" | "<<"      | "ConfigurableFileCollection" | 'files("a.txt")'  | failsWithCause("No signature of method")
        "FileCollection += Object"         | "+="      | "ConfigurableFileCollection" | '"a.txt"'         | failsWithCause("Cannot set the value of read-only property 'input' for task ':myTask' of type MyTask.")
        "FileCollection += File"           | "+="      | "ConfigurableFileCollection" | 'file("a.txt")'   | failsWithCause("Cannot set the value of read-only property 'input' for task ':myTask' of type MyTask.")
        "FileCollection += Iterable<?>"    | "+="      | "ConfigurableFileCollection" | '["a.txt"]'       | failsWithCause("Cannot set the value of read-only property 'input' for task ':myTask' of type MyTask.")
        "FileCollection += Iterable<File>" | "+="      | "ConfigurableFileCollection" | '[file("a.txt")]' | failsWithCause("Cannot set the value of read-only property 'input' for task ':myTask' of type MyTask.")
    }

    def "test Kotlin eager FileCollection types assignment for #description"() {
        file("gradle.properties") << "\nsystemProp.org.gradle.unsafe.kotlin.assignment=true\n"
        buildKotlinFile.text = KOTLIN_BUILD_FILE_TEMPLATE
            .replace("{inputDeclaration}", "var input: $inputType = project.files()")
            .replace("{inputValue}", inputValue)
            .replace("{operation}", operation)

        expect:
        runAndAssert("myTask", expectedResult)

        where:
        description                        | operation | inputType                    | inputValue                         | expectedResult
        "FileCollection = FileCollection"  | "="       | "FileCollection"             | 'files("a.txt") as FileCollection' | 'Result: [a.txt]'
        "FileCollection = Object"          | "="       | "FileCollection"             | '"a.txt"'                          | failsWithDescription("Type mismatch")
        "FileCollection = File"            | "="       | "FileCollection"             | 'file("a.txt")'                    | failsWithDescription("Type mismatch")
        "FileCollection = Iterable<File>"  | "="       | "FileCollection"             | 'listOf(file("a.txt"))'            | failsWithDescription("Type mismatch")
        "FileCollection += FileCollection" | "+="      | "FileCollection"             | 'files("a.txt") as FileCollection' | 'Result: [a.txt]'
        "FileCollection += Object"         | "+="      | "FileCollection"             | '"a.txt"'                          | failsWithDescription("Type mismatch")
        "FileCollection += File"           | "+="      | "FileCollection"             | 'file("a.txt")'                    | failsWithDescription("Type mismatch")
        "FileCollection += Iterable<?>"    | "+="      | "FileCollection"             | 'listOf("a.txt")'                  | failsWithDescription("Type mismatch")
        "FileCollection += Iterable<File>" | "+="      | "FileCollection"             | 'listOf(file("a.txt"))'            | failsWithDescription("Type mismatch")
    }

    def "test Kotlin lazy FileCollection types assignment for #description"() {
        file("gradle.properties") << "\nsystemProp.org.gradle.unsafe.kotlin.assignment=true\n"
        buildKotlinFile.text = KOTLIN_BUILD_FILE_TEMPLATE
            .replace("{inputDeclaration}", "abstract val input: $inputType")
            .replace("{inputValue}", inputValue)
            .replace("{operation}", operation)

        expect:
        runAndAssert("myTask", expectedResult)

        where:
        description                        | operation | inputType                    | inputValue                         | expectedResult
        "FileCollection = FileCollection"  | "="       | "ConfigurableFileCollection" | 'files("a.txt") as FileCollection' | failsWithDescription("Val cannot be reassigned")
        "FileCollection = Object"          | "="       | "ConfigurableFileCollection" | '"a.txt"'                          | failsWithDescription("Val cannot be reassigned")
        "FileCollection = File"            | "="       | "ConfigurableFileCollection" | 'file("a.txt")'                    | failsWithDescription("Val cannot be reassigned")
        "FileCollection = Iterable<File>"  | "="       | "ConfigurableFileCollection" | 'listOf(file("a.txt"))'            | failsWithDescription("Val cannot be reassigned")
        "FileCollection += FileCollection" | "+="      | "ConfigurableFileCollection" | 'files("a.txt") as FileCollection' | failsWithDescription("Val cannot be reassigned")
        "FileCollection += Object"         | "+="      | "ConfigurableFileCollection" | '"a.txt"'                          | failsWithDescription("Val cannot be reassigned")
        "FileCollection += File"           | "+="      | "ConfigurableFileCollection" | 'file("a.txt")'                    | failsWithDescription("Val cannot be reassigned")
        "FileCollection += Iterable<?>"    | "+="      | "ConfigurableFileCollection" | 'listOf("a.txt")'                  | failsWithDescription("Val cannot be reassigned")
        "FileCollection += Iterable<File>" | "+="      | "ConfigurableFileCollection" | 'listOf(file("a.txt"))'            | failsWithDescription("Val cannot be reassigned")
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
