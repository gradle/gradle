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
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.fixtures.jvm.JavaToolchainFixture
import org.gradle.internal.buildconfiguration.fixture.DaemonJvmPropertiesFixture
import org.gradle.internal.jvm.Jvm
import org.gradle.internal.os.OperatingSystem
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.junit.Assume

class DaemonToolchainIntegrationTest extends AbstractIntegrationSpec implements DaemonJvmPropertiesFixture, JavaToolchainFixture {
    def setup() {
        executer.requireIsolatedDaemons()
        executer.requireDaemon()
    }

    def "executes the daemon with the current jvm if the current jvm is specified"() {
        given:
        writeJvmCriteria(Jvm.current())
        captureJavaHome()

        expect:
        succeeds("help")
        assertDaemonUsedJvm(Jvm.current())
        outputContains("Daemon JVM discovery is an incubating feature.")
    }

    @Requires(IntegTestPreconditions.JavaHomeWithDifferentVersionAvailable)
    def "executes the daemon with the specified jdk"() {
        given:
        def otherJvm = AvailableJavaHomes.differentVersion
        writeJvmCriteria(otherJvm.javaVersion)
        captureJavaHome()

        expect:
        withInstallations(otherJvm).succeeds("help")
        assertDaemonUsedJvm(otherJvm)
    }

    @Requires(IntegTestPreconditions.JavaHomeWithDifferentVersionAvailable)
    def "Given other daemon toolchain version and vendor When executing any task Then daemon jvm was set up with expected configuration"() {
        given:
        def otherJvm = AvailableJavaHomes.differentVersion
        def otherMetadata = AvailableJavaHomes.getJvmInstallationMetadata(otherJvm)

        writeJvmCriteria(otherJvm.javaVersion, otherMetadata.vendor.knownVendor.name())
        captureJavaHome()

        expect:
        withInstallations(otherJvm).succeeds("help")
        assertDaemonUsedJvm(otherJvm)
    }

    @Requires(IntegTestPreconditions.JavaHomeWithDifferentVersionAvailable)
    def "Given installation with relative path to project and disabled auto-detection When executing any task Then daemon jvm was set up with the relative path toolchain"() {
        given:
        def otherJvm = AvailableJavaHomes.differentVersion
        def otherMetadata = AvailableJavaHomes.getJvmInstallationMetadata(otherJvm)
        writeJvmCriteria(otherJvm.javaVersion, otherMetadata.vendor.knownVendor.name())
        captureJavaHome()

        def relativePathOtherJvmInstallation = testDirectory.relativePath(otherJvm.javaHome)
        executer.withArguments([
            "-Porg.gradle.java.installations.auto-detect=false",
            "-Porg.gradle.java.installations.paths=" + relativePathOtherJvmInstallation
        ])

        expect:
        succeeds("help")
        assertDaemonUsedJvm(otherJvm)
    }

    @Requires(IntegTestPreconditions.JavaHomeWithDifferentVersionAvailable)
    def "Given installation with relative path to project and disabled auto-detection When executing from subproject Then daemon jvm was set up with toolchain resolved relative to root dir"() {
        def otherJvm = AvailableJavaHomes.differentVersion
        def otherMetadata = AvailableJavaHomes.getJvmInstallationMetadata(otherJvm)

        buildTestFixture.withBuildInSubDir()
        def subproject = "app"
        def subprojects = [subproject]
        def rootProject = multiProjectBuild("project", subprojects) {
            writeJvmCriteria(otherJvm.javaVersion, otherMetadata.vendor.knownVendor.name(), null, delegate)
            captureJavaHome(buildFile)
        }

        when:
        result = executer
            .withArguments([
                "-Porg.gradle.java.installations.auto-detect=false",
                "-Porg.gradle.java.installations.paths=" + rootProject.relativePath(otherJvm.javaHome)
            ])
            .withTasks("help")
            .inDirectory(new File(rootProject, subproject))
            .run()

        then:
        assertDaemonUsedJvm(otherJvm, rootProject)
    }

    def "Given daemon toolchain criteria with version that doesn't match installed ones When executing any task Then fails with the expected message"() {
        given:
        // Java 10 is not available
        def java10 = AvailableJavaHomes.getAvailableJdks(JavaVersion.VERSION_1_10)
        Assume.assumeTrue(java10.isEmpty())
        writeJvmCriteria(JavaVersion.VERSION_1_10)
        captureJavaHome()

        expect:
        fails("help")
        failure.assertHasDescription("Cannot find a Java installation on your machine (${OperatingSystem.current()}) matching: {languageVersion=10, vendor=any vendor, implementation=vendor-specific, nativeImageCapable=false}")
    }

    def "Given daemon toolchain criteria with version and vendor that doesn't match installed ones When executing any task Then fails with the expected message"() {
        given:
        // Java 10 is not available
        def java10 = AvailableJavaHomes.getAvailableJdks(JavaVersion.VERSION_1_10)
        Assume.assumeTrue(java10.isEmpty())
        writeJvmCriteria(JavaVersion.VERSION_1_10, "ibm")
        captureJavaHome()

        expect:
        fails("help")
        failure.assertHasDescription("Cannot find a Java installation on your machine (${OperatingSystem.current()}) matching: {languageVersion=10, vendor=IBM, implementation=vendor-specific, nativeImageCapable=false}")
    }
}
