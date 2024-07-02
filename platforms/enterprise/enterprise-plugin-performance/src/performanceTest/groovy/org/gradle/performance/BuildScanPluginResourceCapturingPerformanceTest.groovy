/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.performance

import org.gradle.performance.fixture.GradleBuildExperimentSpec

class BuildScanPluginResourceCapturingPerformanceTest extends AbstractBuildScanPluginPerformanceTest {

    private static final String WITH_RESOURCE_CAPTURING_LABEL = "With resource capturing"
    private static final String WITHOUT_RESOURCE_CAPTURING_LABEL = "Without resource capturing"

    def "with and without resource capturing (#scenario)"() {
        given:
        def jobArgs = ['--continue', '-DenableScan=true', '--no-scan', '-Dscan.capture-file-fingerprints', '-Dscan.resource-insights.internal.capturingStats=true'] + scenarioArgs

        runner.baseline {
            displayName(WITHOUT_RESOURCE_CAPTURING_LABEL)
            invocation {
                // Increase client VM heap memory because of a huge amount of output events
                clientJvmArgs("-Xmx256m", "-Xms256m")
                args(*jobArgs)
                args("-Dscan.capture-resource-insights=false")
                tasksToRun(*tasks)
                if (withFailure) {
                    expectFailure()
                }
                addBuildMutator { invocationSettings -> new SaveScanSpoolFile(invocationSettings, scenario) }
                if (manageCacheState) {
                    addBuildMutator { new ManageLocalCacheState(it.projectDir) }
                }
            }
        }

        runner.buildSpec {
            displayName(WITH_RESOURCE_CAPTURING_LABEL)
            invocation {
                // Increase client VM heap memory because of a huge amount of output events
                clientJvmArgs("-Xmx256m", "-Xms256m")
                args(*jobArgs)
                args("-Dscan.capture-resource-insights=true")
                tasksToRun(*tasks)
                if (withFailure) {
                    expectFailure()
                }
                addBuildMutator { invocationSettings -> new SaveScanSpoolFile(invocationSettings, scenario) }
                if (manageCacheState) {
                    addBuildMutator { new ManageLocalCacheState(it.projectDir) }
                }
            }
        }

        when:
        def results = runner.run()

        then:
        def withoutResults = buildBaselineResults(results, WITHOUT_RESOURCE_CAPTURING_LABEL)
        def withResults = results.buildResult(WITH_RESOURCE_CAPTURING_LABEL)
        def speedStats = withoutResults.getSpeedStatsAgainst(withResults.name, withResults)
        println(speedStats)
        !withoutResults.significantlyFasterThan(withResults)

        where:
        scenario                     | tasks                              | withFailure | scenarioArgs                                      | manageCacheState
        "clean build - 20 projects"  | ['clean', 'project20:buildNeeded'] | true        | ['--build-cache', '-DreducedOutput=true']         | true
        "help - configuration cache" | ['help']                           | false       | ['--configuration-cache', '-DreducedOutput=true'] | false
        "help - no console output"   | ['help']                           | false       | ['-DreducedOutput=true']                          | false
    }

    @Override
    protected void configureGradleSpec(GradleBuildExperimentSpec.GradleBuilder builder) {
        super.configureGradleSpec(builder)
        builder.warmUpCount = 5
        builder.invocationCount = 10
        builder.addBuildMutator { invocationSettings -> new InjectDevelocityPlugin(invocationSettings.projectDir, pluginVersionNumber) }
    }
}
