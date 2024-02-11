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
import org.gradle.profiler.BuildContext
import org.gradle.profiler.BuildMutator
import org.gradle.profiler.InvocationSettings
import org.gradle.profiler.Phase
import org.gradle.profiler.ScenarioContext
import org.gradle.test.fixtures.file.TestFile

class BuildScanPluginPerformanceTest extends AbstractBuildScanPluginPerformanceTest {

    private static final int MEDIAN_PERCENTAGES_SHIFT = 10

    private static final String WITHOUT_PLUGIN_LABEL = "1 without plugin"
    private static final String WITH_PLUGIN_LABEL = "2 with plugin"
    public static final int WARMUPS = 10
    public static final int INVOCATIONS = 20

    def "with and without plugin application (#scenario)"() {
        given:
        def jobArgs = ['--continue', '-Dscan.capture-task-input-files'] + scenarioArgs

        runner.baseline {
            warmUpCount WARMUPS
            invocationCount INVOCATIONS
            displayName(WITHOUT_PLUGIN_LABEL)
            invocation {
                // Increase client VM heap memory because of a huge amount of output events
                clientJvmArgs("-Xmx256m", "-Xms256m")
                args(*jobArgs)
                tasksToRun(*tasks)
                if (withFailure) {
                    expectFailure()
                }
                addBuildMutator { invocationSettings -> new InjectBuildScanPlugin(invocationSettings.projectDir, pluginVersionNumber) }
                addBuildMutator { invocationSettings -> new SaveScanSpoolFile(invocationSettings, scenario) }
                if (manageCacheState) {
                    addBuildMutator { new ManageLocalCacheState(it.projectDir) }
                }
            }
        }

        runner.buildSpec {
            warmUpCount WARMUPS
            invocationCount INVOCATIONS
            displayName(WITH_PLUGIN_LABEL)
            invocation {
                // Increase client VM heap memory because of a huge amount of output events
                clientJvmArgs("-Xmx256m", "-Xms256m")
                args(*jobArgs)
                args("-DenableScan=true")
                tasksToRun(*tasks)
                if (withFailure) {
                    expectFailure()
                }
                addBuildMutator { invocationSettings -> new InjectBuildScanPlugin(invocationSettings.projectDir, pluginVersionNumber) }
                addBuildMutator { invocationSettings -> new SaveScanSpoolFile(invocationSettings, scenario) }
                if (manageCacheState) {
                    addBuildMutator { new ManageLocalCacheState(it.projectDir) }
                }
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
        scenario                                                | expectedMedianPercentageShift | tasks                              | withFailure | scenarioArgs                                                  | manageCacheState
        "clean build - 50 projects"                             | MEDIAN_PERCENTAGES_SHIFT      | ['clean', 'build']                 | true        | ['--build-cache']                                             | true
        "clean build - 20 projects - slow tasks - less console" | MEDIAN_PERCENTAGES_SHIFT      | ['clean', 'project20:buildNeeded'] | true        | ['--build-cache', '-DreducedOutput=true', '-DslowTasks=true'] | true
        "help"                                                  | MEDIAN_PERCENTAGES_SHIFT      | ['help']                           | false       | []                                                            | false
        "help - no console output"                              | MEDIAN_PERCENTAGES_SHIFT      | ['help']                           | false       | ['-DreducedOutput=true']                                      | false
    }

    static class ManageLocalCacheState implements BuildMutator {
        final File projectDir

        ManageLocalCacheState(File projectDir) {
            this.projectDir = projectDir
        }

        @Override
        void beforeBuild(BuildContext context) {
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
        void afterBuild(BuildContext context, Throwable t) {
            assert !new File(projectDir, 'error.log').exists()
            def buildCacheDirectory = new TestFile(projectDir, 'local-build-cache')
            def cacheEntries = buildCacheDirectory.listFiles()
            if (cacheEntries == null) {
                throw new IllegalStateException("Cache dir doesn't exist, did the build succeed? Please check the build log.")
            }

            cacheEntries.sort().eachWithIndex { TestFile entry, int i ->
                if (i % 2 == 0) {
                    entry.delete()
                }
            }
        }
    }

    static class SaveScanSpoolFile implements BuildMutator {
        final InvocationSettings invocationSettings
        final String testId

        SaveScanSpoolFile(InvocationSettings invocationSettings, String testId) {
            this.invocationSettings = invocationSettings
            this.testId = testId.replaceAll(/[- ]/, '_')
        }

        @Override
        void beforeBuild(BuildContext context) {
            spoolDir().deleteDir()
        }

        @Override
        void afterBuild(BuildContext context, Throwable t) {
            def spoolDir = this.spoolDir()
            if (context.phase == Phase.MEASURE && (context.iteration == invocationSettings.buildCount) && spoolDir.exists()) {
                def targetDirectory = new File("build/scan-dumps/$testId")
                targetDirectory.deleteDir()
                FileUtils.moveToDirectory(spoolDir, targetDirectory, true)
            }
        }

        private File spoolDir() {
            new File(invocationSettings.gradleUserHome, "build-scan-data")
        }
    }

    static class InjectBuildScanPlugin implements BuildMutator {
        final File projectDir
        final String buildScanPluginVersion

        InjectBuildScanPlugin(File projectDir, String buildScanPluginVersion) {
            this.projectDir = projectDir
            this.buildScanPluginVersion = buildScanPluginVersion
            println "InjectBuildScanPlugin buildScanPluginVersion = $buildScanPluginVersion"
        }

        @Override
        void beforeScenario(ScenarioContext context) {
            def projectTestDir = new TestFile(projectDir)
            def settingsScript = projectTestDir.file('settings.gradle')
            settingsScript.text = """
                    buildscript {
                        repositories {
                            maven {
                                name 'gradleInternalRepository'
                                url '${System.getenv("GRADLE_INTERNAL_REPO_URL")}/enterprise-libs-snapshots-local/'
                                credentials {
                                    username = System.getenv("GRADLE_INTERNAL_REPO_USERNAME")
                                    password = System.getenv("GRADLE_INTERNAL_REPO_PASSWORD")
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
