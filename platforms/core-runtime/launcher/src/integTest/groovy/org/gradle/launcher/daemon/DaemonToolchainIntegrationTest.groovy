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
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.junit.Assume

class DaemonToolchainIntegrationTest extends AbstractIntegrationSpec implements DaemonJvmPropertiesFixture, JavaToolchainFixture {
    def setup() {
        executer.requireIsolatedDaemons()
        executer.requireDaemon()
    }

    def "Given daemon toolchain version When executing any task Then daemon jvm was set up with expected configuration"() {
        given:
        writeJvmCriteria(Jvm.current())
        captureJavaHome()

        expect:
        succeeds("help")
        assertDaemonUsedJvm(Jvm.current())
        outputContains("Daemon JVM discovery is an incubating feature.")
    }

    @Requires(IntegTestPreconditions.JavaHomeWithDifferentVersionAvailable)
    def "Given other daemon toolchain version When executing any task Then daemon jvm was set up with expected configuration"() {
        given:
        def otherJvm = AvailableJavaHomes.differentVersion
        writeJvmCriteria(otherJvm)
        captureJavaHome()

        expect:
        withInstallations(otherJvm).succeeds("help")
        assertDaemonUsedJvm(otherJvm)
    }

    def "Given daemon toolchain criteria that doesn't match installed ones When executing any task Then fails with the expected message"() {
        given:
        // Java 10 is not available
        def java10 = AvailableJavaHomes.getAvailableJdks(JavaVersion.VERSION_1_10)
        Assume.assumeTrue(java10.isEmpty())
        writeJvmCriteria(JavaVersion.VERSION_1_10)
        captureJavaHome()

        expect:
        fails("help")
        failure.assertHasDescription("Cannot find a Java installation on your machine")
    }

    @Requires(IntegTestPreconditions.JavaHomeWithDifferentVersionAvailable)
    def "Given non existing toolchain metadata cache When execute any consecutive tasks Then metadata is resolved only for the first build"() {
        def otherJvm = AvailableJavaHomes.differentVersion

        given:
        cleanToolchainsMetadataCache()
        writeJvmCriteria(otherJvm)

        when:
        def results = (0..2).collect {
            withInstallations(otherJvm).executer
                .withArgument("--info")
                .withTasks("help")
                .run()
        }

        then:
        results.size() == 3
        1 == countReceivedJvmInstallationsMetadata(otherJvm, results[0].plainTextOutput)
        0 == countReceivedJvmInstallationsMetadata(otherJvm, results[1].plainTextOutput)
        0 == countReceivedJvmInstallationsMetadata(otherJvm, results[2].plainTextOutput)
    }

    @Requires(IntegTestPreconditions.JavaHomeWithDifferentVersionAvailable)
    def "Given daemon toolchain and task with specific toolchain When execute task Then metadata is resolved only one time storing resolution into cache shared between daemon and task toolchain"() {
        def otherJvm = AvailableJavaHomes.differentVersion
        def otherMetadata = AvailableJavaHomes.getJvmInstallationMetadata(otherJvm)

        given:
        cleanToolchainsMetadataCache()
        writeJvmCriteria(otherJvm)
        buildFile << """
            apply plugin: 'jvm-toolchains'
            tasks.register('exec', JavaExec) {
                javaLauncher.set(javaToolchains.launcherFor {
                    languageVersion = JavaLanguageVersion.of($otherMetadata.languageVersion.majorVersion)
                    vendor = JvmVendorSpec.matching("${otherMetadata.vendor.knownVendor.name()}")
                })
                mainClass.set("None")
                jvmArgs = ['-version']
            }
        """

        when:
        def result = withInstallations(otherJvm).executer
            .withToolchainDetectionEnabled()
            .withArgument("--info")
            .withTasks("exec")
            .run()

        then:
        1 == countReceivedJvmInstallationsMetadata(otherJvm, result.plainTextOutput)
    }
}
