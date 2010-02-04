/*
 * Copyright 2010 the original author or authors.
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

import org.gradle.api.file.FileVisitor;
import org.gradle.api.internal.ClassPathRegistry;
import org.gradle.api.tasks.testing.AbstractTestFrameworkInstanceTest;
import org.gradle.api.tasks.testing.JunitForkOptions;
import org.gradle.api.tasks.testing.TestListener;
import org.gradle.api.tasks.testing.junit.AntJUnitExecute;
import org.gradle.api.tasks.testing.junit.AntJUnitReport;
import org.gradle.api.tasks.testing.junit.JUnitOptions;
import org.gradle.api.testing.TestClassProcessor;
import org.gradle.listener.ListenerBroadcast;
import org.jmock.Expectations;
import org.junit.Before;

import java.io.File;
import java.util.Set;
import java.util.TreeSet;

import static junit.framework.Assert.assertNotNull;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

/**
 * @author Tom Eyckmans
 */
public class JUnitTestFrameworkInstanceTest extends AbstractTestFrameworkInstanceTest {

    private JUnitTestFramework jUnitTestFrameworkMock;
    private JUnitTestFrameworkInstance jUnitTestFrameworkInstance;

    private AntJUnitReport antJUnitReportMock;
    private JUnitOptions jUnitOptionsMock;
    private JunitForkOptions jUnitForkOptionsMock;
    private ListenerBroadcast<TestListener> listenerBroadcastMock;

    @Before
    public void setUp() throws Exception {
        super.setUp();

        jUnitTestFrameworkMock = context.mock(JUnitTestFramework.class);
        antJUnitReportMock = context.mock(AntJUnitReport.class);
        jUnitOptionsMock = context.mock(JUnitOptions.class);
        jUnitForkOptionsMock = context.mock(JunitForkOptions.class);
        listenerBroadcastMock = context.mock(ListenerBroadcast.class);

        jUnitTestFrameworkInstance = new JUnitTestFrameworkInstance(testMock, jUnitTestFrameworkMock);
    }

    @org.junit.Test
    public void testInitialize() {
        setMocks();

        context.checking(new Expectations() {{
            one(jUnitOptionsMock).getForkOptions(); will(returnValue(jUnitForkOptionsMock));
            one(projectMock).getProjectDir(); will(returnValue(projectDir));
            one(jUnitForkOptionsMock).setDir(projectDir);
            one(testMock).getTestClassesDir();will(returnValue(testClassesDir));
            one(testMock).getClasspath();will(returnValue(classpathMock));
            one(classpathMock).getAsFileTree();will(returnValue(classpathAsFileTreeMock));
            one(classpathAsFileTreeMock).visit(with(aNonNull(FileVisitor.class)));
        }});

        jUnitTestFrameworkInstance.initialize();

        assertNotNull(jUnitTestFrameworkInstance.getOptions());
        assertNotNull(jUnitTestFrameworkInstance.getAntJUnitReport());
    }


    @org.junit.Test
    public void testCreatesTestProcessor() {
        setMocks();

        expectHandleEmptyIncludesExcludes();

        final Set<File> classpathSet = new TreeSet<File>();

        context.checking(new Expectations() {{
            one(testMock).getClassPathRegistry();will(returnValue(context.mock(ClassPathRegistry.class)));
            one(testMock).getTestListenerBroadcaster(); will(returnValue(listenerBroadcastMock));
            one(testMock).getTestClassesDir(); will(returnValue(testClassesDir));
            one(testMock).getClasspath(); will(returnValue(classpathMock));
            one(classpathMock).getFiles(); will(returnValue(classpathSet));
            one(testMock).getTestResultsDir(); will(returnValue(testResultsDir));
            one(testMock).getIncludes(); will(returnValue(null));
            one(testMock).getExcludes(); will(returnValue(null));
            one(projectMock).getAnt(); will(returnValue(antBuilderMock));
        }});

        TestClassProcessor testClassProcessor = jUnitTestFrameworkInstance.getProcessorFactory().create();
        assertThat(testClassProcessor, instanceOf(AntJUnitExecute.class));
    }

    @org.junit.Test
    public void testReport() {
        setMocks();

        context.checking(new Expectations() {{
            one(testMock).getTestResultsDir(); will(returnValue(testResultsDir));
            one(testMock).getTestReportDir(); will(returnValue(testReportDir));
            one(projectMock).getAnt(); will(returnValue(antBuilderMock));
            one(testMock).isTestReport(); will(returnValue(true));
            one(antJUnitReportMock).execute(
                    testResultsDir, testReportDir,
                    antBuilderMock
            );
        }});

        jUnitTestFrameworkInstance.report();
    }

    @org.junit.Test
    public void testReportWithDisabledReport() {
        setMocks();

        context.checking(new Expectations() {{
            one(testMock).isTestReport(); will(returnValue(false));
        }});

        jUnitTestFrameworkInstance.report();
    }

    private void setMocks() {
        jUnitTestFrameworkInstance.setAntJUnitReport(antJUnitReportMock);
        jUnitTestFrameworkInstance.setOptions(jUnitOptionsMock);
    }
}
