package org.gradle.test.javaProject;

import static org.junit.Assert.*;

public class Test7 {
    private final Production7 production = new Production7("value");

    @org.junit.Test
    public void test() {
        assertEquals(production.getProperty(), "value");
    }
}