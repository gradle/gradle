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


package org.gradle.integtests.tooling.jvm

import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.tooling.fixture.DaemonJvmPropertiesFixture
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.test.fixtures.file.DoesNotSupportNonAsciiPaths
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.gradle.tooling.ConfigurableLauncher
import org.gradle.tooling.GradleConnectionException
import org.gradle.tooling.model.GradleProject

/**
 * Abstract class to test the JVM version compatibility of the tooling API where the tooling
 * API client JVM is different than the daemon JVM. Subclasses implement the various ways of
 * specifying the daemon JDK version.
 */
@TargetGradleVersion("current") // Supporting multiple Gradle versions is more work.
@DoesNotSupportNonAsciiPaths(reason = "Java 6 seems to have issues with non-ascii paths")
abstract class ExplicitDaemonJvmCrossVersionSpec extends ToolingApiSpecification implements DaemonJvmPropertiesFixture {

    def setup() {
        requireDaemons()
        disableDaemonJavaVersionDeprecationFiltering()
    }

    /**
     * Configure this build to use the given JVM.
     */
    void configureBuild(String majorVersion, File javaHome) { }

    /**
     * Configure the tooling API launcher to use the given JVM.
     */
    void configureLauncher(ConfigurableLauncher<? extends ConfigurableLauncher> launcher, File javaHome) { }

    // region Unsupported JVM

    @Requires(IntegTestPreconditions.UnsupportedDaemonJavaHomeAvailable)
    def "fails to run a build with unsupported java version"() {
        given:
        configureBuild(jdk.majorVersion, jdk.javaHome)

        when:
        fails { connection ->
            def launcher = connection.newBuild()
            configureLauncher(launcher, jdk.javaHome)
            launcher.run()
        }

        then:
        def e = thrown(GradleConnectionException)
        e.message.startsWith("Could not execute build using ")
        e.cause.message == "Gradle ${targetDist.version.version} requires Java 8 or later to run. Your build is currently configured to use Java ${jdk.majorVersion}."

        where:
        jdk << getUnsupportedJdks()
    }

    @Requires(IntegTestPreconditions.UnsupportedDaemonJavaHomeAvailable)
    def "fails to fetch model with unsupported java version"() {
        given:
        configureBuild(jdk.majorVersion, jdk.javaHome)

        when:
        fails { connection ->
            def launcher = connection.model(GradleProject)
            configureLauncher(launcher, jdk.javaHome)
            launcher.get()
        }

        then:
        def e = thrown(GradleConnectionException)
        e.message.startsWith("Could not fetch model of type 'GradleProject' using ")
        e.cause.message == "Gradle ${targetDist.version.version} requires Java 8 or later to run. Your build is currently configured to use Java ${jdk.majorVersion}."

        where:
        jdk << getUnsupportedJdks()
    }

    @Requires(IntegTestPreconditions.UnsupportedDaemonJavaHomeAvailable)
    def "fails to run action with unsupported java version"() {
        given:
        configureBuild(jdk.majorVersion, jdk.javaHome)

        when:
        fails { connection ->
            def launcher = connection.action(new GetBuildJvmAction())
            configureLauncher(launcher, jdk.javaHome)
            launcher.run()
        }

        then:
        def e = thrown(GradleConnectionException)
        e.message.startsWith("Could not run build action using ")
        e.cause.message == "Gradle ${targetDist.version.version} requires Java 8 or later to run. Your build is currently configured to use Java ${jdk.majorVersion}."

        where:
        jdk << getUnsupportedJdks()
    }

    @Requires(IntegTestPreconditions.UnsupportedDaemonJavaHomeAvailable)
    def "fails to run tests with unsupported java version"() {
        given:
        configureBuild(jdk.majorVersion, jdk.javaHome)

        when:
        fails { connection ->
            def launcher = connection.newTestLauncher().withJvmTestClasses("SomeTest")
            configureLauncher(launcher, jdk.javaHome)
            launcher.run()
        }

        then:
        def e = thrown(GradleConnectionException)
        e.message.startsWith("Could not execute tests using ")
        e.cause.message == "Gradle ${targetDist.version.version} requires Java 8 or later to run. Your build is currently configured to use Java ${jdk.majorVersion}."

        where:
        jdk << getUnsupportedJdks()
    }

