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

package org.gradle.launcher.daemon.management.internal

import org.gradle.internal.remote.internal.inet.SocketInetAddress
import org.gradle.internal.serialize.Encoder
import org.gradle.internal.serialize.OutputStreamBackedEncoder
import org.gradle.util.GradleVersion
import spock.lang.Specification

/**
 * Checks that a registry written by any supported Gradle version can be read back.
 *
 * The bytes are produced here in each historical layout rather than taken from a checked in fixture, so
 * that the layouts themselves are stated in code next to the decoder that consumes them.
 */
class LegacyDaemonRegistryReaderTest extends Specification {

    def reader = new LegacyDaemonRegistryReader()
    def token = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16] as byte[]

    def "reads a registry written by Gradle #version"() {
        given:
        def bytes = registryBytes(DaemonContextFormat.forVersion(GradleVersion.version(version)), 4711L)

        when:
        def records = reader.parse(bytes, GradleVersion.version(version))

        then:
        records.size() == 1
        records[0].pid == 4711L
        records[0].uid == "the-uid"
        records[0].state == "Idle"
        records[0].token == token
        records[0].address.port == 5000

        where:
        version << ["7.0", "7.6.4", "8.0.2", "8.1", "8.7", "8.8", "8.9", "8.10", "8.14.3", "9.0.0", "9.6.1"]
    }

    def "reads every record when a registry holds more than one daemon"() {
        given:
        def bytes = registryBytes(DaemonContextFormat.V4, 100L, 200L, 300L)

        when:
        def records = reader.parse(bytes, GradleVersion.version("9.6.1"))

        then:
        records*.pid == [100L, 200L, 300L]
    }

    def "an empty registry holds no daemons"() {
        given:
        def bytes = encode { Encoder encoder -> encoder.writeBoolean(false) }

        expect:
        reader.parse(bytes, GradleVersion.version("9.6.1")).empty
    }

    def "reading a registry with the wrong layout does not invent daemons"() {
        given:
        // Bytes written by 8.0 but read as though they came from 9.6, which is what would happen if the
        // version in the directory name were ignored.
        def bytes = registryBytes(DaemonContextFormat.V1, 4711L)

        when:
        def records = reader.parse(bytes, GradleVersion.version("9.6.1"))

        then:
        // The identity fields still line up, so one daemon is seen, but its context is refused rather
        // than being read as plausible nonsense.
        records.size() == 1
        records[0].pid == null
    }

    def "a file cut off inside a record keeps the identity that was readable"() {
        given:
        // A registry read without its lock can be caught mid-rewrite. What survives is the leading part
        // of the file, which is where a daemon's address and token live, so it stays usable for stopping
        // that daemon even though its context is gone.
        def registryFile = fileHolding(truncate(registryBytes(DaemonContextFormat.V4, 4711L), 0.5))

        when:
        def records = reader.read(registryFile, GradleVersion.version("9.6.1"))

        then:
        records.size() == 1
        records[0].pid == null
        records[0].token == token

        cleanup:
        registryFile.delete()
    }

    def "a file cut off before the address table yields no daemons"() {
        given:
        def registryFile = fileHolding(truncate(registryBytes(DaemonContextFormat.V4, 4711L), 0.02))

        expect:
        reader.read(registryFile, GradleVersion.version("9.6.1")).empty

        cleanup:
        registryFile.delete()
    }

    private static byte[] truncate(byte[] bytes, double fraction) {
        bytes[0..<(bytes.length * fraction as int)] as byte[]
    }

    private static File fileHolding(byte[] bytes) {
        def file = File.createTempFile("registry", ".bin")
        file.bytes = bytes
        file
    }

    def "a missing registry file yields no daemons"() {
        expect:
        reader.read(new File("does-not-exist"), GradleVersion.version("9.6.1")).empty
    }

    private byte[] registryBytes(DaemonContextFormat format, long... pids) {
        encode { Encoder encoder ->
            encoder.writeBoolean(true)
            encoder.writeInt(pids.length)
            pids.eachWithIndex { pid, index ->
                encoder.writeByte((byte) 0)
                SocketInetAddress.SERIALIZER.write(encoder, new SocketInetAddress(InetAddress.getByName("127.0.0.1"), 5000 + index))
            }
            pids.eachWithIndex { pid, index ->
                encoder.writeInt(index)
                encoder.writeBinary(token)
                encoder.writeByte((byte) 0) // Idle
                encoder.writeLong(123456789L)
                writeContext(encoder, format, pid)
            }
            encoder.writeInt(0) // no stop events
        }
    }

    /**
     * Writes a daemon context exactly as the Gradle version owning this layout wrote it.
     */
    private static void writeContext(Encoder encoder, DaemonContextFormat format, long pid) {
        encoder.writeNullableString("the-uid")
        encoder.writeString("/opt/jdk")
        if (format in [DaemonContextFormat.V3, DaemonContextFormat.V4]) {
            encoder.writeSmallInt(17) // java version
        }
        if (format == DaemonContextFormat.V4) {
            encoder.writeString("Eclipse Adoptium") // java vendor
        }
        encoder.writeString("/home/user/.gradle/daemon")
        encoder.writeBoolean(true)
        encoder.writeLong(pid)
        encoder.writeBoolean(true)
        encoder.writeInt(10800000) // idle timeout
        encoder.writeInt(2)
        encoder.writeString("-Xmx512m")
        encoder.writeString("-Dfile.encoding=UTF-8")
        if (format != DaemonContextFormat.V1) {
            encoder.writeBoolean(true) // apply instrumentation agent
        }
        if (format in [DaemonContextFormat.V3, DaemonContextFormat.V4]) {
            encoder.writeSmallInt(0) // native services mode
        }
        encoder.writeBoolean(true)
        encoder.writeInt(1) // priority
    }

    private static byte[] encode(Closure<?> action) {
        def stream = new ByteArrayOutputStream()
        def encoder = new OutputStreamBackedEncoder(stream)
        action.call(encoder)
        encoder.flush()
        stream.toByteArray()
    }
}
