/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.testkit.runner

import org.gradle.integtests.fixtures.daemon.DaemonFixture
import org.gradle.integtests.fixtures.daemon.DaemonLogsAnalyzer
import org.gradle.integtests.fixtures.executer.DaemonGradleExecuter
import org.gradle.integtests.fixtures.executer.GradleDistribution
import org.gradle.integtests.fixtures.executer.GradleExecuter
import org.gradle.integtests.fixtures.executer.UnderDevelopmentGradleDistribution
import org.gradle.internal.FileUtils
import org.gradle.test.fixtures.ConcurrentTestUtil
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.testkit.runner.internal.DefaultGradleRunner
import org.gradle.util.GFileUtils
import org.junit.Rule

import static org.gradle.testkit.runner.TaskOutcome.*

class GradleRunnerIsolatedDaemonIntegrationTest extends AbstractGradleRunnerIntegrationTest {
    @Rule
    final TestNameTestDirectoryProvider userHomeDirProvider = new TestNameTestDirectoryProvider()

    @Rule
    final TestNameTestDirectoryProvider testKitGradleUserHomeDirProvider = new TestNameTestDirectoryProvider()

    @Rule
    final TestNameTestDirectoryProvider userWorkingSpaceGradleUserHomeDirProvider = new TestNameTestDirectoryProvider()

    @Rule
    final ConcurrentTestUtil concurrent = new ConcurrentTestUtil(15000)

    def "configuration in default Gradle user home directory is ignored for test execution with daemon"() {
        given:
        File defaultGradleUserHomeDir = new File(userHomeDirProvider.testDirectory, '.gradle')

        and:
        String gradlePropertiesContent = 'myProp1=propertiesFile'
        writeGradlePropertiesFile(defaultGradleUserHomeDir, gradlePropertiesContent)

        and:
        String initScriptContent = "allprojects { ext.myProp2 = 'initScript' }"
        writeInitScriptFile(defaultGradleUserHomeDir, initScriptContent)

        and:
        buildFile << """
            task check {
                doLast {
                    assert !project.ext.has('myProp1')
                    assert !project.ext.has('myProp2')
                }
            }
        """

        when:
        DefaultGradleRunner gradleRunner = runner('check')
        gradleRunner.withJvmArguments("-Duser.home=$userHomeDirProvider.testDirectory.canonicalPath")
        BuildResult result = gradleRunner.build()

        then:
        result.tasks.collect { it.path } == [':check']
        result.taskPaths(SUCCESS) == [':check']
        result.taskPaths(SKIPPED).empty
        result.taskPaths(UP_TO_DATE).empty
        result.taskPaths(FAILED).empty
    }

    def "configuration in custom Gradle user home directory is used for test execution with daemon"() {
        setup:
        String gradlePropertiesContent = 'myProp1=propertiesFile'
        File gradlePropertiesFile = writeGradlePropertiesFile(buildContext.gradleUserHomeDir, gradlePropertiesContent)

        and:
        String initScriptContent = "allprojects { ext.myProp2 = 'initScript' }"
        File initScriptFile = writeInitScriptFile(buildContext.gradleUserHomeDir, initScriptContent)

        and:
        buildFile << """
            task check {
                doLast {
                    assert project.ext.has('myProp1')
                    assert project.ext.has('myProp2')
                }
            }
        """

        when:
        GradleRunner gradleRunner = runner('check')
        BuildResult result = gradleRunner.build()

        then:
        result.tasks.collect { it.path } == [':check']
        result.taskPaths(SUCCESS) == [':check']
        result.taskPaths(SKIPPED).empty
        result.taskPaths(UP_TO_DATE).empty
        result.taskPaths(FAILED).empty

        cleanup:
        GFileUtils.forceDelete(gradlePropertiesFile)
        GFileUtils.forceDelete(initScriptFile)
    }

    def "daemon process dedicated to test execution uses short idle timeout"() {
        given:
        buildFile << helloWorldTask()

        when:
        DaemonLogsAnalyzer testKitDaemonLogsAnalyzer = createDaemonLogsAnalyzer(testKitGradleUserHomeDirProvider.testDirectory)
        assert testKitDaemonLogsAnalyzer.visible.empty
        GradleRunner gradleRunner = runnerWithCustomGradleUserHomeDir(testKitGradleUserHomeDirProvider.testDirectory, 'helloWorld')
        gradleRunner.build()

        then:
        DaemonFixture testKitDaemon = expectSingleDaemon(testKitDaemonLogsAnalyzer)
        testKitDaemon.context.idleTimeout == 120000
    }

