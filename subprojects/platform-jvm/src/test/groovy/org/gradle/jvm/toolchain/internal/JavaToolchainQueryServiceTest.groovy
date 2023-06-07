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

import org.gradle.api.GradleException
import org.gradle.api.JavaVersion
import org.gradle.api.internal.file.TestFiles
import org.gradle.internal.jvm.Jvm
import org.gradle.internal.jvm.inspection.JavaInstallationRegistry
import org.gradle.internal.jvm.inspection.JvmInstallationMetadata
import org.gradle.internal.jvm.inspection.JvmMetadataDetector
import org.gradle.internal.jvm.inspection.JvmVendor
import org.gradle.internal.operations.TestBuildOperationExecutor
import org.gradle.internal.os.OperatingSystem
import org.gradle.internal.progress.NoOpProgressLoggerFactory
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainSpec
import org.gradle.jvm.toolchain.JvmImplementation
import org.gradle.jvm.toolchain.JvmVendorSpec
import org.gradle.jvm.toolchain.internal.install.JavaToolchainProvisioningService
import org.gradle.platform.Architecture
import org.gradle.platform.BuildPlatform
import org.gradle.util.TestUtil
import spock.lang.Issue
import spock.lang.Specification

import java.util.function.Function

import static org.gradle.api.internal.file.TestFiles.systemSpecificAbsolutePath
import static org.gradle.internal.jvm.inspection.JvmInstallationMetadata.JavaInstallationCapability.J9_VIRTUAL_MACHINE

class JavaToolchainQueryServiceTest extends Specification {

    def "can query for matching toolchain using version #versionToFind"() {
        given:
        def queryService = setupInstallations(versionRange(8, 12))

        when:
        def filter = new DefaultToolchainSpec(TestUtil.objectFactory())
        filter.languageVersion.set(versionToFind)
        def toolchain = queryService.findMatchingToolchain(filter).get()

        then:
        toolchain.languageVersion == versionToFind
        toolchain.getInstallationPath().toString() == systemSpecificAbsolutePath(expectedPath)

        where:
        versionToFind              | expectedPath
        JavaLanguageVersion.of(9)  | "/path/9"
        JavaLanguageVersion.of(12) | "/path/12"
    }

    def "uses most recent version of multiple matches for version #versionToFind"() {
        given:
        def queryService = setupInstallations(["8.0", "8.0.242.hs-adpt", "7.9", "7.7", "14.0.2+12", "8.0.zzz.foo"])

        when:
        def filter = new DefaultToolchainSpec(TestUtil.objectFactory())
        filter.languageVersion.set(versionToFind)
        def toolchain = queryService.findMatchingToolchain(filter).get()

        then:
        toolchain.languageVersion == versionToFind
        toolchain.getInstallationPath().toString() == systemSpecificAbsolutePath(expectedPath)

        where:
        versionToFind              | expectedPath
        JavaLanguageVersion.of(7)  | "/path/7.9"
        JavaLanguageVersion.of(8)  | "/path/8.0.zzz.foo" // zzz resolves to a real tool version 999
        JavaLanguageVersion.of(14) | "/path/14.0.2+12"
    }

    @Issue("https://github.com/gradle/gradle/issues/17195")
    def "uses most recent version of multiple matches if version has a legacy format"() {
        given:
        def queryService = setupInstallations(["1.8.0_282", "1.8.0_292"])
        def versionToFind = JavaLanguageVersion.of(8)

        when:
        def filter = new DefaultToolchainSpec(TestUtil.objectFactory())
        filter.languageVersion.set(versionToFind)
        def toolchain = queryService.findMatchingToolchain(filter).get()

        then:
        toolchain.languageVersion == versionToFind
        toolchain.getInstallationPath().toString() == systemSpecificAbsolutePath("/path/1.8.0_292")
    }

    def "uses j9 toolchain if requested"() {
        given:
        def queryService = setupInstallations(["8.0", "8.0.242.hs-adpt", "7.9", "7.7", "14.0.2+12", "8.0.1.j9"])

        when:
        def filter = new DefaultToolchainSpec(TestUtil.objectFactory())
        filter.languageVersion.set(JavaLanguageVersion.of(8))
        filter.implementation.set(JvmImplementation.J9)
        def toolchain = queryService.findMatchingToolchain(filter).get()

        then:
        toolchain.languageVersion == JavaLanguageVersion.of(8)
        toolchain.getInstallationPath().toString() == systemSpecificAbsolutePath("/path/8.0.1.j9")
    }

