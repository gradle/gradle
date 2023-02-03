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

package org.gradle.internal.extensibility

import org.gradle.api.Action
import org.gradle.api.UnknownDomainObjectException
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.plugins.ExtraPropertiesExtension
import org.gradle.api.reflect.TypeOf
import org.gradle.util.TestUtil
import spock.lang.Specification

import static org.gradle.api.reflect.TypeOf.typeOf

class ExtensionContainerTest extends Specification {

    def container = new DefaultConvention(TestUtil.instantiatorFactory().decorateLenient())
    def extension = new FooExtension()
    def barExtension = new BarExtension()

    class FooExtension {
        String message = "smile"
    }

    class BarExtension {}

    class SomeExtension {}

    def "has dynamic extension"() {
        expect:
        container.getByName(ExtraPropertiesExtension.EXTENSION_NAME) == container.extraProperties
    }

    def "extension can be accessed and configured"() {
        when:
        container.add("foo", extension)
        container.extensionsAsDynamicObject.foo.message = "Hey!"

        then:
        extension.message == "Hey!"
    }

    def "extension can be configured via script block"() {
        when:
        container.add("foo", extension)
        container.extensionsAsDynamicObject.foo {
            message = "You cool?"
        }

        then:
        extension.message == "You cool?"
    }

    def "extension cannot be set as property because we want users to use explicit method to add extensions"() {
        when:
        container.add("foo", extension)
        container.extensionsAsDynamicObject.foo = new FooExtension()

        then:
        IllegalArgumentException e = thrown()
        e.message == "There's an extension registered with name 'foo'. You should not reassign it via a property setter."
    }

    def "can register extensions using dynamic property setter"() {
        when:
        container.foo = extension

        then:
        container.findByName('foo') == extension
    }

    def "can access extensions using dynamic property getter"() {
        when:
        container.add('foo', extension)

        then:
        container.foo == extension
    }

    def "cannot replace an extension"() {
        given:
        container.add('foo', extension)

        when:
        container.add('foo', 'other')

        then:
        IllegalArgumentException e = thrown()
        e.message == "Cannot add extension with name 'foo', as there is an extension already registered with that name."

        when:
        container.foo = 'other'

        then:
        IllegalArgumentException e2 = thrown()
        e2.message == "There's an extension registered with name 'foo'. You should not reassign it via a property setter."

        when:
        container.create('foo', Thing, 'bar')

        then:
        IllegalArgumentException e3 = thrown()
        e3.message == "Cannot add extension with name 'foo', as there is an extension already registered with that name."
    }

    def "knows registered extensions"() {
        when:
        container.add("foo", extension)
        container.add("bar", barExtension)

        then:
        container.getByName("foo") == extension
        container.findByName("bar") == barExtension

        container.getByType(BarExtension) == barExtension
        container.findByType(FooExtension) == extension

        container.findByType(SomeExtension) == null
        container.findByName("i don't exist") == null
    }

    def "throws when unknown exception wanted by name"() {
        container.add("foo", extension)

        when:
        container.getByName("i don't exist")

        then:
        def ex = thrown(UnknownDomainObjectException)
        ex.message == "Extension with name 'i don't exist' does not exist. Currently registered extension names: [${ExtraPropertiesExtension.EXTENSION_NAME}, foo]"
    }

    def "throws when unknown extension wanted by type"() {
        container.add("foo", extension)

        when:
        container.getByType(SomeExtension)

        then:
        def ex = thrown(UnknownDomainObjectException)
        ex.message == "Extension of type 'ExtensionContainerTest.SomeExtension' does not exist. Currently registered extension types: [${ExtraPropertiesExtension.simpleName}, ExtensionContainerTest.FooExtension]"
    }

    def "types can be retrieved by interface and super types"() {
        given:
        def impl = new Impl()
        def child = new Child()

        when:
        container.add('i', impl)
        container.add('c', child)

        then:
        container.findByType(Capability) == impl
        container.getByType(Impl) == impl
        container.findByType(Parent) == child
        container.getByType(Parent) == child
    }

    def "can create ExtensionAware extensions"() {
        given:
        container.add("foo", Parent)
        def extension = container.getByName("foo")

        expect:
        extension instanceof ExtensionAware

        when:
        extension.extensions.create("thing", Thing, "bar")

        then:
        extension.thing.name == "bar"
    }

    def "can hide implementation type of extensions"() {
        given:
        container.create Parent, 'foo', Child
        container.create Capability, 'bar', Impl

        expect:
        container.findByType(Parent) != null
        container.findByType(Child) == null

        and:
        container.findByType(Capability) != null
        container.findByType(Impl) == null
    }

    def "can register extension with generic public type"() {
        given:
        def extension = []

        when:
        container.add new TypeOf<List<String>>() {}, 'foo', extension

        then:
        container.findByType(List) is extension
        container.findByType(new TypeOf<List<String>>() {}) is extension
    }

    def "can distinguish unrelated generic type arguments"() {
        given:
        def parents = []
        def capabilities = []

        when:
        container.add new TypeOf<List<Parent>>() {}, "parents", parents
        container.add new TypeOf<List<Capability>>() {}, "capabilities", capabilities

        then:
        container.getByType(new TypeOf<List<Parent>>() {}) is parents
        container.getByType(new TypeOf<List<Capability>>() {}) is capabilities
    }

    def "can distinguish related generic type arguments"() {
        given:
        def parents = []
        def children = []

        when:
        container.add new TypeOf<List<Parent>>() {}, "parents", parents
        container.add new TypeOf<List<Child>>() {}, "children", children

        then:
        container.getByType(new TypeOf<List<Parent>>() {}) is parents
        container.getByType(new TypeOf<List<Child>>() {}) is children
    }

    def "can get extensions schema"() {
        given:
        container.create Parent, 'foo', Child
        container.create Capability, 'bar', Impl
        container.create 'boo', Child
        container.add new TypeOf<List<String>>() {}, 'baz', []
        container.add "cat", new Thing("gizmo")
        container.add "meo", container.instanceGenerator.newInstance(Thing, "w")

        def schemaMap = container.extensionsSchema.collectEntries { [it.name, it.publicType] }
        expect:
        schemaMap == [ext: typeOf(ExtraPropertiesExtension),
                      foo: typeOf(Parent),
                      bar: typeOf(Capability),
                      boo: typeOf(Child),
                      baz: new TypeOf<List<String>>() {},
                      cat: typeOf(Thing),
                      meo: typeOf(Thing)]
    }

    def "can configure extensions by name"() {
        given:
        container.add "foo", extension

        when:
        container.configure "foo", { FooExtension foo -> foo.message = "bar" } as Action

        then:
        extension.message == "bar"
    }
}

interface Capability {}

class Impl implements Capability {}

class Parent {}

class Child extends Parent {}

class Thing {
    String name

    Thing(String name) {
        this.name = name
    }
}
