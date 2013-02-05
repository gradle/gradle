package org.gradle.api.plugins.sonar.runner.SonarRunnerSmokeIntegrationTest.shared.groovyProject.src.test.groovy.org.gradle.test.groovyProject

import static org.junit.Assert.assertEquals

public class TestGroovy9 {
    private final ProductionGroovy9 production = new ProductionGroovy9("value");

    @org.junit.Test
    public void test() {
        assertEquals(production.getProperty(), "value");
    }
}