/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.launcher.daemon.server.health.gc

import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.ContextualMultiVersionTest
import org.gradle.integtests.fixtures.TargetCoverage
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.integtests.fixtures.compatibility.MultiVersionTest
import org.gradle.integtests.fixtures.daemon.DaemonIntegrationSpec
import org.gradle.integtests.fixtures.daemon.JavaGarbageCollector
import org.gradle.launcher.daemon.server.health.DaemonMemoryStatus
import org.junit.experimental.categories.Category

import static org.gradle.launcher.daemon.server.DaemonStateCoordinator.DAEMON_STOPPING_IMMEDIATELY_MESSAGE
import static org.gradle.launcher.daemon.server.DaemonStateCoordinator.DAEMON_WILL_STOP_MESSAGE

@Category(ContextualMultiVersionTest.class)
@TargetCoverage({ garbageCollectors })
@MultiVersionTest
class GarbageCollectionMonitoringIntegrationTest extends DaemonIntegrationSpec {
    static def version
    GarbageCollectorUnderTest garbageCollector

    def setup() {
        garbageCollector = version
        executer.withBuildJvmOpts(garbageCollector.configuration.jvmArgs.split(" "))
        executer.withEnvironmentVars(JAVA_TOOL_OPTIONS: "-D${DefaultGarbageCollectionMonitor.DISABLE_POLLING_SYSTEM_PROPERTY}=true -D${DaemonMemoryStatus.ENABLE_PERFORMANCE_MONITORING}=true")
        buildFile << """
            ${injectionImports}

            task injectEvents()
        """
    }

    def "expires daemon when heap leaks slowly"() {
        given:
        configureGarbageCollectionHeapEventsFor(256, 512, 35, garbageCollector.monitoringStrategy.gcRateThreshold + 0.2)

        when:
        run "injectEvents"

        then:
        daemons.daemon.stops()

        and:
        daemons.daemon.log.contains(DAEMON_WILL_STOP_MESSAGE)
    }

    def "expires daemon immediately when garbage collector is thrashing"() {
        given:
        configureGarbageCollectionHeapEventsFor(256, 512, 100, garbageCollector.monitoringStrategy.thrashingThreshold + 0.2)
        waitForImmediateDaemonExpiration()

        when:
        fails "injectEvents"

        then:
        failure.assertHasDescription("Gradle build daemon has been stopped: JVM garbage collector thrashing and after running out of JVM heap space")

        and:
        daemons.daemon.stops()

        and:
        daemons.daemon.log.contains(DAEMON_STOPPING_IMMEDIATELY_MESSAGE)
    }

    @ToBeFixedForConfigurationCache(because = "Gradle.buildFinished")
    def "expires daemon when heap leaks while daemon is idle"() {
        def initial = 256
        def max = 512
        def events = eventsFor(initial, max, 35, garbageCollector.monitoringStrategy.gcRateThreshold + 0.2)
        def initScript = file("init.gradle")
        initScript << """
            ${injectionImports}

            gradle.buildFinished {
                ${eventInjectionConfiguration("heap", events, initial, max)}
            }

            gradle.rootProject {
                tasks.create("startLeakAfterBuild")
            }
        """
        executer.usingInitScript(initScript)

        when:
        succeeds "startLeakAfterBuild"

        then:
        daemons.daemon.stops()

        and:
        daemons.daemon.log.contains(DAEMON_WILL_STOP_MESSAGE)
    }

    def "expires daemon when metaspace leaks"() {
        given:
        configureGarbageCollectionHeapEventsFor(256, 512, 0, garbageCollector.monitoringStrategy.gcRateThreshold + 0.2)
        configureGarbageCollectionNonHeapEventsFor(256, 512, 35, 0)

        when:
        run "injectEvents"

        then:
        daemons.daemon.stops()

        and:
        daemons.daemon.log.contains(DAEMON_WILL_STOP_MESSAGE)
    }

    def "does not expire daemon when leak does not consume heap threshold"() {
        given:
        configureGarbageCollectionHeapEventsFor(256, 512, 5, garbageCollector.monitoringStrategy.gcRateThreshold + 0.2)

        when:
        run "injectEvents"

        then:
        daemons.daemon.becomesIdle()
    }

    def "does not expire daemon when leak does not cause excessive garbage collection"() {
        given:
        configureGarbageCollectionHeapEventsFor(256, 512, 35, garbageCollector.monitoringStrategy.gcRateThreshold - 0.2)

        when:
        run "injectEvents"

        then:
        daemons.daemon.becomesIdle()
    }

    def "does not expire daemon when leak does not consume metaspace threshold"() {
        given:
        configureGarbageCollectionHeapEventsFor(256, 512, 0, garbageCollector.monitoringStrategy.gcRateThreshold + 0.2)
        configureGarbageCollectionNonHeapEventsFor(256, 512, 5, 0)

        when:
        run "injectEvents"

        then:
        daemons.daemon.becomesIdle()
    }

    void configureGarbageCollectionHeapEventsFor(long initial, long max, long leakRate, double gcRate) {
        configureGarbageCollectionEvents("heap", initial, max, leakRate, gcRate)
    }

