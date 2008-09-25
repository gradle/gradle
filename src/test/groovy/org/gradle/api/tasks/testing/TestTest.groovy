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

package org.gradle.api.tasks.testing

import groovy.mock.interceptor.MockFor
import org.gradle.api.DependencyManager
import org.gradle.api.GradleException
import org.gradle.api.InvalidUserDataException
import org.gradle.api.internal.AbstractTask
import org.gradle.api.tasks.AbstractConventionTaskTest
import org.gradle.api.tasks.AbstractTaskTest
import org.gradle.api.tasks.StopActionException
import org.gradle.api.tasks.compile.ClasspathConverter
import org.gradle.api.tasks.util.ExistingDirsFilter
import org.hamcrest.Matchers
import static org.junit.Assert.*
import org.junit.Before



/**
 * @author Hans Dockter
 */
class TestTest extends AbstractConventionTaskTest {
    static final String TEST_PATTERN_1 = 'pattern1'
    static final String TEST_PATTERN_2 = 'pattern2'
    static final String TEST_PATTERN_3 = 'pattern3'

    static final File TEST_TEST_CLASSES_DIR = '/testClassesDir' as File
    static final File TEST_TEST_RESULTS_DIR = '/resultDir' as File
    static final File TEST_TEST_REPORT_DIR = '/report/tests' as File
    static final File TEST_ROOT_DIR = '/ROOTDir' as File

    static final List TEST_DEPENDENCY_MANAGER_CLASSPATH = ['jar1' as File]
    static final List TEST_CONVERTED_UNMANAGED_CLASSPATH = ['jar2' as File]
    static final List TEST_UNMANAGED_CLASSPATH = ['jar2']

    Test test

    MockFor antJUnitMocker

    MockFor dependencyManagerMocker

    @Before public void setUp() {
        super.setUp()
        test = new Test(project, AbstractTaskTest.TEST_TASK_NAME)
        test.project.projectDir = TEST_ROOT_DIR
        antJUnitMocker = new MockFor(AntJunit)
        dependencyManagerMocker = new MockFor(DependencyManager)
    }

    AbstractTask getTask() {test}

    @org.junit.Test public void testCompile() {
        assertNotNull(test.antJunit)
        assertNotNull(test.options)
        assertNotNull(test.existingDirsFilter)
        assertNotNull(test.classpathConverter)
        assertNull(test.testClassesDir)
        assertNull(test.dependencyManager)
        assertNull(test.testResultsDir)
        assertNull(test.testReportDir)
        assertEquals([], test.includes)
        assertEquals([], test.excludes)
        assert test.stopAtFailuresOrErrors
    }

    @org.junit.Test public void testExecute() {
        setUpMocks(test)

        setExistingDirsFilter()

        antJUnitMocker.demand.execute(1..1) {File testClassesDir, List classpath, File testResultsDir,
                                             File testReportDir, List includes, List excludes,
                                             JunitOptions junitOptions, AntBuilder ant ->
            assertEquals(TEST_TEST_CLASSES_DIR, testClassesDir)
            assertEquals([TEST_TEST_CLASSES_DIR] + TEST_CONVERTED_UNMANAGED_CLASSPATH + TEST_DEPENDENCY_MANAGER_CLASSPATH, classpath)
            assertEquals(TEST_TEST_RESULTS_DIR, testResultsDir)
            assertEquals(TEST_TEST_REPORT_DIR, testReportDir)
            assertEquals(test.options, junitOptions)
            assert includes.is(test.includes)
            assert excludes.is(test.excludes)
            assert ant.is(project.ant)
        }

        antJUnitMocker.use(test.antJunit) {
            test.execute()
        }
    }

    @org.junit.Test public void testExecuteWithTestFailuresAndStopAtFailures() {
        setUpMocks(test)
        setExistingDirsFilter()
        antJUnitMocker.demand.execute(1..1) {File testClassesDir, List classpath, File testResultsDir,
                                             File testReportDir, List includes, List excludes,
                                             JunitOptions junitOptions, AntBuilder ant ->
            ant.project.setProperty(AntJunit.FAILURES_OR_ERRORS_PROPERTY, 'somevalue')
        }
        antJUnitMocker.use(test.antJunit) {
            shouldFailWithCause(GradleException, "There were failing tests. See the report at ${TEST_TEST_REPORT_DIR}.") {
                test.execute()
            }
        }
    }

