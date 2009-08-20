package org.gradle.api.internal

import org.junit.Test
import static org.junit.Assert.*
import static org.hamcrest.Matchers.*

class AutoCreateDomainObjectContainerTest {
    private final AutoCreateDomainObjectContainer container = new TestContainer()

    @Test
    public void canAddObjectWithName() {
        container.add('obj')
        assertThat(container.getByName('obj'), equalTo([]))
    }

    @Test
    public void canAddAndConfigureAnObjectWithName() {
        container.add('obj') {
            add(1)
            add('value')
        }
        assertThat(container.getByName('obj'), equalTo([1, 'value']))
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
    public void implicitlyAddsAnObjectWhenContainerIsBeingConfigured() {
        container.configure {
            list1
            list2 { add(1) }
        }
        assertThat(container.getByName('list1'), equalTo([]))
        assertThat(container.getByName('list2'), equalTo([1]))
    }
}

class TestContainer extends AutoCreateDomainObjectContainer<List> {

    def TestContainer() {
        super(List);
    }

    List create(String name) {
        return []
    }
}