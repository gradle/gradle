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
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

import static org.gradle.process.internal.util.LongCommandLineDetectionUtil.ARG_MAX_WINDOWS
import static org.gradle.util.Matchers.containsText

class JavaExecWIthLongCommandLineIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        file("src/main/java/Driver.java").text = """
            package driver;

            import java.io.*;
            import java.lang.System;

            public class Driver {
                public static void main(String[] args) {
                    try {
                        FileWriter out = new FileWriter("out.txt");
                        out.write(System.getenv("CLASSPATH"));
                        out.write("\\n");
                        out.close();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        """

        buildFile.text = """
            apply plugin: "java"

            task run(type: JavaExec) {
                classpath = project.layout.files(compileJava)
                main "driver.Driver"
                args "1"
            }
        """
    }

    @Requires(TestPrecondition.NOT_WINDOWS)
    def "does not suggest long command line failures when execution fails on non-Windows system"() {
        buildFile << """
            run.classpath += project.files('${'a' * ARG_MAX_WINDOWS}')
            run.executable 'some-java'
        """

        when:
        def failure = fails("run")

        then:
        failure.assertThatCause(containsText("A problem occurred starting process"))
    }

    @Requires(TestPrecondition.WINDOWS)
    def "can suggest long command line failures when execution fails for long command line on Windows system"() {
        buildFile << """
            run.classpath += project.files('${'a' * ARG_MAX_WINDOWS}')
        """

        when:
        def failure = fails("run")

        then:
        failure.assertThatCause(containsText("could not be started because the command line exceed operating system limits."))
    }

    def "does not suggest long command line failures when execution fails for short command line"() {
        buildFile << """
            run.executable 'some-java'
        """

        when:
        def failure = fails("run")

        then:
        failure.assertThatCause(containsText("A problem occurred starting process"))
    }

    @Requires(TestPrecondition.WINDOWS)
    def "succeeds when long classpath fit inside environment variable"() {
        def fileName = 'a' * (ARG_MAX_WINDOWS / 2)
        buildFile << """
            run.classpath += project.files('${fileName}')
            run.args '${fileName}'
        """

        when:
        succeeds("run")

        then:
        executedAndNotSkipped(":run")
    }

    @Requires(TestPrecondition.WINDOWS)
    def "fails when long classpath fit inside environment variable but CLASSPATH was explicitly defined (no inheritance)"() {
        def fileName = 'a' * (ARG_MAX_WINDOWS / 2)
        buildFile << """
            run.classpath += project.files('${fileName}')
            run.args '${fileName}'
            run.environment('CLASSPATH', 'some-classpath-from-task')
        """

        when:
        executer.withEnvironmentVars([:]) // Currently this has no effect because the gradle[w].bat script leaks a CLASSPATH environment variable, see https://github.com/gradle/gradle/issues/10463
        def failure = fails("run", "-i")

        then:
        failure.assertThatCause(containsText("could not be started because the command line exceed operating system limits."))
        failure.assertOutputContains("CLASSPATH environment variable was explicitly overwritten, Gradle cannot shorten the command line")
    }

    @Requires(TestPrecondition.WINDOWS)
    def "fails when long classpath fit inside environment variable but CLASSPATH was explicitly overwritten"() {
        def fileName = 'a' * (ARG_MAX_WINDOWS / 2)
        buildFile << """
            run.classpath += project.files('${fileName}')
            run.args '${fileName}'
            run.environment('CLASSPATH', 'some-classpath-from-task')
        """

        when:
        executer.withEnvironmentVars([CLASSPATH: 'some-classpath'])
        def failure = fails("run", "-i")

        then:
        failure.assertThatCause(containsText("could not be started because the command line exceed operating system limits."))
        failure.assertOutputContains("CLASSPATH environment variable was explicitly overwritten, Gradle cannot shorten the command line")
    }

    @Requires(TestPrecondition.WINDOWS)
    def "fails when long classpath fit inside environment variable but CLASSPATH was explicitly cleared"() {
        def fileName = 'a' * (ARG_MAX_WINDOWS / 2)
        buildFile << """
            run.classpath += project.files('${fileName}')
            run.args '${fileName}'
            def newEnvironment = run.environment
            newEnvironment.remove('CLASSPATH')
            run.environment = newEnvironment
        """

        when:
        executer.withEnvironmentVars([CLASSPATH: 'some-classpath'])
        def failure = fails("run", "-i")

        then:
        failure.assertThatCause(containsText("could not be started because the command line exceed operating system limits."))
        failure.assertOutputContains("CLASSPATH environment variable was explicitly cleared, Gradle cannot shorten the command line")
    }

    @Requires(TestPrecondition.WINDOWS)
    def "succeeds when long classpath fit inside environment variable and CLASSPATH was inherited"() {
        def fileName = 'a' * (ARG_MAX_WINDOWS / 2)
        buildFile << """
            run.classpath += project.files('${fileName}')
            run.args '${fileName}'
        """

        when:
        executer.withEnvironmentVars([CLASSPATH: 'some-classpath'])
        succeeds("run")

        then:
        executedAndNotSkipped(":run")
        assertOutputFileIs("${file('build/classes/java/main')};${file('build/generated/sources/annotationProcessor/java/main')};${testDirectory.absolutePath}\\${fileName}\n")
    }

    @Requires(TestPrecondition.WINDOWS)
    def "fails when long classpath exceed environment variable value length"() {
        buildFile << """
            run.classpath += project.files('${'a' * ((ARG_MAX_WINDOWS / 2) * 3)}')
        """

        when:
        def failure = fails("run")

        then:
        failure.assertThatCause(containsText("could not be started because the command line exceed operating system limits."))
    }

    private void assertOutputFileIs(String text) {
        assert file("out.txt").text == text
    }
}
