/*
 * Copyright 2024 the original author or authors.
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

class KotlinPropertyAssignmentIntegTest extends AbstractProviderOperatorIntegrationTest {
    def "eager object properties assignment for #description"() {
        def inputDeclaration = "var input: $inputType? = null"
        kotlinBuildFile(inputDeclaration, inputValue, "=")

        expect:
        runAndAssert("myTask", expectedResult)

        where:
        description                                     | inputType  | inputValue                               | expectedResult
        "T = T"                                         | "MyObject" | 'MyObject("hello")'                      | "hello"
        "T = Provider<T>"                               | "MyObject" | 'provider { MyObject("hello") }'         | unsupportedWithDescription("Assignment type mismatch")
        "String = Object"                               | "String"   | 'MyObject("hello")'                      | unsupportedWithDescription("Assignment type mismatch")
        "Enum = String"                                 | "MyEnum"   | '"YES"'                                  | unsupportedWithDescription("Assignment type mismatch")
        "File = T extends FileSystemLocation"           | "File"     | 'layout.buildDirectory.dir("out").get()' | unsupportedWithDescription("Assignment type mismatch")
        "File = Provider<T extends FileSystemLocation>" | "File"     | 'layout.buildDirectory.dir("out")'       | unsupportedWithDescription("Assignment type mismatch")
        "File = File"                                   | "File"     | 'file("out")'                            | "out"
        "File = Provider<File>"                         | "File"     | 'provider { file("out") }'               | unsupportedWithDescription("Assignment type mismatch")
        "File = Object"                                 | "File"     | 'MyObject("out")'                        | unsupportedWithDescription("Assignment type mismatch")
    }

    def "lazy object properties assignment for #description"() {
        def inputDeclaration = "abstract val input: $inputType"
        kotlinBuildFile(inputDeclaration, inputValue, "=")

        expect:
        runAndAssert("myTask", expectedResult)

        where:
        description                                     | inputType            | inputValue                               | expectedResult
        "T = null"                                      | "Property<MyObject>" | 'null'                                   | "undefined"
        "T = T"                                         | "Property<MyObject>" | 'MyObject("hello")'                      | "hello"
        "T = provider { null }"                         | "Property<MyObject>" | 'provider<MyObject> { null }'            | "undefined"
        "T = Provider<T>"                               | "Property<MyObject>" | 'provider { MyObject("hello") }'         | "hello"
        "String = Object"                               | "Property<String>"   | 'MyObject("hello")'                      | unsupportedWithDescription("None of the following candidates is applicable")
        "Enum = String"                                 | "Property<MyEnum>"   | '"YES"'                                  | unsupportedWithDescription("None of the following candidates is applicable")
        "File = T extends FileSystemLocation"           | "DirectoryProperty"  | 'layout.buildDirectory.dir("out").get()' | "out"
        "File = Provider<T extends FileSystemLocation>" | "DirectoryProperty"  | 'layout.buildDirectory.dir("out")'       | "out"
        "File = File"                                   | "DirectoryProperty"  | 'file("out")'                            | "out"
        "File = Provider<File>"                         | "DirectoryProperty"  | 'provider { file("out") }'               | "out"
        "File = Object"                                 | "DirectoryProperty"  | 'MyObject("out")'                        | unsupportedWithDescription("None of the following candidates is applicable")
    }

    def "test eager collection properties assignment for #description"() {
        def initValue = inputType.contains("Map<") ? "mutableMapOf<String, MyObject>()" : "mutableListOf<MyObject>()"
        def inputDeclaration = "var input: $inputType = $initValue"
        kotlinBuildFile(inputDeclaration, inputValue, operation)

        expect:
        runAndAssert("myTask", expectedResult)

        where:
        description                              | operation | inputType                      | inputValue                                                 | expectedResult
        "Collection<T> = T[]"                    | "="       | "List<MyObject>"               | 'arrayOf(MyObject("a"))'                                   | unsupportedWithDescription("Assignment type mismatch")
        "Collection<T> = Iterable<T>"            | "="       | "List<MyObject>"               | 'listOf(MyObject("a")) as Iterable<MyObject>'              | unsupportedWithDescription("Assignment type mismatch")
        "Collection<T> = Provider<Iterable<T>>"  | "="       | "List<MyObject>"               | 'provider { listOf(MyObject("a")) as Iterable<MyObject> }' | unsupportedWithDescription("Assignment type mismatch")
        "Collection<T> += T"                     | "+="      | "MutableList<MyObject>"        | 'MyObject("a")'                                            | '[a]'
        "Collection<T> += Provider<T>"           | "+="      | "MutableList<MyObject>"        | 'provider { MyObject("a") }'                               | unsupportedWithDescription("Assignment type mismatch")
        "Collection<T> += T[]"                   | "+="      | "MutableList<MyObject>"        | 'arrayOf(MyObject("a"))'                                   | '[a]'
        "Collection<T> += Iterable<T>"           | "+="      | "MutableList<MyObject>"        | 'listOf(MyObject("a")) as Iterable<MyObject>'              | '[a]'
        "Collection<T> += Provider<Iterable<T>>" | "+="      | "MutableList<MyObject>"        | 'provider { listOf(MyObject("a")) as Iterable<MyObject> }' | unsupportedWithDescription("Assignment type mismatch")
        "Map<K, V> = Map<K, V>"                  | "="       | "Map<String, MyObject>"        | 'mapOf("a" to MyObject("b"))'                              | '{a=b}'
        "Map<K, V> = Provider<Map<K, V>>"        | "="       | "Map<String, MyObject>"        | 'provider { mapOf("a" to MyObject("b")) }'                 | unsupportedWithDescription("Assignment type mismatch")
        "Map<K, V> += Pair<K, V>"                | "+="      | "MutableMap<String, MyObject>" | '"a" to MyObject("b")'                                     | '{a=b}'
        "Map<K, V> += Provider<Pair<K, V>>"      | "+="      | "MutableMap<String, MyObject>" | 'provider { "a" to MyObject("b") }'                        | unsupportedWithDescription("None of the following candidates is applicable")
        "Map<K, V> += Map<K, V>"                 | "+="      | "MutableMap<String, MyObject>" | 'mapOf("a" to MyObject("b"))'                              | '{a=b}'
        "Map<K, V> += Provider<Map<K, V>>"       | "+="      | "MutableMap<String, MyObject>" | 'provider { mapOf("a" to MyObject("b")) }'                 | unsupportedWithDescription("None of the following candidates is applicable")
    }

    def "lazy collection properties assignment for #description"() {
        def inputDeclaration = "abstract val input: $inputType"
        kotlinBuildFile(inputDeclaration, inputValue, operation)

        expect:
        runAndAssert("myTask", expectedResult)

        where:
        description                                       | operation | inputType                       | inputValue                                                 | expectedResult
        "Collection<T> = null"                            | "="       | "ListProperty<MyObject>"        | 'null'                                                     | 'undefined'
        "Collection<T> = T[]"                             | "="       | "ListProperty<MyObject>"        | 'arrayOf(MyObject("a"))'                                   | unsupportedWithDescription("None of the following candidates is applicable")
        "Collection<T> = Iterable<T>"                     | "="       | "ListProperty<MyObject>"        | 'listOf(MyObject("a")) as Iterable<MyObject>'              | '[a]'
        "Collection<T> = provider<T> { null } "           | "="       | "ListProperty<MyObject>"        | 'provider<Iterable<MyObject>> { null } '                   | 'undefined'
        "Collection<T> = Provider<Iterable<T>>"           | "="       | "ListProperty<MyObject>"        | 'provider { listOf(MyObject("a")) as Iterable<MyObject> }' | '[a]'
        "Collection<T> += T"                              | "+="      | "ListProperty<MyObject>"        | 'MyObject("a")'                                            | '[a]'
        "Collection<T> += Provider<T>"                    | "+="      | "ListProperty<MyObject>"        | 'provider { MyObject("a") }'                               | '[a]'
        "Collection<T> += T[]"                            | "+="      | "ListProperty<MyObject>"        | 'arrayOf(MyObject("a"))'                                   | '[a]'
        "Collection<T> += Iterable<T>"                    | "+="      | "ListProperty<MyObject>"        | 'listOf(MyObject("a")) as Iterable<MyObject>'              | '[a]'
        "Collection<T> += Provider<Iterable<T>>"          | "+="      | "ListProperty<MyObject>"        | 'provider { listOf(MyObject("a")) as Iterable<MyObject> }' | '[a]'
        "Collection<T> += provider<T> { null }"           | "+="      | "ListProperty<MyObject>"        | 'provider<MyObject> { null }'                              | 'undefined'
        "Collection<T> += provider<Iterable<T>> { null }" | "+="      | "ListProperty<MyObject>"        | 'provider<Iterable<MyObject>> { null }'                    | 'undefined'
        "Map<K, V> = null"                                | "="       | "MapProperty<String, MyObject>" | 'null'                                                     | 'undefined'
        "Map<K, V> = Map<K, V>"                           | "="       | "MapProperty<String, MyObject>" | 'mapOf("a" to MyObject("b"))'                              | '{a=b}'
        "Map<K, V> = provider<Map<K, V>> { null }"        | "="       | "MapProperty<String, MyObject>" | 'provider<Map<String, MyObject>> { null }'                 | 'undefined'
        "Map<K, V> = Provider<Map<K, V>>"                 | "="       | "MapProperty<String, MyObject>" | 'provider { mapOf("a" to MyObject("b")) }'                 | '{a=b}'
        "Map<K, V> += Pair<K, V>"                         | "+="      | "MapProperty<String, MyObject>" | '"a" to MyObject("b")'                                     | '{a=b}'
        "Map<K, V> += Provider<Pair<K, V>>"               | "+="      | "MapProperty<String, MyObject>" | 'provider { "a" to MyObject("b") }'                        | '{a=b}'
        "Map<K, V> += Map<K, V>"                          | "+="      | "MapProperty<String, MyObject>" | 'mapOf("a" to MyObject("b"))'                              | '{a=b}'
        "Map<K, V> += Provider<Map<K, V>>"                | "+="      | "MapProperty<String, MyObject>" | 'provider { mapOf("a" to MyObject("b")) }'                 | '{a=b}'
        "Map<K, V> += provider<Map<K, V>> { null }"       | "+="      | "MapProperty<String, MyObject>" | 'provider<Map<String, MyObject>> { null }'                 | 'undefined'
        "Map<K, V> += provider<Pair<K, V>> { null }"      | "+="      | "MapProperty<String, MyObject>" | 'provider<Pair<String, MyObject>> { null }'                | 'undefined'
    }

    def "lazy collection variables assignment for #description"() {
        def inputInitializer = inputType.startsWith("ListProperty<") ? "objects.listProperty<MyObject>()" : "objects.mapProperty<String, MyObject>()"
        kotlinBuildFileWithVariable(inputInitializer, inputValue, operation)

        expect:
        runAndAssert("myTask", expectedResult)

        where:
        description                                       | operation | inputType                       | inputValue                                                 | expectedResult
        "Collection<T> = null"                            | "="       | "ListProperty<MyObject>"        | 'null'                                                     | unsupportedWithDescription("'val' cannot be reassigned")
        "Collection<T> = T[]"                             | "="       | "ListProperty<MyObject>"        | 'arrayOf(MyObject("a"))'                                   | unsupportedWithDescription("'val' cannot be reassigned")
        "Collection<T> = Iterable<T>"                     | "="       | "ListProperty<MyObject>"        | 'listOf(MyObject("a")) as Iterable<MyObject>'              | unsupportedWithDescription("'val' cannot be reassigned")
        "Collection<T> = provider { null } "              | "="       | "ListProperty<MyObject>"        | 'provider { null } '                                       | unsupportedWithDescription("'val' cannot be reassigned")
        "Collection<T> = Provider<Iterable<T>>"           | "="       | "ListProperty<MyObject>"        | 'provider { listOf(MyObject("a")) as Iterable<MyObject> }' | unsupportedWithDescription("'val' cannot be reassigned")
        "Collection<T> += T"                              | "+="      | "ListProperty<MyObject>"        | 'MyObject("a")'                                            | '[a]'
        "Collection<T> += Provider<T>"                    | "+="      | "ListProperty<MyObject>"        | 'provider { MyObject("a") }'                               | '[a]'
        "Collection<T> += T[]"                            | "+="      | "ListProperty<MyObject>"        | 'arrayOf(MyObject("a"))'                                   | '[a]'
        "Collection<T> += Iterable<T>"                    | "+="      | "ListProperty<MyObject>"        | 'listOf(MyObject("a")) as Iterable<MyObject>'              | '[a]'
        "Collection<T> += Provider<Iterable<T>>"          | "+="      | "ListProperty<MyObject>"        | 'provider { listOf(MyObject("a")) as Iterable<MyObject> }' | '[a]'
        "Collection<T> += provider { null }"              | "+="      | "ListProperty<MyObject>"        | 'provider { null }'                                        | unsupportedWithDescription("Overload resolution ambiguity between candidates")
        "Collection<T> += provider<T> { null }"           | "+="      | "ListProperty<MyObject>"        | 'provider<MyObject> { null }'                              | 'undefined'
        "Collection<T> += provider<Iterable<T>> { null }" | "+="      | "ListProperty<MyObject>"        | 'provider<Iterable<MyObject>> { null }'                    | 'undefined'
        "Map<K, V> = null"                                | "="       | "MapProperty<String, MyObject>" | 'null'                                                     | unsupportedWithDescription("'val' cannot be reassigned")
        "Map<K, V> = Map<K, V>"                           | "="       | "MapProperty<String, MyObject>" | 'mapOf("a" to MyObject("b"))'                              | unsupportedWithDescription("'val' cannot be reassigned")
        "Map<K, V> = provider { null }"                   | "="       | "MapProperty<String, MyObject>" | 'provider { null }'                                        | unsupportedWithDescription("'val' cannot be reassigned")
        "Map<K, V> = Provider<Map<K, V>>"                 | "="       | "MapProperty<String, MyObject>" | 'provider { mapOf("a" to MyObject("b")) }'                 | unsupportedWithDescription("'val' cannot be reassigned")
        "Map<K, V> += Pair<K, V>"                         | "+="      | "MapProperty<String, MyObject>" | '"a" to MyObject("b")'                                     | '{a=b}'
        "Map<K, V> += Provider<Pair<K, V>>"               | "+="      | "MapProperty<String, MyObject>" | 'provider { "a" to MyObject("b") }'                        | '{a=b}'
        "Map<K, V> += Map<K, V>"                          | "+="      | "MapProperty<String, MyObject>" | 'mapOf("a" to MyObject("b"))'                              | '{a=b}'
        "Map<K, V> += Provider<Map<K, V>>"                | "+="      | "MapProperty<String, MyObject>" | 'provider { mapOf("a" to MyObject("b")) }'                 | '{a=b}'
        "Map<K, V> += provider { null }"                  | "+="      | "MapProperty<String, MyObject>" | 'provider { null }'                                        | unsupportedWithDescription("Overload resolution ambiguity between candidates")
        "Map<K, V> += provider<Map<K, V>> { null }"       | "+="      | "MapProperty<String, MyObject>" | 'provider<Map<String, MyObject>> { null }'                 | 'undefined'
        "Map<K, V> += provider<Pair<K, V>> { null }"      | "+="      | "MapProperty<String, MyObject>" | 'provider<Pair<String, MyObject>> { null }'                | 'undefined'
    }

    def "eager FileCollection properties assignment for #description"() {
        def inputDeclaration = "var input: $inputType = project.files()"
        kotlinBuildFile(inputDeclaration, inputValue, operation)

        expect:
        runAndAssert("myTask", expectedResult)

        where:
        description                        | operation | inputType        | inputValue                         | expectedResult
        "FileCollection = FileCollection"  | "="       | "FileCollection" | 'files("a.txt") as FileCollection' | '[a.txt]'
        "FileCollection = String"          | "="       | "FileCollection" | '"a.txt"'                          | unsupportedWithDescription("Assignment type mismatch")
        "FileCollection = Object"          | "="       | "FileCollection" | 'MyObject("a.txt")'                | unsupportedWithDescription("Assignment type mismatch")
        "FileCollection = File"            | "="       | "FileCollection" | 'file("a.txt")'                    | unsupportedWithDescription("Assignment type mismatch")
        "FileCollection = Iterable<File>"  | "="       | "FileCollection" | 'listOf(file("a.txt"))'            | unsupportedWithDescription("Assignment type mismatch")
        "FileCollection += FileCollection" | "+="      | "FileCollection" | 'files("a.txt") as FileCollection' | '[a.txt]'
        "FileCollection += String"         | "+="      | "FileCollection" | '"a.txt"'                          | unsupportedWithDescription("Assignment type mismatch")
        "FileCollection += Object"         | "+="      | "FileCollection" | 'MyObject("a.txt")'                | unsupportedWithDescription("Assignment type mismatch")
        "FileCollection += File"           | "+="      | "FileCollection" | 'file("a.txt")'                    | unsupportedWithDescription("Assignment type mismatch")
        "FileCollection += Iterable<?>"    | "+="      | "FileCollection" | 'listOf("a.txt")'                  | unsupportedWithDescription("Assignment type mismatch")
        "FileCollection += Iterable<File>" | "+="      | "FileCollection" | 'listOf(file("a.txt"))'            | unsupportedWithDescription("Assignment type mismatch")
    }

    def "lazy FileCollection properties assignment for #description"() {
        def inputDeclaration = "abstract val input: $inputType"
        kotlinBuildFile(inputDeclaration, inputValue, operation)

        expect:
        runAndAssert("myTask", expectedResult)

        where:
        description                        | operation | inputType                    | inputValue                         | expectedResult
        "FileCollection = FileCollection"  | "="       | "ConfigurableFileCollection" | 'files("a.txt") as FileCollection' | '[a.txt]'
        "FileCollection = String"          | "="       | "ConfigurableFileCollection" | '"a.txt"'                          | unsupportedWithDescription("Argument type mismatch: actual type is 'String', but 'FileCollection' was expected.")
        "FileCollection = Object"          | "="       | "ConfigurableFileCollection" | 'MyObject("a.txt")'                | unsupportedWithDescription("Argument type mismatch: actual type is 'MyObject', but 'FileCollection' was expected.")
        "FileCollection = File"            | "="       | "ConfigurableFileCollection" | 'file("a.txt")'                    | unsupportedWithDescription("Argument type mismatch: actual type is 'File', but 'FileCollection' was expected.")
        "FileCollection = Iterable<File>"  | "="       | "ConfigurableFileCollection" | 'listOf(file("a.txt"))'            | unsupportedWithDescription("Argument type mismatch: actual type is 'List<File>', but 'FileCollection' was expected.")
        "FileCollection += FileCollection" | "+="      | "ConfigurableFileCollection" | 'files("a.txt") as FileCollection' | '[a.txt]'
        "FileCollection += String"         | "+="      | "ConfigurableFileCollection" | '"a.txt"'                          | unsupportedWithDescription("Argument type mismatch: actual type is 'List<Serializable & Comparable<File & String>>', but 'FileCollection' was expected.")
        "FileCollection += Object"         | "+="      | "ConfigurableFileCollection" | 'MyObject("a.txt")'                | unsupportedWithDescription("Argument type mismatch: actual type is 'List<Any>', but 'FileCollection' was expected.")
        "FileCollection += File"           | "+="      | "ConfigurableFileCollection" | 'file("a.txt")'                    | unsupportedWithDescription("Argument type mismatch: actual type is 'List<File>', but 'FileCollection' was expected.")
        "FileCollection += Iterable<?>"    | "+="      | "ConfigurableFileCollection" | 'listOf("a.txt")'                  | unsupportedWithDescription("Argument type mismatch: actual type is 'List<Serializable & Comparable<File & String>>', but 'FileCollection' was expected.")
        "FileCollection += Iterable<File>" | "+="      | "ConfigurableFileCollection" | 'listOf(file("a.txt"))'            | unsupportedWithDescription("Argument type mismatch: actual type is 'List<File>', but 'FileCollection' was expected.")
    }

    def "lazy FileCollection variables assignment for #description"() {
        def inputInitializer = "files()"
        kotlinBuildFileWithVariable(inputInitializer, inputValue, operation)

        expect:
        runAndAssert("myTask", expectedResult)

        where:
        description                        | operation | inputType                    | inputValue                         | expectedResult
        "FileCollection = FileCollection"  | "="       | "ConfigurableFileCollection" | 'files("a.txt") as FileCollection' | unsupportedWithDescription("'val' cannot be reassigned")
        "FileCollection = String"          | "="       | "ConfigurableFileCollection" | '"a.txt"'                          | unsupportedWithDescription("'val' cannot be reassigned")
        "FileCollection = Object"          | "="       | "ConfigurableFileCollection" | 'MyObject("a.txt")'                | unsupportedWithDescription("'val' cannot be reassigned")
        "FileCollection = File"            | "="       | "ConfigurableFileCollection" | 'file("a.txt")'                    | unsupportedWithDescription("'val' cannot be reassigned")
        "FileCollection = Iterable<File>"  | "="       | "ConfigurableFileCollection" | 'listOf(file("a.txt"))'            | unsupportedWithDescription("'val' cannot be reassigned")
        "FileCollection += FileCollection" | "+="      | "ConfigurableFileCollection" | 'files("a.txt") as FileCollection' | '[a.txt]'
        "FileCollection += String"         | "+="      | "ConfigurableFileCollection" | '"a.txt"'                          | unsupportedWithDescription("'val' cannot be reassigned")
        "FileCollection += Object"         | "+="      | "ConfigurableFileCollection" | 'MyObject("a.txt")'                | unsupportedWithDescription("'val' cannot be reassigned")
        "FileCollection += File"           | "+="      | "ConfigurableFileCollection" | 'file("a.txt")'                    | unsupportedWithDescription("'val' cannot be reassigned")
        "FileCollection += Iterable<?>"    | "+="      | "ConfigurableFileCollection" | 'listOf("a.txt")'                  | unsupportedWithDescription("'val' cannot be reassigned")
        "FileCollection += Iterable<File>" | "+="      | "ConfigurableFileCollection" | 'listOf(file("a.txt"))'            | unsupportedWithDescription("'val' cannot be reassigned")
    }

    private void kotlinBuildFile(String inputDeclaration, String inputValue, String operation) {
        buildKotlinFile.text = """
            ${kotlinTypesDefinition()}

            abstract class MyTask: DefaultTask() {
                @get:Internal
                $inputDeclaration

                @TaskAction
                fun run() {
                    ${kotlinInputPrintRoutine()}
                }
            }

            tasks.register<MyTask>("myTask") {
                input $operation $inputValue
            }
        """
    }

    private void kotlinBuildFileWithVariable(String inputInitializer, String inputValue, String operation) {
        buildKotlinFile.text = """
            ${kotlinTypesDefinition()}

            tasks.register("myTask") {
                val input = $inputInitializer
                input $operation $inputValue

                doLast {
                    ${kotlinInputPrintRoutine()}
                }
            }
        """
    }

    private String kotlinTypesDefinition() {
        """
            enum class MyEnum {
                YES, NO
            }

            class MyObject(val value: String) {
                override fun toString(): String = value
            }
        """
    }

    private String kotlinInputPrintRoutine() {
        """
            when (val anyInput = input as Any?) {
               is DirectoryProperty -> println("$RESULT_PREFIX" + anyInput.map { it.asFile.name }.getOrElse("undefined"))
               is File -> println("$RESULT_PREFIX" + anyInput.name)
               is Provider<*> -> println("$RESULT_PREFIX" + anyInput.map { it.toString() }.getOrElse("undefined"))
               is FileCollection -> println("$RESULT_PREFIX" + anyInput.files.map { it.name })
               else -> println("$RESULT_PREFIX" + anyInput.toString())
            }
        """
    }
}
