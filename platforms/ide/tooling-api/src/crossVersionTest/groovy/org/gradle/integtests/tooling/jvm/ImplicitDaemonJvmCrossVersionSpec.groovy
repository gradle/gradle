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

import org.gradle.integtests.tooling.fixture.DaemonJvmPropertiesFixture
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import org.gradle.tooling.model.GradleProject

/**
 * Test the JVM version compatibility of the tooling API where the tooling
 * API client JVM is the same as the daemon JVM.
 */
@TargetGradleVersion("current") // Supporting multiple Gradle versions is more work.
class ImplicitDaemonJvmCrossVersionSpec extends ToolingApiSpecification implements DaemonJvmPropertiesFixture {

    def setup() {
        disableDaemonJavaVersionDeprecationFiltering()
    }

    // region Deprecated JVM

    @Requires(UnitTestPreconditions.DeprecatedDaemonJdkVersion)
    def "running a build with deprecated Java versions is deprecated"() {
        given:
        expectDocumentedDeprecationWarning("Executing Gradle on JVM versions 16 and lower has been deprecated. This will fail with an error in Gradle 9.0. Use JVM 17 or greater to execute Gradle. Projects can continue to use older JVM versions via toolchains. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#minimum_daemon_jvm_version")

        expect:
        succeeds { connection ->
            connection.newBuild().run()
            true
        }
    }

    @Requires(UnitTestPreconditions.DeprecatedDaemonJdkVersion)
    def "fetching a model with deprecated Java versions is deprecated"() {
        given:
        expectDocumentedDeprecationWarning("Executing Gradle on JVM versions 16 and lower has been deprecated. This will fail with an error in Gradle 9.0. Use JVM 17 or greater to execute Gradle. Projects can continue to use older JVM versions via toolchains. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#minimum_daemon_jvm_version")

        expect:
        succeeds { connection ->
            connection.model(GradleProject).get()
        }
    }

    @Requires(UnitTestPreconditions.DeprecatedDaemonJdkVersion)
    def "running an action with deprecated Java versions is deprecated"() {
        given:
        expectDocumentedDeprecationWarning("Executing Gradle on JVM versions 16 and lower has been deprecated. This will fail with an error in Gradle 9.0. Use JVM 17 or greater to execute Gradle. Projects can continue to use older JVM versions via toolchains. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#minimum_daemon_jvm_version")

        expect:
        succeeds { connection ->
            connection.action(new GetBuildJvmAction()).run()
        }
    }

    @Requires(UnitTestPreconditions.DeprecatedDaemonJdkVersion)
    def "running tests with deprecated Java versions is deprecated"() {
        given:
        writeTestFiles()
        expectDocumentedDeprecationWarning("Executing Gradle on JVM versions 16 and lower has been deprecated. This will fail with an error in Gradle 9.0. Use JVM 17 or greater to execute Gradle. Projects can continue to use older JVM versions via toolchains. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#minimum_daemon_jvm_version")

        expect:
        succeeds { connection ->
            connection.newTestLauncher().withJvmTestClasses("SomeTest").run()
            true
        }
    }

    // endregion

    // region Supported JVM

    @Requires(UnitTestPreconditions.NonDeprecatedDaemonJdkVersion)
    def "can run build with non deprecated Java versions without warning"() {
        expect:
        succeeds { connection ->
            connection.newBuild().run()
            true
        }
    }

    @Requires(UnitTestPreconditions.NonDeprecatedDaemonJdkVersion)
    def "can fetch model with non deprecated Java versions without warning"() {
        expect:
        succeeds { connection ->
            connection.model(GradleProject).get()
        }
    }

    @Requires(UnitTestPreconditions.NonDeprecatedDaemonJdkVersion)
    def "can run action with non deprecated Java versions without warning"() {
        expect:
        succeeds { connection ->
            connection.action(new GetBuildJvmAction()).run()
        }
    }

    @Requires(UnitTestPreconditions.NonDeprecatedDaemonJdkVersion)
    def "can run tests with non deprecated Java versions without warning"() {
        given:
        writeTestFiles()

        expect:
        succeeds { connection ->
            connection.newTestLauncher().withJvmTestClasses("SomeTest").run()
            true
        }
    }

    // endregion

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
