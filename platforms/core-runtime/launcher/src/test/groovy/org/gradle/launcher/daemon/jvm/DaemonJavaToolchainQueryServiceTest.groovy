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

package org.gradle.launcher.daemon.jvm

import org.gradle.StartParameter
import org.gradle.api.GradleException
import org.gradle.api.JavaVersion
import org.gradle.internal.jvm.Jvm
import org.gradle.internal.jvm.inspection.JavaInstallationRegistry
import org.gradle.internal.jvm.inspection.JvmInstallationMetadata
import org.gradle.internal.jvm.inspection.JvmInstallationProblemReporter
import org.gradle.internal.jvm.inspection.JvmMetadataDetector
import org.gradle.internal.jvm.inspection.JvmVendor
import org.gradle.internal.operations.TestBuildOperationRunner
import org.gradle.internal.os.OperatingSystem
import org.gradle.internal.progress.NoOpProgressLoggerFactory
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainSpec
import org.gradle.jvm.toolchain.JvmImplementation
import org.gradle.jvm.toolchain.JvmVendorSpec
import org.gradle.jvm.toolchain.internal.DefaultToolchainSpec
import org.gradle.jvm.toolchain.internal.InstallationLocation
import org.gradle.jvm.toolchain.internal.InstallationSupplier
import org.gradle.util.TestUtil
import spock.lang.Specification

import java.util.function.Function

import static org.gradle.api.internal.file.TestFiles.systemSpecificAbsolutePath
import static org.gradle.internal.jvm.inspection.JvmInstallationMetadata.JavaInstallationCapability.J9_VIRTUAL_MACHINE


class DaemonJavaToolchainQueryServiceTest extends Specification {

    def "can query for matching toolchain using version #versionToFind"() {
        given:
        def queryService = createQueryServiceWithInstallations(versionRange(8, 12))

        when:
        def filter = createSpec(versionToFind)
        def toolchain = queryService.findMatchingToolchain(filter, new StartParameter())

        then:
        toolchain.languageVersion == versionToFind
        toolchain.javaHome.toString() == systemSpecificAbsolutePath(expectedPath)

        where:
        versionToFind           | expectedPath
        JavaVersion.VERSION_1_9 | "/path/9"
        JavaVersion.VERSION_12  | "/path/12"
    }

    def "uses most recent version of multiple matches for version #versionToFind"() {
        given:
        def queryService = createQueryServiceWithInstallations(["8.0", "8.0.242.hs-adpt", "7.9", "7.7", "14.0.2+12", "8.0.zzz.foo"])

        when:
        def filter = createSpec(versionToFind)
        def toolchain = queryService.findMatchingToolchain(filter, new StartParameter())

        then:
        toolchain.languageVersion == versionToFind
        toolchain.javaHome.toString() == systemSpecificAbsolutePath(expectedPath)

        where:
        versionToFind           | expectedPath
        JavaVersion.VERSION_1_7 | "/path/7.9"
        JavaVersion.VERSION_1_8 | "/path/8.0.zzz.foo" // zzz resolves to a real tool version 999
        JavaVersion.VERSION_14  | "/path/14.0.2+12"
    }

    def "uses j9 toolchain if requested"() {
        given:
        def queryService = createQueryServiceWithInstallations(["8.0", "8.0.242.hs-adpt", "7.9", "7.7", "14.0.2+12", "8.0.1.j9"])

        when:
        def filter = createSpec(JavaVersion.VERSION_1_8, null, JvmImplementation.J9)
        def toolchain = queryService.findMatchingToolchain(filter, new StartParameter())

        then:
        toolchain.languageVersion == JavaVersion.VERSION_1_8
        toolchain.javaHome.toString() == systemSpecificAbsolutePath("/path/8.0.1.j9")
    }

    def "no preferred implementation if VENDOR_SPECIFIC is requested"() {
        given:
        def queryService = createQueryServiceWithInstallations(["8.0.2.j9", "8.0.1.hs"])

        when:
        def filter = createSpec(JavaVersion.VERSION_1_8, null, JvmImplementation.J9)
        def toolchain = queryService.findMatchingToolchain(filter, new StartParameter())

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
        def filter = createSpec(JavaVersion.VERSION_1_8, JvmVendorSpec.IBM)
        def toolchain = queryService.findMatchingToolchain(filter, new StartParameter())

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
        def filter = createSpec(JavaVersion.VERSION_1_8, JvmVendorSpec.BELLSOFT)
        def toolchain = queryService.findMatchingToolchain(filter, new StartParameter())

        then:
        toolchain.vendor.knownVendor == JvmVendor.KnownJvmVendor.BELLSOFT
    }

