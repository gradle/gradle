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

package org.gradle.integtests.fixtures.daemon

import org.gradle.api.JavaVersion
import org.gradle.api.provider.Property
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.gradle.internal.jvm.Jvm
import org.gradle.internal.jvm.inspection.JvmInstallationMetadata
import org.gradle.internal.jvm.inspection.JvmInstallationMetadataComparator
import org.gradle.internal.jvm.inspection.JvmVendor
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainSpec
import org.gradle.jvm.toolchain.JvmImplementation
import org.gradle.jvm.toolchain.JvmVendorSpec
import org.gradle.jvm.toolchain.internal.JvmInstallationMetadataMatcher

abstract class DaemonToolchainIntegrationSpec extends AbstractIntegrationSpec {

    protected def createDaemonJvmToolchainCriteria(Jvm jvm) {
        def jvmMetadata = AvailableJavaHomes.getJvmInstallationMetadata(jvm)
        createDaemonJvmToolchainCriteria(jvmMetadata.languageVersion.majorVersion, jvmMetadata.vendor.knownVendor.name())
    }

    protected def createDaemonJvmToolchainCriteria(String version = null, String vendor = null, String implementation = null) {
        def properties = new ArrayList()
        if (version != null) {
            properties.add("daemon.jvm.toolchain.version=$version")
        }
        if (vendor != null) {
            properties.add("daemon.jvm.toolchain.vendor=$vendor")
        }
        if (implementation != null) {
            properties.add("daemon.jvm.toolchain.implementation=$implementation")
        }

        file("gradle/gradle-build.properties") << properties.join(System.getProperty("line.separator"))
    }

    protected ExecutionResult succeedsSimpleTaskWithDaemonJvm(Jvm expectedDaemonJvm, Boolean daemonToolchainCriteria = true) {
        return succeedsTaskWithDaemonJvm(expectedDaemonJvm, daemonToolchainCriteria, "help")
    }

    protected ExecutionResult succeedsTaskWithDaemonJvm(Jvm expectedDaemonJvm, Boolean hasDaemonToolchainCriteria = true, String... tasks) {
        addDaemonJvmValidation(expectedDaemonJvm, hasDaemonToolchainCriteria)
        return succeeds(tasks)
    }

    protected ExecutionResult failsSimpleTaskWithDescription(String expectedExceptionDescription) {
        def result = fails 'help'
        failureDescriptionContains(expectedExceptionDescription)
        return result
    }

    private def addDaemonJvmValidation(Jvm expectedDaemonJvm, Boolean hasDaemonToolchainCriteria = true) {
        def expectedJvmMetadata = AvailableJavaHomes.getJvmInstallationMetadata(expectedDaemonJvm)
        def expectedVendor = expectedJvmMetadata.vendor
        def expectedJavaHome = expectedJvmMetadata.javaHome
        def expectedVersion = expectedJvmMetadata.javaVersion
        if (hasDaemonToolchainCriteria) {
            // When Daemon toolchain criteria is defined the detection mechanism should be taken into consideration
            // for the resolution of the toolchain that may be different depending on the local installed ones
            def detectedMatchingToolchain = findMatchingToolchain(expectedDaemonJvm.javaVersion, expectedVendor)
            expectedJavaHome = detectedMatchingToolchain.javaHome
            expectedVersion = detectedMatchingToolchain.javaVersion
        }
        buildFile << """
            tasks.all {
                doLast {
                    assert System.getProperty("java.version").equals("$expectedVersion")
                    assert System.getProperty("java.vendor").equals("$expectedVendor.rawVendor")
                    assert System.getProperty("java.home").equals("$expectedJavaHome")
                }
            }
        """
    }

    private JvmInstallationMetadata findMatchingToolchain(JavaVersion version, JvmVendor vendor) {
        def metadataComparator = new JvmInstallationMetadataComparator(Jvm.current().getJavaHome())
        def toolchainSpec = mockToolchainSpec(version.majorVersion, vendor.rawVendor, null)
        def matcher = new JvmInstallationMetadataMatcher(toolchainSpec)

        return AvailableJavaHomes.getAvailableJvms().collect { jvm ->
            AvailableJavaHomes.getJvmInstallationMetadata(jvm)
        }.stream()
            .filter(metadata -> metadata.isValidInstallation())
            .filter(metadata -> matcher.test(metadata))
            .min(Comparator.comparing(metadata -> metadata, metadataComparator))
            .get()
    }

    private JavaToolchainSpec mockToolchainSpec(String version, String vendor, String implementation) {
        Property<JavaLanguageVersion> javaLanguageVersionProperty = mockProperty(JavaLanguageVersion.of(version))
        Property<JvmImplementation> implementationProperty = mockProperty(implementation ?: JvmImplementation.VENDOR_SPECIFIC)
        Property<JvmVendorSpec> vendorProperty = mockProperty(JvmVendorSpec.matching(vendor))

        return Mock(JavaToolchainSpec) {
            getLanguageVersion() >> javaLanguageVersionProperty
            getImplementation() >> implementationProperty
            getVendor() >> vendorProperty
            getDisplayName() >> "mock spec"
        }
    }

    private def mockProperty(value) {
        return Mock(Property.class) {
            get() >> value
            getOrNull() >> value
        }
    }
}
