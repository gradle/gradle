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
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.ConventionTask;
import org.gradle.api.tasks.AbstractConventionTaskTest;
import org.gradle.api.testing.TestFramework;
import org.gradle.util.GFileUtils;
import org.gradle.util.GUtil;
import org.gradle.util.WrapUtil;
import org.hamcrest.Matchers;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.*;


/**
 * @author Hans Dockter
 */
@RunWith(JMock.class)
public class TestTest extends AbstractConventionTaskTest {
    static final String TEST_PATTERN_1 = "pattern1";
    static final String TEST_PATTERN_2 = "pattern2";
    static final String TEST_PATTERN_3 = "pattern3";

    private File classesDir;
    private File resultsDir;
    private File reportDir;

    private static final Set TEST_DEPENDENCY_MANAGER_CLASSPATH = WrapUtil.toSet(new File("jar1"));
    private List convertedClasspath;

    static final Set<String> okTestClassNames = new HashSet<String>(Arrays.asList("test.HumanTest", "test.CarTest"));

    private JUnit4Mockery context = new JUnit4Mockery() {{
        setImposteriser(ClassImposteriser.INSTANCE);
    }};

    TestFramework testFrameworkMock = context.mock(TestFramework.class);

    private FileCollection configurationMock = context.mock(FileCollection.class);

    private Test test;
    private File classfile;

    @Before public void setUp() {
        super.setUp();
        test = createTask(Test.class);
        context.checking(new Expectations(){{
            one(testFrameworkMock).initialize(getProject(), test);
        }});
        test.useTestFramework(testFrameworkMock);

        File rootDir = getProject().getProjectDir();
        classesDir = new File(rootDir, "testClassesDir");
        classfile = new File(classesDir, "file.class");
        GFileUtils.touch(classfile);
        resultsDir = new File(rootDir, "resultDir");
        reportDir = new File(rootDir, "report/tests");
        convertedClasspath = GUtil.addLists(WrapUtil.toList(classesDir), TEST_DEPENDENCY_MANAGER_CLASSPATH);
    }

    public ConventionTask getTask() {
        return test;
    }

    @org.junit.Test public void testInit() {
        assertNotNull(test.getTestFramework());
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
        context.checking(new Expectations() {{
            one(testFrameworkMock).prepare(getProject(), test);
            one(testFrameworkMock).isTestClass(classfile);
            one(testFrameworkMock).getTestClassNames(); will(returnValue(okTestClassNames));
            one(testFrameworkMock).execute(getProject(), test, okTestClassNames, new ArrayList<String>());
            one(testFrameworkMock).report(getProject(), test);
        }});

        test.execute();
    }

    @org.junit.Test
    public void testExecuteWithoutReporting() {
        setUpMocks(test);
        test.setTestReport(false);
        context.checking(new Expectations() {{
            one(testFrameworkMock).prepare(getProject(), test);
            one(testFrameworkMock).isTestClass(classfile);
            one(testFrameworkMock).getTestClassNames();will(returnValue(okTestClassNames));
            one(testFrameworkMock).execute(getProject(), test, okTestClassNames, new ArrayList<String>());
        }});

        test.execute();
    }

    @org.junit.Test(expected = GradleException.class)
    public void testExecuteWithTestFailuresAndStopAtFailures() {
        setUpMocks(test);
        context.checking(new Expectations() {{
            one(testFrameworkMock).prepare(getProject(), test);
            one(testFrameworkMock).isTestClass(classfile);
            one(testFrameworkMock).getTestClassNames();will(returnValue(okTestClassNames));
            one(testFrameworkMock).execute(getProject(), test, okTestClassNames, new ArrayList<String>());
        }});
        test.execute();
    }

    @org.junit.Test public void testExecuteWithTestFailuresAndContinueWithFailures() {
        setUpMocks(test);
        test.setStopAtFailuresOrErrors(false);
        context.checking(new Expectations() {{
            one(testFrameworkMock).prepare(getProject(), test);
            one(testFrameworkMock).isTestClass(classfile);
            one(testFrameworkMock).getTestClassNames();will(returnValue(okTestClassNames));
            one(testFrameworkMock).execute(getProject(), test, okTestClassNames, new ArrayList<String>());
            one(testFrameworkMock).report(getProject(), test);
        }});
        test.execute();
    }

    @org.junit.Test
    public void testGetClasspath() {
        setUpMocks(test);
        assertEquals(convertedClasspath, test.getClasspath());
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

    private void setUpMocks(final Test test) {
        test.setTestClassesDir(classesDir);
        test.setTestResultsDir(resultsDir);
        test.setTestReportDir(reportDir);
        test.setConfiguration(configurationMock);
        test.setTestSrcDirs(Collections.<File>emptyList());

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
}
