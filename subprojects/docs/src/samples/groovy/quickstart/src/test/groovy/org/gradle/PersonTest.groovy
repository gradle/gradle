package org.gradle

import org.junit.Test
import static org.junit.Assert.*

class PersonTest {
    @Test public void canConstructAPerson() {
        Person p = new Person()
        assertEquals('Barry', p.name)
    }

    @Test public void canConstructAPersonUsingName() {
        Person p = new Person(name: 'name')
        assertEquals('name', p.name)
    }

    @Test public void usingCorrectVersionOfGroovy() {
        assertEquals('2.0.5', GroovySystem.version)
    }
    
    @Test public void testResourcesAreAvailable() {
        assertNotNull(getClass().getResource('/testResource.txt'))
        assertNotNull(getClass().getResource('/testScript.groovy'))
    }
}