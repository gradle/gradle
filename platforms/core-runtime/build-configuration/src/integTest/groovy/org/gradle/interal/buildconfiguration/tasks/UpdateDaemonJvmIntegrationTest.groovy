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

package org.gradle.interal.buildconfiguration.tasks

import groovy.test.NotYetImplemented
import org.gradle.api.JavaVersion
import org.gradle.buildconfiguration.tasks.UpdateDaemonJvm
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.fixtures.jvm.JavaToolchainFixture
import org.gradle.internal.buildconfiguration.fixture.DaemonJvmPropertiesFixture
import org.gradle.internal.jvm.Jvm
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions

import static org.gradle.jvm.toolchain.JavaToolchainDownloadUtil.applyToolchainResolverPlugin
import static org.gradle.jvm.toolchain.JavaToolchainDownloadUtil.noUrlResolverCode

class UpdateDaemonJvmIntegrationTest extends AbstractIntegrationSpec implements DaemonJvmPropertiesFixture, JavaToolchainFixture {

    def setup() {
        executer.requireDaemon()
        executer.requireIsolatedDaemons()
    }

    def "root project has an updateDaemonJvm task only"() {
        buildFile << """
            def updateDaemonJvm = tasks.named("updateDaemonJvm").get()
            assert updateDaemonJvm instanceof ${UpdateDaemonJvm.class.name}
            assert updateDaemonJvm.description == "Generates or updates the Gradle Daemon JVM criteria."
        """
        settingsFile << """
            rootProject.name = 'root'
            include("sub")
        """
        file("sub").mkdir()

        expect:
        succeeds("help", "--task", "updateDaemonJvm")
        fails(":sub:updateDaemonJvm") // should not exist
    }

    @NotYetImplemented
    def "When execute updateDaemonJvm without toolchain download repositories configured Then fails with expected exception message"() {
        when:
        fails "updateDaemonJvm"

        then:
        // TODO The description is different with CC on
//        failureDescriptionContains("Execution failed for task ':updateDaemonJvm'.")
        failureHasCause("Toolchain download repositories have not been configured.")
    }

    def "When execute updateDaemonJvm without options Then daemon jvm properties are populated with default values"() {
        given:
        settingsFile << applyToolchainResolverPlugin("CustomToolchainResolver", noUrlResolverCode())

        when:
        run "updateDaemonJvm"

        then:
        assertJvmCriteria(Jvm.current().javaVersion)
        outputContains("Daemon JVM criteria is an incubating feature.")
    }

    def "When execute updateDaemonJvm for valid version Then daemon jvm properties are populated with expected values"() {
        given:
        settingsFile << applyToolchainResolverPlugin("CustomToolchainResolver", noUrlResolverCode())

        when:
        run "updateDaemonJvm", "--jvm-version=${version.majorVersion}"

        then:
        assertJvmCriteria(version)

        where:
        version << [JavaVersion.VERSION_11, JavaVersion.VERSION_15, JavaVersion.VERSION_HIGHER]
    }

    def "When execute updateDaemonJvm for valid Java 8 versions Then daemon jvm properties are populated with expected values"() {
        given:
        settingsFile << applyToolchainResolverPlugin("CustomToolchainResolver", noUrlResolverCode())

        when:
        run "updateDaemonJvm", "--jvm-version=8"

        then:
        assertJvmCriteria(JavaVersion.VERSION_1_8)
    }

    def "When execute updateDaemonJvm with invalid argument --jvm-version option Then fails with expected exception message"() {
        given:
        settingsFile << applyToolchainResolverPlugin("CustomToolchainResolver", noUrlResolverCode())

        when:
        fails "updateDaemonJvm", "--jvm-version=$invalidVersion"

        then:
        failureDescriptionContains("Problem configuring option 'jvm-version' on task ':updateDaemonJvm' from command line.")
        failureHasCause("JavaLanguageVersion must be a positive integer, not '${invalidVersion}'")

        where:
        invalidVersion << ["0", "-10", 'asdf']
    }

