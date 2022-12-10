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
import org.gradle.jvm.toolchain.JavaToolchainDownload
import org.gradle.jvm.toolchain.internal.DefaultJavaToolchainRequest
import org.gradle.platform.internal.DefaultBuildPlatform
import org.gradle.internal.os.OperatingSystem
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JvmImplementation
import org.gradle.jvm.toolchain.JvmVendorSpec
import org.gradle.jvm.toolchain.internal.DefaultJvmVendorSpec
import org.gradle.jvm.toolchain.internal.DefaultToolchainSpec
import org.gradle.jvm.toolchain.internal.install.AdoptOpenJdkRemoteBinary
import org.gradle.util.TestUtil
import spock.lang.Specification

class AdoptOpenJdkRemoteBinaryTest extends Specification {

    def "generates url for jdk #jdkVersion on #operatingSystemName (#architecture)"() {
        given:
        def spec = newSpec(jdkVersion)
        def systemInfo = Mock(SystemInfo)
        systemInfo.architecture >> architecture
        def operatingSystem = OperatingSystem.forName(operatingSystemName)
        def binary = new AdoptOpenJdkRemoteBinary(providerFactory())

        when:
        def uri = binary.resolve(newToolchainRequest(spec, systemInfo, operatingSystem)).get().getUri()

        then:
        uri.toString() == expectedPath

        where:
        jdkVersion | operatingSystemName | architecture                    | expectedPath
        11         | "Windows"           | SystemInfo.Architecture.amd64   | "https://api.adoptium.net/v3/binary/latest/11/ga/windows/x64/jdk/hotspot/normal/eclipse"
        12         | "Windows"           | SystemInfo.Architecture.i386    | "https://api.adoptopenjdk.net/v3/binary/latest/12/ga/windows/x32/jdk/hotspot/normal/adoptopenjdk"
        13         | "Windows"           | SystemInfo.Architecture.aarch64 | "https://api.adoptopenjdk.net/v3/binary/latest/13/ga/windows/aarch64/jdk/hotspot/normal/adoptopenjdk"
        11         | "Linux"             | SystemInfo.Architecture.amd64   | "https://api.adoptium.net/v3/binary/latest/11/ga/linux/x64/jdk/hotspot/normal/eclipse"
        12         | "Linux"             | SystemInfo.Architecture.i386    | "https://api.adoptopenjdk.net/v3/binary/latest/12/ga/linux/x32/jdk/hotspot/normal/adoptopenjdk"
        13         | "Linux"             | SystemInfo.Architecture.aarch64 | "https://api.adoptopenjdk.net/v3/binary/latest/13/ga/linux/aarch64/jdk/hotspot/normal/adoptopenjdk"
        11         | "Mac OS X"          | SystemInfo.Architecture.amd64   | "https://api.adoptium.net/v3/binary/latest/11/ga/mac/x64/jdk/hotspot/normal/eclipse"
        12         | "Darwin"            | SystemInfo.Architecture.i386    | "https://api.adoptopenjdk.net/v3/binary/latest/12/ga/mac/x32/jdk/hotspot/normal/adoptopenjdk"
        13         | "OSX"               | SystemInfo.Architecture.aarch64 | "https://api.adoptopenjdk.net/v3/binary/latest/13/ga/mac/aarch64/jdk/hotspot/normal/adoptopenjdk"
        13         | "Solaris"           | SystemInfo.Architecture.i386    | "https://api.adoptopenjdk.net/v3/binary/latest/13/ga/solaris/x32/jdk/hotspot/normal/adoptopenjdk"
    }

    def "uses configured base uri #customBaseUrl if available"() {
        given:
        def spec = newSpec()
        def systemInfo = Mock(SystemInfo)
        systemInfo.architecture >> SystemInfo.Architecture.amd64
        def operatingSystem = OperatingSystem.MAC_OS
        def providerFactory = providerFactory(Providers.of(customBaseUrl))
        def binary = new AdoptOpenJdkRemoteBinary(providerFactory)

        when:
        def uri = binary.resolve(newToolchainRequest(spec, systemInfo, operatingSystem)).get().getUri()

        then:
        uri.toString() == "http://foobar/v3/binary/latest/11/ga/mac/x64/jdk/hotspot/normal/eclipse"

        where:
        customBaseUrl << ["http://foobar", "http://foobar/"]
    }

