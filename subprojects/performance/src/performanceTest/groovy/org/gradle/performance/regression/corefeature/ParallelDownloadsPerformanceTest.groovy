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

package org.gradle.performance.regression.corefeature

import org.gradle.performance.AbstractCrossVersionPerformanceTest
import org.gradle.performance.WithExternalRepository
import org.gradle.performance.fixture.BuildExperimentInvocationInfo
import org.gradle.performance.fixture.BuildExperimentListener
import org.gradle.performance.fixture.BuildExperimentListenerAdapter
import org.gradle.performance.measure.MeasuredOperation
import org.junit.Ignore
import org.mortbay.jetty.Handler
import org.mortbay.jetty.servlet.Context
import org.mortbay.jetty.webapp.WebAppContext

import javax.servlet.Filter
import javax.servlet.FilterChain
import javax.servlet.FilterConfig
import javax.servlet.ServletException
import javax.servlet.ServletRequest
import javax.servlet.ServletResponse
import javax.servlet.http.HttpServletRequest
import java.util.concurrent.atomic.AtomicInteger

@Ignore("These tests are dedicated to test external repositories")
class ParallelDownloadsPerformanceTest extends AbstractCrossVersionPerformanceTest implements WithExternalRepository {
    private final static String TEST_PROJECT_NAME = 'springBootApp'

    File tmpRepoDir = temporaryFolder.createDir('repository')

    @Override
    File getRepoDir() {
        tmpRepoDir
    }

    def setup() {
        runner.targetVersions = ["4.9-20180620235919+0000"]
        runner.warmUpRuns = 5
        runner.runs = 15
        runner.addBuildExperimentListener(new BuildExperimentListenerAdapter() {
            @Override
            void afterInvocation(BuildExperimentInvocationInfo invocationInfo, MeasuredOperation operation, BuildExperimentListener.MeasurementCallback measurementCallback) {
                cleanupCache(invocationInfo.gradleUserHome)
            }

            private void cleanupCache(File userHomeDir) {
                ['modules-2', 'external-resources'].each {
                    new File("$userHomeDir/caches/$it").deleteDir()
                }
            }
        })
    }

    def "resolves dependencies from external repository"() {
        runner.testProject = TEST_PROJECT_NAME
        startServer()

        given:
        runner.tasksToRun = ['resolveDependencies']
        runner.gradleOpts = ["-Xms1g", "-Xmx1g"]
        runner.args = ['-I', 'init.gradle', "-PmirrorPath=${repoDir.absolutePath}", "-PmavenRepoURL=http://localhost:${serverPort}/"]

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        cleanup:
        stopServer()
    }

    def "resolves dependencies from external repository (parallel)"() {
        runner.testProject = TEST_PROJECT_NAME
        startServer()

        given:
        runner.tasksToRun = ['resolveDependencies']
        runner.gradleOpts = ["-Xms1g", "-Xmx1g"]
        runner.args = ['-I', 'init.gradle', "-PmirrorPath=${repoDir.absolutePath}", "-PmavenRepoURL=http://localhost:${serverPort}/", '--parallel']

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        cleanup:
        stopServer()
    }


    @Override
    Context createContext() {
        def context = new WebAppContext()
        context.addFilter(SimulatedDownloadLatencyFilter, '/*', Handler.DEFAULT)
        context
    }

    static class SimulatedDownloadLatencyFilter implements Filter {
        // don't put those numbers too high, or the test is going to be really slow for each iteration!
        private final static int[] DELAYS = [2, 3, 5, 8, 13, 21, 34, 55]

        private final static boolean LOG = false
        private final static Map<String, Integer> FACTORS = [
            'jar': 10,
            'xml': 3,
            'pom': 3
        ].withDefault { 1 }

        private final AtomicInteger concurrency = new AtomicInteger()

        @Override
        void init(FilterConfig filterConfig) throws ServletException {

        }

        @Override
        void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
            HttpServletRequest httpRequest = (HttpServletRequest) request
            def path = httpRequest.servletPath
            // compute a digest based on the path, allowing us to use the same latency for the same request each time
            int sig = DELAYS[path.bytes.sum() % DELAYS.length]
            def ext = path.contains('.') ? path.substring(1 + path.lastIndexOf('.')) : ''
            int latency = sig * FACTORS[ext]
            def file = path.substring(path.lastIndexOf('/'))
            if (LOG) {
                println "File ${file} : ${latency}ms - concurrent requests: ${concurrency.incrementAndGet()}"
            }
            if (latency) {
                sleep latency
            }
            concurrency.decrementAndGet()
            chain.doFilter(request, response)
        }

        @Override
        void destroy() {

        }
    }

}
