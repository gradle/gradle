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

import org.apache.commons.io.FileUtils
import org.gradle.performance.categories.PerformanceRegressionTest
import org.gradle.performance.fixture.BuildExperimentInvocationInfo
import org.gradle.performance.fixture.BuildExperimentListener
import org.gradle.performance.fixture.BuildExperimentListenerAdapter
import org.gradle.performance.fixture.BuildExperimentRunner
import org.gradle.performance.fixture.BuildExperimentSpec
import org.gradle.performance.measure.MeasuredOperation
import org.gradle.test.fixtures.file.TestFile
import org.junit.experimental.categories.Category
import spock.lang.Unroll

@Category(PerformanceRegressionTest)
class BuildScanPluginPerformanceTest extends AbstractBuildScanPluginPerformanceTest {

    private static final int MEDIAN_PERCENTAGES_SHIFT = 10

    private static final String WITHOUT_PLUGIN_LABEL = "1 without plugin"
    private static final String WITH_PLUGIN_LABEL = "2 with plugin"
    public static final int WARMUPS = 10
    public static final int INVOCATIONS = 20

    @Unroll
    def "large java project with and without plugin application (#scenario)"() {
        given:
        def sourceProject = "javaProject"
        def jobArgs = ['--continue', '-Dscan.capture-task-input-files'] + scenarioArgs
        def opts = ['-Xms4096m', '-Xmx4096m']

        def buildExperimentListeners = [
                new InjectBuildScanPlugin(pluginVersionNumber),
                new SaveScanSpoolFile(scenario)
        ]

        if (manageCacheState) {
            buildExperimentListeners << new ManageLocalCacheState()
        }

        def buildExperimentListener = BuildExperimentListener.compose(*buildExperimentListeners)

        runner.testId = "large java project with and without plugin application ($scenario)"
        runner.baseline {
            warmUpCount WARMUPS
            invocationCount INVOCATIONS
            projectName(sourceProject)
            displayName(WITHOUT_PLUGIN_LABEL)
            invocation {
                args(*jobArgs)
                tasksToRun(*tasks)
                gradleOpts(*opts)
                if (withFailure) {
                    expectFailure()
                }
                listener(buildExperimentListener)
            }
        }

        runner.buildSpec {
            warmUpCount WARMUPS
            invocationCount INVOCATIONS
            projectName(sourceProject)
            displayName(WITH_PLUGIN_LABEL)
            invocation {
                args(*jobArgs)
                args("-DenableScan=true")
                tasksToRun(*tasks)
                gradleOpts(*opts)
                if (withFailure) {
                    expectFailure()
                }
                listener(buildExperimentListener)
            }
        }

        when:
        def results = runner.run()

        then:
        def withoutResults = buildBaselineResults(results, WITHOUT_PLUGIN_LABEL)
        def withResults = results.buildResult(WITH_PLUGIN_LABEL)
        def speedStats = withoutResults.getSpeedStatsAgainst(withResults.name, withResults)
        println(speedStats)

        def shiftedResults = buildShiftedResults(results, WITHOUT_PLUGIN_LABEL, MEDIAN_PERCENTAGES_SHIFT)
        if (shiftedResults.significantlyFasterThan(withResults)) {
            throw new AssertionError(speedStats)
        }

        where:
        scenario                                                | expectedMedianPercentageShift | tasks                              | withFailure | scenarioArgs                                                                                   | manageCacheState
        "clean build - 50 projects"                             | MEDIAN_PERCENTAGES_SHIFT      | ['clean', 'build']                 | true        | ['--build-cache', '--parallel', '--max-workers=4']                                             | true
        "clean build - 20 projects - slow tasks - less console" | MEDIAN_PERCENTAGES_SHIFT      | ['clean', 'project20:buildNeeded'] | true        | ['--build-cache', '--parallel', '--max-workers=4', '-DreducedOutput=true', '-DslowTasks=true'] | true
        "help"                                                  | MEDIAN_PERCENTAGES_SHIFT      | ['help']                           | false       | []                                                                                             | false
        "help - no console output"                              | MEDIAN_PERCENTAGES_SHIFT      | ['help']                           | false       | ['-DreducedOutput=true']                                                                       | false
    }


