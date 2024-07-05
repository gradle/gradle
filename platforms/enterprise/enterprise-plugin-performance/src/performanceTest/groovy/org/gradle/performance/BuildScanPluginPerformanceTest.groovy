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

package org.gradle.performance

import org.gradle.performance.fixture.GradleBuildExperimentSpec

class BuildScanPluginPerformanceTest extends AbstractBuildScanPluginPerformanceTest {

    private static final int MEDIAN_PERCENTAGES_SHIFT = 10

    private static final String WITHOUT_PLUGIN_LABEL = "1 without plugin"
    private static final String WITH_PLUGIN_LABEL = "2 with plugin"
    public static final int WARMUPS = 10
    public static final int INVOCATIONS = 20

    def "with and without plugin application (#scenario)"() {
        given:
        def jobArgs = ['--continue', '--no-scan', '-Dscan.capture-file-fingerprints'] + scenarioArgs

        runner.baseline {
            displayName(WITHOUT_PLUGIN_LABEL)
            invocation {
                args(*jobArgs)
                tasksToRun(*tasks)
                if (withFailure) {
                    expectFailure()
                }
            }
            addBuildMutator { invocationSettings -> new SaveScanSpoolFile(invocationSettings, scenario) }
            if (manageCacheState) {
                addBuildMutator { new ManageLocalCacheState(it.projectDir) }
            }
        }

        runner.buildSpec {
            displayName(WITH_PLUGIN_LABEL)
            invocation {
                args(*jobArgs)
                args("-DenableScan=true")
                tasksToRun(*tasks)
                if (withFailure) {
                    expectFailure()
                }
            }
            addBuildMutator { invocationSettings -> new SaveScanSpoolFile(invocationSettings, scenario) }
            if (manageCacheState) {
                addBuildMutator { new ManageLocalCacheState(it.projectDir) }
            }
        }

        when:
        def results = runner.run()

        then:
        def withoutResults = buildBaselineResults(results, WITHOUT_PLUGIN_LABEL)
        def withResults = results.buildResult(WITH_PLUGIN_LABEL)
        def speedStats = withoutResults.getSpeedStatsAgainst(withResults.name, withResults)
        println(speedStats)

        where:
        scenario                                                | tasks                              | withFailure | scenarioArgs                                                  | manageCacheState
        "clean build - 50 projects"                             | ['clean', 'build']                 | true        | ['--build-cache']                                             | true
        "clean build - 20 projects - slow tasks - less console" | ['clean', 'project20:buildNeeded'] | true        | ['--build-cache', '-DreducedOutput=true', '-DslowTasks=true'] | true
        "help"                                                  | ['help']                           | false       | []                                                            | false
        "help - no console output"                              | ['help']    | false                                                         | ['-DreducedOutput=true']                                      | false
    }

    @Override
    protected void configureGradleSpec(GradleBuildExperimentSpec.GradleBuilder builder) {
        super.configureGradleSpec(builder)
        builder.warmUpCount = WARMUPS
        builder.invocationCount = INVOCATIONS
        builder.invocation {
            // Increase client VM heap memory because of a huge amount of output events
            clientJvmArgs("-Xmx256m", "-Xms256m")
        }
        builder.addBuildMutator { invocationSettings -> new InjectDevelocityPlugin(invocationSettings.projectDir, pluginVersionNumber) }
    }
}
