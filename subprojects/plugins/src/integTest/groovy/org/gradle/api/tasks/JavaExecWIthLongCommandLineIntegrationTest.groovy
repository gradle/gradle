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

            public class Driver {
                public static void main(String[] args) {}
            }
        """

        buildFile.text = """
            apply plugin: "java"

            task run(type: JavaExec) {
                classpath = project.layout.files(compileJava)
                main "driver.Driver"
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
    def "succeeds when long classpath"() {
        buildFile << """
            run.classpath += project.files('${'a' * ((ARG_MAX_WINDOWS / 2) * 3)}')
        """

        when:
        succeeds("run", "-i")

        then:
        executedAndNotSkipped(":run")
        outputContains("Gradle is shortening the command line by moving the classpath to a pathing JAR.")
    }

    @Requires(TestPrecondition.WINDOWS)
    def "still fail when classpath doesn't shorten the command line enough"() {
        buildFile << """
            run.classpath += project.files('${'a' * (ARG_MAX_WINDOWS / 2)}')
            run.args "${'b' * ARG_MAX_WINDOWS}"
        """

        when:
        succeeds("run", "-i")

        then:
        executedAndNotSkipped(":run")
        outputContains("Gradle is shortening the command line by moving the classpath to a pathing JAR.")
    }
}
