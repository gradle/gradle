/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.performance.regression.android

import org.gradle.integtests.fixtures.versions.AndroidGradlePluginVersions
import org.gradle.internal.scan.config.fixtures.ApplyGradleEnterprisePluginFixture
import org.gradle.performance.AbstractCrossVersionPerformanceTest
import org.gradle.performance.fixture.AndroidTestProject
import org.gradle.profiler.BuildMutator
import org.gradle.profiler.ScenarioContext

class AbstractRealLifeAndroidBuildPerformanceTest extends AbstractCrossVersionPerformanceTest {
    static final String SANTA_AGP_TARGET_VERSION = "3.6"

    def setup() {
        runner.args = [AndroidGradlePluginVersions.OVERRIDE_VERSION_CHECK]
        runner.targetVersions = ["7.0-20210122131800+0000"]
        // AGP 3.6 requires 5.6.1+
        // forUseAtConfigurationTime API used in this senario
        runner.minimumBaseVersion = "6.5"
    }

    void applyEnterprisePlugin() {
        runner.addBuildMutator { invocationSettings ->
            new BuildMutator() {
                @Override
                void beforeScenario(ScenarioContext context) {
                    ApplyGradleEnterprisePluginFixture.applyEnterprisePlugin(new File(invocationSettings.projectDir, "settings.gradle"))
                }
            }
        }
    }

    AndroidTestProject getAndroidTestProject() {
        // We assume here already, since the test may try to cast the returned test project to `IncrementalAndroidTestProject`,
        // which fails when the test project is non-incremental.
        runner.assumeShouldRun()
        AndroidTestProject.projectFor(runner.testProject)
    }
}
