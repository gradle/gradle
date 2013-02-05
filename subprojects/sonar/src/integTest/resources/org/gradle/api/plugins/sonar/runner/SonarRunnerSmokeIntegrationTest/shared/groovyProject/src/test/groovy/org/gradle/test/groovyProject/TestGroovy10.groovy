package org.gradle.api.plugins.sonar.runner.SonarRunnerSmokeIntegrationTest.shared.groovyProject.src.test.groovy.org.gradle.test.groovyProject

import static org.junit.Assert.assertEquals

public class TestGroovy10 {
    private final ProductionGroovy10 production = new ProductionGroovy10("value");

    @org.junit.Test
    public void test() {
        assertEquals(production.getProperty(), "value");
    }
}