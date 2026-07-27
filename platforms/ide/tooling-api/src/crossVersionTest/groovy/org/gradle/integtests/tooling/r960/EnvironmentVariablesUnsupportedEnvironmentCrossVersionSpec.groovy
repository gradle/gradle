/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.integtests.tooling.r960

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.OsTestPreconditions
import org.gradle.tooling.ProjectConnection
import spock.lang.Issue

@TargetGradleVersion(">=9.6.0")
// The test uses a workaround overwriting the native lib files on disk, which are locked on Windows while the daemon that loaded them is alive
@Requires(OsTestPreconditions.NotWindows)
class EnvironmentVariablesUnsupportedEnvironmentCrossVersionSpec extends ToolingApiSpecification {

    TestFile userHome

    def setup() {
        toolingApi.requireDaemons()
        userHome = toolingApi.requireIsolatedUserHome()
    }

    @Issue("https://github.com/gradle/gradle/issues/37366")
    def "warning about dropped environment variables reaches the TAPI client when daemon cannot mutate its environment"() {
        given:
        buildFile << """
            task touchProject {
                doLast {
                    println("OK")
                }
            }
        """

        /**
         * Warmup build: let a daemon extract native-platform libraries to the isolated user home.
         * Once extracted, each library's .lock file holds an "extraction complete" marker byte.
         */
        withConnection { ProjectConnection connection -> connection.newBuild().forTasks("help").run()}

        /**
         * Workaround for https://github.com/gradle/gradle/issues/28203, simply corrupt the native libs
         * so they cannot be loaded.
         */
        corruptNativeLibraries(userHome.file("native"))

        when:
        /*
         * Pass -Xmx that is part of the daemon compatibility check so the second build
         * spawns a fresh daemon instead of reusing the warmup daemon with loaded native libs.
         * Cannot kill the daemon via the test fixture here: it will lead to a flaky clash on startup.
         */
        withConnection { ProjectConnection connection ->
            connection.newBuild()
                .setJvmArguments("-Xmx256m")
                .setEnvironmentVariables(["FOO": "BAR"])
                .forTasks("touchProject")
                .run()
        }

        then:
        stdout.toString().contains(
            "Warning: Unable to set daemon's environment variables to match the client because: \n" +
            "  There is no native integration with this operating environment.\n" +
            "  If the daemon was started with a significantly different environment from the client, and your build \n" +
            "  relies on environment variables, you may experience unexpected behavior."
        )
    }

    private static void corruptNativeLibraries(File nativeDir) {
        assert nativeDir.directory: "Expected native dir to exist after warmup: $nativeDir"
        nativeDir.eachFileRecurse { File file ->
            if (file.directory) {
                return
            }
            if (file.name.endsWith(".lock")) {
                return // preserve the "extraction complete" marker
            }
            if (file.path.contains(File.separator + "jansi" + File.separator)) {
                return // Jansi has its own loading path and is not required for the daemon's Native.init
            }
            file.bytes = new byte[0]
        }
    }
}
