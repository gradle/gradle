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

package org.gradle.performance.experiment.buildcache

import groovy.transform.Canonical
import org.gradle.performance.AbstractCrossBuildPerformanceTest
import org.gradle.performance.categories.PerformanceExperiment
import org.gradle.performance.fixture.BuildExperimentListenerAdapter
import org.gradle.performance.fixture.BuildExperimentSpec
import org.gradle.performance.measure.Amount
import org.gradle.performance.results.MeasuredOperationList
import org.gradle.test.fixtures.file.TestFile
import org.junit.experimental.categories.Category
import spock.lang.Unroll

import static org.gradle.integtests.tooling.fixture.TextUtil.escapeString
import static org.gradle.performance.generator.JavaTestProject.LARGE_JAVA_MULTI_PROJECT
import static org.gradle.performance.generator.JavaTestProject.LARGE_MONOLITHIC_JAVA_PROJECT

@Category(PerformanceExperiment)
class LocalTaskOutputCacheCrossBuildPerformanceTest extends AbstractCrossBuildPerformanceTest {

    @Unroll
    def "#tasks on #testProject with local cache (build comparison)"() {
        def noPushInitScript = temporaryFolder.file("no-push.gradle")
        noPushInitScript << """
            settingsEvaluated { settings ->
                settings.buildCache {
                    local {
                        push = false
                    }
                }
            }
        """.stripIndent()
        def cacheDir = temporaryFolder.file("local-cache")
        def deleteLocalCacheInitScript = temporaryFolder.file("delete-local-cache.gradle")
        deleteLocalCacheInitScript << """
            rootProject {
                task cleanBuildCache(type: Delete) {
                    delete(file("${escapeString(cacheDir.absolutePath)}"))
                }
            }
        """.stripIndent()

        when:
        runner.buildExperimentListener = new BuildExperimentListenerAdapter() {
            @Override
            void beforeExperiment(BuildExperimentSpec experimentSpec, File projectDir) {
                cacheDir.deleteDir().mkdirs()
                def settingsFile = new TestFile(projectDir).file('settings.gradle')
                settingsFile << """
                    buildCache {
                        local {
                            directory = '${cacheDir.absoluteFile.toURI()}'
                        }
                    }
                """.stripIndent()
            }
        }
        runner.testGroup = "task output cache"
        runner.buildSpec {
            projectName(testProject.projectName).displayName("always-miss pull-only cache").invocation {
                tasksToRun(tasks.split(' ')).cleanTasks("clean").gradleOpts("-Xms${testProject.daemonMemory}", "-Xmx${testProject.daemonMemory}").useDaemon().args(
                    "--build-cache",
                    "--init-script", escapeString(noPushInitScript.absolutePath))
            }
        }
        runner.buildSpec {
            projectName(testProject.projectName).displayName("fully cached").invocation {
                tasksToRun(tasks.split(' ')).cleanTasks("clean").gradleOpts("-Xms${testProject.daemonMemory}", "-Xmx${testProject.daemonMemory}").useDaemon().args(
                    "--build-cache")
            }
        }
        runner.buildSpec {
            projectName(testProject.projectName).displayName("push-only").invocation {
                tasksToRun(tasks.split(' ')).cleanTasks("clean", "cleanBuildCache").gradleOpts("-Xms${testProject.daemonMemory}", "-Xmx${testProject.daemonMemory}").useDaemon().args(
                    "--build-cache",
                    "--init-script", escapeString(deleteLocalCacheInitScript.absolutePath))
            }
        }
        runner.baseline {
            projectName(testProject.projectName).displayName("fully up-to-date").invocation {
                tasksToRun(tasks.split(' ')).gradleOpts("-Xms${testProject.daemonMemory}", "-Xmx${testProject.daemonMemory}").useDaemon()
            }
        }
        runner.baseline {
            projectName(testProject.projectName).displayName("non-cached").invocation {
                tasksToRun(tasks.split(' ')).cleanTasks('clean').gradleOpts("-Xms${testProject.daemonMemory}", "-Xmx${testProject.daemonMemory}").useDaemon()
            }
        }

        def results = runner.run()

        then:

        def buildResults = [:].withDefault { String key ->
            results.buildResult(results.builds.find { it.displayName.contains(key) })
        }

        def fullyCached = buildResults['fully cached']
        def alwaysMissPullOnly = buildResults['always-miss']
        def pushOnly = buildResults['push-only']
        def fullyUpToDate = buildResults['fully up-to-date']
        def nonCached = buildResults['non-cached']

        Overhead.of('checking remote cache', nonCached, alwaysMissPullOnly).assertWithinPerc(2.5)
        Overhead.of('pushing to cache', nonCached, pushOnly).assertWithinPerc(15)
        println Overhead.of('fetching from cache', fullyUpToDate, fullyCached)

        where:
        testProject                   | tasks
        LARGE_MONOLITHIC_JAVA_PROJECT | "assemble"
        LARGE_JAVA_MULTI_PROJECT      | "assemble"
    }

    @Canonical
    private static class Overhead {
        String label
        MeasuredOperationList baseline
        MeasuredOperationList series

        static Overhead of(String label, MeasuredOperationList baseline, MeasuredOperationList series) {
            new Overhead(label, baseline, series)
        }

        Amount overhead() {
            timeOf(series) - timeOf(baseline)
        }

        private Amount timeOf(MeasuredOperationList measurement) {
            measurement.totalTime.average
        }

        void assertWithinPerc(double maxPerc) {
            def baseUnits = baseline.totalTime.average.units
            double maxOverhead = timeOf(baseline).value * maxPerc / 100d
            double overhead = overhead().toUnits(baseUnits).value
            double overheadPc = overhead/timeOf(baseline).value * 100d
            assert overhead<maxOverhead:"Max overhead for $label (${overheadPc.round(1)}%) exceeds ${maxPerc.round(1)}% : ${this.overhead()}"
            println "Max overhead for $label (${overheadPc.round(1)}%) exceeds ${maxPerc.round(1)}% : ${this.overhead()}"
        }

        String toString() {
            def baseUnits = baseline.totalTime.average.units
            double overhead = overhead().toUnits(baseUnits).value
            double overheadPc = overhead/timeOf(baseline).value * 100d
            println "Overhead for $label is ${this.overhead()} (${overheadPc.round(1)}%)"
        }
    }

}