    @org.junit.Test public void testExecuteWithTestFailuresAndContinueWithFailures() {
        setUpMocks(test)
        test.stopAtFailuresOrErrors = false
        setExistingDirsFilter()
        antJUnitMocker.demand.execute(1..1) {File testClassesDir, List classpath, File testResultsDir,
                                             File testReportDir, List includes, List excludes,
                                             JunitOptions junitOptions, AntBuilder ant ->
            ant.project.setProperty(AntJunit.FAILURES_OR_ERRORS_PROPERTY, 'somevalue')
        }

        antJUnitMocker.use(test.antJunit) {
            test.execute()
        }
    }

    @org.junit.Test public void testExecuteWithUnspecifiedCompiledTestsDir() {
        setUpMocks(test)
        test.testClassesDir = null
        shouldFailWithCause(InvalidUserDataException, "The testClassesDir property is not set, testing can't be triggered!") {
            test.execute()
        }
    }

    @org.junit.Test public void testExecuteWithUnspecifiedTestResultsDir() {
        setUpMocks(test)
        test.testResultsDir = null
        shouldFailWithCause(InvalidUserDataException, "The testResultsDir property is not set, testing can't be triggered!") {
            test.execute()
        }
    }

    @org.junit.Test public void testExecuteWithNonExistingCompiledTestsDir() {
        setUpMocks(test)
        test.unmanagedClasspath = null

        test.existingDirsFilter = [checkExistenceAndThrowStopActionIfNot: {File dir ->
            assertEquals(TEST_TEST_CLASSES_DIR, dir)
            throw new StopActionException()
        }] as ExistingDirsFilter

        antJUnitMocker.demand.execute(0..0) {File compiledTestClassesDir, List classpath, File testResultsDir,
                                             File testReportDir, List includes, List excludes,
                                             JunitOptions junitOptions, List jvmArgs, Map systemProperties,
                                             AntBuilder ant ->
        }

        antJUnitMocker.use(test.antJunit) {
            test.execute()
        }
    }

    private void setUpMocks(Test test) {
        test.testClassesDir = TEST_TEST_CLASSES_DIR
        test.testResultsDir = TEST_TEST_RESULTS_DIR
        test.testReportDir = TEST_TEST_REPORT_DIR
        test.unmanagedClasspath = TEST_UNMANAGED_CLASSPATH

        test.dependencyManager = [resolveTask: {String taskName ->
            assertEquals(test.name, taskName)
            TEST_DEPENDENCY_MANAGER_CLASSPATH
        }] as DependencyManager

        test.classpathConverter = [createFileClasspath: {File baseDir, List pathElements ->
            assertEquals(TEST_ROOT_DIR, baseDir)
            assertEquals([TEST_TEST_CLASSES_DIR] + TEST_UNMANAGED_CLASSPATH, pathElements as List)
            [TEST_TEST_CLASSES_DIR] + TEST_CONVERTED_UNMANAGED_CLASSPATH
        }] as ClasspathConverter
    }

    @org.junit.Test public void testIncludes() {
        checkIncludesExcludes('include')
    }

    @org.junit.Test public void testExcludes() {
        checkIncludesExcludes('exclude')
    }

    void checkIncludesExcludes(String name) {
        assert test."$name"(TEST_PATTERN_1, TEST_PATTERN_2).is(test)
        assertEquals([TEST_PATTERN_1, TEST_PATTERN_2], test."${name}s")
        test."$name"(TEST_PATTERN_3)
        assertEquals([TEST_PATTERN_1, TEST_PATTERN_2, TEST_PATTERN_3], test."${name}s")
    }

    @org.junit.Test public void testUnmanagedClasspath() {
        List list1 = ['a', new Object()]
        assert test.unmanagedClasspath(list1 as Object[]).is(test)
        assertEquals(list1, test.unmanagedClasspath)
        List list2 = [['b', 'c']]
        test.unmanagedClasspath(list2)
        assertEquals(list1 + list2.flatten(), test.unmanagedClasspath)
    }

    private setExistingDirsFilter() {
        test.existingDirsFilter = [checkExistenceAndThrowStopActionIfNot: {File dir ->
            assertEquals(TEST_TEST_CLASSES_DIR, dir)
        }] as ExistingDirsFilter
    }

    private shouldFailWithCause(Class exceptionClass, String message, Closure closure) {
        try {
            closure.call()
            fail()
        } catch (Throwable t) {
            assertThat(exceptionClass.isInstance(t.getCause()), Matchers.equalTo(true))
            assertThat(t.getCause().getMessage(), Matchers.containsString(message))
        }
    }
}
