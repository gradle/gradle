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
import org.gradle.jvm.toolchain.JvmImplementation
import org.gradle.jvm.toolchain.JvmVendorSpec
import org.gradle.jvm.toolchain.internal.DefaultJvmVendorSpec
import org.gradle.jvm.toolchain.internal.DefaultToolchainSpec
import org.gradle.util.TestUtil
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification
import spock.lang.Unroll

class AdoptOpenJdkRemoteBinaryTest extends Specification {

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder()

    @Unroll
    def "generates url for jdk #jdkVersion on #operatingSystemName (#architecture)"() {
        given:
        def spec = newSpec(jdkVersion)
        def systemInfo = Mock(SystemInfo)
        systemInfo.architecture >> architecture
        def operatingSystem = OperatingSystem.forName(operatingSystemName)
        def binary = new AdoptOpenJdkRemoteBinary(systemInfo, operatingSystem, Mock(AdoptOpenJdkDownloader), providerFactory())

        when:
        def uri = binary.toDownloadUri(spec)

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
        def systemInfo = Mock(SystemInfo)
        systemInfo.architecture >> architecture
        def operatingSystem = OperatingSystem.forName(operatingSystemName)
        def binary = new AdoptOpenJdkRemoteBinary(systemInfo, operatingSystem, Mock(AdoptOpenJdkDownloader), providerFactory())

        when:
        def filename = binary.toFilename(spec)

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
        def systemInfo = Mock(SystemInfo)
        systemInfo.architecture >> SystemInfo.Architecture.amd64
        def operatingSystem = OperatingSystem.MAC_OS
        def providerFactory = providerFactory(Providers.of(customBaseUrl))
        def binary = new AdoptOpenJdkRemoteBinary(systemInfo, operatingSystem, Mock(AdoptOpenJdkDownloader), providerFactory)

        when:
        def uri = binary.toDownloadUri(spec)

        then:
        uri.toString() == "http://foobar/v3/binary/latest/11/ga/mac/x64/jdk/hotspot/normal/adoptopenjdk"

        where:
        customBaseUrl << ["http://foobar", "http://foobar/"]
    }

    def "downloads from url"() {
        given:
        def spec = newSpec(12)
        def systemInfo = Mock(SystemInfo)
        systemInfo.architecture >> SystemInfo.Architecture.amd64
        def operatingSystem = OperatingSystem.MAC_OS
        def downloader = Mock(AdoptOpenJdkDownloader)
        def binary = new AdoptOpenJdkRemoteBinary(systemInfo, operatingSystem, downloader, providerFactory())

        when:
        def targetFile = temporaryFolder.newFile("jdk")
        binary.download(spec, targetFile)

        then:
        1 * downloader.download(URI.create("https://api.adoptopenjdk.net/v3/binary/latest/12/ga/mac/x64/jdk/hotspot/normal/adoptopenjdk"), _)
    }

    @Unroll
    def "skips downloading unsupported java version #javaVersion"() {
        given:
        def spec = newSpec(javaVersion)
        def systemInfo = Mock(SystemInfo)
        systemInfo.architecture >> SystemInfo.Architecture.amd64
        def operatingSystem = OperatingSystem.MAC_OS
        def binary = new AdoptOpenJdkRemoteBinary(systemInfo, operatingSystem, Mock(AdoptOpenJdkDownloader), providerFactory())

        when:
        def file = binary.download(spec, Mock(File))

        then:
        !file.present

        where:
        javaVersion << [5, 6, 7]
    }

    @Unroll
    def "skips downloading unsupported vendor #vendor"() {
        given:
        def spec = newSpec(8)
        spec.vendor.set(vendor)
        def systemInfo = Mock(SystemInfo)
        systemInfo.architecture >> SystemInfo.Architecture.amd64
        def operatingSystem = OperatingSystem.MAC_OS
        def binary = new AdoptOpenJdkRemoteBinary(systemInfo, operatingSystem, Mock(AdoptOpenJdkDownloader), providerFactory())

        when:
        def file = binary.download(spec, Mock(File))

        then:
        !file.present

        where:
        vendor << [JvmVendorSpec.AMAZON, JvmVendorSpec.IBM]
    }

    @Unroll
    def "downloads with matching vendor spec using #vendor"() {
        given:
        def spec = newSpec(12)
        spec.vendor.set(vendor)
        def systemInfo = Mock(SystemInfo)
        systemInfo.architecture >> SystemInfo.Architecture.amd64
        def operatingSystem = OperatingSystem.MAC_OS
        def downloader = Mock(AdoptOpenJdkDownloader)
        def binary = new AdoptOpenJdkRemoteBinary(systemInfo, operatingSystem, downloader, providerFactory())

        when:
        def targetFile = temporaryFolder.newFile("jdk")
        binary.download(spec, targetFile)

        then:
        1 * downloader.download(URI.create("https://api.adoptopenjdk.net/v3/binary/latest/12/ga/mac/x64/jdk/hotspot/normal/adoptopenjdk"), _)

        where:
        vendor << [JvmVendorSpec.ADOPTOPENJDK, JvmVendorSpec.matching("adoptopenjdk"), DefaultJvmVendorSpec.any()]
    }

    def "downloads j9 impl if requested"() {
        given:
        def spec = newSpec(12)
        spec.implementation.set(JvmImplementation.J9)
        def systemInfo = Mock(SystemInfo)
        systemInfo.architecture >> SystemInfo.Architecture.amd64
        def operatingSystem = OperatingSystem.MAC_OS
        def downloader = Mock(AdoptOpenJdkDownloader)
        def binary = new AdoptOpenJdkRemoteBinary(systemInfo, operatingSystem, downloader, providerFactory())

        when:
        def targetFile = temporaryFolder.newFile("jdk")
        binary.download(spec, targetFile)

        then:
        1 * downloader.download(URI.create("https://api.adoptopenjdk.net/v3/binary/latest/12/ga/mac/x64/jdk/openj9/normal/adoptopenjdk"), _)
    }

    def newSpec(int jdkVersion = 11) {
        def spec = new DefaultToolchainSpec(TestUtil.objectFactory())
        spec.languageVersion.set(JavaLanguageVersion.of(jdkVersion))
        spec
    }

    ProviderFactory providerFactory(hostnameProvider = Providers.notDefined()) {
        Mock(ProviderFactory) {
            gradleProperty("org.gradle.jvm.toolchain.install.adoptopenjdk.baseUri") >> hostnameProvider
        }
    }
}
