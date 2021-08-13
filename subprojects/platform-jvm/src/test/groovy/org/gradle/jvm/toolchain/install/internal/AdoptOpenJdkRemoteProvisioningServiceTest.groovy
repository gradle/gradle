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

package org.gradle.jvm.toolchain.install.internal

import net.rubygrapefruit.platform.SystemInfo
import org.gradle.api.internal.provider.Providers
import org.gradle.api.provider.ProviderFactory
import org.gradle.internal.os.OperatingSystem
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainCandidate
import org.gradle.jvm.toolchain.JavaToolchainProvisioningDetails
import org.gradle.jvm.toolchain.JavaToolchainSpec
import org.gradle.jvm.toolchain.JvmImplementation
import org.gradle.jvm.toolchain.JvmVendorSpec
import org.gradle.jvm.toolchain.internal.DefaultJvmVendorSpec
import org.gradle.jvm.toolchain.internal.DefaultToolchainSpec
import org.gradle.util.TestUtil
import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Unroll

class AdoptOpenJdkRemoteProvisioningServiceTest extends Specification {

    @TempDir
    public File temporaryFolder

    @Unroll
    def "generates url for jdk #jdkVersion on #operatingSystemName (#architecture)"() {
        given:
        def spec = newSpec(jdkVersion)
        def operatingSystem = OperatingSystem.forName(operatingSystemName)
        def provisioner = new AdoptOpenJdkRemoteProvisioningService(Mock(JdkDownloader), providerFactory())
        def details = newDetails(spec, operatingSystem, systemInfoFor(architecture))
        provisioner.findCandidates(details)
        def candidate = details.candidates.get()[0]

        when:
        def uri = provisioner.constructUri(candidate)

        then:
        uri.toString() == "https://api.adoptopenjdk.net/v3/binary/latest" + expectedPath

        where:
        jdkVersion | operatingSystemName | architecture                    | expectedPath
        11         | "Windows"           | SystemInfo.Architecture.amd64   | "/11/ga/windows/x64/jdk/hotspot/normal/adoptopenjdk"
        12         | "Windows"           | SystemInfo.Architecture.i386    | "/12/ga/windows/x32/jdk/hotspot/normal/adoptopenjdk"
        13         | "Windows"           | SystemInfo.Architecture.aarch64 | "/13/ga/windows/aarch64/jdk/hotspot/normal/adoptopenjdk"
        11         | "Linux"             | SystemInfo.Architecture.amd64   | "/11/ga/linux/x64/jdk/hotspot/normal/adoptopenjdk"
        12         | "Linux"             | SystemInfo.Architecture.i386    | "/12/ga/linux/x32/jdk/hotspot/normal/adoptopenjdk"
        13         | "Linux"             | SystemInfo.Architecture.aarch64 | "/13/ga/linux/aarch64/jdk/hotspot/normal/adoptopenjdk"
        11         | "Mac OS X"          | SystemInfo.Architecture.amd64   | "/11/ga/mac/x64/jdk/hotspot/normal/adoptopenjdk"
        12         | "Darwin"            | SystemInfo.Architecture.i386    | "/12/ga/mac/x32/jdk/hotspot/normal/adoptopenjdk"
        13         | "OSX"               | SystemInfo.Architecture.aarch64 | "/13/ga/mac/aarch64/jdk/hotspot/normal/adoptopenjdk"
        13         | "Solaris"           | SystemInfo.Architecture.i386    | "/13/ga/solaris/x32/jdk/hotspot/normal/adoptopenjdk"
    }

