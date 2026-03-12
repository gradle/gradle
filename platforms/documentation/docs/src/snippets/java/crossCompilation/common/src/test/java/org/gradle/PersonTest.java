package org.gradle;

import org.junit.Test;

import static org.junit.Assert.*;

public class PersonTest {
    @Test
    public void canConstructAPersonWithAName() {
        Person person = new Person("Larry");
        assertEquals("Larry", person.getName());
    }

    @Test(expected = IllegalArgumentException.class)
    public void cannotConstructAPersonWithEmptyName() {
        Person person = new Person("");
        person.getName();
    }
}
