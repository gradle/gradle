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

package org.gradle.launcher

import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.jvm.JDWPUtil
import org.gradle.test.fixtures.ConcurrentTestUtil
import org.gradle.test.fixtures.Flaky
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.gradle.test.preconditions.UnitTestPreconditions
import org.junit.Assume
import spock.lang.Issue
import spock.lang.Timeout

class CommandLineIntegrationSpec extends AbstractIntegrationSpec {

    @Requires(IntegTestPreconditions.NotParallelExecutor)
    def "reasonable failure message when --max-workers=#value"() {
        given:
        executer.requireDaemon().requireIsolatedDaemons()  // otherwise exception gets thrown in testing infrastructure

        when:
        executer.withArgument("--max-workers=$value")

        then:
        fails "help"

        and:
        failure.assertHasErrorOutput "Argument value '$value' given for --max-workers option is invalid (must be a positive, non-zero, integer)"

        where:
        value << ["-1", "0", "foo", " 1"]
    }

    def "reasonable failure message when org.gradle.workers.max=#value"() {
        given:
        executer.requireDaemon().requireIsolatedDaemons() // otherwise exception gets thrown in testing infrastructure

        when:
        executer.withArgument("-Dorg.gradle.workers.max=$value")

        then:
        fails "help"

        and:
        failure.assertHasDescription "Value '$value' given for org.gradle.workers.max Gradle property is invalid (must be a positive, non-zero, integer)"

        where:
        value << ["-1", "0", "foo", " 1"]
    }

    @Requires(IntegTestPreconditions.NotEmbeddedExecutor)
    def "can debug with org.gradle.debug=true"() {
        given:
        Assume.assumeTrue(debugPortIsFree())
        executer.requireDaemon().requireIsolatedDaemons()
        JDWPUtil jdwpClient = new JDWPUtil(5005)

        when:
        def gradle = executer.withArgument("-Dorg.gradle.debug=true").withTasks("help").start()

        then:
        ConcurrentTestUtil.poll() {
            // Connect, resume threads, and disconnect from VM
            jdwpClient.connect().dispose()
        }
        gradle.waitForFinish()
    }

    @Issue('https://github.com/gradle/gradle/issues/18084')
    @Requires(IntegTestPreconditions.NotEmbeddedExecutor)
    @Flaky(because = "Sometimes it hangs for hours")
    def "can debug on selected port with org.gradle.debug.port"() {
        given:
        executer.requireDaemon().requireIsolatedDaemons()
        JDWPUtil jdwpClient = new JDWPUtil()

        when:
        def gradle = executer
            .withArgument("-Dorg.gradle.debug=true")
            .withArgument("-Dorg.gradle.debug.port=${jdwpClient.port}")
            .withTasks("help").start()

        then:
        ConcurrentTestUtil.poll() {
            // Connect, resume threads, and disconnect from VM
            jdwpClient.connect().dispose()
        }
        gradle.waitForFinish()

        cleanup:
        jdwpClient.close()
    }

    def "can debug via host"() {
        given:
        executer.requireDaemon().requireIsolatedDaemons()

        JDWPUtil jdwpClient = new JDWPUtil()

        def jdwpHost = nonLoopbackAddress()
        Assume.assumeNotNull(jdwpHost)
        jdwpClient.host = jdwpHost


        def port = jdwpClient.port

        def host = jdwpClient.host
        when:
        def gradle = startWithDebug(port, host)

        then:
        ConcurrentTestUtil.poll() {
            // Connect, resume threads, and disconnect from VM
            jdwpClient.connect().dispose()
        }
        gradle.waitForFinish()

        cleanup:
        jdwpClient.close()
    }

    private startWithDebug(int port, String host) {
        executer.withArgument("-Dorg.gradle.debug=true")
            .withArgument("-Dorg.gradle.debug.port=" + port)
            .withArgument("-Dorg.gradle.debug.host=" + host)
            .withTasks("help")
            .start()
    }

