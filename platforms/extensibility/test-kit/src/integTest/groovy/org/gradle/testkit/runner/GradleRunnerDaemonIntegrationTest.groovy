/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.testkit.runner

import org.gradle.integtests.fixtures.executer.DaemonGradleExecuter
import org.gradle.test.fixtures.ConcurrentTestUtil
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.gradle.testkit.runner.fixtures.CustomDaemonDirectory
import org.gradle.testkit.runner.fixtures.NoDebug
import org.gradle.util.GradleVersion
import org.junit.Assume
import org.junit.Rule

@NoDebug
class GradleRunnerDaemonIntegrationTest extends BaseGradleRunnerIntegrationTest {
    def setup() {
        // https://github.com/gradle/gradle-private/issues/3207
        Assume.assumeTrue(gradleVersion >= GradleVersion.version('2.7'))
        requireIsolatedTestKitDir = true
    }

    @Rule
    final ConcurrentTestUtil concurrent = new ConcurrentTestUtil(60000)

    def "daemon process dedicated to test execution uses short idle timeout"() {
        when:
        runner().build()

        then:
        testKitDaemons().daemon.context.idleTimeout == 120000
    }

    def "daemon process dedicated to test execution is reused if one already exists"() {
        when:
        runner().build()

        then:
        def pid = testKitDaemons().daemon.with {
            assertIdle()
            context.pid
        }

        when:
        runner().build()

        then:
        testKitDaemons().daemon.context.pid == pid
    }

    @Requires(value = IntegTestPreconditions.NotEmbeddedExecutor, reason = "TestKit needs a real Gradle distribution here")
    @CustomDaemonDirectory
    def "user daemon process does not reuse existing daemon process intended for test execution even when using same gradle user home"() {
        given:
        def defaultDaemonDir = testKitDir.file("daemon")
        def nonTestKitDaemons = daemons(defaultDaemonDir, gradleVersion)

        when:
        runner().build()

        then:
        def testKitDaemon = testKitDaemons().daemon
        testKitDaemon.assertIdle()
        nonTestKitDaemons.visible.empty

        when:
        new DaemonGradleExecuter(buildContext.distribution(gradleVersion.version), testDirectoryProvider)
            .usingProjectDirectory(testDirectory)
            .withGradleUserHomeDir(testKitDir)
            .withDaemonBaseDir(defaultDaemonDir) // simulate default, our fixtures deviate from the default
            .withWarningMode(null)
            .run()

        then:
        def userDaemon = nonTestKitDaemons.daemon
        userDaemon.assertIdle()
        userDaemon.context.pid != testKitDaemon.context.pid

        cleanup:
        userDaemon?.kill()
    }

    def "executing a build with a -g option does not affect daemon mechanics"() {
        when:
        runner("-g", file("custom-gradle-user-home").absolutePath).build()

        then:
        testKitDaemons().daemon.assertIdle()
    }

    def "runners executed concurrently can share the same Gradle user home directory"() {
        when:
        3.times {
            concurrent.start {
                runner().build()
            }
        }

        then:
        concurrent.finished()
    }

}
