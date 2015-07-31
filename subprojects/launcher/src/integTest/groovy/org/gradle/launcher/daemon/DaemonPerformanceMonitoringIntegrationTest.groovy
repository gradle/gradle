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

class DaemonPerformanceMonitoringIntegrationTest extends DaemonIntegrationSpec {
    def "when build leaks more than available memory the daemon is expired eagerly"() {
        expect:
        daemonIsExpiredEagerly("-Xmx30m")
    }

    def "when build leaks within available memory the daemon is not expired"() {
        expect:
        !daemonIsExpiredEagerly("-Xmx500m")
    }

    private boolean daemonIsExpiredEagerly(String xmx) {
        setupLeakyBuild()
        int newDaemons = 0
        for (int i = 0; i < 10; i++) {
            executer.noExtraLogging()
            executer.withBuildJvmOpts("-D${DaemonStatus.EXPIRE_AT_PROPERTY}=80", xmx, "-Dorg.gradle.daemon.performance.logging=true")
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
