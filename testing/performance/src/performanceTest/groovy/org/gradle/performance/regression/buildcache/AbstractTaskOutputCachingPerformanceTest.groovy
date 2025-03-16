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
import org.gradle.performance.AbstractCrossVersionPerformanceTest
import org.gradle.profiler.BuildContext
import org.gradle.profiler.BuildMutator
import org.gradle.profiler.InvocationSettings
import org.gradle.profiler.Phase
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.server.http.HttpBuildCacheServer
import org.junit.Rule

class AbstractTaskOutputCachingPerformanceTest extends AbstractCrossVersionPerformanceTest {
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
        runner.addBuildMutator { invocationSettings ->
            new BuildMutator() {
                @Override
                void beforeBuild(BuildContext context) {
                    if (isRunWithCache(context)) {
                        if (!buildCacheServer.isRunning()) {
                            buildCacheServer.start()
                        }
                        def settings = new TestFile(invocationSettings.projectDir).file('settings.gradle')
                        if (isFirstRunWithCache(context)) {
                            cacheDir.deleteDir().mkdirs()
                            buildCacheServer.cacheDir.deleteDir().mkdirs()
                            settings << remoteCacheSettingsScript
                        }
                        assert buildCacheServer.uri.toString().startsWith("${protocol}://")
                        assert settings.text.contains(buildCacheServer.uri.toString())
                    }
                }

                @Override
                void afterBuild(BuildContext context, Throwable error) {
                    if (isLastRun(context, invocationSettings) && checkIfCacheUsed) {
                        assert !(buildCacheServer.cacheDir.allDescendants().empty && cacheDir.allDescendants().isEmpty())
                        assert pushToRemote || buildCacheServer.cacheDir.allDescendants().empty
                    }
                }
            }
        }
    }

    static boolean isLastRun(BuildContext context, InvocationSettings invocationSettings) {
        context.iteration == invocationSettings.buildCount && context.phase == Phase.MEASURE
    }

    boolean isRunWithCache(BuildContext context) {
        context.iteration >= firstWarmupWithCache || context.phase == Phase.MEASURE
    }

    boolean isFirstRunWithCache(BuildContext context) {
        context.iteration == firstWarmupWithCache && context.phase == Phase.WARM_UP
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

    BuildMutator cleanLocalCache() {
        new BuildMutator() {
            @Override
            void beforeBuild(BuildContext context) {
                cacheDir.deleteDir().mkdirs()
            }
        }
    }

    BuildMutator cleanRemoteCache() {
        new BuildMutator() {
            @Override
            void beforeBuild(BuildContext context) {
                buildCacheServer.cacheDir.deleteDir().mkdirs()
            }
        }
    }
}