    def "no preferred implementation if vendor-specific is requested"() {
        given:
        def queryService = setupInstallations(["8.0.2.j9", "8.0.1.hs"])

        when:
        def filter = new DefaultToolchainSpec(TestUtil.objectFactory())
        filter.languageVersion.set(JavaLanguageVersion.of(8))
        def toolchain = queryService.findMatchingToolchain(filter).get()

        then:
        toolchain.languageVersion == JavaLanguageVersion.of(8)
        toolchain.getInstallationPath().toString() == systemSpecificAbsolutePath("/path/8.0.2.j9")
    }

    def "matches J9 toolchain via vendor"() {
        given:
        def queryService = setupInstallations(
            ["8.hs-amazon", "8.j9-international business machines corporation"],
            null,
            version -> version.split("\\.")[0],
            version -> version.split("-")[1]
        )

        when:
        def filter = new DefaultToolchainSpec(TestUtil.objectFactory())
        filter.languageVersion.set(JavaLanguageVersion.of(8))
        filter.vendor.set(JvmVendorSpec.IBM)
        def toolchain = queryService.findMatchingToolchain(filter)

        then:
        toolchain.isPresent()
        toolchain.get().metadata.vendor.knownVendor == JvmVendor.KnownJvmVendor.IBM
        toolchain.get().vendor == "IBM"
    }

    def "ignores invalid toolchains when finding a matching one"() {
        given:
        def queryService = setupInstallations(["8.0", "8.0.242.hs-adpt", "8.0.broken"])

        when:
        def filter = new DefaultToolchainSpec(TestUtil.objectFactory())
        filter.languageVersion.set(JavaLanguageVersion.of(8))
        def toolchain = queryService.findMatchingToolchain(filter).get()

        then:
        toolchain.languageVersion.asInt() == 8
        toolchain.getInstallationPath().toString() == systemSpecificAbsolutePath("/path/8.0.242.hs-adpt")
    }

    def "returns failing provider if no toolchain matches"() {
        given:
        def queryService = setupInstallations(["8", "9", "10"])

        when:
        def filter = new DefaultToolchainSpec(TestUtil.objectFactory())
        filter.languageVersion.set(JavaLanguageVersion.of(12))
        def toolchain = queryService.findMatchingToolchain(filter)
        toolchain.get()

        then:
        def e = thrown(NoToolchainAvailableException)
        e.message == "No matching toolchains found for requested specification: {languageVersion=12, vendor=any, implementation=vendor-specific} for LINUX on x86_64."
        e.cause.message == "Configured toolchain download repositories can't match requested specification"
    }

    def "returns current JVM toolchain if requested"() {
        given:
        def currentJvm = locationFor("17")
        def queryService = setupInstallations(versionRange(8, 19), currentJvm)

        when:
        def filter = new CurrentJvmToolchainSpec(TestUtil.objectFactory())
        def toolchain = queryService.findMatchingToolchain(filter)

        then:
        toolchain.isPresent()
        !toolchain.get().isFallbackToolchain()
        toolchain.get().languageVersion == JavaLanguageVersion.of(17)
        toolchain.get().getInstallationPath().toString() == currentJvm.location.absolutePath
    }

    def "returns fallback toolchain if filter is not configured"() {
        given:
        def currentJvm = locationFor("17")
        def queryService = setupInstallations(versionRange(8, 19), currentJvm)

        when:
        def filter = new DefaultToolchainSpec(TestUtil.objectFactory())
        def toolchain = queryService.findMatchingToolchain(filter)

        then:
        toolchain.isPresent()
        toolchain.get().isFallbackToolchain()
        toolchain.get().languageVersion == JavaLanguageVersion.of(17)
        toolchain.get().getInstallationPath().toString() == currentJvm.location.absolutePath
    }

    def "returns non-fallback current JVM toolchain for matching filter"() {
        given:
        def currentJvm = locationFor("17")
        def queryService = setupInstallations(versionRange(8, 19), currentJvm)
        def versionToFind = JavaLanguageVersion.of(17)

        when:
        def filter = new DefaultToolchainSpec(TestUtil.objectFactory())
        filter.languageVersion.set(versionToFind)
        def toolchain = queryService.findMatchingToolchain(filter).get()

        then:
        !toolchain.isFallbackToolchain()
        toolchain.languageVersion == versionToFind
        toolchain.getInstallationPath().toString() == systemSpecificAbsolutePath("/path/17")
    }

