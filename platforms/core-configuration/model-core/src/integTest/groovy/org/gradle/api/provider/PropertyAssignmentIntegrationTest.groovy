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
import org.gradle.integtests.fixtures.executer.ExecutionFailure

import static org.gradle.integtests.fixtures.executer.GradleContextualExecuter.configCache
import static org.hamcrest.CoreMatchers.containsString

class PropertyAssignmentIntegrationTest extends AbstractIntegrationSpec {

    private static final String RESULT_PREFIX = "Result: "

    def "test Groovy eager object types assignment for #description"() {
        def inputDeclaration = "$inputType input"
        groovyBuildFile(inputDeclaration, inputValue, "=")

        expect:
        runAndAssert("myTask", expectedResult)

        where:
        description                                     | inputType  | inputValue                               | expectedResult
        "T = T"                                         | "MyObject" | 'new MyObject("hello")'                  | "hello"
        "T = Provider<T>"                               | "MyObject" | 'provider { new MyObject("hello") }'     | unsupportedWithCause("Cannot cast object")
        "String = Object"                               | "String"   | 'new MyObject("hello")'                  | "hello"
        "Enum = String"                                 | "MyEnum"   | '"YES"'                                  | "YES"
        "File = T extends FileSystemLocation"           | "File"     | 'layout.buildDirectory.dir("out").get()' | unsupportedWithCause("Cannot cast object")
        "File = Provider<T extends FileSystemLocation>" | "File"     | 'layout.buildDirectory.dir("out")'       | unsupportedWithCause("Cannot cast object")
        "File = File"                                   | "File"     | 'file("$buildDir/out")'                  | "out"
        "File = Provider<File>"                         | "File"     | 'provider { file("$buildDir/out") }'     | unsupportedWithCause("Cannot cast object")
        "File = Object"                                 | "File"     | 'new MyObject("out")'                    | unsupportedWithCause("Cannot cast object")
    }

    def "test Groovy lazy object types assignment for #description"() {
        def inputDeclaration = "abstract $inputType getInput()"
        groovyBuildFile(inputDeclaration, inputValue, "=")

        expect:
        runAndAssert("myTask", expectedResult)

        where:
        description                                     | inputType            | inputValue                               | expectedResult
        "T = T"                                         | "Property<MyObject>" | 'new MyObject("hello")'                  | "hello"
        "T = Provider<T>"                               | "Property<MyObject>" | 'provider { new MyObject("hello") }'     | "hello"
        "String = Object"                               | "Property<String>"   | 'new MyObject("hello")'                  | unsupportedWithCause("Cannot set the value of task ':myTask' property 'input'")
        "Enum = String"                                 | "Property<MyEnum>"   | '"YES"'                                  | unsupportedWithCause("Cannot set the value of task ':myTask' property 'input'")
        "File = T extends FileSystemLocation"           | "DirectoryProperty"  | 'layout.buildDirectory.dir("out").get()' | "out"
        "File = Provider<T extends FileSystemLocation>" | "DirectoryProperty"  | 'layout.buildDirectory.dir("out")'       | "out"
        "File = File"                                   | "DirectoryProperty"  | 'file("$buildDir/out")'                  | "out"
        "File = Provider<File>"                         | "DirectoryProperty"  | 'provider { file("$buildDir/out") }'     | unsupportedWithCause("Cannot get the value of task ':myTask' property 'input'")
        "File = Object"                                 | "DirectoryProperty"  | 'new MyObject("out")'                    | unsupportedWithCause("Cannot set the value of task ':myTask' property 'input'")
    }

    def "test Groovy lazy object types assignment for 'property value' notation #description"() {
        def inputDeclaration = "abstract $inputType getInput()"
        groovyBuildFile(inputDeclaration, inputValue, " ")

        expect:
        runAndAssert("myTask", expectedResult)

        where:
        description                                   | inputType             | inputValue                               | expectedResult
        "T T"                                         | "Property<MyObject>"  | 'new MyObject("hello")'                  | "hello"
        "T Provider<T>"                               | "Property<MyObject>"  | 'provider { new MyObject("hello") }'     | "hello"
        "String Object"                               | "Property<String>"    | 'new MyObject("hello")'                  | unsupportedWithCause("Cannot set the value of task ':myTask' property 'input'")
        "Enum String"                                 | "Property<MyEnum>"    | '"YES"'                                  | unsupportedWithCause("Cannot set the value of task ':myTask' property 'input'")
        "File T extends FileSystemLocation"           | "DirectoryProperty"   | 'layout.buildDirectory.dir("out").get()' | "out"
        "File Provider<T extends FileSystemLocation>" | "DirectoryProperty"   | 'layout.buildDirectory.dir("out")'       | "out"
        "File File"                                   | "RegularFileProperty" | 'file("$buildDir/out.txt")'              | "out.txt"
        "File File"                                   | "DirectoryProperty"   | 'file("$buildDir/out")'                  | "out"
        "File Provider<File>"                         | "DirectoryProperty"   | 'provider { file("$buildDir/out") }'     | unsupportedWithCause("Cannot get the value of task ':myTask' property 'input'")
        "File Object"                                 | "DirectoryProperty"   | 'new MyObject("out")'                    | unsupportedWithCause("Cannot set the value of task ':myTask' property 'input'")
    }

