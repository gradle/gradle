/*
 * Copyright 2014 the original author or authors.
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



package org.gradle.launcher.daemon

import org.gradle.integtests.fixtures.daemon.DaemonIntegrationSpec
import org.gradle.launcher.daemon.server.health.DaemonStatus
import spock.lang.Ignore
import spock.lang.Unroll

class DaemonPerformanceMonitoringIntegrationTest extends DaemonIntegrationSpec {
    int maxBuilds
    String heapSize
    int leakRate
    Closure setupBuildScript

    @Ignore @Unroll
    def "when build leaks quickly daemon is expired eagerly (#heap heap)"() {
        when:
        setupBuildScript = tenuredHeapLeak
        maxBuilds = builds
        heapSize = heap
        leakRate = rate

        then:
        daemonIsExpiredEagerly()

        where:
        builds | heap    | rate
        10     | "200m"  | 3000
        10     | "1024m" | 15000
    }

    @Unroll
    def "when build leaks slowly daemon is eventually expired (#heap heap)"() {
        when:
        setupBuildScript = tenuredHeapLeak
        maxBuilds = builds
        heapSize = heap
        leakRate = rate

        then:
        daemonIsExpiredEagerly()

        where:
        builds | heap    | rate
        40     | "200m"  | 800
        40     | "1024m" | 4000
    }

    def "when build leaks within available memory the daemon is not expired"() {
        when:
        setupBuildScript = tenuredHeapLeak
        maxBuilds = 20
        heapSize = "500m"
        leakRate = 300

        then:
        !daemonIsExpiredEagerly()
    }

    def "greedy build with no leak does not expire daemon"() {
        when:
        setupBuildScript = greedyBuildNoLeak
        maxBuilds = 20
        heapSize = "200m"
        leakRate = 4000

        then:
        !daemonIsExpiredEagerly()
    }

    private boolean daemonIsExpiredEagerly() {
        def dataFile = file("stats")
        setupBuildScript()
        int newDaemons = 0
        try {
            for (int i = 0; i < maxBuilds; i++) {
                executer.noExtraLogging()
                executer.withBuildJvmOpts("-D${DaemonStatus.ENABLE_PERFORMANCE_MONITORING}=true", "-Xmx${heapSize}", "-Dorg.gradle.daemon.performance.logging=true")
                def r = run()
                if (r.output.contains("Starting build in new daemon [memory: ")) {
                    newDaemons++;
                }
                if (newDaemons > 1) {
                    return true
                }
                def lines = r.output.readLines()
                dataFile << lines[lines.findLastIndexOf { it.startsWith "Starting" }]
                dataFile << "  " + lines[lines.findLastIndexOf { it.contains "Total time:" }]
                dataFile << "\n"
            }
        } finally {
            println dataFile.text
        }
        return false
    }

    private final Closure tenuredHeapLeak = {
        buildFile << """
            class State {
                static int x
                static map = [:]
            }
            State.x++

            //simulate normal collectible objects
            5000.times {
                State.map.put(it, "foo" * ${leakRate})
            }

            //simulate the leak
            1000.times {
                State.map.put(UUID.randomUUID(), "foo" * ${leakRate})
            }

            println "Build: " + State.x
        """
    }

    private final Closure greedyBuildNoLeak = {
        buildFile << """
            Map map = [:]

            //simulate normal collectible objects
            5000.times {
                map.put(it, "foo" * ${leakRate})
            }
        """
    }
}
