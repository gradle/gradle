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

package org.gradle.internal.jvm.inspection


import org.gradle.api.logging.Logger
import org.gradle.internal.operations.TestBuildOperationRunner
import org.gradle.internal.os.OperatingSystem
import org.gradle.internal.progress.RecordingProgressLoggerFactory
import org.gradle.jvm.toolchain.internal.DefaultToolchainConfiguration
import org.gradle.jvm.toolchain.internal.InstallationLocation
import org.gradle.jvm.toolchain.internal.InstallationSupplier
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class DefaultJavaInstallationRegistryTest extends Specification {
    @Rule
    private final TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider(getClass())

    def toolchainConfiguration = new DefaultToolchainConfiguration()
    def jvmMetadataDetector = Mock(JvmMetadataDetector)
    def operations = new TestBuildOperationRunner()
    def loggerFactory = new RecordingProgressLoggerFactory()
    def logger = Mock(Logger)

    def "registry keeps track of initial installations"() {
        def jdk8 = createJdkInstallation("8")
        when:
        def registry = createRegistry([jdk8])
        def installations = registry.listInstallations()

        then:
        installations.size() == 1
        installations[0].location == jdk8
        installations[0].source == "testSource"
    }

    def "registry filters duplicate locations"() {
        def jdk8 = createJdkInstallation("8")
        when:
        def registry = createRegistry([jdk8, jdk8])
        def installations = registry.listInstallations()

        then:
        installations.size() == 1
        installations[0].location == jdk8
        installations[0].source == "testSource"
    }

    def "duplicates are detected using canonical form"() {
        given:
        def jdk8 = createJdkInstallation("8")

        when:
        def registry = createRegistry([jdk8, new File(jdk8, "/.")])
        def installations = registry.listInstallations()

        then:
        installations.size() == 1
        installations[0].location == jdk8
        installations[0].source == "testSource"
    }

    def "can be initialized with suppliers"() {
        given:
        def jdk8 = createJdkInstallation("8")
        def jdk11 = createJdkInstallation("11")
        def jdk15 = createJdkInstallation("15")

        when:
        def registry = createRegistry([jdk8, jdk11, jdk15])
        def installations = registry.listInstallations()

        then:
        installations.size() == 3
        installations*.location.containsAll(jdk8, jdk11, jdk15)
        installations*.source.unique() == ["testSource"]
    }

    def "list of installations is cached"() {
        def jdk8 = createJdkInstallation("8")
        when:
        def registry = createRegistry([jdk8])
        def installations = registry.listInstallations()
        def installations2 = registry.listInstallations()

        then:
        installations.is(installations2)
    }

    def "normalize installations to account for macOS folder layout"() {
        given:
        def jdkHome = temporaryFolder.createDir("jdk")
        def macOsJdkHome = jdkHome.createDir("Contents/Home")
        def binDir = macOsJdkHome.createDir("bin")
        binDir.createFile(OperatingSystem.MAC_OS.getExecutableName("java"))

        when:
        def registry = createRegistry([jdkHome], OperatingSystem.MAC_OS)
        def installations = registry.listInstallations()

        then:
        installations.size() == 1
        installations[0].location == macOsJdkHome
        installations[0].source == "testSource"
    }

    def "normalize installations to account for standalone jre"() {
        given:
        def jre = createJreInstallation("8")

        when:
        def registry = createRegistry([jre])
        def installations = registry.listInstallations()

        then:
        installations.size() == 1
        installations[0].location == jre
        installations[0].source == "testSource"
    }

    def "skip path normalization on non-osx systems"() {
        given:
        def jdkHome = temporaryFolder.createDir("jdk")
        def binDir = jdkHome.createDir("bin")
        binDir.createFile(OperatingSystem.LINUX.getExecutableName("java"))

        // Make it look like a macOS installation
        def macOsJdkHomeBinDir = jdkHome.createDir("Contents/Home/bin")
        macOsJdkHomeBinDir.createFile(OperatingSystem.LINUX.getExecutableName("java"))

        when:
        def registry = createRegistry([jdkHome], OperatingSystem.LINUX)
        def installations = registry.listInstallations()

        then:
        installations.size() == 1
        installations[0].location == jdkHome
        installations[0].source == "testSource"
    }

    def "detecting installations is tracked as build operation"() {
        given:
        def registry = createRegistry([])

        when:
        registry.listInstallations()

        then:
        operations.log.getDescriptors().find { it.displayName == "Toolchain detection" }
    }

    def "filters always and warns once about an installation that is not a directory"() {
        given:
        def jdk8 = temporaryFolder.createFile("not-a-directory")
        def logOutput = "Path for java installation '${jdk8}' (testSource) points to a file, not a directory"
        when:
        def registry = createRegistry([jdk8])
        def installations = registry.listInstallations()

        then:
        installations.isEmpty()
        1 * logger.log(_, logOutput)

        when:
        installations = registry.listInstallations()

        then:
        installations.isEmpty()
        0 * logger._
    }

    def "filters always and warns once about an installation that does not exist"() {
        given:
        def jdk8 = temporaryFolder.createDir("non-existent")
        jdk8.deleteDir()
        def logOutput = "Directory '${jdk8}' (testSource) used for java installations does not exist"
        when:
        def registry = createRegistry([jdk8])
        def installations = registry.listInstallations()

        then:
        installations.isEmpty()
        1 * logger.log(_, logOutput)

        when:
        installations = registry.listInstallations()

        then:
        installations.isEmpty()
        0 * logger._
    }

    def "filters always and warns once about an installation without java executable"() {
        given:
        def jdk8 = createJdkInstallation("8")
        jdk8.file("bin").deleteDir() // break the installation
        def logOutput = "Path for java installation '" + jdk8 + "' (testSource) does not contain a java executable"
        when:
        def registry = createRegistry([jdk8])
        def installations = registry.listInstallations()

        then:
        installations.isEmpty()
        1 * logger.log(_, logOutput)

        when:
        installations = registry.listInstallations()

        then:
        installations.isEmpty()
        0 * logger._
    }

    def "can detect enclosed jre installations"() {
        given:
        def jre = createJreInstallation("8")

        when:
        def registry = createRegistry([jre])
        def installations = registry.listInstallations()

        then:
        installations.size() == 1
        installations[0].location == jre
        installations[0].source == "testSource"
    }

    def "displays progress"() {
        def jdk8 = createJdkInstallation("8")
        when:
        def registry = createRegistry([jdk8])
        def toolchains = registry.toolchains()

        then:
        toolchains.size() == 1
        loggerFactory.recordedMessages.find { it.contains("Extracting toolchain metadata from '$jdk8'") }
    }

    private TestFile createJdkInstallation(String version) {
        def jdkHome = temporaryFolder.createDir("jdk-$version")
        def binDir = jdkHome.createDir("bin")
        binDir.createFile(OperatingSystem.current().getExecutableName("java"))
        return jdkHome
    }

    private TestFile createJreInstallation(String version) {
        def jdkHome = temporaryFolder.createDir("jdk-$version")
        def jreHome = jdkHome.file("jre").createDir()
        def binDir = jreHome.createDir("bin")
        binDir.createFile(OperatingSystem.current().getExecutableName("java"))

        def jdkHomeBinDir = jdkHome.createDir("bin")
        jdkHomeBinDir.createFile(OperatingSystem.current().getExecutableName("java"))
        return jdkHome
    }

    private DefaultJavaInstallationRegistry createRegistry(List<File> location, OperatingSystem os = OperatingSystem.current()) {
        def installations = Mock(InstallationSupplier)
        installations.sourceName >> "testSource"
        installations.get() >> location.collect { InstallationLocation.userDefined(it, "testSource") }

        return new DefaultJavaInstallationRegistry(
            toolchainConfiguration,
            [],
            [ installations ],
            jvmMetadataDetector,
            logger,
            operations,
            os,
            loggerFactory,
            new JvmInstallationProblemReporter(),
        )
    }
}
