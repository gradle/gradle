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

package org.gradle.api.tasks.testing;

import org.gradle.api.GradleException;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.ConventionTask;
import org.gradle.api.tasks.AbstractConventionTaskTest;
import org.gradle.api.tasks.util.JavaForkOptions;
import org.gradle.api.testing.TestClassProcessor;
import org.gradle.api.testing.TestClassProcessorFactory;
import org.gradle.api.testing.detection.TestClassScanner;
import org.gradle.api.testing.detection.TestClassScannerFactory;
import org.gradle.api.testing.execution.RestartEveryNTestClassProcessor;
import org.gradle.api.testing.execution.fork.ForkingTestClassProcessor;
import org.gradle.api.testing.fabric.TestFramework;
import org.gradle.api.testing.fabric.TestFrameworkInstance;
import org.gradle.util.GFileUtils;
import org.gradle.util.HelperUtil;
import org.gradle.util.TestClosure;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.jmock.Expectations;
import org.jmock.api.Action;
import org.jmock.api.Invocation;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.Before;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.Collections;

import static org.gradle.util.WrapUtil.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;


/**
 * @author Hans Dockter
 */
@RunWith(JMock.class)
public class AntTestTest extends AbstractConventionTaskTest {
    static final String TEST_PATTERN_1 = "pattern1";
    static final String TEST_PATTERN_2 = "pattern2";
    static final String TEST_PATTERN_3 = "pattern3";

    private File classesDir;
    private File resultsDir;
    private File reportDir;

    private JUnit4Mockery context = new JUnit4Mockery() {{
        setImposteriser(ClassImposteriser.INSTANCE);
    }};

    TestFramework testFrameworkMock = context.mock(TestFramework.class);
    TestFrameworkInstance testFrameworkInstanceMock = context.mock(TestFrameworkInstance.class);
    TestClassScannerFactory testClassScannerFactoryMock = context.mock(TestClassScannerFactory.class);
    TestClassScanner testClassScannerMock = context.mock(TestClassScanner.class);
    TestClassProcessorFactory testProcessorFactoryMock = context.mock(TestClassProcessorFactory.class);

    private FileCollection classpathMock = context.mock(FileCollection.class);
    private AntTest test;

    @Before public void setUp() {
        super.setUp();

        File rootDir = getProject().getProjectDir();
        classesDir = new File(rootDir, "testClassesDir");
        File classfile = new File(classesDir, "FileTest.class");
        GFileUtils.touch(classfile);
        resultsDir = new File(rootDir, "resultDir");
        reportDir = new File(rootDir, "report/tests");

        test = createTask(AntTest.class);
    }

    public ConventionTask getTask() {
        return test;
    }

    @org.junit.Test public void testInit() {
        assertNotNull(test.getTestFramework());
        assertNull(test.getTestClassesDir());
        assertNull(test.getClasspath());
        assertNull(test.getTestResultsDir());
        assertNull(test.getTestReportDir());
        assertEquals(toLinkedSet(), test.getIncludes());
        assertEquals(toLinkedSet(), test.getExcludes());
        assertFalse(test.isIgnoreFailures());
    }

    @org.junit.Test
    public void testExecute() {
        configureTask();
        expectTestsExecuted();

        test.executeTests();
    }

    @org.junit.Test
    public void testExecuteWithMaxClassesPerForkLimit() {
        configureTask();

        test.setForkEvery(12L);
        expectTestsExecuted(instanceOf(RestartEveryNTestClassProcessor.class));

        test.executeTests();
    }

    @org.junit.Test
    public void testExecuteWithTestFailuresAndStopAtFailures() {
        configureTask();
        expectTestsFail();
        try {
            test.executeTests();
            fail();
        } catch (GradleException e) {
            assertThat(e.getMessage(), startsWith("There were failing tests. See the report at"));
        }
    }

    @org.junit.Test public void testExecuteWithTestFailuresAndContinueWithFailures() {
        configureTask();
        test.setIgnoreFailures(true);
        expectTestsFail();
        test.executeTests();
    }

    @org.junit.Test public void testListenerMethodsDelegateToListenerBroadcast() {
        final TestListener listener = context.mock(TestListener.class);
        final TestClosure closure = context.mock(TestClosure.class);
        test.addTestListener(listener);
        test.beforeTest(HelperUtil.toClosure(closure));
        test.afterTest(HelperUtil.toClosure(closure));

        final Test testInfo = context.mock(Test.class);
        final TestResult result = context.mock(TestResult.class);
        context.checking(new Expectations() {{
            one(listener).beforeTest(testInfo);
            one(closure).call(testInfo);
        }});

        test.getTestListenerBroadcaster().getSource().beforeTest(testInfo);

        context.checking(new Expectations() {{
            one(listener).afterTest(testInfo, result);
            one(closure).call(testInfo);
        }});

        test.getTestListenerBroadcaster().getSource().afterTest(testInfo, result);
    }