    // endregion

    // region Deprecated JVM

    @Requires(IntegTestPreconditions.DeprecatedDaemonJavaHomeAvailable)
    def "running a build with deprecated Java versions is deprecated"() {
        given:
        def jdk = asJavaInfo(AvailableJavaHomes.deprecatedDaemonJdk)

        captureJavaHome()
        configureBuild(jdk.majorVersion, jdk.javaHome)
        expectDocumentedDeprecationWarning("Executing Gradle on JVM versions 16 and lower has been deprecated. This will fail with an error in Gradle 9.0. Use JVM 17 or greater to execute Gradle. Projects can continue to use older JVM versions via toolchains. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#minimum_daemon_jvm_version")

         when:
        succeeds { connection ->
            def launcher = connection.newBuild()
             configureLauncher(launcher, jdk.javaHome)
            launcher.run()
        }

        then:
        assertDaemonUsedJvm(jdk.javaHome)
    }

    @Requires(IntegTestPreconditions.DeprecatedDaemonJavaHomeAvailable)
    def "fetching a model with deprecated Java versions is deprecated"() {
        given:
        def jdk = asJavaInfo(AvailableJavaHomes.deprecatedDaemonJdk)

        captureJavaHome()
        configureBuild(jdk.majorVersion, jdk.javaHome)
        expectDocumentedDeprecationWarning("Executing Gradle on JVM versions 16 and lower has been deprecated. This will fail with an error in Gradle 9.0. Use JVM 17 or greater to execute Gradle. Projects can continue to use older JVM versions via toolchains. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#minimum_daemon_jvm_version")

        when:
        succeeds { connection ->
            def launcher = connection.model(GradleProject)
            configureLauncher(launcher, jdk.javaHome)
            launcher.get()
        }

        then:
        assertDaemonUsedJvm(jdk.javaHome)
    }

    @Requires(IntegTestPreconditions.DeprecatedDaemonJavaHomeAvailable)
    def "running an action with deprecated Java versions is deprecated"() {
        given:
        def jdk = asJavaInfo(AvailableJavaHomes.deprecatedDaemonJdk)

        configureBuild(jdk.majorVersion, jdk.javaHome)
        expectDocumentedDeprecationWarning("Executing Gradle on JVM versions 16 and lower has been deprecated. This will fail with an error in Gradle 9.0. Use JVM 17 or greater to execute Gradle. Projects can continue to use older JVM versions via toolchains. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#minimum_daemon_jvm_version")

        when:
        def javaHome = succeeds { connection ->
            def launcher = connection.action(new GetBuildJvmAction())
            configureLauncher(launcher, jdk.javaHome)
            launcher.run()
        }

        then:
        // Using startsWith since the returned `javaHome` may have /jre in its path
        javaHome.absoluteFile.getPath().startsWith(jdk.javaHome.absoluteFile.getPath())
    }

    @Requires(IntegTestPreconditions.DeprecatedDaemonJavaHomeAvailable)
    def "running tests with deprecated Java versions is deprecated"() {
        given:
        def jdk = asJavaInfo(AvailableJavaHomes.deprecatedDaemonJdk)

        writeTestFiles()
        captureJavaHome()
        configureBuild(jdk.majorVersion, jdk.javaHome)
        expectDocumentedDeprecationWarning("Executing Gradle on JVM versions 16 and lower has been deprecated. This will fail with an error in Gradle 9.0. Use JVM 17 or greater to execute Gradle. Projects can continue to use older JVM versions via toolchains. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#minimum_daemon_jvm_version")

        when:
        succeeds { connection ->
            def launcher = connection.newTestLauncher().withJvmTestClasses("SomeTest")
            configureLauncher(launcher, jdk.javaHome)
            launcher.run()
        }

        then:
        assertDaemonUsedJvm(jdk.javaHome)
    }

