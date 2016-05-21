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
import org.gradle.integtests.fixtures.executer.ExecutionFailure
import org.gradle.integtests.fixtures.executer.GradleHandle
import org.gradle.launcher.daemon.server.health.DaemonStatus
import org.gradle.test.fixtures.ConcurrentTestUtil
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
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
        leakRate = 3800

        then:
        !daemonIsExpiredEagerly()
    }

    def "when leak occurs while daemon is idle daemon is still expired"() {
        // This is so the idle timeout expiration strategy doesn't kick in
        // before the gc monitoring expires the daemon
        executer.withDaemonIdleTimeoutSecs(300)
        heapSize = "200m"
        leakRate = 1000

        when:
        leaksWhenIdle()
        executer.withArguments("-Dorg.gradle.daemon.healthcheckinterval=1000")
        executer.withBuildJvmOpts("-D${DaemonStatus.ENABLE_PERFORMANCE_MONITORING}=true", "-Xmx${heapSize}", "-Dorg.gradle.daemon.performance.logging=true")
        executer.noExtraLogging()
        run()

        then:
        daemons.daemon.assertIdle()

        and:
        String logText = daemons.daemon.log
        ConcurrentTestUtil.poll(30) {
            println daemons.daemon.log - logText
            logText = daemons.daemon.log
            daemons.daemon.assertStopped()
        }

        and:
        daemons.daemon.log.contains("Daemon stopping because JVM tenured space is exhausted")
    }

    @Requires([TestPrecondition.JDK7_OR_EARLIER, TestPrecondition.NOT_JDK_IBM])
    def "when build leaks permgen space daemon is expired"() {
        when:
        setupBuildScript = permGenLeak
        maxBuilds = 20
        heapSize = "200m"
        leakRate = 3300

        then:
        daemonIsExpiredEagerly()
    }

    def "detects a thrashing condition" () {
        // This is so the idle timeout expiration strategy doesn't kick in
        // before the gc monitoring expires the daemon
        executer.withDaemonIdleTimeoutSecs(300)
        heapSize = "200m"
        leakRate = 1700

        when:
        leaksWithinOneBuild()
        executer.withArguments("-Dorg.gradle.daemon.healthcheckinterval=1000", "--debug")
        executer.withBuildJvmOpts("-D${DaemonStatus.ENABLE_PERFORMANCE_MONITORING}=true", "-Xmx${heapSize}", "-Dorg.gradle.daemon.performance.logging=true")
        executer.noExtraLogging()
        GradleHandle gradle = executer.start()

        then:
        ConcurrentTestUtil.poll(10) {
            daemons.daemon.assertBusy()
        }

        when:
        file("leak").createFile()

        then:
        ConcurrentTestUtil.poll(60) {
            daemons.daemon.assertStopped()
        }

        and:
        daemons.daemon.log.contains("Daemon stopping immediately because garbage collector is starting to thrash")

        when:
        ExecutionFailure failure = gradle.waitForFailure()

        then:
        failure.assertOutputContains("Gradle build daemon has been stopped: garbage collector is starting to thrash")
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

    private final Closure leaksWhenIdle = {
        buildFile << """
            class State {
                static int x
                static map = [:]
            }
            State.x++

            new Thread().start {
                while (true) {
                    logger.warn "leaking some heap"

                    //simulate normal collectible objects
                    5000.times {
                        State.map.put(it, "foo" * ${leakRate})
                    }

                    //simulate the leak
                    1000.times {
                        State.map.put(UUID.randomUUID(), "foo" * ${leakRate})
                    }
                    sleep(750)
                }
            }
        """
    }

    private final Closure permGenLeak = {
        leakRate.times {
            file("buildSrc/src/main/java/Generated${it}.java") << """
                public class Generated${it} { }
            """
        }
        buildFile << """
            import java.net.URLClassLoader

            class State {
                static int x
                static map = [:]
            }
            State.x++

            //simulate normal perm gen usage
            5.times {
                ClassLoader classLoader1 = new URLClassLoader(buildscript.classLoader.URLs)
                ${leakRate}.times {
                    classLoader1.loadClass("Generated\${it}")
                }
                State.map.put("CL${it}", classLoader1)
            }

            //simulate the leak
            ClassLoader classLoader2 = new URLClassLoader(buildscript.classLoader.URLs)
            ${leakRate}.times {
                classLoader2.loadClass("Generated\${it}")
            }
            State.map.put(UUID.randomUUID(), classLoader2)

            println "Build: " + State.x
        """
    }

    private final Closure leaksWithinOneBuild = {
        buildFile << """
            def map = [:]

            while (true) {
                if (file("leak").exists()) {
                    logger.debug "leaking some heap"
                    //simulate normal collectible objects
                    10000.times {
                        map.put(it, "foo" * ${leakRate})
                    }

                    //simulate the leak
                    1000.times {
                        map.put(UUID.randomUUID(), "foo" * ${leakRate})
                    }
                } else {
                    logger.warn "waiting for leak to start"
                }
                sleep 1000
            }
        """
    }
}
