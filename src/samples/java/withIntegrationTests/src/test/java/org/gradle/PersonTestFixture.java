package org.gradle;

import static org.junit.Assert.*;

public class PersonTestFixture {
    public static Person create(String name) {
        assertNotNull(name);
        return new Person(name);
    }
}