    // endregion

    // region Supported JVM

    @Requires(IntegTestPreconditions.NonDeprecatedDaemonJavaHomeAvailable)
    def "can run build with non deprecated Java versions without warning"() {
        given:
        def jdk = asJavaInfo(AvailableJavaHomes.nonDeprecatedDaemonJdk)

        captureJavaHome()
        configureBuild(jdk.majorVersion, jdk.javaHome)

        when:
        succeeds { connection ->
            def launcher = connection.newBuild()
            configureLauncher(launcher, jdk.javaHome)
            launcher.run()
        }

        then:
        assertDaemonUsedJvm(jdk.javaHome)
    }

    @Requires(IntegTestPreconditions.NonDeprecatedDaemonJavaHomeAvailable)
    def "can fetch model with non deprecated Java versions without warning"() {
        given:
        def jdk = asJavaInfo(AvailableJavaHomes.nonDeprecatedDaemonJdk)

        captureJavaHome()
        configureBuild(jdk.majorVersion, jdk.javaHome)

        when:
        succeeds { connection ->
            def launcher = connection.model(GradleProject)
            configureLauncher(launcher, jdk.javaHome)
            launcher.get()
        }

        then:
        assertDaemonUsedJvm(jdk.javaHome)
    }

    @Requires(IntegTestPreconditions.NonDeprecatedDaemonJavaHomeAvailable)
    def "can run action with non deprecated Java versions without warning"() {
        given:
        def jdk = asJavaInfo(AvailableJavaHomes.nonDeprecatedDaemonJdk)

        configureBuild(jdk.majorVersion, jdk.javaHome)

        when:
        def javaHome = succeeds { connection ->
            def launcher = connection.action(new GetBuildJvmAction())
            configureLauncher(launcher, jdk.javaHome)
            launcher.run()
        }

        then:
        // Using startsWith since the returned `javaHome` may have /jre in its path
        javaHome.absoluteFile.getPath().startsWith(jdk.javaHome.absoluteFile.getPath())
    }

    @Requires(IntegTestPreconditions.NonDeprecatedDaemonJavaHomeAvailable)
    def "can run tests with non deprecated Java versions without warning"() {
        given:
        def jdk = asJavaInfo(AvailableJavaHomes.nonDeprecatedDaemonJdk)

        writeTestFiles()
        captureJavaHome()
        configureBuild(jdk.majorVersion, jdk.javaHome)

        when:
        succeeds { connection ->
            def launcher = connection.newTestLauncher().withJvmTestClasses("SomeTest")
            configureLauncher(launcher, jdk.javaHome)
            launcher.run()
        }

        then:
        assertDaemonUsedJvm(jdk.javaHome)
    }

    // Using dynamic Groovy since we cannot reference the Jvm type here -- due to Tooling API test weirdness
    private static JvmInfo asJavaInfo(def jvm) {
        new JvmInfo(jvm.javaVersion.majorVersion, jvm.javaHome)
    }

    /**
     * Wraps the Jvm type, as that class can not be included as part of a
     * tooling API
     */
    static class JvmInfo {

        final String majorVersion
        final File javaHome

        JvmInfo(String majorVersion, File javaHome) {
            this.majorVersion = majorVersion
            this.javaHome = javaHome
        }

        String getMajorVersion() {
            return majorVersion
        }

        File getJavaHome() {
            return javaHome
        }
    }

    // endregion

    private static List<JvmInfo> getUnsupportedJdks() {
        AvailableJavaHomes.getUnsupportedDaemonJdks().collect { asJavaInfo(it) }
    }

    void writeTestFiles() {
        buildFile << """
            plugins {
                id("java-library")
            }

            ${mavenCentralRepository()}

            testing.suites.test.useJUnitJupiter()
        """
        file("src/test/java/SomeTest.java") << """
            class SomeTest {
                @org.junit.jupiter.api.Test
                public void test() {}
            }
        """
    }

}
