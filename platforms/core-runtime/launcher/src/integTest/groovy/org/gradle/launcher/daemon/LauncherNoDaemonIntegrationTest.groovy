/*
 * Copyright 2012 the original author or authors.
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
import org.gradle.launcher.daemon.client.SingleUseDaemonClient
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions

@Requires(IntegTestPreconditions.NotEmbeddedExecutor)
// need to fork to run with different JAVA_OPTS
class LauncherNoDaemonIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        executer.withArgument("--no-daemon")
        executer.requireIsolatedDaemons()
    }

    def "does not fork if user GRADLE_OPTS match the defaults plus the immutable args"() {
        def args = [
            "-Xms300m",
            "-Xmx600m",
            "-ea"
        ]

        given:
        executer.withArgument("--info").withTasks("help")
        requireJvmArg(args.join(" "))
        run("help")
        executer.stop()
        wasForked()
        def gradleOpts =
            output.readLines()
                .find { it.startsWith("Checking if the launcher JVM can be re-used for build. To be re-used, the launcher JVM needs to match the parameters required for the build process: ") }
                .takeAfter("the build process: ")
                .split(" ")

        when:
        executer.reset()
        setup()
        executer.useOnlyRequestedJvmOpts()
        executer.withEnvironmentVars([
            'JAVA_OPTS': gradleOpts.join(" ")
        ])
        run "help"

        then:
        wasNotForked()
    }


    private def requireJvmArg(String jvmArg) {
        file('gradle.properties') << "org.gradle.jvmargs=$jvmArg"
    }

    private void wasForked() {
        outputContains(SingleUseDaemonClient.MESSAGE)
    }

    private void wasNotForked() {
        outputDoesNotContain(SingleUseDaemonClient.MESSAGE)
    }
}
