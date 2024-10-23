/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.tasks.userinput

import org.gradle.api.tasks.TasksWithInputsAndOutputs
import org.gradle.integtests.fixtures.ToBeFixedForIsolatedProjects
import org.gradle.util.internal.TextUtil

import static org.gradle.integtests.fixtures.BuildScanUserInputFixture.EOF
import static org.gradle.integtests.fixtures.BuildScanUserInputFixture.NO
import static org.gradle.integtests.fixtures.BuildScanUserInputFixture.YES
import static org.gradle.integtests.fixtures.BuildScanUserInputFixture.writeToStdInAndClose
import static org.gradle.test.fixtures.ConcurrentTestUtil.poll

class UserInputHandlingIntegrationTest extends AbstractUserInputHandlerIntegrationTest implements TasksWithInputsAndOutputs {
    private static final int INTERACTIVE_WAIT_TIME_SECONDS = 60
    private static final List<Boolean> VALID_BOOLEAN_CHOICES = [false, true]
    private static final String YES_NO_PROMPT = "thing? [yes, no]"
    private static final String BOOLEAN_PROMPT = "thing? (default: yes) [yes, no]"
    private static final String INT_PROMPT = "thing? (min: 2, default: 3)"
    private static final String SELECT_PROMPT = "select thing:"
    private static final String QUESTION_PROMPT = "what? (default: thing):"

    def setup() {
        buildFile << """
            def handler = services.get(${UserInputHandler.name})

            task askYesNo {
                def result = handler.askUser { it.askYesNoQuestion("thing?") }
                doLast {
                    println "result = " + result.getOrElse("<default>")
                }
            }

            task askBoolean {
                def result = handler.askUser { it.askBooleanQuestion("thing?", true) }
                doLast {
                    println "result = " + result.get()
                }
            }

            task askInt {
                def result = handler.askUser { it.askIntQuestion("thing?", 2, 3) }
                doLast {
                    println "result = " + result.get()
                }
            }

            task selectOption {
                def result = handler.askUser { it.selectOption("select thing", ["a", "b", "c"], "b") }
                doLast {
                    println "result = " + result.get()
                }
            }

            task ask {
                def result = handler.askUser { it.askQuestion("what?", "thing") }
                doLast {
                    println "result = " + result.get()
                }
            }
        """

        settingsFile << ''
    }

    def "can ask yes/no question in interactive build [rich console: #richConsole]"() {
        given:
        withRichConsole(richConsole)

        when:
        runWithInput("askYesNo", YES_NO_PROMPT, YES)

        then:
        result.output.count(YES_NO_PROMPT) == 1
        outputContains("result = true")

        where:
        richConsole << VALID_BOOLEAN_CHOICES
    }

    def "use of ctrl-d when asking yes/no question returns null [rich console: #richConsole]"() {
        given:
        withRichConsole(richConsole)

        when:
        runWithInterruptedInput("askYesNo")

        then:
        result.output.count(YES_NO_PROMPT) == 1
        outputContains("result = <default>")

        where:
        richConsole << VALID_BOOLEAN_CHOICES
    }

    def "can ask yes/no and handle valid input '#input' in interactive build"() {
        when:
        runWithInput("askYesNo", YES_NO_PROMPT, stdin)

        then:
        outputContains("result = $accepted")

        where:
        input | stdin | accepted
        YES   | "yes" | true
        NO    | "no"  | false
    }

    def "can ask yes/no and handle invalid input in interactive build"() {
        given:
        interactiveExecution()

        when:
        def gradleHandle = executer.withTasks("askYesNo").start()

        then:
        poll(INTERACTIVE_WAIT_TIME_SECONDS) {
            assert gradleHandle.standardOutput.contains(YES_NO_PROMPT)
        }
        gradleHandle.stdinPipe.write(input.bytes)
        gradleHandle.stdinPipe.write(TextUtil.platformLineSeparator.bytes)
        poll {
            assert gradleHandle.standardOutput.contains("Please enter 'yes' or 'no': ")
        }
        writeToStdInAndClose(gradleHandle, "yes")
        gradleHandle.waitForFinish()
        gradleHandle.standardOutput.contains("result = true")

        where:
        input    | _
        ""       | _
        "broken" | _
        "false"  | _
        "nope"   | _
        "y"      | _
        "n"      | _
    }

