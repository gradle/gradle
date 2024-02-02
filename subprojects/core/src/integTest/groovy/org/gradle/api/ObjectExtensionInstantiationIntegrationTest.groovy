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

package org.gradle.api

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Issue

class ObjectExtensionInstantiationIntegrationTest extends AbstractIntegrationSpec {
    // Document current behaviour
    def "can create instance of extension with multiple constructors without @Inject annotation"() {
        buildFile """
            class Thing {
                String a
                String b
                Thing(String a, String b = a) {
                    this.a = a
                    this.b = b
                }
            }

            extensions.create("one", Thing, "a")
            extensions.create("two", Thing, "a", "b")

            assert one.a == "a"
            assert one.b == "a"

            assert two.a == "a"
            assert two.b == "b"
        """

        expect:
        succeeds()
    }

    def "fails when too few construction parameters provided"() {
        buildFile """
            class Thing {
                Thing(String a, String b) {
                }
            }

            extensions.create("thing", Thing, "a")
        """

        expect:
        fails()
        failure.assertHasCause("Could not create an instance of type Thing.")
        failure.assertHasCause("Unable to determine constructor argument #2: missing parameter of type String, or no service of type String")
    }

    def "fails when non-static inner class provided"() {
        buildFile """
            class Things {
                class Thing { }
            }

            extensions.create("thing", Things.Thing, "a")
        """

        expect:
        fails()
        failure.assertHasCause("Could not create an instance of type Things\$Thing.")
        failure.assertHasCause("Class Things.Thing is a non-static inner class.")
    }

    def "fails when mismatched construction parameters provided"() {
        buildFile """
            class Thing {
                Thing(String a, String b) {
                }
            }

            extensions.create("thing", Thing, "a", 12)
        """

        expect:
        fails()
        failure.assertHasCause("Could not create an instance of type Thing.")
        failure.assertHasCause("Unable to determine constructor argument #2: value 12 not assignable to type String")
    }

    def "fails when mismatched construction parameters provided when there are multiple constructors"() {
        buildFile """
            class Thing {
                Thing(String a, String b) {
                }
                Thing(String a, boolean b) {
                }
            }

            extensions.create("thing", Thing, "a", 12)
        """

        expect:
        fails()
        failure.assertHasCause("Could not create an instance of type Thing.")
        failure.assertHasCause("No constructors of type Thing match parameters: ['a', 12]")
    }

    def "fails when constructor is ambiguous"() {
        buildFile """
            class Thing {
                Thing(String a, String b, ObjectFactory f) {
                }
                Thing(String a, String b, ProjectLayout p) {
                }
            }

            extensions.create("thing", Thing, "a", "b")
        """

        expect:
        fails()
        failure.assertHasCause("Could not create an instance of type Thing.")
        failure.assertHasCause("""Multiple constructors for parameters ['a', 'b']:
  1. candidate: Thing(String, String, ProjectLayout)
  2. best match: Thing(String, String, ObjectFactory)""")
    }

    def "fails when too many construction parameters provided"() {
        buildFile """
            class Thing {
                Thing(String a, String b) {
                }
            }

            extensions.create("thing", Thing, "a", "b", "c")
        """

        expect:
        fails()
        failure.assertHasCause("Could not create an instance of type Thing.")
        failure.assertHasCause("Too many parameters provided for constructor for type Thing. Expected 2, received 3.")
    }

    def "can create instance of interface with mutable property of type #type"() {
        buildFile << """
            interface Thing {
                ${type} getValue()
                void setValue(${type} value)
            }

            extensions.create("thing", Thing)
            assert thing.value == ${defaultValue}
            thing {
                value = ${newValue}
            }
            assert thing.value == ${newValue}
        """

        expect:
        succeeds()

        where:
        type           | defaultValue | newValue
        "String"       | null         | "'123'"
        "List<String>" | null         | "['a', 'b', 'c']"
        "boolean"      | false        | true
        "Boolean"      | null         | true
        "int"          | 0            | 12
        "Integer"      | null         | 12
    }

    def "can create instance of interface with read-only ConfigurableFileCollection property"() {
        buildFile """
            interface Thing {
                ConfigurableFileCollection getValue()
            }

            extensions.create("thing", Thing)
            assert thing.value.toString() == "file collection"
            assert thing.value.files.empty
            thing.value.from("a.txt")
            assert thing.value.files as List == [file("a.txt")]
        """

        expect:
        succeeds()
    }

