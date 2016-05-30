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
import org.gradle.integtests.fixtures.executer.ExecutionResult

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

    def "should capture basic data when a foreground daemon runs multiple builds"() {
        given:
        buildFile << """
        import org.gradle.launcher.daemon.server.scaninfo.DaemonScanInfo

        ${captureTask("capture1", 1, 1)}
        ${captureTask("capture2", 2, 1)}
        """

        when:
        def daemon = startAForegroundDaemon()

        List<ExecutionResult> captureResults = []
        captureResults << executer.withTasks('capture1').run()
        captureResults << executer.withTasks('capture2').run()

        then:
        captureResults[0].getExecutedTasks().contains(':capture1')
        captureResults[1].getExecutedTasks().contains(':capture2')

        cleanup:
        daemon?.abort()
    }

    static String captureTask(String name, int buildCount, int daemonCount) {
        """
    task $name {
        doLast {
            DaemonScanInfo info = project.getServices().get(DaemonScanInfo)
            ${assertInfo(buildCount, daemonCount)}
        }
    }
    """
    }

    static String captureAndAssert() {
        return """
           import org.gradle.launcher.daemon.server.scaninfo.DaemonScanInfo
           DaemonScanInfo info = project.getServices().get(DaemonScanInfo)
           ${assertInfo(1, 1)}
           """
    }

    static String assertInfo(int numberOfBuilds, int numDaemons) {
        return """
           assert info.getNumberOfBuilds() == ${numberOfBuilds}
           assert info.getNumberOfRunningDaemons() == ${numDaemons}
           assert info.getIdleTimeout() == 120000
           assert info.getStartedAt() <= System.currentTimeMillis()
        """
    }
}
