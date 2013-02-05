package org.gradle.api.plugins.sonar.runner.SonarRunnerSmokeIntegrationTest.shared.groovyProject.src.test.groovy.org.gradle.test.groovyProject

import static org.junit.Assert.assertEquals

public class TestGroovy5 {
    private final ProductionGroovy5 production = new ProductionGroovy5("value");

    @org.junit.Test
    public void test() {
        assertEquals(production.getProperty(), "value");
    }
}