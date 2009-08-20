package org.gradle.integtests.testng
/**
 * @author Tom Eyckmans
 */

public class TestNGIntegrationProject {
    String name
    boolean expectFailure
    def assertClosure

    static TestNGIntegrationProject failingIntegrationProject(String language, String jdk, assertClosure)
    {
        new TestNGIntegrationProject(language + "-" + jdk + "-failing", true, null, assertClosure)
    }

    static TestNGIntegrationProject failingIntegrationProject(String language, String jdk, String nameSuffix, assertClosure)
    {
        new TestNGIntegrationProject(language + "-" + jdk + "-failing", true, nameSuffix, assertClosure)
    }

    static TestNGIntegrationProject passingIntegrationProject(String language, String jdk, assertClosure)
    {
        new TestNGIntegrationProject(language + "-" + jdk + "-passing", false, null, assertClosure)
    }

    static TestNGIntegrationProject passingIntegrationProject(String language, String jdk, String nameSuffix, assertClosure)
    {
        new TestNGIntegrationProject(language + "-" + jdk + "-passing", false, nameSuffix, assertClosure)
    }

    public TestNGIntegrationProject(String name, boolean expectFailure, String nameSuffix, assertClosure)
    {
        if ( nameSuffix == null )
            this.name = name
        else
            this.name = name + nameSuffix 
        this.expectFailure = expectFailure
        this.assertClosure = assertClosure
    }

    void doAssert(projectDir, result) {
        assertClosure(name, projectDir, result)
    }
}