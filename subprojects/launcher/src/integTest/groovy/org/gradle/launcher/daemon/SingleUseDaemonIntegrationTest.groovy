/*
 * Copyright 2012 the original author or authors.
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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.fixtures.daemon.DaemonLogsAnalyzer
import org.gradle.launcher.daemon.client.DaemonStartupMessage
import org.gradle.launcher.daemon.client.SingleUseDaemonClient
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions

import java.nio.charset.Charset

@Requires(IntegTestPreconditions.NotDaemonExecutor)
class SingleUseDaemonIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        executer.withArgument("--no-daemon")
        executer.requireIsolatedDaemons()
    }

    def "forks build when JVM args are requested"() {
        requireJvmArg('-Xmx64m')

        file('build.gradle') << "println 'hello world'"

        when:
        succeeds()

        then:
        wasForked()

        and:
        daemons.daemon.stops()
    }

    def "forks build when default client VM memory is used and user didn't specify a different limit"() {
        executer.withCommandLineGradleOpts('-Xmx64m')
        executer.useOnlyRequestedJvmOpts()
        executer.requireDaemon()

        when:
        succeeds()

        then:
        wasForked()

        and:
        daemons.daemon.stops()
    }

    def "stops single use daemon when build fails"() {
        requireJvmArg('-Xmx64m')

        file('build.gradle') << "throw new RuntimeException('bad')"

        when:
        fails()

        then:
        wasForked()
        failureHasCause "bad"

        and:
        daemons.daemon.stops()
    }

    @Requires(IntegTestPreconditions.JavaHomeWithDifferentVersionAvailable)
    def "forks build with default daemon JVM args when java home from gradle properties does not match current process"() {
        def javaHome = AvailableJavaHomes.differentJdk.javaHome.canonicalFile

        file('gradle.properties').writeProperties("org.gradle.java.home": javaHome.path)

        file('build.gradle') << """
println 'javaHome=' + org.gradle.internal.jvm.Jvm.current().javaHome.absolutePath
assert java.lang.management.ManagementFactory.runtimeMXBean.inputArguments.contains('-Xmx512m')
assert java.lang.management.ManagementFactory.runtimeMXBean.inputArguments.contains('-XX:+HeapDumpOnOutOfMemoryError')
"""

        when:
        succeeds()

        then:
        wasForked()
        outputContains("javaHome=${javaHome}")
        daemons.daemon.stops()
    }

    @Requires(IntegTestPreconditions.JavaHomeWithDifferentVersionAvailable)
    def "does not fork build when java home from gradle properties matches current process"() {
        def javaHome = AvailableJavaHomes.differentJdk.javaHome

        file('gradle.properties').writeProperties("org.gradle.java.home": javaHome.canonicalPath)

        file('build.gradle') << "println 'javaHome=' + org.gradle.internal.jvm.Jvm.current().javaHome.absolutePath"

        when:
        executer.withJavaHome(javaHome)
        succeeds()

        then:
        wasNotForked()
    }

    def "forks build to run when immutable jvm args set regardless of the environment"() {
        when:
        requireJvmArg('-Xmx64m')
        runWithJvmArg('-Xmx64m')

        and:
        file('build.gradle') << """
assert java.lang.management.ManagementFactory.runtimeMXBean.inputArguments.contains('-Xmx64m')
"""

        then:
        succeeds()

        and:
        wasForked()
        daemons.daemon.stops()
    }

    def "does not fork build when only mutable system properties requested in gradle properties"() {
        when:
        requireJvmArg('-Dsome-prop=some-value')

        and:
        file('build.gradle') << """
assert System.getProperty('some-prop') == 'some-value'
"""

        then:
        succeeds()

        and:
        wasNotForked()
    }

    def "does not fork build when immutable system property is set on command line with same value as current JVM"() {
        def encoding = Charset.defaultCharset().name()

        given:
        buildScript """
            task encoding {
                doFirst { println "encoding = " + java.nio.charset.Charset.defaultCharset().name() }
            }
        """
        when:
        run "encoding", "-Dfile.encoding=$encoding"

        then:
        outputContains "encoding = $encoding"

        and:
        wasNotForked()
    }

    def "does not print daemon startup message for a single use daemon"() {
        given:
        requireJvmArg('-Xmx64m')

        when:
        succeeds()

        then:
        outputDoesNotContain(DaemonStartupMessage.STARTING_DAEMON_MESSAGE)
        wasForked()
        daemons.daemon.stops()
    }

    private def requireJvmArg(String jvmArg) {
        file('gradle.properties') << "org.gradle.jvmargs=$jvmArg"
    }

    private def runWithJvmArg(String jvmArg) {
        executer.withEnvironmentVars(["JAVA_OPTS": "$jvmArg -ea"])
    }

    private void wasForked() {
        outputContains(SingleUseDaemonClient.MESSAGE)
        assert daemons.daemons.size() == 1
    }

    private void wasNotForked() {
        outputDoesNotContain(SingleUseDaemonClient.MESSAGE)
        assert daemons.daemons.size() == 0
    }

    private def getDaemons() {
        return new DaemonLogsAnalyzer(executer.daemonBaseDir)
    }
}