    /**
     * This test validates that caching of toolchains works correctly,
     * i.e. that a cached toolchain for the current JVM does not get returned for a non-configured case.
     */
    def "returns fallback toolchain if filter is not configured even after returning current JVM"() {
        given:
        def currentJvm = locationFor("17")
        def queryService = setupInstallations(versionRange(8, 19), currentJvm)

        when:
        def currentJvmFilter = new CurrentJvmToolchainSpec(TestUtil.objectFactory())
        def currentJvmToolchain = queryService.findMatchingToolchain(currentJvmFilter).get()
        then:
        !currentJvmToolchain.isFallbackToolchain()

        when:
        def fallbackFilter = new DefaultToolchainSpec(TestUtil.objectFactory())
        def fallbackToolchain = queryService.findMatchingToolchain(fallbackFilter).get()
        then:
        fallbackToolchain.isFallbackToolchain()
        currentJvmToolchain !== fallbackToolchain
    }

    /**
     * This test validates that caching of toolchains works correctly,
     * i.e. that a cached toolchain for the fallback case does not get returned for a current JVM request case.
     */
    def "returns non-fallback current JVM toolchain if requested even after returning fallback toolchain"() {
        given:
        def currentJvm = locationFor("17")
        def queryService = setupInstallations(versionRange(8, 19), currentJvm)

        when:
        def fallbackFilter = new DefaultToolchainSpec(TestUtil.objectFactory())
        def fallbackToolchain = queryService.findMatchingToolchain(fallbackFilter).get()
        then:
        fallbackToolchain.isFallbackToolchain()

        when:
        def currentJvmFilter = new CurrentJvmToolchainSpec(TestUtil.objectFactory())
        def currentJvmToolchain = queryService.findMatchingToolchain(currentJvmFilter).get()
        then:
        !currentJvmToolchain.isFallbackToolchain()
        currentJvmToolchain !== fallbackToolchain
    }

    def "returns toolchain matching vendor"() {
        given:
        def queryService = setupInstallations(
            ["8-amazon", "8-bellsoft", "8-ibm", "8-zulu"],
            null,
            version -> version.split("-")[0],
            version -> version.split("-")[1]
        )

        when:
        def filter = new DefaultToolchainSpec(TestUtil.objectFactory())
        filter.languageVersion.set(JavaLanguageVersion.of(8))
        filter.vendor.set(JvmVendorSpec.BELLSOFT)
        def toolchain = queryService.findMatchingToolchain(filter)

        then:
        toolchain.isPresent()
        toolchain.get().metadata.vendor.knownVendor == JvmVendor.KnownJvmVendor.BELLSOFT
        toolchain.get().vendor == "BellSoft Liberica"
    }

    def "install toolchain if no matching toolchain found"() {
        given:
        def registry = createInstallationRegistry([])
        def installed = false
        def provisionService = new JavaToolchainProvisioningService() {
            @Override
            boolean isAutoDownloadEnabled() {
                return true
            }

            @Override
            boolean hasConfiguredToolchainRepositories() {
                return true
            }

            File tryInstall(JavaToolchainSpec spec) {
                installed = true
                new File("/path/12")
            }
        }
        def queryService = createQueryService(registry, newJvmMetadataDetector(), provisionService)

        when:
        def filter = new DefaultToolchainSpec(TestUtil.objectFactory())
        filter.languageVersion.set(JavaLanguageVersion.of(12))
        def toolchain = queryService.findMatchingToolchain(filter)
        toolchain.get()

        then:
        installed
    }

