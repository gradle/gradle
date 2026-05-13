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

/**
 * Guards against silent drift of the daemon context binary format.
 *
 * <p>If this test fails, the live {@link DefaultDaemonContext.Serializer} has changed
 * and the Tooling API's frozen-format readers under
 * {@code org.gradle.tooling.internal.consumer.daemon} are out of date.
 *
 * <p>To fix:
 * <ol>
 *   <li>Add a new {@code DaemonContextReaderVN} that mirrors the new format.</li>
 *   <li>Add a new branch in {@link DaemonContextReaders#forVersion(String)} keyed
 *       to the Gradle version that introduces the change.</li>
 *   <li>Update this test's reader reference to the new {@code VN}.</li>
 * </ol>
 *
 * <p>The point of the test is to make the cross-version impact of a format change
 * loud rather than silent.
 */
class CurrentFormatDriftTest extends Specification {

    def "current DefaultDaemonContext.SERIALIZER output is parseable by the current frozen reader"() {
        given:
        def context = new DefaultDaemonContext(
            "drift-uid",
            new File("/jdk"),
            JavaLanguageVersion.of(21),
            "DriftVendor",
            new File("/r"),
            999L,
            60_000,
            ["-Xmx512m"],
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
        // Crucially, also that the decoder did not over- or under-consume —
        // any trailing bytes would surface as we compare position.
        view.uid == "drift-uid"
        view.javaMajorVersion == 21
        view.javaVendor == "DriftVendor"
        view.pid == 999L
        view.idleTimeoutMillis == 60_000
        view.daemonOpts == ["-Xmx512m"]
    }
}
