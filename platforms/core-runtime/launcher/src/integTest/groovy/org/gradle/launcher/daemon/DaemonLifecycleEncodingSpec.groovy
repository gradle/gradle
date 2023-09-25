/*
 * Copyright 2011 the original author or authors.
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

import org.gradle.test.fixtures.file.DoesNotSupportNonAsciiPaths
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions

@DoesNotSupportNonAsciiPaths(reason = "Requires running with ASCII file encoding")
class DaemonLifecycleEncodingSpec extends AbstractDaemonLifecycleSpec {

    def "if a daemon exists but is using a file encoding, a new compatible daemon will be created and used"() {
        when:
        startBuild(null, "US-ASCII")
        waitForBuildToWait()

        then:
        busy()
        daemonContext {
            assert daemonOpts.contains("-Dfile.encoding=US-ASCII")
        }

        then:
        completeBuild()

        then:
        idle()

        when:
        startBuild(null, "UTF-8")
        waitForLifecycleLogToContain(1, "1 incompatible")
        waitForBuildToWait()

        then:
        state 1, 1

        then:
        completeBuild(1)

        then:
        idle 2
        daemonContext(1) {
            assert daemonOpts.contains("-Dfile.encoding=UTF-8")
        }
    }

    @Requires(IntegTestPreconditions.NotEmbeddedExecutor) // need to start Gradle process from command line to use GRADLE_OPTS
    def "forks new daemon when file encoding is set to different value via GRADLE_OPTS"() {
        setup:
        buildScript """
            println "GRADLE_VERSION: " + gradle.gradleVersion

            task verify {
                doFirst {
                    println "verified = " + java.nio.charset.Charset.defaultCharset().name()
                }
            }
        """

        when:
        executer.withEnvironmentVars(GRADLE_OPTS: "-Dfile.encoding=UTF-8");
        run "verify"

        then:
        String gradleVersion = (output =~ /GRADLE_VERSION: (.*)/)[0][1]
        daemons(gradleVersion).daemons.size() == 1

        when:
        executer.withEnvironmentVars(GRADLE_OPTS: "-Dfile.encoding=ISO-8859-1");
        executer.withArgument("-i")
        run "verify"

        then:
        output.contains("verified = ISO-8859-1")
        daemons(gradleVersion).daemons.size() == 2
    }

}
