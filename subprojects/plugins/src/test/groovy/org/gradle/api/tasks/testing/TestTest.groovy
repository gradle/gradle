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

package org.gradle.api.tasks.testing

import org.gradle.api.Action
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileTree
import org.gradle.api.internal.ConventionTask
import org.gradle.api.internal.file.CompositeFileTree
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.file.collections.DefaultFileCollectionResolveContext
import org.gradle.api.internal.file.collections.DirectoryFileTree
import org.gradle.api.internal.file.collections.FileTreeAdapter
import org.gradle.api.internal.file.collections.SimpleFileCollection
import org.gradle.api.internal.tasks.testing.TestFramework
import org.gradle.api.internal.tasks.testing.TestResultProcessor
import org.gradle.api.internal.tasks.testing.WorkerTestClassProcessorFactory
import org.gradle.api.internal.tasks.testing.detection.TestExecuter
import org.gradle.api.internal.tasks.testing.detection.TestFrameworkDetector
import org.gradle.api.internal.tasks.testing.junit.JUnitTestFramework
import org.gradle.api.internal.tasks.testing.junit.report.TestReporter
import org.gradle.api.internal.tasks.testing.junit.result.TestResultsProvider
import org.gradle.api.tasks.AbstractConventionTaskTest
import org.gradle.internal.work.WorkerLeaseRegistry
import org.gradle.process.internal.worker.WorkerProcessBuilder
import org.gradle.util.GFileUtils

import java.lang.ref.WeakReference

import static org.gradle.util.WrapUtil.toLinkedSet
import static org.gradle.util.WrapUtil.toSet

class TestTest extends AbstractConventionTaskTest {
    static final String TEST_PATTERN_1 = "pattern1"
    static final String TEST_PATTERN_2 = "pattern2"
    static final String TEST_PATTERN_3 = "pattern3"

    private File classesDir
    private File resultsDir
    private File binResultsDir
    private File reportDir

    def testExecuterMock = Mock(TestExecuter)
    def testFrameworkMock = Mock(TestFramework)

    private WorkerLeaseRegistry.WorkerLeaseCompletion completion
    private FileCollection classpathMock = new SimpleFileCollection(new File("classpath"))
    private Test test

    def setup() {
        classesDir = temporaryFolder.createDir("classes")
        File classfile = new File(classesDir, "FileTest.class")
        GFileUtils.touch(classfile)
        resultsDir = temporaryFolder.createDir("testResults")
        binResultsDir = temporaryFolder.createDir("binResults")
        reportDir = temporaryFolder.createDir("report")
        completion = project.services.get(WorkerLeaseRegistry).getWorkerLease().start()

        test = createTask(Test.class)
    }

    def cleanup() {
        completion.leaseFinish()
    }

    public ConventionTask getTask() {
        return test
    }

    def "test default settings"() {
        expect:
        test.getTestFramework() instanceof JUnitTestFramework
        test.getTestClassesDirs() == null
        test.getClasspath() == null
        test.getReports().getJunitXml().getDestination() == null
        test.getReports().getHtml().getDestination() == null
        test.getIncludes().isEmpty()
        test.getExcludes().isEmpty()
        !test.getIgnoreFailures()
    }

    def "test execute()"() {
        given:
        configureTask()

        when:
        test.executeTests()

        then:
        1 * testExecuterMock.execute(test, _ as TestResultProcessor)
    }

    def "generates report"() {
        given:
        configureTask()
        final testReporter = Mock(TestReporter)
        test.setTestReporter(testReporter)

        when:
        test.executeTests()

        then:
        1 * testReporter.generateReport(_ as TestResultsProvider, reportDir)
        1 * testExecuterMock.execute(test, _ as TestResultProcessor)
    }

    /* TODO(pepper): WTF?!? This test wasn't ever doing shit. Fuck this! */
    def "execute with test failures and ignore failures"() {
        given:
        configureTask()
        test.setIgnoreFailures(true)

        when:
        test.executeTests()

        then:
        1 * testExecuterMock.execute(test, _ as TestResultProcessor)
    }

    def "scans for test classes in the classes dir"() {
        given:
        configureTask()
        test.include("include")
        test.exclude("exclude")
        def classFiles = test.getCandidateClassFiles()

        expect:
        assertIsDirectoryTree(classFiles, toSet("include"), toSet("exclude"))
    }

    def "sets test framework to null after execution"() {
        given:
        configureTask()
        test.useTestFramework(new TestFramework() {

            public TestFrameworkDetector getDetector() {
                return null
            }

            public TestFrameworkOptions getOptions() {
                return null
            }

            public WorkerTestClassProcessorFactory getProcessorFactory() {
                return null
            }

            public Action<WorkerProcessBuilder> getWorkerConfigurationAction() {
                return null
            }
        })

        when:
        WeakReference<TestFramework> weakRef = new WeakReference<TestFramework>(test.getTestFramework())
        test.executeTests()

        then:
        1 * testExecuterMock.execute(test, _ as TestResultProcessor)

        when:
        System.gc() //explicit gc should normally be avoided, but necessary here.

        then:
        weakRef.get() == null
    }

    def "disables parallel execution when in debug mode"() {
        given:
        configureTask()

        when:
        test.setDebug(true)
        test.setMaxParallelForks(4)

        then:
        test.getMaxParallelForks() == 1
    }

    def "test includes"() {
        expect:
        test.is(test.include(TEST_PATTERN_1, TEST_PATTERN_2))
        test.getIncludes() == toLinkedSet(TEST_PATTERN_1, TEST_PATTERN_2)

        when:
        test.include(TEST_PATTERN_3)

        then:
        test.getIncludes() == toLinkedSet(TEST_PATTERN_1, TEST_PATTERN_2, TEST_PATTERN_3)
    }

    def "test excludes"() {
        expect:
        test.is(test.exclude(TEST_PATTERN_1, TEST_PATTERN_2))
        test.getExcludes() == toLinkedSet(TEST_PATTERN_1, TEST_PATTERN_2)

        when:
        test.exclude(TEST_PATTERN_3)

        then:
        test.getExcludes() == toLinkedSet(TEST_PATTERN_1, TEST_PATTERN_2, TEST_PATTERN_3)
    }

    private void assertIsDirectoryTree(FileTree classFiles, Set<String> includes, Set<String> excludes) {
        assert classFiles instanceof CompositeFileTree
        def files = (CompositeFileTree) classFiles
        def context = new DefaultFileCollectionResolveContext(TestFiles.resolver())
        files.visitContents(context)
        List<? extends FileTree> contents = context.resolveAsFileTrees()
        FileTreeAdapter adapter = (FileTreeAdapter) contents.get(0)
        assert adapter.getTree() instanceof DirectoryFileTree
        def directoryFileTree = (DirectoryFileTree) adapter.getTree()

        assert directoryFileTree.getDir() == classesDir
        assert directoryFileTree.getPatterns().getIncludes() == includes
        assert directoryFileTree.getPatterns().getExcludes() == excludes
    }

    private void configureTask() {
        test.useTestFramework(testFrameworkMock)
        test.setTestExecuter(testExecuterMock)

        test.setTestClassesDir(classesDir)
        test.getReports().getJunitXml().setDestination(resultsDir)
        test.setBinResultsDir(binResultsDir)
        test.getReports().getHtml().setDestination(reportDir)
        test.setClasspath(classpathMock)
    }
}
