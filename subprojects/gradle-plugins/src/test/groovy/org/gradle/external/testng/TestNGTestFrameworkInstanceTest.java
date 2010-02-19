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

package org.gradle.external.testng;

import org.gradle.api.JavaVersion;
import org.gradle.api.internal.tasks.testing.testng.TestNGTestClassProcessor;
import org.gradle.api.tasks.testing.AbstractTestFrameworkInstanceTest;
import org.gradle.api.tasks.testing.testng.TestNGOptions;
import org.gradle.api.testing.TestClassProcessor;
import org.gradle.util.IdGenerator;
import org.jmock.Expectations;
import org.junit.Before;

import java.io.File;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

/**
 * @author Tom Eyckmans
 */
public class TestNGTestFrameworkInstanceTest extends AbstractTestFrameworkInstanceTest {

    private TestNGTestFramework testNgTestFrameworkMock;
    private TestNGTestFrameworkInstance testNGTestFrameworkInstance;
    private TestNGOptions testngOptionsMock;
    private IdGenerator idGeneratorMock;

    @Before
    public void setUp() throws Exception {
        super.setUp();

        testNgTestFrameworkMock = context.mock(TestNGTestFramework.class);
        testngOptionsMock = context.mock(TestNGOptions.class);
        idGeneratorMock = context.mock(IdGenerator.class);

        testNGTestFrameworkInstance = new TestNGTestFrameworkInstance(testMock, testNgTestFrameworkMock);
    }

    @org.junit.Test
    public void testInitialize() {
        setMocks();

        final JavaVersion sourceCompatibility = JavaVersion.VERSION_1_5;
        context.checking(new Expectations() {{
            one(projectMock).getProjectDir(); will(returnValue(new File("projectDir")));
            one(projectMock).property("sourceCompatibility"); will(returnValue(sourceCompatibility));
            one(testMock).getTestClassesDir();will(returnValue(testClassesDir));
            one(testMock).getClasspath();will(returnValue(classpathMock));
        }});

        testNGTestFrameworkInstance.initialize();

        assertNotNull(testNGTestFrameworkInstance.getOptions());
    }

    @org.junit.Test
    public void testCreatesTestProcessor() {
        setMocks();

        context.checking(new Expectations() {{
            allowing(testMock).getTestSrcDirs();  will(returnValue(testSrcDirs));
            allowing(testMock).getTestReportDir(); will(returnValue(testReportDir));
            one(testngOptionsMock).setTestResources(testSrcDirs);
            one(testngOptionsMock).getSuites(testReportDir);
        }});

        TestClassProcessor processor = testNGTestFrameworkInstance.getProcessorFactory().create(idGeneratorMock);
        assertThat(processor, instanceOf(TestNGTestClassProcessor.class));
    }

    private void setMocks() {
        testNGTestFrameworkInstance.setOptions(testngOptionsMock);
    }
}
