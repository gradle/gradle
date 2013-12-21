package org.gradle.test.groovyProject

import static org.junit.Assert.assertEquals

public class TestGroovy4 {
    private final ProductionGroovy4 production = new ProductionGroovy4("value");

    @org.junit.Test
    public void test() {
        assertEquals(production.getProperty(), "value");
    }
}