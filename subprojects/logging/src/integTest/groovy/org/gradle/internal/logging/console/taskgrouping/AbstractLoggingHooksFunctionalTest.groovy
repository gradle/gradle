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

    def "listener added to task receives only the output generated while the task is running"() {
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
                    System.err.println "error" 
                }
            }
            
            log.logging.addStandardOutputListener(before)
            log.logging.addStandardErrorListener(before)

            task other {
                dependsOn log
                doLast {
                    log.logging.addStandardOutputListener(after)
                    log.logging.addStandardErrorListener(after)
                    System.out.println "other" 
                    System.err.println "other" 
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
}
