package org.gradle.integtests.testng;

import org.gradle.integtests.AbstractIntegrationTest;
import org.gradle.api.Plugin;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.GroovyPlugin;
import org.gradle.util.GFileUtils;
import org.junit.Test;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.io.File;

import static junit.framework.Assert.assertEquals;

/**
 * @author Tom Eyckmans
 */
public class TestNGJdk15IntegrationTest extends AbstractTestNGIntegrationTest {
    public TestNGJdk15IntegrationTest() {
        super("jdk15");
    }

    @Test
    public void javaPluginWithPassingTests()
    {
        final TestFile buildFile = testFile("java-jdk15-passing.gradle");

        final GradleExecutionResult result = doPassingTest(buildFile, JAVA_TYPE, true);
    }

    @Test
    public void javaPluginWithPassingTestsWithoutReport()
    {
        final TestFile buildFile = testFile("java-jdk15-passing-no-report.gradle");

        final GradleExecutionResult result = doPassingTest(buildFile, JAVA_TYPE, false);

        final File testReportDirectory = GFileUtils.toFile("tmpTest", "build", "reports", "tests");
        assertTrue(testReportDirectory.exists());
        assertEquals(testReportDirectory.listFiles().length, 0);
    }

    @Test
    public void javaPluginWithFailingTest()
    {
        final TestFile buildFile = testFile("java-jdk15-failing.gradle");

        final GradleExecutionFailure failureResult = doFailingTest(buildFile, JAVA_TYPE, true);
    }



    /**
     * Currently commented out because this test dependens on the hierarchical classloader that is created in BootstrapMain.
     */
//    @Test
    public void groovyPluginWithPassingTests()
    {
        final TestFile buildFile = testFile("groovy-jdk15-passing.gralde");

        final GradleExecutionResult result = doPassingTest(buildFile, GROOVY_TYPE, true);
    }

    /**
     * Currently commented out because this test dependens on the hierarchical classloader that is created in BootstrapMain.
     */
//    @Test
    public void groovyPluginWithFailingTests()
    {
        final TestFile buildFile = testFile("groovy-jdk15-failing.gradle");

        final GradleExecutionFailure failureResult = doFailingTest(buildFile, GROOVY_TYPE, true);
    }

    @Override
    protected List<String> writeBuildFileTestTask(boolean testReport) {
        List<String> lines = new ArrayList<String>();

        lines.addAll(Arrays.asList("test {",
            "   useTestNG().options.dumpCommand() "
        ));

        if ( !testReport ) {
            lines.addAll(Arrays.asList("disableTestReport()"));
        }

        lines.addAll(Arrays.asList("}"));

        return lines;
    }

    @Override
    protected void writePassingTestClass(CompileType type)
    {
        testFile("src/test/"+type.getName()+"/org/gradle/OkTest."+type.getName()).writelns(
            "package org.gradle;",
            "public class OkTest {",
            "   @org.testng.annotations.Test",
            "   public void passingTest() { }",
            "}"
        );
    }

    @Override
    protected void writeFailingTestClass(CompileType type)
    {
        testFile("src/test/"+type.getName()+"/org/gradle/BadTest."+type.getName()).writelns(
            "package org.gradle;",
            "public class BadTest {",
            "   @org.testng.annotations.Test",
            "   public void passingTest() { throw new IllegalArgumentException(); }",
            "}"
        );
    }
}