    @Unroll
    def "generates filename for jdk #jdkVersion on #operatingSystemName (#architecture)"() {
        given:
        def spec = newSpec(jdkVersion)

        def operatingSystem = OperatingSystem.forName(operatingSystemName)
        def provisioner = new AdoptOpenJdkRemoteProvisioningService(Mock(JdkDownloader), providerFactory())
        def details = newDetails(spec, operatingSystem, systemInfoFor(architecture))
        provisioner.findCandidates(details)
        def candidate = details.candidates.get()[0]
        def handler = provisioner.provisionerFor(candidate)

        when:
        def filename = handler.fileName

        then:
        filename == expectedFilename

        where:
        jdkVersion | operatingSystemName | architecture                    | expectedFilename
        11         | "Windows"           | SystemInfo.Architecture.amd64   | "adoptopenjdk-11-x64-windows.zip"
        12         | "Windows"           | SystemInfo.Architecture.i386    | "adoptopenjdk-12-x32-windows.zip"
        13         | "Windows"           | SystemInfo.Architecture.aarch64 | "adoptopenjdk-13-aarch64-windows.zip"
        11         | "Linux"             | SystemInfo.Architecture.amd64   | "adoptopenjdk-11-x64-linux.tar.gz"
        12         | "Linux"             | SystemInfo.Architecture.i386    | "adoptopenjdk-12-x32-linux.tar.gz"
        13         | "Linux"             | SystemInfo.Architecture.aarch64 | "adoptopenjdk-13-aarch64-linux.tar.gz"
        11         | "Mac OS X"          | SystemInfo.Architecture.amd64   | "adoptopenjdk-11-x64-mac.tar.gz"
        12         | "Darwin"            | SystemInfo.Architecture.i386    | "adoptopenjdk-12-x32-mac.tar.gz"
        13         | "OSX"               | SystemInfo.Architecture.aarch64 | "adoptopenjdk-13-aarch64-mac.tar.gz"
        13         | "Solaris"           | SystemInfo.Architecture.i386    | "adoptopenjdk-13-x32-solaris.tar.gz"
    }

    @Unroll
    def "uses configured base uri #customBaseUrl if available"() {
        given:
        def spec = newSpec()
        def operatingSystem = OperatingSystem.MAC_OS
        def providerFactory = providerFactory(Providers.of(customBaseUrl))
        def provisioner = new AdoptOpenJdkRemoteProvisioningService(Mock(JdkDownloader), providerFactory)
        def details = newDetails(spec, operatingSystem, systemInfoFor(SystemInfo.Architecture.amd64))
        provisioner.findCandidates(details)
        def candidate = details.candidates.get()[0]

        when:
        def uri = provisioner.constructUri(candidate)

        then:
        uri.toString() == "http://foobar/v3/binary/latest/11/ga/mac/x64/jdk/hotspot/normal/adoptopenjdk"

        where:
        customBaseUrl << ["http://foobar", "http://foobar/"]
    }

    def "downloads from url"() {
        given:
        def spec = newSpec(12)
        def downloader = Mock(JdkDownloader)
        def provisioner = new AdoptOpenJdkRemoteProvisioningService(downloader, providerFactory())
        def details = newDetails(spec, OperatingSystem.MAC_OS, systemInfoFor(SystemInfo.Architecture.amd64))
        provisioner.findCandidates(details)
        def candidate = details.candidates.get()[0]

        when:
        def targetFile = new File(temporaryFolder, "jdk")
        provisioner.provisionerFor(candidate).provision(targetFile)

        then:
        1 * downloader.download(URI.create("https://api.adoptopenjdk.net/v3/binary/latest/12/ga/mac/x64/jdk/hotspot/normal/adoptopenjdk"), _)
    }

    @Unroll
    def "skips downloading unsupported java version #javaVersion"() {
        given:
        def spec = newSpec(javaVersion)
        def downloader = Mock(JdkDownloader)
        def provisioner = new AdoptOpenJdkRemoteProvisioningService(downloader, providerFactory())
        def details = newDetails(spec, OperatingSystem.MAC_OS, systemInfoFor(SystemInfo.Architecture.amd64))
        provisioner.findCandidates(details)

        when:
        def candidates = details.candidates

        then:
        !candidates.present

        where:
        javaVersion << [5, 6, 7]
    }

