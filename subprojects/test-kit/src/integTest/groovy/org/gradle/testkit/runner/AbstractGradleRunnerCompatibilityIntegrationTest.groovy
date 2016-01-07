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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.daemon.DaemonLogsAnalyzer
import org.gradle.integtests.fixtures.daemon.DaemonsFixture
import org.gradle.integtests.fixtures.executer.*
import org.gradle.internal.nativeintegration.services.NativeServices
import org.gradle.test.fixtures.file.TestFile
import org.gradle.testkit.runner.fixtures.GradleRunnerCompatibilityIntegTestRunner
import org.gradle.testkit.runner.internal.dist.InstalledGradleDistribution
import org.gradle.testkit.runner.internal.dist.VersionBasedGradleDistribution
import org.gradle.util.SetSystemProperties
import org.gradle.wrapper.GradleUserHomeLookup
import org.junit.Rule
import org.junit.runner.RunWith
import spock.lang.Shared

import static org.gradle.testkit.runner.internal.ToolingApiGradleExecutor.TEST_KIT_DAEMON_DIR_NAME

@RunWith(GradleRunnerCompatibilityIntegTestRunner)
class AbstractGradleRunnerCompatibilityIntegrationTest extends AbstractIntegrationSpec {

    @Shared
    IntegrationTestBuildContext buildContext = new IntegrationTestBuildContext()

    @Rule
    SetSystemProperties setSystemProperties = new SetSystemProperties(
        (NativeServices.NATIVE_DIR_OVERRIDE): buildContext.gradleUserHomeDir.file("native").absolutePath,
        (GradleUserHomeLookup.GRADLE_USER_HOME_PROPERTY_KEY): buildContext.gradleUserHomeDir.absolutePath
    )

    boolean requireIsolatedTestKitDir

    TestFile getTestKitDir() {
        requireIsolatedTestKitDir ? file("test-kit-workspace") : buildContext.gradleUserHomeDir
    }

    String getRootProjectName() {
        testDirectory.name
    }

    GradleRunner runner(List<String> arguments) {
        runner(arguments as String[])
    }

    GradleRunner runner(String... arguments) {
        def gradleRunner = GradleRunner.create()
            .withTestKitDir(testKitDir)
            .withProjectDir(testDirectory)
            .withArguments(arguments)
            .withDebug(GradleRunnerCompatibilityIntegTestRunner.debug)

        def gradleDistribution = GradleRunnerCompatibilityIntegTestRunner.distribution

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

    DaemonsFixture daemons(String version = GradleRunnerCompatibilityIntegTestRunner.gradleVersion.version) {
        daemons(testKitDir, TEST_KIT_DAEMON_DIR_NAME, version)
    }

    DaemonsFixture daemons(File gradleUserHomeDir, String daemonDir, String version) {
        DaemonLogsAnalyzer.newAnalyzer(new File(gradleUserHomeDir, daemonDir), version)
    }

    def cleanup() {
        if (requireIsolatedTestKitDir) {
            def daemonDir = new File(testKitDir, TEST_KIT_DAEMON_DIR_NAME)
            def versions = daemonDir.listFiles({ it.name ==~ /\d.+/ } as FileFilter)*.name
            versions.each { daemons(it).killAll() }
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