    def "test Kotlin eager object types assignment for #description"() {
        def inputDeclaration = "var input: $inputType? = null"
        kotlinBuildFile(inputDeclaration, inputValue, "=")

        expect:
        runAndAssert("myTask", expectedResult)

        where:
        description                                     | inputType  | inputValue                               | expectedResult
        "T = T"                                         | "MyObject" | 'MyObject("hello")'                      | "hello"
        "T = Provider<T>"                               | "MyObject" | 'provider { MyObject("hello") }'         | unsupportedWithDescription("Type mismatch")
        "String = Object"                               | "String"   | 'MyObject("hello")'                      | unsupportedWithDescription("Type mismatch")
        "Enum = String"                                 | "MyEnum"   | '"YES"'                                  | unsupportedWithDescription("Type mismatch")
        "File = T extends FileSystemLocation"           | "File"     | 'layout.buildDirectory.dir("out").get()' | unsupportedWithDescription("Type mismatch")
        "File = Provider<T extends FileSystemLocation>" | "File"     | 'layout.buildDirectory.dir("out")'       | unsupportedWithDescription("Type mismatch")
        "File = File"                                   | "File"     | 'file("$buildDir/out")'                  | "out"
        "File = Provider<File>"                         | "File"     | 'provider { file("$buildDir/out") }'     | unsupportedWithDescription("Type mismatch")
        "File = Object"                                 | "File"     | 'MyObject("out")'                        | unsupportedWithDescription("Type mismatch")
    }

    def "test Kotlin lazy object types assignment for #description"() {
        def inputDeclaration = "abstract val input: $inputType"
        kotlinBuildFile(inputDeclaration, inputValue, "=")

        expect:
        runAndAssert("myTask", expectedResult)

        where:
        description                                     | inputType            | inputValue                               | expectedResult
        "T = T"                                         | "Property<MyObject>" | 'MyObject("hello")'                      | "hello"
        "T = Provider<T>"                               | "Property<MyObject>" | 'provider { MyObject("hello") }'         | "hello"
        "String = Object"                               | "Property<String>"   | 'MyObject("hello")'                      | unsupportedWithDescription("Type mismatch")
        "Enum = String"                                 | "Property<MyEnum>"   | '"YES"'                                  | unsupportedWithDescription("Type mismatch")
        "File = T extends FileSystemLocation"           | "DirectoryProperty"  | 'layout.buildDirectory.dir("out").get()' | "out"
        "File = Provider<T extends FileSystemLocation>" | "DirectoryProperty"  | 'layout.buildDirectory.dir("out")'       | "out"
        "File = File"                                   | "DirectoryProperty"  | 'file("$buildDir/out")'                  | "out"
        "File = Provider<File>"                         | "DirectoryProperty"  | 'provider { file("$buildDir/out") }'     | "out"
        "File = Object"                                 | "DirectoryProperty"  | 'MyObject("out")'                        | unsupportedWithDescription("Type mismatch")
    }

