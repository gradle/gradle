/*
 * Copyright 2025 the original author or authors.
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
import org.gradle.util.internal.TextUtil

@Requires(value = IntegTestPreconditions.NotEmbeddedExecutor, reason = "explicitly requests a daemon")
class DaemonToolchainPropertiesIntegrationTest extends AbstractIntegrationSpec implements DaemonJvmPropertiesFixture, JavaToolchainFixture, ToolchainPropertiesFixture {
    @Requires(IntegTestPreconditions.JavaHomeWithDifferentVersionAvailable)
    def "nags when the daemon jdk is specified as a project property on the command line"() {
        given:
        def otherJvm = AvailableJavaHomes.differentVersion
        writeJvmCriteria(otherJvm.javaVersion)
        captureJavaHome()

        expect:
        executer.withArgument("-Porg.gradle.java.installations.paths=" + otherJvm.javaHome.absolutePath)
        fails("help")

        and:
        failure.assertHasDescription(toolchainPropertyErrorMessageFor('org.gradle.java.installations.paths', otherJvm.javaHome.absolutePath))
    }

    @Requires(IntegTestPreconditions.JavaHomeWithDifferentVersionAvailable)
    def "nags when the daemon jdk is specified as a project property on the command line and as a property in gradle.properties"() {
        given:
        def otherJvm = AvailableJavaHomes.differentVersion
        writeJvmCriteria(otherJvm.javaVersion)
        captureJavaHome()

        expect:
        file("gradle.properties") << "org.gradle.java.installations.paths=" + otherJvm.javaHome.absolutePath
        executer.withArgument("-Porg.gradle.java.installations.paths=" + otherJvm.javaHome.absolutePath)
        fails("help")

        and:
        failure.assertHasDescription(toolchainPropertyErrorMessageFor('org.gradle.java.installations.paths', otherJvm.javaHome.absolutePath))
    }

    @Requires(IntegTestPreconditions.JavaHomeWithDifferentVersionAvailable)
    def "does not nag when the daemon jdk is specified as a system property on the command line"() {
        given:
        def otherJvm = AvailableJavaHomes.differentVersion
        writeJvmCriteria(otherJvm.javaVersion)
        captureJavaHome()

        expect:
        executer.withArgument("-Dorg.gradle.java.installations.paths=" + otherJvm.javaHome.absolutePath)
        succeeds("help")
        assertDaemonUsedJvm(otherJvm)
    }

    @Requires(IntegTestPreconditions.JavaHomeWithDifferentVersionAvailable)
    def "does not nag when the daemon jdk is specified in gradle.properties"() {
        given:
        def otherJvm = AvailableJavaHomes.differentVersion
        writeJvmCriteria(otherJvm.javaVersion)
        captureJavaHome()

        expect:
        file("gradle.properties") << "org.gradle.java.installations.paths=" + TextUtil.normaliseFileSeparators(otherJvm.javaHome.absolutePath)
        succeeds("help")
        assertDaemonUsedJvm(otherJvm)
    }

    @Requires(IntegTestPreconditions.JavaHomeWithDifferentVersionAvailable)
    def "does not nag when the daemon jdk is specified in gradle user home gradle.properties"() {
        given:
        def otherJvm = AvailableJavaHomes.differentVersion
        writeJvmCriteria(otherJvm.javaVersion)
        captureJavaHome()

        expect:
        executer.requireOwnGradleUserHomeDir("so we can set properties in GUH/gradle.properties")
        file("user-home/gradle.properties") << "org.gradle.java.installations.paths=" + TextUtil.normaliseFileSeparators(otherJvm.javaHome.absolutePath)
        succeeds("help")
        assertDaemonUsedJvm(otherJvm)
    }

    @Requires(IntegTestPreconditions.JavaHomeWithDifferentVersionAvailable)
    def "does not nag when the daemon jdk is specified as a system property and a project property on the command line"() {
        given:
        def otherJvm = AvailableJavaHomes.differentVersion
        writeJvmCriteria(otherJvm.javaVersion)
        captureJavaHome()

        expect:
        executer.withArgument("-Dorg.gradle.java.installations.paths=" + otherJvm.javaHome.absolutePath)
        executer.withArgument("-Porg.gradle.java.installations.paths=" + otherJvm.javaHome.absolutePath)
        succeeds("help")
        assertDaemonUsedJvm(otherJvm)
    }

    @Requires(IntegTestPreconditions.JavaHomeWithDifferentVersionAvailable)
    def "sensible error when the daemon jdk is specified as a system property and a project property on the command line and the values differ"() {
        given:
        def otherJvm = AvailableJavaHomes.differentVersion
        writeJvmCriteria(otherJvm.javaVersion)
        captureJavaHome()

        expect:
        executer.withArgument("-Dorg.gradle.java.installations.paths=" + Jvm.current().javaHome.absolutePath)
        executer.withArgument("-Porg.gradle.java.installations.paths=" + otherJvm.javaHome.absolutePath)
        fails("help")

        and:
        failure.assertHasDescription(toolchainPropertyMisMatchErrorFor('org.gradle.java.installations.paths', Jvm.current().javaHome.absolutePath, otherJvm.javaHome.absolutePath))
    }
}
