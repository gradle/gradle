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

import static org.gradle.integtests.fixtures.executer.GradleContextualExecuter.configCache

class GroovyPropertyAssignmentIntegrationTest extends AbstractProviderOperatorIntegrationTest {
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
        "T = null" | "Property<MyObject>" | 'null' | "undefined"
        "T = T"                                         | "Property<MyObject>" | 'new MyObject("hello")'                  | "hello"
        "T = provider { null }"                         | "Property<MyObject>" | 'provider { null }'                      | "undefined"
        "T = Provider<T>"                               | "Property<MyObject>" | 'provider { new MyObject("hello") }'     | "hello"
        "String = Object"                               | "Property<String>"   | 'new MyObject("hello")'                  | unsupportedWithCause("Cannot set the value of task ':myTask' property 'input'")
        "Enum = String"                                 | "Property<MyEnum>"   | '"YES"'                                  | unsupportedWithCause("Cannot set the value of task ':myTask' property 'input'")
        "File = T extends FileSystemLocation"           | "DirectoryProperty"  | 'layout.buildDirectory.dir("out").get()' | "out"
        "File = Provider<T extends FileSystemLocation>" | "DirectoryProperty"  | 'layout.buildDirectory.dir("out")'       | "out"
        "File = File"                                   | "DirectoryProperty"  | 'file("out")'                            | "out"
        "File = Provider<File>"                         | "DirectoryProperty"  | 'provider { file("out") }'               | unsupportedWithCause("Cannot get the value of task ':myTask' property 'input'")
        "File = Object"                                 | "DirectoryProperty"  | 'new MyObject("out")'                    | unsupportedWithCause("Cannot set the value of task ':myTask' property 'input'")
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

