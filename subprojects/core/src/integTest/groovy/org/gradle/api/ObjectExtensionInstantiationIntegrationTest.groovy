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

import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.ProjectLayout
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ProviderFactory
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.internal.execution.WorkExecutor
import org.gradle.process.ExecOperations
import spock.lang.Unroll

import javax.inject.Inject


class ObjectExtensionInstantiationIntegrationTest extends AbstractIntegrationSpec {
    // Document current behaviour
    def "can create instance of extension with multiple constructors without @Inject annotation"() {
        buildFile << """
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
        buildFile << """
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
        buildFile << """
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
        buildFile << """
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
        buildFile << """
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
        buildFile << """
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
        failure.assertHasCause("Multiple constructors of type Thing match parameters: ['a', 'b']")
    }

    def "fails when too many construction parameters provided"() {
        buildFile << """
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

    @Unroll
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
        buildFile << """
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
        buildFile << """
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
        buildFile << """
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
        buildFile << """
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
        buildFile << """
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
        buildFile << """
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
        buildFile << """
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
        buildFile << """
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
        buildFile << """
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
        buildFile << """
            class Bean {
                String name
            }

            interface Thing {
                DomainObjectSet<Bean> getValue()
            }

            extensions.create("thing", Thing)
            assert thing.value.toString() == "[]"
            assert thing.value.empty
            thing.value.add(new Bean(name: "a"))
            thing.value.add(new Bean(name: "b"))
            assert thing.value*.name == ['a', 'b']
        """

        expect:
        succeeds()
    }

    def "can create instance of abstract class with mutable property"() {
        buildFile << """
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

    // Document current behaviour
    def "can inject service and configuration as constructor args when constructor not annotated with @Inject"() {
        buildFile << """
            class Thing {
                Thing(String a, ObjectFactory objects, int b) {
                    assert a == "a"
                    assert b == 12
                    assert objects != null
                }
            }

            extensions.create("thing", Thing, "a", 12)
        """

        expect:
        succeeds()
    }

    def "can inject service using getter"() {
        buildFile << """
            import ${Inject.name}

            class Thing {
                Thing(String a) {
                }

                @Inject
                ObjectFactory getObjects() { }
            }

            extensions.create("thing", Thing, "a")
            assert thing.objects != null
        """

        expect:
        succeeds()
    }

    def "can inject service using abstract getter"() {
        buildFile << """
            import ${Inject.name}

            abstract class Thing {
                Thing(String a) {
                }

                @Inject
                abstract ObjectFactory getObjects()
            }

            extensions.create("thing", Thing, "a")
            assert thing.objects != null
        """

        expect:
        succeeds()
    }

    def "can use getter injected services from constructor"() {
        buildFile << """
            import ${Inject.name}

            class Thing {
                Thing(String a) {
                    objects.property(String).set(a)
                }

                @Inject
                ObjectFactory getObjects() { }
            }

            extensions.create("thing", Thing, "a")
            assert thing.objects != null
        """

        expect:
        succeeds()
    }

    def "can inject service using getter on interface"() {
        buildFile << """
            import ${Inject.name}

            interface Thing {
                @Inject
                ObjectFactory getObjects()
            }

            extensions.create("thing", Thing)
            assert thing.objects != null
        """

        expect:
        succeeds()
    }

    @Unroll
    def "service of type #serviceType is available for injection into project extension"() {
        buildFile << """
            class Thing {
                ${serviceType} service

                Thing(${serviceType} service) {
                    this.service = service
                }
            }

            extensions.create("thing", Thing)
            assert thing.service != null
        """

        expect:
        succeeds()

        where:
        serviceType << [
            ObjectFactory,
            ProjectLayout,
            ProviderFactory,
            WorkExecutor,
            FileSystemOperations,
            ExecOperations,
        ].collect { it.name }
    }

    @Unroll
    def "service of type #serviceType is available for injection into settings extension"() {
        settingsFile << """
            class Thing {
                ${serviceType} service

                Thing(${serviceType} service) {
                    this.service = service
                }
            }

            extensions.create("thing", Thing)
            assert thing.service != null
        """

        expect:
        succeeds()

        where:
        serviceType << [
            ObjectFactory,
            ProviderFactory,
            FileSystemOperations,
            ExecOperations,
        ].collect { it.name }
    }

    @Unroll
    def "service of type #serviceType is available for injection into gradle object extension"() {
        settingsFile << """
            class Thing {
                ${serviceType} service

                Thing(${serviceType} service) {
                    this.service = service
                }
            }

            gradle.extensions.create("thing", Thing)
            assert gradle.thing.service != null
        """

        expect:
        succeeds()

        where:
        serviceType << [
            ObjectFactory,
            ProviderFactory,
            FileSystemOperations,
            ExecOperations,
        ].collect { it.name }
    }

    def "fails when construction parameters provided for interface"() {
        buildFile << """
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
        buildFile << """
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

    def "generates a display name for extension interface"() {
        buildFile << """
            interface NoDisplayName { }

            def noDisplayName = extensions.create("no-name", NoDisplayName)

            println("display name = \${noDisplayName}")
        """

        expect:
        succeeds()
        outputContains("display name = extension 'no-name'")
    }
}
