/*
 * Copyright 2016 the original author or authors.
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

import org.gradle.integtests.fixtures.TargetCoverage
import org.gradle.integtests.fixtures.executer.ExecutionFailure
import org.gradle.integtests.fixtures.executer.GradleHandle
import org.gradle.launcher.daemon.fixtures.DaemonMultiJdkIntegrationTest
import org.gradle.launcher.daemon.fixtures.JdkVendor
import org.gradle.launcher.daemon.server.DaemonStateCoordinator
import org.gradle.launcher.daemon.server.api.DaemonStoppedException
import org.gradle.launcher.daemon.server.health.DaemonMemoryStatus
import org.gradle.launcher.daemon.server.health.GcThrashingDaemonExpirationStrategy
import org.gradle.soak.categories.SoakTest
import org.gradle.test.fixtures.ConcurrentTestUtil
import org.junit.experimental.categories.Category
import spock.lang.Ignore
import spock.lang.Unroll

import static org.junit.Assume.assumeTrue

@Category(SoakTest)
@TargetCoverage({DaemonPerformanceMonitoringCoverage.ALL_VERSIONS})
class DaemonPerformanceMonitoringSoakTest extends DaemonMultiJdkIntegrationTest {
    int maxBuilds
    String heapSize
    int leakRate
    Closure setupBuildScript

    def setup() {
        buildFile << "${logJdk()}"

        // Set JVM args for GC
        String jvmArgs = ""
        if (file('gradle.properties').exists()) {
            jvmArgs = file("gradle.properties").getProperties().getOrDefault("org.gradle.jvmargs", "")
        }
        file("gradle.properties").writeProperties(
            "org.gradle.java.home": jdk.javaHome.absolutePath,
            "org.gradle.jvmargs": jvmArgs + " " + version.gc.jvmArgs
        )
    }

    @Unroll
    def "when build leaks slowly daemon is eventually expired (heap: #heap)"() {
        when:
        setupBuildScript = tenuredHeapLeak
        maxBuilds = builds
        heapSize = heap
        leakRate = rate

        then:
        daemonIsExpiredEagerly()

        where:
        builds | heap    | rate
        45     | "200m"  | 600
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

    @Ignore
    def "when leak occurs while daemon is idle daemon is still expired"() {
        // This is so the idle timeout expiration strategy doesn't kick in
        // before the gc monitoring expires the daemon
        executer.withDaemonIdleTimeoutSecs(300)
        heapSize = "200m"
        leakRate = 900

        when:
        leaksWhenIdle()
        executer.withArguments("-Dorg.gradle.daemon.healthcheckinterval=1000")
        executer.withBuildJvmOpts("-D${DaemonMemoryStatus.ENABLE_PERFORMANCE_MONITORING}=true", "-Xms128m", "-Xmx${heapSize}", "-Dorg.gradle.daemon.performance.logging=true")
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
        daemons.daemon.log.contains(DaemonStateCoordinator.DAEMON_WILL_STOP_MESSAGE) || daemons.daemon.log.contains(DaemonStateCoordinator.DAEMON_STOPPING_IMMEDIATELY_MESSAGE)
    }

    @Ignore
    def "when build leaks permgen space daemon is expired"() {
        assumeTrue(version.vendor != JdkVendor.IBM)

        when:
        setupBuildScript = permGenLeak
        maxBuilds = 30
        heapSize = "200m"
        leakRate = 3700

        then:
        daemonIsExpiredEagerly()
    }

    @Ignore
    def "detects a thrashing condition" () {
        // This is so the idle timeout expiration strategy doesn't kick in
        // before the gc monitoring expires the daemon
        executer.withDaemonIdleTimeoutSecs(300)
        heapSize = "200m"
        leakRate = 1300

        when:
        leaksWithinOneBuild()
        executer.withArguments("-Dorg.gradle.daemon.healthcheckinterval=1000", "--debug")
        executer.withBuildJvmOpts("-D${DaemonMemoryStatus.ENABLE_PERFORMANCE_MONITORING}=true", "-Xms128m", "-Xmx${heapSize}", "-Dorg.gradle.daemon.performance.logging=true")
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
        daemons.daemon.log.contains(DaemonStateCoordinator.DAEMON_STOPPING_IMMEDIATELY_MESSAGE)

        when:
        ExecutionFailure failure = gradle.waitForFailure()

        then:
        failure.assertHasDescription(DaemonStoppedException.MESSAGE + ": " + GcThrashingDaemonExpirationStrategy.EXPIRATION_REASON)
    }

    private boolean daemonIsExpiredEagerly() {
        def dataFile = file("stats")
        setupBuildScript()
        int newDaemons = 0
        try {
            for (int i = 0; i < maxBuilds; i++) {
                executer.noExtraLogging()
                executer.withBuildJvmOpts("-D${DaemonMemoryStatus.ENABLE_PERFORMANCE_MONITORING}=true", "-Xms128m", "-XX:MaxMetaspaceSize=${heapSize}", "-Xmx${heapSize}", "-Dorg.gradle.daemon.performance.logging=true")
                GradleHandle gradle = executer.start()
                gradle.waitForExit()
                if (gradle.standardOutput ==~ /(?s).*Starting build in new daemon \[memory: [0-9].*/) {
                    newDaemons++;
                }
                if (newDaemons > 1) {
                    return true
                }
                def lines = gradle.standardOutput.readLines()
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
            try {
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
            } catch(OutOfMemoryError e) {
                // TeamCity recognizes this message as build failures if it occurs in build log
                throw new OutOfMemoryError(e?.message?.replace(' ', '_'))
            }
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

    String logJdk() {
        return """logger.warn("Build is running with JDK: \${System.getProperty('java.home')}")"""
    }
}