    def "handles broken provisioned toolchain"() {
        given:
        def registry = createInstallationRegistry([])
        def installed = false
        def provisionService = new JavaToolchainProvisioningService() {
            @Override
            boolean isAutoDownloadEnabled() {
                return true
            }

            @Override
            boolean hasConfiguredToolchainRepositories() {
                return true
            }

            File tryInstall(JavaToolchainSpec spec) {
                installed = true
                new File("/path/12.broken")
            }
        }
        def queryService = createQueryService(registry, newJvmMetadataDetector(), provisionService)

        when:
        def filter = new DefaultToolchainSpec(TestUtil.objectFactory())
        filter.languageVersion.set(JavaLanguageVersion.of(12))
        def toolchain = queryService.findMatchingToolchain(filter)
        toolchain.get()

        then:
        def e = thrown(GradleException)
        e.message == "Toolchain installation '${File.separator}path${File.separator}12.broken' could not be probed: errorMessage"
    }

    def "provisioned toolchain is cached no re-request"() {
        given:
        def registry = createInstallationRegistry([])
        int installed = 0
        def provisionService = new JavaToolchainProvisioningService() {
            @Override
            boolean isAutoDownloadEnabled() {
                return true
            }

            @Override
            boolean hasConfiguredToolchainRepositories() {
                return true
            }

            File tryInstall(JavaToolchainSpec spec) {
                installed++
                new File("/path/12")
            }
        }
        def queryService = createQueryService(registry, newJvmMetadataDetector(), provisionService)

        when:
        def filter = new DefaultToolchainSpec(TestUtil.objectFactory())
        filter.languageVersion.set(JavaLanguageVersion.of(12))
        def toolchain = queryService.findMatchingToolchain(filter)
        toolchain.get()
        toolchain.get()
        toolchain.get()

        then:
        installed == 1
    }

    def "prefer version Gradle is running on as long as it is a match"() {
        given:
        def queryService = setupInstallations(["1.8.1", "1.8.2", "1.8.3"], locationFor("1.8.2"))
        def versionToFind = JavaLanguageVersion.of(8)

        when:
        def filter = new DefaultToolchainSpec(TestUtil.objectFactory())
        filter.languageVersion.set(versionToFind)
        def toolchain = queryService.findMatchingToolchain(filter).get()

        then:
        toolchain.languageVersion == versionToFind
        toolchain.getInstallationPath().toString() == systemSpecificAbsolutePath("/path/1.8.2")
    }

    private JavaToolchainQueryService setupInstallations(
        Collection<String> installations,
        InstallationLocation currentJvm = null,
        Function<String, String> getVersion = { it },
        Function<String, String> getVendor = { "" }
    ) {
        def detector = newJvmMetadataDetector(getVersion, getVendor)
        def registry = createInstallationRegistry(installations, detector)
        def currentJvmPath = currentJvm?.location ?: Jvm.current().javaHome

        def queryService = createQueryService(registry, detector, createProvisioningService(), currentJvmPath)
        return queryService
    }

    private JavaToolchainProvisioningService createProvisioningService() {
        def provisioningService = Mock(JavaToolchainProvisioningService)
        provisioningService.tryInstall(_ as JavaToolchainSpec) >> { throw new ToolchainDownloadFailedException("Configured toolchain download repositories can't match requested specification") }
        provisioningService
    }

    private def newJvmMetadataDetector(
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

    def newMetadata(InstallationLocation javaHome, String languageVersion, String vendor = "") {
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

    private def createInstallationRegistry(
        Collection<String> installations,
        JvmMetadataDetector detector = newJvmMetadataDetector()
    ) {
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
        def registry = new JavaInstallationRegistry([supplier], detector, new TestBuildOperationExecutor(), OperatingSystem.current(), new NoOpProgressLoggerFactory()) {
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

    private static def versionRange(int begin, int end) {
        return (begin..end).collect { it.toString() }
    }

    private InstallationLocation locationFor(String version){
        return new InstallationLocation(new File("/path/${version}").absoluteFile, "test")
    }

    private JavaToolchainQueryService createQueryService(
        JavaInstallationRegistry registry,
        JvmMetadataDetector detector,
        JavaToolchainProvisioningService provisioningService,
        File currentJavaHome = Jvm.current().getJavaHome()
    ) {
        def buildPlatform = new BuildPlatform() {
            @Override
            org.gradle.platform.OperatingSystem getOperatingSystem() {
                return org.gradle.platform.OperatingSystem.LINUX
            }

            @Override
            Architecture getArchitecture() {
                return Architecture.X86_64
            }
        }
        new JavaToolchainQueryService(registry, detector, TestFiles.fileFactory(), provisioningService, TestUtil.objectFactory(), currentJavaHome, buildPlatform)
    }
}