    def "When execute updateDaemonJvm with unsupported Java version Then fails with expected exception message"() {
        given:
        settingsFile << applyToolchainResolverPlugin("CustomToolchainResolver", noUrlResolverCode())

        when:
        fails "updateDaemonJvm", "--jvm-version=7"

        then:
        failureDescriptionContains("Execution failed for task ':updateDaemonJvm'")
        failureHasCause("Unsupported Java version '7' provided for the 'jvm-version' option. Gradle can only run with Java 8 and above.")
    }

    def "When execute updateDaemonJvm with unsupported future Java version"() {
        given:
        settingsFile << applyToolchainResolverPlugin("CustomToolchainResolver", noUrlResolverCode())

        // Captures current, but maybe not desired behavior
        expect:
        succeeds( "updateDaemonJvm", "--jvm-version=10000")
    }

    def "When execute updateDaemonJvm for valid vendor option Then daemon jvm properties are populated with expected values"() {
        given:
        settingsFile << applyToolchainResolverPlugin("CustomToolchainResolver", noUrlResolverCode())

        when:
        run "updateDaemonJvm", "--jvm-vendor=$vendor"

        then:
        assertJvmCriteria(JavaVersion.current(), vendor)

        where:
        vendor << ["ADOPTIUM", "ADOPTOPENJDK", "AMAZON", "APPLE", "AZUL", "BELLSOFT", "GRAAL_VM", "HEWLETT_PACKARD", "IBM", "JETBRAINS", "MICROSOFT", "ORACLE", "SAP", "TENCENT", "UNKNOWN"]
    }

    @NotYetImplemented
    def "When execute updateDaemonJvm for valid implementation option Then daemon jvm properties are populated with expected values"() {
        given:
        settingsFile << applyToolchainResolverPlugin("CustomToolchainResolver", noUrlResolverCode())

        when:
        run "updateDaemonJvm", "--toolchain-implementation=$implementation"

        then:
        assertJvmCriteria(JavaVersion.current(), null, implementation)

        where:
        implementation << ["VENDOR_SPECIFIC", "J9"]
    }

    def "When execute updateDaemonJvm specifying different options Then daemon jvm properties are populated with expected values"() {
        given:
        settingsFile << applyToolchainResolverPlugin("CustomToolchainResolver", noUrlResolverCode())

        when:
        run "updateDaemonJvm", "--jvm-version=17", "--jvm-vendor=IBM"

        then:
        assertJvmCriteria(JavaVersion.VERSION_17, "IBM")
    }

    def "When execute updateDaemonJvm specifying different options in lower case Then daemon jvm properties are populated with expected values"() {
        given:
        settingsFile << applyToolchainResolverPlugin("CustomToolchainResolver", noUrlResolverCode())

        when:
        run "updateDaemonJvm", "--jvm-version=17", "--jvm-vendor=ibm", "-S"

        then:
        assertJvmCriteria(JavaVersion.VERSION_17, "IBM")
    }

    @NotYetImplemented
    def "When execute updateDaemonJvm with unexpected --jvm-vendor option Then fails with expected exception message"() {
        when:
        fails "updateDaemonJvm", "--jvm-vendor=unknown-vendor"

        then:
        failureDescriptionContains("Value 'unknown-vendor' given for toolchainVendor is an invalid Java vendor. " +
            "Possible values are [ADOPTIUM, ADOPTOPENJDK, AMAZON, APPLE, AZUL, BELLSOFT, GRAAL_VM, HEWLETT_PACKARD, IBM, JETBRAINS, MICROSOFT, ORACLE, SAP, TENCENT, UNKNOWN]")
    }

    @NotYetImplemented
    def "When execute updateDaemonJvm with unexpected --toolchain-implementation option Then fails with expected exception message"() {
        given:
        settingsFile << applyToolchainResolverPlugin("CustomToolchainResolver", noUrlResolverCode())

        when:
        fails "updateDaemonJvm", "--toolchain-implementation=unknown-implementation"

        then:
        failureDescriptionContains("Problem configuring option 'toolchain-implementation' on task ':updateDaemonJvm' from command line.")
        failureHasCause("Cannot convert string value 'unknown-implementation' to an enum value of type 'org.gradle.jvm.toolchain.JvmImplementation' " +
            "(valid case insensitive values: VENDOR_SPECIFIC, J9)")
    }

