/*
 * Copyright 2007 the original author or authors.
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

package org.gradle.api.tasks.testing;

import org.apache.commons.io.FileUtils;
import org.gradle.api.GradleException;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.ConventionTask;
import org.gradle.api.tasks.AbstractConventionTaskTest;
import org.gradle.api.tasks.StopActionException;
import org.gradle.api.tasks.util.ExistingDirsFilter;
import org.gradle.api.testing.TestFramework;
import org.gradle.util.GUtil;
import org.gradle.util.WrapUtil;
import org.hamcrest.Matchers;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.After;
import static org.junit.Assert.*;
import org.junit.Before;

import java.io.File;
import java.io.IOException;
import java.util.*;


/**
 * @author Hans Dockter
 */
public class TestTest extends AbstractConventionTaskTest {
    static final String TEST_PATTERN_1 = "pattern1";
    static final String TEST_PATTERN_2 = "pattern2";
    static final String TEST_PATTERN_3 = "pattern3";

    static final File TEST_ROOT_DIR = new File("ROOTDir");
    static final File TEST_TEST_CLASSES_DIR = new File(TEST_ROOT_DIR, "testClassesDir");
    static final File TEST_TEST_RESULTS_DIR = new File(TEST_ROOT_DIR, "resultDir");
    static final File TEST_TEST_REPORT_DIR = new File(TEST_ROOT_DIR, "report/tests");

    static final Set TEST_DEPENDENCY_MANAGER_CLASSPATH = WrapUtil.toSet(new File("jar1"));
    static final List TEST_CONVERTED_CLASSPATH = GUtil.addLists(WrapUtil.toList(TEST_TEST_CLASSES_DIR),
            TEST_DEPENDENCY_MANAGER_CLASSPATH);

    static final Set<String> okTestClassNames = new HashSet<String>(Arrays.asList("test.HumanTest", "test.CarTest"));

    private JUnit4Mockery context = new JUnit4Mockery() {{
        setImposteriser(ClassImposteriser.INSTANCE);
    }};

    TestFramework testFrameworkMock = context.mock(TestFramework.class);

    private ExistingDirsFilter existentDirsFilterMock = context.mock(ExistingDirsFilter.class);
    private FileCollection configurationMock = context.mock(FileCollection.class);

    private Test test;

    @Before public void setUp() {
        super.setUp();
        test = createTask(Test.class);
        context.checking(new Expectations(){{
            one(testFrameworkMock).initialize(getProject(), test);
        }});
        test.useTestFramework(testFrameworkMock);
        
        if ( !TEST_TEST_CLASSES_DIR.exists() )
            assertTrue(TEST_TEST_CLASSES_DIR.mkdirs());
    }

    public ConventionTask getTask() {
        return test;
    }

    @org.junit.Test public void testInit() {
        assertNotNull(test.getTestFramework());
        assertNotNull(test.existingDirsFilter);
        assertNull(test.getTestClassesDir());
        assertNull(test.getConfiguration());
        assertNull(test.getTestResultsDir());
        assertNull(test.getTestReportDir());
        assertEquals(WrapUtil.toLinkedSet(), test.getIncludes());
        assertEquals(WrapUtil.toLinkedSet(), test.getExcludes());
        assert test.isStopAtFailuresOrErrors();
    }

    @org.junit.Test
    public void testExecute() {
        setUpMocks(test);
        setExistingDirsFilter();
        context.checking(new Expectations() {{
            one(testFrameworkMock).prepare(getProject(), test);
            one(testFrameworkMock).getTestClassNames();will(returnValue(okTestClassNames));
            one(testFrameworkMock).execute(getProject(), test, okTestClassNames, new ArrayList<String>());
            one(testFrameworkMock).report(getProject(), test);
        }});

        test.execute();
    }

    @org.junit.Test
    public void testExecuteWithoutReporting() {
        setUpMocks(test);
        setExistingDirsFilter();
        test.setTestReport(false);
        context.checking(new Expectations() {{
            one(testFrameworkMock).prepare(getProject(), test);
            one(testFrameworkMock).getTestClassNames();will(returnValue(okTestClassNames));
            one(testFrameworkMock).execute(getProject(), test, okTestClassNames, new ArrayList<String>());
        }});

        test.execute();
    }

