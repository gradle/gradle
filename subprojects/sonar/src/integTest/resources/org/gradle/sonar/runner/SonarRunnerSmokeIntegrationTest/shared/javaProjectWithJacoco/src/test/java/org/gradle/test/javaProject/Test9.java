package org.gradle.test.javaProject;

import static org.junit.Assert.*;

public class Test9 {
    private final Production9 production = new Production9("value");

    @org.junit.Test
    public void test() {
        assertEquals(production.getProperty(), "value");
    }
}