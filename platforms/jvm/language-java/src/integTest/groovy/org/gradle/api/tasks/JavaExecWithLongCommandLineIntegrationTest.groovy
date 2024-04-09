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
import org.gradle.integtests.fixtures.UnsupportedWithConfigurationCache
import org.gradle.internal.os.OperatingSystem
import org.gradle.process.internal.util.LongCommandLineDetectionUtil

import static org.gradle.util.Matchers.containsText

class JavaExecWithLongCommandLineIntegrationTest extends AbstractIntegrationSpec {
    def veryLongFileNames = getLongCommandLine()

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
                mainClass = "driver.Driver"
            }

            task runWithJavaExec {
                dependsOn sourceSets.main.runtimeClasspath
                doLast {
                    project.javaexec {
                        if (run.executable) {
                            executable run.executable
                        }
                        classpath = run.classpath
                        mainClass = run.mainClass
                        args run.args
                    }
                }
            }

            tasks.register("runWithExecOperations") {
                dependsOn sourceSets.main.runtimeClasspath
                def runExecutable = run.executable ? run.executable : null
                def runClasspath = run.classpath
                def runMain = run.mainClass
                def runArgs = run.args
                def execOps = services.get(ExecOperations)
                doLast {
                    execOps.javaexec {
                        if (runExecutable) {
                           executable = runExecutable
                        }
                        classpath = runClasspath
                        mainClass = runMain
                        args runArgs
                    }
                }
            }
        """
    }

    @UnsupportedWithConfigurationCache(iterationMatchers = ".* project.javaexec")
    def "still fail when classpath doesn't shorten the command line enough with #method"() {
        def veryLongCommandLineArgs = getLongCommandLine(getMaxArgs() * 16)
        buildFile << """
            extraClasspath.from('${veryLongFileNames.join("','")}')

            run.args '${veryLongCommandLineArgs.join("','")}'
        """

        when:
        fails taskName

        then:
        failure.assertThatCause(containsText("could not be started because the command line exceed operating system limits."))

        where:
        method                    | taskName
        'JavaExec task'           | 'run'
        'project.javaexec'        | 'runWithJavaExec'
        'ExecOperations.javaexec' | 'runWithExecOperations'
    }

    @UnsupportedWithConfigurationCache(iterationMatchers = ".* project.javaexec")
    def "does not suggest long command line failures when execution fails with #method"() {
        buildFile << """
            extraClasspath.from('${veryLongFileNames.join("','")}')
            run.executable 'does-not-exist'
        """

        when:
        fails taskName

        then:
        failure.assertThatCause(containsText("A problem occurred starting process"))
        failure.assertHasNoCause("could not be started because the command line exceed operating system limits.")

        where:
        method                    | taskName
        'project.javaexec'        | 'runWithJavaExec'
        'ExecOperations.javaexec' | 'runWithExecOperations'
        // The test does not work with the JavaExec task because the task resolves the executable prior to starting the process.
        // At the same time, all the cases test the same functionality of the ExecHandle implementation.
        // 'JavaExec task'           | 'run'
    }

    @UnsupportedWithConfigurationCache(iterationMatchers = ".* project.javaexec")
    def "does not suggest long command line failures when execution fails for short command line with #method"() {
        buildFile << """
            run.executable 'does-not-exist'
        """

        when:
        fails taskName

        then:
        failure.assertThatCause(containsText("A problem occurred starting process"))
        failure.assertHasNoCause("could not be started because the command line exceed operating system limits.")

        where:
        method                    | taskName
        'project.javaexec'        | 'runWithJavaExec'
        'ExecOperations.javaexec' | 'runWithExecOperations'
        // The test does not work with the JavaExec task because the task resolves the executable prior to starting the process.
        // At the same time, all the cases test the same functionality of the ExecHandle implementation.
        // 'JavaExec task'           | 'run'
    }

    @UnsupportedWithConfigurationCache(iterationMatchers = ".* project.javaexec")
    def "succeeds with long classpath with #method"() {
        buildFile << """
            extraClasspath.from('${veryLongFileNames.join("','")}')
        """

        // Artificially lower the length of the command-line we try to shorten
        file("gradle.properties") << """
            systemProp.org.gradle.internal.cmdline.max.length=1000
        """

        when:
        succeeds taskName, "-i"

        then:
        executedAndNotSkipped(":$taskName")
        assertOutputContainsShorteningMessage()

        where:
        method                    | taskName
        'JavaExec task'           | 'run'
        'project.javaexec'        | 'runWithJavaExec'
        'ExecOperations.javaexec' | 'runWithExecOperations'
    }

    private void assertOutputContainsShorteningMessage() {
        outputContains("Shortening Java classpath")
    }

    private static List<String> getLongCommandLine(int maxCommandLength = getMaxArgs()) {
        final int maxIndividualArgLength = 65530
        List<String> result = new ArrayList<>()
        while (maxCommandLength > 0) {
            result.add('a' * maxIndividualArgLength)
            maxCommandLength -= maxIndividualArgLength
        }

        return result
    }

    private static int getMaxArgs() {
        switch (OperatingSystem.current()) {
            case OperatingSystem.WINDOWS:
                return LongCommandLineDetectionUtil.MAX_COMMAND_LINE_LENGTH_WINDOWS
            case OperatingSystem.MAC_OS:
                return LongCommandLineDetectionUtil.MAX_COMMAND_LINE_LENGTH_OSX
            default:
                return LongCommandLineDetectionUtil.MAX_COMMAND_LINE_LENGTH_NIX
        }
    }
}
