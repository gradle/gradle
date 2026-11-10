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

package org.gradle.launcher.daemon.registry

import org.gradle.internal.jvm.Jvm
import org.gradle.internal.remote.Address
import org.gradle.internal.remote.internal.inet.MultiChoiceAddress
import org.gradle.internal.remote.internal.inet.SocketInetAddress
import org.gradle.internal.serialize.InputStreamBackedDecoder
import org.gradle.internal.serialize.OutputStreamBackedEncoder
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.launcher.daemon.context.DefaultDaemonContext
import org.gradle.launcher.daemon.startup.DaemonPriority
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

import static org.gradle.internal.nativeintegration.services.NativeServices.NativeServicesMode
import static org.gradle.launcher.daemon.server.api.DaemonState.Idle

class DaemonRegistryContentTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmp = new TestNameTestDirectoryProvider(getClass())

    def "reads back the addresses it writes"() {
        def content = new DaemonRegistryContent()
        def multiChoice = new MultiChoiceAddress(UUID.randomUUID(), 1234, [InetAddress.loopbackAddress])
        def socket = new SocketInetAddress(InetAddress.loopbackAddress, 5678)
        content.setStatus(multiChoice, daemonInfo(multiChoice))
        content.setStatus(socket, daemonInfo(socket))

        when:
        def read = DaemonRegistryContent.SERIALIZER.read(decoder(write(content)))

        then:
        read.infos*.address == [multiChoice, socket]
    }

    def "does not read an address of any other type"() {
        def bytes = new ByteArrayOutputStream()
        def encoder = new OutputStreamBackedEncoder(bytes)
        encoder.writeBoolean(true)
        encoder.writeInt(1)
        encoder.writeByte((byte) 2)
        encoder.flush()
        new ObjectOutputStream(bytes).withCloseable { it.writeObject("an object") }

        when:
        DaemonRegistryContent.SERIALIZER.read(decoder(bytes.toByteArray()))

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Unexpected address type 2 in the daemon registry."
    }

    def "does not store an address of any other type"() {
        def content = new DaemonRegistryContent()
        def address = Stub(Address)
        content.setStatus(address, daemonInfo(address))

        when:
        write(content)

        then:
        def e = thrown(IllegalArgumentException)
        e.message.startsWith("Cannot store an address of type ")
    }

    private DaemonInfo daemonInfo(Address address) {
        def context = new DefaultDaemonContext(UUID.randomUUID().toString(), Jvm.current().javaHome, JavaLanguageVersion.current(), Jvm.current().vendor, tmp.createDir("daemons"), 1L, 600, [], false, NativeServicesMode.ENABLED, DaemonPriority.NORMAL)
        new DaemonInfo(address, context, "token".bytes, Idle)
    }

    private static byte[] write(DaemonRegistryContent content) {
        def bytes = new ByteArrayOutputStream()
        def encoder = new OutputStreamBackedEncoder(bytes)
        DaemonRegistryContent.SERIALIZER.write(encoder, content)
        encoder.flush()
        bytes.toByteArray()
    }

    private static InputStreamBackedDecoder decoder(byte[] bytes) {
        new InputStreamBackedDecoder(new ByteArrayInputStream(bytes))
    }
}
