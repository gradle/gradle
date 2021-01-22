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

import org.eclipse.jetty.webapp.WebAppContext
import org.gradle.performance.AbstractCrossVersionPerformanceTest
import org.gradle.performance.WithExternalRepository
import org.gradle.performance.annotations.RunFor
import org.gradle.performance.annotations.Scenario
import org.gradle.profiler.BuildContext
import org.gradle.profiler.BuildMutator

import javax.servlet.DispatcherType
import javax.servlet.Filter
import javax.servlet.FilterChain
import javax.servlet.FilterConfig
import javax.servlet.ServletException
import javax.servlet.ServletRequest
import javax.servlet.ServletResponse
import javax.servlet.http.HttpServletRequest
import java.util.concurrent.atomic.AtomicInteger

import static org.gradle.performance.annotations.ScenarioType.PER_DAY
import static org.gradle.performance.results.OperatingSystem.LINUX

@RunFor(
    @Scenario(type = PER_DAY, operatingSystems = [LINUX], testProjects = ["springBootApp"])
)
class ParallelDownloadsPerformanceTest extends AbstractCrossVersionPerformanceTest implements WithExternalRepository {
    File tmpRepoDir = temporaryFolder.createDir('repository')

    @Override
    File getRepoDir() {
        tmpRepoDir
    }

    def setup() {
        runner.targetVersions = ["7.0-20210122131800+0000"]
        // Example project requires TaskContainer.register
        runner.minimumBaseVersion = "4.9"
        runner.warmUpRuns = 5
        runner.runs = 15
        runner.addBuildMutator { invocationSettings ->
            new BuildMutator() {
                @Override
                void afterBuild(BuildContext context, Throwable error) {
                    cleanupCache(invocationSettings.gradleUserHome)
                }

                private void cleanupCache(File userHomeDir) {
                    ['modules-2', 'external-resources'].each {
                        new File("$userHomeDir/caches/$it").deleteDir()
                    }
                }
            }
        }
    }

    def "resolves dependencies from external repository"() {
        startServer()

        given:
        runner.tasksToRun = ['resolveDependencies']
        runner.args = ['-I', 'init.gradle', "-PmirrorPath=${repoDir.absolutePath}", "-PmavenRepoURL=http://127.0.0.1:${serverPort}/", "-Dorg.gradle.parallel=false"]

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        cleanup:
        stopServer()
    }

    def "resolves dependencies from external repository (parallel)"() {
        startServer()

        given:
        runner.tasksToRun = ['resolveDependencies']
        runner.args = ['-I', 'init.gradle', "-PmirrorPath=${repoDir.absolutePath}", "-PmavenRepoURL=http://127.0.0.1:${serverPort}/", '--parallel']

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        cleanup:
        stopServer()
    }


    @Override
    WebAppContext createContext() {
        def context = new WebAppContext()
        context.addFilter(SimulatedDownloadLatencyFilter, '/*', EnumSet.of(DispatcherType.REQUEST))
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

