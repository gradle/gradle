package org.gradle.test.javaProject;

import static org.junit.Assert.*;

public class Test3 {
    private final Production3 production = new Production3("value");

    @org.junit.Test
    public void test() {
        assertEquals(production.getProperty(), "value");
    }
}