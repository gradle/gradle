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

import org.gradle.api.JavaVersion
import org.gradle.api.file.Directory
import org.gradle.api.internal.file.FileFactory
import org.gradle.jvm.toolchain.JavaInstallation
import org.gradle.jvm.toolchain.JavaInstallationRegistry
import org.gradle.jvm.toolchain.JavaToolchainSpec
import org.gradle.jvm.toolchain.install.internal.JavaToolchainProvisioningService
import org.gradle.util.TestUtil
import spock.lang.Specification
import spock.lang.Unroll

import static org.gradle.api.internal.file.TestFiles.systemSpecificAbsolutePath

class JavaToolchainQueryServiceTest extends Specification {

    @Unroll
    def "can query for matching toolchain using version #versionToFind"() {
        given:
        def registry = createInstallationRegistry()
        def toolchainFactory = newToolchainFactory()
        def queryService = new JavaToolchainQueryService(registry, toolchainFactory, Mock(JavaToolchainProvisioningService))

        when:
        def filter = new DefaultToolchainSpec(TestUtil.objectFactory())
        filter.languageVersion.set(versionToFind)
        def toolchain = queryService.findMatchingToolchain(filter).get()

        then:
        toolchain.javaMajorVersion == versionToFind
        toolchain.installation.installationDirectory.asFile.absolutePath == systemSpecificAbsolutePath(expectedPath)

        where:
        versionToFind           | expectedPath
        JavaVersion.VERSION_1_9 | "/path/9"
        JavaVersion.VERSION_12  | "/path/12"
    }

    @Unroll
    def "uses most recent version of multiple matches for version #versionToFind"() {
        given:
        def registry = createInstallationRegistry(["8.0", "8.0.242.hs-adpt", "7.9", "7.7", "14.0.2+12"])
        def toolchainFactory = newToolchainFactory()
        def queryService = new JavaToolchainQueryService(registry, toolchainFactory, Mock(JavaToolchainProvisioningService))

        when:
        def filter = new DefaultToolchainSpec(TestUtil.objectFactory())
        filter.languageVersion.set(versionToFind)
        def toolchain = queryService.findMatchingToolchain(filter).get()

        then:
        toolchain.javaMajorVersion == versionToFind
        toolchain.installation.installationDirectory.asFile.absolutePath == systemSpecificAbsolutePath(expectedPath)

        where:
        versionToFind           | expectedPath
        JavaVersion.VERSION_1_7 | "/path/7.9"
        JavaVersion.VERSION_1_8 | "/path/8.0.242.hs-adpt"
        JavaVersion.VERSION_14  | "/path/14.0.2+12"
    }

    def "returns failing provider if no toolchain matches"() {
        given:
        def registry = createInstallationRegistry(["8", "9", "10"])
        def toolchainFactory = newToolchainFactory()
        def provisioningService = Mock(JavaToolchainProvisioningService)
        provisioningService.tryInstall(_) >> Optional.empty()
        def queryService = new JavaToolchainQueryService(registry, toolchainFactory, provisioningService)

        when:
        def filter = new DefaultToolchainSpec(TestUtil.objectFactory())
        filter.languageVersion.set(JavaVersion.VERSION_12)
        def toolchain = queryService.findMatchingToolchain(filter)
        toolchain.get()

        then:
        def e = thrown(NoToolchainAvailableException)
        e.message == "No compatible toolchains found for request filter: {languageVersion=12}"
    }

    def "returns no toolchain if filter is not configured"() {
        given:
        def registry = createInstallationRegistry(["8", "9", "10"])
        def toolchainFactory = newToolchainFactory()
        def queryService = new JavaToolchainQueryService(registry, toolchainFactory, Mock(JavaToolchainProvisioningService))

        when:
        def filter = new DefaultToolchainSpec(TestUtil.objectFactory())
        def toolchain = queryService.findMatchingToolchain(filter)

        then:
        !toolchain.isPresent()
    }

    def "install toolchain if no matching toolchain found"() {
        given:
        def registry = createInstallationRegistry([])
        def toolchainFactory = newToolchainFactory()
        def installed = false
        def provisionService = new JavaToolchainProvisioningService() {
            Optional<File> tryInstall(JavaToolchainSpec spec) {
                installed = true
                Optional.of(new File("/path/12"))
            }
        }
        def queryService = new JavaToolchainQueryService(registry, toolchainFactory, provisionService)

        when:
        def filter = new DefaultToolchainSpec(TestUtil.objectFactory())
        filter.languageVersion.set(JavaVersion.VERSION_12)
        def toolchain = queryService.findMatchingToolchain(filter)
        toolchain.get()

        then:
        installed
    }

    private JavaToolchainFactory newToolchainFactory() {
        def compilerFactory = Mock(JavaCompilerFactory)
        def toolFactory = Mock(ToolchainToolFactory)
        def toolchainFactory = new JavaToolchainFactory(Mock(FileFactory), Mock(JavaInstallationRegistry), compilerFactory, toolFactory) {
            JavaToolchain newInstance(File javaHome) {
                return new JavaToolchain(newInstallation(javaHome), compilerFactory, toolFactory)
            }
        }
        toolchainFactory
    }

    def newInstallation(File javaHome) {
        def installation = Mock(JavaInstallation)
        installation.getJavaVersion() >> JavaVersion.toVersion(javaHome.name)
        def installDir = Mock(Directory)
        installDir.asFile >> javaHome
        installation.installationDirectory >> installDir
        installation
    }

    def createInstallationRegistry(List<String> installations = ["8", "9", "10", "11", "12"]) {
        def supplier = new InstallationSupplier() {
            @Override
            Set<InstallationLocation> get() {
                installations.collect { new InstallationLocation(new File("/path/${it}"), "test") } as Set
            }
        }
        def registry = new SharedJavaInstallationRegistry([supplier]) {
            boolean installationExists(InstallationLocation installationLocation) {
                return true
            }
        }
        registry
    }
}
