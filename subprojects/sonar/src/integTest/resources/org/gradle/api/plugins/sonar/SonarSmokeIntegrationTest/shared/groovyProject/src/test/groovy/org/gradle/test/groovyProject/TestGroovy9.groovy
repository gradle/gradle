package org.gradle.test.groovyProject

import static org.junit.Assert.assertEquals

public class TestGroovy9 {
    private final ProductionGroovy9 production = new ProductionGroovy9("value");

    @org.junit.Test
    public void test() {
        assertEquals(production.getProperty(), "value");
    }
}