    def "can create instance of interface with read-only ConfigurableFileTree property"() {
        buildFile """
            interface Thing {
                ConfigurableFileTree getValue()
            }

            extensions.create("thing", Thing)
            assert thing.value.toString() == "directory 'null'"
            thing.value.from("dir")
            assert thing.value.files == [file("dir/a.txt"), file("dir/sub/b.txt")] as Set
        """
        file("dir/a.txt").createFile()
        file("dir/sub/b.txt").createFile()

        expect:
        succeeds()
    }

    def "can create instance of interface with read-only Property<T> property"() {
        buildFile """
            interface Thing {
                Property<String> getValue()
            }

            extensions.create("thing", Thing)
            assert thing.value.getOrNull() == null
            assert thing.value.toString() == "extension 'thing' property 'value'"
            thing {
                value = "value"
            }
            assert thing.value.get() == "value"
        """

        expect:
        succeeds()
    }

    def "can create instance of interface with read-only RegularFileProperty property"() {
        buildFile """
            interface Thing {
                RegularFileProperty getValue()
            }

            extensions.create("thing", Thing)
            assert thing.value.toString() == "extension 'thing' property 'value'"
            assert thing.value.getOrNull() == null
            thing {
                value = file("thing.txt")
            }
            assert thing.value.get() == layout.projectDir.file("thing.txt")
        """

        expect:
        succeeds()
    }

    def "can create instance of interface with read-only DirectoryProperty property"() {
        buildFile """
            interface Thing {
                DirectoryProperty getValue()
            }

            extensions.create("thing", Thing)
            assert thing.value.toString() == "extension 'thing' property 'value'"
            assert thing.value.getOrNull() == null
            thing {
                value = file("thing.txt")
            }
            assert thing.value.get() == layout.projectDir.dir("thing.txt")
        """

        expect:
        succeeds()
    }

    def "can create instance of interface with read-only ListProperty property"() {
        buildFile """
            interface Thing {
                ListProperty<String> getValue()
            }

            extensions.create("thing", Thing)
            assert thing.value.toString() == "extension 'thing' property 'value'"
            assert thing.value.getOrNull() == []
            thing {
                value = ["thing"]
            }
            assert thing.value.get() == ["thing"]
        """

        expect:
        succeeds()
    }

    def "can create instance of interface with read-only SetProperty property"() {
        buildFile """
            interface Thing {
                SetProperty<String> getValue()
            }

            extensions.create("thing", Thing)
            assert thing.value.toString() == "extension 'thing' property 'value'"
            assert thing.value.getOrNull() == [] as Set
            thing {
                value = ["thing"]
            }
            assert thing.value.get() == ["thing"] as Set
        """

        expect:
        succeeds()
    }

    def "can create instance of interface with read-only MapProperty property"() {
        buildFile """
            interface Thing {
                MapProperty<String, String> getValue()
            }

            extensions.create("thing", Thing)
            assert thing.value.toString() == "extension 'thing' property 'value'"
            assert thing.value.getOrNull() == [:]
            thing {
                value = [a: "b"]
            }
            assert thing.value.get() == [a: "b"]
        """

        expect:
        succeeds()
    }

    def "can create instance of interface with read-only NamedDomainObjectContainer property"() {
        buildFile """
            class Bean {
                final String name
                Bean(String name) {
                    this.name = name
                }
            }

            interface Thing {
                NamedDomainObjectContainer<Bean> getValue()
            }

            extensions.create("thing", Thing)
            assert thing.value.toString() == "Bean container"
            assert thing.value.empty
            thing {
                value {
                    a { }
                    b
                }
            }
            assert thing.value.names == ["a", "b"] as Set
        """

        expect:
        succeeds()
    }

    def "can create instance of interface with read-only DomainObjectSet property"() {
        buildFile """
            class Bean {
                String name
            }

            interface Thing {
                DomainObjectSet<Bean> getValue()
            }

            extensions.create("thing", Thing)
            assert thing.value.toString() == "Bean collection"
            assert thing.value.empty
            thing.value.add(new Bean(name: "a"))
            thing.value.add(new Bean(name: "b"))
            assert thing.value*.name == ['a', 'b']
        """

        expect:
        succeeds()
    }

