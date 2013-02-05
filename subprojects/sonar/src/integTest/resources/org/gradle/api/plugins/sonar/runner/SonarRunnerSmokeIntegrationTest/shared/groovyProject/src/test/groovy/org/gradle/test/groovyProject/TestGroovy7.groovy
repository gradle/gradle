package org.gradle.api.plugins.sonar.runner.SonarRunnerSmokeIntegrationTest.shared.groovyProject.src.test.groovy.org.gradle.test.groovyProject

import static org.junit.Assert.assertEquals

public class TestGroovy7 {
    private final ProductionGroovy7 production = new ProductionGroovy7("value");

    @org.junit.Test
    public void test() {
        assertEquals(production.getProperty(), "value");
    }
}