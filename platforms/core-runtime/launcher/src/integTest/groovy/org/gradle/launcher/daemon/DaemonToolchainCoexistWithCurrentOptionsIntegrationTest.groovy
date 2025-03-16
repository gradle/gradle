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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.fixtures.jvm.JavaToolchainFixture
import org.gradle.internal.buildconfiguration.fixture.DaemonJvmPropertiesFixture
import org.gradle.internal.jvm.Jvm
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions

class DaemonToolchainCoexistWithCurrentOptionsIntegrationTest extends AbstractIntegrationSpec implements DaemonJvmPropertiesFixture, JavaToolchainFixture {
    def setup() {
        executer.requireDaemon().requireIsolatedDaemons() // Prevent addition of Java 9 JPMS args to the launcher and potentially daemon process which could be Java 8
    }

    @Requires(IntegTestPreconditions.JavaHomeWithDifferentVersionAvailable)
    def "Given disabled auto-detection When using daemon toolchain Then option is ignored resolving with expected toolchain"() {
        given:
        def otherJvm = AvailableJavaHomes.differentVersion
        writeJvmCriteria(otherJvm)
        captureJavaHome()
        executer.withArgument("-Porg.gradle.java.installations.auto-detect=false")

        expect:
        withInstallations(otherJvm).succeeds("help")
        assertDaemonUsedJvm(otherJvm)
    }

    @Requires(IntegTestPreconditions.JavaHomeWithDifferentVersionAvailable)
    def "Given defined org.gradle.java.home gradle property When using daemon toolchain Then option is ignored resolving with expected toolchain"() {
        given:
        def otherJvm = AvailableJavaHomes.differentVersion
        writeJvmCriteria(otherJvm)
        captureJavaHome()
        file("gradle.properties").writeProperties("org.gradle.java.home": Jvm.current().javaHome.canonicalPath)

        expect:
        withInstallations(otherJvm).succeeds("help")
        assertDaemonUsedJvm(otherJvm)
    }

    @Requires(IntegTestPreconditions.JavaHomeWithDifferentVersionAvailable)
    def "Given daemon toolchain properties When executing any task passing them as arguments Then those are ignored since aren't defined on daemon-jvm properties file"() {
        given:
        def otherJvm = AvailableJavaHomes.differentVersion
        def otherJvmMetadata = AvailableJavaHomes.getJvmInstallationMetadata(otherJvm)
        captureJavaHome()
        executer
            .withArgument("-PtoolchainVersion=$otherJvmMetadata.javaVersion")
            .withArgument("-PtoolchainVendor=$otherJvmMetadata.vendor.knownVendor")

        expect:
        succeeds("help")
        assertDaemonUsedJvm(Jvm.current())
    }

    @Requires(IntegTestPreconditions.JavaHomeWithDifferentVersionAvailable)
    def "Given daemon toolchain properties defined on gradle properties When executing any task Then those are ignored since aren't defined on daemon-jvm properties file"() {
        given:
        def otherJvm = AvailableJavaHomes.differentVersion
        def otherJvmMetadata = AvailableJavaHomes.getJvmInstallationMetadata(otherJvm)
        captureJavaHome()
        file("gradle.properties")
            .writeProperties(
                "toolchainVersion": otherJvmMetadata.javaVersion,
                "toolchainVendor": otherJvmMetadata.vendor.knownVendor.name()
            )

        expect:
        succeeds("help")
        assertDaemonUsedJvm(Jvm.current())
    }

    @Requires(IntegTestPreconditions.JavaHomeWithDifferentVersionAvailable)
    def "Given defined org.gradle.java.home under Build properties When executing any task Then this is ignored since isn't defined on gradle properties file"() {
        given:
        def otherJvm = AvailableJavaHomes.differentVersion
        def otherJvmMetadata = AvailableJavaHomes.getJvmInstallationMetadata(otherJvm)
        captureJavaHome()

        file("gradle/gradle-daemon-jvm.properties")
            .writeProperties(
                "org.gradle.java.home": otherJvmMetadata.javaVersion,
            )

        expect:
        succeeds("help")
        assertDaemonUsedJvm(Jvm.current())
    }
}
