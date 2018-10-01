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

import org.gradle.performance.AbstractCrossBuildPerformanceTest
import org.gradle.performance.categories.PerformanceExperiment
import org.gradle.performance.fixture.BuildExperimentListenerAdapter
import org.gradle.performance.fixture.BuildExperimentSpec
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

        given:
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

        when:
        def results = runner.run()

        then:
        results

        where:
        testProject                   | tasks
        LARGE_MONOLITHIC_JAVA_PROJECT | "assemble"
        LARGE_JAVA_MULTI_PROJECT      | "assemble"
    }

}
