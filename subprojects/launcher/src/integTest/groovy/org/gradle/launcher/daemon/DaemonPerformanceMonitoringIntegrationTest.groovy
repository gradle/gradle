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

import org.gradle.integtests.fixtures.executer.UnexpectedBuildFailure
import spock.lang.Ignore

/**
 * This test runs a 'leaky' build many times against a single daemon instance.
 * When daemon performance monitoring is turned on, the build can be executed many times.
 * When monitoring is off, the n-th build will incur build failure related to memory (OOME, gc overhead, etc.)
 */
class DaemonPerformanceMonitoringIntegrationTest extends DaemonIntegrationSpec {

    def setup() {
        executer.requireIsolatedDaemons()
    }

    def "leaky build is happy"() {
        healthMonitor(true)
        expect: runManyLeakyBuilds()
    }

    @Ignore
    def "leaky build fails"() {
        healthMonitor(false)

        when:
        runManyLeakyBuilds()

        then:
        thrown(UnexpectedBuildFailure)
        //assuming the failure is OOME or gc overhead, etc.
    }

    private void healthMonitor(boolean enabled) {
        file("gradle.properties") << "org.gradle.jvmargs=-Xmx30m -Dorg.gradle.caching.classloaders=true -Dorg.gradle.daemon.performance.monitor=$enabled"
    }

    private void runManyLeakyBuilds() {
        setupLeakyBuild()
        for (int i = 0; i < 20; i++) {
            run()
        }
    }

    private void setupLeakyBuild() {
        executer.noExtraLogging()

        buildFile << """
            class Counter {
                static int x
            }
            Counter.x ++

            def map = [:]
            (Counter.x * 1000).times {
                map.put(it, "foo" * 200)
            }

            println "Build: " + Counter.x
        """
    }
}
