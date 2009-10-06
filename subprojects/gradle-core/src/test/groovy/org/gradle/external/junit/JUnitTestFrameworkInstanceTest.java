/*
 * Copyright 2009 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.external.junit;

import static junit.framework.Assert.assertNotNull;
import org.gradle.api.AntBuilder;
import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.ProjectPluginsContainer;
import org.gradle.api.tasks.testing.*;
import org.gradle.api.tasks.testing.junit.AntJUnitExecute;
import org.gradle.api.tasks.testing.junit.AntJUnitReport;
import org.gradle.api.tasks.testing.junit.JUnitOptions;
import org.hamcrest.Matchers;
import org.jmock.Expectations;
import org.junit.Before;

import java.io.File;
import java.util.TreeSet;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;

/**
 * @author Tom Eyckmans
 */
public class JUnitTestFrameworkInstanceTest extends AbstractTestFrameworkInstanceTest {

    private JUnitTestFramework jUnitTestFrameworkMock;
    private JUnitTestFrameworkInstance jUnitTestFrameworkInstance;

    private AntJUnitExecute antJUnitExecuteMock;
    private AntJUnitReport antJUnitReportMock;
    private JUnitOptions jUnitOptionsMock;
    private JunitForkOptions jUnitForkOptionsMock;
    private AbstractTestTask testTask;
    private FileCollection classpathMock;

    private AntBuilder antBuilderMock;

    private ProjectPluginsContainer projectPluginsHandlerMock;

    @Before
    public void setUp() throws Exception {
        super.setUp();

        jUnitTestFrameworkMock = context.mock(JUnitTestFramework.class);
        antJUnitExecuteMock = context.mock(AntJUnitExecute.class);
        antJUnitReportMock = context.mock(AntJUnitReport.class);
        jUnitOptionsMock = context.mock(JUnitOptions.class);
        jUnitForkOptionsMock = context.mock(JunitForkOptions.class);
        antBuilderMock = context.mock(AntBuilder.class);
        projectPluginsHandlerMock = context.mock(ProjectPluginsContainer.class);
        testTask = context.mock(AntTest.class, "JUnitTestFrameworkInstanceTest");
        classpathMock = context.mock(FileCollection.class);

        jUnitTestFrameworkInstance = new JUnitTestFrameworkInstance(testTask, jUnitTestFrameworkMock);
    }

    @org.junit.Test
    public void testInitialize() {
        setMocks();

        context.checking(new Expectations() {{
            allowing(projectMock).getPlugins(); will(returnValue(projectPluginsHandlerMock));
            allowing(projectPluginsHandlerMock).hasPlugin(JavaPlugin.class); will(returnValue(true));
            allowing(projectPluginsHandlerMock).hasPlugin(with(Matchers.not(JavaPlugin.class))); will(returnValue(true));
            one(jUnitOptionsMock).getForkOptions(); will(returnValue(jUnitForkOptionsMock));
            one(jUnitOptionsMock).setFork(true);
            one(jUnitForkOptionsMock).setForkMode(ForkMode.PER_TEST);
            one(projectMock).getProjectDir(); will(returnValue(projectDir));
            one(jUnitForkOptionsMock).setDir(projectDir);
        }});

        jUnitTestFrameworkInstance.initialize(projectMock, testMock);

        assertNotNull(jUnitTestFrameworkInstance.getOptions());
        assertNotNull(jUnitTestFrameworkInstance.getAntJUnitExecute());
        assertNotNull(jUnitTestFrameworkInstance.getAntJUnitReport());
    }


    @org.junit.Test
    public void testExecute() {
        setMocks();

        expectHandleEmptyIncludesExcludes();

        final Set<File> classpathSet = new TreeSet<File>();
        final List<File> classpathList = new ArrayList<File>();

        context.checking(new Expectations() {{
            one(testMock).getTestClassesDir(); will(returnValue(testClassesDir));
            one(testMock).getClasspath(); will(returnValue(classpathMock));
            one(classpathMock).getFiles(); will(returnValue(classpathSet));
            one(testMock).getTestResultsDir(); will(returnValue(testResultsDir));
            one(testMock).getIncludes(); will(returnValue(null));
            one(testMock).getExcludes(); will(returnValue(null));
            one(projectMock).getAnt(); will(returnValue(antBuilderMock));
            one(antJUnitExecuteMock).execute(
                    testClassesDir, classpathList, testResultsDir, null, null,
                    jUnitOptionsMock,
                    antBuilderMock
            );
        }});

        jUnitTestFrameworkInstance.execute(projectMock, testMock, null, null);
    }

    @org.junit.Test
    public void testReport() {
        setMocks();

        context.checking(new Expectations() {{
            one(testMock).getTestResultsDir(); will(returnValue(testResultsDir));
            one(testMock).getTestReportDir(); will(returnValue(testReportDir));
            one(projectMock).getAnt(); will(returnValue(antBuilderMock));
            one(antJUnitReportMock).execute(
                    testResultsDir, testReportDir,
                    antBuilderMock
            );
        }});

        jUnitTestFrameworkInstance.report(projectMock, testMock);
    }

    private void setMocks() {
        jUnitTestFrameworkInstance.setAntJUnitExecute(antJUnitExecuteMock);
        jUnitTestFrameworkInstance.setAntJUnitReport(antJUnitReportMock);
        jUnitTestFrameworkInstance.setOptions(jUnitOptionsMock);
    }
}
