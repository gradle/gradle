package org.gradle.api.internal

import org.junit.Test
import static org.junit.Assert.*
import static org.hamcrest.Matchers.*

class AutoCreateDomainObjectContainerTest {
    private final AutoCreateDomainObjectContainer container = new TestContainer()

    @Test
    public void canAddObjectWithName() {
        container.add('obj')
        assertThat(container.getByName('obj'), equalTo(['obj']))
    }

    @Test
    public void canAddAndConfigureAnObjectWithName() {
        container.add('obj') {
            add(1)
            add('value')
        }
        assertThat(container.getByName('obj'), equalTo(['obj', 1, 'value']))
    }

    @Test
    public void failsToAddObjectWhenObjectWithSameNameAlreadyInContainer() {
        container.add('obj')

        try {
            container.add('obj')
            fail()
        } catch (org.gradle.api.InvalidUserDataException e) {
            assertThat(e.message, equalTo('Cannot add List \'obj\' as a List with that name already exists.'))
        }
    }
    
    @Test
    public void canConfigureExistingObject() {
        container.add('list1')
        container.configure {
            list1 { add(1) }
        }
        assertThat(container.list1, equalTo(['list1', 1]))
    }

    @Test
    public void propogatesNestedMissingMethodException() {
        container.add('list1')
        try {
            container.configure {
                list1 { unknown { anotherUnknown(2) } }
            }
        } catch (groovy.lang.MissingMethodException e) {
            assertThat(e.method, equalTo('unknown'))
            assertThat(e.type, equalTo(TestObject))
        }
    }

    @Test
    public void propogatesMethodInvocationException() {
        RuntimeException failure = new RuntimeException()
        try {
            container.configure {
                list1 { throw failure }
            }
        } catch (RuntimeException e) {
            assertThat(e, sameInstance(failure))
        }
    }

    @Test
    public void implicitlyAddsAnObjectWhenContainerIsBeingConfigured() {
        container.configure {
            list1
            list2 { add(1) }
        }
        assertThat(container.list1, equalTo(['list1']))
        assertThat(container.list2, equalTo(['list2', 1]))
    }

    @Test
    public void canReferToPropertiesAndMethodsOfOwner() {
        new DynamicOwner().configure(container)
        assertThat(container.asMap.keySet(), equalTo(['list1', 'list2'] as Set))
        assertThat(container.list1, equalTo(['list1', 'dynamicProp', 'ownerProp', 'ownerMethod', 'dynamicMethod', 'dynamicMethod', 1, 'prop', 'testObjectDynamicMethod']))
        assertThat(container.list1.prop, equalTo('prop'))
        assertThat(container.list2, equalTo(['list2', container.list1]))
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
                add all.size()
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

class TestObject extends ArrayList {
    def String prop

    def methodMissing(String name, Object params) {
        if (name == 'testObjectDynamicMethod') {
            add(name)
            return name
        }
        throw new groovy.lang.MissingMethodException(name, getClass(), params)
    }
}

class TestContainer extends AutoCreateDomainObjectContainer<TestObject> {

    def TestContainer() {
        super(List);
    }

    TestObject create(String name) {
        return new TestObject() << name
    }
}