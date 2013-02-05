package org.gradle.api.plugins.sonar.runner.SonarRunnerSmokeIntegrationTest.shared.groovyProject.src.test.groovy.org.gradle.test.groovyProject

import static org.junit.Assert.assertEquals

public class TestGroovy8 {
    private final ProductionGroovy8 production = new ProductionGroovy8("value");

    @org.junit.Test
    public void test() {
        assertEquals(production.getProperty(), "value");
    }
}