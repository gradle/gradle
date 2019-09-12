/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.api.tasks

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.process.internal.util.LongCommandLineDetectionUtil
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

import static org.gradle.util.Matchers.containsText

class JavaExecWithLongCommandLineIntegrationTest extends AbstractIntegrationSpec {
    def veryLongFileName = 'a' * LongCommandLineDetectionUtil.MAX_COMMAND_LINE_LENGTH_WINDOWS

    def setup() {
        file("src/main/java/Driver.java") << """
            package driver;

            public class Driver {
                public static void main(String[] args) {}
            }
        """

        buildFile << """
            apply plugin: "java"

            def extraClasspath = files()
            task run(type: JavaExec) {
                classpath = sourceSets.main.runtimeClasspath
                classpath extraClasspath
                main "driver.Driver"
            }
            
            task runWithJavaExec {
                dependsOn sourceSets.main.runtimeClasspath
                doLast {
                    project.javaexec {
                        if (run.executable) {
                            executable run.executable
                        }
                        classpath = run.classpath
                        main run.main
                        args run.args
                    }
                }
            }
        """
    }

    @Requires(TestPrecondition.WINDOWS)
    def "still fail when classpath doesn't shorten the command line enough"() {
        def veryLongCommandLineArg = 'b' * LongCommandLineDetectionUtil.MAX_COMMAND_LINE_LENGTH_WINDOWS
        buildFile << """
            extraClasspath.from('${veryLongFileName}')
            
            run.args "${veryLongCommandLineArg}"
        """

        when:
        fails("run")

        then:
        failure.assertThatCause(containsText("could not be started because the command line exceed operating system limits."))

        when:
        fails("runWithJavaExec")

        then:
        failure.assertThatCause(containsText("could not be started because the command line exceed operating system limits."))
    }

    @Requires(TestPrecondition.NOT_WINDOWS)
    def "does not suggest long command line failures when execution fails on non-Windows system"() {
        buildFile << """
            extraClasspath.from('${veryLongFileName}')
            run.executable 'does-not-exist'
        """

        when:
        fails("run")

        then:
        failure.assertThatCause(containsText("A problem occurred starting process"))
        failure.assertHasNoCause("could not be started because the command line exceed operating system limits.")

        when:
        fails("runWithJavaExec")

        then:
        failure.assertThatCause(containsText("A problem occurred starting process"))
        failure.assertHasNoCause("could not be started because the command line exceed operating system limits.")
    }

    def "does not suggest long command line failures when execution fails for short command line"() {
        buildFile << """
            run.executable 'does-not-exist'
        """

        when:
        fails("run")

        then:
        failure.assertThatCause(containsText("A problem occurred starting process"))
        failure.assertHasNoCause("could not be started because the command line exceed operating system limits.")

        when:
        fails("runWithJavaExec")

        then:
        failure.assertThatCause(containsText("A problem occurred starting process"))
        failure.assertHasNoCause("could not be started because the command line exceed operating system limits.")
    }

    def "succeeds with long classpath"() {
        buildFile << """
            extraClasspath.from('${veryLongFileName}')
        """

        // Artificially lower the length of the command-line we try to shorten
        file("gradle.properties") << """
            systemProp.org.gradle.internal.cmdline.max.length=1000
        """

        when:
        succeeds("run", "-i")

        then:
        executedAndNotSkipped(":run")
        assertOutputContainsShorteningMessage()

        when:
        succeeds("runWithJavaExec", "-i")

        then:
        executedAndNotSkipped(":runWithJavaExec")
        assertOutputContainsShorteningMessage()
    }

    private void assertOutputContainsShorteningMessage() {
        outputContains("Shortening Java classpath")
    }
}
