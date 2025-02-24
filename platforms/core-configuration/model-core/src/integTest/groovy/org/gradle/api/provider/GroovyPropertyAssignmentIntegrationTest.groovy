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

import org.gradle.api.internal.provider.support.CompoundAssignmentSupport
import org.gradle.util.internal.ToBeImplemented
import org.junit.Assume

import static org.gradle.integtests.fixtures.executer.GradleContextualExecuter.configCache

class GroovyPropertyAssignmentIntegrationTest extends AbstractProviderOperatorIntegrationTest {
    protected static final String EXPRESSION_PREFIX = "Expression value: "

    @Override
    def setup() {
        if (compoundAssignmentSupported()) {
            executer.withArgument("-D${CompoundAssignmentSupport.FEATURE_FLAG_NAME}=true")
        }
    }

    boolean compoundAssignmentSupported() { false }

    private def withCompoundAssignment(def expectedResultWithCompoundAssignment) {
        if (compoundAssignmentSupported()) {
            return expectedResultWithCompoundAssignment
        }
        return unsupportedWithCause("Cannot call plus() on")
    }

    static class GroovyPropertyAssignmentWithCompoundEnabledIntegrationTest extends GroovyPropertyAssignmentIntegrationTest {
        @Override
        boolean compoundAssignmentSupported() { true }
    }

    def "eager object properties assignment for #description"() {
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
        "File = File"                                   | "File"     | 'file("out")'                            | "out"
        "File = Provider<File>"                         | "File"     | 'provider { file("out") }'               | unsupportedWithCause("Cannot cast object")
        "File = Object"                                 | "File"     | 'new MyObject("out")'                    | unsupportedWithCause("Cannot cast object")
    }

    def "lazy object properties assignment for #description"() {
        def inputDeclaration = "abstract $inputType getInput()"
        groovyBuildFile(inputDeclaration, inputValue, "=")

        expect:
        runAndAssert("myTask", expectedResult)

        where:
        description                                     | inputType            | inputValue                               | expectedResult
        "T = null"                                      | "Property<MyObject>" | 'null'                                   | "undefined"
        "T = T"                                         | "Property<MyObject>" | 'new MyObject("hello")'                  | "hello"
        "T = provider { null }"                         | "Property<MyObject>" | 'provider { null }'                      | "undefined"
        "T = Provider<T>"                               | "Property<MyObject>" | 'provider { new MyObject("hello") }'     | "hello"
        "String = Object"                               | "Property<String>"   | 'new MyObject("hello")'                  | unsupportedWithCause("Cannot set the value of task ':myTask' property 'input'")
        "File = T extends FileSystemLocation"           | "DirectoryProperty"  | 'layout.buildDirectory.dir("out").get()' | "out"
        "File = Provider<T extends FileSystemLocation>" | "DirectoryProperty"  | 'layout.buildDirectory.dir("out")'       | "out"
        "File = File"                                   | "DirectoryProperty"  | 'file("out")'                            | "out"
        "File = Provider<File>"                         | "DirectoryProperty"  | 'provider { file("out") }'               | unsupportedWithCause("Cannot get the value of task ':myTask' property 'input'")
        "File = Object"                                 | "DirectoryProperty"  | 'new MyObject("out")'                    | unsupportedWithCause("Cannot set the value of task ':myTask' property 'input'")
    }

    def "lazy object properties assignment for deprecated string to enum coercion"() {
        def inputDeclaration = "abstract $inputType getInput()"
        groovyBuildFile(inputDeclaration, inputValue, "=")

        expect:
        deprecationValues.forEach {
            executer.expectDocumentedDeprecationWarning("Assigning String value '$it' to property of enum type 'MyEnum'. This behavior has been deprecated. This will fail with an error in Gradle 10.0. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#deprecated_string_to_enum_coercion_for_rich_properties")
        }
        runAndAssert("myTask", expectedResult)

        where:
        description                 | inputType              | inputValue      | expectedResult | deprecationValues
        "Enum = String"             | "Property<MyEnum>"     | '"yes"'         | "YES"          | ["yes"]
        "List<Enum> = List<String>" | "ListProperty<MyEnum>" | '["yes", "NO"]' | "[YES, NO]"    | ["yes", "NO"]
    }

