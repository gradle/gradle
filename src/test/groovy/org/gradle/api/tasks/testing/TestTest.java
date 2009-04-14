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

import org.gradle.api.GradleException;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.FileCollection;
import org.gradle.api.internal.AbstractTask;
import org.gradle.api.internal.project.AbstractProject;
import org.gradle.api.tasks.AbstractConventionTaskTest;
import org.gradle.api.tasks.AbstractTaskTest;
import org.gradle.api.tasks.StopActionException;
import org.gradle.api.tasks.compile.ClasspathConverter;
import org.gradle.api.tasks.util.ExistingDirsFilter;
import org.gradle.util.GUtil;
import org.gradle.util.WrapUtil;
import org.hamcrest.Matchers;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import static org.junit.Assert.*;
import org.junit.Before;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;


/**
 * @author Hans Dockter
 */
public class TestTest extends AbstractConventionTaskTest {
    static final String TEST_PATTERN_1 = "pattern1";
    static final String TEST_PATTERN_2 = "pattern2";
    static final String TEST_PATTERN_3 = "pattern3";

    static final File TEST_TEST_CLASSES_DIR = new File("/testClassesDir");
    static final File TEST_TEST_RESULTS_DIR = new File("/resultDir");
    static final File TEST_TEST_REPORT_DIR = new File("/report/tests");
    static final File TEST_ROOT_DIR = new File("/ROOTDir");

    static final Set TEST_DEPENDENCY_MANAGER_CLASSPATH = WrapUtil.toSet(new File("jar1"));
    static final List TEST_CONVERTED_UNMANAGED_CLASSPATH = WrapUtil.toList(new File("jar2"));
    static final List TEST_UNMANAGED_CLASSPATH = WrapUtil.toList("jar2");
    static final List TEST_CONVERTED_CLASSPATH = GUtil.addLists(WrapUtil.toList(TEST_TEST_CLASSES_DIR),
            TEST_CONVERTED_UNMANAGED_CLASSPATH, TEST_DEPENDENCY_MANAGER_CLASSPATH);

    private JUnit4Mockery context = new JUnit4Mockery() {{
        setImposteriser(ClassImposteriser.INSTANCE);
    }};

    TestFramework testFrameworkMock = context.mock(TestFramework.class);

    private ClasspathConverter classpathConverterMock = context.mock(ClasspathConverter.class);
    private ExistingDirsFilter existentDirsFilterMock = context.mock(ExistingDirsFilter.class);
    private FileCollection configurationMock = context.mock(FileCollection.class);

    private Test test;

    @Before public void setUp() {
        super.setUp();
        test = new Test(getProject(), AbstractTaskTest.TEST_TASK_NAME);
        ((AbstractProject) test.getProject()).setProjectDir(TEST_ROOT_DIR);
        context.checking(new Expectations(){{
            one(testFrameworkMock).initialize(getProject(), test);
        }});
        test.useTestFramework(testFrameworkMock);
    }

    public AbstractTask getTask() {
        return test;
    }

    @org.junit.Test public void testInit() {
        assertNotNull(test.getTestFramework());
        assertNotNull(test.existingDirsFilter);
        assertNotNull(test.classpathConverter);
        assertNull(test.getTestClassesDir());
        assertNull(test.getConfiguration());
        assertNull(test.getTestResultsDir());
        assertNull(test.getTestReportDir());
        assertNull(test.getIncludes());
        assertNull(test.getExcludes());
        assertNull(test.getUnmanagedClasspath());
        assert test.isStopAtFailuresOrErrors();
    }

    @org.junit.Test
    public void testExecute() {
        setUpMocks(test);
        setExistingDirsFilter();
        context.checking(new Expectations() {{
            one(testFrameworkMock).execute(getProject(), test);
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
            one(testFrameworkMock).execute(getProject(), test);
        }});

        test.execute();
    }

    @org.junit.Test(expected = GradleException.class)
    public void testExecuteWithTestFailuresAndStopAtFailures() {
        setUpMocks(test);
        setExistingDirsFilter();
        context.checking(new Expectations() {{
            one(testFrameworkMock).execute(getProject(), test);
        }});
        test.execute();
    }

    @org.junit.Test public void testExecuteWithTestFailuresAndContinueWithFailures() {
        setUpMocks(test);
        setExistingDirsFilter();
        test.setStopAtFailuresOrErrors(false);
        context.checking(new Expectations() {{
            one(testFrameworkMock).execute(getProject(), test);
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
        test.setUnmanagedClasspath(null);
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
        test.setUnmanagedClasspath(TEST_UNMANAGED_CLASSPATH);
        test.setConfiguration(configurationMock);
        test.classpathConverter = classpathConverterMock;

        context.checking(new Expectations() {{
            allowing(configurationMock).iterator();
            will(returnIterator(TEST_DEPENDENCY_MANAGER_CLASSPATH));
            allowing(classpathConverterMock).createFileClasspath(TEST_ROOT_DIR, GUtil.addLists(WrapUtil.toList(
                    TEST_TEST_CLASSES_DIR), TEST_UNMANAGED_CLASSPATH, TEST_DEPENDENCY_MANAGER_CLASSPATH));
            will(returnValue(TEST_CONVERTED_CLASSPATH));
        }});
    }

    @org.junit.Test public void testIncludes() {
        assertSame(test, test.include(TEST_PATTERN_1, TEST_PATTERN_2));
        assertEquals(WrapUtil.toList(TEST_PATTERN_1, TEST_PATTERN_2), test.getIncludes());
        test.include(TEST_PATTERN_3);
        assertEquals(WrapUtil.toList(TEST_PATTERN_1, TEST_PATTERN_2, TEST_PATTERN_3), test.getIncludes());
    }

    @org.junit.Test public void testExcludes() {
        assertSame(test, test.exclude(TEST_PATTERN_1, TEST_PATTERN_2));
        assertEquals(WrapUtil.toList(TEST_PATTERN_1, TEST_PATTERN_2), test.getExcludes());
        test.exclude(TEST_PATTERN_3);
        assertEquals(WrapUtil.toList(TEST_PATTERN_1, TEST_PATTERN_2, TEST_PATTERN_3), test.getExcludes());
    }

    @org.junit.Test public void testUnmanagedClasspath() {
        List<Object> list1 = WrapUtil.toList("a", new Object());
        assertSame(test, test.unmanagedClasspath(list1.toArray(new Object[list1.size()])));
        assertEquals(list1, test.getUnmanagedClasspath());
        List list2 = WrapUtil.toList(WrapUtil.toList("b", "c"));
        test.unmanagedClasspath(list2.toArray(new Object[list2.size()]));
        assertEquals(GUtil.addLists(list1, GUtil.flatten(list2)), test.getUnmanagedClasspath());
    }

    private void setExistingDirsFilter() {
        context.checking(new Expectations() {{
            allowing(existentDirsFilterMock).checkExistenceAndThrowStopActionIfNot(TEST_TEST_CLASSES_DIR);
        }});
        test.existingDirsFilter = existentDirsFilterMock;
    }
}
