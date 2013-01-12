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
import org.gradle.api.file.FileTree;
import org.gradle.api.internal.ConventionTask;
import org.gradle.api.internal.file.CompositeFileTree;
import org.gradle.api.internal.file.collections.DefaultFileCollectionResolveContext;
import org.gradle.api.internal.file.collections.DirectoryFileTree;
import org.gradle.api.internal.file.collections.FileTreeAdapter;
import org.gradle.api.internal.file.collections.SimpleFileCollection;
import org.gradle.api.internal.tasks.testing.TestDescriptorInternal;
import org.gradle.api.internal.tasks.testing.TestFramework;
import org.gradle.api.internal.tasks.testing.WorkerTestClassProcessorFactory;
import org.gradle.api.internal.tasks.testing.detection.TestExecuter;
import org.gradle.api.internal.tasks.testing.detection.TestFrameworkDetector;
import org.gradle.api.internal.tasks.testing.junit.JUnitTestFramework;
import org.gradle.api.internal.tasks.testing.junit.report.TestReporter;
import org.gradle.api.internal.tasks.testing.junit.result.TestResultsProvider;
import org.gradle.api.internal.tasks.testing.results.TestListenerAdapter;
import org.gradle.api.tasks.AbstractConventionTaskTest;
import org.gradle.process.internal.WorkerProcessBuilder;
import org.gradle.util.GFileUtils;
import org.gradle.util.HelperUtil;
import org.gradle.util.TestClosure;
import org.hamcrest.Description;
import org.jmock.Expectations;
import org.jmock.api.Action;
import org.jmock.api.Invocation;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.Before;
import org.junit.runner.RunWith;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.gradle.util.Matchers.isEmpty;
import static org.gradle.util.WrapUtil.toLinkedSet;
import static org.gradle.util.WrapUtil.toSet;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;


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

    private JUnit4Mockery context = new JUnit4Mockery() {{
        setImposteriser(ClassImposteriser.INSTANCE);
    }};

    TestFramework testFrameworkMock = context.mock(TestFramework.class);
    TestExecuter testExecuterMock = context.mock(TestExecuter.class);
    private FileCollection classpathMock = new SimpleFileCollection(new File("classpath"));
    private Test test;

    @Before
    public void setUp() {
        File rootDir = getProject().getProjectDir();
        classesDir = new File(rootDir, "testClassesDir");
        File classfile = new File(classesDir, "FileTest.class");
        GFileUtils.touch(classfile);
        resultsDir = new File(rootDir, "resultDir");
        reportDir = new File(rootDir, "report/tests");

        test = createTask(Test.class);
    }

    public ConventionTask getTask() {
        return test;
    }

    @org.junit.Test
    public void testInit() {
        assertThat(test.getTestFramework(), instanceOf(JUnitTestFramework.class));
        assertNull(test.getTestClassesDir());
        assertNull(test.getClasspath());
        assertNull(test.getTestResultsDir());
        assertNull(test.getTestReportDir());
        assertThat(test.getIncludes(), isEmpty());
        assertThat(test.getExcludes(), isEmpty());
        assertFalse(test.getIgnoreFailures());
    }

    @org.junit.Test
    public void testExecute() {
        configureTask();
        expectTestsExecuted();

        test.executeTests();
    }

    @org.junit.Test
    public void generatesReport() {
        configureTask();
        expectTestsExecuted();

        final TestReporter testReporter = context.mock(TestReporter.class);
        test.setTestReporter(testReporter);
        context.checking(new Expectations() {{
            one(testReporter).generateReport(with(any(TestResultsProvider.class)), with(equal(reportDir)));
        }});

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

    @org.junit.Test
    public void testExecuteWithTestFailuresAndIgnoreFailures() {
        configureTask();
        test.setIgnoreFailures(true);
        expectTestsFail();
        test.executeTests();
    }

    @org.junit.Test
    public void testScansForTestClassesInTheTestClassesDir() {
        configureTask();
        test.include("include");
        test.exclude("exclude");

        FileTree classFiles = test.getCandidateClassFiles();
        assertIsDirectoryTree(classFiles, toSet("include"), toSet("exclude"));
    }

    @org.junit.Test
    public void testSetsTestFrameworkToNullAfterExecution() {
        configureTask();
        // using a jmock generated mock for testFramework does not work here as it is referenced
        // by jmock holds some references.

        test.useTestFramework(new TestFramework() {

            public TestFrameworkDetector getDetector() {
                return null;
            }

            public TestFrameworkOptions getOptions() {
                return null;
            }

            public WorkerTestClassProcessorFactory getProcessorFactory() {
                return null;
            }

            public org.gradle.api.Action<WorkerProcessBuilder> getWorkerConfigurationAction() {
                return null;
            }
        });
        context.checking(new Expectations() {{
            one(testExecuterMock).execute(with(sameInstance(test)), with(notNullValue(TestListenerAdapter.class)));
        }});

        WeakReference<TestFramework> weakRef = new WeakReference<TestFramework>(test.getTestFramework());
        test.executeTests();

        System.gc(); //explicit gc should normally be avoided, but necessary here.

        assertNull(weakRef.get());

    }

    @org.junit.Test
    public void testDisablesParallelExecutionWhenInDebugMode() {
        configureTask();
        test.setDebug(true);
        test.setMaxParallelForks(4);

        assertEquals(1, test.getMaxParallelForks());
    }

    private void assertIsDirectoryTree(FileTree classFiles, Set<String> includes, Set<String> excludes) {
        assertThat(classFiles, instanceOf(CompositeFileTree.class));
        CompositeFileTree files = (CompositeFileTree) classFiles;
        DefaultFileCollectionResolveContext context = new DefaultFileCollectionResolveContext();
        files.resolve(context);
        List<? extends FileTree> contents = context.resolveAsFileTrees();
        FileTreeAdapter adapter = (FileTreeAdapter) contents.get(0);
        assertThat(adapter.getTree(), instanceOf(DirectoryFileTree.class));
        DirectoryFileTree directoryFileTree = (DirectoryFileTree) adapter.getTree();
        assertThat(directoryFileTree.getDir(), equalTo(classesDir));
        assertThat(directoryFileTree.getPatterns().getIncludes(), equalTo(includes));
        assertThat(directoryFileTree.getPatterns().getExcludes(), equalTo(excludes));
    }

    @org.junit.Test
    public void notifiesListenerOfEvents() {
        final TestListener listener = context.mock(TestListener.class);
        test.addTestListener(listener);

        final TestDescriptor testDescriptor = context.mock(TestDescriptor.class);

        context.checking(new Expectations() {{
            one(listener).beforeSuite(testDescriptor);
        }});

        test.getTestListenerBroadcaster().getSource().beforeSuite(testDescriptor);
    }

    @org.junit.Test
    public void notifiesListenerBeforeSuite() {
        final TestClosure closure = context.mock(TestClosure.class);
        test.beforeSuite(HelperUtil.toClosure(closure));

        final TestDescriptor testDescriptor = context.mock(TestDescriptor.class);

        context.checking(new Expectations() {{
            one(closure).call(testDescriptor);
        }});

        test.getTestListenerBroadcaster().getSource().beforeSuite(testDescriptor);
    }

    @org.junit.Test
    public void notifiesListenerAfterSuite() {
        final TestClosure closure = context.mock(TestClosure.class);
        test.afterSuite(HelperUtil.toClosure(closure));

        final TestDescriptor testDescriptor = context.mock(TestDescriptor.class);
        final TestResult result = context.mock(TestResult.class);

        context.checking(new Expectations() {{
            one(closure).call(testDescriptor);
        }});

        test.getTestListenerBroadcaster().getSource().afterSuite(testDescriptor, result);
    }

    @org.junit.Test
    public void notifiesListenerBeforeTest() {
        final TestClosure closure = context.mock(TestClosure.class);
        test.beforeTest(HelperUtil.toClosure(closure));

        final TestDescriptor testDescriptor = context.mock(TestDescriptor.class);

        context.checking(new Expectations() {{
            one(closure).call(testDescriptor);
        }});

        test.getTestListenerBroadcaster().getSource().beforeTest(testDescriptor);
    }

    @org.junit.Test
    public void notifiesListenerAfterTest() {
        final TestClosure closure = context.mock(TestClosure.class);
        test.afterTest(HelperUtil.toClosure(closure));

        final TestDescriptor testDescriptor = context.mock(TestDescriptor.class);
        final TestResult result = context.mock(TestResult.class);

        context.checking(new Expectations() {{
            one(closure).call(testDescriptor);
        }});

        test.getTestListenerBroadcaster().getSource().afterTest(testDescriptor, result);
    }

    @org.junit.Test
    public void testIncludes() {
        assertSame(test, test.include(TEST_PATTERN_1, TEST_PATTERN_2));
        assertEquals(toLinkedSet(TEST_PATTERN_1, TEST_PATTERN_2), test.getIncludes());
        test.include(TEST_PATTERN_3);
        assertEquals(toLinkedSet(TEST_PATTERN_1, TEST_PATTERN_2, TEST_PATTERN_3), test.getIncludes());
    }

    @org.junit.Test
    public void testExcludes() {
        assertSame(test, test.exclude(TEST_PATTERN_1, TEST_PATTERN_2));
        assertEquals(toLinkedSet(TEST_PATTERN_1, TEST_PATTERN_2), test.getExcludes());
        test.exclude(TEST_PATTERN_3);
        assertEquals(toLinkedSet(TEST_PATTERN_1, TEST_PATTERN_2, TEST_PATTERN_3), test.getExcludes());
    }

    private void expectOptionsBuilt() {
        context.checking(new Expectations() {{
            TestFrameworkOptions testOptions = context.mock(TestFrameworkOptions.class);
            allowing(testFrameworkMock).getOptions();
            will(returnValue(testOptions));
        }});
    }

    private void expectTestsExecuted() {
        expectOptionsBuilt();
        context.checking(new Expectations() {{
            one(testExecuterMock).execute(with(sameInstance(test)), with(notNullValue(TestListenerAdapter.class)));
        }});
    }

    private void expectTestsFail() {
        expectOptionsBuilt();

        context.checking(new Expectations() {{
            final TestResult result = context.mock(TestResult.class);
            allowing(result).getResultType();
            will(returnValue(TestResult.ResultType.FAILURE));
            ignoring(result);

            final TestDescriptorInternal testDescriptor = context.mock(TestDescriptorInternal.class);
            allowing(testDescriptor).getName();
            will(returnValue("test"));
            allowing(testDescriptor).getParent();
            will(returnValue(null));
            allowing(testDescriptor).getId();
            will(returnValue(0));

            ignoring(testDescriptor);

            one(testExecuterMock).execute(with(sameInstance(test)), with(notNullValue(TestListenerAdapter.class)));
            will(new Action() {
                public void describeTo(Description description) {
                    description.appendText("fail tests");
                }

                public Object invoke(Invocation invocation) throws Throwable {
                    TestTest.this.test.getTestListenerBroadcaster().getSource().beforeSuite(testDescriptor);
                    TestTest.this.test.getTestListenerBroadcaster().getSource().afterSuite(testDescriptor, result);
                    return null;
                }
            });
        }});
    }

    private void configureTask() {
        test.useTestFramework(testFrameworkMock);
        test.setTestExecuter(testExecuterMock);

        test.setTestClassesDir(classesDir);
        test.setTestResultsDir(resultsDir);
        test.setTestReportDir(reportDir);
        test.setClasspath(classpathMock);
        test.setTestSrcDirs(Collections.<File>emptyList());
    }
}