    def "eager collection properties assignment for #description"() {
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

    def "lazy collection properties assignment for #description"() {
        def inputDeclaration = "abstract $inputType getInput()"
        groovyBuildFile(inputDeclaration, inputValue, operation)

        expect:
        runAndAssert("myTask", expectedResult)
        if (expectedResult !instanceof Failure) {
            assertExpression(expressionValue)
        }

        where:
        description                               | operation | inputType                       | inputValue                                               | expressionValue | expectedResult
        "Collection<T> = null"                    | "="       | "ListProperty<MyObject>"        | 'null'                                                   | 'null'          | 'undefined'
        "Collection<T> = T[]"                     | "="       | "ListProperty<MyObject>"        | '[new MyObject("a")] as MyObject[]'                      | _               | unsupportedWithCause("Cannot set the value of a property of type java.util.List using an instance of type [LMyObject;")
        "Collection<T> = Iterable<T>"             | "="       | "ListProperty<MyObject>"        | '[new MyObject("a")] as Iterable<MyObject>'              | '[a]'           | '[a]'
        "Collection<T> = provider { null }"       | "="       | "ListProperty<MyObject>"        | 'provider { null }'                                      | 'undefined'     | 'undefined'
        "Collection<T> = Provider<Iterable<T>>"   | "="       | "ListProperty<MyObject>"        | 'provider { [new MyObject("a")] as Iterable<MyObject> }' | '[a]'           | '[a]'
        "Collection<T> += T"                      | "+="      | "ListProperty<MyObject>"        | 'new MyObject("a")'                                      | 'null'          | withCompoundAssignment('[a]')
        "Collection<T> += !T"                     | "+="      | "ListProperty<MyObject>"        | '"a"'                                                    | _               | withCompoundAssignment(unsupportedWithCause("Cannot add an element of type String to a property of type List<MyObject>"))
        "Collection<T> << T"                      | "<<"      | "ListProperty<MyObject>"        | 'new MyObject("a")'                                      | _               | unsupportedWithCause("No signature of method")
        "Collection<T> += provider { null }"      | "+="      | "ListProperty<MyObject>"        | 'provider { null }'                                      | 'null'          | withCompoundAssignment('undefined')
        "Collection<T> += Provider<T>"            | "+="      | "ListProperty<MyObject>"        | 'provider { new MyObject("a") }'                         | 'null'          | withCompoundAssignment('[a]')
        "Collection<T> += Provider<!T>"           | "+="      | "ListProperty<MyObject>"        | 'provider { "a" }'                                       | _               | withCompoundAssignment(unsupportedWithCause("Cannot get the value of a property of type java.util.List with element type MyObject as the source value contains an element of type java.lang.String"))
        "Collection<T> << Provider<T>"            | "<<"      | "ListProperty<MyObject>"        | 'provider { new MyObject("a") }'                         | _               | unsupportedWithCause("No signature of method")
        "Collection<T> += T[]"                    | "+="      | "ListProperty<MyObject>"        | '[new MyObject("a")] as MyObject[]'                      | 'null'          | withCompoundAssignment('[a]')
        "Collection<T> << T[]"                    | "<<"      | "ListProperty<MyObject>"        | '[new MyObject("a")] as MyObject[]'                      | _               | unsupportedWithCause("No signature of method")
        "Collection<T> += Iterable<T>"            | "+="      | "ListProperty<MyObject>"        | '[new MyObject("a")] as Iterable<MyObject>'              | 'null'          | withCompoundAssignment('[a]')
        "Collection<T> << Iterable<T>"            | "<<"      | "ListProperty<MyObject>"        | '[new MyObject("a")] as Iterable<MyObject>'              | _               | unsupportedWithCause("No signature of method")
        "Collection<T> += Provider<Iterable<T>>"  | "+="      | "ListProperty<MyObject>"        | 'provider { [new MyObject("a")] as Iterable<MyObject> }' | 'null'          | withCompoundAssignment('[a]')
        "Collection<T> += Provider<Iterable<!T>>" | "+="      | "ListProperty<MyObject>"        | 'provider { ["a"] as Iterable<String> }'                 | _               | withCompoundAssignment(unsupportedWithCause("Cannot get the value of a property of type java.util.List with element type MyObject as the source value contains an element of type java.lang.String."))
        "Collection<T> << Provider<Iterable<T>>"  | "<<"      | "ListProperty<MyObject>"        | 'provider { [new MyObject("a")] as Iterable<MyObject> }' | _               | unsupportedWithCause("No signature of method")
        "Map<K, V> = null"                        | "="       | "MapProperty<String, MyObject>" | 'null'                                                   | 'null'          | 'undefined'
        "Map<K, V> = Map<K, V>"                   | "="       | "MapProperty<String, MyObject>" | '["a": new MyObject("b")]'                               | '[a:b]'         | '{a=b}'
        "Map<K, V> = provider { null }"           | "="       | "MapProperty<String, MyObject>" | 'provider { null }'                                      | 'undefined'     | 'undefined'
        "Map<K, V> = Provider<Map<K, V>>"         | "="       | "MapProperty<String, MyObject>" | 'provider { ["a": new MyObject("b")] }'                  | '[a:b]'         | '{a=b}'
        "Map<K, V> += Map<K, V>"                  | "+="      | "MapProperty<String, MyObject>" | '["a": new MyObject("b")]'                               | 'null'          | withCompoundAssignment('{a=b}')
        "Map<K, V> << Map<K, V>"                  | "<<"      | "MapProperty<String, MyObject>" | '["a": new MyObject("b")]'                               | _               | unsupportedWithCause("No signature of method")
        "Map<K, V> += Provider<Map<K, V>>"        | "+="      | "MapProperty<String, MyObject>" | 'provider { ["a": new MyObject("b")] }'                  | 'null'          | withCompoundAssignment('{a=b}')
        "Map<K, V> << Provider<Map<K, V>>"        | "<<"      | "MapProperty<String, MyObject>" | 'provider { ["a": new MyObject("b")] }'                  | _               | unsupportedWithCause("No signature of method")
    }

    def "lazy collection variables assignment for #description"() {
        def inputInitializer = inputType.startsWith("ListProperty<") ? "objects.listProperty(MyObject)" : "objects.mapProperty(String, MyObject)"
        groovyBuildFileWithVariable(inputInitializer, inputValue, operation)

        expect:
        runAndAssert("myTask", expectedResult)
        if (expectedResult !instanceof Failure) {
            assertExpression(expressionValue)
        }

        where:
        description                              | operation | inputType                       | inputValue                                               | expressionValue | expectedResult
        "Collection<T> = null"                   | "="       | "ListProperty<MyObject>"        | 'null'                                                   | 'null'          | 'null'
        "Collection<T> = T[]"                    | "="       | "ListProperty<MyObject>"        | '[new MyObject("a")] as MyObject[]'                      | '[a]'           | '[a]'
        "Collection<T> = Iterable<T>"            | "="       | "ListProperty<MyObject>"        | '[new MyObject("a")] as Iterable<MyObject>'              | '[a]'           | '[a]'
        "Collection<T> = provider { null }"      | "="       | "ListProperty<MyObject>"        | 'provider { null }'                                      | 'undefined'     | 'undefined'
        "Collection<T> = Provider<Iterable<T>>"  | "="       | "ListProperty<MyObject>"        | 'provider { [new MyObject("a")] as Iterable<MyObject> }' | '[a]'           | '[a]'
        "Collection<T> += T"                     | "+="      | "ListProperty<MyObject>"        | 'new MyObject("a")'                                      | 'null'          | withCompoundAssignment('[a]')
        "Collection<T> << T"                     | "<<"      | "ListProperty<MyObject>"        | 'new MyObject("a")'                                      | _               | unsupportedWithCause("No signature of method")
        "Collection<T> += provider { null }"     | "+="      | "ListProperty<MyObject>"        | 'provider { null }'                                      | 'null'          | withCompoundAssignment('undefined')
        "Collection<T> += Provider<T>"           | "+="      | "ListProperty<MyObject>"        | 'provider { new MyObject("a") }'                         | 'null'          | withCompoundAssignment('[a]')
        "Collection<T> << Provider<T>"           | "<<"      | "ListProperty<MyObject>"        | 'provider { new MyObject("a") }'                         | _               | unsupportedWithCause("No signature of method")
        "Collection<T> += T[]"                   | "+="      | "ListProperty<MyObject>"        | '[new MyObject("a")] as MyObject[]'                      | 'null'          | withCompoundAssignment('[a]')
        "Collection<T> << T[]"                   | "<<"      | "ListProperty<MyObject>"        | '[new MyObject("a")] as MyObject[]'                      | _               | unsupportedWithCause("No signature of method")
        "Collection<T> += Iterable<T>"           | "+="      | "ListProperty<MyObject>"        | '[new MyObject("a")] as Iterable<MyObject>'              | 'null'          | withCompoundAssignment('[a]')
        "Collection<T> << Iterable<T>"           | "<<"      | "ListProperty<MyObject>"        | '[new MyObject("a")] as Iterable<MyObject>'              | _               | unsupportedWithCause("No signature of method")
        "Collection<T> += Provider<Iterable<T>>" | "+="      | "ListProperty<MyObject>"        | 'provider { [new MyObject("a")] as Iterable<MyObject> }' | 'null'          | withCompoundAssignment('[a]')
        "Collection<T> << Provider<Iterable<T>>" | "<<"      | "ListProperty<MyObject>"        | 'provider { [new MyObject("a")] as Iterable<MyObject> }' | _               | unsupportedWithCause("No signature of method")
        "Map<K, V> = null"                       | "="       | "MapProperty<String, MyObject>" | 'null'                                                   | 'null'          | 'null'
        "Map<K, V> = Map<K, V>"                  | "="       | "MapProperty<String, MyObject>" | '["a": new MyObject("b")]'                               | '[a:b]'         | '[a:b]'
        "Map<K, V> = provider { null }"          | "="       | "MapProperty<String, MyObject>" | 'provider { null }'                                      | 'undefined'     | 'undefined'
        "Map<K, V> = Provider<Map<K, V>>"        | "="       | "MapProperty<String, MyObject>" | 'provider { ["a": new MyObject("b")] }'                  | '[a:b]'         | '[a:b]'
        "Map<K, V> += Map<K, V>"                 | "+="      | "MapProperty<String, MyObject>" | '["a": new MyObject("b")]'                               | 'null'          | withCompoundAssignment('[a:b]')
        "Map<K, V> << Map<K, V>"                 | "<<"      | "MapProperty<String, MyObject>" | '["a": new MyObject("b")]'                               | _               | unsupportedWithCause("No signature of method")
        "Map<K, V> += Provider<Map<K, V>>"       | "+="      | "MapProperty<String, MyObject>" | 'provider { ["a": new MyObject("b")] }'                  | 'null'          | withCompoundAssignment('[a:b]')
        "Map<K, V> << Provider<Map<K, V>>"       | "<<"      | "MapProperty<String, MyObject>" | 'provider { ["a": new MyObject("b")] }'                  | _               | unsupportedWithCause("No signature of method")
    }

    def "eager FileCollection properties assignment for #description"() {
        def inputDeclaration = "$inputType input = project.files()"
        groovyBuildFile(inputDeclaration, inputValue, operation)

        expect:
        runAndAssert("myTask", expectedResult)
        if (expectedResult !instanceof Failure) {
            assertExpression(expectedResult)
        }

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

    def "lazy FileCollection properties assignment for #description"() {
        def inputDeclaration = "abstract $inputType getInput()"
        groovyBuildFile(inputDeclaration, inputValue, operation)

        expect:
        runAndAssert("myTask", expectedResult)
        if (expectedResult !instanceof Failure) {
            assertExpression(expectedResult)
        }

        where:
        description                        | operation | inputType                    | inputValue              | expectedResult
        "FileCollection = FileCollection"  | "="       | "ConfigurableFileCollection" | 'files("a.txt")'        | '[a.txt]'
        "FileCollection = String"          | "="       | "ConfigurableFileCollection" | '"a.txt"'               | unsupportedWithCause("Failed to cast object")
        "FileCollection = Object"          | "="       | "ConfigurableFileCollection" | 'new MyObject("a.txt")' | unsupportedWithCause("Failed to cast object")
        "FileCollection = File"            | "="       | "ConfigurableFileCollection" | 'file("a.txt")'         | unsupportedWithCause("Failed to cast object")
        "FileCollection = Iterable<File>"  | "="       | "ConfigurableFileCollection" | '[file("a.txt")]'       | unsupportedWithCause("Failed to cast object")
        "FileCollection += FileCollection" | "+="      | "ConfigurableFileCollection" | 'files("a.txt")'        | (compoundAssignmentSupported() ? '[a.txt]' : unsupportedWithCause("Self-referencing ConfigurableFileCollections are not supported. Use the from() method to add to a ConfigurableFileCollection."))
        "FileCollection << FileCollection" | "<<"      | "ConfigurableFileCollection" | 'files("a.txt")'        | unsupportedWithCause("No signature of method")
        "FileCollection += String"         | "+="      | "ConfigurableFileCollection" | '"a.txt"'               | unsupportedWithCause("Failed to cast object")
        "FileCollection += Object"         | "+="      | "ConfigurableFileCollection" | 'new MyObject("a.txt")' | unsupportedWithCause("Failed to cast object")
        "FileCollection += File"           | "+="      | "ConfigurableFileCollection" | 'file("a.txt")'         | unsupportedWithCause("Failed to cast object")
        "FileCollection += Iterable<?>"    | "+="      | "ConfigurableFileCollection" | '["a.txt"]'             | unsupportedWithCause("Failed to cast object")
        "FileCollection += Iterable<File>" | "+="      | "ConfigurableFileCollection" | '[file("a.txt")]'       | unsupportedWithCause("Failed to cast object")
    }

    def "lazy FileCollection variables assignment for #description"() {
        def inputInitializer = "files()"
        groovyBuildFileWithVariable(inputInitializer, inputValue, operation, expectedType)

        expect:
        runAndAssert("myTask", expectedResult)
        if (expectedResult !instanceof Failure) {
            assertExpression(expectedResult)
        }

        where:
        description                        | operation | inputType                    | inputValue              | expectedType                 | expectedResult
        "FileCollection = FileCollection"  | "="       | "ConfigurableFileCollection" | 'files("a.txt")'        | "ConfigurableFileCollection" | '[a.txt]'
        "FileCollection = String"          | "="       | "ConfigurableFileCollection" | '"a.txt"'               | "String"                     | "a.txt"
        "FileCollection = Object"          | "="       | "ConfigurableFileCollection" | 'new MyObject("a.txt")' | "MyObject"                   | "a.txt"
        "FileCollection = File"            | "="       | "ConfigurableFileCollection" | 'file("a.txt")'         | "File"                       | "a.txt"
        "FileCollection = Iterable<File>"  | "="       | "ConfigurableFileCollection" | '[file("a.txt")]'       | "List"                       | "[a.txt]"
        "FileCollection += FileCollection" | "+="      | "ConfigurableFileCollection" | 'files("a.txt")'        | "FileCollection"             | "[a.txt]"
        "FileCollection << FileCollection" | "<<"      | "ConfigurableFileCollection" | 'files("a.txt")'        | ""                           | unsupportedWithCause("No signature of method")
        "FileCollection += String"         | "+="      | "ConfigurableFileCollection" | '"a.txt"'               | "List"                       | "[a.txt]"
        "FileCollection += Object"         | "+="      | "ConfigurableFileCollection" | 'new MyObject("a.txt")' | "List"                       | "[a.txt]"
        "FileCollection += File"           | "+="      | "ConfigurableFileCollection" | 'file("a.txt")'         | "List"                       | "[a.txt]"
        "FileCollection += Iterable<?>"    | "+="      | "ConfigurableFileCollection" | '["a.txt"]'             | "List"                       | "[a.txt]"
        "FileCollection += Iterable<File>" | "+="      | "ConfigurableFileCollection" | '[file("a.txt")]'       | "List"                       | "[a.txt]"
    }

    def "variable can be used as task input after #description"() {
        buildFile """
            ${groovyTypesDefinition()}
            import static ${org.gradle.api.internal.provider.Providers.name}.changing

            abstract class MyTask extends DefaultTask {
                @Input
                abstract $inputType getInput()

                @TaskAction
                def action() {
                    ${groovyInputPrintRoutine(RESULT_PREFIX)}
                }
            }

            tasks.register("myTask", MyTask) {
                def property = $factory
                property += $value
                input = property
            }
        """

        expect:
        runAndAssert("myTask", expectedResult)

        where:
        description                                  | inputType                     | factory     | value                    | expectedResult
        "ListProperty<T> += T"                       | "ListProperty<String>"        | listFactory | '"a"'                    | withCompoundAssignment("[a]")
        "ListProperty<T> += Collection<T>"           | "ListProperty<String>"        | listFactory | '["a"]'                  | withCompoundAssignment("[a]")
        "ListProperty<T> += Provider<T>"             | "ListProperty<String>"        | listFactory | 'provider { "a" }'       | withCompoundAssignment("[a]")
        "ListProperty<T> += Provider<Collection<T>>" | "ListProperty<String>"        | listFactory | 'provider { ["a"] }'     | withCompoundAssignment("[a]")
        "SetProperty<T> += T"                        | "SetProperty<String>"         | setFactory  | '"a"'                    | withCompoundAssignment("[a]")
        "SetProperty<T> += Collection<T>"            | "SetProperty<String>"         | setFactory  | '["a"]'                  | withCompoundAssignment("[a]")
        "SetProperty<T> += Provider<T>"              | "SetProperty<String>"         | setFactory  | 'provider { "a" }'       | withCompoundAssignment("[a]")
        "SetProperty<T> += Provider<Collection<T>>"  | "SetProperty<String>"         | setFactory  | 'provider { ["a"] }'     | withCompoundAssignment("[a]")
        "MapProperty<K,V> += Map<K,V>"               | "MapProperty<String, String>" | mapFactory  | '["a":"b"]'              | withCompoundAssignment("{a=b}")
        "MapProperty<K,V> += Provider<Map<K,V>>"     | "MapProperty<String, String>" | mapFactory  | 'provider { ["a":"b"] }' | withCompoundAssignment("{a=b}")
    }

    // We need these properties to have changing values so the tests trigger CC-serialization of intermediate providers created by += implementation.
    private static String getListFactory() { "objects.listProperty(String).value(changing {[]})" }

    private static String getSetFactory() { "objects.setProperty(String).value(changing {[]})" }

    private static String getMapFactory() { "objects.mapProperty(String, String).value(changing {[:]})" }

    def "compound assignment preserves dependencies"() {
        Assume.assumeTrue(compoundAssignmentSupported())

        given:
        buildFile """
            abstract class MyTask extends DefaultTask {
                @OutputFile abstract RegularFileProperty getOutputFile()

                MyTask() {
                    outputFile.convention(project.layout.buildDirectory.file(name + ".txt"))
                }

                @TaskAction
                def action() {
                    outputFile.get().asFile.text = name
                }
            }

            def t1 = tasks.register("t1", MyTask)
            def t2 = tasks.register("t2", MyTask)
            def t3 = tasks.register("t3", MyTask)
            def t4 = tasks.register("t4", MyTask)

            tasks.register("echo") {
                Closure<Provider<String>> outputAsText = { t -> t.flatMap { it.outputFile }.map { it.asFile.text.trim() }}

                def lines = objects.listProperty(String)
                lines.add(outputAsText(t1))
                lines += outputAsText(t2)

                def mapLines = objects.mapProperty(String, String)
                mapLines.put("k3", outputAsText(t3))
                mapLines += outputAsText(t4).map { [k4: it] }

                inputs.property("lines", lines)
                inputs.property("mapLines", mapLines)

                doLast {
                    lines.get().forEach {
                        println("line: \$it")
                    }
                    mapLines.get().forEach { k, v ->
                        println("mapLine: \$k=\$v")
                    }
                }
            }
        """

        when:
        succeeds("echo")

        then:
        result.assertTasksExecuted(":t1", ":t2", ":t3", ":t4", ":echo")
        outputContains("line: t1")
        outputContains("line: t2")
        outputContains("mapLine: k3=t3")
        outputContains("mapLine: k4=t4")
    }

    def "Groovy assignment for ConfigurableFileCollection doesn't resolve a Configuration"() {
        buildFile """
            configurations {
                resolvable("customCompileClasspath")
            }

            abstract class MyTask extends DefaultTask {
                @InputFiles
                abstract ConfigurableFileCollection getMyConfiguration()
            }

            tasks.register("myTask", MyTask) {
                myConfiguration = configurations.customCompileClasspath
                assert configurations.customCompileClasspath.state.toString() == "UNRESOLVED"
            }
        """

        expect:
        run("myTask")
    }

    def "lazy property assignment with NamedDomainObjectContainer"() {
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

    @ToBeImplemented
    def "compound assignments work in plugins too"() {
        given:
        createDir("plugin") {
            buildFile(file("build.gradle"), """
                plugins {
                    id "groovy"
                    id "java-gradle-plugin"
                }

                dependencies {
                    implementation gradleApi()
                    implementation localGroovy()
                }

                gradlePlugin {
                    plugins {
                        pluginInGroovy {
                            id = "org.example.plugin-in-groovy"
                            implementationClass = "org.example.PluginInGroovy"
                        }
                    }
                }
            """)

            file("src/main/groovy/org/example/PluginInGroovy.groovy") << """
                package org.example

                import org.gradle.api.Plugin
                import org.gradle.api.Project
                import org.gradle.api.provider.ListProperty

                abstract class PluginInGroovy implements Plugin<Project> {
                    abstract ListProperty<String> getStringList()

                    @Override
                    void apply(Project target) {
                        stringList += ["a", "b"]

                        target.tasks.register("printProperties") {
                            def stringList = stringList
                            doLast {
                                println("stringList = \${stringList.get()}")
                            }
                        }
                    }
                }
            """
        }

        settingsFile """
            pluginManagement {
                includeBuild("plugin")
            }
        """

        buildFile """
            plugins {
                id("org.example.plugin-in-groovy")
            }
        """

        expect:
        fails("printProperties")

        // TODO(mlopatkin): With the AST transformation applied globally this should succeed and print the property.
        // outputContains("stringList = [a, b]")
    }

    private void groovyBuildFile(String inputDeclaration, String inputValue, String operation) {
        buildFile.text = """
            ${groovyTypesDefinition()}

            abstract class MyTask extends DefaultTask {
                @Internal
                $inputDeclaration

                @TaskAction
                void run() {
                    ${groovyInputPrintRoutine(RESULT_PREFIX, "input")}
                }
            }

            tasks.register("myTask", MyTask) {
                def result = (input $operation $inputValue)

                doLast {
                    ${groovyInputPrintRoutine(EXPRESSION_PREFIX, "result")}
                }
            }
        """
    }

    private void groovyBuildFileWithVariable(String inputInitializer, String inputValue, String operation, String expectedType = null) {
        buildFile.text = """
            ${groovyTypesDefinition()}

            tasks.register("myTask") {
                def input = $inputInitializer
                def result = (input $operation $inputValue)
                ${expectedType ? "assert input instanceof $expectedType" : ""}
                doLast {
                    ${groovyInputPrintRoutine(RESULT_PREFIX, "input")}
                    ${groovyInputPrintRoutine(EXPRESSION_PREFIX, "result")}
                }
            }
        """
    }

    private String groovyTypesDefinition() {
        """
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
        """
    }

    private String groovyInputPrintRoutine(String prefix = RESULT_PREFIX, String variable = "input") {
        """
            if (${variable} instanceof FileSystemLocationProperty) {
                println("$prefix" + ${variable}.map { it.asFile.name }.getOrElse("undefined"))
            } else if (${variable} instanceof File) {
               println("$prefix" + ${variable}.name)
            } else if (${variable} instanceof Provider) {
                println("$prefix" + ${variable}.map { it.toString() }.getOrElse("undefined"))
            } else if (${variable} instanceof FileCollection) {
                println("$prefix" + ${variable}.files.collect { it.name })
            } else if (${variable} instanceof Iterable) {
                println("$prefix" + ${variable}.collect { it instanceof File ? it.name : it })
            } else if (${variable}?.getClass()?.isArray()) {
                println("$prefix" + Arrays.toString(${variable}))
            } else {
                println("$prefix" + ${variable})
            }
        """
    }

    private void assertExpression(def value) {
        outputContains(EXPRESSION_PREFIX + value)
    }
}

