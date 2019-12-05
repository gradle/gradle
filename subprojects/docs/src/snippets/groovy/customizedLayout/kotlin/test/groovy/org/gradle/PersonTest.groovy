package org.gradle

import org.junit.Test
import static org.junit.Assert.*

class PersonTest {
    @Test public void canConstructAPerson() {
        Person p = new Person(name: 'name')
        assertEquals('name', p.name)
    }
}