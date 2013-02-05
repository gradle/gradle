package org.gradle.api.plugins.sonar.runner.SonarRunnerSmokeIntegrationTest.shared.groovyProject.src.test.groovy.org.gradle.test.groovyProject

import static org.junit.Assert.assertEquals

public class TestGroovy2 {
    private final ProductionGroovy2 production = new ProductionGroovy2("value");

    @org.junit.Test
    public void test() {
        assertEquals(production.getProperty(), "value");
    }
}