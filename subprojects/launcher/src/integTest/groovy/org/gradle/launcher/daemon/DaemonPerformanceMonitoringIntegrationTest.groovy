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

    def "leaky build is happy"() {
        file("gradle.properties") << ("org.gradle.jvmargs=-Xmx30m"
                + " -Dorg.gradle.caching.classloaders=true"
                + " -Dorg.gradle.daemon.performance.logging=true")

        when:
        def daemonsUsed = runManyLeakyBuilds(20)

        then:
        daemonsUsed > 1  //feature actually works and expires tired daemons
        daemonsUsed <= 5 //feature does not fork new daemon for each build
    }

    //invokes leaky build x times and returns number of daemons used
    private int runManyLeakyBuilds(int x) {
        setupLeakyBuild()
        int newDaemons = 0
        for (int i = 0; i < x; i++) {
            def r = run()
            if (r.output.contains("Starting build in new daemon [memory: ")) {
                newDaemons++;
            }
        }
        newDaemons
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
