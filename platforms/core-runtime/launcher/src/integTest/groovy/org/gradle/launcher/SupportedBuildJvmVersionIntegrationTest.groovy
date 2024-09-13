/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.launcher

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.fixtures.jvm.JavaToolchainFixture
import org.gradle.internal.buildconfiguration.fixture.DaemonJvmPropertiesFixture
import org.gradle.test.fixtures.file.DoesNotSupportNonAsciiPaths
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.gradle.test.preconditions.UnitTestPreconditions
import org.gradle.util.GradleVersion

/**
 * Tests that the Gradle daemon can or cannot be started with JVMs of certain versions.
 */
@DoesNotSupportNonAsciiPaths(reason = "Java 6 seems to have issues with non-ascii paths")
class SupportedBuildJvmVersionIntegrationTest extends AbstractIntegrationSpec implements DaemonJvmPropertiesFixture, JavaToolchainFixture {

    def setup() {
        executer.disableDaemonJavaVersionDeprecationFiltering()
        executer.requireIsolatedDaemons() // Because we check which JVM the daemon uses, and we don't want to pick a compatible JVM from another test but with different java home.
    }

    @Requires(
        value = [IntegTestPreconditions.UnsupportedDaemonJavaHomeAvailable, IntegTestPreconditions.NotEmbeddedExecutor],
        reason = "This test requires to start Gradle from scratch with the wrong Java version"
    )
    def "provides reasonable failure message when attempting to run under java #jdk.javaVersion"() {
        given:
        executer.withJvm(jdk)

        expect:
        fails("help")
        failure.assertHasErrorOutput("Gradle ${GradleVersion.current().version} requires Java 1.8 or later to run. You are currently using Java ${jdk.javaVersion}.")

        where:
        jdk << AvailableJavaHomes.getUnsupportedDaemonJdks()
    }

    // region JAVA_HOME

    @Requires(
        value = [IntegTestPreconditions.UnsupportedDaemonJavaHomeAvailable, IntegTestPreconditions.NotEmbeddedExecutor],
        reason = "This test requires to start Gradle from scratch with the wrong Java version"
    )
    def "running a build with an unsupported JVM emits a failure"() {
        given:
        def unsupportedJdk = AvailableJavaHomes.unsupportedDaemonJdk
        executer.withJvm(unsupportedJdk)

        expect:
        fails(["help"] + (noDaemon ? ["--no-daemon"] : []))
        failure.assertHasErrorOutput("Gradle ${GradleVersion.current().version} requires Java 1.8 or later to run. You are currently using Java ${unsupportedJdk.javaVersion}.")

        where:
        noDaemon << [false, true]
    }

    @Requires(UnitTestPreconditions.DeprecatedDaemonJdkVersion)
    def "running a build with a deprecated JVM is deprecated"() {
        expect:
        executer.expectDocumentedDeprecationWarning("Executing Gradle on JVM versions 16 and lower has been deprecated. This will fail with an error in Gradle 9.0. Use JVM 17 or greater to execute Gradle. Projects can continue to use older JVM versions via toolchains. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#minimum_daemon_jvm_version")
        succeeds(["help"] + (noDaemon ? ["--no-daemon"] : []))

        where:
        noDaemon << [false, true]
    }

    @Requires(UnitTestPreconditions.NonDeprecatedDaemonJdkVersion)
    def "can run on supported JVM succeeds without warning"() {
        expect:
        succeeds(["help"] + (noDaemon ? ["--no-daemon"] : []))

        where:
        noDaemon << [false, true]
    }

    // region org.gradle.java.home

    @Requires(IntegTestPreconditions.UnsupportedDaemonJavaHomeAvailable)
    def "specifying unsupported java home via system property fails"() {
        given:
        def unsupportedJdk = AvailableJavaHomes.unsupportedDaemonJdk
        propertiesFile.writeProperties("org.gradle.java.home": unsupportedJdk.javaHome.canonicalPath)
        captureJavaHome()

        when:
        fails(["help"] + (noDaemon ? ["--no-daemon"] : []))

        then:
        failure.assertHasDescription("Gradle ${GradleVersion.current().version} requires Java 8 or later to run. Your build is currently configured to use Java ${unsupportedJdk.javaVersion.majorVersion}.")

        where:
        noDaemon << [false, true]
    }

