package org.gradle.test.nestedProject;

import static org.junit.Assert.*;

public class Test1 {
    private final Production1 production = new Production1("value");

    @org.junit.Test
    public void test() {
        assertEquals(production.getProperty(), "value");
    }
}