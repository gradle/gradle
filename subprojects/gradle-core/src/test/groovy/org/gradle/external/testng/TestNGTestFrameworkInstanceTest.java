package org.gradle.external.testng;

import org.gradle.api.AntBuilder;
import org.gradle.api.JavaVersion;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.testing.AbstractTestFrameworkInstanceTest;
import org.gradle.api.tasks.testing.AbstractTestTask;
import org.gradle.api.tasks.testing.AntTest;
import org.gradle.api.tasks.testing.testng.AntTestNGExecute;
import org.gradle.api.tasks.testing.testng.TestNGOptions;
import org.jmock.Expectations;
import static org.junit.Assert.assertNotNull;
import org.junit.Before;

import java.io.File;
import java.util.TreeSet;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;

/**
 * @author Tom Eyckmans
 */
public class TestNGTestFrameworkInstanceTest extends AbstractTestFrameworkInstanceTest {

    private TestNGTestFramework testNgTestFrameworkMock;
    private TestNGTestFrameworkInstance testNGTestFrameworkInstance;

    private AntTestNGExecute antTestNGExecuteMock;
    private TestNGOptions testngOptionsMock;
    private AntBuilder antBuilderMock;
    private AbstractTestTask testTask;
    private FileCollection classpathMock;

    @Before
    public void setUp() throws Exception {
        super.setUp();

        testNgTestFrameworkMock = context.mock(TestNGTestFramework.class);
        antTestNGExecuteMock = context.mock(AntTestNGExecute.class);
        testngOptionsMock = context.mock(TestNGOptions.class);
        antBuilderMock = context.mock(AntBuilder.class);
        testTask = context.mock(AntTest.class, "TestNGTestFrameworkInstanceTest");
        classpathMock = context.mock(FileCollection.class);

        testNGTestFrameworkInstance = new TestNGTestFrameworkInstance(testTask, testNgTestFrameworkMock);
    }

    @org.junit.Test
    public void testInitialize() {
        setMocks();

        final JavaVersion sourceCompatibility = JavaVersion.VERSION_1_5;
        context.checking(new Expectations() {{
            one(projectMock).getProjectDir(); will(returnValue(new File("projectDir")));
            one(projectMock).property("sourceCompatibility"); will(returnValue(sourceCompatibility));
            one(testngOptionsMock).setAnnotationsOnSourceCompatibility(sourceCompatibility);
        }});

        testNGTestFrameworkInstance.initialize(projectMock, testMock);

        assertNotNull(testNGTestFrameworkInstance.getOptions());
        assertNotNull(testNGTestFrameworkInstance.getAntTestNGExecute());
    }

    @org.junit.Test
    public void testExecuteWithJDKAnnoations() {
        setMocks();

        context.checking(new Expectations() {{
            one(testMock).getTestSrcDirs();  will(returnValue(testSrcDirs));
            one(testngOptionsMock).setTestResources(testSrcDirs);
            one(testMock).getTestClassesDir(); will(returnValue(testClassesDir));
            one(testMock).getClasspath(); will(returnValue(classpathMock));
            one(testMock).getTestResultsDir(); will(returnValue(testResultsDir));
            one(testMock).getTestReportDir(); will(returnValue(testReportDir));
            one(projectMock).getAnt(); will(returnValue(antBuilderMock));
            one(antTestNGExecuteMock).execute(
                    testClassesDir, classpathMock, testResultsDir, testReportDir, null, null,
                    testngOptionsMock,
                    antBuilderMock
            );
        }});

        testNGTestFrameworkInstance.execute(projectMock, testMock, null, null);
    }

    private void setMocks() {
        testNGTestFrameworkInstance.setAntTestNGExecute(antTestNGExecuteMock);
        testNGTestFrameworkInstance.setOptions(testngOptionsMock);
    }
}
