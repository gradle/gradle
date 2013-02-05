package org.gradle.api.plugins.sonar.runner.SonarRunnerSmokeIntegrationTest.shared.groovyProject.src.test.groovy.org.gradle.test.groovyProject

import static org.junit.Assert.assertEquals

public class TestGroovy3 {
    private final ProductionGroovy3 production = new ProductionGroovy3("value");

    @org.junit.Test
    public void test() {
        assertEquals(production.getProperty(), "value");
    }
}