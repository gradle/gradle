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

import org.gradle.api.GradleException
import org.gradle.test.fixtures.file.LeaksFileHandles
import org.gradle.tooling.GradleConnectionException
import org.gradle.util.Requires

@LeaksFileHandles //With older 2.x Gradle versions -> Unable to delete file: native-platform.dll
class CrossVersionToolingApiSpecificationRetryRuleTest extends ToolingApiSpecification {

    def setup() {
        //these meta tests mess with the daemon log: do not interfere with other tests when running in parallel
        toolingApi.requireIsolatedDaemons()
    }

    def iteration = 0

    @TargetGradleVersion("<1.8")
    def "retries if NPE is thrown in daemon registry in <1.8"() {
        given:
        iteration++

        when:
        throwWhen(new GradleConnectionException("Test Exception", new NullPointerException()), iteration == 1)

        then:
        true
    }

    @TargetGradleVersion(">=1.8")
    def "does not retry if NPE is thrown in daemon registry in >=1.8"() {
        given:
        iteration++

        when:
        throwWhen(new GradleConnectionException("Test Exception", new NullPointerException()), iteration == 1)

        then:
        GradleConnectionException gce = thrown()
        gce.cause instanceof NullPointerException
    }

    @TargetGradleVersion("<3.0")
    def "retries if daemon seems to have disappeared and a daemon that did not do anything is idling (<3.0)"() {
        given:
        iteration++

        when:
        throwWhen(new IOException("Some action failed", new GradleException("Timeout waiting to connect to Gradle daemon.\n more infos")), iteration == 1)

        then:
        true
    }

    @TargetGradleVersion(">=3.0")
    def "does not retry for 3.0 or later"() {
        given:
        iteration++

        when:
        throwWhen(new IOException("Some action failed", new GradleException("Timeout waiting to connect to the Gradle daemon.\n more infos")), iteration == 1)

        then:
        IOException ioe = thrown()
        ioe.cause?.message == "Timeout waiting to connect to the Gradle daemon.\n more infos"
    }

    @Requires(adhoc = {ToolingApiSpecification.runsOnWindowsAndJava7or8()})
    def "retries if expected exception occurs"() {
        given:
        iteration++
        logWhileBuildingOnDaemon('java.net.SocketException: Socket operation on nonsocket: no further information')

        when:
        toolingApi.withConnection { connection -> connection.newBuild().run() }
        throwWhen(new IOException("Could not dispatch a message to the daemon.",
            new IOException("Some exception in the chain",
                new IOException("An existing connection was forcibly closed by the remote host"))), iteration == 1)

        then:
        true
    }

    @Requires(adhoc = {!ToolingApiSpecification.runsOnWindowsAndJava7or8()})
    def "does not retry on non-windows and non-java7 environments"() {
        given:
        iteration++
        logWhileBuildingOnDaemon('java.net.SocketException: Socket operation on nonsocket: no further information')

        when:
        toolingApi.withConnection { connection -> connection.newBuild().run() }
        throwWhen(new IOException("Could not dispatch a message to the daemon.", new IOException("An existing connection was forcibly closed by the remote host")), iteration == 1)

        then:
        IOException ioe = thrown()
        ioe.cause?.message == "An existing connection was forcibly closed by the remote host"
    }

    @Requires(adhoc = {ToolingApiSpecification.runsOnWindowsAndJava7or8()})
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

    @Requires(adhoc = {ToolingApiSpecification.runsOnWindowsAndJava7or8()})
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

    @TargetGradleVersion("<1.8")
    def "project directory is cleaned before retry"() {
        given:
        iteration++
        projectDir.assertIsEmptyDir()

        when:
        settingsFile << "root.name = 'root'"
        def folder = new File(projectDir, "subproject")
        folder.mkdirs()
        new File(folder, "abc.txt") << "content"

        throwWhen(new GradleConnectionException("Test Exception", new NullPointerException()), iteration == 1)

        then:
        true
    }

    @TargetGradleVersion("<1.8")
    def "considers GradleConnectionException caught inside the test"() {
        given:
        iteration++

        when:
        settingsFile << "root.name = 'root'"
        new File(projectDir, "subproject").mkdirs()
        if (iteration == 1) {
            caughtGradleConnectionException = new GradleConnectionException("Test Exception", new NullPointerException())
        }

        then:
        iteration != 1
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
