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

package org.gradle.api.internal.tasks.testing.testng;

import org.gradle.api.JavaVersion;
import org.gradle.internal.Factory;
import org.gradle.internal.id.IdGenerator;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.api.internal.tasks.testing.AbstractTestFrameworkTest;
import org.gradle.api.internal.tasks.testing.TestClassProcessor;
import org.gradle.api.tasks.testing.testng.TestNGOptions;
import org.jmock.Expectations;
import org.junit.Before;

import java.io.File;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

/**
 * @author Tom Eyckmans
 */
public class TestNGTestFrameworkTest extends AbstractTestFrameworkTest {

    private TestNGTestFramework testNGTestFramework;
    private TestNGOptions testngOptionsMock;
    private IdGenerator<?> idGeneratorMock;
    private ServiceRegistry serviceRegistry;

    @Before
    public void setUp() throws Exception {
        super.setUp();

        testngOptionsMock = context.mock(TestNGOptions.class);
        idGeneratorMock = context.mock(IdGenerator.class);
        serviceRegistry = context.mock(ServiceRegistry.class);
        final Factory<File> temporaryDirFactory = new Factory<File>() {
            public File create() {
                return temporaryDir;
            }
        };

        final JavaVersion sourceCompatibility = JavaVersion.VERSION_1_5;
        context.checking(new Expectations() {{
            allowing(projectMock).getProjectDir(); will(returnValue(new File("projectDir")));
            allowing(projectMock).property("sourceCompatibility"); will(returnValue(sourceCompatibility));
            allowing(testMock).getTestClassesDir(); will(returnValue(testClassesDir));
            allowing(testMock).getClasspath(); will(returnValue(classpathMock));
            allowing(testMock).getTemporaryDir(); will(returnValue(temporaryDir));
            allowing(testMock).getTemporaryDirFactory(); will(returnValue(temporaryDirFactory));
        }});
    }

    @org.junit.Test
    public void testInitialize() {
        testNGTestFramework = new TestNGTestFramework(testMock);
        setMocks();

        assertNotNull(testNGTestFramework.getOptions());
    }

    @org.junit.Test
    public void testCreatesTestProcessor() {
        testNGTestFramework = new TestNGTestFramework(testMock);
        setMocks();

        context.checking(new Expectations() {{
            allowing(testMock).getTestSrcDirs();    will(returnValue(testSrcDirs));
            allowing(testMock).getTestReportDir();  will(returnValue(testReportDir));
            allowing(testMock).getTestResultsDir(); will(returnValue(testResultsDir));
            allowing(testMock).isTestReport();      will(returnValue(false));
            allowing(serviceRegistry).get(IdGenerator.class); will(returnValue(idGeneratorMock));
            one(testngOptionsMock).setTestResources(testSrcDirs);
            one(testngOptionsMock).getSuites(temporaryDir);
        }});

        TestClassProcessor processor = testNGTestFramework.getProcessorFactory().create(serviceRegistry);
        assertThat(processor, instanceOf(TestNGTestClassProcessor.class));
    }

    private void setMocks() {
        testNGTestFramework.setOptions(testngOptionsMock);
    }
}
