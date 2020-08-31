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

import org.gradle.performance.fixture.BuildExperimentInvocationInfo
import org.gradle.performance.fixture.BuildExperimentListener
import org.gradle.performance.fixture.GradleInvocationSpec
import org.gradle.performance.fixture.InvocationCustomizer
import org.gradle.performance.fixture.InvocationSpec
import org.gradle.test.fixtures.keystore.TestKeyStore
import spock.lang.Unroll

import static org.gradle.performance.fixture.BuildExperimentRunner.Phase.MEASUREMENT
import static org.gradle.performance.fixture.BuildExperimentRunner.Phase.WARMUP
import static org.gradle.performance.regression.buildcache.TaskOutputCachingJavaPerformanceTest.getScenarios
import static org.gradle.performance.regression.buildcache.TaskOutputCachingJavaPerformanceTest.setupTestProject
import static org.gradle.performance.regression.buildcache.TaskOutputCachingJavaPerformanceTest.touchCacheArtifactsDir

// Gradle profiler doesn't support different parameters per invocation, so we extract the single method here
@Unroll
class HttpsTaskOutputCachingJavaPerformanceTest extends AbstractTaskOutputCachingPerformanceTest {

    def setup() {
        runner.warmUpRuns = 11
        runner.runs = 21
        runner.minimumBaseVersion = "3.5"
        runner.targetVersions = ["6.7-20200804220106+0000"]
    }

    def "clean #tasks on #testProject with remote https cache"() {
        setupTestProject(runner, testProject, tasks)
        firstWarmupWithCache = 2 // Do one run without the cache to populate the dependency cache from maven central
        protocol = "https"
        pushToRemote = true
        runner.addBuildExperimentListener(cleanLocalCache())
        runner.addBuildExperimentListener(touchCacheArtifacts())

        def keyStore = TestKeyStore.init(temporaryFolder.file('ssl-keystore'))
        keyStore.enableSslWithServerCert(buildCacheServer)

        runner.addInvocationCustomizer(new InvocationCustomizer() {
            @Override
            <T extends InvocationSpec> T customize(BuildExperimentInvocationInfo invocationInfo, T invocationSpec) {
                GradleInvocationSpec gradleInvocation = invocationSpec as GradleInvocationSpec
                if (isRunWithCache(invocationInfo)) {
                    gradleInvocation.withBuilder().gradleOpts(*keyStore.serverAndClientCertArgs).build() as T
                } else {
                    gradleInvocation.withBuilder()
                    // We need a different daemon for the other runs because of the certificate Gradle JVM args
                    // so we disable the daemon completely in order not to confuse the performance test
                        .useDaemon(false)
                    // We run one iteration without the cache to download artifacts from Maven central.
                    // We can't download with the cache since we set the trust store and Maven central uses https.
                        .args("--no-build-cache")
                        .build() as T
                }
            }
        })

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        [testProject, tasks] << scenarios
    }

    private BuildExperimentListener touchCacheArtifacts() {
        new BuildExperimentListener() {
            @Override
            void beforeInvocation(BuildExperimentInvocationInfo invocationInfo) {
                touchCacheArtifactsDir(cacheDir)
                if (buildCacheServer.running) {
                    touchCacheArtifactsDir(buildCacheServer.cacheDir)
                }
            }
        }
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
}
