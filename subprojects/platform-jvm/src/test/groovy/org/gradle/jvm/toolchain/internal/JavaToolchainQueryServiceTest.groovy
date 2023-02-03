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
import org.gradle.internal.jvm.inspection.JvmInstallationMetadata
import org.gradle.internal.jvm.inspection.JvmMetadataDetector
import org.gradle.internal.jvm.inspection.JvmVendor
import org.gradle.internal.operations.BuildOperationProgressEventEmitter
import org.gradle.internal.operations.TestBuildOperationExecutor
import org.gradle.internal.os.OperatingSystem
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainSpec
import org.gradle.jvm.toolchain.JvmImplementation
import org.gradle.jvm.toolchain.JvmVendorSpec
import org.gradle.jvm.toolchain.internal.install.JavaToolchainProvisioningService
import org.gradle.util.TestUtil
import spock.lang.Issue
import spock.lang.Specification

import java.util.function.Function
import java.util.function.Predicate

import static org.gradle.api.internal.file.TestFiles.systemSpecificAbsolutePath
import static org.gradle.internal.jvm.inspection.JvmInstallationMetadata.JavaInstallationCapability.J9_VIRTUAL_MACHINE

class JavaToolchainQueryServiceTest extends Specification {

    def "can query for matching toolchain using version #versionToFind"() {
        given:
        def registry = createInstallationRegistry(versionRange(8, 12))
        def toolchainFactory = newToolchainFactory()
        def queryService = new JavaToolchainQueryService(registry, toolchainFactory, createProvisioningService(), TestUtil.objectFactory())

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
        def registry = createInstallationRegistry(["8.0", "8.0.242.hs-adpt", "7.9", "7.7", "14.0.2+12", "8.0.zzz.foo"])
        def toolchainFactory = newToolchainFactory()
        def queryService = new JavaToolchainQueryService(registry, toolchainFactory, createProvisioningService(), TestUtil.objectFactory())

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
        def registry = createDeterministicInstallationRegistry(["1.8.0_282", "1.8.0_292"])
        def toolchainFactory = newToolchainFactory()
        def queryService = new JavaToolchainQueryService(registry, toolchainFactory, createProvisioningService(), TestUtil.objectFactory())
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
        def registry = createInstallationRegistry(["8.0", "8.0.242.hs-adpt", "7.9", "7.7", "14.0.2+12", "8.0.1.j9"])
        def toolchainFactory = newToolchainFactory()
        def queryService = new JavaToolchainQueryService(registry, toolchainFactory, createProvisioningService(), TestUtil.objectFactory())

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
        def registry = createInstallationRegistry(["8.0.2.j9", "8.0.1.hs"])
        def toolchainFactory = newToolchainFactory()
        def queryService = new JavaToolchainQueryService(registry, toolchainFactory, createProvisioningService(), TestUtil.objectFactory())

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
        def registry = createInstallationRegistry(["8.hs-amazon", "8.j9-international business machines corporation"])
        def compilerFactory = Mock(JavaCompilerFactory)
        def toolFactory = Mock(ToolchainToolFactory)
        def eventEmitter = Stub(BuildOperationProgressEventEmitter)
        def toolchainFactory = new JavaToolchainFactory(Mock(JvmMetadataDetector), compilerFactory, toolFactory, TestFiles.fileFactory(), eventEmitter) {
            @Override
            JavaToolchainInstantiationResult newInstance(InstallationLocation javaHome, JavaToolchainInput input, boolean isFallbackToolchain) {
                String locationName = javaHome.location.name
                def vendor = locationName.substring(5)
                def metadata = newMetadata(new InstallationLocation(new File("/path/" + locationName), javaHome.source), "8", vendor)
                return new JavaToolchainInstantiationResult(javaHome, metadata,
                    new JavaToolchain(metadata, compilerFactory, toolFactory, TestFiles.fileFactory(), input, isFallbackToolchain, eventEmitter))
            }
        }
        def queryService = new JavaToolchainQueryService(registry, toolchainFactory, createProvisioningService(), TestUtil.objectFactory())

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
        def registry = createInstallationRegistry(["8.0", "8.0.242.hs-adpt", "8.0.broken"])
        def toolchainFactory = newToolchainFactory()
        def queryService = new JavaToolchainQueryService(registry, toolchainFactory, createProvisioningService(), TestUtil.objectFactory())

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
        def registry = createInstallationRegistry(["8", "9", "10"])
        def toolchainFactory = newToolchainFactory()
        def queryService = new JavaToolchainQueryService(registry, toolchainFactory, createProvisioningService(), TestUtil.objectFactory())

        when:
        def filter = new DefaultToolchainSpec(TestUtil.objectFactory())
        filter.languageVersion.set(JavaLanguageVersion.of(12))
        def toolchain = queryService.findMatchingToolchain(filter)
        toolchain.get()

        then:
        def e = thrown(NoToolchainAvailableException)
        e.message == "No matching toolchains found for requested specification: {languageVersion=12, vendor=any, implementation=vendor-specific}."
        e.cause.message == "Configured toolchain download repositories can't match requested specification"
    }

