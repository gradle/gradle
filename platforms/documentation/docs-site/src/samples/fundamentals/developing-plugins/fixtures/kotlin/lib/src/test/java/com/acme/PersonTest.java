package com.acme;

import org.junit.Test;
import static org.junit.Assert.*;

public class PersonTest {
    @Test
    public void testPerson() {
        Person person = Simpsons.homer();

        assertEquals("Homer", person.getFirstName());
        assertEquals("Simpson", person.getLastName());
    }
}
