package org.gradle.test.javaProject;

import static org.junit.Assert.*;

public class Test4 {
    private final Production4 production = new Production4("value");

    @org.junit.Test
    public void test() {
        assertEquals(production.getProperty(), "value");
    }
}