    @org.junit.Test(expected = GradleException.class)
    public void testExecuteWithTestFailuresAndStopAtFailures() {
        setUpMocks(test);
        setExistingDirsFilter();
        context.checking(new Expectations() {{
            one(testFrameworkMock).prepare(getProject(), test);
            one(testFrameworkMock).getTestClassNames();will(returnValue(okTestClassNames));
            one(testFrameworkMock).execute(getProject(), test, okTestClassNames, new ArrayList<String>());
        }});
        test.execute();
    }

    @org.junit.Test public void testExecuteWithTestFailuresAndContinueWithFailures() {
        setUpMocks(test);
        setExistingDirsFilter();
        test.setStopAtFailuresOrErrors(false);
        context.checking(new Expectations() {{
            one(testFrameworkMock).prepare(getProject(), test);
            one(testFrameworkMock).getTestClassNames();will(returnValue(okTestClassNames));
            one(testFrameworkMock).execute(getProject(), test, okTestClassNames, new ArrayList<String>());
            one(testFrameworkMock).report(getProject(), test);
        }});
        test.execute();
    }

    @org.junit.Test
    public void testGetClasspath() {
        setUpMocks(test);
        assertEquals(TEST_CONVERTED_CLASSPATH, test.getClasspath());
    }

    public void testExecuteWithUnspecifiedCompiledTestsDir() {
        setUpMocks(test);
        test.setTestClassesDir(null);
        try {
            test.execute();
            fail();
        } catch (Exception e) {
            assertThat(e.getCause(), Matchers.instanceOf(InvalidUserDataException.class));
        }
    }

    public void testExecuteWithUnspecifiedTestResultsDir() {
        setUpMocks(test);
        test.setTestResultsDir(null);
        try {
            test.execute();
            fail();
        } catch (Exception e) {
            assertThat(e.getCause(), Matchers.instanceOf(InvalidUserDataException.class));
        }
    }

    @org.junit.Test public void testExecuteWithNonExistingCompiledTestsDir() {
        setUpMocks(test);
        context.checking(new Expectations() {{
            allowing(existentDirsFilterMock).checkExistenceAndThrowStopActionIfNot(TEST_TEST_CLASSES_DIR); will(throwException(new StopActionException()));
        }});
        test.existingDirsFilter = existentDirsFilterMock;

        test.execute();
    }

    private void setUpMocks(final Test test) {
        test.setTestClassesDir(TEST_TEST_CLASSES_DIR);
        test.setTestResultsDir(TEST_TEST_RESULTS_DIR);
        test.setTestReportDir(TEST_TEST_REPORT_DIR);
        test.setConfiguration(configurationMock);

        context.checking(new Expectations() {{
            allowing(configurationMock).iterator();
            will(returnIterator(TEST_DEPENDENCY_MANAGER_CLASSPATH));
        }});
    }

    @org.junit.Test public void testIncludes() {
        assertSame(test, test.include(TEST_PATTERN_1, TEST_PATTERN_2));
        assertEquals(WrapUtil.toLinkedSet(TEST_PATTERN_1, TEST_PATTERN_2), test.getIncludes());
        test.include(TEST_PATTERN_3);
        assertEquals(WrapUtil.toLinkedSet(TEST_PATTERN_1, TEST_PATTERN_2, TEST_PATTERN_3), test.getIncludes());
    }

    @org.junit.Test public void testExcludes() {
        assertSame(test, test.exclude(TEST_PATTERN_1, TEST_PATTERN_2));
        assertEquals(WrapUtil.toLinkedSet(TEST_PATTERN_1, TEST_PATTERN_2), test.getExcludes());
        test.exclude(TEST_PATTERN_3);
        assertEquals(WrapUtil.toLinkedSet(TEST_PATTERN_1, TEST_PATTERN_2, TEST_PATTERN_3), test.getExcludes());
    }

    private void setExistingDirsFilter() {
        context.checking(new Expectations() {{
            allowing(existentDirsFilterMock).checkExistenceAndThrowStopActionIfNot(TEST_TEST_CLASSES_DIR);
        }});
        test.existingDirsFilter = existentDirsFilterMock;
    }

    @After
    public void tearDown() throws IOException {
        FileUtils.deleteDirectory(TEST_ROOT_DIR);
    }
}
