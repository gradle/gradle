package org.gradle.test.javaProject;

import static org.junit.Assert.*;

public class Test5 {
    private final Production5 production = new Production5("value");

    @org.junit.Test
    public void test() {
        assertEquals(production.getProperty(), "value");
    }
}