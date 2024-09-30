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
import org.gradle.integtests.fixtures.jvm.JavaToolchainFixture
import org.gradle.internal.buildconfiguration.fixture.DaemonJvmPropertiesFixture
import org.gradle.launcher.daemon.client.DaemonStartupMessage
import org.gradle.launcher.daemon.client.SingleUseDaemonClient
import org.gradle.launcher.daemon.configuration.DaemonParameters
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions

import java.nio.charset.Charset

@Requires(IntegTestPreconditions.NotEmbeddedExecutor)
class SingleUseDaemonIntegrationTest extends AbstractIntegrationSpec implements DaemonJvmPropertiesFixture, JavaToolchainFixture {
    def tmpdir = buildContext.getTmpDir().createDir()

    def setup() {
        // This is doing some strange things to avoid the testing infrastructure from making these tests even more complicated.
        // We want to start the launcher JVM with a controlled set of JVM args
        // and we want the daemon to have a reasonable set of JVM args
        // This sets things up so that they match by default. The tests below then create different scenarios where
        // the launcher or build JVM args are different.
        // Ultimately, we want everything to be single use and get rid of the non-forking scenarios below.
        // This is not really testing real world behavior because it's impossible to get non-forking scenarios to work in most builds
        // because the wrapper requires a single use daemon.

        executer.withArgument("--no-daemon")
        executer.requireIsolatedDaemons()
        executer.useOnlyRequestedJvmOpts()
        executer.requireDaemon()
        executer.withCommandLineGradleOpts(DaemonParameters.DEFAULT_JVM_ARGS)
        executer.withCommandLineGradleOpts("-Djava.io.tmpdir=${tmpdir}")

        file('gradle.properties').writeProperties('org.gradle.jvmargs': DaemonParameters.DEFAULT_JVM_ARGS.join(" ") + " -Djava.io.tmpdir=${tmpdir} -ea")
    }

    def "forks build when incompatible JVM args are requested"() {
        buildRequestsJvmArgs('-Xmx64m')

        file('build.gradle') << "println 'hello world'"

        when:
        succeeds()

        then:
        wasForked()

        and:
        daemons.daemon.stops()
    }

    def "stops single use daemon when build fails"() {
        buildRequestsJvmArgs('-Xmx64m')

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

    @Requires([IntegTestPreconditions.JavaHomeWithDifferentVersionAvailable])
    def "forks build with default daemon JVM args when daemon jvm criteria from build properties does not match current process"() {
        def otherJdk = AvailableJavaHomes.differentVersion
        writeJvmCriteria(otherJdk)
        captureJavaHome()

        when:
        withInstallations(otherJdk).succeeds()

        then:
        assertDaemonUsedJvm(otherJdk)
        wasForked()
        daemons.daemon.stops()
    }

    @Requires(IntegTestPreconditions.JavaHomeWithDifferentVersionAvailable)
    def "does not fork build when java home from gradle properties matches current process"() {
        def differentJdk = AvailableJavaHomes.differentJdk
        file('gradle.properties').mergeProperties("org.gradle.java.home": differentJdk.javaHome.canonicalPath)

        file('build.gradle') << "println 'javaHome=' + org.gradle.internal.jvm.Jvm.current().javaHome.absolutePath"

        when:
        executer.withJvm(differentJdk)
        succeeds()

        then:
        wasNotForked()
    }

    @Requires([IntegTestPreconditions.JavaHomeWithDifferentVersionAvailable])
    def "does not fork build when daemon jvm criteria from build properties matches current process"() {
        def otherJdk = AvailableJavaHomes.differentVersion

        writeJvmCriteria(otherJdk)
        captureJavaHome()

        when:
        executer.withJvm(otherJdk)
        withInstallations(otherJdk).succeeds()
        assertDaemonUsedJvm(otherJdk)

        then:
        wasNotForked()
    }

    def "forks build to run when immutable jvm args set regardless of the environment"() {
        when:
        buildRequestsJvmArgs('-Xmx64m')
        executer.withCommandLineGradleOpts('-Xmx64m', '-Xms64m')

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
        file('gradle.properties').writeProperties('org.gradle.jvmargs': DaemonParameters.DEFAULT_JVM_ARGS.join(" ") + " -Djava.io.tmpdir=${tmpdir} -ea -Dsome-prop=some-value")

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
        buildFile """
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
        buildRequestsJvmArgs('-Xmx64m')

        when:
        succeeds()

        then:
        outputDoesNotContain(DaemonStartupMessage.STARTING_DAEMON_MESSAGE)
        wasForked()
        daemons.daemon.stops()
    }

    private def buildRequestsJvmArgs(String jvmArg) {
        file('gradle.properties').mergeProperties("org.gradle.jvmargs": jvmArg)
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
