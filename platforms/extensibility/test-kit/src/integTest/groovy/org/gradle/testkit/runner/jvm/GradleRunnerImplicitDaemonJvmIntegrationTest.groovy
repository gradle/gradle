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

import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import org.gradle.testkit.runner.BaseGradleRunnerIntegrationTest
import org.gradle.testkit.runner.fixtures.NonCrossVersion
import spock.lang.Issue

/**
 * Test the JVM version compatibility of the GradleRunner where the
 * client JVM is the same as the daemon JVM.
 */
@NonCrossVersion // Supporting multiple Gradle versions is more work.
@SuppressWarnings('IntegrationTestFixtures')
class GradleRunnerImplicitDaemonJvmIntegrationTest extends BaseGradleRunnerIntegrationTest {

    // region Deprecated JVM

    @Requires(UnitTestPreconditions.DeprecatedDaemonJdkVersion)
    def "expecting passing builds on deprecated jvm is deprecated"() {
        when:
        def result = runner("help").build()

        then:
        result.output.contains("Executing Gradle on JVM versions 16 and lower has been deprecated. This will fail with an error in Gradle 9.0. Use JVM 17 or greater to execute Gradle. Projects can continue to use older JVM versions via toolchains. Consult the upgrading guide for further information: https://docs.gradle.org/${gradleVersion.version}/userguide/upgrading_version_8.html#minimum_daemon_jvm_version")
    }

    @Requires(UnitTestPreconditions.DeprecatedDaemonJdkVersion)
    def "expecting failing builds on deprecated jvm is deprecated"() {
        given:
        failingBuild()

        when:
        def result = runner("help").buildAndFail()

        then:
        result.output.contains("Executing Gradle on JVM versions 16 and lower has been deprecated. This will fail with an error in Gradle 9.0. Use JVM 17 or greater to execute Gradle. Projects can continue to use older JVM versions via toolchains. Consult the upgrading guide for further information: https://docs.gradle.org/${gradleVersion.version}/userguide/upgrading_version_8.html#minimum_daemon_jvm_version")
        result.output.contains("A problem occurred evaluating root project")
        result.output.contains("> Boom")
    }

    @Requires(UnitTestPreconditions.DeprecatedDaemonJdkVersion)
    def "running passing builds on deprecated jvm is deprecated"() {
        when:
        def result = runner("help").run()

        then:
        result.output.contains("Executing Gradle on JVM versions 16 and lower has been deprecated. This will fail with an error in Gradle 9.0. Use JVM 17 or greater to execute Gradle. Projects can continue to use older JVM versions via toolchains. Consult the upgrading guide for further information: https://docs.gradle.org/${gradleVersion.version}/userguide/upgrading_version_8.html#minimum_daemon_jvm_version")
    }

    @Requires(UnitTestPreconditions.DeprecatedDaemonJdkVersion)
    def "running failing builds on deprecated jvm is deprecated"() {
        given:
        failingBuild()

        when:
        def result = runner("help").run()

        then:
        result.output.contains("Executing Gradle on JVM versions 16 and lower has been deprecated. This will fail with an error in Gradle 9.0. Use JVM 17 or greater to execute Gradle. Projects can continue to use older JVM versions via toolchains. Consult the upgrading guide for further information: https://docs.gradle.org/${gradleVersion.version}/userguide/upgrading_version_8.html#minimum_daemon_jvm_version")
        result.output.contains("A problem occurred evaluating root project")
        result.output.contains("> Boom")
    }

    // endregion

    // region Supported JVM

    @Requires(UnitTestPreconditions.NonDeprecatedDaemonJdkVersion)
    def "supports expecting passing builds on non deprecated jvms"() {
        when:
        def result = runner("help").build()

        then:
        !result.output.containsIgnoreCase("deprecated")
    }

    @Issue("https://github.com/gradle/gradle/issues/13957")
    @Requires(UnitTestPreconditions.NonDeprecatedDaemonJdkVersion)
    def "supports expecting failing builds on non deprecated jvms"() {
        given:
        failingBuild()

        when:
        def result = runner("help").buildAndFail()

        then:
        !result.output.containsIgnoreCase("deprecated")
        result.output.contains("A problem occurred evaluating root project")
        result.output.contains("> Boom")
    }

    @Requires(UnitTestPreconditions.NonDeprecatedDaemonJdkVersion)
    def "supports running passing builds on non deprecated jvms"() {
        when:
        def result = runner("help").run()

        then:
        !result.output.containsIgnoreCase("deprecated")
    }

    @Requires(UnitTestPreconditions.NonDeprecatedDaemonJdkVersion)
    def "supports running failing builds on non deprecated jvms"() {
        given:
        failingBuild()

        when:
        def result = runner("help").run()

        then:
        !result.output.containsIgnoreCase("deprecated")
        result.output.contains("A problem occurred evaluating root project")
        result.output.contains("> Boom")
    }

    // endregion

    def failingBuild() {
        buildFile << """
            throw new RuntimeException("Boom")
        """
    }

}
