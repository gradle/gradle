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

import org.gradle.internal.buildconfiguration.resolvers.ToolchainSupportedPlatformsMatrix
import org.gradle.internal.buildconfiguration.tasks.DaemonJvmPropertiesAccessor
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JvmVendorSpec
import spock.lang.Specification

class DaemonJvmPropertiesAccessorTest extends Specification {

    def "Given valid properties When parse them Then expected values are returned"() {
        given:
        def properties = [
            "toolchainVersion": "12",
            "toolchainVendor": "BELLSOFT",
            "toolchainFreeBsdX8664Url": "https://server?os=FREE_BSD&architecture=X86_64",
            "toolchainFreeBsdAarch64Url": "https://server?os=FREE_BSD&architecture=AARCH64",
            "toolchainLinuxX8664Url": "https://server?os=LINUX&architecture=X86_64",
            "toolchainLinuxAarch64Url": "https://server?os=LINUX&architecture=AARCH64",
            "toolchainMacOsX8664Url": "https://server?os=MAC_OS&architecture=X86_64",
            "toolchainMacOsAarch64Url": "https://server?os=MAC_OS&architecture=AARCH64",
            "toolchainSolarisX8664Url": "https://server?os=SOLARIS&architecture=X86_64",
            "toolchainSolarisAarch64Url": "https://server?os=SOLARIS&architecture=AARCH64",
            "toolchainUnixX8664Url": "https://server?os=UNIX&architecture=X86_64",
            "toolchainUnixAarch64Url": "https://server?os=UNIX&architecture=AARCH64",
            "toolchainWindowsX8664Url": "https://server?os=WINDOWS&architecture=X86_64",
            "toolchainWindowsAarch64Url": "https://server?os=WINDOWS&architecture=AARCH64",
            "toolchainAixX8664Url": "https://server?os=AIX&architecture=X86_64",
            "toolchainAixAarch64Url": "https://server?os=AIX&architecture=AARCH64",
        ]

        when:
        def propertiesAccessor = new DaemonJvmPropertiesAccessor(properties)

        then:
        propertiesAccessor.version == JavaLanguageVersion.of(12)
        propertiesAccessor.vendor == JvmVendorSpec.BELLSOFT
        def expectedDownloadUrlsBySupportedBuildPlatform = ToolchainSupportedPlatformsMatrix.getToolchainSupportedBuildPlatforms().collectEntries { buildPlatform ->
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

    def "Given invalid download urls properties When parse properties Then empty list is returned"() {
        given:
        def properties = [
            "toolchainInvalidBsdX8664Url": "https://server",
            "toolchainFreeBsdInvalidUrl": "https://server",
            "toolchainLinuxX86Url": "https://server",
            "toolchainLinuxUrl": "https://server",
            "toolchainX8664Url": "https://server",
        ]

        when:
        def propertiesAccessor = new DaemonJvmPropertiesAccessor(properties)

        then:
        propertiesAccessor.toolchainDownloadUrls.isEmpty()
    }
}
