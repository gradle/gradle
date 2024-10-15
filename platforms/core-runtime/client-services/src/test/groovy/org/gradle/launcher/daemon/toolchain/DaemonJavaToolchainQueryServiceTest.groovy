/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.launcher.daemon.toolchain

import org.gradle.api.GradleException
import org.gradle.api.JavaVersion
import org.gradle.internal.jvm.Jvm
import org.gradle.internal.jvm.inspection.JavaInstallationRegistry
import org.gradle.internal.jvm.inspection.JvmInstallationMetadata
import org.gradle.internal.jvm.inspection.JvmMetadataDetector
import org.gradle.internal.jvm.inspection.JvmToolchainMetadata
import org.gradle.internal.jvm.inspection.JvmVendor
import org.gradle.internal.os.OperatingSystem
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JvmImplementation
import org.gradle.jvm.toolchain.JvmVendorSpec
import org.gradle.jvm.toolchain.internal.DefaultJvmVendorSpec
import org.gradle.jvm.toolchain.internal.InstallationLocation
import spock.lang.Specification

import java.util.function.Function

import static org.gradle.api.internal.file.TestFiles.systemSpecificAbsolutePath

class DaemonJavaToolchainQueryServiceTest extends Specification {

    def "can query for matching toolchain using version #versionToFind"() {
        given:
        def queryService = createQueryServiceWithInstallations(versionRange(8, 12))

        when:
        def filter = createSpec(JavaLanguageVersion.of(versionToFind))
        def toolchain = queryService.findMatchingToolchain(filter)

        then:
        toolchain.languageVersion == JavaVersion.toVersion(versionToFind)
        toolchain.javaHome.toString() == systemSpecificAbsolutePath(expectedPath)

        where:
        versionToFind | expectedPath
        9             | "/path/9"
        12            | "/path/12"
    }

    def "uses most recent version of multiple matches for version #versionToFind"() {
        given:
        def queryService = createQueryServiceWithInstallations(["8.0", "8.0.242.hs-adpt", "7.9", "7.7", "14.0.2+12", "8.0.zzz.foo"])

        when:
        def filter = createSpec(JavaLanguageVersion.of(versionToFind))
        def toolchain = queryService.findMatchingToolchain(filter)

        then:
        toolchain.languageVersion == JavaVersion.toVersion(versionToFind)
        toolchain.javaHome.toString() == systemSpecificAbsolutePath(expectedPath)

        where:
        versionToFind | expectedPath
        7             | "/path/7.9"
        8             | "/path/8.0.zzz.foo" // zzz resolves to a real tool version 999
        14            | "/path/14.0.2+12"
    }

    def "uses j9 toolchain if requested"() {
        given:
        def queryService = createQueryServiceWithInstallations(["8.0", "8.0.242.hs-adpt", "7.9", "7.7", "14.0.2+12", "8.0.1.j9"])

        when:
        def filter = createSpec(JavaLanguageVersion.of(8), DefaultJvmVendorSpec.any(), JvmImplementation.J9)
        def toolchain = queryService.findMatchingToolchain(filter)

        then:
        toolchain.languageVersion == JavaVersion.VERSION_1_8
        toolchain.javaHome.toString() == systemSpecificAbsolutePath("/path/8.0.1.j9")
    }

    def "no preferred implementation if VENDOR_SPECIFIC is requested"() {
        given:
        def queryService = createQueryServiceWithInstallations(["8.0.2.j9", "8.0.1.hs"])

        when:
        def filter = createSpec(JavaLanguageVersion.of(8), DefaultJvmVendorSpec.any(), JvmImplementation.J9)
        def toolchain = queryService.findMatchingToolchain(filter)

        then:
        toolchain.languageVersion == JavaVersion.VERSION_1_8
        toolchain.javaHome.toString() == systemSpecificAbsolutePath("/path/8.0.2.j9")
    }

    def "matches J9 toolchain via vendor"() {
        given:
        def queryService = createQueryServiceWithInstallations(
            ["8.hs-amazon", "8.j9-international business machines corporation"],
            null,
            version -> version.split("\\.")[0],
            version -> version.split("-")[1]
        )

        when:
        def filter = createSpec(JavaLanguageVersion.of(8), JvmVendorSpec.IBM)
        def toolchain = queryService.findMatchingToolchain(filter)

        then:
        toolchain.vendor.knownVendor == JvmVendor.KnownJvmVendor.IBM
    }

    def "returns toolchain matching vendor"() {
        given:
        def queryService = createQueryServiceWithInstallations(
            ["8-amazon", "8-bellsoft", "8-ibm", "8-zulu"],
            null,
            version -> version.split("-")[0],
            version -> version.split("-")[1]
        )

        when:
        def filter = createSpec(JavaLanguageVersion.of(8), JvmVendorSpec.BELLSOFT)
        def toolchain = queryService.findMatchingToolchain(filter)

        then:
        toolchain.vendor.knownVendor == JvmVendor.KnownJvmVendor.BELLSOFT
    }