    def "test Groovy eager collection types assignment for #description"() {
        def initValue = inputType.contains("Map<") ? "[:]" : "[]"
        def inputDeclaration = "$inputType input = $initValue"
        groovyBuildFile(inputDeclaration, inputValue, operation)

        expect:
        runAndAssert("myTask", expectedResult)

        where:
        description                              | operation | inputType               | inputValue                                               | expectedResult
        "Collection<T> = T[]"                    | "="       | "List<MyObject>"        | '[new MyObject("a")] as MyObject[]'                      | '[a]'
        "Collection<T> = Iterable<T>"            | "="       | "List<MyObject>"        | '[new MyObject("a")] as Iterable<MyObject>'              | '[a]'
        "Collection<T> = Provider<Iterable<T>>"  | "="       | "List<MyObject>"        | 'provider { [new MyObject("a")] as Iterable<MyObject> }' | unsupportedWithCause("Cannot cast object")
        "Collection<T> += T"                     | "+="      | "List<MyObject>"        | 'new MyObject("a")'                                      | '[a]'
        "Collection<T> << T"                     | "<<"      | "List<MyObject>"        | 'new MyObject("a")'                                      | '[a]'
        "Collection<T> += Provider<T>"           | "+="      | "List<MyObject>"        | 'provider { new MyObject("a") }'                         | ('[fixed(class MyObject, a)]'.find { configCache } ?: '[provider(?)]')
        "Collection<T> << Provider<T>"           | "<<"      | "List<MyObject>"        | 'provider { new MyObject("a") }'                         | ('[fixed(class MyObject, a)]'.find { configCache } ?: '[provider(?)]')
        "Collection<T> += T[]"                   | "+="      | "List<MyObject>"        | '[new MyObject("a")] as MyObject[]'                      | '[[a]]'
        "Collection<T> << T[]"                   | "<<"      | "List<MyObject>"        | '[new MyObject("a")] as MyObject[]'                      | unsupportedWithCause("Cannot cast object")
        "Collection<T> += Iterable<T>"           | "+="      | "List<MyObject>"        | '[new MyObject("a")] as Iterable<MyObject>'              | '[a]'
        "Collection<T> << Iterable<T>"           | "<<"      | "List<MyObject>"        | '[new MyObject("a")] as Iterable<MyObject>'              | '[[a]]'
        "Collection<T> += Provider<Iterable<T>>" | "+="      | "List<MyObject>"        | 'provider { [new MyObject("a")] as Iterable<MyObject> }' | ('[fixed(class java.util.ArrayList, [a])]'.find { configCache } ?: '[provider(?)]')
        "Collection<T> << Provider<Iterable<T>>" | "<<"      | "List<MyObject>"        | 'provider { [new MyObject("a")] as Iterable<MyObject> }' | ('[fixed(class java.util.ArrayList, [a])]'.find { configCache } ?: '[provider(?)]')
        "Map<K, V> = Map<K, V>"                  | "="       | "Map<String, MyObject>" | '["a": new MyObject("b")]'                               | '[a:b]'
        "Map<K, V> = Provider<Map<K, V>>"        | "="       | "Map<String, MyObject>" | 'provider { ["a": new MyObject("b")] }'                  | unsupportedWithCause("Cannot cast object")
        "Map<K, V> += Map<K, V>"                 | "+="      | "Map<String, MyObject>" | '["a": new MyObject("b")]'                               | '[a:b]'
        "Map<K, V> << Map<K, V>"                 | "<<"      | "Map<String, MyObject>" | '["a": new MyObject("b")]'                               | '[a:b]'
        "Map<K, V> += Provider<Map<K, V>>"       | "+="      | "Map<String, MyObject>" | 'provider { ["a": new MyObject("b")] }'                  | unsupportedWithCause("No signature of method")
        "Map<K, V> << Provider<Map<K, V>>"       | "<<"      | "Map<String, MyObject>" | 'provider { ["a": new MyObject("b")] }'                  | unsupportedWithCause("No signature of method")
    }

