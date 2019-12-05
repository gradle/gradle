package org.gradle

import org.junit.Test
import static org.junit.Assert.*

class PersonTest {
    @Test public void canConstructAJavaPerson() {
        Person p = new JavaPerson('name')
        assertEquals('name', p.name)
    }

    @Test public void canConstructAGroovyPerson() {
        Person p = new GroovyPerson(name: 'name')
        assertEquals('name', p.name)
    }
}