    static class ManageLocalCacheState extends BuildExperimentListenerAdapter {
        void beforeExperiment(BuildExperimentSpec experimentSpec, File projectDir) {
            def projectTestDir = new TestFile(projectDir)
            def cacheDir = projectTestDir.file('local-build-cache')
            def settingsFile = projectTestDir.file('settings.gradle')
            settingsFile << """
                    buildCache {
                        local {
                            directory = '${cacheDir.absoluteFile.toURI()}'
                        }
                    }
                """.stripIndent()
        }

        @Override
        void afterInvocation(BuildExperimentInvocationInfo invocationInfo, MeasuredOperation operation, MeasurementCallback measurementCallback) {
            assert !new File(invocationInfo.projectDir, 'error.log').exists()
            def buildCacheDirectory = new TestFile(invocationInfo.projectDir, 'local-build-cache')
            def cacheEntries = buildCacheDirectory.listFiles().sort()
            cacheEntries.eachWithIndex { TestFile entry, int i ->
                if (i % 2 == 0) {
                    entry.delete()
                }
            }
        }
    }

    static class SaveScanSpoolFile extends BuildExperimentListenerAdapter {
        final String testId

        SaveScanSpoolFile(String testId) {
            this.testId = testId.replaceAll(/[- ]/, '_')
        }

        @Override
        void beforeInvocation(BuildExperimentInvocationInfo invocationInfo) {
            spoolDir(invocationInfo).deleteDir()
        }

        @Override
        void afterInvocation(BuildExperimentInvocationInfo invocationInfo, MeasuredOperation operation, MeasurementCallback measurementCallback) {
            def spoolDir = this.spoolDir(invocationInfo)
            if (invocationInfo.phase == BuildExperimentRunner.Phase.MEASUREMENT && (invocationInfo.iterationNumber == invocationInfo.iterationMax) && spoolDir.exists()) {
                def targetDirectory = new File("build/scan-dumps/$testId")
                targetDirectory.deleteDir()
                FileUtils.moveToDirectory(spoolDir, targetDirectory, true)
            }
        }

        private File spoolDir(BuildExperimentInvocationInfo invocationInfo) {
            new File(invocationInfo.gradleUserHome, "build-scan-data")
        }
    }

    static class InjectBuildScanPlugin extends BuildExperimentListenerAdapter {
        final String buildScanPluginVersion

        InjectBuildScanPlugin(String buildScanPluginVersion) {
            this.buildScanPluginVersion = buildScanPluginVersion
            println "InjectBuildScanPlugin buildScanPluginVersion = $buildScanPluginVersion"
        }

        @Override
        void beforeExperiment(BuildExperimentSpec experimentSpec, File projectDir) {

            def projectTestDir = new TestFile(projectDir)
            def settingsScript = projectTestDir.file('settings.gradle')
            settingsScript.text = """
                    buildscript {
                        repositories {
                            maven {
                                url 'https://repo.gradle.org/gradle/enterprise-libs-snapshots-local/'
                                credentials {
                                    username = System.getenv("ARTIFACTORY_USERNAME")
                                    password = System.getenv("ARTIFACTORY_PASSWORD")
                                }
                                authentication {
                                    basic(BasicAuthentication)
                                }
                            }
                        }

                        dependencies {
                            classpath "com.gradle:gradle-enterprise-gradle-plugin:${buildScanPluginVersion}"
                        }
                    }

                    if (System.getProperty('enableScan')) {
                        apply plugin: 'com.gradle.enterprise'
                    }
                    """ + settingsScript.text
        }
    }
}
