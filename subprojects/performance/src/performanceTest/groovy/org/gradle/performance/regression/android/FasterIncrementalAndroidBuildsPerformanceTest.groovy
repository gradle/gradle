/*
 * Copyright 2019 the original author or authors.
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

import org.gradle.performance.AbstractCrossBuildPerformanceTest
import org.gradle.performance.categories.PerformanceExperiment
import org.gradle.performance.fixture.BuildExperimentSpec
import org.gradle.performance.fixture.GradleBuildExperimentSpec
import org.junit.experimental.categories.Category
import spock.lang.Unroll

import static org.gradle.performance.regression.android.IncrementalAndroidTestProject.SANTA_TRACKER_JAVA
import static org.gradle.performance.regression.android.IncrementalAndroidTestProject.SANTA_TRACKER_KOTLIN

@Category(PerformanceExperiment)
class FasterIncrementalAndroidBuildsPerformanceTest extends AbstractCrossBuildPerformanceTest {
    private static final String INSTANT_EXECUTION_PROPERTY = "-Dorg.gradle.unsafe.instant-execution"

    @Unroll
    def "faster incremental build on #testProject (build comparison)"() {
        given:
        runner.testGroup = "incremental android changes"
        runner.buildSpec {
            testProject.configureForNonAbiChange(it)
            displayName("non abi change")
        }
        runner.buildSpec {
            testProject.configureForAbiChange(it)
            displayName("abi change")
        }
        if (testProject != SANTA_TRACKER_KOTLIN) {
            // Kotlin is not supported for instant execution
            runner.buildSpec {
                testProject.configureForNonAbiChange(it)
                configureFastIncrementalBuild(it)
                displayName("instant non abi change")
            }
            runner.buildSpec {
                testProject.configureForAbiChange(it)
                configureFastIncrementalBuild(it)
                displayName("instant abi change")
            }
        }

        when:
        def results = runner.run()
        then:
        results

        where:
        testProject << [SANTA_TRACKER_KOTLIN, SANTA_TRACKER_JAVA]
    }

    @Override
    protected void defaultSpec(BuildExperimentSpec.Builder builder) {
        if (builder instanceof GradleBuildExperimentSpec.GradleBuilder) {
            builder.invocation.args('-Dcom.android.build.gradle.overrideVersionCheck=true')
        }
    }

    def configureFastIncrementalBuild(GradleBuildExperimentSpec.GradleBuilder builder) {
        builder.invocation.args(INSTANT_EXECUTION_PROPERTY)
    }
}
