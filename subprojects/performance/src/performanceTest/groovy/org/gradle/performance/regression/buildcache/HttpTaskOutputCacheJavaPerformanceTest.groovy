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
import org.gradle.performance.measure.MeasuredOperation
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.keystore.TestKeyStore
import org.gradle.test.fixtures.server.http.HttpBuildCache
import org.junit.Rule
import spock.lang.Unroll

@Unroll
class HttpTaskOutputCacheJavaPerformanceTest extends AbstractTaskOutputCacheJavaPerformanceTest {

    @Rule
    public HttpBuildCache buildCache = new HttpBuildCache(temporaryFolder)
    private String protocol

    def setup() {
        buildCache.logRequests = false
        runner.addBuildExperimentListener(new BuildExperimentListener() {
            @Override
            void beforeInvocation(BuildExperimentInvocationInfo invocationInfo) {
                if (isRunWithCache(invocationInfo)) {
                    if (!buildCache.isRunning()) {
                        buildCache.start()
                    }
                    def settings = new TestFile(invocationInfo.projectDir).file('settings.gradle')
                    if (isFirstRunWithCache(invocationInfo)) {
                        buildCache.cacheDir.deleteDir().mkdirs()
                        settings << remoteCacheSettingsScript
                    }
                    assert buildCache.uri.toString().startsWith("${protocol}://")
                    assert settings.text.contains(buildCache.uri.toString())
                }
            }

            @Override
            void afterInvocation(BuildExperimentInvocationInfo invocationInfo, MeasuredOperation operation, BuildExperimentListener.MeasurementCallback measurementCallback) {
                if (isLastRun(invocationInfo)) {
                    assert !buildCache.cacheDir.allDescendants().empty
                }
            }
        })
    }

    def "clean #tasks on #testProject with remote http cache"() {
        runner.testProject = testProject
        runner.gradleOpts = ["-Xms${testProject.daemonMemory}", "-Xmx${testProject.daemonMemory}"]
        runner.tasksToRun = tasks.split(' ')
        protocol = "http"

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        [testProject, tasks] << scenarios
    }

    def "clean #tasks on #testProject with remote https cache"() {
        runner.testProject = testProject
        runner.gradleOpts = ["-Xms${testProject.daemonMemory}", "-Xmx${testProject.daemonMemory}"]
        runner.tasksToRun = tasks.split(' ')
        firstWarmupWithCache = 3 // Do one run without the cache to populate the dependency cache from maven central
        protocol = "https"

        def keyStore = TestKeyStore.init(temporaryFolder.file('ssl-keystore'))
        keyStore.enableSslWithServerCert(buildCache)

        runner.addInvocationCustomizer(new InvocationCustomizer() {
            @Override
            def <T extends InvocationSpec> T customize(BuildExperimentInvocationInfo invocationInfo, T invocationSpec) {
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

    private String getRemoteCacheSettingsScript() {
        """
            def httpCacheClass = Class.forName('org.gradle.caching.http.HttpBuildCache')
            buildCache {
                local {
                    enabled = false
                }
                remote(httpCacheClass) {
                    url = '${buildCache.uri}/' 
                    push = true
                }
            }
        """.stripIndent()
    }
}
