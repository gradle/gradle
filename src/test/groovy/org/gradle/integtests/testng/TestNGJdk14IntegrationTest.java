package org.gradle.integtests.testng;

import org.gradle.integtests.AbstractIntegrationTest;
import org.junit.Test;

import java.util.List;
import java.util.Arrays;

/**
 * @author Tom Eyckmans
 */
public class TestNGJdk14IntegrationTest extends AbstractTestNGIntegrationTest {
    public TestNGJdk14IntegrationTest() {
        super("jdk14");
    }

    @Test
    public void javaPluginWithPassingTests()
    {
        final TestFile buildFile = testFile("java-jdk14-passing.gradle");

        final GradleExecutionResult result = doPassingTest(buildFile, JAVA_TYPE, true);
    }

    @Test
    public void javaPluginWithFailingTest()
    {
        final TestFile buildFile = testFile("java-jdk14-failing.gradle");

        final GradleExecutionFailure failureResult = doFailingTest(buildFile, JAVA_TYPE, true);
    }

    /**
     * Currently commented out because this test dependens on the hierarchical classloader that is created in BootstrapMain.
     */
//    @Test
    public void groovyPluginWithPassingTests()
    {
        final TestFile buildFile = testFile("groovy-jdk14-passing.gralde");

        final GradleExecutionResult result = doPassingTest(buildFile, GROOVY_TYPE, true);
    }

    /**
     * Currently commented out because this test dependens on the hierarchical classloader that is created in BootstrapMain.
     */
//    @Test
    public void groovyPluginWithFailingTests()
    {
        final TestFile buildFile = testFile("groovy-jdk14-failing.gradle");

        final GradleExecutionFailure failureResult = doFailingTest(buildFile, GROOVY_TYPE, true);
    }

    @Override
    protected List<String> writeBuildFileTestTask(boolean testReport) {
        return Arrays.asList("",
            "test {",
            "   useTestNG().options.dumpCommand().javadocAnnotations() ",
            "}");
    }

    protected void writePassingTestClass(AbstractTestNGIntegrationTest.CompileType type)
    {
        testFile("src/test/"+type.getName()+"/org/gradle/OkTest."+type.getName()).writelns(
            "package org.gradle;",
            "public class OkTest {",
            "   /** ",
            "    * @testng.test ",
            "    */",
            "   public void passingTest() { }",
            "}"
        );
    }

    protected void writeFailingTestClass(AbstractTestNGIntegrationTest.CompileType type)
    {
        testFile("src/test/"+type.getName()+"/org/gradle/BadTest."+type.getName()).writelns(
            "package org.gradle;",
            "public class BadTest {",
            "   /** ",
            "    * @testng.test ",
            "    */",
            "   public void passingTest() { throw new IllegalArgumentException(); }",
            "}"
        );
    }

}
