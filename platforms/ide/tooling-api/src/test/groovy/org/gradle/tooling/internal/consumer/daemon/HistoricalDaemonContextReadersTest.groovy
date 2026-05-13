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

import org.gradle.internal.serialize.InputStreamBackedDecoder
import org.gradle.internal.serialize.OutputStreamBackedEncoder
import spock.lang.Specification

/**
 * Validates V1, V2, V3 readers against synthetic byte streams that match each era's
 * documented binary layout. We can't easily run a real Gradle 5.0 or 8.5 daemon in a
 * unit test, so this stands in for fixture-based coverage until cross-version
 * integration tests are wired up.
 */
class HistoricalDaemonContextReadersTest extends Specification {

    def "V1 reader (Gradle 5.0 – 8.2): uid, javaHome, registryDir, pid, idle, daemonOpts, priority"() {
        given:
        def out = new ByteArrayOutputStream()
        def encoder = new OutputStreamBackedEncoder(out)
        encoder.with {
            writeNullableString("v1-uid")
            writeString("/opt/jdk8")
            writeString("/.gradle/daemon/8.0")
            writeBoolean(true); writeLong(1111L)
            writeBoolean(true); writeInt(7_200_000)
            writeInt(2); writeString("-Xmx512m"); writeString("-Dfoo")
            writeBoolean(true); writeInt(0)        // priority NORMAL
            flush()
        }

        when:
        def view = new DaemonContextReaderV1().read(new InputStreamBackedDecoder(new ByteArrayInputStream(out.toByteArray())))

        then:
        view.uid == "v1-uid"
        view.javaHome == new File("/opt/jdk8")
        view.javaMajorVersion == null
        view.javaVendor == null
        view.pid == 1111L
        view.idleTimeoutMillis == 7_200_000
        view.daemonOpts == ["-Xmx512m", "-Dfoo"]
    }

    def "V2 reader (Gradle 8.3 – 8.7): adds applyInstrumentationAgent before priority"() {
        given:
        def out = new ByteArrayOutputStream()
        def encoder = new OutputStreamBackedEncoder(out)
        encoder.with {
            writeNullableString("v2-uid")
            writeString("/opt/jdk11")
            writeString("/r")
            writeBoolean(true); writeLong(2222L)
            writeBoolean(true); writeInt(3600_000)
            writeInt(0)
            writeBoolean(true)                      // applyInstrumentationAgent
            writeBoolean(true); writeInt(0)         // priority
            flush()
        }

        when:
        def view = new DaemonContextReaderV2().read(new InputStreamBackedDecoder(new ByteArrayInputStream(out.toByteArray())))

        then:
        view.uid == "v2-uid"
        view.javaHome == new File("/opt/jdk11")
        view.javaMajorVersion == null
        view.javaVendor == null
        view.pid == 2222L
        view.idleTimeoutMillis == 3600_000
        view.daemonOpts == []
    }

    def "V3 reader (Gradle 8.8 – 8.9): adds javaVersion after javaHome and nativeServicesMode before priority"() {
        given:
        def out = new ByteArrayOutputStream()
        def encoder = new OutputStreamBackedEncoder(out)
        encoder.with {
            writeNullableString("v3-uid")
            writeString("/opt/jdk17")
            writeSmallInt(17)                       // javaVersion
            writeString("/r")
            writeBoolean(true); writeLong(3333L)
            writeBoolean(true); writeInt(10_800_000)
            writeInt(1); writeString("-Xmx2g")
            writeBoolean(true)                      // applyInstrumentationAgent
            writeSmallInt(1)                        // nativeServicesMode
            writeBoolean(false)                     // priority null
            flush()
        }

        when:
        def view = new DaemonContextReaderV3().read(new InputStreamBackedDecoder(new ByteArrayInputStream(out.toByteArray())))

        then:
        view.uid == "v3-uid"
        view.javaMajorVersion == 17
        view.javaVendor == null
        view.pid == 3333L
        view.daemonOpts == ["-Xmx2g"]
    }
}
