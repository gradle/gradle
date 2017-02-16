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

package org.gradle.performance.java

import org.gradle.performance.fixture.BuildExperimentInvocationInfo
import org.gradle.performance.fixture.BuildExperimentListenerAdapter
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.keystore.TestKeyStore
import org.gradle.test.fixtures.server.http.HttpBuildCache
import org.junit.Rule
import spock.lang.Unroll

@Unroll
class HttpTaskOutputCacheJavaPerformanceTest extends AbstractTaskOutputCacheJavaPerformanceTest {

    @Rule
    public HttpBuildCache buildCache = new HttpBuildCache(tmpDir)

    def setup() {
        runner.addBuildExperimentListener(new BuildExperimentListenerAdapter() {
            @Override
            void beforeInvocation(BuildExperimentInvocationInfo invocationInfo) {
                if (!buildCache.isRunning()) {
                    buildCache.start()
                }
                new TestFile(invocationInfo.projectDir).file('settings.gradle') << remoteCacheSettingsScript
            }
        })
    }

    def "Builds '#testProject' calling #tasks with remote http cache"(String testProject, List<String> tasks) {
        runner.testId = "cached ${tasks.join(' ')} $testProject project - remote http cache"
        runner.testProject = testProject
        runner.tasksToRun = tasks

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        [testProject, tasks] << scenarios
    }

    def "Builds '#testProject' calling #tasks with remote https cache"(String testProject, List<String> tasks) {
        runner.testId = "cached ${tasks.join(' ')} $testProject project - remote https cache"
        runner.testProject = testProject
        runner.tasksToRun = tasks

        def keyStore = TestKeyStore.init(tmpDir.file('ssl-keystore'))
        keyStore.enableSslWithServerCert(buildCache)
        runner.args = runner.args + keyStore.serverAndClientCertArgs

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        [testProject, tasks] << scenarios
    }

    private String getRemoteCacheSettingsScript() {
        """                                
            if (GradleVersion.current() > GradleVersion.version('3.4')) {
                def httpCacheClass = Class.forName('org.gradle.caching.http.HttpBuildCache')
                buildCache {
                    remote(httpCacheClass) {
                        url = '${buildCache.uri}/'
                    }
                }
            } else {
                def httpCacheClass = Class.forName('org.gradle.caching.http.internal.HttpBuildCacheFactory')
                gradle.buildCache.useCacheFactory(
                    httpCacheClass.getConstructor(URI.class).newInstance(
                        new URI('${buildCache.uri}/')
                    )
                )
            }
        """.stripIndent()
    }
}
