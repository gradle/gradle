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

import org.gradle.integtests.fixtures.daemon.DaemonsFixture
import org.gradle.testkit.runner.fixtures.GradleRunnerCompatibilityIntegTestRunner
import org.gradle.testkit.runner.internal.DefaultGradleRunner
import org.gradle.testkit.runner.internal.dist.GradleDistribution
import org.gradle.testkit.runner.internal.dist.InstalledGradleDistribution
import org.gradle.testkit.runner.internal.dist.VersionBasedGradleDistribution
import org.junit.runner.RunWith

import static org.gradle.testkit.runner.internal.ToolingApiGradleExecutor.TEST_KIT_DAEMON_DIR_NAME

@RunWith(GradleRunnerCompatibilityIntegTestRunner)
class AbstractGradleRunnerCompatibilityIntegrationTest extends AbstractGradleRunnerIntegrationTest {

    @Override
    DefaultGradleRunner runner(String... arguments) {
        DefaultGradleRunner gradleRunner = super.runner(arguments)
        GradleDistribution gradleDistribution = GradleRunnerCompatibilityIntegTestRunner.distribution

        if (gradleDistribution instanceof InstalledGradleDistribution) {
            gradleRunner.withGradleInstallation(((InstalledGradleDistribution) gradleDistribution).gradleHome)
        } else if (gradleDistribution instanceof VersionBasedGradleDistribution) {
            gradleRunner.withGradleVersion(((VersionBasedGradleDistribution) gradleDistribution).gradleVersion)
        }

        throw unsupportedGradleDistributionException()
    }

    @Override
    DaemonsFixture daemons() {
        GradleDistribution gradleDistribution = GradleRunnerCompatibilityIntegTestRunner.distribution

        if (gradleDistribution instanceof InstalledGradleDistribution) {
            return super.daemons()
        } else if (gradleDistribution instanceof VersionBasedGradleDistribution) {
            return daemons(testKitDir, TEST_KIT_DAEMON_DIR_NAME, ((VersionBasedGradleDistribution) gradleDistribution).gradleVersion)
        }

        throw unsupportedGradleDistributionException()
    }

    private RuntimeException unsupportedGradleDistributionException() {
        new IllegalArgumentException('Unsupported Gradle distribution. Please pick from an installed or version-based Gradle distribution!')
    }
}