    def "daemon process dedicated to test execution is reused if one already exists"() {
        given:
        buildFile << helloWorldTask()

        when:
        DaemonLogsAnalyzer testKitDaemonLogsAnalyzer = createDaemonLogsAnalyzer(testKitGradleUserHomeDirProvider.testDirectory)
        assert testKitDaemonLogsAnalyzer.visible.empty
        GradleRunner gradleRunner = runnerWithCustomGradleUserHomeDir(testKitGradleUserHomeDirProvider.testDirectory, 'helloWorld')
        gradleRunner.build()

        then:
        DaemonFixture initialDaemon = expectSingleDaemon(testKitDaemonLogsAnalyzer)
        initialDaemon.assertIdle()

        when:
        gradleRunner.build()

        then:
        DaemonFixture laterDaemon = expectSingleDaemon(testKitDaemonLogsAnalyzer)
        laterDaemon.assertIdle()
        laterDaemon.context.pid == initialDaemon.context.pid
    }

    def "user daemon process does not reuse existing daemon process intended for test execution"() {
        given:
        buildFile << helloWorldTask()

        when:
        File testKitGradleUserHomeDir = testKitGradleUserHomeDirProvider.testDirectory
        DaemonLogsAnalyzer testKitDaemonLogsAnalyzer = createDaemonLogsAnalyzer(testKitGradleUserHomeDir)
        assert testKitDaemonLogsAnalyzer.visible.empty
        GradleRunner gradleRunner = runnerWithCustomGradleUserHomeDir(testKitGradleUserHomeDir, 'helloWorld')
        gradleRunner.build()

        then:
        DaemonFixture testKitDaemon = expectSingleDaemon(testKitDaemonLogsAnalyzer)
        testKitDaemon.assertIdle()

        when:
        DaemonLogsAnalyzer userDaemonLogsAnalyzer = createDaemonLogsAnalyzer(userWorkingSpaceGradleUserHomeDirProvider.testDirectory)
        assert userDaemonLogsAnalyzer.visible.empty
        GradleDistribution distribution = new UnderDevelopmentGradleDistribution()
        GradleExecuter executer = new DaemonGradleExecuter(distribution, userWorkingSpaceGradleUserHomeDirProvider)
        executer.usingProjectDirectory(testProjectDir.testDirectory).withArguments('helloWorld').requireIsolatedDaemons().run()

        then:
        DaemonFixture userDaemon = expectSingleDaemon(userDaemonLogsAnalyzer)
        userDaemon.assertIdle()
        userDaemon.context.pid != testKitDaemon.context.pid
    }

    def "executing a build with a -g option does not affect daemon mechanics"() {
        given:
        File customGradleUserHomeDir = testKitGradleUserHomeDirProvider.testDirectory
        buildFile << helloWorldTask()

        when:
        GradleRunner gradleRunner = runner('helloWorld', "-g ${FileUtils.toSafeFileName(customGradleUserHomeDir.absolutePath)}")
        gradleRunner.build()

        then:
        customGradleUserHomeDir.exists()
        !new File(customGradleUserHomeDir, 'daemon').exists()
        new File(gradleRunner.gradleUserHomeDir, 'daemon').exists()
        DaemonLogsAnalyzer daemonLogsAnalyzer = createDaemonLogsAnalyzer(gradleRunner.gradleUserHomeDir)
        !daemonLogsAnalyzer.visible.empty
    }

    def "runners executed concurrently can share the same Gradle user home directory"() {
        given:
        buildFile << helloWorldTask()

        when:
        Set<GradleRunner> usedGradleUserHomeDirs = [] as Set<GradleRunner>

        3.times {
            concurrent.start {
                GradleRunner gradleRunner = runner('helloWorld')
                usedGradleUserHomeDirs << gradleRunner.gradleUserHomeDir
                gradleRunner.build()
            }
        }

        then:
        concurrent.finished()
        usedGradleUserHomeDirs.size() == 1
    }

    private DaemonFixture expectSingleDaemon(DaemonLogsAnalyzer daemonLogsAnalyzer) {
        List<DaemonFixture> userDaemons = daemonLogsAnalyzer.visible
        assert userDaemons.size() == 1
        DaemonFixture daemon = userDaemons[0]
        assert daemon.context.pid
        daemon
    }

    private DaemonLogsAnalyzer createDaemonLogsAnalyzer(File customGradleUserHomeDir)  {
        DaemonLogsAnalyzer.newAnalyzer(new File(customGradleUserHomeDir, 'daemon'), buildContext.version.version)
    }

    private GradleRunner runnerWithCustomGradleUserHomeDir(File customGradleUserHomeDir, String... arguments) {
        DefaultGradleRunner gradleRunner = runner(arguments)
        gradleRunner.withGradleUserHomeDir(customGradleUserHomeDir)
        gradleRunner
    }

    private File writeGradlePropertiesFile(File gradleUserHomeDir, String content) {
        File gradlePropertiesFile = new File(gradleUserHomeDir, 'gradle.properties')
        GFileUtils.writeFile(content, gradlePropertiesFile)
        gradlePropertiesFile
    }

    private File writeInitScriptFile(File gradleUserHomeDir, String content) {
        File initScriptFile = new File(gradleUserHomeDir, 'init.gradle')
        GFileUtils.writeFile(content, initScriptFile)
        initScriptFile
    }
}
