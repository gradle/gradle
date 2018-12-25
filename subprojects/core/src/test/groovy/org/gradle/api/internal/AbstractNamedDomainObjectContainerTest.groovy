/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.internal

import org.gradle.api.Action
import org.gradle.api.InvalidUserDataException
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.reflect.TypeOf
import org.gradle.internal.reflect.Instantiator
import org.gradle.util.TestUtil
import spock.lang.Issue
import spock.lang.Specification

class AbstractNamedDomainObjectContainerTest extends Specification {
    Instantiator instantiator = TestUtil.instantiatorFactory().decorateLenient()
    CollectionCallbackActionDecorator collectionCallbackActionDecorator = CollectionCallbackActionDecorator.NOOP
    AbstractNamedDomainObjectContainer<TestObject> container = instantiator.newInstance(TestContainer.class, instantiator)

    def "is dynamic object aware"() {
        expect:
        container instanceof DynamicObjectAware
    }

    def "can create object by name"() {
        when:
        def obj = container.create('obj')

        then:
        container.getByName('obj') == obj
        container.findByName('obj') == obj
        container.obj == obj
        container['obj'] == obj
    }

    def "can create and configure object using closure"() {
        when:
        container.create('obj') {
            prop = 'value'
        }

        then:
        container.obj.prop == 'value'
    }

    def "can create and configure object using action"() {
        def action = Mock(Action)

        given:
        action.execute(_) >> { TestObject obj ->
            obj.prop = 'value'
        }

        when:
        container.create('obj', action)

        then:
        container.obj.prop == 'value'
    }

    def "can use 'maybeCreate' to find or create object by name"() {
        when:
        def created = container.maybeCreate('obj')
        def fetched = container.maybeCreate('obj')

        then:
        fetched.is(created)
    }

    def "creation fails if object with same name already exists"() {
        container.create('obj')

        when:
        container.create('obj')

        then:
        InvalidUserDataException e = thrown()
        e.message == 'Cannot add a TestObject with name \'obj\' as a TestObject with that name already exists.'
    }

    def "can configure existing object"() {
        container.create('someObj')

        when:
        container.configure {
            someObj { prop = 'value' }
        }

        then:
        container.someObj.prop == 'value'
    }

    def "can register objects"() {
        when:
        def someObj = container.register("someObj")
        def otherObj = container.register("otherObj") {
            prop = 'value'
        }
        then:
        someObj.present
        otherObj.get().prop == 'value'
    }

    def "can find registered objects"() {
        when:
        container.register("someObj")
        container.register("otherObj") {
            prop = 'value'
        }
        def someObj = container.named("someObj")
        def otherObj = container.named("otherObj")
        then:
        someObj.present
        otherObj.get().prop == 'value'
    }

    def "propagates nested MissingMethodException"() {
        container.create('someObj')

        when:
        container.configure {
            someObj {
                unknown {
                    anotherUnknown(2)
                }
            }
        }

        then:
        groovy.lang.MissingMethodException e = thrown()
        e.method == 'unknown'
        e.type == TestObject
    }

    def "propagates method invocation exception"() {
        RuntimeException failure = new RuntimeException()

        when:
        container.configure {
            someObj { throw failure }
        }

        then:
        RuntimeException e = thrown()
        e.is(failure)
    }

    def "implicitly creates an object when container is being configured"() {
        when:
        container.configure {
            obj1
            obj2 { prop = 'value' }
        }

        then:
        container.obj1.prop == null
        container.obj2.prop == 'value'
    }

    def "does not implicitly create an object when closure parameter is used explicitly"() {
        when:
        container.configure {
            it.obj1
        }

        then:
        MissingPropertyException missingProp = thrown()
        missingProp.property == 'obj1'
    }

    def "does not implicitly create an object when container is not being configured"() {
        when:
        container.obj1

        then:
        MissingPropertyException missingProp = thrown()
        missingProp.property == 'obj1'

        when:
        container.obj2 { }

        then:
        MissingMethodException missingMethod = thrown()
        missingMethod.method == 'obj2'

        when:
        container.configure {
            element {
                nested
            }
        }

        then:
        missingProp = thrown()
        missingProp.property == 'nested'

        when:
        container.configure {
            element {
                prop = nested
            }
        }

        then:
        missingProp = thrown()
        missingProp.property == 'nested'
    }

    def "can nest containers"() {
        when:
        container.configure {
            someObj {
                children {
                    child1 {
                        prop = 'child1'
                    }
                    child2
                }
            }
        }

        then:
        container.names == ['someObj'] as SortedSet
        container.someObj.prop == null
        container.someObj.children.names == ['child1', 'child2'] as SortedSet
        container.someObj.children.child1.prop == 'child1'
        container.someObj.children.child2.prop == null
    }

