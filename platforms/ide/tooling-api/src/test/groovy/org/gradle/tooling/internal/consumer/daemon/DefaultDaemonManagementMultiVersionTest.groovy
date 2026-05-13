/*
 * Copyright 2026 the original author or authors.
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
package org.gradle.tooling.internal.consumer.daemon

import org.gradle.internal.serialize.Encoder
import org.gradle.internal.serialize.OutputStreamBackedEncoder
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

/**
 * Validates that {@link DefaultDaemonManagement} walks every version directory under
 * {@code <gradleUserHome>/daemon/} and correctly attributes each entry to its source
 * Gradle version.
 *
 * <p>We synthesize registry.bin files matching each era's documented layout — the
 * end-to-end cross-version spec already exercises the readers against real daemons,
 * so this test just covers the enumeration + version-tagging logic.
 */
class DefaultDaemonManagementMultiVersionTest extends Specification {

    @Rule
    TemporaryFolder tmp = new TemporaryFolder()

    def "listDaemons returns daemons across multiple version directories"() {
        given:
        def userHome = tmp.newFolder("gradle-user-home")
        writeV1Registry(new File(userHome, "daemon/5.0"), "5-uid", 5005L, "/jdk8", "127.0.0.1", 9001)
        writeV4Registry(new File(userHome, "daemon/9.5.0"), "9-uid", 9009L, "/jdk21", "Adoptium", 21, "127.0.0.1", 9002)

        when:
        def daemons = new DefaultDaemonManagement(userHome).listDaemons()

        then:
        daemons.size() == 2
        def byVersion = daemons.collectEntries { [(it.gradleVersion): it] }
        byVersion["5.0"].pid == 5005L
        byVersion["5.0"].javaHome == new File("/jdk8")
        byVersion["5.0"].javaMajorVersion == null
        byVersion["5.0"].javaVendor == null
        byVersion["9.5.0"].pid == 9009L
        byVersion["9.5.0"].javaHome == new File("/jdk21")
        byVersion["9.5.0"].javaMajorVersion == 21
        byVersion["9.5.0"].javaVendor == "Adoptium"
    }

    def "listDaemons(version) filters to the requested version"() {
        given:
        def userHome = tmp.newFolder("gradle-user-home")
        writeV1Registry(new File(userHome, "daemon/5.0"), "5-uid", 1L, "/jdk", "127.0.0.1", 9001)
        writeV4Registry(new File(userHome, "daemon/9.5.0"), "9-uid", 2L, "/jdk", "V", 21, "127.0.0.1", 9002)

        expect:
        def management = new DefaultDaemonManagement(userHome)
        management.listDaemons("5.0").collect { it.uid } == ["5-uid"]
        management.listDaemons("9.5.0").collect { it.uid } == ["9-uid"]
        management.listDaemons("8.0.0").empty
    }

    def "ignores directories whose names are not Gradle versions"() {
        given:
        def userHome = tmp.newFolder("gradle-user-home")
        writeV4Registry(new File(userHome, "daemon/9.5.0"), "v-uid", 1L, "/jdk", "V", 21, "127.0.0.1", 9000)
        new File(userHome, "daemon/notes").mkdirs()
        new File(userHome, "daemon/notes/registry.bin").bytes = new byte[]{0x00}

        expect:
        new DefaultDaemonManagement(userHome).listDaemons().collect { it.gradleVersion } == ["9.5.0"]
    }

    def "swallows corrupted registry files"() {
        given:
        def userHome = tmp.newFolder("gradle-user-home")
        def versionDir = new File(userHome, "daemon/9.5.0")
        versionDir.mkdirs()
        new File(versionDir, "registry.bin").bytes = "not a registry".bytes

        expect:
        new DefaultDaemonManagement(userHome).listDaemons().empty
    }

    // --- Helpers ---

    private void writeV1Registry(File versionDir, String uid, long pid, String javaHome, String host, int port) {
        versionDir.mkdirs()
        def file = new File(versionDir, "registry.bin")
        file.withOutputStream { out ->
            def encoder = new OutputStreamBackedEncoder(out)
            writeEnvelopeStart(encoder, host, port)
            // DaemonInfo wrapper
            encoder.writeInt(0)                          // addressIndex
            encoder.writeBinary([0x01, 0x02] as byte[])  // token
            encoder.writeByte((byte) 0)                  // state IDLE
            encoder.writeLong(0L)                        // lastBusy
            // V1 context layout
            encoder.writeNullableString(uid)
            encoder.writeString(javaHome)
            encoder.writeString("/registry")
            encoder.writeBoolean(true); encoder.writeLong(pid)
            encoder.writeBoolean(true); encoder.writeInt(120_000)
            encoder.writeInt(1); encoder.writeString("-Xmx512m")
            encoder.writeBoolean(false)                  // priority null
            encoder.writeInt(0)                          // stop event count
            encoder.flush()
        }
    }

    private void writeV4Registry(File versionDir, String uid, long pid, String javaHome, String vendor, int javaMajor, String host, int port) {
        versionDir.mkdirs()
        def file = new File(versionDir, "registry.bin")
        file.withOutputStream { out ->
            def encoder = new OutputStreamBackedEncoder(out)
            writeEnvelopeStart(encoder, host, port)
            encoder.writeInt(0)
            encoder.writeBinary([0x03] as byte[])
            encoder.writeByte((byte) 1)                  // state BUSY
            encoder.writeLong(123_456L)
            // V4 context layout
            encoder.writeNullableString(uid)
            encoder.writeString(javaHome)
            encoder.writeSmallInt(javaMajor)
            encoder.writeString(vendor)
            encoder.writeString("/registry")
            encoder.writeBoolean(true); encoder.writeLong(pid)
            encoder.writeBoolean(true); encoder.writeInt(180_000)
            encoder.writeInt(2); encoder.writeString("-Xmx2g"); encoder.writeString("-Dx=y")
            encoder.writeBoolean(true)                   // applyInstrumentationAgent
            encoder.writeSmallInt(1)                     // nativeServicesMode
            encoder.writeBoolean(false)
            encoder.writeInt(0)
            encoder.flush()
        }
    }

    /** registry header: present=true, one SocketInetAddress entry. */
    private void writeEnvelopeStart(Encoder encoder, String host, int port) {
        encoder.writeBoolean(true)
        encoder.writeInt(1)
        encoder.writeByte((byte) 0)                                      // SocketInetAddress
        encoder.writeBinary(java.net.InetAddress.getByName(host).address)
        encoder.writeInt(port)
    }
}
