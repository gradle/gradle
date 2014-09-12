package org.gradle.test.groovyProject

import static org.junit.Assert.assertEquals

public class TestGroovy2 {
    private final ProductionGroovy2 production = new ProductionGroovy2("value");

    @org.junit.Test
    public void test() {
        assertEquals(production.getProperty(), "value");
    }
}