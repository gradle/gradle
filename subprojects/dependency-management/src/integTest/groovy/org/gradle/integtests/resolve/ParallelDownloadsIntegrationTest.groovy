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

package org.gradle.integtests.resolve

import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest

import javax.servlet.http.HttpServletRequest
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class ParallelDownloadsIntegrationTest extends AbstractHttpDependencyResolutionTest {

    private final AtomicInteger concurrentRequests = new AtomicInteger()
    protected volatile int maxConcurrentRequests
    protected Closure<Boolean> acceptURI

    def setup() {
        executer.withArguments('--max-workers', '4')
        // make sure we serve at least 2 requests concurrently
        // and if we don't, the timeout will unblock consumers, and the test should fail
        def barrier = new CyclicBarrier(2)
        server.beforeHandle { HttpServletRequest request ->
            if (acceptURI(request.requestURI)) {
                barrier.await(20, TimeUnit.SECONDS)
                maxConcurrentRequests = Math.max(maxConcurrentRequests, concurrentRequests.incrementAndGet())
            }
        }
        server.afterHandle { HttpServletRequest request ->
            if (acceptURI(request.requestURI)) {
                concurrentRequests.decrementAndGet()
            }
        }
    }

    def "downloads artifacts in parallel"() {
        countParallelArtifactDownloads()
        given:
        buildFile << """
            allprojects {
                repositories {
                    maven { url '$mavenHttpRepo.uri' }
                }
                configurations {
                    compile
                }
            }
            
            dependencies {
        """
        (1..8).each {
            def module = mavenHttpRepo.module("test", "test$it", "1.0").publish()
            module.pom.expectGet()
            module.artifact.expectGet()
            buildFile << "               compile 'test:test$it:1.0'\n"
        }

        buildFile << """
            }
            
            task resolve {
                doLast {
                    println configurations.compile.files
                }
            }
"""

        when:
        run 'resolve'

        then:
        noExceptionThrown()
        println "Max concurrent requests: $maxConcurrentRequests"
        maxConcurrentRequests>1
    }

    private void countParallelArtifactDownloads() {
        acceptURI = { String uri ->
            uri.endsWith('.jar')
        }
    }
}