    def "can refer to properties and methods of owner"() {
        new DynamicOwner().configure(container)

        expect:
        container.asMap.keySet() == ['list1', 'list2'] as Set
        container.list1.prop == 'list1'
        container.list2.prop == 'list2'
    }

    def "has public type"() {
        expect:
        container.publicType == new TypeOf<NamedDomainObjectContainer<TestObject>>() {}
    }


    static class Owner {
        void thing(Closure closure) {}
    }


    @Issue("https://issues.gradle.org/browse/GRADLE-3126")
    def "can create element when owner scope has item with same name"() {
        when:
        new Owner().with {
            container.configure {
                thing {}
            }
        }

        then:
        container.names.toList() == ["thing"]
    }

    def "can remove unrealized registered element using register provider"() {
        when:
        def provider = container.register('obj')

        then:
        provider.present

        when:
        container.remove(provider)

        then:
        container.names.toList() == []

        and:
        !provider.present
        provider.orNull == null

        when:
        provider.get()

        then:
        def ex = thrown(IllegalStateException)
        ex.message == "The domain object 'obj' (TestObject) for this provider is no longer present in its container."
    }

    def "can remove unrealized registered element using named provider"() {
        when:
        def provider = container.register('obj')

        then:
        provider.present

        when:
        container.remove(container.named('obj'))

        then:
        container.names.toList() == []

        and:
        !provider.present
        provider.orNull == null

        when:
        provider.get()

        then:
        def ex = thrown(IllegalStateException)
        ex.message == "The domain object 'obj' (TestObject) for this provider is no longer present in its container."
    }

    def "can remove realized registered element using register provider"() {
        when:
        def provider = container.register('obj')
        def obj = provider.get()

        then:
        provider.present
        obj == container.getByName('obj')

        when:
        container.remove(provider)

        then:
        container.names.toList() == []

        and:
        !provider.present
        provider.orNull == null

        when:
        provider.get()

        then:
        def ex = thrown(IllegalStateException)
        ex.message == "The domain object 'obj' (TestObject) for this provider is no longer present in its container."
    }

    def "can remove realized registered element using named provider"() {
        when:
        def provider = container.register('obj')
        def obj = provider.get()

        then:
        provider.present
        obj == container.getByName('obj')

        when:
        container.remove(container.named('obj'))

        then:
        container.names.toList() == []

        and:
        !provider.present
        provider.orNull == null

        when:
        provider.get()

        then:
        def ex = thrown(IllegalStateException)
        ex.message == "The domain object 'obj' (TestObject) for this provider is no longer present in its container."
    }
}

class DynamicOwner {
    Map values = [:]

    def ownerMethod(String value) {
        return value
    }

    def getOwnerProp() {
        return 'ownerProp'
    }

    def propertyMissing(String name) {
        if (name == 'dynamicProp') {
            return values[name]
        }
        throw new MissingPropertyException("fail")
    }

    def propertyMissing(String name, Object value) {
        if (name == 'dynamicProp') {
            values[name] = value
            return
        }
        throw new MissingPropertyException("fail")
    }

    def methodMissing(String name, Object params) {
        if (name == 'dynamicMethod') {
            return 'dynamicMethod'
        }
        throw new groovy.lang.MissingMethodException(name, getClass(), params)
    }

    def configure(def container) {
        container.configure {
            list1 {
                // owner properties and methods - owner is a DynamicOwner
                dynamicProp = 'dynamicProp'
                assert dynamicProp == 'dynamicProp'
                assert ownerProp == 'ownerProp'
                assert ownerMethod('ownerMethod') == 'ownerMethod'
                assert dynamicMethod('a', 'b', 'c') == 'dynamicMethod'
                assert dynamicMethod { doesntGetEvaluated } == 'dynamicMethod'
                // delegate properties and methods - delegate is a TestObject
                prop = 'list1'
                assert testObjectDynamicMethod { doesntGetEvaluated } == 'testObjectDynamicMethod'
            }
            list2 {
                prop = 'list2'
            }
        }
    }
}

class TestObject {
    String prop
    String name
    final children

    TestObject(Instantiator instantiator) {
        children = instantiator.newInstance(TestContainer, instantiator)
    }

    def children(Closure cl) {
        children.configure(cl)
    }

    def methodMissing(String name, Object params) {
        if (name == 'testObjectDynamicMethod') {
            return name
        }
        throw new groovy.lang.MissingMethodException(name, getClass(), params)
    }
}