    def "Given already existing daemon jvm properties When execute updateDaemonJvm with different criteria Then criteria get modified"() {
        given:
        settingsFile << applyToolchainResolverPlugin("CustomToolchainResolver", noUrlResolverCode())
        def otherJvm = AvailableJavaHomes.differentVersion
        writeJvmCriteria(Jvm.current())

        when:
        run "updateDaemonJvm", "--jvm-version=${otherJvm.javaVersion.majorVersion}"

        then:
        assertJvmCriteria(otherJvm.javaVersion)
    }

    @Requires(IntegTestPreconditions.JavaHomeWithDifferentVersionAvailable)
    def "Given defined valid criteria matching with local toolchain When execute updateDaemonJvm with different criteria Then criteria get modified using the expected local toolchain"() {
        given:
        def otherJvm = AvailableJavaHomes.differentVersion
        def otherMetadata = AvailableJavaHomes.getJvmInstallationMetadata(otherJvm)
        writeJvmCriteria(otherJvm.javaVersion, otherMetadata.vendor.knownVendor.name())

        captureJavaHome()
        settingsFile << applyToolchainResolverPlugin("CustomToolchainResolver", noUrlResolverCode())

        expect:
        withInstallations(otherJvm).succeeds("updateDaemonJvm", "--jvm-version=20", "--jvm-vendor=AZUL")
        assertJvmCriteria(JavaVersion.VERSION_20, "AZUL")
        assertDaemonUsedJvm(otherJvm)
    }

    def "Given custom applied toolchain resolver When execute updateDaemonJvm Then daemon jvm properties are populated with download toolchain urls"() {
        given:
        writeJvmCriteria(Jvm.current())
        settingsFile << applyToolchainResolverPlugin("CustomToolchainResolver", """
            @Override
            public Optional<JavaToolchainDownload> resolve(JavaToolchainRequest request) {
                def version = request.getJavaToolchainSpec().getLanguageVersion().get().asInt()
                def vendor = getVendorString(request)
                def operatingSystem = request.getBuildPlatform().operatingSystem
                def architecture = request.getBuildPlatform().architecture
                URI uri = URI.create("https://server?platform=\$operatingSystem.\$architecture&toolchain=\$version.\$vendor")
                return Optional.of(JavaToolchainDownload.fromUri(uri))
            }

            public String getVendorString(JavaToolchainRequest request) {
                def pattern = /vendor matching\\('(.*)'\\)/
                def matcher = request.getJavaToolchainSpec().getVendor().get() =~ pattern
                if (matcher.matches()) {
                    return matcher.group(1)
                }
                return null
            }
        """)

        when:
        run "updateDaemonJvm", "--jvm-version=20", "--jvm-vendor=FOO"

        then:
        assertJvmCriteria(JavaVersion.VERSION_20, "FOO")
        assertToolchainDownloadUrlsProperties([
            ["FREE_BSD", "X86_64"]: "https://server?platform=FREE_BSD.X86_64&toolchain=20.FOO",
            ["FREE_BSD", "AARCH64"]: "https://server?platform=FREE_BSD.AARCH64&toolchain=20.FOO",
            ["LINUX", "X86_64"]: "https://server?platform=LINUX.X86_64&toolchain=20.FOO",
            ["LINUX", "AARCH64"]: "https://server?platform=LINUX.AARCH64&toolchain=20.FOO",
            ["MAC_OS", "X86_64"]: "https://server?platform=MAC_OS.X86_64&toolchain=20.FOO",
            ["MAC_OS", "AARCH64"]: "https://server?platform=MAC_OS.AARCH64&toolchain=20.FOO",
            ["SOLARIS", "X86_64"]: "https://server?platform=SOLARIS.X86_64&toolchain=20.FOO",
            ["SOLARIS", "AARCH64"]: "https://server?platform=SOLARIS.AARCH64&toolchain=20.FOO",
            ["UNIX", "X86_64"]: "https://server?platform=UNIX.X86_64&toolchain=20.FOO",
            ["UNIX", "AARCH64"]: "https://server?platform=UNIX.AARCH64&toolchain=20.FOO",
            ["WINDOWS", "X86_64"]: "https://server?platform=WINDOWS.X86_64&toolchain=20.FOO",
            ["WINDOWS", "AARCH64"]: "https://server?platform=WINDOWS.AARCH64&toolchain=20.FOO",
        ])
    }
}
