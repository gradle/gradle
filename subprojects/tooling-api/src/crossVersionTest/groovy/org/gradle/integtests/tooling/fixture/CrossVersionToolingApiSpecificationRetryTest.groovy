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
import org.gradle.integtests.fixtures.executer.UnexpectedBuildFailure
import org.gradle.test.fixtures.file.LeaksFileHandles
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import org.gradle.tooling.GradleConnectionException

@LeaksFileHandles
//With older 2.x Gradle versions -> Unable to delete file: native-platform.dll
class CrossVersionToolingApiSpecificationRetryTest extends ToolingApiSpecification {

    def setup() {
        //these meta tests mess with the daemon log: do not interfere with other tests when running in parallel
        toolingApi.requireIsolatedDaemons()
    }

    def iteration = 0

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

    @Requires([
        UnitTestPreconditions.Windows,
        UnitTestPreconditions.Jdk7OrLater,
        UnitTestPreconditions.Jdk8OrEarlier
    ])
    def "retries if expected exception occurs"() {
        given:
        iteration++

        when:
        logToFakeDaemonLog('java.net.SocketException: Socket operation on nonsocket: no further information')
        throwWhen(new IOException("Could not dispatch a message to the daemon.",
            new IOException("Some exception in the chain",
                new IOException("An existing connection was forcibly closed by the remote host"))), iteration == 1)

        then:
        true
    }

    @Requires([
        UnitTestPreconditions.Windows,
        UnitTestPreconditions.Jdk7OrLater,
        UnitTestPreconditions.Jdk8OrEarlier
    ])    def "does not retry on non-windows and non-java7 environments"() {
        given:
        iteration++

        when:
        logToFakeDaemonLog('java.net.SocketException: Socket operation on nonsocket: no further information')
        throwWhen(new IOException("Could not dispatch a message to the daemon.", new IOException("An existing connection was forcibly closed by the remote host")), iteration == 1)

        then:
        IOException ioe = thrown()
        ioe.cause?.message == "An existing connection was forcibly closed by the remote host"
    }

    @Requires([
        UnitTestPreconditions.Windows,
        UnitTestPreconditions.Jdk7OrLater,
        UnitTestPreconditions.Jdk8OrEarlier
    ])
    def "should fail for unexpected cause on client side"() {
        given:
        iteration++

        when:
        logToFakeDaemonLog('java.net.SocketException: Socket operation on nonsocket: no further information')
        throwWhen(new IOException("Could not dispatch a message to the daemon.", new IOException("A different cause")), iteration == 1)

        then:
        IOException ioe = thrown()
        ioe.cause?.message == "A different cause"
    }

    @Requires([
        UnitTestPreconditions.Windows,
        UnitTestPreconditions.Jdk7OrLater,
        UnitTestPreconditions.Jdk8OrEarlier
    ])
    def "should fail for unexpected cause on daemon side"() {
        given:
        iteration++

        when:
        logToFakeDaemonLog("Caused by: java.net.SocketException: Something else")
        throwWhen(new IOException("Could not dispatch a message to the daemon.", new IOException("An existing connection was forcibly closed by the remote host")), iteration == 1)

        then:
        IOException ioe = thrown()
        ioe.message == "Could not dispatch a message to the daemon."
    }

    @TargetGradleVersion(">=2.6 <2.10")
    def "retries on clock shift issue for <2.10 if exception is provided through build error output"() {
        given:
        iteration++

        when:
        throwWhen(new UnexpectedBuildFailure("Gradle execution failed",
            new IllegalArgumentException("Unable to calculate percentage: 19 of -233. All inputs must be >= 0")), iteration == 1)

        then:
        true
    }

    private static void throwWhen(Throwable throwable, boolean condition) {
        if (condition) {
            throw throwable
        }
    }

    private void logToFakeDaemonLog(String exceptionInDaemon) {
        def logDir = new File(daemonsFixture.daemonBaseDir, daemonsFixture.getVersion())
        logDir.mkdirs()
        def log = new File(logDir, "daemon-fake.log")
        log << "DefaultDaemonContext[uid=0000,javaHome=javaHome,daemonRegistryDir=daemonRegistryDir,pid=-9999,idleTimeout=120000,daemonOpts=daemonOpts]\n"
        log << exceptionInDaemon
    }
}
