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
import org.gradle.internal.buildconfiguration.fixture.BuildPropertiesFixture
import org.gradle.internal.jvm.Jvm
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions

class DaemonToolchainCoexistWithCurrentOptionsIntegrationTest extends AbstractIntegrationSpec implements BuildPropertiesFixture {

    @Requires(IntegTestPreconditions.JavaHomeWithDifferentVersionAvailable)
    def "Given disabled auto-detection When using daemon toolchain Then option is ignored resolving with expected toolchain"() {
        given:
        def otherJvm = AvailableJavaHomes.differentVersion
        writeJvmCriteria(otherJvm)
        expectJavaHome(otherJvm)
        executer.withArgument("-Porg.gradle.java.installations.auto-detect=false")

        expect:
        succeeds("help")
    }

    @Requires(IntegTestPreconditions.JavaHomeWithDifferentVersionAvailable)
    def "Given defined org.gradle.java.home gradle property When using daemon toolchain Then option is ignored resolving with expected toolchain"() {
        given:
        def otherJvm = AvailableJavaHomes.differentVersion
        writeJvmCriteria(otherJvm)
        expectJavaHome(otherJvm)
        file("gradle.properties").writeProperties("org.gradle.java.home": Jvm.current().javaHome.canonicalPath)

        expect:
        succeeds("help")
    }

    @Requires(IntegTestPreconditions.JavaHomeWithDifferentVersionAvailable)
    def "Given daemon toolchain properties When executing any task passing them as arguments Then those are ignored since aren't defined on build properties file"() {
        given:
        def otherJvm = AvailableJavaHomes.differentVersion
        def otherJvmMetadata = AvailableJavaHomes.getJvmInstallationMetadata(otherJvm)
        expectJavaHome(Jvm.current())
        executer
            .withArgument("-Pdaemon.jvm.toolchain.version=$otherJvmMetadata.javaVersion")
            .withArgument("-Pdaemon.jvm.toolchain.vendor=$otherJvmMetadata.vendor.knownVendor")

        expect:
        succeeds("help")
    }

    @Requires(IntegTestPreconditions.JavaHomeWithDifferentVersionAvailable)
    def "Given daemon toolchain properties defined on gradle properties When executing any task Then those are ignored since aren't defined on build properties file"() {
        given:
        def otherJvm = AvailableJavaHomes.differentVersion
        def otherJvmMetadata = AvailableJavaHomes.getJvmInstallationMetadata(otherJvm)
        expectJavaHome(Jvm.current())
        file("gradle.properties")
            .writeProperties(
                "daemon.jvm.toolchain.version": otherJvmMetadata.javaVersion,
                "daemon.jvm.toolchain.vendor": otherJvmMetadata.vendor.knownVendor.name()
            )

        expect:
        succeeds("help")
    }

    @Requires(IntegTestPreconditions.JavaHomeWithDifferentVersionAvailable)
    def "Given defined org.gradle.java.home under Build properties When executing any task Then this is ignored since isn't defined on gradle properties file"() {
        given:
        def otherJvm = AvailableJavaHomes.differentVersion
        def otherJvmMetadata = AvailableJavaHomes.getJvmInstallationMetadata(otherJvm)
        expectJavaHome(Jvm.current())
        createDir("gradle")
        file("gradle/gradle-build.properties")
            .writeProperties(
                "org.gradle.java.home": otherJvmMetadata.javaVersion,
            )

        expect:
        succeeds("help")
    }
}
