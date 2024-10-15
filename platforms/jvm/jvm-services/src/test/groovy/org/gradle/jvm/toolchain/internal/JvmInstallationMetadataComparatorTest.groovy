/*
 * Copyright 2023 the original author or authors.
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

import org.gradle.internal.jvm.inspection.JavaInstallationCapability
import org.gradle.internal.jvm.inspection.JvmInstallationMetadata
import org.gradle.internal.jvm.inspection.JvmInstallationMetadataComparator
import spock.lang.Issue
import spock.lang.Specification

import static org.gradle.internal.jvm.inspection.JvmVendor.KnownJvmVendor
import static org.gradle.internal.jvm.inspection.JvmVendor.KnownJvmVendor.ADOPTOPENJDK
import static org.gradle.internal.jvm.inspection.JvmVendor.KnownJvmVendor.AMAZON
import static org.gradle.internal.jvm.inspection.JvmVendor.KnownJvmVendor.BELLSOFT
import static org.gradle.internal.jvm.inspection.JvmVendor.KnownJvmVendor.ORACLE
import static org.gradle.internal.jvm.inspection.JvmVendor.KnownJvmVendor.UNKNOWN

/**
 * Tests {@link JvmInstallationMetadataComparator}.
 */
class JvmInstallationMetadataComparatorTest extends Specification {

    def "prefers higher major versions"() {
        given:
        def metadata = [
            jvmMetadata("6.0"),
            jvmMetadata("8.0"),
            jvmMetadata("11.0"),
            jvmMetadata("5.1")
        ]

        when:
        metadata.sort(new JvmInstallationMetadataComparator(getJavaHome()))

        then:
        assertOrder(metadata, "11.0", "8.0", "6.0", "5.1")
    }

    def "deterministically matches vendor over vendor-specific tool version"() {
        given:
        def metadata = [
            jvmMetadata("8.2", true, AMAZON),
            jvmMetadata("8.3", true, AMAZON),
            jvmMetadata("8.1", true, AMAZON),
            jvmMetadata("8.8", true, BELLSOFT),
            jvmMetadata("8.7", true, ORACLE),
            jvmMetadata("8.4", true, UNKNOWN),
        ]

        when:
        metadata.sort(new JvmInstallationMetadataComparator(getJavaHome()))

        then:
        assertOrder(metadata, "8.3", "8.2", "8.1", "8.8", "8.7", "8.4")
    }

    def "prefers higher minor versions"() {
        given:
        def metadata = [
            jvmMetadata("8.0.1"),
            jvmMetadata("8.0.123"),
            jvmMetadata("8.0.1234"),
        ]

        when:
        metadata.sort(new JvmInstallationMetadataComparator(getJavaHome()))

        then:
        assertOrder(metadata, "8.0.1234", "8.0.123", "8.0.1")
    }

    def "prefers jdk over jre"() {
        def jdk = jvmMetadata("8.0.1", true)
        def jre = jvmMetadata("8.0.1", false)

        given:
        def metadata = [jre, jdk]

        when:
        metadata.sort(new JvmInstallationMetadataComparator(getJavaHome()))

        then:
        metadata == [jdk, jre]
    }

    @Issue("https://github.com/gradle/gradle/issues/17195")
    def "compares installation paths as a last resort"() {
        given:
        def prevJdk = jvmMetadata("8.0.1", true, ADOPTOPENJDK, "/jdks/openjdk-8.0.1")
        def nextJdk = jvmMetadata("8.0.1", true, ADOPTOPENJDK, "/jdks/openjdk-8.0.1.1")
        def metadata = [prevJdk, nextJdk]

        when:
        metadata.sort(new JvmInstallationMetadataComparator(getJavaHome()))

        then:
        metadata == [nextJdk, prevJdk]
    }

    def "current JVM is picked above all else"() {
        given:
        def metadata = [
            jvmMetadata("8.2", true, AMAZON),
            jvmMetadata("8.3", true, AMAZON),
            jvmMetadata("8.1", true, AMAZON,),
            jvmMetadata("8.8", true, BELLSOFT),
            jvmMetadata("8.7", true, ORACLE),
            jvmMetadata("8.0", false, ORACLE),
            jvmMetadata("8.4", true, UNKNOWN),
        ]

        when:
        metadata.sort(new JvmInstallationMetadataComparator(getJavaHome("8.0", false, null)))

        then:
        assertOrder(metadata, "8.0", "8.3", "8.2", "8.1", "8.8", "8.7", "8.4")
    }

    static void assertOrder(List<JvmInstallationMetadata> list, String[] expectedOrder) {
        assert list*.javaVersion as String[] == expectedOrder
    }

    JvmInstallationMetadata jvmMetadata(String implementationVersion, boolean isJdk = false, KnownJvmVendor jvmVendor = ADOPTOPENJDK, String installPath = null) {
        return Mock(JvmInstallationMetadata) {
            getJavaHome() >> getJavaHome(implementationVersion, isJdk, installPath).toPath()
            getCapabilities() >> (isJdk ? JavaInstallationCapability.JDK_CAPABILITIES : Collections.emptySet())
            getVendor() >> jvmVendor.asJvmVendor()
            getJavaVersion() >> implementationVersion
        }
    }

    private static File getJavaHome(String implementationVersion = "1.1", boolean isJdk = false, String installPath = null) {
        return new File(installPath != null ? installPath : ("/" + (isJdk ? "jdk" : "jre") + "s/" + implementationVersion)).absoluteFile
    }
}
