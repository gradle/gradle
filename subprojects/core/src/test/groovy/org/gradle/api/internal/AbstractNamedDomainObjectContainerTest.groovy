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
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.reflect.DirectInstantiator

import spock.lang.Ignore
import spock.lang.Specification

class AbstractNamedDomainObjectContainerTest extends Specification {
    Instantiator instantiator = new ClassGeneratorBackedInstantiator(new AsmBackedClassGenerator(), new DirectInstantiator())
    AbstractNamedDomainObjectContainer container = instantiator.newInstance(TestContainer.class, instantiator)

    def "is dynamic object aware"() {
        expect:
        container instanceof DynamicObjectAware
    }

    def "can create object by name"() {
        when:
        container.create('obj')

        then:
        container.getByName('obj') == ['obj']
    }

    def "can create and configure object using closure"() {
        when:
        container.create('obj') {
            add(1)
            add('value')
        }

        then:
        container.getByName('obj') == ['obj', 1, 'value']
    }

    def "can create and configure object using action"() {
        def action = Mock(Action)

        given:
        action.execute(_) >> { TestObject obj ->
            obj.add(1)
            obj.add('value')
        }

        when:
        container.create('obj', action)

        then:
        container.getByName('obj') == ['obj', 1, 'value']
    }

    def "can use 'maybeCreate' to find or create object by name"() {
        when:
        def created = container.maybeCreate('obj')

        then:
        container.getByName('obj') == ['obj']

        when:
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
        container.create('list1')

        when:
        container.configure {
            list1 { add(1) }
        }

        then:
        container.list1 == ['list1', 1]
    }

    def "propagates nested MissingMethodException"() {
        container.create('list1')

        when:
        container.configure {
            list1 { unknown { anotherUnknown(2) } }
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
            list1 { throw failure }
        }

        then:
        RuntimeException e = thrown()
        e.is(failure)
    }

    def "implicitly creates an object when container is being configured"() {
        when:
        container.configure {
            list1
            list2 { add(1) }
        }

        then:
        container.list1 == ['list1']
        container.list2 == ['list2', 1]
    }

    def "can refer to properties and methods of owner"() {
        new DynamicOwner().configure(container)

        expect:
        container.asMap.keySet() == ['list1', 'list2'] as Set
        container.list1 == ['list1', 'dynamicProp', 'ownerProp', 'ownerMethod', 'dynamicMethod', 'dynamicMethod', 1, 'prop', 'testObjectDynamicMethod']
        container.list1.prop == 'prop'
        container.list2 == ['list2', container.list1]
    }

    @Ignore
    def "can use an item called 'main' in a script"() {
        def script = new GroovyShell().parse("""import org.gradle.util.ConfigureUtil
            c.configure {
                run
                main { add(1) }
            }

""")
        script.getBinding().setProperty("c", container)

        when:
        script.run()

        then:
        container.run == ['run']
        container.main == ['main', 1]
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
                add dynamicProp
                add ownerProp
                add ownerMethod('ownerMethod')
                add dynamicMethod('a', 'b', 'c')
                add dynamicMethod { doesntGetEvaluated }
                // delegate properties and methods - delegate is a TestObject
                add owner.size()
                prop = 'prop'
                add prop
                testObjectDynamicMethod { doesntGetEvaluated }
            }
            list2 {
                add list1
            }
        }
    }
}

class TestObject extends ArrayList<String> {
    String prop
    String name

    def methodMissing(String name, Object params) {
        if (name == 'testObjectDynamicMethod') {
            add(name)
            return name
        }
        throw new groovy.lang.MissingMethodException(name, getClass(), params)
    }
}
