package org.gradle.sample

import org.junit.*
import static org.junit.Assert.*

class PersonTest {
    @Test
    def void canCreateAPerson() {
        Person person = new Person()
        person.name = 'Barry'
        assertEquals('Barry', person.name)
    }
}