    @org.junit.Test public void testIncludes() {
        assertSame(test, test.include(TEST_PATTERN_1, TEST_PATTERN_2));
        assertEquals(toLinkedSet(TEST_PATTERN_1, TEST_PATTERN_2), test.getIncludes());
        test.include(TEST_PATTERN_3);
        assertEquals(toLinkedSet(TEST_PATTERN_1, TEST_PATTERN_2, TEST_PATTERN_3), test.getIncludes());
    }

    @org.junit.Test public void testExcludes() {
        assertSame(test, test.exclude(TEST_PATTERN_1, TEST_PATTERN_2));
        assertEquals(toLinkedSet(TEST_PATTERN_1, TEST_PATTERN_2), test.getExcludes());
        test.exclude(TEST_PATTERN_3);
        assertEquals(toLinkedSet(TEST_PATTERN_1, TEST_PATTERN_2, TEST_PATTERN_3), test.getExcludes());
    }

    private void expectTestsExecuted() {
        expectOptionsBuilt();
        expectTestsExecuted(forkingProcessor());
    }

    private void expectOptionsBuilt() {
        context.checking(new Expectations() {{
            AbstractTestFrameworkOptions testOptions = context.mock(AbstractTestFrameworkOptions.class);
            allowing(testFrameworkInstanceMock).getOptions();
            will(returnValue(testOptions));
            one(testOptions).createForkOptions();
            will(returnValue(context.mock(JavaForkOptions.class)));
        }});
    }

    private void expectTestsExecuted(final Matcher<TestClassProcessor> testClassProcessorMatcher) {
        context.checking(new Expectations() {{
            allowing(classpathMock).iterator();
            will(returnIterator(new File("classpath.jar")));

            one(testFrameworkInstanceMock).getProcessorFactory();
            will(returnValue(testProcessorFactoryMock));

            one(testClassScannerFactoryMock).createTestClassScanner(with(sameInstance(test)), with(testClassProcessorMatcher));
            will(returnValue(testClassScannerMock));

            one(testClassScannerMock).run();
            
            one(testFrameworkInstanceMock).report();
        }});
    }

    private void expectTestsFail() {
        expectOptionsBuilt();
        
        context.checking(new Expectations() {{
            allowing(classpathMock).iterator();
            will(returnIterator(new File("classpath.jar")));

            one(testFrameworkInstanceMock).getProcessorFactory();
            will(returnValue(testProcessorFactoryMock));

            one(testClassScannerFactoryMock).createTestClassScanner(with(sameInstance(test)), with(notNullValue(TestClassProcessor.class)));
            will(returnValue(testClassScannerMock));

            final TestResult result = context.mock(TestResult.class);
            allowing(result).getResultType();
            will(returnValue(TestResult.ResultType.FAILURE));

            final Test test = context.mock(Test.class);
            allowing(test).getName();
            will(returnValue("test"));

            one(testClassScannerMock).run();
            will(new Action() {
                public void describeTo(Description description) {
                    description.appendText("fail tests");
                }

                public Object invoke(Invocation invocation) throws Throwable {
                    AntTestTest.this.test.getTestListenerBroadcaster().getSource().afterTest(test, result);
                    return null;
                }
            });

            one(testFrameworkInstanceMock).report();
        }});
    }

    private void configureTask() {
        context.checking(new Expectations(){{
            one(testFrameworkMock).getInstance(test);
            will(returnValue(testFrameworkInstanceMock));
            one(testFrameworkInstanceMock).initialize();
        }});

        test.useTestFramework(testFrameworkMock);

        test.setScanForTestClasses(false);
        test.setTestClassScannerFactory(testClassScannerFactoryMock);
        test.setTestClassesDir(classesDir);
        test.setTestResultsDir(resultsDir);
        test.setTestReportDir(reportDir);
        test.setClasspath(classpathMock);
        test.setTestSrcDirs(Collections.<File>emptyList());
    }

    private Matcher<TestClassProcessor> forkingProcessor() {
        return new BaseMatcher<TestClassProcessor>() {
            public boolean matches(Object o) {
                if (!(o instanceof ForkingTestClassProcessor)) {
                    return false;
                }
                ForkingTestClassProcessor processor = (ForkingTestClassProcessor) o;
                return processor.getProcessorFactory() == testProcessorFactoryMock;
            }

            public void describeTo(Description description) {
                description.appendText("a forking test processor that uses ").appendValue(testProcessorFactoryMock);
            }
        };
    }

    private Matcher<TestClassProcessor> instanceOf(final Class<? extends TestClassProcessor> type) {
        return new BaseMatcher<TestClassProcessor>() {
            public boolean matches(Object object) {
                return type.isInstance(object);
            }

            public void describeTo(Description description) {
                description.appendText("instance of ").appendValue(type.getName());
            }
        };
    }
}
