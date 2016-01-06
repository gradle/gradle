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

import org.gradle.integtests.fixtures.daemon.DaemonLogsAnalyzer
import org.gradle.integtests.fixtures.daemon.DaemonsFixture
import org.gradle.integtests.fixtures.executer.ExecutionFailure
import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.gradle.integtests.fixtures.executer.IntegrationTestBuildContext
import org.gradle.integtests.fixtures.executer.OutputScrapingExecutionFailure
import org.gradle.integtests.fixtures.executer.OutputScrapingExecutionResult
import org.gradle.internal.nativeintegration.services.NativeServices
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.testkit.runner.fixtures.GradleRunnerCompatibilityIntegTestRunner
import org.gradle.testkit.runner.fixtures.GradleRunnerIntegTestRunner
import org.gradle.testkit.runner.internal.dist.GradleDistribution
import org.gradle.testkit.runner.internal.dist.InstalledGradleDistribution
import org.gradle.testkit.runner.internal.dist.VersionBasedGradleDistribution
import org.gradle.util.SetSystemProperties
import org.junit.Rule
import org.junit.runner.RunWith
import spock.lang.Shared
import spock.lang.Specification

import static org.gradle.testkit.runner.internal.ToolingApiGradleExecutor.TEST_KIT_DAEMON_DIR_NAME

@RunWith(GradleRunnerCompatibilityIntegTestRunner)
class AbstractGradleRunnerCompatibilityIntegrationTest extends Specification {
    @Shared
    IntegrationTestBuildContext buildContext = new IntegrationTestBuildContext()

    @Rule
    TestNameTestDirectoryProvider testProjectDir = new TestNameTestDirectoryProvider()

    @Rule
    SetSystemProperties setSystemProperties = new SetSystemProperties((NativeServices.NATIVE_DIR_OVERRIDE): buildContext.gradleUserHomeDir.file("native").absolutePath)

    boolean requireIsolatedTestKitDir

    TestFile getTestKitDir() {
        requireIsolatedTestKitDir ? testProjectDir.file("test-kit-workspace") : buildContext.gradleUserHomeDir
    }

    TestFile getBuildFile() {
        file('build.gradle')
    }

    TestFile file(String path) {
        testProjectDir.file(path)
    }

    String getRootProjectName() {
        testProjectDir.testDirectory.name
    }

    GradleRunner runner(List<String> arguments) {
        runner(arguments as String[])
    }

    GradleRunner runner(String... arguments) {
        GradleRunner gradleRunner = GradleRunner.create()
            .withGradleInstallation(buildContext.gradleHomeDir)
            .withTestKitDir(testKitDir)
            .withProjectDir(testProjectDir.testDirectory)
            .withArguments(arguments)
            .withDebug(GradleRunnerIntegTestRunner.debug)
        GradleDistribution gradleDistribution = GradleRunnerCompatibilityIntegTestRunner.distribution

        if (gradleDistribution instanceof InstalledGradleDistribution) {
            gradleRunner.withGradleInstallation(((InstalledGradleDistribution) gradleDistribution).gradleHome)
        } else if (gradleDistribution instanceof VersionBasedGradleDistribution) {
            gradleRunner.withGradleVersion(((VersionBasedGradleDistribution) gradleDistribution).gradleVersion)
        } else {
            throw unsupportedGradleDistributionException()
        }
    }

    static String helloWorldTask() {
        """
        task helloWorld {
            doLast {
                println 'Hello world!'
            }
        }
        """
    }

    DaemonsFixture daemons(File gradleUserHomeDir, String daemonDir = 'daemon', String version = buildContext.version.version) {
        DaemonLogsAnalyzer.newAnalyzer(new File(gradleUserHomeDir, daemonDir), version)
    }

    DaemonsFixture daemons() {
        GradleDistribution gradleDistribution = GradleRunnerCompatibilityIntegTestRunner.distribution

        if (gradleDistribution instanceof InstalledGradleDistribution) {
            return daemons(testKitDir, TEST_KIT_DAEMON_DIR_NAME)
        } else if (gradleDistribution instanceof VersionBasedGradleDistribution) {
            return daemons(testKitDir, TEST_KIT_DAEMON_DIR_NAME, ((VersionBasedGradleDistribution) gradleDistribution).gradleVersion)
        } else {
            throw unsupportedGradleDistributionException()
        }
    }

    def cleanup() {
        if (requireIsolatedTestKitDir) {
            daemons().killAll()
        }
    }

    ExecutionResult execResult(BuildResult buildResult) {
        new OutputScrapingExecutionResult(buildResult.output, buildResult.output)
    }

    ExecutionFailure execFailure(BuildResult buildResult) {
        new OutputScrapingExecutionFailure(buildResult.output, buildResult.output)
    }

    private static RuntimeException unsupportedGradleDistributionException() {
        new IllegalArgumentException('Unsupported Gradle distribution. Please pick from an installed or version-based Gradle distribution!')
    }
}