    def "test Groovy lazy collection types assignment for #description"() {
        def inputDeclaration = "abstract $inputType getInput()"
        groovyBuildFile(inputDeclaration, inputValue, operation)

        expect:
        runAndAssert("myTask", expectedResult)

        where:
        description                              | operation | inputType                       | inputValue                                               | expectedResult
        "Collection<T> = T[]"                    | "="       | "ListProperty<MyObject>"        | '[new MyObject("a")] as MyObject[]'                      | unsupportedWithCause("Cannot set the value of a property of type java.util.List using an instance of type [LMyObject;")
        "Collection<T> = Iterable<T>"            | "="       | "ListProperty<MyObject>"        | '[new MyObject("a")] as Iterable<MyObject>'              | '[a]'
        "Collection<T> = Provider<Iterable<T>>"  | "="       | "ListProperty<MyObject>"        | 'provider { [new MyObject("a")] as Iterable<MyObject> }' | '[a]'
        "Collection<T> += T"                     | "+="      | "ListProperty<MyObject>"        | 'new MyObject("a")'                                      | unsupportedWithCause("No signature of method")
        "Collection<T> << T"                     | "<<"      | "ListProperty<MyObject>"        | 'new MyObject("a")'                                      | unsupportedWithCause("No signature of method")
        "Collection<T> += Provider<T>"           | "+="      | "ListProperty<MyObject>"        | 'provider { new MyObject("a") }'                         | unsupportedWithCause("No signature of method")
        "Collection<T> << Provider<T>"           | "<<"      | "ListProperty<MyObject>"        | 'provider { new MyObject("a") }'                         | unsupportedWithCause("No signature of method")
        "Collection<T> += T[]"                   | "+="      | "ListProperty<MyObject>"        | '[new MyObject("a")] as MyObject[]'                      | unsupportedWithCause("No signature of method")
        "Collection<T> << T[]"                   | "<<"      | "ListProperty<MyObject>"        | '[new MyObject("a")] as MyObject[]'                      | unsupportedWithCause("No signature of method")
        "Collection<T> += Iterable<T>"           | "+="      | "ListProperty<MyObject>"        | '[new MyObject("a")] as Iterable<MyObject>'              | unsupportedWithCause("No signature of method")
        "Collection<T> << Iterable<T>"           | "<<"      | "ListProperty<MyObject>"        | '[new MyObject("a")] as Iterable<MyObject>'              | unsupportedWithCause("No signature of method")
        "Collection<T> += Provider<Iterable<T>>" | "+="      | "ListProperty<MyObject>"        | 'provider { [new MyObject("a")] as Iterable<MyObject> }' | unsupportedWithCause("No signature of method")
        "Collection<T> << Provider<Iterable<T>>" | "<<"      | "ListProperty<MyObject>"        | 'provider { [new MyObject("a")] as Iterable<MyObject> }' | unsupportedWithCause("No signature of method")
        "Map<K, V> = Map<K, V>"                  | "="       | "MapProperty<String, MyObject>" | '["a": new MyObject("b")]'                               | '[a:b]'
        "Map<K, V> = Provider<Map<K, V>>"        | "="       | "MapProperty<String, MyObject>" | 'provider { ["a": new MyObject("b")] }'                  | '[a:b]'
        "Map<K, V> += Map<K, V>"                 | "+="      | "MapProperty<String, MyObject>" | '["a": new MyObject("b")]'                               | unsupportedWithCause("No signature of method")
        "Map<K, V> << Map<K, V>"                 | "<<"      | "MapProperty<String, MyObject>" | '["a": new MyObject("b")]'                               | unsupportedWithCause("No signature of method")
        "Map<K, V> += Provider<Map<K, V>>"       | "+="      | "MapProperty<String, MyObject>" | 'provider { ["a": new MyObject("b")] }'                  | unsupportedWithCause("No signature of method")
        "Map<K, V> << Provider<Map<K, V>>"       | "<<"      | "MapProperty<String, MyObject>" | 'provider { ["a": new MyObject("b")] }'                  | unsupportedWithCause("No signature of method")
    }

