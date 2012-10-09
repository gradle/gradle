/*
 * Copyright 2012 the original author or authors.
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
import spock.lang.Ignore

/**
 * by Szczepan Faber, created at: 9/5/12
 */
class TaskCommandLineConfigurationIntegrationSpec extends AbstractIntegrationSpec {

    def "can configure task from command line in multiple projects"() {
        given:
        file("settings.gradle") << "include 'project2'"
        file("build.gradle") << """
            allprojects {
                task someTask(type: SomeTask)
            }
            task task1 //extra stress
            task task2

            import org.gradle.api.internal.tasks.CommandLineOption

            class SomeTask extends DefaultTask {

                boolean first
                String second

                @CommandLineOption(options = "first", description = "configures 'first' field")
                void setFirst(boolean first) {
                    this.first = first
                }

                @CommandLineOption(options = "second", description = "configures 'second' field")
                void setSecond(String second) {
                    this.second = second
                }

                //more stress
                void setSecond(Object second) {
                    this.second = second.toString()
                }

                @TaskAction
                void renderFields() {
                    println "first=" + first + ",second=" + second
                }
            }
"""

        when:
        run 'someTask'

        then:
        output.contains 'first=false,second=null'

        when:
        run 'task1', 'someTask', '--first', '--second', '"hey buddy"', 'task2'

        then:
        output.count('first=true,second="hey buddy"') == 2
        result.assertTasksExecuted(":task1", ":someTask", ":project2:someTask", ":task2")
    }

    def "task name that matches command value is not included in execution"() {
        given:
        file("build.gradle") << """
            task foo
            task someTask(type: SomeTask)

            import org.gradle.api.internal.tasks.CommandLineOption

            class SomeTask extends DefaultTask {

                String second

                @CommandLineOption(options = "second", description = "configures 'second' field")
                void setSecond(String second) {
                    this.second = second
                }

                @TaskAction
                void renderFields() {
                    println "second=" + second
                }
            }
"""

        when:
        run 'someTask', '--second', 'foo'

        then:
        output.contains 'second=foo'
        result.assertTasksExecuted(":someTask") //no 'foo' task
    }

    def "multiple different tasks configured at single command line"() {
        given:
        file("build.gradle") << """
            task foo
            task someTask(type: SomeTask)

            import org.gradle.api.internal.tasks.CommandLineOption

            class SomeTask extends DefaultTask {

                String second

                @CommandLineOption(options = "second", description = "configures 'second' field")
                void setSecond(String second) {
                    this.second = second
                }

                @TaskAction
                void renderFields() {
                    println "second=" + second
                }
            }
"""

        when:
        run 'someTask', '--second', 'foo', 'tasks', '--all'

        then:
        output.contains 'second=foo'
        result.assertTasksExecuted(":someTask", ":tasks")
    }

    def "using an unknown option yields decent error message"() {
        given:
        file("build.gradle") << """
            task foo
            task someTask(type: SomeTask)

            import org.gradle.api.internal.tasks.CommandLineOption

            class SomeTask extends DefaultTask {

                String second

                @CommandLineOption(options = "second", description = "configures 'second' field")
                void setSecond(String second) {
                    this.second = second
                }

                @TaskAction
                void renderFields() {
                    println "second=" + second
                }
            }
"""

        when:
        runAndFail 'someTask', '--secon', 'foo'

        then:
        failure.assertHasDescription("Problem configuring task :someTask from command line. Unknown command-line option '--secon'.")

        //TODO SF it's not fixable easily we would need to change some stuff in options parsing. See also ignore test method below.
//        when:
//        runAndFail 'someTask', '-second', 'foo'
//
//        then:
//        failure.assertHasDescription("Problem configuring task :someTask from command line. Unknown command-line option '-second'.")

        when:
        runAndFail 'someTask', '--second'

        then:
        failure.assertHasDescription("Problem configuring task :someTask from command line. No argument was provided for command-line option '--second'.")
    }

    def "single dash user error yields decent error message"() {
        when:
        runAndFail 'tasks', '-all'

        then:
        failure.assertHasDescription("Incorrect command line arguments: [-l, -l]. Task options require double dash, for example: 'gradle tasks --all'.")
    }

    @Ignore
    //some existing problems with command line interface
    def "incorrect behavior of command line parsing"() {
        when:
        run '-all'

        then:
        "should fail with a decent error, not internal error (applies to all CommandLineArgumentException)"
        "should complain that there's no '-all' option"

        when:
        run 'tasks', '-refresh-dependenciess'

        then:
        "should fail in a consistent way as with '--refresh-dependenciess'"
    }
}