    def "can create instance of interface with read-only @Nested interface property"() {
        buildFile """
            interface Bean {
                Property<String> getName()
            }

            interface Thing {
                @Nested
                Bean getValue()
            }

            extensions.create("thing", Thing)
            assert thing.value.toString() == "extension 'thing' property 'value'"
            assert thing.value.name.toString() == "extension 'thing' property 'value.name'"
            thing.value.name = 'some name'
            assert thing.value.name.get() == 'some name'
        """

        expect:
        succeeds()
    }

    def "@Nested property constructor can use identity information"() {
        buildFile """
            abstract class Bean {
                abstract Property<String> getName()

                Bean() {
                    println("toString() = " + this)
                }
            }

            interface Thing {
                @Nested
                Bean getValue()
            }

            extensions.create("thing", Thing)
            thing.value.name = 'some name'
            assert thing.value.name.get() == 'some name'
        """

        expect:
        succeeds()
        outputContains("toString() = extension 'thing' property 'value'")
    }

    def "can create instance of abstract class with mutable property"() {
        buildFile """
            abstract class Thing {
                abstract String getValue()
                abstract void setValue(String value)
            }

            extensions.create("thing", Thing)
            assert thing.value == null
            thing {
                value = "123"
            }
            assert thing.value == "123"
        """

        expect:
        succeeds()
    }

    def "fails when construction parameters provided for interface"() {
        buildFile """
            interface Thing {
            }

            extensions.create("thing", Thing, "a")
        """

        expect:
        fails()
        failure.assertHasCause("Could not create an instance of type Thing.")
        failure.assertHasCause("Too many parameters provided for constructor for type Thing. Expected 0, received 1.")
    }

    def "generates a display name for extension when it does not provide a toString() implementation"() {
        buildFile """
            class NoDisplayName { }
            class DisplayName {
                String toString() { return "<display name>" }
            }

            def noDisplayName = extensions.create("no-name", NoDisplayName)
            def displayName = extensions.create("name", DisplayName)

            println("no display name = \${noDisplayName}")
            println("display name = \${displayName}")
        """

        expect:
        succeeds()
        outputContains("no display name = extension 'no-name'")
        outputContains("display name = <display name>")
    }

    def "generates a display name for settings extension"() {
        settingsFile << """
            class NoDisplayName { }
            class DisplayName {
                String toString() { return "<display name>" }
            }

            def noDisplayName = extensions.create("no-name", NoDisplayName)
            def displayName = extensions.create("name", DisplayName)

            println("no display name = \${noDisplayName}")
            println("display name = \${displayName}")
        """

        expect:
        succeeds()
        outputContains("no display name = extension 'no-name'")
        outputContains("display name = <display name>")
    }

    def "generates a display name for Gradle object extension"() {
        settingsFile << """
            class NoDisplayName { }
            class DisplayName {
                String toString() { return "<display name>" }
            }

            def noDisplayName = gradle.extensions.create("no-name", NoDisplayName)
            def displayName = gradle.extensions.create("name", DisplayName)

            println("no display name = \${noDisplayName}")
            println("display name = \${displayName}")
        """

        expect:
        succeeds()
        outputContains("no display name = extension 'no-name'")
        outputContains("display name = <display name>")
    }

    def "generates a display name for extension interface"() {
        buildFile """
            interface NoDisplayName { }

            def noDisplayName = extensions.create("no-name", NoDisplayName)

            println("display name = \${noDisplayName}")
        """

        expect:
        succeeds()
        outputContains("display name = extension 'no-name'")
    }

    @Issue("https://github.com/gradle/gradle/issues/11466")
    def "extension toString() implementation can use toString() of managed property"() {
        buildFile """
            abstract class DisplayName {
                abstract Property<String> getProp()

                String toString() { return "<display name> prop=" + prop }
            }

            def displayName = extensions.create("name", DisplayName)
            println("display name = \${displayName}")
        """

        expect:
        succeeds()
        outputContains("display name = <display name> prop=extension 'name' property 'prop'")
    }

    @Issue("https://github.com/gradle/gradle/issues/16936")
    def "extension with managed properties can be created on task"() {
        buildFile """
            interface MyExtension {
                Property<String> getProp()
            }

            tasks.register("mytask") {
                extensions.create("myext", MyExtension)
                myext.prop = "foobar"
                def myextProp = myext.prop
                doLast {
                    println("myext.prop = " + myextProp.get())
                }
            }
        """

        expect:
        succeeds("mytask")
        outputContains("myext.prop = foobar")
    }
}
