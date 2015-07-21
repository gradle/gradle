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
import org.gradle.internal.id.UUIDGenerator
import org.gradle.launcher.daemon.context.DaemonContext
import org.gradle.testkit.runner.internal.DefaultGradleRunner
import org.gradle.util.GFileUtils
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Ignore

import static org.gradle.testkit.runner.TaskResult.*

class GradleRunnerIsolatedDaemonIntegrationTest extends AbstractGradleRunnerIntegrationTest {
    @Rule TemporaryFolder testUserHomeDir = new TemporaryFolder()

    @Ignore
    def "configuration in default Gradle user home directory is ignored for test execution with daemon"() {
        given:
        File defaultGradleUserHomeDir = new File(testUserHomeDir.root, '.gradle')

        and:
        String gradlePropertiesContent = 'myProp1=propertiesFile'
        writeGradlePropertiesFile(defaultGradleUserHomeDir, gradlePropertiesContent)

        and:
        String initScriptContent = "allprojects { ext.myProp2 = 'initScript' }"
        writeInitScriptFile(defaultGradleUserHomeDir, initScriptContent)

        and:
        buildFile << """
            task verifyProjectProperties {
                doLast {
                    assert !project.ext.has('myProp1')
                    assert !project.ext.has('myProp2')
                }
            }
        """

        when:
        DefaultGradleRunner gradleRunner = runner('verifyProjectProperties')
        gradleRunner.withJvmArguments("-Duser.home=$testUserHomeDir.root.canonicalPath")
        BuildResult result = gradleRunner.build()

        then:
        result.tasks.collect { it.path } == [':verifyProjectProperties']
        result.taskPaths(SUCCESS) == [':verifyProjectProperties']
        result.taskPaths(SKIPPED).empty
        result.taskPaths(UP_TO_DATE).empty
        result.taskPaths(FAILED).empty
    }

    @Ignore
    def "configuration in custom Gradle user home directory is used for test execution with daemon"() {
        setup:
        String gradlePropertiesContent = 'myProp1=propertiesFile'
        File gradlePropertiesFile = writeGradlePropertiesFile(buildContext.gradleUserHomeDir, gradlePropertiesContent)

        and:
        String initScriptContent = "allprojects { ext.myProp2 = 'initScript' }"
        File initScriptFile = writeInitScriptFile(buildContext.gradleUserHomeDir, initScriptContent)

        and:
        buildFile << """
            task verifyProjectProperties {
                doLast {
                    assert project.ext.has('myProp1')
                    assert project.ext.has('myProp2')
                }
            }
        """

        when:
        GradleRunner gradleRunner = runner('verifyProjectProperties')
        BuildResult result = gradleRunner.build()

        then:
        result.tasks.collect { it.path } == [':verifyProjectProperties']
        result.taskPaths(SUCCESS) == [':verifyProjectProperties']
        result.taskPaths(SKIPPED).empty
        result.taskPaths(UP_TO_DATE).empty
        result.taskPaths(FAILED).empty

        cleanup:
        GFileUtils.forceDelete(gradlePropertiesFile)
        GFileUtils.forceDelete(initScriptFile)
    }

    def "daemon process dedicated to test execution uses short idle timeout"() {
        given:
        File customGradleUserHomeDir = createCustomGradleUserHomeDir()
        DaemonLogsAnalyzer daemonLogsAnalyzer = createDaemonLogsAnalyzer(customGradleUserHomeDir)
        buildFile << helloWorldTask()

        when:
        GradleRunner gradleRunner = runnerWithCustomGradleUserHomeDir(customGradleUserHomeDir, 'helloWorld')
        gradleRunner.build()

        then:
        List<DaemonFixture> daemons = daemonLogsAnalyzer.visible
        daemons.size() == 1
        daemons[0].context.idleTimeout == 120000
    }

    @Ignore
    def "daemon process dedicated to test execution is reused if one already exists"() {
        given:
        File customGradleUserHomeDir = createCustomGradleUserHomeDir()
        DaemonLogsAnalyzer daemonLogsAnalyzer = createDaemonLogsAnalyzer(customGradleUserHomeDir)
        buildFile << helloWorldTask()

        when:
        GradleRunner gradleRunner = runnerWithCustomGradleUserHomeDir(customGradleUserHomeDir, 'helloWorld')
        gradleRunner.build()

        then:
        List<DaemonFixture> initialDaemons = daemonLogsAnalyzer.visible
        initialDaemons.size() == 1
        DaemonContext initialDaemonContext = initialDaemons[0].context
        Long daemonPidInUse = initialDaemonContext.pid
        daemonPidInUse

        when:
        gradleRunner.build()

        then:
        List<DaemonFixture> laterDaemons = daemonLogsAnalyzer.visible
        laterDaemons.size() == 1
        DaemonContext laterDaemonContext = laterDaemons[0].context
        daemonPidInUse == laterDaemonContext.pid
    }

    private File createCustomGradleUserHomeDir() {
        new File(testUserHomeDir.root, new UUIDGenerator().generateId().toString())
    }

    private DaemonLogsAnalyzer createDaemonLogsAnalyzer(File customGradleUserHomeDir)  {
        DaemonLogsAnalyzer daemonLogsAnalyzer = new DaemonLogsAnalyzer(new File(customGradleUserHomeDir, 'daemon'), buildContext.version.version)
        assert daemonLogsAnalyzer.visible.empty
        daemonLogsAnalyzer
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
