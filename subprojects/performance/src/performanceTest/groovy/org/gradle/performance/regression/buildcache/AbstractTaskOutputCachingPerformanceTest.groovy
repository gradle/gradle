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

package org.gradle.performance.regression.buildcache

import org.gradle.initialization.StartParameterBuildOptions
import org.gradle.performance.AbstractCrossVersionGradleInternalPerformanceTest
import org.gradle.performance.fixture.BuildExperimentInvocationInfo
import org.gradle.performance.fixture.BuildExperimentListener
import org.gradle.performance.fixture.BuildExperimentListenerAdapter
import org.gradle.performance.measure.MeasuredOperation
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.server.http.HttpBuildCacheServer
import org.junit.Rule

import static org.gradle.performance.fixture.BuildExperimentRunner.Phase.MEASUREMENT
import static org.gradle.performance.fixture.BuildExperimentRunner.Phase.WARMUP

class AbstractTaskOutputCachingPerformanceTest extends AbstractCrossVersionGradleInternalPerformanceTest{
    int firstWarmupWithCache = 1
    TestFile cacheDir
    String protocol = "http"
    boolean pushToRemote
    boolean checkIfCacheUsed = true

    @Rule
    HttpBuildCacheServer buildCacheServer = new HttpBuildCacheServer(temporaryFolder)

    def setup() {
        runner.cleanTasks = ["clean"]
        runner.args = ["-D${StartParameterBuildOptions.BuildCacheOption.GRADLE_PROPERTY}=true"]
        buildCacheServer.logRequests = false
        cacheDir = temporaryFolder.file("local-cache")
        runner.addBuildExperimentListener(new BuildExperimentListenerAdapter() {
            @Override
            void beforeInvocation(BuildExperimentInvocationInfo invocationInfo) {
                if (isRunWithCache(invocationInfo)) {
                    if (!buildCacheServer.isRunning()) {
                        buildCacheServer.start()
                    }
                    def settings = new TestFile(invocationInfo.projectDir).file('settings.gradle')
                    if (isFirstRunWithCache(invocationInfo)) {
                        cacheDir.deleteDir().mkdirs()
                        buildCacheServer.cacheDir.deleteDir().mkdirs()
                        settings << remoteCacheSettingsScript
                    }
                    assert buildCacheServer.uri.toString().startsWith("${protocol}://")
                    assert settings.text.contains(buildCacheServer.uri.toString())
                }
            }

            @Override
            void afterInvocation(BuildExperimentInvocationInfo invocationInfo, MeasuredOperation operation, BuildExperimentListener.MeasurementCallback measurementCallback) {
                if (isLastRun(invocationInfo) && checkIfCacheUsed) {
                    assert !(buildCacheServer.cacheDir.allDescendants().empty && cacheDir.allDescendants().isEmpty())
                    assert pushToRemote || buildCacheServer.cacheDir.allDescendants().empty
                }
            }
        })
    }

    static boolean isLastRun(BuildExperimentInvocationInfo invocationInfo) {
        invocationInfo.iterationNumber == invocationInfo.iterationMax && invocationInfo.phase == MEASUREMENT
    }

    boolean isRunWithCache(BuildExperimentInvocationInfo invocationInfo) {
        invocationInfo.iterationNumber >= firstWarmupWithCache || invocationInfo.phase == MEASUREMENT
    }

    boolean isFirstRunWithCache(BuildExperimentInvocationInfo invocationInfo) {
        invocationInfo.iterationNumber == firstWarmupWithCache && invocationInfo.phase == WARMUP
    }

    String getRemoteCacheSettingsScript() {
        """
            def httpCacheClass = Class.forName('org.gradle.caching.http.HttpBuildCache')
            buildCache {
                local {
                    directory = '${cacheDir.absoluteFile.toURI()}'
                }
                remote(httpCacheClass) {
                    url = '${buildCacheServer.uri}/' 
                    push = ${pushToRemote}
                }
            }
        """.stripIndent()
    }

    BuildExperimentListenerAdapter cleanLocalCache() {
        new BuildExperimentListenerAdapter() {
            @Override
            void beforeInvocation(BuildExperimentInvocationInfo invocationInfo) {
                cacheDir.deleteDir().mkdirs()
            }
        }
    }

    BuildExperimentListenerAdapter cleanRemoteCache() {
        new BuildExperimentListenerAdapter() {
            @Override
            void beforeInvocation(BuildExperimentInvocationInfo invocationInfo) {
                buildCacheServer.cacheDir.deleteDir().mkdirs()
            }
        }
    }

}
