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

package org.gradle.launcher.daemon.bootstrap

import org.gradle.api.GradleException
import org.gradle.internal.nativeintegration.services.NativeServices.NativeServicesMode
import org.gradle.internal.remote.internal.inet.MultiChoiceAddress
import org.gradle.launcher.daemon.diagnostics.DaemonDiagnostics
import org.gradle.launcher.daemon.startup.DaemonPriority
import org.gradle.launcher.daemon.startup.DaemonServerConfiguration
import org.gradle.launcher.daemon.startup.DaemonStartupCommunication
import org.gradle.launcher.daemon.startup.DaemonStartupInfo
import org.gradle.launcher.daemon.startup.DefaultDaemonServerConfiguration
import org.jspecify.annotations.Nullable
import spock.lang.Specification

import java.nio.charset.StandardCharsets

class DaemonStartupCommunicationSpec extends Specification {

    def dummyFile = new File("C:\\foo;;\\daemon-123.log\n\r\n\u03b1")
    def uuid = UUID.randomUUID()
    def addresses = [InetAddress.getByName(null)]

    def "can simply communicate diagnostics"() {
        when:
        def message = writeStartupInfo(newStartupInfo(uuid, 12, addresses, "1234", dummyFile, 123))
        def startupInfo = DaemonStartupCommunication.readDaemonStartupInfo(message)

        then:
        startupInfo.uid == "1234"
        startupInfo.address.canonicalAddress == uuid
        startupInfo.address.port == 12
        startupInfo.address.candidates == addresses
        startupInfo.pid == 123
        startupInfo.diagnostics.pid == 123
        startupInfo.diagnostics.daemonLog == dummyFile
    }

    def "null pid is supported"() {
        when:
        def message = writeStartupInfo(newStartupInfo(uuid, 123, addresses, "1234", dummyFile, null))
        def startupInfo = DaemonStartupCommunication.readDaemonStartupInfo(message)

        then:
        startupInfo.diagnostics.pid == null
    }

    def "parses the process output"() {
        given:
        def outputStream = new ByteArrayOutputStream()
        def printStream = new PrintStream(outputStream)
        printStream.print("""hey joe!
another line of output...
""")

        DaemonStartupCommunication.writeDaemonStartupInfo(printStream, newStartupInfo(uuid, 123, addresses, "1234", dummyFile, 12))

        when:
        def daemonStartupInfo = DaemonStartupCommunication.readStartupInfoFromDaemonOutput(new ByteArrayInputStream(outputStream.toByteArray()))

        then:
        daemonStartupInfo.uid == "1234"
        daemonStartupInfo.address.canonicalAddress == uuid
        daemonStartupInfo.address.port == 123
        daemonStartupInfo.address.candidates == addresses
        daemonStartupInfo.pid == 12
        daemonStartupInfo.diagnostics.pid == 12
        daemonStartupInfo.diagnostics.daemonLog == dummyFile
    }

    def "shouts if daemon did not start"() {
        given:
        def output = """hey joe!
another line of output..."""

        when:
        DaemonStartupCommunication.readStartupInfoFromDaemonOutput(new ByteArrayInputStream(output.getBytes(StandardCharsets.UTF_8)))

        then:
        def ex = thrown(GradleException)
        ex.message.contains("Could not parse daemon handshake response.")
        ex.message.contains("hey joe!")
    }

    def "shouts if daemon broke completely"() {
        when:
        DaemonStartupCommunication.readStartupInfoFromDaemonOutput(new ByteArrayInputStream(new byte[0]))

        then:
        def ex = thrown(GradleException)
        ex.message.contains("Could not parse daemon handshake response.")
    }

    String writeStartupInfo(DaemonStartupInfo info) {
        def outputStream = new ByteArrayOutputStream()
        DaemonStartupCommunication.writeDaemonStartupInfo(new PrintStream(outputStream), info)
        return new String(outputStream.toByteArray())
    }

    private static DaemonStartupInfo newStartupInfo(UUID addressId, int port, List<InetAddress> addresses, String daemonId, File logFile, @Nullable Long pid) {
        def address = new MultiChoiceAddress(addressId, port, addresses)
        new DaemonStartupInfo(daemonId, address, new DaemonDiagnostics(logFile, pid))
    }

    def "can round-trip daemon server configuration"() {
        given:
        def configuration = new DefaultDaemonServerConfiguration(
            new File("gradle-user-home").absoluteFile,
            "daemon-uid-1234",
            new File("daemon-base").absoluteFile,
            120000,
            10000,
            true,
            DaemonPriority.LOW,
            ["-Xmx512m", "-Dfoo=bar"],
            true,
            NativeServicesMode.ENABLED
        )

        when:
        def result = roundTrip(configuration)

        then:
        result.gradleUserHomeDir == configuration.gradleUserHomeDir
        result.baseDir == configuration.baseDir
        result.idleTimeout == configuration.idleTimeout
        result.periodicCheckIntervalMs == configuration.periodicCheckIntervalMs
        result.singleUse == configuration.singleUse
        result.instrumentationAgentAllowed == configuration.instrumentationAgentAllowed
        result.nativeServicesMode == configuration.nativeServicesMode
        result.uid == configuration.uid
        result.priority == configuration.priority
        result.jvmOptions == configuration.jvmOptions
    }

    def "can round-trip daemon server configuration with empty jvm options"() {
        given:
        def configuration = new DefaultDaemonServerConfiguration(
            new File("/gradle/user/home"),
            "daemon-uid-1234",
            new File("/gradle/daemon/base"),
            120000,
            10000,
            false,
            DaemonPriority.NORMAL,
            [],
            false,
            NativeServicesMode.DISABLED
        )

        when:
        def result = roundTrip(configuration)

        then:
        result.jvmOptions == []
        result.singleUse == false
        result.instrumentationAgentAllowed == false
        result.priority == DaemonPriority.NORMAL
        result.nativeServicesMode == NativeServicesMode.DISABLED
    }

    private static DaemonServerConfiguration roundTrip(DaemonServerConfiguration configuration) {
        def outputStream = new ByteArrayOutputStream()
        DaemonStartupCommunication.writeDaemonServerConfiguration(outputStream, configuration)
        DaemonStartupCommunication.readDaemonServerConfiguration(new ByteArrayInputStream(outputStream.toByteArray()))
    }

}
