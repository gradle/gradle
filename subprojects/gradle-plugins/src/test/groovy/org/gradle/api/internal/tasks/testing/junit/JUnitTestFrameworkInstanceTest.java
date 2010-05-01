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

package org.gradle.api.internal.tasks.testing.junit;

import org.gradle.api.internal.project.ServiceRegistry;
import org.gradle.api.internal.tasks.testing.AbstractTestFrameworkInstanceTest;
import org.gradle.api.internal.tasks.testing.TestClassProcessor;
import org.gradle.api.tasks.testing.junit.JUnitOptions;
import org.gradle.util.IdGenerator;
import org.jmock.Expectations;
import org.junit.Before;

import static junit.framework.Assert.assertNotNull;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

/**
 * @author Tom Eyckmans
 */
public class JUnitTestFrameworkInstanceTest extends AbstractTestFrameworkInstanceTest {

    private JUnitTestFrameworkInstance jUnitTestFrameworkInstance;

    private AntJUnitReport antJUnitReportMock;
    private JUnitOptions jUnitOptionsMock;
    private IdGenerator<?> idGenerator;
    private ServiceRegistry serviceRegistry;

    @Before
    public void setUp() throws Exception {
        super.setUp();

        JUnitTestFramework jUnitTestFrameworkMock = context.mock(JUnitTestFramework.class);
        antJUnitReportMock = context.mock(AntJUnitReport.class);
        jUnitOptionsMock = context.mock(JUnitOptions.class);
        idGenerator = context.mock(IdGenerator.class);
        serviceRegistry = context.mock(ServiceRegistry.class);

        jUnitTestFrameworkInstance = new JUnitTestFrameworkInstance(testMock, jUnitTestFrameworkMock);
    }

    @org.junit.Test
    public void testInitialize() {
        setMocks();

        context.checking(new Expectations() {{
            one(testMock).getTestClassesDir();will(returnValue(testClassesDir));
            one(testMock).getClasspath();will(returnValue(classpathMock));
        }});

        jUnitTestFrameworkInstance.initialize();

        assertNotNull(jUnitTestFrameworkInstance.getOptions());
        assertNotNull(jUnitTestFrameworkInstance.getAntJUnitReport());
    }

    @org.junit.Test
    public void testCreatesTestProcessor() {
        setMocks();

        context.checking(new Expectations() {{
            one(testMock).getTestResultsDir(); will(returnValue(testResultsDir));
            one(serviceRegistry).get(IdGenerator.class); will(returnValue(idGenerator));
        }});

        TestClassProcessor testClassProcessor = jUnitTestFrameworkInstance.getProcessorFactory().create(serviceRegistry);
        assertThat(testClassProcessor, instanceOf(JUnitTestClassProcessor.class));
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
