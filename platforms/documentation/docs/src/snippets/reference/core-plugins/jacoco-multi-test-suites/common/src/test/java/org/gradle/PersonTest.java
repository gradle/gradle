package org.gradle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PersonTest {

    @Test
    void testAge() {
        Person person = new Person();
        person.setAge(30);
        assertEquals(30, person.getAge());
    }
}