    def "returns current JVM toolchain if requested"() {
        given:
        def registry = createInstallationRegistry(versionRange(8, 19))
        def toolchainFactory = newToolchainFactory()
        def queryService = new JavaToolchainQueryService(registry, toolchainFactory, createProvisioningService(), TestUtil.objectFactory())

        when:
        def filter = new CurrentJvmToolchainSpec(TestUtil.objectFactory())
        def toolchain = queryService.findMatchingToolchain(filter)

        then:
        toolchain.isPresent()
        !toolchain.get().isFallbackToolchain()
        toolchain.get().languageVersion == JavaLanguageVersion.of(Jvm.current().javaVersion.majorVersion)
        toolchain.get().getInstallationPath().toString() == Jvm.current().javaHome.absolutePath
    }

    def "returns fallback toolchain if filter is not configured"() {
        given:
        def registry = createInstallationRegistry(versionRange(8, 19))
        def toolchainFactory = newToolchainFactory()
        def queryService = new JavaToolchainQueryService(registry, toolchainFactory, createProvisioningService(), TestUtil.objectFactory())

        when:
        def filter = new DefaultToolchainSpec(TestUtil.objectFactory())
        def toolchain = queryService.findMatchingToolchain(filter)

        then:
        toolchain.isPresent()
        toolchain.get().isFallbackToolchain()
        toolchain.get().languageVersion == JavaLanguageVersion.of(Jvm.current().javaVersion.majorVersion)
        toolchain.get().getInstallationPath().toString() == Jvm.current().javaHome.absolutePath
    }

    def "returns non-fallback current JVM toolchain for matching filter"() {
        given:
        def registry = createInstallationRegistry(versionRange(8, 19))
        def toolchainFactory = newToolchainFactory(javaHome -> javaHome.name, javaHome -> javaHome.name == "17")
        def queryService = new JavaToolchainQueryService(registry, toolchainFactory, createProvisioningService(), TestUtil.objectFactory())
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
        def registry = createInstallationRegistry(versionRange(8, 19))
        def toolchainFactory = newToolchainFactory()
        def queryService = new JavaToolchainQueryService(registry, toolchainFactory, createProvisioningService(), TestUtil.objectFactory())

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
        def registry = createInstallationRegistry(versionRange(8, 19))
        def toolchainFactory = newToolchainFactory()
        def queryService = new JavaToolchainQueryService(registry, toolchainFactory, createProvisioningService(), TestUtil.objectFactory())

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
        def registry = createInstallationRegistry(["8-amazon", "8-bellsoft", "8-ibm", "8-zulu"])
        def compilerFactory = Mock(JavaCompilerFactory)
        def toolFactory = Mock(ToolchainToolFactory)
        def eventEmitter = Stub(BuildOperationProgressEventEmitter)
        def toolchainFactory = new JavaToolchainFactory(Mock(JvmMetadataDetector), compilerFactory, toolFactory, TestFiles.fileFactory(), eventEmitter) {
            @Override
            JavaToolchainInstantiationResult newInstance(InstallationLocation javaHome, JavaToolchainInput input, boolean isFallbackToolchain) {
                def vendor = javaHome.location.name.substring(2)
                def metadata = newMetadata(new InstallationLocation(new File("/path/8"), javaHome.source), "8", vendor)
                return new JavaToolchainInstantiationResult(javaHome, metadata,
                        new JavaToolchain(metadata, compilerFactory, toolFactory, TestFiles.fileFactory(), input, isFallbackToolchain, eventEmitter))
            }
        }
        def queryService = new JavaToolchainQueryService(registry, toolchainFactory, createProvisioningService(), TestUtil.objectFactory())

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
        def toolchainFactory = newToolchainFactory()
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
        def queryService = new JavaToolchainQueryService(registry, toolchainFactory, provisionService, TestUtil.objectFactory())

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
        def toolchainFactory = newToolchainFactory()
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
        def queryService = new JavaToolchainQueryService(registry, toolchainFactory, provisionService, TestUtil.objectFactory())

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
        def toolchainFactory = newToolchainFactory()
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
        def queryService = new JavaToolchainQueryService(registry, toolchainFactory, provisionService, TestUtil.objectFactory())

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
        def registry = createDeterministicInstallationRegistry(["1.8.1", "1.8.2", "1.8.3"])
        def toolchainFactory = newToolchainFactory(javaHome -> javaHome.name, javaHome -> javaHome.name == "1.8.2")
        def queryService = new JavaToolchainQueryService(registry, toolchainFactory, createProvisioningService(), TestUtil.objectFactory())
        def versionToFind = JavaLanguageVersion.of(8)

        when:
        def filter = new DefaultToolchainSpec(TestUtil.objectFactory())
        filter.languageVersion.set(versionToFind)
        def toolchain = queryService.findMatchingToolchain(filter).get()

        then:
        toolchain.languageVersion == versionToFind
        toolchain.getInstallationPath().toString() == systemSpecificAbsolutePath("/path/1.8.2")
    }