        where:
        description                              | operation | inputType                       | inputValue                                               | expectedResult
        "Collection<T> = null"                   | "="       | "ListProperty<MyObject>"        | 'null'                                                   | 'undefined'
        "Collection<T> = T[]"                    | "="       | "ListProperty<MyObject>"        | '[new MyObject("a")] as MyObject[]'                      | unsupportedWithCause("Cannot set the value of a property of type java.util.List using an instance of type [LMyObject;")
        "Collection<T> = Iterable<T>"            | "="       | "ListProperty<MyObject>"        | '[new MyObject("a")] as Iterable<MyObject>'              | '[a]'
        "Collection<T> = provider { null }"      | "="       | "ListProperty<MyObject>"        | 'provider { null }'                                      | 'undefined'
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
        "Map<K, V> = null"                       | "="       | "MapProperty<String, MyObject>" | 'null'                                                   | 'undefined'
        "Map<K, V> = Map<K, V>"                  | "="       | "MapProperty<String, MyObject>" | '["a": new MyObject("b")]'                               | '{a=b}'
        "Map<K, V> = provider { null }"          | "="       | "MapProperty<String, MyObject>" | 'provider { null }'                                      | 'undefined'
        "Map<K, V> = Provider<Map<K, V>>"        | "="       | "MapProperty<String, MyObject>" | 'provider { ["a": new MyObject("b")] }'                  | '{a=b}'
        "Map<K, V> += Map<K, V>"                 | "+="      | "MapProperty<String, MyObject>" | '["a": new MyObject("b")]'                               | unsupportedWithCause("No signature of method")
        "Map<K, V> << Map<K, V>"                 | "<<"      | "MapProperty<String, MyObject>" | '["a": new MyObject("b")]'                               | unsupportedWithCause("No signature of method")
        "Map<K, V> += Provider<Map<K, V>>"       | "+="      | "MapProperty<String, MyObject>" | 'provider { ["a": new MyObject("b")] }'                  | unsupportedWithCause("No signature of method")
        "Map<K, V> << Provider<Map<K, V>>"       | "<<"      | "MapProperty<String, MyObject>" | 'provider { ["a": new MyObject("b")] }'                  | unsupportedWithCause("No signature of method")
    }

    def "lazy collection variables assignment for #description"() {
        def inputInitializer = inputType.startsWith("ListProperty<") ? "objects.listProperty(MyObject)" : "objects.mapProperty(String, MyObject)"
        groovyBuildFileWithVariable(inputInitializer, inputValue, operation)

        expect:
        runAndAssert("myTask", expectedResult)

        where:
        description                              | operation | inputType                       | inputValue                                               | expectedResult
        "Collection<T> = null"                   | "="       | "ListProperty<MyObject>"        | 'null'                                                   | 'null'
        "Collection<T> = T[]"                    | "="       | "ListProperty<MyObject>"        | '[new MyObject("a")] as MyObject[]'                      | '[a]'
        "Collection<T> = Iterable<T>"            | "="       | "ListProperty<MyObject>"        | '[new MyObject("a")] as Iterable<MyObject>'              | '[a]'
        "Collection<T> = provider { null }"      | "="       | "ListProperty<MyObject>"        | 'provider { null }'                                      | 'undefined'
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
        "Map<K, V> = null"                       | "="       | "MapProperty<String, MyObject>" | 'null'                                                   | 'null'
        "Map<K, V> = Map<K, V>"                  | "="       | "MapProperty<String, MyObject>" | '["a": new MyObject("b")]'                               | '[a:b]'
        "Map<K, V> = provider { null }"          | "="       | "MapProperty<String, MyObject>" | 'provider { null }'                                      | 'undefined'
        "Map<K, V> = Provider<Map<K, V>>"        | "="       | "MapProperty<String, MyObject>" | 'provider { ["a": new MyObject("b")] }'                  | '[a:b]'
        "Map<K, V> += Map<K, V>"                 | "+="      | "MapProperty<String, MyObject>" | '["a": new MyObject("b")]'                               | unsupportedWithCause("No signature of method")
        "Map<K, V> << Map<K, V>"                 | "<<"      | "MapProperty<String, MyObject>" | '["a": new MyObject("b")]'                               | unsupportedWithCause("No signature of method")
        "Map<K, V> += Provider<Map<K, V>>"       | "+="      | "MapProperty<String, MyObject>" | 'provider { ["a": new MyObject("b")] }'                  | unsupportedWithCause("No signature of method")
        "Map<K, V> << Provider<Map<K, V>>"       | "<<"      | "MapProperty<String, MyObject>" | 'provider { ["a": new MyObject("b")] }'                  | unsupportedWithCause("No signature of method")
    }

    def "eager FileCollection properties assignment for #description"() {
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

    def "lazy FileCollection properties assignment for #description"() {
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

    def "lazy FileCollection variables assignment for #description"() {
        def inputInitializer = "files()"
        groovyBuildFileWithVariable(inputInitializer, inputValue, operation, expectedType)

        expect:
        runAndAssert("myTask", expectedResult)

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

    private void groovyBuildFile(String inputDeclaration, String inputValue, String operation) {
        buildFile.text = """
            ${groovyTypesDefinition()}

            abstract class MyTask extends DefaultTask {
                @Internal
                $inputDeclaration

                @TaskAction
                void run() {
                    ${groovyInputPrintRoutine()}
                }
            }

            tasks.register("myTask", MyTask) {
                input $operation $inputValue
            }
        """
    }

    private void groovyBuildFileWithVariable(String inputInitializer, String inputValue, String operation, String expectedType = null) {
        buildFile.text = """
            ${groovyTypesDefinition()}

            tasks.register("myTask") {
                def input = $inputInitializer
                input $operation $inputValue
                ${expectedType ? "assert input instanceof $expectedType" : ""}
                doLast {
                    ${groovyInputPrintRoutine()}
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

    private String groovyInputPrintRoutine() {
        """
            if (input instanceof FileSystemLocationProperty) {
                println("$RESULT_PREFIX" + input.map { it.asFile.name }.getOrElse("undefined"))
            } else if (input instanceof File) {
               println("$RESULT_PREFIX" + input.name)
            } else if (input instanceof Provider) {
                println("$RESULT_PREFIX" + input.map { it.toString() }.getOrElse("undefined"))
            } else if (input instanceof FileCollection) {
                println("$RESULT_PREFIX" + input.files.collect { it.name })
            } else if (input instanceof Iterable) {
                println("$RESULT_PREFIX" + input.collect { it instanceof File ? it.name : it })
            } else {
                println("$RESULT_PREFIX" + input.toString())
            }
        """
    }
}
