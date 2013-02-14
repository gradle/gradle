package org.gradle.test.javaProject;

import static org.junit.Assert.*;

public class Test8 {
    private final Production8 production = new Production8("value");

    @org.junit.Test
    public void test() {
        assertEquals(production.getProperty(), "value");
    }
}