    def "test Kotlin eager collection types assignment for #description"() {
        def initValue = inputType.contains("Map<") ? "mutableMapOf<String, MyObject>()" : "mutableListOf<MyObject>()"
        def inputDeclaration = "var input: $inputType = $initValue"
        kotlinBuildFile(inputDeclaration, inputValue, operation)

        expect:
        runAndAssert("myTask", expectedResult)

        where:
        description                              | operation | inputType                      | inputValue                                                 | expectedResult
        "Collection<T> = T[]"                    | "="       | "List<MyObject>"               | 'arrayOf(MyObject("a"))'                                   | unsupportedWithDescription("Type mismatch")
        "Collection<T> = Iterable<T>"            | "="       | "List<MyObject>"               | 'listOf(MyObject("a")) as Iterable<MyObject>'              | unsupportedWithDescription("Type mismatch")
        "Collection<T> = Provider<Iterable<T>>"  | "="       | "List<MyObject>"               | 'provider { listOf(MyObject("a")) as Iterable<MyObject> }' | unsupportedWithDescription("Type mismatch")
        "Collection<T> += T"                     | "+="      | "MutableList<MyObject>"        | 'MyObject("a")'                                            | '[a]'
        "Collection<T> += Provider<T>"           | "+="      | "MutableList<MyObject>"        | 'provider { MyObject("a") }'                               | unsupportedWithDescription("Type mismatch")
        "Collection<T> += T[]"                   | "+="      | "MutableList<MyObject>"        | 'arrayOf(MyObject("a"))'                                   | '[a]'
        "Collection<T> += Iterable<T>"           | "+="      | "MutableList<MyObject>"        | 'listOf(MyObject("a")) as Iterable<MyObject>'              | '[a]'
        "Collection<T> += Provider<Iterable<T>>" | "+="      | "MutableList<MyObject>"        | 'provider { listOf(MyObject("a")) as Iterable<MyObject> }' | unsupportedWithDescription("Type mismatch")
        "Map<K, V> = Map<K, V>"                  | "="       | "Map<String, MyObject>"        | 'mapOf("a" to MyObject("b"))'                              | '{a=b}'
        "Map<K, V> = Provider<Map<K, V>>"        | "="       | "Map<String, MyObject>"        | 'provider { mapOf("a" to MyObject("b")) }'                 | unsupportedWithDescription("Type mismatch")
        "Map<K, V> += Pair<K, V>"                | "+="      | "MutableMap<String, MyObject>" | '"a" to MyObject("b")'                                     | '{a=b}'
        "Map<K, V> += Provider<Pair<K, V>>"      | "+="      | "MutableMap<String, MyObject>" | 'provider { "a" to MyObject("b") }'                        | unsupportedWithDescription("None of the following")
        "Map<K, V> += Map<K, V>"                 | "+="      | "MutableMap<String, MyObject>" | 'mapOf("a" to MyObject("b"))'                              | '{a=b}'
        "Map<K, V> += Provider<Map<K, V>>"       | "+="      | "MutableMap<String, MyObject>" | 'provider { mapOf("a" to MyObject("b")) }'                 | unsupportedWithDescription("None of the following")
    }

    def "test Kotlin lazy collection types assignment for #description"() {
        def inputDeclaration = "abstract val input: $inputType"
        kotlinBuildFile(inputDeclaration, inputValue, operation)

        expect:
        runAndAssert("myTask", expectedResult)

        where:
        description                              | operation | inputType                       | inputValue                                                 | expectedResult
        "Collection<T> = T[]"                    | "="       | "ListProperty<MyObject>"        | 'arrayOf(MyObject("a"))'                                   | unsupportedWithDescription("No applicable 'assign' function found for '=' overload")
        "Collection<T> = Iterable<T>"            | "="       | "ListProperty<MyObject>"        | 'listOf(MyObject("a")) as Iterable<MyObject>'              | '[a]'
        "Collection<T> = Provider<Iterable<T>>"  | "="       | "ListProperty<MyObject>"        | 'provider { listOf(MyObject("a")) as Iterable<MyObject> }' | '[a]'
        "Collection<T> += T"                     | "+="      | "ListProperty<MyObject>"        | 'MyObject("a")'                                            | unsupportedWithDescription("Unresolved reference. None of the following candidates is applicable because of receiver type mismatch:")
        "Collection<T> += Provider<T>"           | "+="      | "ListProperty<MyObject>"        | 'provider { MyObject("a") }'                               | unsupportedWithDescription("Unresolved reference. None of the following candidates is applicable because of receiver type mismatch:")
        "Collection<T> += T[]"                   | "+="      | "ListProperty<MyObject>"        | 'arrayOf(MyObject("a"))'                                   | unsupportedWithDescription("Unresolved reference. None of the following candidates is applicable because of receiver type mismatch:")
        "Collection<T> += Iterable<T>"           | "+="      | "ListProperty<MyObject>"        | 'listOf(MyObject("a")) as Iterable<MyObject>'              | unsupportedWithDescription("Unresolved reference. None of the following candidates is applicable because of receiver type mismatch:")
        "Collection<T> += Provider<Iterable<T>>" | "+="      | "ListProperty<MyObject>"        | 'provider { listOf(MyObject("a")) as Iterable<MyObject> }' | unsupportedWithDescription("Unresolved reference. None of the following candidates is applicable because of receiver type mismatch:")
        "Map<K, V> = Map<K, V>"                  | "="       | "MapProperty<String, MyObject>" | 'mapOf("a" to MyObject("b"))'                              | '{a=b}'
        "Map<K, V> = Provider<Map<K, V>>"        | "="       | "MapProperty<String, MyObject>" | 'provider { mapOf("a" to MyObject("b")) }'                 | '{a=b}'
        "Map<K, V> += Pair<K, V>"                | "+="      | "MapProperty<String, MyObject>" | '"a" to MyObject("b")'                                     | unsupportedWithDescription("Unresolved reference. None of the following candidates is applicable because of receiver type mismatch:")
        "Map<K, V> += Provider<Pair<K, V>>"      | "+="      | "MapProperty<String, MyObject>" | 'provider { "a" to MyObject("b") }'                        | unsupportedWithDescription("Unresolved reference. None of the following candidates is applicable because of receiver type mismatch:")
        "Map<K, V> += Map<K, V>"                 | "+="      | "MapProperty<String, MyObject>" | 'mapOf("a" to MyObject("b"))'                              | unsupportedWithDescription("Unresolved reference. None of the following candidates is applicable because of receiver type mismatch:")
        "Map<K, V> += Provider<Map<K, V>>"       | "+="      | "MapProperty<String, MyObject>" | 'provider { mapOf("a" to MyObject("b")) }'                 | unsupportedWithDescription("Unresolved reference. None of the following candidates is applicable because of receiver type mismatch:")
    }

