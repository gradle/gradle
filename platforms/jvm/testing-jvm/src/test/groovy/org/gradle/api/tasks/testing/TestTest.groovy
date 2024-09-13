/*
 * Copyright 2020 the original author or authors.
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

import org.apache.commons.io.FileUtils
import org.gradle.api.InvalidUserDataException
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.ConventionTask
import org.gradle.api.internal.file.FileCollectionInternal
import org.gradle.api.internal.file.FileCollectionStructureVisitor
import org.gradle.api.internal.file.FileTreeInternal
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.file.collections.FileSystemMirroringFileTree
import org.gradle.api.internal.provider.AbstractProperty
import org.gradle.api.internal.tasks.testing.TestExecuter
import org.gradle.api.internal.tasks.testing.TestExecutionSpec
import org.gradle.api.internal.tasks.testing.TestFramework
import org.gradle.api.internal.tasks.testing.TestResultProcessor
import org.gradle.api.internal.tasks.testing.junit.JUnitTestFramework
import org.gradle.api.tasks.AbstractConventionTaskTest
import org.gradle.api.tasks.util.PatternSet
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.internal.jvm.inspection.JvmInstallationMetadata
import org.gradle.jvm.toolchain.internal.DefaultToolchainJavaLauncher
import org.gradle.jvm.toolchain.internal.JavaToolchain
import org.gradle.jvm.toolchain.internal.JavaToolchainInput
import org.gradle.process.CommandLineArgumentProvider
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.gradle.util.TestUtil

import static org.gradle.util.internal.WrapUtil.toLinkedSet
import static org.gradle.util.internal.WrapUtil.toSet

class TestTest extends AbstractConventionTaskTest {
    static final String TEST_PATTERN_1 = "pattern1"
    static final String TEST_PATTERN_2 = "pattern2"
    static final String TEST_PATTERN_3 = "pattern3"

    private TestFile classesDir
    private TestFile resultsDir
    private TestFile binResultsDir
    private TestFile reportDir

    def testExecuterMock = Mock(TestExecuter)
    def testFrameworkMock = Mock(TestFramework)

    private FileCollection classpathMock = TestFiles.fixed(new File("classpath"))
    private Test test

    def setup() {
        classesDir = temporaryFolder.createDir("classes")
        File classfile = new File(classesDir, "FileTest.class")
        FileUtils.touch(classfile)
        resultsDir = temporaryFolder.createDir("testResults")
        binResultsDir = temporaryFolder.createDir("binResults")
        reportDir = temporaryFolder.createDir("report")

        test = createTask(Test.class)
    }

    ConventionTask getTask() {
        return test
    }

    def "test default settings"() {
        expect:
        test.getTestFramework() instanceof JUnitTestFramework
        test.getTestClassesDirs() == null
        test.getClasspath().files.isEmpty()
        test.getReports().getJunitXml().outputLocation.getOrNull() == null
        test.getReports().getHtml().outputLocation.getOrNull() == null
        test.getIncludes().isEmpty()
        test.getExcludes().isEmpty()
        !test.getIgnoreFailures()
        !test.getFailFast()
    }

    def "test execute()"() {
        given:
        configureTask()

        when:
        test.executeTests()

        then:
        1 * testExecuterMock.execute(_ as TestExecutionSpec, _ as TestResultProcessor)
    }

    def "generates report"() {
        given:
        configureTask()

        when:
        test.executeTests()

        then:
        reportDir.assertContainsDescendants("index.html")
        1 * testExecuterMock.execute(_ as TestExecutionSpec, _ as TestResultProcessor)
    }

    def "execute with test failures and ignore failures"() {
        given:
        configureTask()
        test.setIgnoreFailures(true)

        when:
        test.executeTests()

        then:
        1 * testExecuterMock.execute(_ as TestExecutionSpec, _ as TestResultProcessor)
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

    def "--tests is combined with includes and excludes"() {
        given:
        test.include(TEST_PATTERN_1)
        test.exclude(TEST_PATTERN_1)

        when:
        test.setTestNameIncludePatterns([TEST_PATTERN_2])

        then:
        test.includes == [TEST_PATTERN_1] as Set
        test.excludes == [TEST_PATTERN_1] as Set
        test.filter.commandLineIncludePatterns == [TEST_PATTERN_2] as Set
    }

    def "--tests is combined with filter.includeTestsMatching"() {
        given:
        test.filter.includeTestsMatching(TEST_PATTERN_1)

        when:
        test.setTestNameIncludePatterns([TEST_PATTERN_2])

        then:
        test.includes.empty
        test.excludes.empty
        test.filter.includePatterns == [TEST_PATTERN_1] as Set
        test.filter.commandLineIncludePatterns == [TEST_PATTERN_2] as Set
    }

    def "--tests is combined with filter.includePatterns"() {
        given:
        test.filter.includePatterns = [TEST_PATTERN_1]

        when:
        test.setTestNameIncludePatterns([TEST_PATTERN_2])

        then:
        test.includes.empty
        test.excludes.empty
        test.filter.includePatterns == [TEST_PATTERN_1] as Set
        test.filter.commandLineIncludePatterns == [TEST_PATTERN_2] as Set
    }

    def "jvm arg providers are added to java fork options"() {
        when:
        test.jvmArgumentProviders.add(new CommandLineArgumentProvider() {
            @Override
            Iterable<String> asArguments() {
                return ["First", "Second"]
            }
        })
        def javaForkOptions = TestFiles.execFactory().newJavaForkOptions()
        test.copyTo(javaForkOptions)

        then:
        javaForkOptions.getAllJvmArgs().get().contains('First')
        javaForkOptions.getAllJvmArgs().get().contains('Second')
    }

    @Requires(IntegTestPreconditions.JavaHomeWithDifferentVersionAvailable)
    def "java version is determined with toolchain if set"() {
        def jdk = AvailableJavaHomes.differentVersion

        def metadata = JvmInstallationMetadata.from(
            jdk.javaHome,
            Integer.toString(jdk.javaVersionMajor),
            "", "", "", "", "", "", ""
        )
        def toolchain = new JavaToolchain(metadata, TestFiles.fileFactory(), Mock(JavaToolchainInput), false)
        def launcher = new DefaultToolchainJavaLauncher(toolchain)

        when:
        test.javaLauncher.set(launcher)

        then:
        test.getJavaVersion().majorVersion == Integer.toString(jdk.javaVersionMajor)
    }

    private void assertIsDirectoryTree(FileTreeInternal classFiles, Set<String> includes, Set<String> excludes) {
        classFiles.visitStructure(new FileCollectionStructureVisitor() {
            @Override
            void visitCollection(FileCollectionInternal.Source source, Iterable<File> contents) {
                throw new IllegalArgumentException()
            }


            @Override
            void visitFileTree(File root, PatternSet patterns, FileTreeInternal fileTree) {
                assert root == classesDir
                assert patterns.getIncludes() == includes
                assert patterns.getExcludes() == excludes
            }

            @Override
            void visitFileTreeBackedByFile(File file, FileTreeInternal fileTree, FileSystemMirroringFileTree sourceTree) {
                throw new IllegalArgumentException()
            }
        })
    }

    private void configureTask() {
        test.useTestFramework(testFrameworkMock)
        test.setTestExecuter(testExecuterMock)

        test.setTestClassesDirs(TestFiles.fixed(classesDir))
        test.getReports().getJunitXml().outputLocation.set(resultsDir)
        test.binaryResultsDirectory.set(binResultsDir)
        test.getReports().getHtml().outputLocation.set(reportDir)
        test.setClasspath(classpathMock)
    }

    def 'fails if custom executable does not exist'() {
        def task = project.tasks.create("test", Test)
        task.testClassesDirs = TestFiles.fixed(new File("tmp"))
        task.binaryResultsDirectory.fileValue(new File("out"))
        def invalidExecutable = temporaryFolder.file("invalidExecutable")

        when:
        task.executable = invalidExecutable
        task.javaLauncher.get()

        then:
        def e = thrown(AbstractProperty.PropertyQueryException)
        def cause = TestUtil.getRootCause(e) as InvalidUserDataException
        cause.message.contains("The configured executable does not exist")
        cause.message.contains(invalidExecutable.absolutePath)
    }

    def "fails if custom executable is a directory"() {
        def testTask = project.tasks.create("test", Test)
        def executableDir = temporaryFolder.createDir("javac")

        when:
        testTask.executable = executableDir.absolutePath
        testTask.javaVersion

        then:
        def e = thrown(AbstractProperty.PropertyQueryException)
        def cause = TestUtil.getRootCause(e) as InvalidUserDataException
        cause.message.contains("The configured executable is a directory")
        cause.message.contains(executableDir.name)
    }

    def "fails if custom executable is not from a valid JVM"() {
        def testTask = project.tasks.create("test", Test)
        def invalidJavac = temporaryFolder.createFile("invalidJavac")

        when:
        testTask.executable = invalidJavac.absolutePath
        testTask.javaVersion

        then:
        def e = thrown(AbstractProperty.PropertyQueryException)
        assertHasMatchingCause(e, m -> m.startsWith("Toolchain installation '${invalidJavac.parentFile.parentFile.absolutePath}' could not be probed:"))
        assertHasMatchingCause(e, m -> m ==~ /Cannot run program .*java.*/)
    }
}