    private JavaToolchainProvisioningService createProvisioningService() {
        def provisioningService = Mock(JavaToolchainProvisioningService)
        provisioningService.tryInstall(_ as JavaToolchainSpec) >> { throw new ToolchainDownloadFailedException("Configured toolchain download repositories can't match requested specification") }
        provisioningService
    }

    private JavaToolchainFactory newToolchainFactory() {
        Predicate<File> isCurrentJvm = { it -> Jvm.current().javaHome.absoluteFile == it.absoluteFile }
        def getVersion = { File javaHome -> isCurrentJvm(javaHome) ? Jvm.current().javaVersion.toString() : javaHome.name }
        newToolchainFactory(getVersion, isCurrentJvm)
    }

    private JavaToolchainFactory newToolchainFactory(Function<File, String> getVersion, Predicate<File> isCurrentJvm) {
        def compilerFactory = Mock(JavaCompilerFactory)
        def toolFactory = Mock(ToolchainToolFactory)
        def eventEmitter = Stub(BuildOperationProgressEventEmitter)
        def toolchainFactory = new JavaToolchainFactory(Mock(JvmMetadataDetector), compilerFactory, toolFactory, TestFiles.fileFactory(), eventEmitter) {
            @Override
            JavaToolchainInstantiationResult newInstance(InstallationLocation javaHome, JavaToolchainInput input, boolean isFallbackToolchain) {
                def languageVersion = Jvm.current().javaHome == javaHome.location ? Jvm.current().javaVersion.toString() : getVersion.apply(javaHome.location)
                def metadata = newMetadata(javaHome, languageVersion)
                if (metadata.isValidInstallation()) {
                    def toolchain = new JavaToolchain(metadata, compilerFactory, toolFactory, TestFiles.fileFactory(), input, isFallbackToolchain, eventEmitter) {
                        @Override
                        boolean isCurrentJvm() {
                            return isCurrentJvm.test(javaHome.location)
                        }
                    }
                    return new JavaToolchainInstantiationResult(javaHome, metadata, toolchain)
                }
                return new JavaToolchainInstantiationResult(javaHome, metadata)
            }
        }
        toolchainFactory
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

    static def createInstallationRegistry(List<String> installations) {
        def supplier = new InstallationSupplier() {
            @Override
            Set<InstallationLocation> get() {
                installations.collect { new InstallationLocation(new File("/path/${it}").absoluteFile, "test") } as Set
            }
        }
        def registry = new JavaInstallationRegistry([supplier], new TestBuildOperationExecutor(), OperatingSystem.current()) {
            boolean installationExists(InstallationLocation installationLocation) {
                return true
            }
            boolean installationHasExecutable(InstallationLocation installationLocation) {
                return true
            }
        }
        registry
    }

    private static def versionRange(int begin, int end) {
        return (begin..end).collect { it.toString() }
    }

    def createDeterministicInstallationRegistry(List<String> installations) {
        def installationLocations = installations.collect { new InstallationLocation(new File("/path/${it}").absoluteFile, "test") } as LinkedHashSet
        Mock(JavaInstallationRegistry) {
            listInstallations() >> installationLocations
            installationExists(_ as InstallationLocation) >> true
        }
    }
}
