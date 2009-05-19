package org.gradle.external.testng;

import org.jmock.Expectations;
import org.gradle.api.JavaVersion;
import org.gradle.api.tasks.testing.AbstractTestFrameworkTest;
import org.gradle.api.tasks.testing.testng.AntTestNGExecute;
import org.gradle.api.tasks.testing.testng.TestNGOptions;
import org.gradle.external.testng.TestNGTestFramework;
import org.junit.Before;
import static org.junit.Assert.*;
import groovy.util.AntBuilder;

import java.io.File;

/**
 * @author Tom Eyckmans
 */
public class TestNGTestFrameworkTest extends AbstractTestFrameworkTest {

    private TestNGTestFramework testNGTestFramework;

    private AntTestNGExecute antTestNGExecuteMock;
    private TestNGOptions testngOptionsMock;
    private AntBuilder antBuilderMock;

    @Before
    public void setUp() throws Exception
    {
        super.setUp();

        antTestNGExecuteMock = context.mock(AntTestNGExecute.class);
        testngOptionsMock = context.mock(TestNGOptions.class);
        antBuilderMock = context.mock(AntBuilder.class);

        testNGTestFramework = new TestNGTestFramework();
    }

    @org.junit.Test
    public void testInitialize()
    {
        setMocks();

        final JavaVersion sourceCompatibility = JavaVersion.VERSION_1_5;
        context.checking(new Expectations(){{
            one(projectMock).getProjectDir();will(returnValue(new File("projectDir")));
            one(projectMock).property("sourceCompatibility");will(returnValue(sourceCompatibility));
            one(testngOptionsMock).setAnnotationsOnSourceCompatibility(sourceCompatibility);
        }});

        testNGTestFramework.initialize(projectMock, testMock);

        assertNotNull(testNGTestFramework.getOptions());
        assertNotNull(testNGTestFramework.getAntTestNGExecute());
    }

    @org.junit.Test
    public void testExecuteWithJDKAnnoations()
    {
        setMocks();

        expectHandleEmptyIncludesExcludes();

        context.checking(new Expectations(){{
            one(testMock).getTestSrcDirs();will(returnValue(testSrcDirs));
            one(testngOptionsMock).setTestResources(testSrcDirs);
            one(testMock).getTestClassesDir();will(returnValue(testClassesDir));
            one(testMock).getClasspath();will(returnValue(null));
            one(testMock).getTestResultsDir();will(returnValue(testResultsDir));
            one(testMock).getTestReportDir();will(returnValue(testReportDir));
            one(testMock).getIncludes();will(returnValue(null));
            one(testMock).getExcludes();will(returnValue(null));
            one(projectMock).getAnt();will(returnValue(antBuilderMock));
            one(testMock).isTestReport();will(returnValue(true));
            one(antTestNGExecuteMock).execute(
                testClassesDir, null, testResultsDir, testReportDir, null, null,
                testngOptionsMock,
                antBuilderMock,
                true
            );
        }});

        testNGTestFramework.execute(projectMock, testMock, null, null);
    }

    private void setMocks()
    {
        testNGTestFramework.setAntTestNGExecute(antTestNGExecuteMock);
        testNGTestFramework.setOptions(testngOptionsMock);
    }
}
