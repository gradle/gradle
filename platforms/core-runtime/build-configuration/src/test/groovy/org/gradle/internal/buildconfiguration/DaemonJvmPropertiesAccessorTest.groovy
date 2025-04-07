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

package org.gradle.internal.buildconfiguration

import org.gradle.internal.buildconfiguration.tasks.DaemonJvmPropertiesAccessor
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JvmVendorSpec
import org.gradle.platform.Architecture
import org.gradle.platform.BuildPlatformFactory
import org.gradle.platform.OperatingSystem
import spock.lang.Specification

import java.util.stream.Collectors
import java.util.stream.Stream

class DaemonJvmPropertiesAccessorTest extends Specification {

    def "Given valid properties When parse them Then expected values are returned"() {
        given:
        def properties = [
            "toolchainVersion": "12",
            "toolchainVendor": "BELLSOFT",
            "toolchainNativeImageCapable": "true",
            "toolchainUrl.FREE_BSD.X86_64": "https://server?os=FREE_BSD&architecture=X86_64",
            "toolchainUrl.FREE_BSD.AARCH64": "https://server?os=FREE_BSD&architecture=AARCH64",
            "toolchainUrl.LINUX.X86_64": "https://server?os=LINUX&architecture=X86_64",
            "toolchainUrl.LINUX.AARCH64": "https://server?os=LINUX&architecture=AARCH64",
            "toolchainUrl.MAC_OS.X86_64": "https://server?os=MAC_OS&architecture=X86_64",
            "toolchainUrl.MAC_OS.AARCH64": "https://server?os=MAC_OS&architecture=AARCH64",
            "toolchainUrl.SOLARIS.X86_64": "https://server?os=SOLARIS&architecture=X86_64",
            "toolchainUrl.SOLARIS.AARCH64": "https://server?os=SOLARIS&architecture=AARCH64",
            "toolchainUrl.UNIX.X86_64": "https://server?os=UNIX&architecture=X86_64",
            "toolchainUrl.UNIX.AARCH64": "https://server?os=UNIX&architecture=AARCH64",
            "toolchainUrl.WINDOWS.X86_64": "https://server?os=WINDOWS&architecture=X86_64",
            "toolchainUrl.WINDOWS.AARCH64": "https://server?os=WINDOWS&architecture=AARCH64",
        ]

        when:
        def propertiesAccessor = new DaemonJvmPropertiesAccessor(properties)

        then:
        propertiesAccessor.version == JavaLanguageVersion.of(12)
        propertiesAccessor.vendor == JvmVendorSpec.BELLSOFT
        propertiesAccessor.nativeImageCapable
        def expectedDownloadUrlsBySupportedBuildPlatform = Stream.of(Architecture.X86_64, Architecture.AARCH64).flatMap(arch ->
            Stream.of(OperatingSystem.values()).map(os -> BuildPlatformFactory.of(arch, os)))
            .collect(Collectors.toSet()).collectEntries { buildPlatform ->
            [buildPlatform, "https://server?os=${buildPlatform.operatingSystem}&architecture=${buildPlatform.architecture}"]
        }
        propertiesAccessor.toolchainDownloadUrls == expectedDownloadUrlsBySupportedBuildPlatform
    }

    def "Given invalid version property When parse value Then fails with excepted exception message"() {
        given:
        def properties = ["toolchainVersion": "invalid version"]

        when:
        def propertiesAccessor = new DaemonJvmPropertiesAccessor(properties)
        propertiesAccessor.version

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Value 'invalid version' given for toolchainVersion is an invalid Java version"
        e.cause == null
    }

    def "Given invalid download urls properties When parse properties Then fails with exception"() {
        given:
        def properties = [
            "toolchainUrl.InvalidBsdX86_64": "https://server"
        ]

        when:
        def propertiesAccessor = new DaemonJvmPropertiesAccessor(properties)
        propertiesAccessor.toolchainDownloadUrls

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Invalid toolchain URL property name: toolchainUrl.InvalidBsdX86_64"
        e.cause == null
    }
}