    def "ignores invalid toolchains when finding a matching one"() {
        given:
        def queryService = createQueryServiceWithInstallations(["8.0", "8.0.242.hs-adpt", "8.0.broken"])

        when:
        def filter = createSpec(JavaVersion.VERSION_1_8)
        def toolchain = queryService.findMatchingToolchain(filter, new StartParameter())

        then:
        toolchain.languageVersion == JavaVersion.VERSION_1_8
        toolchain.javaHome.toString() == systemSpecificAbsolutePath("/path/8.0.242.hs-adpt")
    }

    def "prefer version Gradle is running on as long as it is a match"() {
        given:
        def queryService = createQueryServiceWithInstallations(["1.8.1", "1.8.2", "1.8.3"], locationFor("1.8.2"))

        when:
        def filter = createSpec(JavaVersion.VERSION_1_8)
        def toolchain = queryService.findMatchingToolchain(filter, new StartParameter())

        then:
        toolchain.languageVersion == JavaVersion.VERSION_1_8
        toolchain.javaHome.toString() == systemSpecificAbsolutePath("/path/1.8.2")
    }

    def "fails with expected exception if no toolchain matches"() {
        given:
        def queryService = createQueryServiceWithInstallations(["8", "9", "10"])

        when:
        def filter = createSpec(JavaVersion.VERSION_12)
        queryService.findMatchingToolchain(filter, new StartParameter())

        then:
        def e = thrown(GradleException)
        e.message == "Cannot find a Java installation on your machine matching the Daemon JVM defined requirements: {languageVersion=12, vendor=any, implementation=vendor-specific} for ${OperatingSystem.current()}."
        e.cause == null
    }

    private DaemonJavaToolchainQueryService createQueryServiceWithInstallations(
        Collection<String> installations,
        InstallationLocation currentJavaHome = null,
        Function<String, String> getVersion = { it },
        Function<String, String> getVendor = { "" }
    ) {
        def detector = createJvmMetadataDetector(getVersion, getVendor)
        def installationRegistryFactory = new SimpleJavaInstallationRegistryFactory(installations, detector)
        def currentJavaHomePath = currentJavaHome?.location ?: Jvm.current().javaHome

        return new DaemonJavaToolchainQueryService(installationRegistryFactory, currentJavaHomePath)
    }

    private def createInstallationRegistry(Collection<String> installations, JvmMetadataDetector detector) {
        def supplier = new InstallationSupplier() {
            @Override
            String getSourceName() {
                "test"
            }

            @Override
            Set<InstallationLocation> get() {
                installations.collect{ locationFor(it) } as Set<InstallationLocation>
            }
        }

        def registry = new JavaInstallationRegistry([supplier], detector, new TestBuildOperationRunner(), OperatingSystem.current(), new NoOpProgressLoggerFactory(), new JvmInstallationProblemReporter()) {
            @Override
            boolean installationExists(InstallationLocation installationLocation) {
                return true
            }

            @Override
            boolean installationHasExecutable(InstallationLocation installationLocation) {
                return true
            }
        }
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

        Mock(JvmInstallationMetadata) {
            getLanguageVersion() >> JavaVersion.toVersion(languageVersion)
            getJavaHome() >> location.absoluteFile.toPath()
            getJavaVersion() >> languageVersion.replace("zzz", "999")
            isValidInstallation() >> true
            getVendor() >> JvmVendor.fromString(vendor)
            hasCapability(_ as JvmInstallationMetadata.JavaInstallationCapability) >> { JvmInstallationMetadata.JavaInstallationCapability capability ->
                if (capability == J9_VIRTUAL_MACHINE) {
                    String name = location.name
                    return name.contains("j9")
                }
                return false
            }
        }
    }

    private def versionRange(int begin, int end) {
        return (begin..end).collect { it.toString() }
    }

    private class SimpleJavaInstallationRegistryFactory implements JavaInstallationRegistryFactory {

        private Collection<String> installations
        private JvmMetadataDetector detector

        SimpleJavaInstallationRegistryFactory(Collection<String> installations, JvmMetadataDetector detector) {
            this.installations = installations
            this.detector = detector
        }

        @Override
        JavaInstallationRegistry getRegistry(StartParameter startParameter) {
            return createInstallationRegistry(installations, detector)
        }
    }

    JavaToolchainSpec createSpec(JavaVersion javaVersion, JvmVendorSpec vendor = null, JvmImplementation implementation = null) {
        def spec = TestUtil.objectFactory().newInstance(DefaultToolchainSpec.class)
        spec.languageVersion.set(JavaLanguageVersion.of(javaVersion.majorVersion))
        spec.vendor.set(vendor)
        spec.implementation.set(implementation)
        spec
    }
}