    def "test Groovy eager FileCollection types assignment for #description"() {
        def inputDeclaration = "$inputType input = project.files()"
        groovyBuildFile(inputDeclaration, inputValue, operation)

        expect:
        runAndAssert("myTask", expectedResult)

        where:
        description                        | operation | inputType        | inputValue        | expectedResult
        "FileCollection = FileCollection"  | "="       | "FileCollection" | 'files("a.txt")'  | '[a.txt]'
        "FileCollection = Object"          | "="       | "FileCollection" | '"a.txt"'         | unsupportedWithCause("Cannot cast object")
        "FileCollection = File"            | "="       | "FileCollection" | 'file("a.txt")'   | unsupportedWithCause("Cannot cast object")
        "FileCollection = Iterable<File>"  | "="       | "FileCollection" | '[file("a.txt")]' | unsupportedWithCause("Cannot cast object")
        "FileCollection += FileCollection" | "+="      | "FileCollection" | 'files("a.txt")'  | '[a.txt]'
        "FileCollection << FileCollection" | "<<"      | "FileCollection" | 'files("a.txt")'  | unsupportedWithCause("No signature of method")
        "FileCollection += Object"         | "+="      | "FileCollection" | '"a.txt"'         | unsupportedWithCause("Cannot cast object")
        "FileCollection += File"           | "+="      | "FileCollection" | 'file("a.txt")'   | unsupportedWithCause("Cannot cast object")
        "FileCollection += Iterable<?>"    | "+="      | "FileCollection" | '["a.txt"]'       | unsupportedWithCause("Cannot cast object")
        "FileCollection += Iterable<File>" | "+="      | "FileCollection" | '[file("a.txt")]' | unsupportedWithCause("Cannot cast object")
    }

    def "test Groovy lazy FileCollection types assignment for #description"() {
        def inputDeclaration = "abstract $inputType getInput()"
        groovyBuildFile(inputDeclaration, inputValue, operation)

        expect:
        runAndAssert("myTask", expectedResult)

        where:
        description                        | operation | inputType                    | inputValue              | expectedResult
        "FileCollection = FileCollection"  | "="       | "ConfigurableFileCollection" | 'files("a.txt")'        | '[a.txt]'
        "FileCollection = String"          | "="       | "ConfigurableFileCollection" | '"a.txt"'               | unsupportedWithCause("Failed to cast object")
        "FileCollection = Object"          | "="       | "ConfigurableFileCollection" | 'new MyObject("a.txt")' | unsupportedWithCause("Failed to cast object")
        "FileCollection = File"            | "="       | "ConfigurableFileCollection" | 'file("a.txt")'         | unsupportedWithCause("Failed to cast object")
        "FileCollection = Iterable<File>"  | "="       | "ConfigurableFileCollection" | '[file("a.txt")]'       | unsupportedWithCause("Failed to cast object")
        "FileCollection += FileCollection" | "+="      | "ConfigurableFileCollection" | 'files("a.txt")'        | unsupportedWithCause("Self-referencing ConfigurableFileCollections are not supported. Use the from() method to add to a ConfigurableFileCollection.")
        "FileCollection << FileCollection" | "<<"      | "ConfigurableFileCollection" | 'files("a.txt")'        | unsupportedWithCause("No signature of method")
        "FileCollection += String"         | "+="      | "ConfigurableFileCollection" | '"a.txt"'               | unsupportedWithCause("Failed to cast object")
        "FileCollection += Object"         | "+="      | "ConfigurableFileCollection" | 'new MyObject("a.txt")' | unsupportedWithCause("Failed to cast object")
        "FileCollection += File"           | "+="      | "ConfigurableFileCollection" | 'file("a.txt")'         | unsupportedWithCause("Failed to cast object")
        "FileCollection += Iterable<?>"    | "+="      | "ConfigurableFileCollection" | '["a.txt"]'             | unsupportedWithCause("Failed to cast object")
        "FileCollection += Iterable<File>" | "+="      | "ConfigurableFileCollection" | '[file("a.txt")]'       | unsupportedWithCause("Failed to cast object")
    }