    @ToBeFixedForIsolatedProjects(because = "subprojects")
    def "can ask yes/no question when build is executed in parallel"() {
        given:
        withParallel()

        buildFile << """
            subprojects {
                task "askYesNo"
            }
        """
        createDirs("a", "b", "c")
        settingsFile << "include 'a', 'b', 'c'"

        when:
        runWithInput("askYesNo", YES_NO_PROMPT, "yes")

        then:
        outputContains("result = true")
    }

    def "does not prompt user for yes/no question in non-interactive build"() {
        when:
        run("askYesNo")

        then:
        outputDoesNotContain(YES_NO_PROMPT)
        outputContains("result = <default>")
    }

    def "can ask boolean question and handle valid input '#stdin' in interactive build"() {
        when:
        runWithInput("askBoolean", BOOLEAN_PROMPT, stdin)

        then:
        outputContains("result = $accepted")

        where:
        stdin | accepted
        "yes" | true
        "no"  | false
        ""    | true
    }

    def "can ask boolean question and handle invalid input in interactive build"() {
        given:
        interactiveExecution()

        when:
        def gradleHandle = executer.withTasks("askBoolean").start()

        then:
        poll(INTERACTIVE_WAIT_TIME_SECONDS) {
            assert gradleHandle.standardOutput.contains(BOOLEAN_PROMPT)
        }
        gradleHandle.stdinPipe.write(input.bytes)
        gradleHandle.stdinPipe.write(TextUtil.platformLineSeparator.bytes)
        poll {
            assert gradleHandle.standardOutput.contains("Please enter 'yes' or 'no' (default: 'yes'): ")
        }
        writeToStdInAndClose(gradleHandle, "yes")
        gradleHandle.waitForFinish()
        gradleHandle.standardOutput.contains("result = true")

        where:
        input    | _
        "broken" | _
        "false"  | _
    }

    def "does not prompt user for boolean question in non-interactive build"() {
        when:
        run("askBoolean")

        then:
        outputDoesNotContain(BOOLEAN_PROMPT)
        outputContains("result = true")
    }

    def "can ask int question and handle valid input '#stdin' in interactive build"() {
        when:
        runWithInput("askInt", INT_PROMPT, stdin)

        then:
        outputContains("result = $accepted")

        where:
        stdin | accepted
        "2"   | 2
        "34"  | 34
        ""    | 3
    }

    def "does not prompt user for int question in non-interactive build"() {
        when:
        run("askInt")

        then:
        outputDoesNotContain(INT_PROMPT)
        outputContains("result = 3")
    }

    def "can select option in interactive build [rich console: #richConsole]"() {
        given:
        withRichConsole(richConsole)

        when:
        runWithInput("selectOption", SELECT_PROMPT, "1")

        then:
        outputContains("1: a")
        outputContains("2: b")
        outputContains("3: c")
        outputContains("Enter selection (default: b) [1..3] ")
        outputContains("result = a")

        where:
        richConsole << VALID_BOOLEAN_CHOICES
    }

    def "use of ctrl-d when selection option returns default option [rich console: #richConsole]"() {
        given:
        withRichConsole(richConsole)

        when:
        runWithInterruptedInput("selectOption")

        then:
        result.output.count(SELECT_PROMPT) == 1
        outputContains("result = b")

        where:
        richConsole << VALID_BOOLEAN_CHOICES
    }

    def "can select option and handle valid input '#input' in interactive build"() {
        when:
        runWithInput("selectOption", SELECT_PROMPT, input)

        then:
        outputContains("result = $accepted")

        where:
        input | accepted
        "1"   | "a"
        "3"   | "c"
        ""    | "b"
    }

