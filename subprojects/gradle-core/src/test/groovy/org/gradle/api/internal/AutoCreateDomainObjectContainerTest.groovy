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
        assertThat(container.list1, equalTo(['list1', 'dynamicProp', 'ownerProp', 'ownerMethod', 1, 'prop']))
        assertThat(container.list1.property, equalTo('prop'))
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

    def configure(def container) {
        container.configure {
            list1 {
                dynamicProp = 'dynamicProp'
                add dynamicProp
                add ownerProp
                add ownerMethod('ownerMethod')
                add all.size()
                property = 'prop'
                add property
            }
            list2 {
                add list1
            }
        }
    }
}

class TestObject extends ArrayList {
    def String property
}

class TestContainer extends AutoCreateDomainObjectContainer<TestObject> {

    def TestContainer() {
        super(List);
    }

    TestObject create(String name) {
        return new TestObject() << name
    }
}