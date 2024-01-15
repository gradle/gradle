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


import org.gradle.util.internal.TextUtil

import static org.gradle.integtests.fixtures.BuildScanUserInputFixture.EOF
import static org.gradle.integtests.fixtures.BuildScanUserInputFixture.NO
import static org.gradle.integtests.fixtures.BuildScanUserInputFixture.YES
import static org.gradle.integtests.fixtures.BuildScanUserInputFixture.writeToStdInAndClose
import static org.gradle.test.fixtures.ConcurrentTestUtil.poll

class UserInputHandlingIntegrationTest extends AbstractUserInputHandlerIntegrationTest {
    private static final int INTERACTIVE_WAIT_TIME_SECONDS = 60
    private static final List<Boolean> VALID_BOOLEAN_CHOICES = [false, true]
    private static final String YES_NO_PROMPT = "thing? [yes, no]"
    private static final String YES_NO_PROMPT_WITH_DEFAULT = "thing? (default: yes) [yes, no]"
    private static final String SELECT_PROMPT = "select thing:"

    def setup() {
        buildFile << """
            task askYesNo {
                doLast {
                    def handler = services.get(${UserInputHandler.name})
                    println "result = " + handler.askYesNoQuestion("thing?")
                }
            }

            task askYesNoWithDefault {
                doLast {
                    def handler = services.get(${UserInputHandler.name})
                    println "result = " + handler.askYesNoQuestion("thing?", true)
                }
            }

            task selectOption {
                doLast {
                    def handler = services.get(${UserInputHandler.name})
                    println "result = " + handler.selectOption("select thing", ["a", "b", "c"], "b")
                }
            }

            task ask {
                doLast {
                    def handler = services.get(${UserInputHandler.name})
                    println "result = " + handler.askQuestion("what?", "thing")
                }
            }
        """

        settingsFile << ''
    }

    def "can ask yes/no question in interactive build [rich console: #richConsole]"() {
        given:
        interactiveExecution()
        withRichConsole(richConsole)

        when:
        def gradleHandle = executer.withTasks("askYesNo").start()

        then:
        writeToStdInAndClose(gradleHandle, YES.bytes)
        gradleHandle.waitForFinish()
        gradleHandle.standardOutput.contains(YES_NO_PROMPT)
        gradleHandle.standardOutput.contains("result = true")

        where:
        richConsole << VALID_BOOLEAN_CHOICES
    }

    def "use of ctrl-d when asking yes/no question returns null [rich console: #richConsole]"() {
        given:
        interactiveExecution()
        withRichConsole(richConsole)

        when:
        def gradleHandle = executer.withTasks("askYesNo").start()

        then:
        writeToStdInAndClose(gradleHandle, EOF)
        gradleHandle.waitForFinish()
        gradleHandle.standardOutput.contains(YES_NO_PROMPT)
        gradleHandle.standardOutput.contains("result = null")

        where:
        richConsole << VALID_BOOLEAN_CHOICES
    }

