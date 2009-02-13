package org.gradle.api.tasks.testing.junit;

import org.junit.Before;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.jmock.Expectations;
import org.gradle.api.tasks.testing.Test;
import org.gradle.api.tasks.testing.JunitForkOptions;
import org.gradle.api.tasks.testing.ForkMode;
import org.gradle.api.tasks.testing.AbstractTestFrameworkTest;
import org.gradle.api.Project;
import org.gradle.api.Plugin;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.GroovyPlugin;
import static junit.framework.Assert.*;

import java.io.File;
import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.HashSet;

import groovy.util.AntBuilder;

/**
 * @author Tom Eyckmans
 */
public class JUnitTestFrameworkTest extends AbstractTestFrameworkTest {

    private JUnitTestFramework jUnitTestFramework;

    private AntJUnitExecute antJUnitExecuteMock;
    private AntJUnitReport antJUnitReportMock;
    private JUnitOptions jUnitOptionsMock;
    private JunitForkOptions jUnitForkOptionsMock;

    private AntBuilder antBuilderMock;

    @Before
    public void setUp() throws Exception
    {
        super.setUp();

        antJUnitExecuteMock = context.mock(AntJUnitExecute.class);
        antJUnitReportMock = context.mock(AntJUnitReport.class);
        jUnitOptionsMock = context.mock(JUnitOptions.class);
        jUnitForkOptionsMock = context.mock(JunitForkOptions.class);
        antBuilderMock = context.mock(AntBuilder.class);

        jUnitTestFramework = new JUnitTestFramework();
    }

    @org.junit.Test
    public void testInitialize()
    {
        setMocks();

        context.checking(new Expectations(){{
            one(projectMock).getAppliedPlugins(); will(returnValue(new HashSet(Arrays.asList(JavaPlugin.class))));
            one(jUnitOptionsMock).getForkOptions();will(returnValue(jUnitForkOptionsMock));
            one(jUnitOptionsMock).setFork(true);
            one(jUnitForkOptionsMock).setForkMode(ForkMode.PER_TEST);
            one(projectMock).getProjectDir();will(returnValue(projectDir));
            one(jUnitForkOptionsMock).setDir(projectDir);
        }});

        jUnitTestFramework.initialize(projectMock, testMock);

        assertNotNull(jUnitTestFramework.getOptions());
        assertNotNull(jUnitTestFramework.getAntJUnitExecute());
        assertNotNull(jUnitTestFramework.getAntJUnitReport());
    }



    @org.junit.Test
    public void testExecute()
    {
        setMocks();

        expectHandleEmptyIncludesExcludes();

        context.checking(new Expectations() {{
            one(testMock).getTestClassesDir();will(returnValue(testClassesDir));
            one(testMock).getClasspath();will(returnValue(null));
            one(testMock).getTestResultsDir();will(returnValue(testResultsDir));
            one(testMock).getIncludes();will(returnValue(null));
            one(testMock).getExcludes();will(returnValue(null));
            one(projectMock).getAnt();will(returnValue(antBuilderMock));
            one(antJUnitExecuteMock).execute(
                testClassesDir, null, testResultsDir, null, null,
                jUnitOptionsMock,
                antBuilderMock
            );
        }});

        jUnitTestFramework.execute(projectMock, testMock);
    }

    @org.junit.Test
    public void testReport()
    {
        setMocks();

        context.checking(new Expectations() {{
            one(testMock).getTestResultsDir();will(returnValue(testResultsDir));
            one(testMock).getTestReportDir();will(returnValue(testReportDir));
            one(projectMock).getAnt();will(returnValue(antBuilderMock));
            one(antJUnitReportMock).execute(
                testResultsDir, testReportDir,
                antBuilderMock
            );
        }});

        jUnitTestFramework.report(projectMock, testMock);
    }

    private void setMocks()
    {
        jUnitTestFramework.setAntJUnitExecute(antJUnitExecuteMock);
        jUnitTestFramework.setAntJUnitReport(antJUnitReportMock);
        jUnitTestFramework.setOptions(jUnitOptionsMock);
    }
}
