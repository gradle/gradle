package org.gradle.test.javaProject;

import static org.junit.Assert.*;

public class Test2 {
    private final Production2 production = new Production2("value");

    @org.junit.Test
    public void test() {
        assertEquals(production.getProperty(), "value");
    }
}