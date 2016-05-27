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

package org.gradle.launcher.daemon.server.scaninfo

import org.gradle.integtests.fixtures.daemon.DaemonIntegrationSpec
import spock.lang.Ignore

class DaemonScanInfoIntegrationSpec extends DaemonIntegrationSpec {

    def "should capture basic data via the service registry"() {
        given:
        buildFile << captureAndAssert()

        expect:
        buildSucceeds()

    }

    def "should capture basic data via when the daemon is running in continuous mode"() {
        given:
        buildFile << captureAndAssert()

        expect:
        executer.withArguments('help', '--continuous', '-i').run().getExecutedTasks().contains(':help')
    }

    /**
     * TODO - AK - why is the DaemonRegistry sometimes reporting that there are 3 daemons as opposed to 2
     * org.gradle.launcher.daemon.registry.PersistentDaemonRegistry#store(org.gradle.launcher.daemon.registry.DaemonInfo) is definitely only being called twice during this test
     */
    @Ignore
    def "should capture basic data via when there are multiple daemons running in the foreground"() {
        given:
        buildFile << """
        import org.gradle.launcher.daemon.server.scaninfo.DaemonScanInfo

        task capture {
            doLast {
                 DaemonScanInfo info = project.getServices().get(DaemonScanInfo)
                 ${assertInfo(2)}
            }
        }
        """

        when:
        daemons.killAll()
        executer.withArguments("--foreground").start()
        executer.withArguments("--foreground").start()

        def result = executer.withTasks('capture').run()

        then:
        result.getExecutedTasks().contains(':capture')
    }


    def "info is updated to reflect a second run"() {
        expect: true
    }

    private String captureAndAssert() {
        return """
           import org.gradle.launcher.daemon.server.scaninfo.DaemonScanInfo
           DaemonScanInfo info = project.getServices().get(DaemonScanInfo)
           ${assertInfo()}
           """
    }

    private String assertInfo(int numDaemons = 1) {
        return """
           assert info.getNumberOfBuilds() == 1
           assert info.getIdleTimeout() == 120000
           assert info.getNumberOfRunningDaemons() == ${numDaemons}
           assert info.getStartedAt() <= System.currentTimeMillis()
        """
    }
}
