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

package org.gradle.internal.logging.console.taskgrouping

import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.integtests.fixtures.console.AbstractConsoleGroupedTaskFunctionalTest


abstract class AbstractLoggingHooksFunctionalTest extends AbstractConsoleGroupedTaskFunctionalTest{
    def setup() {
        buildFile << """
            class CollectingListener implements StandardOutputListener {
                def result = new StringBuilder()

                String toString() {
                    return result.toString()
                }

                void onOutput(CharSequence output) {
                    result.append(output)
                }
            }
        """
    }

    @ToBeFixedForConfigurationCache(because = "Gradle.buildFinished")
    def "listener added to script receives output synchronously and only while script is running"() {
        buildFile << """
            System.out.println "before"
            System.err.println "before"

            def output = new CollectingListener()
            def error = new CollectingListener()
            logging.addStandardOutputListener(output)
            logging.addStandardErrorListener(error)

            System.out.println "output 1"
            assert output.toString().readLines() == ["output 1"]
            System.err.println "error 1"
            assert error.toString().readLines() == ["error 1"]

            task log {
                doLast {
                    System.out.println "output 2"
                    assert output.toString().readLines() == ["output 1"]
                    System.err.println "error 2"
                    assert error.toString().readLines() == ["error 1"]
                }
            }

            gradle.buildFinished {
                println "finished"
                assert output.toString().readLines() == ["output 1"]
                assert error.toString().readLines() == ["error 1"]
            }
        """

        expect:
        succeeds("log")
    }

    @ToBeFixedForConfigurationCache(because = "Gradle.buildFinished")
    def "listener added to task receives output synchronously and only while the task is running"() {
        buildFile << """
            def output = new CollectingListener()
            def error = new CollectingListener()
            def after = new CollectingListener()
            def before = new CollectingListener()

            task log {
                doLast {
                    logging.addStandardOutputListener(output)
                    logging.addStandardErrorListener(error)
                    System.out.println "output"
                    assert output.toString().readLines() == [":log", "output"]
                    System.err.println "error"
                    assert error.toString().readLines() == ["error"]
                }
            }

            // Listener added before
            log.logging.addStandardOutputListener(before)
            log.logging.addStandardErrorListener(before)
            System.out.println "ignore"
            System.err.println "ignore"

            task other {
                dependsOn log
                doLast {
                    // Listener added after
                    log.logging.addStandardOutputListener(after)
                    log.logging.addStandardErrorListener(after)
                    System.out.println "ignore"
                    System.err.println "ignore"
                }
            }

            gradle.buildFinished {
                log.logging.addStandardOutputListener(after)
                log.logging.addStandardErrorListener(after)
                println "finished"
                assert output.toString().readLines() == [":log", "output"]
                assert error.toString().readLines() == ["error"]
                assert before.toString().readLines() == [":log", "output", "error"]
                assert after.toString().readLines() == []
            }
        """

        expect:
        succeeds("log", "other")
    }

    @ToBeFixedForConfigurationCache(because = "Gradle.buildFinished")
    def "listener added to task receives logging for log level"() {
        buildFile << """
            def output = new CollectingListener()

            task log {
                doLast {
                    logging.addStandardOutputListener(output)
                    logging.addStandardErrorListener(output)
                    logger.debug("debug")
                    logger.info("info")
                    logger.lifecycle("lifecycle")
                    logger.warn("warn")
                    logger.error("error")
                    System.out.println "System.out"
                    System.err.println "System.err"
                }
            }

            gradle.buildFinished {
                file("output.txt").text = output.toString()
            }
        """

        when:
        executer.withArguments("--debug")
        run("log")
        def captured = file("output.txt").text

        then:
        captured.contains("[DEBUG] [org.gradle.api.Task] debug")
        captured.contains("[INFO] [org.gradle.api.Task] info")
        captured.contains("[LIFECYCLE] [org.gradle.api.Task] lifecycle")
        captured.contains("[WARN] [org.gradle.api.Task] warn")
        captured.contains("[ERROR] [org.gradle.api.Task] error")
        captured.contains("[QUIET] [system.out] System.out")
        captured.contains("[ERROR] [system.err] System.err")

        when:
        executer.withArguments("--info")
        run("log")
        def lines = file("output.txt").text.readLines()

        then:
        lines.containsAll([
                'info',
                'lifecycle',
                'warn',
                'error',
                'System.out',
                'System.err'
        ])

        and:
        !lines.contains('debug')

        when:
        run("log")
        lines = file("output.txt").text.readLines()

        then:
        lines.containsAll([
                ':log',
                'lifecycle',
                'warn',
                'error',
                'System.out',
                'System.err'
        ])

        and:
        !lines.contains('debug')
        !lines.contains('info')

        when:
        executer.withArguments("--warn")
        run("log")
        lines = file("output.txt").text.readLines()

        then:
        lines.containsAll([
                'warn',
                'error',
                'System.out',
                'System.err'
        ])

        and:
        !lines.contains('debug')
        !lines.contains('info')
        !lines.contains('lifecycle')

        when:
        executer.withArguments("--quiet")
        run("log")
        lines = file("output.txt").text.readLines()

        then:
        lines.containsAll([
                'error',
                'System.out',
                'System.err'
        ])

        and:
        !lines.contains('debug')
        !lines.contains('info')
        !lines.contains('lifecycle')
        !lines.contains('warn')
    }

    def "broken listener fails build but does not kill logging output"() {
        buildFile << """
            class BrokenListener implements StandardOutputListener {
                void onOutput(CharSequence output) {
                    throw new RuntimeException("broken")
                }
            }
            def output = new BrokenListener()
            def error = new BrokenListener()

            task brokenOut {
                doLast {
                    logging.addStandardOutputListener(output)
                    System.out.println "output 1"
                    assert false // should not get here
                }
            }
            task brokenErr {
                mustRunAfter brokenOut
                doLast {
                    logging.addStandardErrorListener(error)
                    System.err.println "error 1"
                    assert false // should not get here
                }
            }
            task ok {
                mustRunAfter brokenOut, brokenErr // Must not run in parallel with the broken tasks, otherwise the logging done by this task will also fail
                doLast {
                    System.out.println "output 2"
                    System.err.println "error 2"
                }
            }
        """

        expect:
        executer.withArguments("--continue")
        fails("brokenOut", "brokenErr", "ok")

        failure.assertHasFailures(2)
        failure.assertHasCause("broken")

        result.groupedOutput.task(":brokenOut").output == "output 1"
        if (errorsShouldAppearOnStdout()) {
            result.groupedOutput.task(":brokenErr").output == "error 1"
        } else {
            result.assertHasErrorOutput("error 1")
        }
        outputContains("output 2")
        result.assertHasErrorOutput("error 2")
    }
}
