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
import org.gradle.internal.jvm.Jvm
import org.gradle.internal.jvm.SupportedJavaVersionsExpectations
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.gradle.util.internal.TextUtil

/**
 * Tests that the Gradle client can or cannot be started with JVMs of certain versions.
 */
@Requires(value = IntegTestPreconditions.NotEmbeddedExecutor, reason = "explicitly testing the Gradle CLI client")
class SupportedClientJvmVersionIntegrationTest extends AbstractIntegrationSpec implements DaemonJvmPropertiesFixture, JavaToolchainFixture {

    def setup() {
        executer.disableDaemonJavaVersionDeprecationFiltering()

        // For non-daemon executors, tests single-use daemon mode
        executer.requireDaemon()
        executer.requireIsolatedDaemons()

        // Run the daemon on a supported java version, so it doesn't inherit the client version.
        propertiesFile << """
            org.gradle.java.home=${TextUtil.escapeString(Jvm.current().javaHome.canonicalPath)}
        """
    }

    @Requires(value = [IntegTestPreconditions.UnsupportedClientJavaHomeAvailable])
    def "fails to run client on java #jdk.javaVersion"() {
        given:
        executer.withStackTraceChecksDisabled()
        executer.withJvm(jdk)

        expect:
        fails("help")
        failure.assertHasErrorOutput("Unsupported major.minor version")

        where:
        jdk << AvailableJavaHomes.getUnsupportedClientJdks()
    }

    @Requires(value = [IntegTestPreconditions.DeprecatedClientJavaHomeAvailable])
    def "emits deprecation message when running client on java #jdk.javaVersion"() {
        given:
        executer.withJvm(jdk)

        expect:
        executer.expectDocumentedDeprecationWarning(SupportedJavaVersionsExpectations.getExpectedClientDeprecationWarning("CLI"))
        succeeds("help")

        where:
        jdk << AvailableJavaHomes.getDeprecatedClientJdks()
    }

    @Requires(value = [IntegTestPreconditions.NonDeprecatedClientJavaHomeAvailable])
    def "does not emit deprecation message when running client on java #jdk.javaVersion"() {
        given:
        executer.withJvm(jdk)

        expect:
        succeeds("help")

        where:
        jdk << AvailableJavaHomes.getNonDeprecatedClientJdks()
    }

}
