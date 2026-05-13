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

import org.gradle.internal.nativeintegration.services.NativeServices.NativeServicesMode
import org.gradle.internal.serialize.InputStreamBackedDecoder
import org.gradle.internal.serialize.OutputStreamBackedEncoder
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.launcher.daemon.configuration.DaemonPriority
import org.gradle.launcher.daemon.context.DefaultDaemonContext
import spock.lang.Specification

class DaemonContextReaderV4Test extends Specification {

    def "decodes current-format context produced by the live serializer"() {
        given:
        def context = new DefaultDaemonContext(
            "test-uid",
            new File("/opt/jdk"),
            JavaLanguageVersion.of(21),
            "Adoptium",
            new File("/.gradle/daemon/9.0.0"),
            12345L,
            10_800_000,
            ["-Xmx1g", "-Dfoo=bar"],
            true,
            NativeServicesMode.ENABLED,
            DaemonPriority.NORMAL
        )

        def bytes = new ByteArrayOutputStream()
        def encoder = new OutputStreamBackedEncoder(bytes)
        DefaultDaemonContext.SERIALIZER.write(encoder, context)
        encoder.flush()

        when:
        def decoder = new InputStreamBackedDecoder(new ByteArrayInputStream(bytes.toByteArray()))
        def view = new DaemonContextReaderV4().read(decoder)

        then:
        view.uid == "test-uid"
        view.javaHome == new File("/opt/jdk")
        view.javaMajorVersion == 21
        view.javaVendor == "Adoptium"
        view.pid == 12345L
        view.idleTimeoutMillis == 10_800_000
        view.daemonOpts == ["-Xmx1g", "-Dfoo=bar"]
    }

    def "decodes context with null pid, null idleTimeout, null priority"() {
        given:
        def context = new DefaultDaemonContext(
            null,
            new File("/jdk"),
            JavaLanguageVersion.of(17),
            "OpenJDK",
            new File("/r"),
            null,
            null,
            [],
            false,
            NativeServicesMode.DISABLED,
            null
        )

        def bytes = new ByteArrayOutputStream()
        def encoder = new OutputStreamBackedEncoder(bytes)
        DefaultDaemonContext.SERIALIZER.write(encoder, context)
        encoder.flush()

        when:
        def decoder = new InputStreamBackedDecoder(new ByteArrayInputStream(bytes.toByteArray()))
        def view = new DaemonContextReaderV4().read(decoder)

        then:
        view.uid == null
        view.javaMajorVersion == 17
        view.javaVendor == "OpenJDK"
        view.pid == null
        view.idleTimeoutMillis == null
        view.daemonOpts == []
    }
}