    def "test Kotlin eager FileCollection types assignment for #description"() {
        def inputDeclaration = "var input: $inputType = project.files()"
        kotlinBuildFile(inputDeclaration, inputValue, operation)

        expect:
        runAndAssert("myTask", expectedResult)

        where:
        description                        | operation | inputType        | inputValue                         | expectedResult
        "FileCollection = FileCollection"  | "="       | "FileCollection" | 'files("a.txt") as FileCollection' | '[a.txt]'
        "FileCollection = String"          | "="       | "FileCollection" | '"a.txt"'                          | unsupportedWithDescription("Type mismatch")
        "FileCollection = Object"          | "="       | "FileCollection" | 'MyObject("a.txt")'                | unsupportedWithDescription("Type mismatch")
        "FileCollection = File"            | "="       | "FileCollection" | 'file("a.txt")'                    | unsupportedWithDescription("Type mismatch")
        "FileCollection = Iterable<File>"  | "="       | "FileCollection" | 'listOf(file("a.txt"))'            | unsupportedWithDescription("Type mismatch")
        "FileCollection += FileCollection" | "+="      | "FileCollection" | 'files("a.txt") as FileCollection' | '[a.txt]'
        "FileCollection += String"         | "+="      | "FileCollection" | '"a.txt"'                          | unsupportedWithDescription("Type mismatch")
        "FileCollection += Object"         | "+="      | "FileCollection" | 'MyObject("a.txt")'                | unsupportedWithDescription("Type mismatch")
        "FileCollection += File"           | "+="      | "FileCollection" | 'file("a.txt")'                    | unsupportedWithDescription("Type mismatch")
        "FileCollection += Iterable<?>"    | "+="      | "FileCollection" | 'listOf("a.txt")'                  | unsupportedWithDescription("Type mismatch")
        "FileCollection += Iterable<File>" | "+="      | "FileCollection" | 'listOf(file("a.txt"))'            | unsupportedWithDescription("Type mismatch")
    }

    def "test Kotlin lazy FileCollection types assignment for #description"() {
        def inputDeclaration = "abstract val input: $inputType"
        kotlinBuildFile(inputDeclaration, inputValue, operation)

        expect:
        runAndAssert("myTask", expectedResult)

        where:
        description                        | operation | inputType                    | inputValue                         | expectedResult
        "FileCollection = FileCollection"  | "="       | "ConfigurableFileCollection" | 'files("a.txt") as FileCollection' | '[a.txt]'
        "FileCollection = String"          | "="       | "ConfigurableFileCollection" | '"a.txt"'                          | unsupportedWithDescription("Val cannot be reassigned")
        "FileCollection = Object"          | "="       | "ConfigurableFileCollection" | 'MyObject("a.txt")'                | unsupportedWithDescription("Val cannot be reassigned")
        "FileCollection = File"            | "="       | "ConfigurableFileCollection" | 'file("a.txt")'                    | unsupportedWithDescription("Val cannot be reassigned")
        "FileCollection = Iterable<File>"  | "="       | "ConfigurableFileCollection" | 'listOf(file("a.txt"))'            | unsupportedWithDescription("Val cannot be reassigned")
        "FileCollection += FileCollection" | "+="      | "ConfigurableFileCollection" | 'files("a.txt") as FileCollection' | unsupportedWithDescription("Val cannot be reassigned")
        "FileCollection += String"         | "+="      | "ConfigurableFileCollection" | '"a.txt"'                          | unsupportedWithDescription("Val cannot be reassigned")
        "FileCollection += Object"         | "+="      | "ConfigurableFileCollection" | 'MyObject("a.txt")'                | unsupportedWithDescription("Val cannot be reassigned")
        "FileCollection += File"           | "+="      | "ConfigurableFileCollection" | 'file("a.txt")'                    | unsupportedWithDescription("Val cannot be reassigned")
        "FileCollection += Iterable<?>"    | "+="      | "ConfigurableFileCollection" | 'listOf("a.txt")'                  | unsupportedWithDescription("Val cannot be reassigned")
        "FileCollection += Iterable<File>" | "+="      | "ConfigurableFileCollection" | 'listOf(file("a.txt"))'            | unsupportedWithDescription("Val cannot be reassigned")
    }

