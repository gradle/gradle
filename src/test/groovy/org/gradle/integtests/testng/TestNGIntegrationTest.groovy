package org.gradle.integtests.testng
/**
 * @author Tom Eyckmans
 */
import static org.gradle.integtests.testng.TestNGIntegrationProject.*
import org.gradle.integtests.Executer;
import org.gradle.integtests.GradleDistribution
import org.junit.runner.RunWith
import org.gradle.integtests.DistributionIntegrationTestRunner
import org.junit.Test

@RunWith(DistributionIntegrationTestRunner.class)
public class TestNGIntegrationTest {
    static final String GROOVY = "groovy"
    static final String JAVA = "java"
    static final String JDK14 = "jdk14"
    static final String JDK15 = "jdk15"

    static final GROOVY_JDK15_FAILING = failingIntegrationProject(GROOVY, JDK15, { name, projectDir, result ->
        checkExists(projectDir, 'build/classes/org/gradle/Ok.class')
        checkExists(projectDir, 'build/test-classes/org/gradle/BadTest.class')
        checkExists(projectDir, 'build/reports/tests/index.html')
    })
    static final GROOVY_JDK15_PASSING = passingIntegrationProject(GROOVY, JDK15, { name, projectDir, result ->
        checkExists(projectDir, 'build/classes/org/gradle/Ok.class')
        checkExists(projectDir, 'build/test-classes/org/gradle/OkTest.class')
        checkExists(projectDir, 'build/reports/tests/index.html')
    })
    static final JAVA_JDK14_FAILING = failingIntegrationProject(JAVA, JDK14, { name, projectDir, result ->
        checkExists(projectDir, 'build/classes/org/gradle/Ok.class')
        checkExists(projectDir, 'build/test-classes/org/gradle/BadTest.class')
        checkExists(projectDir, 'build/reports/tests/index.html')
    })
    static final JAVA_JDK14_PASSING = passingIntegrationProject(JAVA, JDK14, { name, projectDir, result ->
        checkExists(projectDir, 'build/classes/org/gradle/Ok.class')
        checkExists(projectDir, 'build/test-classes/org/gradle/OkTest.class')
        checkExists(projectDir, 'build/reports/tests/index.html')
    })
    static final JAVA_JDK15_FAILING = failingIntegrationProject(JAVA, JDK15, { name, projectDir, result ->
        checkExists(projectDir, 'build/classes/org/gradle/Ok.class')
        checkExists(projectDir, 'build/test-classes/org/gradle/BadTest.class')
        checkExists(projectDir, 'build/reports/tests/index.html')
    })
    static final JAVA_JDK15_PASSING = passingIntegrationProject(JAVA, JDK15, { name, projectDir, result ->
        checkExists(projectDir, 'build/classes/org/gradle/Ok.class')
        checkExists(projectDir, 'build/test-classes/org/gradle/OkTest.class')
        checkExists(projectDir, 'build/reports/tests/index.html')
    })
    static final JAVA_JDK15_PASSING_NO_REPORT = passingIntegrationProject(JAVA, JDK15, "-no-report", { name, projectDir, result ->
        checkExists(projectDir, 'build/classes/org/gradle/Ok.class')
        checkExists(projectDir, 'build/test-classes/org/gradle/OkTest.class')
        checkDoesNotExists(projectDir, 'build/reports/tests/index.html')
    })
    static final SUITE_XML_BUILDER = new TestNGIntegrationProject("suitexmlbuilder", false, null, { name, projectDir, result ->
        checkExists(projectDir, 'build/classes/org/gradle/testng/User.class')
        checkExists(projectDir, 'build/classes/org/gradle/testng/UserImpl.class')
        checkExists(projectDir, 'build/test-classes/org/gradle/testng/UserImplTest.class')
        checkExists(projectDir, 'build/reports/tests/index.html')
        checkExists(projectDir, 'build/reports/tests/emailable-report.html')
    })

    // Injected by test runner
    private GradleDistribution dist;

    @Test
    public void testNGSamples() {
        final List projects =
            [   SUITE_XML_BUILDER,
                GROOVY_JDK15_FAILING, GROOVY_JDK15_PASSING,
                JAVA_JDK14_FAILING, JAVA_JDK14_PASSING, JAVA_JDK15_FAILING, JAVA_JDK15_PASSING, JAVA_JDK15_PASSING_NO_REPORT]

        projects.each { it ->
            final File projectDir = new File(new File(dist.samplesDir, "testng"), it.name)

            final Map result = Executer.execute(dist.gradleHomeDir.absolutePath, projectDir.absolutePath, ['clean', 'test'], [:], '', Executer.QUIET, it.expectFailure)

            // output: output, error: error, command: actualCommand, unixCommand: unixCommand, windowsCommand: windowsCommand
            it.doAssert(projectDir, result)
        }
    }

    static File file(File baseDir, String[] path) {
        new File(baseDir, path.join('/'));
    }

    static void checkExists(File baseDir, String[] path) {
        checkExistence(baseDir, true, path)
    }

    static void checkDoesNotExists(File baseDir, String[] path) {
        checkExistence(baseDir, false, path)
    }

    static void checkExistence(File baseDir, boolean shouldExists, String[] path) {
        File file = file(baseDir, path)
        try {
            assert shouldExists ? file.exists() : !file.exists()
        } catch (AssertionError e) {
            if (shouldExists) {
                println("File: $file should exists, but does not!")
            } else {
                println("File: $file should not exists, but does!")
            }
            throw e
        }
    }

}