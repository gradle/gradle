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


package org.gradle.integtests.tooling

import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.r18.BrokenAction
import org.gradle.integtests.tooling.r18.CounterAction
import org.gradle.test.fixtures.file.DoesNotSupportNonAsciiPaths
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.gradle.test.preconditions.UnitTestPreconditions
import org.gradle.tooling.GradleConnectionException
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.model.GradleProject

@TargetGradleVersion("current")
@DoesNotSupportNonAsciiPaths(reason = "Java 6 seems to have issues with non-ascii paths")
class ToolingApiUnsupportedBuildJvmCrossVersionSpec extends ToolingApiSpecification {
    def setup() {
        toolingApi.requireDaemons()
    }

    @Requires(IntegTestPreconditions.UnsupportedJavaHomeAvailable)
    def "cannot run a build when build is configured to use Java 7 or earlier"() {
        given:
        projectDir.file("gradle.properties").writeProperties("org.gradle.java.home": jdk.javaHome.absolutePath)

        when:
        toolingApi.withConnection { ProjectConnection connection ->
            connection.newBuild().run()
        }

        then:
        GradleConnectionException e = thrown()
        e.message.startsWith("Could not execute build using ")
        e.cause.message == "Gradle ${targetDist.version.version} requires Java 8 or later to run. Your build is currently configured to use Java ${jdk.javaVersion.majorVersion}."

        where:
        jdk << AvailableJavaHomes.getJdks("1.5", "1.6", "1.7")
    }

    @Requires(IntegTestPreconditions.UnsupportedJavaHomeAvailable)
    def "cannot fetch model when build is configured to use Java 7 or earlier"() {
        given:
        projectDir.file("gradle.properties").writeProperties("org.gradle.java.home": jdk.javaHome.absolutePath)

        when:
        toolingApi.withConnection { ProjectConnection connection ->
            connection.model(GradleProject).get()
        }

        then:
        GradleConnectionException e = thrown()
        e.message.startsWith("Could not fetch model of type 'GradleProject' using ")
        e.cause.message == "Gradle ${targetDist.version.version} requires Java 8 or later to run. Your build is currently configured to use Java ${jdk.javaVersion.majorVersion}."

        where:
        jdk << AvailableJavaHomes.getJdks("1.5", "1.6", "1.7")
    }

    @Requires(IntegTestPreconditions.UnsupportedJavaHomeAvailable)
    def "cannot run action when build is configured to use Java 7 or earlier"() {
        given:
        projectDir.file("gradle.properties").writeProperties("org.gradle.java.home": jdk.javaHome.absolutePath)

        when:
        toolingApi.withConnection { ProjectConnection connection ->
            connection.action(new BrokenAction()).run()
        }

        then:
        GradleConnectionException e = thrown()
        e.message.startsWith("Could not run build action using ")
        e.cause.message == "Gradle ${targetDist.version.version} requires Java 8 or later to run. Your build is currently configured to use Java ${jdk.javaVersion.majorVersion}."

        where:
        jdk << AvailableJavaHomes.getJdks("1.5", "1.6", "1.7")
    }

    @Requires(IntegTestPreconditions.UnsupportedJavaHomeAvailable)
    def "cannot run tests when build is configured to use Java 7 or earlier"() {
        given:
        projectDir.file("gradle.properties").writeProperties("org.gradle.java.home": jdk.javaHome.absolutePath)

        when:
        toolingApi.withConnection { ProjectConnection connection ->
            connection.newTestLauncher().withJvmTestClasses("SomeTest").run()
        }

        then:
        GradleConnectionException e = thrown()
        e.message.startsWith("Could not execute tests using ")
        e.cause.message == "Gradle ${targetDist.version.version} requires Java 8 or later to run. Your build is currently configured to use Java ${jdk.javaVersion.majorVersion}."

        where:
        jdk << AvailableJavaHomes.getJdks("1.5", "1.6", "1.7")
    }