    def "ignores invalid toolchains when finding a matching one"() {
        given:
        def queryService = createQueryServiceWithInstallations(["8.0", "8.0.242.hs-adpt", "8.0.broken"])

        when:
        def filter = createSpec(JavaLanguageVersion.of(8))
        def toolchain = queryService.findMatchingToolchain(filter)

        then:
        toolchain.languageVersion == JavaVersion.VERSION_1_8
        toolchain.javaHome.toString() == systemSpecificAbsolutePath("/path/8.0.242.hs-adpt")
    }

    def "prefer version Gradle is running on as long as it is a match"() {
        given:
        def queryService = createQueryServiceWithInstallations(["1.8.1", "1.8.2", "1.8.3"], locationFor("1.8.2"))

        when:
        def filter = createSpec(JavaLanguageVersion.of(8))
        def toolchain = queryService.findMatchingToolchain(filter)

        then:
        toolchain.languageVersion == JavaVersion.VERSION_1_8
        toolchain.javaHome.toString() == systemSpecificAbsolutePath("/path/1.8.2")
    }

    def "fails with expected exception if no toolchain matches with version"() {
        given:
        def queryService = createQueryServiceWithInstallations(["8", "9", "10"])

        when:
        def filter = createSpec(JavaLanguageVersion.of(12))
        queryService.findMatchingToolchain(filter)

        then:
        def e = thrown(GradleException)
        e.message == "Cannot find a Java installation on your machine (${OperatingSystem.current()}) matching: Compatible with Java 12, any vendor (from gradle/gradle-daemon-jvm.properties)."
        e.cause == null
    }

    def "fails with expected exception if no toolchain matches with version and vendor"() {
        given:
        def queryService = createQueryServiceWithInstallations(["8", "9", "10"])

        when:
        def filter = createSpec(JavaLanguageVersion.of(12), JvmVendorSpec.AMAZON)
        queryService.findMatchingToolchain(filter)

        then:
        def e = thrown(GradleException)
        e.message == "Cannot find a Java installation on your machine (${OperatingSystem.current()}) matching: Compatible with Java 12, Amazon Corretto (from gradle/gradle-daemon-jvm.properties)."
        e.cause == null
    }

    private DaemonJavaToolchainQueryService createQueryServiceWithInstallations(
        Collection<String> installations,
        InstallationLocation currentJavaHome = null,
        Function<String, String> getVersion = { it },
        Function<String, String> getVendor = { "" }
    ) {
        def detector = createJvmMetadataDetector(getVersion, getVendor)
        def currentJavaHomePath = currentJavaHome?.location ?: Jvm.current().javaHome

        return new DaemonJavaToolchainQueryService(createInstallationRegistry(installations, detector), currentJavaHomePath)
    }

    private def createInstallationRegistry(Collection<String> locations, JvmMetadataDetector detector) {
        def installations = locations.collect {
            def location = locationFor(it)
            new JvmToolchainMetadata(detector.getMetadata(location), location)
        }

        def registry = Stub(JavaInstallationRegistry)
        registry.toolchains() >> installations
        registry
    }

    private InstallationLocation locationFor(String version) {
        return InstallationLocation.userDefined(new File("/path/${version}").absoluteFile, "test")
    }

    private def createJvmMetadataDetector(
        Function<String, String> getVersion = { it },
        Function<String, String> getVendor = { "" }
    ) {
        return new JvmMetadataDetector() {
            @Override
            JvmInstallationMetadata getMetadata(InstallationLocation javaInstallationLocation) {
                def languageVersion = getVersion.apply(javaInstallationLocation.location.name)
                def vendor = getVendor.apply(javaInstallationLocation.location.name)
                newMetadata(javaInstallationLocation, languageVersion, vendor)
            }
        }
    }

    private def newMetadata(InstallationLocation javaHome, String languageVersion, String vendor = "") {
        def location = javaHome.location
        if (location.name.contains("broken")) {
            return JvmInstallationMetadata.failure(location, "errorMessage")
        }

        String jvmName = ""
        if (location.name.contains("j9")) {
            jvmName = "J9"
        }

        JvmInstallationMetadata.from(
            location.absoluteFile,
            languageVersion.replace("zzz", "999"),
            vendor,
            "",
            "",
            jvmName,
            "",
            "",
            ""
        )
    }

    private def versionRange(int begin, int end) {
        return (begin..end).collect { it.toString() }
    }

    DaemonJvmCriteria.Spec createSpec(JavaLanguageVersion javaVersion, JvmVendorSpec vendor = DefaultJvmVendorSpec.any(), JvmImplementation implementation = JvmImplementation.VENDOR_SPECIFIC) {
        new DaemonJvmCriteria.Spec(javaVersion, vendor, implementation)
    }
}