    def "can't provide java version #javaVersion"() {
        given:
        def spec = newSpec(javaVersion as int)
        def systemInfo = Mock(SystemInfo)
        systemInfo.architecture >> SystemInfo.Architecture.amd64
        def operatingSystem = OperatingSystem.MAC_OS
        def binary = new AdoptOpenJdkRemoteBinary(providerFactory())


        when:
        Optional<JavaToolchainDownload> download = binary.resolve(newToolchainRequest(spec, systemInfo, operatingSystem))

        then:
        !download.isPresent()

        where:
        javaVersion << ([5, 6, 7])
    }

    def "can't provide vendor #vendor"() {
        given:
        def spec = newSpec(8)
        spec.vendor.set(vendor)
        def systemInfo = Mock(SystemInfo)
        systemInfo.architecture >> SystemInfo.Architecture.amd64
        def operatingSystem = OperatingSystem.MAC_OS
        def binary = new AdoptOpenJdkRemoteBinary(providerFactory())

        when:
        Optional<JavaToolchainDownload> download = binary.resolve(newToolchainRequest(spec, systemInfo, operatingSystem))

        then:
        !download.isPresent()

        where:
        vendor << [JvmVendorSpec.AMAZON]
    }

    def "can provide matching vendor spec using #vendor"() {
        given:
        def spec = newSpec(12)
        spec.vendor.set(vendor)
        def systemInfo = Mock(SystemInfo)
        systemInfo.architecture >> SystemInfo.Architecture.amd64
        def operatingSystem = OperatingSystem.MAC_OS
        def binary = new AdoptOpenJdkRemoteBinary(providerFactory())

        when:
        Optional<JavaToolchainDownload> download = binary.resolve(newToolchainRequest(spec, systemInfo, operatingSystem))

        then:
        download.isPresent()
        download.get().getUri() == URI.create("https://api.adoptopenjdk.net/v3/binary/latest/12/ga/mac/x64/jdk/hotspot/normal/adoptopenjdk")

        where:
        vendor << [JvmVendorSpec.ADOPTOPENJDK, JvmVendorSpec.IBM, JvmVendorSpec.matching("adoptopenjdk"), DefaultJvmVendorSpec.any()]
    }

    def "can provide j9 impl if requested"() {
        given:
        def spec = newSpec()
        spec.implementation.set(JvmImplementation.J9)
        def systemInfo = Mock(SystemInfo)
        systemInfo.architecture >> SystemInfo.Architecture.amd64
        def operatingSystem = OperatingSystem.MAC_OS
        def binary = new AdoptOpenJdkRemoteBinary(providerFactory())

        when:
        Optional<JavaToolchainDownload> download = binary.resolve(newToolchainRequest(spec, systemInfo, operatingSystem))

        then:
        download.isPresent()
        download.get().getUri() == URI.create("https://api.adoptopenjdk.net/v3/binary/latest/11/ga/mac/x64/jdk/openj9/normal/adoptopenjdk")
    }

    private static DefaultJavaToolchainRequest newToolchainRequest(DefaultToolchainSpec spec, SystemInfo sysInfo, OperatingSystem os) {
        new DefaultJavaToolchainRequest(spec, new DefaultBuildPlatform(sysInfo, os))
    }

    def newSpec(int jdkVersion = 11, JvmImplementation implementation = null, JvmVendorSpec vendor = null) {
        def spec = new DefaultToolchainSpec(TestUtil.objectFactory())
        spec.languageVersion.set(JavaLanguageVersion.of(jdkVersion))
        spec.implementation.set(implementation)
        spec.vendor.set(vendor)
        spec
    }

    ProviderFactory providerFactory(hostnameProvider = Providers.notDefined()) {
        Mock(ProviderFactory) {
            gradleProperty("org.gradle.jvm.toolchain.install.adoptopenjdk.baseUri") >> hostnameProvider
            gradleProperty("org.gradle.jvm.toolchain.install.adoptium.baseUri") >> hostnameProvider
        }
    }
}