    @Requires(IntegTestPreconditions.DeprecatedDaemonJavaHomeAvailable)
    def "specifying a deprecated jvm via system property is deprecated"() {
        given:
        def deprecatedJvm = AvailableJavaHomes.deprecatedDaemonJdk
        propertiesFile.writeProperties("org.gradle.java.home": deprecatedJvm.javaHome.canonicalPath)
        captureJavaHome()

        when:
        executer.expectDocumentedDeprecationWarning("Executing Gradle on JVM versions 16 and lower has been deprecated. This will fail with an error in Gradle 9.0. Use JVM 17 or greater to execute Gradle. Projects can continue to use older JVM versions via toolchains. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#minimum_daemon_jvm_version")
        succeeds(["help"] + (noDaemon ? ["--no-daemon"] : []))

        then:
        assertDaemonUsedJvm(deprecatedJvm)

        where:
        noDaemon << [false, true]
    }

    @Requires(IntegTestPreconditions.NonDeprecatedDaemonJavaHomeAvailable)
    def "specifying a supported jvm via system property emits no jvm deprecation warning"() {
        given:
        def nonDeprecatedJvm = AvailableJavaHomes.nonDeprecatedDaemonJdk
        propertiesFile.writeProperties("org.gradle.java.home": nonDeprecatedJvm.javaHome.canonicalPath)
        captureJavaHome()

        when:
        succeeds(["help"] + (noDaemon ? ["--no-daemon"] : []))

        then:
        assertDaemonUsedJvm(nonDeprecatedJvm)

        where:
        noDaemon << [false, true]
    }

    // region daemon toolchain

    @Requires(IntegTestPreconditions.UnsupportedDaemonJavaHomeAvailable)
    def "throws an exception when specifying a daemon toolchain with an unsupported daemon JDK version"() {
        given:
        def oldJvm = AvailableJavaHomes.unsupportedDaemonJdk
        writeJvmCriteria(oldJvm)
        withInstallations(oldJvm)

        when:
        fails(["help"] + (noDaemon ? ["--no-daemon"] : []))

        then:
        failure.assertHasDescription("Gradle ${GradleVersion.current().version} requires Java 8 or later to run. Your build is currently configured to use Java 7.")

        where:
        noDaemon << [false, true]
    }

    @Requires(IntegTestPreconditions.DeprecatedDaemonJavaHomeAvailable)
    def "specifying a daemon toolchain with a deprecated jvm is deprecated"() {
        given:
        def deprecatedJvm = AvailableJavaHomes.deprecatedDaemonJdk
        writeJvmCriteria(deprecatedJvm)
        withInstallations(deprecatedJvm)
        captureJavaHome()

        when:
        executer.expectDocumentedDeprecationWarning("Executing Gradle on JVM versions 16 and lower has been deprecated. This will fail with an error in Gradle 9.0. Use JVM 17 or greater to execute Gradle. Projects can continue to use older JVM versions via toolchains. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#minimum_daemon_jvm_version")
        succeeds(["help"] + (noDaemon ? ["--no-daemon"] : []))

        then:
        assertDaemonUsedJvm(deprecatedJvm)

        where:
        noDaemon << [false, true]
    }

    @Requires(IntegTestPreconditions.NonDeprecatedDaemonJavaHomeAvailable)
    def "specifying a daemon toolchain with a supported jvm emits no jvm deprecation warning"() {
        given:
        def nonDeprecatedJvm = AvailableJavaHomes.nonDeprecatedDaemonJdk
        writeJvmCriteria(nonDeprecatedJvm)
        withInstallations(nonDeprecatedJvm)
        captureJavaHome()

        when:
        succeeds(["help"] + (noDaemon ? ["--no-daemon"] : []))

        then:
        assertDaemonUsedJvm(nonDeprecatedJvm)

        where:
        noDaemon << [false, true]
    }

    // endregion
}
