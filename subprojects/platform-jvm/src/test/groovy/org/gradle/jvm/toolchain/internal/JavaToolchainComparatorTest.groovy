/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.jvm.toolchain.internal

import org.gradle.internal.jvm.inspection.JvmInstallationMetadata
import org.gradle.internal.jvm.inspection.JvmVendor
import org.gradle.util.internal.VersionNumber
import spock.lang.Specification

class JavaToolchainComparatorTest extends Specification {

    def "prefers higher major versions"() {
        given:
        def toolchains = [
            mockToolchain("6.0"),
            mockToolchain("8.0"),
            mockToolchain("11.0"),
            mockToolchain("5.1")
        ]

        when:
        toolchains.sort(new JavaToolchainComparator())

        then:
        assertOrder(toolchains, "11.0.0", "8.0.0", "6.0.0", "5.1.0")
    }

    def "deterministically matches vendor over vendor-specific tool version"() {
        given:
        def toolchains = [
            mockToolchain("8.2", true, JvmVendor.KnownJvmVendor.AMAZON),
            mockToolchain("8.3", true, JvmVendor.KnownJvmVendor.AMAZON),
            mockToolchain("8.1", true, JvmVendor.KnownJvmVendor.AMAZON),
            mockToolchain("8.8", true, JvmVendor.KnownJvmVendor.BELLSOFT),
            mockToolchain("8.7", true, JvmVendor.KnownJvmVendor.ORACLE),
            mockToolchain("8.4", true, JvmVendor.KnownJvmVendor.UNKNOWN),
        ]

        when:
        toolchains.sort(new JavaToolchainComparator())

        then:
        assertOrder(toolchains, "8.3.0", "8.2.0", "8.1.0", "8.8.0", "8.7.0", "8.4.0")
    }

    def "prefers higher minor versions"() {
        given:
        def toolchains = [
            mockToolchain("8.0.1"),
            mockToolchain("8.0.123"),
            mockToolchain("8.0.1234"),
        ]

        when:
        toolchains.sort(new JavaToolchainComparator())

        then:
        assertOrder(toolchains, "8.0.1234", "8.0.123", "8.0.1")
    }

    def "prefers jdk over jre"() {
        def jdk = Mock(JavaToolchain) {
            getToolVersion() >> VersionNumber.parse("8.0.1")
            isJdk() >> true
        }
        def jre = Mock(JavaToolchain) {
            getToolVersion() >> VersionNumber.parse("8.0.1")
            isJdk() >> false
        }
        given:
        def toolchains = [jre, jdk]

        when:
        toolchains.sort(new JavaToolchainComparator())

        then:
        toolchains == [jdk, jre]
    }

    void assertOrder(List<JavaToolchain> list, String[] expectedOrder) {
        assert list*.toolVersion.toString() == expectedOrder.toString()
    }

    JavaToolchain mockToolchain(String implementationVersion, boolean isJdk = false, JvmVendor.KnownJvmVendor jvmVendor = JvmVendor.KnownJvmVendor.ADOPTOPENJDK) {
        Mock(JavaToolchain) {
            getToolVersion() >> VersionNumber.parse(implementationVersion)
            isJdk() >> isJdk
            getMetadata() >> Mock(JvmInstallationMetadata) {
                getVendor() >> JvmVendor.fromString(jvmVendor.name())
            }
        }
    }
}
