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

package org.gradle.testkit.runner.jvm

import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.internal.buildconfiguration.fixture.DaemonJvmPropertiesFixture
import org.gradle.internal.jvm.Jvm
import org.gradle.test.fixtures.file.DoesNotSupportNonAsciiPaths
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.gradle.testkit.runner.BaseGradleRunnerIntegrationTest
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.fixtures.NoDebug
import org.gradle.testkit.runner.fixtures.NonCrossVersion
import org.gradle.tooling.GradleConnectionException
import org.gradle.util.GradleVersion
import spock.lang.Issue

/**
 * Abstract class to test the JVM version compatibility of the Gradle Runner where the
 * client JVM is different than the daemon JVM. Subclasses implement the various ways of
 * specifying the daemon JDK version.
 */
@NoDebug // We are starting daemons with different JAVA_HOMEs
@NonCrossVersion // Supporting multiple Gradle versions is more work.
@SuppressWarnings('IntegrationTestFixtures')
@DoesNotSupportNonAsciiPaths(reason = "Java 6 seems to have issues with non-ascii paths")
abstract class GradleRunnerExplicitDaemonJvmIntegrationTest extends BaseGradleRunnerIntegrationTest implements DaemonJvmPropertiesFixture {

    /**
     * Configure this build to use the given JVM.
     */
    def configureBuild(Jvm jvm) { }

    /**
     * Configure the gradle runner to use the given JVM.
     */
    def configureRunner(GradleRunner runner, Jvm jvm) { }

    // region Unsupported JVM

    @Requires(IntegTestPreconditions.UnsupportedDaemonJavaHomeAvailable)
    def "fails to build on unsupported jvms"() {
        given:
        configureBuild(jdk)

        when:
        newRunner(jdk, "help").build()

        then:
        IllegalStateException e = thrown()
        e.message.startsWith("An error occurred executing build")
        e.cause instanceof GradleConnectionException
        e.cause.cause.message == "Gradle ${GradleVersion.current().version} requires Java 8 or later to run. Your build is currently configured to use Java ${jdk.javaVersion.majorVersion}."

        where:
        jdk << AvailableJavaHomes.getUnsupportedDaemonJdks()
    }

    @Requires(IntegTestPreconditions.UnsupportedDaemonJavaHomeAvailable)
    def "fails to build and fail on unsupported jvms"() {
        given:
        configureBuild(jdk)

        when:
        newRunner(jdk, "help").buildAndFail()

        then:
        IllegalStateException e = thrown()
        e.message.startsWith("An error occurred executing build")
        e.cause instanceof GradleConnectionException
        e.cause.cause.message == "Gradle ${GradleVersion.current().version} requires Java 8 or later to run. Your build is currently configured to use Java ${jdk.javaVersion.majorVersion}."

        where:
        jdk << AvailableJavaHomes.getUnsupportedDaemonJdks()
    }

    @Requires(IntegTestPreconditions.UnsupportedDaemonJavaHomeAvailable)
    def "fails to run on unsupported jvms"() {
        given:
        configureBuild(jdk)

        when:
        newRunner(jdk, "help").run()

        then:
        IllegalStateException e = thrown()
        e.message.startsWith("An error occurred executing build")
        e.cause instanceof GradleConnectionException
        e.cause.cause.message == "Gradle ${GradleVersion.current().version} requires Java 8 or later to run. Your build is currently configured to use Java ${jdk.javaVersion.majorVersion}."

        where:
        jdk << AvailableJavaHomes.getUnsupportedDaemonJdks()
    }

    // endregion

    // region Deprecated JVM

    @Requires(IntegTestPreconditions.DeprecatedDaemonJavaHomeAvailable)
    def "expecting passing builds on deprecated jvm is deprecated"() {
        given:
        def jdk = AvailableJavaHomes.deprecatedDaemonJdk

        captureJavaHome()
        configureBuild(jdk)

        when:
        def result = newRunner(jdk, "help").build()

        then:
        assertDaemonUsedJvm(jdk)
        result.output.contains("Executing Gradle on JVM versions 16 and lower has been deprecated. This will fail with an error in Gradle 9.0. Use JVM 17 or greater to execute Gradle. Projects can continue to use older JVM versions via toolchains. Consult the upgrading guide for further information: https://docs.gradle.org/${gradleVersion.version}/userguide/upgrading_version_8.html#minimum_daemon_jvm_version")
    }

