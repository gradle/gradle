package org.gradle.test.javaProject;

import static org.junit.Assert.*;

public class Test10 {
    private final Production10 production = new Production10("value");

    @org.junit.Test
    public void test() {
        assertEquals(production.getProperty(), "value");
    }
}