    def "test Groovy lazy property assignment with NamedDomainObjectContainer"() {
        buildFile """
            abstract class PluginDeclaration {
                final String name
                final Property<String> id
                abstract Property<String> getDescription()
                abstract ListProperty<String> getTags()
                abstract ConfigurableFileCollection getMyFiles()

                PluginDeclaration(String name, ObjectFactory objectFactory) {
                    this.id = objectFactory.property(String.class)
                    this.name = name
                }
            }

            project.extensions.add('pluginDeclarations', project.container(PluginDeclaration))

            pluginDeclarations {
                myPlugin {
                    id = "my-id"
                    description = "hello"
                    tags = ["tag1", "tag2"]
                    myFiles = files("a/b/c")
                }
            }

            pluginDeclarations.all {
                assert it.id.get() == "my-id"
                assert it.description.get() == "hello"
                assert it.tags.get() == ["tag1", "tag2"]
                assert it.myFiles.files == files("a/b/c").files
            }
        """

        expect:
        run("help")
    }

    private void groovyBuildFile(String inputDeclaration, String inputValue, String operation) {
        buildFile.text = """
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
                $inputDeclaration

                @TaskAction
                void run() {
                    if (input instanceof FileSystemLocationProperty) {
                        println("$RESULT_PREFIX" + input.get().asFile.name)
                    } else if (input instanceof File) {
                        println("$RESULT_PREFIX" + input.name)
                    } else if (input instanceof Provider) {
                        println("$RESULT_PREFIX" + input.get())
                    } else if (input instanceof FileCollection) {
                        println("$RESULT_PREFIX" + input.files.collect { it.name })
                    } else {
                        println("$RESULT_PREFIX" + input)
                    }
                }
            }

            tasks.register("myTask", MyTask) {
                input $operation $inputValue
            }
        """
    }

    private void kotlinBuildFile(String inputDeclaration, String inputValue, String operation) {
        buildKotlinFile.text = """
            enum class MyEnum {
                YES, NO
            }

            class MyObject(val value: String) {
                override fun toString(): String = value
            }

            abstract class MyTask: DefaultTask() {
                @get:Internal
                $inputDeclaration

                @TaskAction
                fun run() {
                    when (val anyInput = input as Any?) {
                       is DirectoryProperty -> println("$RESULT_PREFIX" + anyInput.get().asFile.name)
                       is File -> println("$RESULT_PREFIX" + anyInput.name)
                       is Provider<*> -> println("$RESULT_PREFIX" + anyInput.get())
                       is FileCollection -> println("$RESULT_PREFIX" + anyInput.files.map { it.name })
                       else -> println("$RESULT_PREFIX" + anyInput)
                    }
                }
            }

            tasks.register<MyTask>("myTask") {
                input $operation $inputValue
            }
        """
    }

    private void runAndAssert(String task, Object expectedResult) {
        if (expectedResult instanceof Failure) {
            def failure = runAndFail(task)
            expectedResult.assertHasExpectedFailure(failure)
        } else {
            run(task)
            outputContains(RESULT_PREFIX + expectedResult)
        }
    }

    private static FailureWithCause unsupportedWithCause(String failureCause) {
        return new FailureWithCause(failureCause)
    }

    private static FailureWithDescription unsupportedWithDescription(String error) {
        return new FailureWithDescription(error)
    }

    private static interface Failure {
        void assertHasExpectedFailure(ExecutionFailure failure);
    }

    private static class FailureWithCause implements Failure {
        final String failureCause

        FailureWithCause(String failureCause) {
            this.failureCause = failureCause
        }

        @Override
        void assertHasExpectedFailure(ExecutionFailure failure) {
            failure.assertHasCause(failureCause)
        }
    }

    private static class FailureWithDescription implements Failure {
        final String failureDescription

        FailureWithDescription(String failureDescription) {
            this.failureDescription = failureDescription
        }

        @Override
        void assertHasExpectedFailure(ExecutionFailure failure) {
            failure.assertThatDescription(containsString(failureDescription))
        }
    }
}