    void configureGarbageCollectionNonHeapEventsFor(long initial, long max, long leakRate, double gcRate) {
        configureGarbageCollectionEvents("nonHeap", initial, max, leakRate, gcRate)
    }

    void configureGarbageCollectionEvents(String type, long initial, long max, leakRate, gcRate) {
        def events = eventsFor(initial, max, leakRate, gcRate)
        buildFile << """
            injectEvents.doLast {
                ${eventInjectionConfiguration(type, events, initial, max)}
            }
        """
    }

    String getInjectionImports() {
        return """
            import org.gradle.launcher.daemon.server.health.gc.GarbageCollectionMonitor
            import org.gradle.launcher.daemon.server.health.gc.GarbageCollectionEvent
            import org.gradle.launcher.daemon.server.health.DaemonHealthStats
            import org.gradle.internal.time.Clock
            import org.gradle.internal.time.Time
            import java.lang.management.MemoryUsage
        """
    }

    String eventInjectionConfiguration(String type, Collection<Map> events, long initial, long max) {
        return """
                    DaemonHealthStats stats = services.get(DaemonHealthStats.class)
                    GarbageCollectionMonitor monitor = stats.getGcMonitor()
                    long startTime = Time.clock().getCurrentTime()
                    ${injectEvents("monitor.get${type.capitalize()}Events()", events, initial, max)}
                    println "GC rate: " + stats.get${type.capitalize()}Stats().getGcRate()
                    println " % used: " + stats.get${type.capitalize()}Stats().getUsedPercent() + "%"
        """
    }

    void waitForImmediateDaemonExpiration() {
        buildFile << """
            import org.gradle.internal.event.ListenerManager
            import org.gradle.launcher.daemon.server.expiry.DaemonExpirationListener
            import org.gradle.launcher.daemon.server.expiry.DaemonExpirationResult
            import java.util.concurrent.CountDownLatch
            import java.util.concurrent.TimeUnit

            def latch = new CountDownLatch(1)
            services.get(ListenerManager.class).addListener(new DaemonExpirationListener() {
                void onExpirationEvent(DaemonExpirationResult result) {
                    latch.countDown()
                }
            })

            injectEvents.doLast {
                // Wait for a daemon expiration event to occur
                latch.await(6, TimeUnit.SECONDS)
                // Give the monitor a chance to stop the daemon abruptly
                sleep 6000
            }
        """
    }

    String injectEvents(String getter, Collection<Map> events, long initial, long max) {
        StringBuilder builder = new StringBuilder()
        events.each { Map event ->
            builder.append("${getter}.slideAndInsert(new GarbageCollectionEvent(startTime + ${fromSeconds(event.timeOffset)}, ${usageFrom(initial, max, event.poolUsage)}, ${event.gcCount}))\n")
        }
        return builder.toString()
    }

    /**
     * Generates garbage collection events starting at initial heap size (MB) and increasing by
     * leakRate (MB) for every event, registering a variable number of garbage collections
     * according to gcRate.  Heap usage will cap at max.
     */
    Collection<Map> eventsFor(long initial, long max, long leakRate, double gcRate) {
        def events = []
        long usage = initial
        long gcCount = 0
        20.times { count ->
            usage += leakRate
            if (usage > max) {
                usage = max
            }
            gcCount = (gcRate * count) as long
            events << ["timeOffset": count, "poolUsage": usage, "gcCount": gcCount]
        }
        return events
    }

    long fromMB(long sizeInMB) {
        return sizeInMB * 1024 * 1024
    }

    long fromSeconds(long seconds) {
        return seconds * 1000
    }

    String usageFrom(long initial, long max, long used) {
        return "new MemoryUsage(${fromMB(initial)}, ${fromMB(used)}, ${fromMB(used)}, ${fromMB(max)})"
    }

    static List<GarbageCollectorUnderTest> getGarbageCollectors() {
        if (JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_14)) {
            return [
                new GarbageCollectorUnderTest(JavaGarbageCollector.ORACLE_SERIAL9, GarbageCollectorMonitoringStrategy.ORACLE_SERIAL),
                new GarbageCollectorUnderTest(JavaGarbageCollector.ORACLE_G1, GarbageCollectorMonitoringStrategy.ORACLE_G1)
            ]
        } else {
            return [
                new GarbageCollectorUnderTest(JavaGarbageCollector.ORACLE_PARALLEL_CMS, GarbageCollectorMonitoringStrategy.ORACLE_PARALLEL_CMS),
                new GarbageCollectorUnderTest(JavaGarbageCollector.ORACLE_SERIAL9, GarbageCollectorMonitoringStrategy.ORACLE_SERIAL),
                new GarbageCollectorUnderTest(JavaGarbageCollector.ORACLE_G1, GarbageCollectorMonitoringStrategy.ORACLE_G1)
            ]
        }
    }

    static class GarbageCollectorUnderTest {
        final JavaGarbageCollector configuration
        final GarbageCollectorMonitoringStrategy monitoringStrategy

        GarbageCollectorUnderTest(JavaGarbageCollector configuration, GarbageCollectorMonitoringStrategy monitoringStrategy) {
            this.configuration = configuration
            this.monitoringStrategy = monitoringStrategy
        }


        @Override
        String toString() {
            return configuration.name()
        }
    }
}
