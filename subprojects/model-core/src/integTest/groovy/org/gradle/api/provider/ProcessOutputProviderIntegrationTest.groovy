/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.api.provider

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.process.ShellScript
import org.gradle.process.TestJavaMain
import org.gradle.util.internal.TextUtil

class ProcessOutputProviderIntegrationTest extends AbstractIntegrationSpec {
    def "providers.exec can be used during configuration time"() {
        given:
        def testScript = ShellScript.builder()
            .printArguments()
            .printEnvironmentVariable("TEST_FOO")
            .writeTo(testDirectory, "script")

        buildFile """
            def execProvider = providers.exec {
                ${cmdToExecConfig(*testScript.commandLine, "--some-arg")}
                environment("TEST_FOO", "fooValue")
            }

            execProvider.result.get().assertNormalExitValue()
            println(execProvider.standardOutput.asText.get())

            task empty() {}
        """

        when:
        run("-q", ":empty")

        then:
        outputContains("--some-arg")
        outputContains("TEST_FOO=fooValue")
    }

    def "providers.javaexec can be used during configuration time"() {
        given:
        def testClasspath = TestJavaMain.classLocation
        def testClass = TestJavaMain.class.name

        buildFile """
            def execProvider = providers.javaexec {
                classpath("${TextUtil.escapeString(testClasspath)}")
                mainClass = "$testClass"
                args("--some-arg")
                environment("TEST_FOO", "fooValue")
                systemProperty("test.foo", "fooValue")
            }

            execProvider.result.get().assertNormalExitValue()
            println(execProvider.standardOutput.asText.get())

            task empty() {}
        """

        when:
        run("-q", ":empty")

        then:
        outputContains("--some-arg")
        outputContains("TEST_FOO=fooValue")
        outputContains("test.foo=fooValue")
    }

    def "providers.exec returns a provider that throws if execution fails"() {
        given:
        def testScript = ShellScript.builder()
            .withExitValue(42)
            .writeTo(testDirectory, "script")

        buildFile """
            def execProvider = providers.exec {
                ${cmdToExecConfig(*testScript.commandLine)}
            }

            println("exit value = " + execProvider.result.get().exitValue)
            println(execProvider.standardOutput.asText.get())

            task empty() {}
        """

        when:
        runAndFail("-q", ":empty")

        then:
        result.assertHasErrorOutput("finished with non-zero exit value 42")
    }

    def "providers.exec provides exit value if exit value is ignored"() {
        given:
        def testScript = ShellScript.builder()
            .withExitValue(1)
            .writeTo(testDirectory, "script")

        buildFile """
            def execProvider = providers.exec {
                ${cmdToExecConfig(*testScript.commandLine)}
                setIgnoreExitValue(true)
            }

            println("exit value = " + execProvider.result.get().exitValue)
            println(execProvider.standardOutput.asText.get())

            task empty() {}
        """

        when:
        run("-q", ":empty")

        then:
        outputContains("exit value = 1")
    }

    def "providers.exec allows configuring working dir"() {
        given:
        def testScript = ShellScript.builder()
            .printWorkingDir()
            .writeTo(testDirectory, "script")

        def workingDir = testDirectory.createDir("workdir")

        buildFile """
            def execProvider = providers.exec {
                ${cmdToExecConfig(*testScript.commandLine)}
                workingDir("${TextUtil.escapeString(workingDir.path)}")
            }

            println(execProvider.standardOutput.asText.get())

            task empty() {}
        """

        when:
        run("-q", ":empty")

        then:
        outputContains("CWD=${workingDir.absolutePath}")
    }

    def "providers.exec provider can be wired to the task"() {
        given:
        def testScript = ShellScript.builder()
            .printText("Script output")
            .writeTo(testDirectory, "script")

        buildFile """
            def execProvider = providers.exec {
                ${cmdToExecConfig(*testScript.commandLine)}
            }

            abstract class MyTask extends DefaultTask {
                @Input
                abstract Property<String> getScriptOutput()
                @TaskAction
                def action() {
                    println(scriptOutput.get())
                }
            }
            tasks.register("printScriptOutput", MyTask) {
                scriptOutput = execProvider.standardOutput.asText
            }
        """

        when:
        run("-q", ":printScriptOutput")

        then:
        outputContains("Script output")
    }

    def "task with providers.exec provider input is up to date for second run"() {
        given:
        def testScript = ShellScript.builder()
            .printText("Script output")
            .writeTo(testDirectory, "script")

        buildFile """
            def execProvider = providers.exec {
                ${cmdToExecConfig(*testScript.commandLine)}
            }

            abstract class MyTask extends DefaultTask {
                @Input
                abstract Property<String> getScriptOutput()

                MyTask() {
                    outputs.upToDateWhen { true }
                }
                @TaskAction
                def action() {
                    println(scriptOutput.get())
                }
            }
            tasks.register("printScriptOutput", MyTask) {
                scriptOutput = execProvider.standardOutput.asText
            }
        """

        when:
        run("-q", ":printScriptOutput")
        def result = run(":printScriptOutput")

        then:
        result.assertTaskSkipped(":printScriptOutput")
    }

    def "task with providers.exec provider input is not up to date if script output changes"() {
        given:
        def testScriptName = "script"
        def testScript = ShellScript.builder()
            .printText("Script output")
            .writeTo(testDirectory, testScriptName)

        buildFile """
            def execProvider = providers.exec {
                ${cmdToExecConfig(*testScript.commandLine)}
            }

            abstract class MyTask extends DefaultTask {
                @Input
                abstract Property<String> getScriptOutput()

                MyTask() {
                    outputs.upToDateWhen { true }
                }
                @TaskAction
                def action() {
                    println(scriptOutput.get())
                }
            }
            tasks.register("printScriptOutput", MyTask) {
                scriptOutput = execProvider.standardOutput.asText
            }
        """

        when:
        run("-q", ":printScriptOutput")

        // Overwrite script file with a new text
        ShellScript.builder().printText("Other script output").writeTo(testDirectory, testScriptName)

        def result = run(":printScriptOutput")

        then:
        outputContains("Other script output")
        result.assertTaskExecuted(":printScriptOutput")
    }

    static String cmdToExecConfig(String... args) {
        return """
            commandLine(${ShellScript.cmdToVarargLiterals(args.toList())})
        """
    }
}
