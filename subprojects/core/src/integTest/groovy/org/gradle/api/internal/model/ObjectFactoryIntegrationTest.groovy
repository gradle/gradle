/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.api.internal.model

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class ObjectFactoryIntegrationTest extends AbstractIntegrationSpec {
    def "plugin can create instances of class using injected factory"() {
        buildFile """
            @groovy.transform.ToString
            class Thing {
                @javax.inject.Inject
                Thing(String name) { this.name = name }

                String name
            }

            class CustomPlugin implements Plugin<Project> {
                ObjectFactory objects

                @javax.inject.Inject
                CustomPlugin(ObjectFactory objects) {
                    this.objects = objects
                }

                void apply(Project project) {
                    project.tasks.create('thing1', CustomTask) {
                        thing = objects.newInstance(Thing, 'thing1')
                    }
                    project.tasks.create('thing2', CustomTask) {
                        thing = project.objects.newInstance(Thing, 'thing2')
                    }
                }
            }

            class CustomTask extends DefaultTask {
                @Internal
                Thing thing

                @javax.inject.Inject
                ObjectFactory getObjects() { null }

                @TaskAction
                void run() {
                    println thing.toString() + ": " + objects.newInstance(Thing, thing.name)
                }
            }

            apply plugin: CustomPlugin
"""

        when:
        run "thing1", "thing2"

        then:
        outputContains("Thing(thing1): Thing(thing1)")
        outputContains("Thing(thing2): Thing(thing2)")
    }

    def "plugin can create instance of interface with mutable properties"() {
        buildFile """
            interface Thing {
                String getProp()
                void setProp(String value)
            }

            def t = objects.newInstance(Thing)
            assert t.prop == null
            t.prop = "value"
            assert t.prop == "value"
"""

        expect:
        succeeds()
    }

    def "plugin can create instance of interface with read-only FileCollection property"() {
        buildFile """
            interface Thing {
                ConfigurableFileCollection getFiles()
            }

            def t = objects.newInstance(Thing)
            assert t.files.toString() == "file collection"
            assert t.files.files.empty
            t.files.from('a.txt')
            assert t.files as List == [file('a.txt')]
"""

        expect:
        succeeds()
    }

    def "plugin can create instance of interface with read-only Property<T> property"() {
        buildFile """
            interface Thing {
                Property<String> getValue()
            }

            def t = objects.newInstance(Thing)
            assert t.value.toString() == "property 'value'"
            assert !t.value.present
            t.value = 'abc'
            assert t.value.get() == 'abc'
"""

        expect:
        succeeds()
    }

    def "plugin can create instance of abstract class with mutable properties"() {
        buildFile """
            abstract class Thing {
                String otherProp

                abstract String getProp()
                abstract void setProp(String value)
            }

            def t = objects.newInstance(Thing)
            assert t.prop == null
            assert t.otherProp == null
            t.prop = "value"
            assert t.prop == "value"
"""

        expect:
        succeeds()
    }

    def "plugin can create instance of interface with name property"() {
        buildFile """
            interface Thing {
                abstract String getName()
            }

            def t = objects.newInstance(Thing, "thingName")
            assert t.name == "thingName"
"""

        expect:
        succeeds()
    }

    def "plugin can create instance of interface that extends Named"() {
        buildFile """
            interface Thing extends Named { }

            def t = objects.newInstance(Thing, "thingName")
            assert t.name == "thingName"
"""

        expect:
        succeeds()
    }

    def "plugin can create instance of abstract class with name property"() {
        buildFile """
            abstract class Thing {
                @javax.inject.Inject Thing() { }

                abstract String getName()
            }

            def t = objects.newInstance(Thing, "thingName")
            assert t.name == "thingName"
"""

        expect:
        succeeds()
    }

    def "plugin can create instance of abstract class that implements Named"() {
        buildFile """
            abstract class Thing implements Named {
                @javax.inject.Inject Thing() { }
            }

            def t = objects.newInstance(Thing, "thingName")
            assert t.name == "thingName"
"""

        expect:
        succeeds()
    }

    def "fails when abstract method cannot be implemented"() {
        buildFile """
            interface Thing {
                String getProp()
            }

            objects.newInstance(Thing)
"""

        expect:
        fails()
        failure.assertHasCause("Could not create an instance of type Thing.")
        failure.assertHasCause("Could not generate a decorated class for type Thing.")
        failure.assertHasCause("Cannot have abstract method Thing.getProp().")
    }

    def "services are injected into instances using constructor or getter"() {
        buildFile """
            class Thing1 {
                final Property<String> name

                @javax.inject.Inject
                Thing1(ObjectFactory objects) { this.name = objects.property(String) }
            }

            class Thing2 {
                @javax.inject.Inject
                ObjectFactory getObjects() { null }

                String getName() {
                    def t = objects.newInstance(Thing1)
                    t.name.set("name")
                    t.name.get()
                }
            }

            assert objects.newInstance(Thing2).name == "name"
"""

        expect:
        succeeds()
    }

    def "services injected using getter can be used from constructor"() {
        buildFile """
            class Thing1 {
                final Property<String> name

                Thing1() { this.name = objects.property(String) }

                @javax.inject.Inject
                ObjectFactory getObjects() { null }
            }

            class Thing2 {
                String name

                Thing2() {
                    def t = objects.newInstance(Thing1)
                    t.name.set("name")
                    name = t.name.get()
                }

                @javax.inject.Inject
                ObjectFactory getObjects() { null }
            }

            assert objects.newInstance(Thing2).name == "name"
"""

        expect:
        succeeds()
    }

    def "services can be injected using abstract getter"() {
        buildFile """
            class Thing1 {
                final Property<String> name

                Thing1() { this.name = objects.property(String) }

                @javax.inject.Inject
                ObjectFactory getObjects() { null }
            }

            abstract class Thing2 {
                String name

                Thing2() {
                    def t = objects.newInstance(Thing1)
                    t.name.set("name")
                    name = t.name.get()
                }

                @javax.inject.Inject
                abstract ObjectFactory getObjects()
            }

            assert objects.newInstance(Thing2).name == "name"
"""

        expect:
        succeeds()
    }

    def "services can be injected using getter on interface"() {
        buildFile """
            interface Thing {
                @javax.inject.Inject
                ObjectFactory getObjects()
            }

            assert objects.newInstance(Thing).objects != null
"""

        expect:
        succeeds()
    }

    def "can create nested DSL elements using injected ObjectFactory"() {
        buildFile """
            class Thing {
                String name
            }

            class Thing2 {
                Thing thing

                @javax.inject.Inject
                Thing2(ObjectFactory factory) {
                    thing = factory.newInstance(Thing)
                }

                void thing(Action<? super Thing> action) { action.execute(thing) }
            }

            class Thing3 {
                Thing2 thing

                Thing3(ObjectFactory factory) {
                    thing = factory.newInstance(Thing2)
                }

                void thing(Action<? super Thing2> action) { action.execute(thing) }
            }

            project.extensions.create('thing', Thing3)

            thing {
                thing {
                    thing {
                        name = 'thing'
                    }
                }
            }
            assert thing.thing.thing.name == 'thing'
"""

        expect:
        succeeds()
    }

    def "DSL elements created using injected ObjectFactory can be extended and those extensions can receive services"() {
        buildFile """
            class Thing {
                String name

                @javax.inject.Inject
                Thing(ObjectFactory factory) { assert factory != null }
            }

            class Thing2 {
            }

            class Thing3 {
                Thing2 thing

                Thing3(ObjectFactory factory) {
                    thing = factory.newInstance(Thing2)
                }

                void thing(Action<? super Thing2> action) { action.execute(thing) }
            }

            project.extensions.create('thing', Thing3)

            thing.extensions.create('thing2', Thing)
            thing.thing.extensions.create('thing2', Thing)
            thing.thing.thing2.extensions.create('thing2', Thing)
            thing.thing2.extensions.create('thing2', Thing)

            thing {
                thing {
                    thing2 {
                        name = 'thing'
                    }
                }
                thing2 {
                    thing2 {
                        name = 'thing'
                    }
                }
            }

            assert thing.thing.thing2.name == 'thing'
            assert thing.thing2.thing2.name == 'thing'
"""

        expect:
        succeeds()
    }

    def "object creation fails with ObjectInstantiationException given invalid construction parameters"() {
        given:
        buildFile """
        class Thing {}

        task fail {
            def objects = project.objects
            doLast {
                objects.newInstance(Thing, 'bogus')
            }
        }
"""

        when:
        fails "fail"

        then:
        failure.assertHasCause('Could not create an instance of type Thing.')
        failure.assertHasCause('Too many parameters provided for constructor for type Thing. Expected 0, received 1.')
    }

    def "object creation fails with ObjectInstantiationException when construction parameters provided for interface"() {
        given:
        buildFile """
        interface Thing {}

        task fail {
            def objects = project.objects
            doLast {
                objects.newInstance(Thing, 'bogus')
            }
        }
"""

        when:
        fails "fail"

        then:
        failure.assertHasCause('Could not create an instance of type Thing.')
        failure.assertHasCause('Too many parameters provided for constructor for type Thing. Expected 0, received 1.')
    }

    def "object creation fails with ObjectInstantiationException given non-static inner class"() {
        given:
        buildFile """
        class Things {
            class Thing {
            }
        }

        task fail {
            def objects = project.objects
            doLast {
                objects.newInstance(Things.Thing, 'bogus')
            }
        }
"""

        when:
        fails "fail"

        then:
        failure.assertHasCause('Could not create an instance of type Things$Thing.')
        failure.assertHasCause('Class Things.Thing is a non-static inner class.')
    }

    def "object creation fails with ObjectInstantiationException given unknown service requested as constructor parameter"() {
        given:
        buildFile """
        interface Unknown { }

        class Thing {
            @javax.inject.Inject
            Thing(Unknown u) { }
        }

        task fail {
            def objects = project.objects
            doLast {
                objects.newInstance(Thing)
            }
        }
"""

        when:
        fails "fail"

        then:
        failure.assertHasCause('Could not create an instance of type Thing.')
        failure.assertHasCause('Unable to determine constructor argument #1: missing parameter of type Unknown, or no service of type Unknown')
    }

    def "object creation fails with ObjectInstantiationException when constructor throws an exception"() {
        given:
        buildFile """
        class Thing {
            Thing() { throw new GradleException("broken") }
        }

        task fail {
            def objects = project.objects
            doLast {
                objects.newInstance(Thing)
            }
        }
"""

        when:
        fails "fail"

        then:
        failure.assertHasCause('Could not create an instance of type Thing.')
        failure.assertHasCause('broken')
    }

    def "object creation fails with ObjectInstantiationException when constructor takes parameters but is not annotated"() {
        given:
        buildFile """
        class Thing {
            Thing(ObjectFactory factory) { }
        }

        task fail {
            def objects = project.objects
            doLast {
                objects.newInstance(Thing)
            }
        }
"""

        when:
        fails "fail"

        then:
        failure.assertHasCause('Could not create an instance of type Thing.')
        failure.assertHasCause('The constructor for type Thing should be annotated with @Inject.')
    }

    def "object creation fails with ObjectInstantiationException when type has multiple constructors not annotated"() {
        given:
        buildFile """
        class Thing {
            Thing() {}
            Thing(String foo) {}
        }

        task fail {
            def objects = project.objects
            doLast {
                objects.newInstance(Thing)
            }
        }
"""

        when:
        fails "fail"

        then:
        failure.assertHasCause('Could not create an instance of type Thing.')
        failure.assertHasCause('Class Thing has no constructor that is annotated with @Inject.')
    }

    def "plugin can create SourceDirectorySet instances"() {
        given:
        buildFile << """
            def dirSet = project.objects.sourceDirectorySet("sources", "some source files")
            assert dirSet != null
        """

        expect:
        succeeds()
    }

    def "plugin can create a NamedDomainObjectContainer instance that creates decorated elements of type and uses name bean property"() {
        given:
        buildFile """
            abstract class NamedThing implements Named {
                final String name
                abstract Property<String> getProp()
                NamedThing(String name) {
                    this.name = name
                }
            }

            def container = project.objects.domainObjectContainer(NamedThing)
            assert container != null
            container.configure {
                foo {
                    prop = 'abc'
                }
            }
            assert container.size() == 1
            def element = container.getByName('foo')
            assert element.name == 'foo'
            assert element.prop.get() == 'abc'
        """

        expect:
        succeeds()
    }

    def "plugin can create NamedDomainObjectContainer instances that creates elements using user provided factory"() {
        given:
        buildFile """
            class NamedThing implements Named {
                final String name
                NamedThing(String name) {
                    this.name = name
                }
            }

            def container = project.objects.domainObjectContainer(NamedThing) {
                return new NamedThing("prefix-" + it)
            }
            assert container != null
            def element = container.create("foo")
            assert element.name == 'prefix-foo'
            assert container.size() == 1
        """

        expect:
        succeeds()
    }

    def "plugin can create NamedDomainObjectContainer instances that creates decorated elements of a named managed type"() {
        given:
        buildFile """
            interface NamedThing extends Named {
                Property<String> getProp()
            }

            def container = project.objects.domainObjectContainer(NamedThing)
            assert container != null
            container.configure {
                foo {
                    prop = 'abc'
                }
            }
            def element = container.getByName('foo')
            assert element.name == 'foo'
            assert element.prop.get() == 'abc'
"""
        expect:
        succeeds()
    }

    def "plugin can create DomainObjectSet instances"() {
        given:
        buildFile """
            def domainObjectSet = project.objects.domainObjectSet(String)
            assert domainObjectSet != null
            assert domainObjectSet.add('foo')
            assert domainObjectSet.size() == 1
        """

        expect:
        succeeds()
    }

    def "plugin can create a NamedDomainObjectSet instance that uses name bean property"() {
        given:
        buildFile """
            class NamedThing implements Named {
                final String name
                NamedThing(String name) {
                    this.name = name
                }
            }

            def container = project.objects.namedDomainObjectSet(NamedThing)
            assert container != null
            container.add(new NamedThing('foo'))
            container.add(new NamedThing('bar'))
            assert container.size() == 2
            def element = container.getByName('foo')
            assert element.name == 'foo'
        """

        expect:
        succeeds()
    }

    def "plugin can create a NamedDomainObjectSet instance that uses a named managed type"() {
        given:
        buildFile """
            interface NamedThing extends Named { }

            def container = project.objects.namedDomainObjectSet(NamedThing)
            assert container != null
            container.add(project.objects.newInstance(NamedThing, 'foo'))
            container.add(project.objects.newInstance(NamedThing, 'bar'))
            assert container.size() == 2
            def element = container.getByName('foo')
            assert element.name == 'foo'
        """

        expect:
        succeeds()
    }

    def "plugin can create a NamedDomainObjectList instance that uses name bean property"() {
        given:
        buildFile """
            class NamedThing implements Named {
                final String name
                NamedThing(String name) {
                    this.name = name
                }
            }

            def container = project.objects.namedDomainObjectList(NamedThing)
            assert container != null
            container.add(new NamedThing('foo'))
            container.add(new NamedThing('bar'))
            assert container.size() == 2
            def element = container.getByName('foo')
            assert element.name == 'foo'
            assert element == container[0]
        """

        expect:
        succeeds()
    }

    def "plugin can create a NamedDomainObjectList instance that uses a named managed type"() {
        given:
        buildFile """
            interface NamedThing extends Named { }

            def container = project.objects.namedDomainObjectList(NamedThing)
            assert container != null
            container.add(project.objects.newInstance(NamedThing, 'foo'))
            container.add(project.objects.newInstance(NamedThing, 'bar'))
            assert container.size() == 2
            def element = container.getByName('foo')
            assert element.name == 'foo'
            assert element == container[0]
        """

        expect:
        succeeds()
    }

    def "plugin can create ExtensiblePolymorphicDomainObjectContainer instances"() {
        given:
        buildFile """
            class NamedThing implements Named {
                final String name
                NamedThing(String name) {
                    this.name = name
                }
            }

            def container = project.objects.polymorphicDomainObjectContainer(Named)
            container.registerBinding(Named, NamedThing)
            container.register("a", Named) { }
            container.register("b", Named) { }
            assert container.size() == 2
            assert container.every { it instanceof NamedThing }
        """

        expect:
        succeeds()
    }

    def "plugin can create ExtensiblePolymorphicDomainObjectContainer instances using named managed types"() {
        given:
        buildFile """
            interface BaseThing extends Named { 
                Property<Integer> getValue()
            }
            interface ThingA extends BaseThing { }
            interface ThingB extends BaseThing { }

            def container = project.objects.polymorphicDomainObjectContainer(BaseThing)
            container.registerBinding(ThingA, ThingA)
            container.registerBinding(ThingB, ThingB)
            container.register("a", ThingA) { 
                value = 0
            }
            container.register("b", ThingB) { 
                value = 1
            }
            assert container.size() == 2
            assert container[0] instanceof ThingA
            assert container[0].value.get() == 0
            assert container[1] instanceof ThingB
            assert container[1].value.get() == 1
        """

        expect:
        succeeds()
    }

    def "plugin can create instance of interface with nested NamedDomainObjectContainer using named managed types"() {
        given:
        buildFile """
            interface Thing extends Named {
                Property<Integer> getValue()
            }

            interface Bag {
                NamedDomainObjectContainer<Thing> getThings()
            }

            def bag = project.objects.newInstance(Bag)

            bag.things {
                a {
                    value = 1
                }
                b {
                    value = 2
                }
            }

            def container = bag.things
            assert container.size() == 2
            assert container[0].name == 'a'
            assert container[0].value.get() == 1
            assert container[1].name == 'b'
            assert container[1].value.get() == 2
        """

        expect:
        succeeds()
    }

    def "plugin can create instance of interface with nested ExtensiblePolymorphicDomainObjectContainer using named managed types"() {
        given:
        buildFile """
            interface Element extends Named {
            }
            interface Thing extends Element {
                Property<Integer> getValue()
            }
            interface AnotherThing extends Element {
                Property<String> getValue()
            }

            interface Bag {
                ExtensiblePolymorphicDomainObjectContainer<Element> getThings()
            }

            def bag = project.objects.newInstance(Bag)
            bag.things.registerBinding(Thing, Thing)
            bag.things.registerBinding(AnotherThing, AnotherThing)

            bag.things {
                a(Thing) {
                    value = 1
                }
                b(AnotherThing) {
                    value = "foo"
                }
            }

            def container = bag.things
            assert container.size() == 2
            assert container[0].name == 'a'
            assert container[0].value.get() == 1
            assert container[1].name == 'b'
            assert container[1].value.get() == "foo"
        """

        expect:
        succeeds()
    }
}