    @Requires(IntegTestPreconditions.DeprecatedDaemonJavaHomeAvailable)
    def "expecting failing builds on deprecated jvm is deprecated"() {
        given:
        def jdk = AvailableJavaHomes.deprecatedDaemonJdk

        captureJavaHome()
        failingBuild()
        configureBuild(jdk)

        when:
        def result = newRunner(jdk, "help").buildAndFail()

        then:
        assertDaemonUsedJvm(jdk)
        result.output.contains("Executing Gradle on JVM versions 16 and lower has been deprecated. This will fail with an error in Gradle 9.0. Use JVM 17 or greater to execute Gradle. Projects can continue to use older JVM versions via toolchains. Consult the upgrading guide for further information: https://docs.gradle.org/${gradleVersion.version}/userguide/upgrading_version_8.html#minimum_daemon_jvm_version")
        result.output.contains("A problem occurred evaluating root project")
        result.output.contains("> Boom")
    }

    @Requires(IntegTestPreconditions.DeprecatedDaemonJavaHomeAvailable)
    def "running passing builds on deprecated jvm is deprecated"() {
        given:
        def jdk = AvailableJavaHomes.deprecatedDaemonJdk

        captureJavaHome()
        configureBuild(jdk)

        when:
        def result = newRunner(jdk, "help").run()

        then:
        assertDaemonUsedJvm(jdk)
        result.output.contains("Executing Gradle on JVM versions 16 and lower has been deprecated. This will fail with an error in Gradle 9.0. Use JVM 17 or greater to execute Gradle. Projects can continue to use older JVM versions via toolchains. Consult the upgrading guide for further information: https://docs.gradle.org/${gradleVersion.version}/userguide/upgrading_version_8.html#minimum_daemon_jvm_version")
    }

    @Requires(IntegTestPreconditions.DeprecatedDaemonJavaHomeAvailable)
    def "running failing builds on deprecated jvm is deprecated"() {
        given:
        def jdk = AvailableJavaHomes.deprecatedDaemonJdk

        captureJavaHome()
        failingBuild()
        configureBuild(jdk)

        when:
        def result = newRunner(jdk, "help").run()

        then:
        result.output.contains("Executing Gradle on JVM versions 16 and lower has been deprecated. This will fail with an error in Gradle 9.0. Use JVM 17 or greater to execute Gradle. Projects can continue to use older JVM versions via toolchains. Consult the upgrading guide for further information: https://docs.gradle.org/${gradleVersion.version}/userguide/upgrading_version_8.html#minimum_daemon_jvm_version")
        result.output.contains("A problem occurred evaluating root project")
        result.output.contains("> Boom")
        assertDaemonUsedJvm(jdk)
    }

    // endregion

    // region Supported JVM

    @Requires(IntegTestPreconditions.NonDeprecatedDaemonJavaHomeAvailable)
    def "supports expecting passing builds on non deprecated jvms"() {
        given:
        def jdk = AvailableJavaHomes.nonDeprecatedDaemonJdk

        captureJavaHome()
        configureBuild(jdk)

        when:
        def result = newRunner(jdk, "help").build()

        then:
        !result.output.containsIgnoreCase("deprecated")
        assertDaemonUsedJvm(jdk)
    }

    @Issue("https://github.com/gradle/gradle/issues/13957")
    @Requires(IntegTestPreconditions.NonDeprecatedDaemonJavaHomeAvailable)
    def "supports expecting failing builds on non deprecated jvms"() {
        given:
        def jdk = AvailableJavaHomes.nonDeprecatedDaemonJdk

        captureJavaHome()
        failingBuild()
        configureBuild(jdk)

        when:
        def result = newRunner(jdk, "help").buildAndFail()

        then:
        !result.output.containsIgnoreCase("deprecated")
        result.output.contains("A problem occurred evaluating root project")
        result.output.contains("> Boom")
        assertDaemonUsedJvm(jdk)
    }

    @Requires(IntegTestPreconditions.NonDeprecatedDaemonJavaHomeAvailable)
    def "supports running passing builds on non deprecated jvms"() {
        given:
        def jdk = AvailableJavaHomes.nonDeprecatedDaemonJdk

        captureJavaHome()
        configureBuild(jdk)

        when:
        def result = newRunner(jdk, "help").run()

        then:
        !result.output.containsIgnoreCase("deprecated")
        assertDaemonUsedJvm(jdk)
    }

    @Requires(IntegTestPreconditions.NonDeprecatedDaemonJavaHomeAvailable)
    def "supports running failing builds on non deprecated jvms"() {
        given:
        def jdk = AvailableJavaHomes.nonDeprecatedDaemonJdk

        captureJavaHome()
        failingBuild()
        configureBuild(jdk)

        when:
        def result = newRunner(jdk, "help").run()

        then:
        !result.output.containsIgnoreCase("deprecated")
        result.output.contains("A problem occurred evaluating root project")
        result.output.contains("> Boom")
        assertDaemonUsedJvm(jdk)
    }

    // endregion

    final GradleRunner newRunner(Jvm jvm, String task) {
        def r = runner()
        configureRunner(r, jvm)
        r.withArguments([task] + r.getArguments())
    }

    def failingBuild() {
        buildFile << """
            throw new RuntimeException("Boom")
        """
    }

}
