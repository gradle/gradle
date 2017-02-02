/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.testkit.runner

import org.gradle.tooling.GradleConnectionException
import org.gradle.util.GradleVersion
import org.gradle.util.Requires

class GradleRunnerRetryRuleTest extends BaseGradleRunnerIntegrationTest {

    def setup() {
        //these meta tests mess with the daemon log: do not interfere with other tests when running in parallel
        requireIsolatedTestKitDir = true
    }

    def iteration = 0

    def "retries on clock shift issue for <2.10"() {
        given:
        iteration++
        def isVersionWithIssue = gradleVersion < GradleVersion.version('2.10')

        when:
        throwWhen(new GradleConnectionException("Test Exception",
            new IllegalArgumentException("Unable to calculate percentage: 19 of -233. All inputs must be >= 0")), isVersionWithIssue && iteration == 1)

        then:
        true
    }

    @Requires(adhoc = {!BaseGradleRunnerIntegrationTest.runsOnWindowsAndJava7or8()})
    def "does not retry on non-windows and non-java environments"() {
        given:
        iteration++
        logWhileBuildingOnDaemon('java.net.SocketException: Socket operation on nonsocket: no further information')

        when:
        runner().build()
        throwWhen(new IOException("Could not dispatch a message to the daemon.", new IOException("An existing connection was forcibly closed by the remote host")), iteration == 1)

        then:
        IOException ioe = thrown()
        ioe.cause?.message == "An existing connection was forcibly closed by the remote host"
    }

    @Requires(adhoc = {BaseGradleRunnerIntegrationTest.runsOnWindowsAndJava7or8()})
    def "should fail for unexpected cause on client side"() {
        given:
        iteration++
        logWhileBuildingOnDaemon('java.net.SocketException: Socket operation on nonsocket: no further information')

        when:
        runner().build()
        throwWhen(new IOException("Could not dispatch a message to the daemon.", new IOException("A different cause")), iteration == 1)

        then:
        IOException ioe = thrown()
        ioe.cause?.message == "A different cause"
    }

    @Requires(adhoc = {BaseGradleRunnerIntegrationTest.runsOnWindowsAndJava7or8()})
    def "should fail for unexpected cause on daemon side"() {
        given:
        iteration++
        logWhileBuildingOnDaemon("Caused by: java.net.SocketException: Something else")

        when:
        runner().build()
        throwWhen(new IOException("Could not dispatch a message to the daemon.", new IOException("An existing connection was forcibly closed by the remote host")), iteration == 1)

        then:
        IOException ioe = thrown()
        ioe.message == "Could not dispatch a message to the daemon."
    }

    private static void throwWhen(Throwable throwable, boolean condition) {
        if (condition) {
            throw throwable
        }
    }

    private void logWhileBuildingOnDaemon(String exceptionInDaemon) {
        buildFile << "println '$exceptionInDaemon'" //makes the expected error appear in the daemon's log
    }
}
