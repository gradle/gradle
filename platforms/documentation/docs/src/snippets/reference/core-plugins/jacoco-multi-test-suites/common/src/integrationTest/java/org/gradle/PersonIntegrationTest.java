package org.gradle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PersonIntegrationTest {

    @Test
    void testSurname() {
        Person person = new Person();
        person.setSurname("Duke");
        assertEquals("Duke", person.getSurname());
    }
}
