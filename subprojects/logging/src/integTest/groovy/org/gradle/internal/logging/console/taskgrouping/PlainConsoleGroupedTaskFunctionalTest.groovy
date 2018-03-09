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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

import static org.gradle.api.logging.configuration.ConsoleOutput.Plain

class PlainConsoleGroupedTaskFunctionalTest extends AbstractIntegrationSpec {
    def setup() {
        executer.withConsole(Plain)
    }

    def "output from tasks is grouped with a header"() {
        buildFile << """
            task foo {
                doFirst {
                    logger.quiet 'foo First line of text'
                    logger.quiet 'foo Second line of text'
                }
            }
            
            task bar(dependsOn: foo) {
                doFirst {
                    logger.quiet 'bar First line of text'
                    logger.quiet 'bar Second line of text'
                }
            }
        """

        when:
        succeeds('bar')

        then:
        result.groupedOutput.task(':foo').output == "foo First line of text\nfoo Second line of text"
        result.groupedOutput.task(':bar').output == "bar First line of text\nbar Second line of text"
    }

    def "output from outside of tasks are present"() {
        given:
        buildFile << """Thread.start { 
            println 'bar'
            println 'baz'
        }
        
        task foo {
            doLast {
                logger.quiet 'foo'
            }
        }
        """

        when:
        succeeds('foo')

        then:
        result.groupedOutput.task(':foo').output == "foo"
        output.contains('bar')
        output.contains('baz')
    }

    def "tasks run in parallel have their output grouped with a header"() {
        settingsFile << """
            include ':foo', ':bar'
        """
        buildFile << """
            subprojects {
                task baz {
                    doLast {
                        logger.quiet "logged from " + project.name
                        logger.quiet "also logged from " + project.name
                    }
                }
            }
        """

        when:
        args("--parallel")
        succeeds('baz')

        then:
        result.groupedOutput.task(':foo:baz').output == "logged from foo\nalso logged from foo"
        result.groupedOutput.task(':bar:baz').output == "logged from bar\nalso logged from bar"
    }

    def "tasks that log to both stdout and stderr are grouped with a header"() {
        buildFile << """
            task foo {
                doFirst {
                    logger.quiet 'foo First line of text'
                    logger.error 'foo Second line of text'
                }
            }
            
            task bar(dependsOn: foo) {
                doFirst {
                    logger.error 'bar First line of text'
                    logger.quiet 'bar Second line of text'
                }
            }
        """

        when:
        succeeds('bar')

        then:
        result.groupedOutput.task(':foo').output == "foo First line of text\nfoo Second line of text"
        result.groupedOutput.task(':bar').output == "bar First line of text\nbar Second line of text"
    }
}