    def "can ask yes/no and handle valid input '#input' in interactive build"() {
        given:
        interactiveExecution()

        when:
        def gradleHandle = executer.withTasks("askYesNo").start()

        then:
        writeToStdInAndClose(gradleHandle, stdin)
        gradleHandle.waitForFinish()
        gradleHandle.standardOutput.contains(YES_NO_PROMPT)
        gradleHandle.standardOutput.contains("result = $accepted")

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

    def "can ask yes/no question when build is executed in parallel"() {
        given:
        interactiveExecution()
        withParallel()

        buildFile << """
            subprojects {
                task "askYesNo"
            }
        """
        createDirs("a", "b", "c")
        settingsFile << "include 'a', 'b', 'c'"

        when:
        def gradleHandle = executer.withTasks("askYesNo").start()

        then:
        writeToStdInAndClose(gradleHandle, "yes")
        gradleHandle.waitForFinish()
        gradleHandle.standardOutput.contains(YES_NO_PROMPT)
        gradleHandle.standardOutput.contains("result = true")
    }

    def "does not request user input prompt for yes/no question in non-interactive build"() {
        when:
        def gradleHandle = executer.withTasks("askYesNo").start()

        then:
        gradleHandle.waitForFinish()
        !gradleHandle.standardOutput.contains(YES_NO_PROMPT)
        gradleHandle.standardOutput.contains("result = null")
    }

    def "can ask yes/no with default and handle valid input '#stdin' in interactive build"() {
        given:
        interactiveExecution()

        when:
        def gradleHandle = executer.withTasks("askYesNoWithDefault").start()

        then:
        writeToStdInAndClose(gradleHandle, stdin)
        gradleHandle.waitForFinish()
        gradleHandle.standardOutput.contains(YES_NO_PROMPT_WITH_DEFAULT)
        gradleHandle.standardOutput.contains("result = $accepted")

        where:
        stdin | accepted
        "yes" | true
        "no"  | false
        ""    | true
    }

    def "can ask yes/no with default and handle invalid input in interactive build"() {
        given:
        interactiveExecution()

        when:
        def gradleHandle = executer.withTasks("askYesNoWithDefault").start()

        then:
        poll(INTERACTIVE_WAIT_TIME_SECONDS) {
            assert gradleHandle.standardOutput.contains(YES_NO_PROMPT_WITH_DEFAULT)
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
        "broken" | _
        "false"  | _
    }

    def "does not request user input prompt for yes/no question with default in non-interactive build"() {
        when:
        def gradleHandle = executer.withTasks("askYesNoWithDefault").start()

        then:
        gradleHandle.waitForFinish()
        !gradleHandle.standardOutput.contains(YES_NO_PROMPT_WITH_DEFAULT)
        gradleHandle.standardOutput.contains("result = true")
    }

    def "can select option in interactive build [rich console: #richConsole]"() {
        given:
        interactiveExecution()
        withRichConsole(richConsole)

        when:
        def gradleHandle = executer.withTasks("selectOption").start()

        then:
        writeToStdInAndClose(gradleHandle, "1")
        gradleHandle.waitForFinish()
        gradleHandle.standardOutput.contains(SELECT_PROMPT)
        gradleHandle.standardOutput.contains("1: a")
        gradleHandle.standardOutput.contains("2: b")
        gradleHandle.standardOutput.contains("3: c")
        gradleHandle.standardOutput.contains("Enter selection (default: b) [1..3] ")
        gradleHandle.standardOutput.contains("result = a")

        where:
        richConsole << VALID_BOOLEAN_CHOICES
    }

    def "use of ctrl-d when selection option returns default option [rich console: #richConsole]"() {
        given:
        interactiveExecution()
        withRichConsole(richConsole)

        when:
        def gradleHandle = executer.withTasks("selectOption").start()

        then:
        writeToStdInAndClose(gradleHandle, EOF)
        gradleHandle.waitForFinish()
        gradleHandle.standardOutput.contains("result = b")

        where:
        richConsole << VALID_BOOLEAN_CHOICES
    }

    def "can select option and handle valid input '#input' in interactive build"() {
        given:
        interactiveExecution()

        when:
        def gradleHandle = executer.withTasks("selectOption").start()

        then:
        writeToStdInAndClose(gradleHandle, input)
        gradleHandle.waitForFinish()
        gradleHandle.standardOutput.contains("result = $accepted")

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
        def gradleHandle = executer.withTasks("selectOption").start()

        then:
        gradleHandle.waitForFinish()
        !gradleHandle.standardOutput.contains(SELECT_PROMPT)
        gradleHandle.standardOutput.contains("result = b")
    }

    def "can answer text question in interactive build [rich console: #richConsole]"() {
        given:
        interactiveExecution()
        withRichConsole(richConsole)

        when:
        def gradleHandle = executer.withTasks("ask").start()

        then:
        writeToStdInAndClose(gradleHandle, "answer")
        gradleHandle.waitForFinish()
        gradleHandle.standardOutput.contains("what? (default: thing): ")
        gradleHandle.standardOutput.contains("result = answer")

        where:
        richConsole << VALID_BOOLEAN_CHOICES
    }

    def "use of ctrl-d when asking text question returns default value [rich console: #richConsole]"() {
        given:
        interactiveExecution()
        withRichConsole(richConsole)

        when:
        def gradleHandle = executer.withTasks("ask").start()

        then:
        writeToStdInAndClose(gradleHandle, EOF)
        gradleHandle.waitForFinish()
        gradleHandle.standardOutput.contains("result = thing")

        where:
        richConsole << VALID_BOOLEAN_CHOICES
    }

    def "can ask text question and handle valid input '#input' in interactive build"() {
        given:
        interactiveExecution()

        when:
        def gradleHandle = executer.withTasks("ask").start()

        then:
        writeToStdInAndClose(gradleHandle, input)
        gradleHandle.waitForFinish()
        gradleHandle.standardOutput.contains("result = $accepted")

        where:
        input   | accepted
        "a"     | "a"
        "thing" | "thing"
        ""      | "thing"
    }

    def "does not request user input prompt and returns default option for text question in non-interactive build"() {
        when:
        def gradleHandle = executer.withTasks("ask").start()

        then:
        gradleHandle.waitForFinish()
        !gradleHandle.standardOutput.contains(SELECT_PROMPT)
        gradleHandle.standardOutput.contains("result = thing")
    }

}
