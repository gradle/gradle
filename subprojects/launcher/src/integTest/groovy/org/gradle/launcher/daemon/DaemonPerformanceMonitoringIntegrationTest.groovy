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

class DaemonPerformanceMonitoringIntegrationTest extends DaemonIntegrationSpec {

    def setup() {
        executer.requireIsolatedDaemons()
    }

    def "when build leaks more than available memory the daemon is expired eagerly"() {
        expect:
        daemonIsExpiredEagerly("-Xmx30m")
    }

    def "when build leaks within available memory the daemon is not expired"() {
        expect:
        !daemonIsExpiredEagerly("-Xmx100m")
    }

    private boolean daemonIsExpiredEagerly(String xmx) {
        file("gradle.properties") << ("org.gradle.jvmargs=$xmx"
                + " -Dorg.gradle.caching.classloaders=true"
                + " -Dorg.gradle.daemon.performance.logging=true")

        setupLeakyBuild()
        int newDaemons = 0
        for (int i = 0; i < 10; i++) {
            def r = run()
            if (r.output.contains("Starting build in new daemon [memory: ")) {
                newDaemons++;
            }
            if (newDaemons > 1) {
                return true
            }
        }
        return false
    }

    private void setupLeakyBuild() {
        executer.noExtraLogging()

        buildFile << """
            class State {
                static int x
                static map = [:]
            }
            State.x++

            //simulate the leak
            (State.x * 1000).times {
                State.map.put(it, "foo" * 300)
            }

            println "Build: " + State.x
        """
    }
}