    @Unroll
    def "skips downloading unsupported vendor #vendor"() {
        given:
        def spec = newSpec(8)
        spec.vendor.set(vendor)
        def downloader = Mock(JdkDownloader)
        def provisioner = new AdoptOpenJdkRemoteProvisioningService(downloader, providerFactory())
        def details = newDetails(spec, OperatingSystem.MAC_OS, systemInfoFor(SystemInfo.Architecture.amd64))
        provisioner.findCandidates(details)

        when:
        def candidates = details.candidates

        then:
        !candidates.present

        where:
        vendor << [JvmVendorSpec.AMAZON, JvmVendorSpec.IBM]
    }

    @Unroll
    def "downloads with matching vendor spec using #vendor"() {
        given:
        def spec = newSpec(12)
        spec.vendor.set(vendor)
        def downloader = Mock(JdkDownloader)
        def provisioner = new AdoptOpenJdkRemoteProvisioningService(downloader, providerFactory())
        def details = newDetails(spec, OperatingSystem.MAC_OS, systemInfoFor(SystemInfo.Architecture.amd64))
        provisioner.findCandidates(details)
        def candidate = details.candidates.get()[0]

        when:
        def targetFile = new File(temporaryFolder, "jdk")
        provisioner.provisionerFor(candidate).provision(targetFile)

        then:
        1 * downloader.download(URI.create("https://api.adoptopenjdk.net/v3/binary/latest/12/ga/mac/x64/jdk/hotspot/normal/adoptopenjdk"), _)

        where:
        vendor << [JvmVendorSpec.ADOPTOPENJDK, JvmVendorSpec.matching("adoptopenjdk"), DefaultJvmVendorSpec.any()]
    }

    def "downloads j9 impl if requested"() {
        given:
        def spec = newSpec(12)
        spec.implementation.set(JvmImplementation.J9)
        def downloader = Mock(JdkDownloader)
        def provisioner = new AdoptOpenJdkRemoteProvisioningService(downloader, providerFactory())
        def details = newDetails(spec, OperatingSystem.MAC_OS, systemInfoFor(SystemInfo.Architecture.amd64))
        provisioner.findCandidates(details)
        def candidate = details.candidates.get()[0]

        when:
        def targetFile = new File(temporaryFolder, "jdk")
        provisioner.provisionerFor(candidate).provision(targetFile)

        then:
        1 * downloader.download(URI.create("https://api.adoptopenjdk.net/v3/binary/latest/12/ga/mac/x64/jdk/openj9/normal/adoptopenjdk"), _)
    }

    JavaToolchainSpec newSpec(int jdkVersion = 11) {
        def spec = new DefaultToolchainSpec(TestUtil.objectFactory())
        spec.languageVersion.set(JavaLanguageVersion.of(jdkVersion))
        spec
    }

    SystemInfo systemInfoFor(SystemInfo.Architecture arch) {
        Stub(SystemInfo) {
            getArchitecture() >> arch
            getArchitectureName() >> arch.toString()
        }
    }

    JavaToolchainProvisioningDetails newDetails(JavaToolchainSpec spec, OperatingSystem os, SystemInfo info) {
        new JavaToolchainProvisioningDetailsInternal() {
            List<JavaToolchainCandidate> candidates

            @Override
            Optional<List<JavaToolchainCandidate>> getCandidates() {
                Optional.ofNullable(candidates)
            }

            @Override
            JavaToolchainSpec getRequested() {
                spec
            }

            @Override
            JavaToolchainCandidate.Builder newCandidate() {
                new DefaultJavaToolchainCandidateBuilder(operatingSystem, systemArch)
            }

            @Override
            void listCandidates(List<JavaToolchainCandidate> candidates) {
                this.candidates = candidates
            }

            @Override
            String getOperatingSystem() {
                DefaultJavaToolchainInstallationService.determineOs(os)
            }

            @Override
            String getSystemArch() {
                DefaultJavaToolchainInstallationService.determineArch(info)
            }
        }
    }

    ProviderFactory providerFactory(hostnameProvider = Providers.notDefined()) {
        Mock(ProviderFactory) {
            gradleProperty("org.gradle.jvm.toolchain.install.adoptopenjdk.baseUri") >> hostnameProvider
        }
    }
}
