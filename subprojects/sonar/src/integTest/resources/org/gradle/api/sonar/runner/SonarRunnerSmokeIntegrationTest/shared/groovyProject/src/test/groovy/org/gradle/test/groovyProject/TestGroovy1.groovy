package org.gradle.test.groovyProject

import static org.junit.Assert.assertEquals

public class TestGroovy1 {
    private final ProductionGroovy1 production = new ProductionGroovy1("value");

    @org.junit.Test
    public void test() {
        assertEquals(production.getProperty(), "value");
    }
}