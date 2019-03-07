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

import org.gradle.integtests.fixtures.daemon.DaemonIntegrationSpec
import org.gradle.launcher.daemon.server.health.DaemonMemoryStatus

import static org.gradle.launcher.daemon.server.DaemonStateCoordinator.DAEMON_STOPPING_IMMEDIATELY_MESSAGE
import static org.gradle.launcher.daemon.server.DaemonStateCoordinator.DAEMON_WILL_STOP_MESSAGE

class GarbageCollectionMonitoringIntegrationTest extends DaemonIntegrationSpec {
    def setup() {
        executer.withEnvironmentVars(JAVA_TOOL_OPTIONS: "-D${DefaultGarbageCollectionMonitor.DISABLE_POLLING_SYSTEM_PROPERTY}=true -D${DaemonMemoryStatus.ENABLE_PERFORMANCE_MONITORING}=true")
    }

    def "expires daemon when heap leaks slowly"() {
        given:
        configureGarbageCollectionHeapEventsFor(256, 512, 35, 1.8)

        when:
        run "injectEvents"

        then:
        daemons.daemon.stops()

        and:
        daemons.daemon.log.contains(DAEMON_WILL_STOP_MESSAGE)
    }

    def "expires daemon immediately when garbage collector is thrashing"() {
        given:
        configureGarbageCollectionHeapEventsFor(256, 512, 100, 5)
        waitForDaemonExpiration()

        when:
        fails "injectEvents"

        then:
        failure.assertHasDescription("Gradle build daemon has been stopped: JVM garbage collector thrashing and after running out of JVM memory")

        and:
        daemons.daemon.stops()

        and:
        daemons.daemon.log.contains(DAEMON_STOPPING_IMMEDIATELY_MESSAGE)
    }

    def "expires daemon when heap leaks while daemon is idle"() {
        def initial = 256
        def max = 512
        def events = eventsFor(initial, max, 35, 1.8)
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
        configureGarbageCollectionNonHeapEventsFor(256, 512, 35, 0)

        when:
        run "injectEvents"

        then:
        daemons.daemon.stops()

        and:
        daemons.daemon.log.contains(DAEMON_WILL_STOP_MESSAGE)
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
            ${injectionImports}
            
            task injectEvents {
                doLast {
                    ${eventInjectionConfiguration(type, events, initial, max)}                   
                }
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

    void waitForDaemonExpiration() {
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
            
            injectEvents.doLast { latch.await(6, TimeUnit.SECONDS) }
        """
    }

    String injectEvents(String getter, Collection<Map> events, long initial, long max) {
        StringBuilder builder = new StringBuilder()
        events.each { Map event ->
            builder.append("${getter}.slideAndInsert(new GarbageCollectionEvent(startTime + ${fromSeconds(event.timeOffset)}, ${usageFrom(initial, max, event.poolUsage)}, ${event.gcCount}))\n")
        }
        return builder.toString()
    }

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
}
