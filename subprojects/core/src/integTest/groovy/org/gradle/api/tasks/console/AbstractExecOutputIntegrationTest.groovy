/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.tasks.console

import org.gradle.integtests.fixtures.UnsupportedWithConfigurationCache
import org.gradle.integtests.fixtures.console.AbstractConsoleGroupedTaskFunctionalTest
import spock.lang.Issue
import spock.util.environment.OperatingSystem

@Issue("https://github.com/gradle/gradle/issues/2009")
abstract class AbstractExecOutputIntegrationTest extends AbstractConsoleGroupedTaskFunctionalTest {
    private static final String EXPECTED_OUTPUT = "Hello, World!"
    private static final String EXPECTED_ERROR = "Goodbye, World!"

    @UnsupportedWithConfigurationCache(because = "Task.getProject() during execution")
    def "Project.javaexec output is grouped with its task output"() {
        given:
        generateMainJavaFileEchoing(EXPECTED_OUTPUT, EXPECTED_ERROR)
        buildFile << """
            apply plugin: 'java'

            task run {
                dependsOn 'compileJava'
                doLast {
                    project.javaexec {
                        classpath = sourceSets.main.runtimeClasspath
                        mainClass = 'Main'
                    }
                }
            }
        """

        when:
        if (shouldCheckDeprecations()) {
            executer.expectDocumentedDeprecationWarning("The Project.javaexec(Closure) method has been deprecated. " +
                "This is scheduled to be removed in Gradle 9.0. " +
                "Use ExecOperations.javaexec(Action) or ProviderFactory.javaexec(Action) instead. " +
                "Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#deprecated_project_exec")
            executer.expectDocumentedDeprecationWarning("Invocation of Task.project at execution time has been deprecated. "+
                "This will fail with an error in Gradle 9.0. " +
                "Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_7.html#task_project")
        }
        executer.withConsole(consoleType)
        succeeds("run")

        then:
        def output = result.groupedOutput.task(':run').output
        output.contains(EXPECTED_OUTPUT)
        def errorOutput = errorsShouldAppearOnStdout() ? output : result.getError()
        errorOutput.contains(EXPECTED_ERROR)
    }

    def "ExecOperations.javaexec output is grouped with its task output"() {
        given:
        generateMainJavaFileEchoing(EXPECTED_OUTPUT, EXPECTED_ERROR)
        buildFile << """
            apply plugin: 'java'

            task run {
                dependsOn 'compileJava'
                def execOps = services.get(ExecOperations)
                def execClasspath = sourceSets.main.runtimeClasspath
                doLast {
                    execOps.javaexec {
                        classpath = execClasspath
                        mainClass = 'Main'
                    }
                }
            }
        """

        when:
        executer.withConsole(consoleType)
        succeeds("run")

        then:
        def output = result.groupedOutput.task(':run').output
        output.contains(EXPECTED_OUTPUT)
        def errorOutput = errorsShouldAppearOnStdout() ? output : result.getError()
        errorOutput.contains(EXPECTED_ERROR)
    }

    def "JavaExec task output is grouped with its task output"() {
        given:
        generateMainJavaFileEchoing(EXPECTED_OUTPUT, EXPECTED_ERROR)
        buildFile << """
            apply plugin: 'java'

            task run(type: JavaExec) {
                dependsOn 'compileJava'
                classpath = sourceSets.main.runtimeClasspath
                mainClass = 'Main'
            }
        """

        when:
        executer.withConsole(consoleType)
        succeeds("run")

        then:
        def output = result.groupedOutput.task(':run').output
        output.contains(EXPECTED_OUTPUT)
        def errorOutput = errorsShouldAppearOnStdout() ? output : result.getError()
        errorOutput.contains(EXPECTED_ERROR)
    }

    @UnsupportedWithConfigurationCache(because = "Task.getProject() during execution")
    def "Project.exec output is grouped with its task output"() {
        given:
        buildFile << """
            task run {
                doLast {
                    project.exec {
                        commandLine ${echo(EXPECTED_OUTPUT)}
                    }
                }
            }
        """

        when:
        executer.withConsole(consoleType)
        if (shouldCheckDeprecations()) {
            executer.expectDocumentedDeprecationWarning("The Project.exec(Closure) method has been deprecated. " +
                "This is scheduled to be removed in Gradle 9.0. " +
                "Use ExecOperations.exec(Action) or ProviderFactory.exec(Action) instead. " +
                "Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#deprecated_project_exec")
            executer.expectDocumentedDeprecationWarning("Invocation of Task.project at execution time has been deprecated. "+
                "This will fail with an error in Gradle 9.0. " +
                "Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_7.html#task_project")
        }
        succeeds("run")

        then:
        result.groupedOutput.task(':run').output.contains(EXPECTED_OUTPUT)
    }

    def "ExecOperations.exec output is grouped with its task output"() {
        given:
        buildFile << """
            task run {
                def execOps = services.get(ExecOperations)
                doLast {
                    execOps.exec {
                        commandLine ${echo(EXPECTED_OUTPUT)}
                    }
                }
            }
        """

        when:
        executer.withConsole(consoleType).withArgument("--no-problems-report")
        succeeds("run")

        then:
        result.groupedOutput.task(':run').output == EXPECTED_OUTPUT
    }

    def "Exec task output is grouped with its task output"() {
        given:
        buildFile << """
            task run(type: Exec) {
                commandLine ${echo(EXPECTED_OUTPUT)}
            }
        """

        when:
        executer.withConsole(consoleType).withArgument("--no-problems-report")
        succeeds("run")

        then:
        result.groupedOutput.task(':run').output == EXPECTED_OUTPUT
    }

    private static String echo(String s) {
        if (OperatingSystem.current.windows) {
            return "'cmd.exe', '/d', '/c', 'echo $s'"
        }
        return "'echo', '$s'"
    }

    private void generateMainJavaFileEchoing(String out, String err) {
        file("src/main/java/Main.java") << """
            public class Main {
                public static void main(String[] args) {
                    System.out.println("$out");
                    System.err.println("$err");
                }
            }
        """
    }
}
