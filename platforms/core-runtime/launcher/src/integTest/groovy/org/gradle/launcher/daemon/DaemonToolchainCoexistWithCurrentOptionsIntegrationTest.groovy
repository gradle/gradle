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

import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.fixtures.daemon.DaemonToolchainIntegrationSpec
import org.gradle.internal.jvm.Jvm
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions

@Requires(IntegTestPreconditions.NotEmbeddedExecutor)
class DaemonToolchainCoexistWithCurrentOptionsIntegrationTest extends DaemonToolchainIntegrationSpec {

    @Requires(IntegTestPreconditions.JavaHomeWithDifferentVersionAvailable)
    def "Given disabled auto-detection When using daemon toolchain Then option is ignored resolving with expected toolchain"() {
        def otherJvm = AvailableJavaHomes.differentVersion

        given:
        createDaemonJvmToolchainCriteria(otherJvm)
        executer.withArgument("-Porg.gradle.java.installations.auto-detect=false")

        expect:
        succeedsSimpleTaskWithDaemonJvm(otherJvm)
    }

    @Requires(IntegTestPreconditions.JavaHomeWithDifferentVersionAvailable)
    def "Given defined org.gradle.java.home gradle property When using daemon toolchain Then option is ignored resolving with expected toolchain"() {
        def currentJvm = Jvm.current()
        def otherJvm = AvailableJavaHomes.differentVersion

        given:
        createDaemonJvmToolchainCriteria(otherJvm)
        file("gradle.properties").writeProperties("org.gradle.java.home": currentJvm.javaHome.canonicalPath)

        expect:
        succeedsSimpleTaskWithDaemonJvm(otherJvm)
    }

    @Requires(IntegTestPreconditions.JavaHomeWithDifferentVersionAvailable)
    def "Given daemon toolchain properties When executing any task passing them as arguments Then those are ignored since aren't defined on build properties file"() {
        def currentJvm = Jvm.current()
        def otherJvm = AvailableJavaHomes.differentVersion
        def otherJvmMetadata = AvailableJavaHomes.getJvmInstallationMetadata(otherJvm)

        given:
        executer
            .withArgument("-Pdaemon.jvm.toolchain.version=$otherJvmMetadata.javaVersion")
            .withArgument("-Pdaemon.jvm.toolchain.vendor=$otherJvmMetadata.vendor.knownVendor")

        expect:
        succeedsSimpleTaskWithDaemonJvm(currentJvm)
    }

    @Requires(IntegTestPreconditions.JavaHomeWithDifferentVersionAvailable)
    def "Given daemon toolchain properties defined on gradle properties When executing any task Then those are ignored since aren't defined on build properties file"() {
        def currentJvm = Jvm.current()
        def otherJvm = AvailableJavaHomes.differentVersion
        def otherJvmMetadata = AvailableJavaHomes.getJvmInstallationMetadata(otherJvm)

        given:
        file("gradle.properties")
            .writeProperties(
                "daemon.jvm.toolchain.version": otherJvmMetadata.javaVersion,
                "daemon.jvm.toolchain.vendor": otherJvmMetadata.vendor.knownVendor.name()
            )

        expect:
        succeedsSimpleTaskWithDaemonJvm(currentJvm)
    }
}