    def "can select option and handle invalid input in interactive build"() {
        given:
        interactiveExecution()

        when:
        def gradleHandle = executer.withTasks("selectOption").start()

        then:
        poll(INTERACTIVE_WAIT_TIME_SECONDS) {
            assert gradleHandle.standardOutput.contains(SELECT_PROMPT)
        }
        gradleHandle.stdinPipe.write(input.bytes)
        gradleHandle.stdinPipe.write(TextUtil.platformLineSeparator.bytes)
        poll {
            assert gradleHandle.standardOutput.contains("Please enter a value between 1 and 3: ")
        }
        writeToStdInAndClose(gradleHandle, "1")
        gradleHandle.waitForFinish()
        gradleHandle.standardOutput.contains("result = a")

        where:
        input    | _
        "broken" | _
        "0"      | _
        "a"      | _
        "4"      | _
        "yes"    | _
    }

    def "does not request user input prompt and returns default option for select option in non-interactive build"() {
        when:
        run("selectOption")

        then:
        outputDoesNotContain(SELECT_PROMPT)
        outputContains("result = b")
    }

    def "can answer text question in interactive build [rich console: #richConsole]"() {
        given:
        withRichConsole(richConsole)

        when:
        runWithInput("ask", QUESTION_PROMPT, "answer")

        then:
        outputContains("result = answer")

        where:
        richConsole << VALID_BOOLEAN_CHOICES
    }

    def "use of ctrl-d when asking text question returns default value [rich console: #richConsole]"() {
        given:
        withRichConsole(richConsole)

        when:
        runWithInterruptedInput("ask")

        then:
        result.output.count(QUESTION_PROMPT) == 1
        outputContains("result = thing")

        where:
        richConsole << VALID_BOOLEAN_CHOICES
    }

    def "can ask text question and handle valid input '#input' in interactive build"() {
        when:
        runWithInput("ask", QUESTION_PROMPT, input)

        then:
        outputContains("result = $accepted")

        where:
        input   | accepted
        "a"     | "a"
        "thing" | "thing"
        ""      | "thing"
    }

    def "does not request user input prompt and returns default option for text question in non-interactive build"() {
        when:
        run("ask")

        then:
        outputDoesNotContain(QUESTION_PROMPT)
        outputContains("result = thing")
    }

    def "task can declare user prompt as input property"() {
        given:
        taskTypeWithOutputFileProperty()
        buildFile << """
            tasks.register("generate", FileProducer) {
                output = layout.buildDirectory.file("out.txt")
                content = handler.askUser { it.askQuestion("what?", "<default>") }
            }
        """
        def prompt = "what? (default: <default>):"

        when:
        runWithInput("generate", prompt, "value")

        then:
        result.assertTaskNotSkipped(":generate")
        file("build/out.txt").text == "value"

        when:
        runWithInput("generate", prompt, "value")

        then:
        result.assertTaskSkipped(":generate")

        when:
        runWithInput("generate", prompt, "")

        then:
        result.assertTaskNotSkipped(":generate")
        file("build/out.txt").text == "<default>"

        when:
        // Non interactive
        run("generate")

        then:
        result.output.count(prompt) == 0
        result.assertTaskSkipped(":generate")
    }

    def "can use askYesNoQuestion"() {
        buildFile << """
        task askYesNoQuestion {
                def result = handler.askYesNoQuestion("thing?")
                doLast {
                    println "result = " + result
                }
            }
        """

        when:
        runWithInput("askYesNoQuestion", YES_NO_PROMPT, input)

        then:
        outputContains("result = $booleanValue")

        where:
        input  | booleanValue
        YES    | true
        NO     | false
        "What" | null
        ""     | null
    }

    void runWithInput(String task, String prompt, String input) {
        interactiveExecution()
        def gradleHandle = executer.withTasks(task).start()
        poll(INTERACTIVE_WAIT_TIME_SECONDS) {
            assert gradleHandle.standardOutput.contains(prompt)
        }
        writeToStdInAndClose(gradleHandle, input)
        result = gradleHandle.waitForFinish()
        result.output.count(prompt) == 1
    }

    void runWithInterruptedInput(String task) {
        interactiveExecution()
        def gradleHandle = executer.withTasks(task).start()
        writeToStdInAndClose(gradleHandle, EOF)
        result = gradleHandle.waitForFinish()
    }
}
