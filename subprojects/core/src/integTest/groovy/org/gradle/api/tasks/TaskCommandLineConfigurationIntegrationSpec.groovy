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

    final String someConfigurableTaskType = """
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
    }"""

    def "can configure task from command line in multiple projects"() {
        given:
        file("settings.gradle") << "include 'project2'"
        file("build.gradle") << """
            allprojects {
                task someTask(type: SomeTask)
            }
            task task1 //extra stress
            task task2

            $someConfigurableTaskType
"""

        when:
        run 'someTask'

        then:
        output.contains 'first=false,second=null'

        when:
        run 'task1', 'someTask', '--first', '--second', 'hey buddy', 'task2'

        then:
        output.count('first=true,second=hey buddy') == 2
        result.assertTasksExecuted(":task1", ":someTask", ":project2:someTask", ":task2")
    }

    def "tasks can be configured with different options"() {
        given:
        file("settings.gradle") << "include 'project2'"
        file("build.gradle") << """
            allprojects {
                task someTask(type: SomeTask)
            }

            $someConfigurableTaskType
"""

        when:
        run ':someTask', '--second', 'one', ':project2:someTask', '--second', 'two'

        then:
        result.assertTasksExecuted(":someTask", ":project2:someTask")
        output.count('second=one') == 1
        output.count('second=two') == 1
    }

    def "tasks are configured exclusively with their options"() {
        given:
        file("settings.gradle") << "include 'project2'"
        file("build.gradle") << """
            allprojects {
                task someTask(type: SomeTask)
            }

            $someConfigurableTaskType
"""

        when:
        run ':someTask', '--second', 'one', ':project2:someTask', '--first'

        then:
        result.assertTasksExecuted(":someTask", ":project2:someTask")
        output.count('first=false,second=one') == 1 //--first flag was set only on the :project2:someTask
        output.count('first=true,second=null') == 1 //--second option was set only on the :someTask
    }

    def "task name that matches command value is not included in execution"() {
        given:
        file("build.gradle") << """
            task foo
            task someTask(type: SomeTask)

            $someConfigurableTaskType
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

            $someConfigurableTaskType
"""

        when:
        run 'someTask', '--second', 'foo', 'tasks', '--all'

        then:
        output.contains 'second=foo'
        result.assertTasksExecuted(":someTask", ":tasks")
    }

    def "different tasks match name but only one accepts the option"() {
        given:
        file("settings.gradle") << "include 'other'"
        file("build.gradle") << """
            task someTask(type: SomeTask)
            project(":other") {
              task someTask
            }

            $someConfigurableTaskType
"""

        when:
        def failure = runAndFail 'someTask', '--first'

        then:
        failure.assertHasDescription("Problem configuring task :other:someTask from command line. Unknown command-line option '--first'.")
    }

    def "using an unknown option yields decent error message"() {
        given:
        file("build.gradle") << """
            task foo
            task someTask(type: SomeTask)
            task someTask2(type: SomeTask)

            $someConfigurableTaskType
"""

        when:
        runAndFail 'someTask', '--second', 'foo', 'someTask2', '--secon', 'bar'

        then:
        failure.assertHasDescription("Problem configuring task :someTask2 from command line. Unknown command-line option '--secon'.")

        //TODO it's not fixable easily we would need to change some stuff in options parsing. See also ignored test method below.
//        when:
//        runAndFail 'someTask', '-second', 'foo'
//
//        then:
//        failure.assertHasDescription("Problem configuring task :someTask from command line. Unknown command-line option '-second'.")

        when:
        runAndFail 'someTask', '--second'

        then:
        failure.assertHasDescription("Problem configuring task :someTask from command line. No argument was provided for command-line option '--second'.")

        when:
        runAndFail 'someTask', '--second', 'hey', '--second', 'buddy'

        then:
        failure.assertHasDescription("Problem configuring task :someTask from command line. Multiple arguments were provided for command-line option '--second'.")
    }

    def "single dash user error yields decent error message"() {
        when:
        runAndFail 'tasks', '-all'

        then:
        failure.assertHasDescription("Problem configuring task :tasks from command line. Unknown command-line option '-l'.")
    }

    @Ignore
    //more work & design decisions needed
    def "single dash error is detected in the subsequent option"() {
        given:
        file("build.gradle") << """
            task someTask(type: SomeTask)

            $someConfigurableTaskType
"""

        when:
        runAndFail 'someTask', '--first', '-second', 'foo'

        then:
        failure.assertHasDescription("Incorrect command line arguments: [-l, -l]. Task options require double dash, for example: 'gradle tasks --all'.")
    }

    @Ignore
    //some existing problems with command line interface
    def "unfriendly behavior of command line parsing"() {
        when:
        run '-all'

        then:
        "should fail with a decent error, not internal error (applies to all CommandLineArgumentExceptions)"
        "should complain that there's no '-all' option"

        when:
        run 'tasks', '-refresh-dependenciess'

        then:
        "should fail in a consistent way as with '--refresh-dependenciess'"
    }
}