    @Requires(UnitTestPreconditions.Jdk9OrLater)
    def "can debug on explicitly any host"() {
        given:
        executer.requireDaemon().requireIsolatedDaemons()

        JDWPUtil jdwpClient = new JDWPUtil()

        def address = nonLoopbackAddress()
        Assume.assumeNotNull(address)
        jdwpClient.host = address

        when:
        def gradle = startWithDebug(jdwpClient.port, "*")

        then:
        ConcurrentTestUtil.poll() {
            // Connect, resume threads, and disconnect from VM
            jdwpClient.connect().dispose()
        }
        gradle.waitForFinish()

        cleanup:
        jdwpClient.close()
    }

    private static String nonLoopbackAddress() {
        println("Looking at network interfaces")
        def address = Collections.list(NetworkInterface.getNetworkInterfaces())
            .collectMany { it.isLoopback() ? [] : Collections.list(it.inetAddresses) }
            .find { it instanceof Inet4Address && !it.isLoopbackAddress() }
            .hostAddress
        println("using address=$address")
        return address
    }

    @Issue('https://github.com/gradle/gradle/issues/18084')
    @Timeout(30)
    def "reasonable failure message when org.gradle.debug.port=#value"() {
        given:
        executer.requireDaemon().requireIsolatedDaemons() // otherwise exception gets thrown in testing infrastructure

        when:
        args("-Dorg.gradle.debug=true", "-Dorg.gradle.debug.port=$value")

        then:
        fails "help"

        and:
        failure.assertHasDescription "Value '$value' given for org.gradle.debug.port Gradle property is invalid (must be a number between 1 and 65535)"

        where:
        value << ["-1", "0", "1.1", "foo", " 1", "65536"]
    }

    @Flaky(because = "Sometimes it hangs for hours")
    @Issue('https://github.com/gradle/gradle/issues/18084')
    @Requires(IntegTestPreconditions.NotEmbeddedExecutor)
    @Timeout(30)
    def "can debug with org.gradle.debug.server=false"() {
        given:
        executer.requireDaemon().requireIsolatedDaemons()
        JDWPUtil jdwpClient = new JDWPUtil()
        jdwpClient.listen(false)

        when:
        def handle = executer.withArguments("-Dorg.gradle.debug=true", "-Dorg.gradle.debug.server=false", "-Dorg.gradle.debug.port=${jdwpClient.port}").withTasks("help").start()

        and:
        jdwpClient.accept()
        jdwpClient.resume()
        jdwpClient.asyncResumeWhile { handle.running }

        then:
        handle.waitForFinish()

        cleanup:
        jdwpClient.close()
    }

    @Issue('https://github.com/gradle/gradle/issues/18084')
    @Requires(IntegTestPreconditions.NotEmbeddedExecutor)
    @Timeout(30)
    def "can debug with org.gradle.debug.suspend=false"() {
        given:
        executer.requireDaemon().requireIsolatedDaemons()
        JDWPUtil jdwpClient = new JDWPUtil()
        jdwpClient.listen(false)

        when:
        def handle = executer.withArguments("-Dorg.gradle.debug=true", "-Dorg.gradle.debug.suspend=false", "-Dorg.gradle.debug.server=false", "-Dorg.gradle.debug.port=${jdwpClient.port}").withTasks("help").start()

        and:
        jdwpClient.accept()
        if (JavaVersion.current() == JavaVersion.VERSION_1_8) {
            // Only on Java 8, we must actually resume the VM on events, or it won't finish.
            jdwpClient.asyncResumeWhile { handle.running }
        }

        then:
        handle.waitForFinish()

        cleanup:
        jdwpClient.close()
    }

    static boolean debugPortIsFree() {
        boolean free = true
        ConcurrentTestUtil.poll(30) {
            Socket probe
            try {
                probe = new Socket(InetAddress.getLocalHost(), 5005)
                // something is listening, keep polling
                free = false
            } catch (Exception e) {
                // nothing listening - exit the polling loop
            } finally {
                probe?.close()
            }
        }
        free
    }

}
