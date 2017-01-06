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

package org.gradle.integtests.tooling.fixture

import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

class CrossVersionToolingApiSpecificationRetryRuleTest extends ToolingApiSpecification {

    def setup() {
        //these meta tests mess with the daemon log: do not interfere with other tests when running in parallel
        toolingApi.requireIsolatedDaemons()
    }

    def iteration = 0

    @Requires(TestPrecondition.WINDOWS)
    def "retries when expected exception occurs"() {
        given:
        iteration++
        logWhileBuildingOnDaemon('java.net.SocketException: Socket operation on nonsocket: no further information')

        when:
        toolingApi.withConnection { connection -> connection.newBuild().run() }
        throwWhen(new IOException("Could not dispatch a message to the daemon.", new IOException("An existing connection was forcibly closed by the remote host")), iteration == 1)

        then:
        true
    }

    @Requires(TestPrecondition.WINDOWS)
    def "should fail for unexpected cause on client side"() {
        given:
        iteration++
        logWhileBuildingOnDaemon('java.net.SocketException: Socket operation on nonsocket: no further information')

        when:
        toolingApi.withConnection { connection -> connection.newBuild().run() }
        throwWhen(new IOException("Could not dispatch a message to the daemon.", new IOException("A different cause")), iteration == 1)

        then:
        IOException ioe = thrown()
        ioe.cause?.message == "A different cause"
    }

    @Requires(TestPrecondition.WINDOWS)
    def "should fail for unexpected cause on daemon side"() {
        given:
        iteration++
        logWhileBuildingOnDaemon("Caused by: java.net.SocketException: Something else")

        when:
        toolingApi.withConnection { connection -> connection.newBuild().run() }
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
