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

package org.gradle.launcher.daemon

import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.fixtures.daemon.DaemonToolchainIntegrationSpec
import org.gradle.internal.jvm.inspection.JvmVendor
import org.gradle.internal.os.OperatingSystem
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.junit.Assume

@Requires(IntegTestPreconditions.NotEmbeddedExecutor)
class DaemonToolchainIntegrationTest extends DaemonToolchainIntegrationSpec {

    @Requires(IntegTestPreconditions.JavaHomeWithDifferentVersionAvailable)
    def "Given daemon toolchain version When executing any task Then daemon jvm was set up with expected configuration"() {
        def otherJvm = AvailableJavaHomes.differentVersion
        def otherMetadata = AvailableJavaHomes.getJvmInstallationMetadata(otherJvm)

        given:
        createDaemonJvmToolchainCriteria(otherMetadata.languageVersion.majorVersion)

        expect:
        succeedsSimpleTaskWithDaemonJvm(otherJvm)
    }

    @Requires(IntegTestPreconditions.JavaHomeWithDifferentVersionAvailable)
    def "Given daemon toolchain version and vendor When executing any task Then daemon jvm was set up with expected configuration"() {
        def otherJvm = AvailableJavaHomes.differentVersion
        def otherMetadata = AvailableJavaHomes.getJvmInstallationMetadata(otherJvm)

        given:
        createDaemonJvmToolchainCriteria(otherMetadata.languageVersion.majorVersion, otherMetadata.vendor.knownVendor.name())

        expect:
        succeedsSimpleTaskWithDaemonJvm(otherJvm)
    }

    def "Given daemon toolchain criteria that doesn't match installed ones When executing any task Then fails with the expected message"() {
        given:
        createDaemonJvmToolchainCriteria("100000", "amazon")

        expect:
        failsSimpleTaskWithDescription("Cannot find a Java installation on your machine matching the Daemon JVM defined requirements: " +
            "{languageVersion=100000, vendor=AMAZON, implementation=vendor-specific} for ${OperatingSystem.current()}.")
    }

    @Requires(IntegTestPreconditions.Java11HomeAvailable)
    def "Given daemon toolchain criteria that match installed version but not vendor When executing any task Then fails with the expected message"() {
        def supportedVendors = JvmVendor.KnownJvmVendor.values().toList()
        AvailableJavaHomes.getAvailableJdks(JavaVersion.VERSION_11)
            .collect { jvm -> AvailableJavaHomes.getJvmInstallationMetadata(jvm)}
            .forEach { metadata -> supportedVendors.remove(metadata.vendor.knownVendor)}
        Assume.assumeTrue(supportedVendors.size() > 0)
        def missingInstalledVendor = supportedVendors.first()

        given:
        createDaemonJvmToolchainCriteria("11", missingInstalledVendor.name())

        expect:
        failsSimpleTaskWithDescription("Cannot find a Java installation on your machine matching the Daemon JVM defined requirements: " +
            "{languageVersion=11, vendor=$missingInstalledVendor, implementation=vendor-specific} for ${OperatingSystem.current()}.")
    }

    @Requires(IntegTestPreconditions.Java11HomeAvailable)
    def "Given daemon toolchain criteria that match installed version and vendor but not implementation When executing any task Then fails with the expected message"() {
        def nonIbmJvm = AvailableJavaHomes.getAvailableJdks(JavaVersion.VERSION_11).find {!it.isIbmJvm() }
        def nonIbmJvmMetadata = AvailableJavaHomes.getJvmInstallationMetadata(nonIbmJvm)

        given:
        createDaemonJvmToolchainCriteria(nonIbmJvmMetadata.languageVersion.majorVersion, nonIbmJvmMetadata.vendor.knownVendor.name(), "J9")

        expect:
        failsSimpleTaskWithDescription("Cannot find a Java installation on your machine matching the Daemon JVM defined requirements: " +
            "{languageVersion=11, vendor=AMAZON, implementation=J9} for ${OperatingSystem.current()}.")
    }

    @Requires(IntegTestPreconditions.Java11HomeAvailable)
    def "Given daemon toolchain criteria with placeholder implementation that match installed version and version When executing any task Then daemon jvm was set up with expected configuration"() {
        def jvm = AvailableJavaHomes.getJdk11()
        def jvmMetadata = AvailableJavaHomes.getJvmInstallationMetadata(jvm)

        given:
        createDaemonJvmToolchainCriteria(jvmMetadata.languageVersion.majorVersion, jvmMetadata.vendor.knownVendor.name(), "vendor_specific")

        expect:
        succeedsSimpleTaskWithDaemonJvm(jvm)
    }
}