    @TargetGradleVersion(">=8.10")
    @Requires(UnitTestPreconditions.Jdk16OrEarlier)
    def "running a build with Java versions older than 17 is deprecated"() {
        given:
        noJavaVersionDeprecationExpectation()
        expectDocumentedDeprecationWarning("Executing Gradle on JVM versions 16 and lower has been deprecated. This will fail with an error in Gradle X. Use JVM 17 or greater to execute Gradle. Projects can continue to use older JVM versions via toolchains. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#minimum_daemon_jvm_version")

        expect:
        succeeds { connection ->
            connection.newBuild().run()
            true
        }
    }

    @TargetGradleVersion(">=8.10")
    @Requires(UnitTestPreconditions.Jdk16OrEarlier)
    def "fetching a model with Java versions older than 17 is deprecated"() {
        given:
        noJavaVersionDeprecationExpectation()
        expectDocumentedDeprecationWarning("Executing Gradle on JVM versions 16 and lower has been deprecated. This will fail with an error in Gradle X. Use JVM 17 or greater to execute Gradle. Projects can continue to use older JVM versions via toolchains. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#minimum_daemon_jvm_version")

        expect:
        succeeds { connection ->
            connection.model(GradleProject).get()
        }
    }

    @TargetGradleVersion(">=8.10")
    @Requires(UnitTestPreconditions.Jdk16OrEarlier)
    def "running an action with Java versions older than 17 is deprecated"() {
        given:
        noJavaVersionDeprecationExpectation()
        expectDocumentedDeprecationWarning("Executing Gradle on JVM versions 16 and lower has been deprecated. This will fail with an error in Gradle X. Use JVM 17 or greater to execute Gradle. Projects can continue to use older JVM versions via toolchains. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#minimum_daemon_jvm_version")

        expect:
        succeeds { connection ->
            connection.action(new CounterAction()).run()
        }
    }

    @TargetGradleVersion(">=8.10")
    @Requires(UnitTestPreconditions.Jdk16OrEarlier)
    def "running tests with Java versions older than 17 is deprecated"() {
        given:
        noJavaVersionDeprecationExpectation()
        expectDocumentedDeprecationWarning("Executing Gradle on JVM versions 16 and lower has been deprecated. This will fail with an error in Gradle X. Use JVM 17 or greater to execute Gradle. Projects can continue to use older JVM versions via toolchains. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#minimum_daemon_jvm_version")

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

        expect:
        succeeds { connection ->
            connection.newTestLauncher().withJvmTestClasses("SomeTest").run()
            true
        }
    }

    @TargetGradleVersion(">=8.10")
    @Requires(UnitTestPreconditions.Jdk17OrLater)
    def "can run build with Java versions 17 and greater without warning"() {
        given:
        noJavaVersionDeprecationExpectation()

        expect:
        succeeds { connection ->
            connection.newBuild().run()
            true
        }
    }

    @TargetGradleVersion(">=8.10")
    @Requires(UnitTestPreconditions.Jdk17OrLater)
    def "can fetch model with Java versions 17 and greater without warning"() {
        given:
        noJavaVersionDeprecationExpectation()

        expect:
        succeeds { connection ->
            connection.model(GradleProject).get()
        }
    }

    @TargetGradleVersion(">=8.10")
    @Requires(UnitTestPreconditions.Jdk17OrLater)
    def "can run action with Java versions 17 and greater without warning"() {
        given:
        noJavaVersionDeprecationExpectation()

        expect:
        succeeds { connection ->
            connection.action(new CounterAction()).run()
        }
    }

    @TargetGradleVersion(">=8.10")
    @Requires(UnitTestPreconditions.Jdk17OrLater)
    def "can run tests with Java versions 17 and greater without warning"() {
        given:
        noJavaVersionDeprecationExpectation()

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

        expect:
        succeeds { connection ->
            connection.newTestLauncher().withJvmTestClasses("SomeTest").run()
            true
        }
    }
}
