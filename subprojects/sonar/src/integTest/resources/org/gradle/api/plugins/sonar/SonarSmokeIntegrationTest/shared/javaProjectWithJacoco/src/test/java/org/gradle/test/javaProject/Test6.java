package org.gradle.test.javaProject;

import static org.junit.Assert.*;

public class Test6 {
    private final Production6 production = new Production6("value");

    @org.junit.Test
    public void test() {
        assertEquals(production.getProperty(), "value");
    }
}