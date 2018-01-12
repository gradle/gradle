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

package org.gradle.api.tasks.options

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Ignore

import static org.gradle.api.tasks.options.TaskOptionFixture.taskWithMultipleOptions

class TaskOptionFailureIntegrationTest extends AbstractIntegrationSpec {

    def "different tasks match name but only one accepts the option"() {
        given:
        file("settings.gradle") << "include 'other'"
        file("build.gradle") << """
            task someTask(type: SomeTask)
            project(":other") {
              task someTask
            }

            ${taskWithMultipleOptions()}
        """

        when:
        def failure = runAndFail 'someTask', '--first'

        then:
        failure.assertHasDescription("Problem configuring task :other:someTask from command line.")
        failure.assertHasCause("Unknown command-line option '--first'.")
    }

    def "using an unknown option yields decent error message"() {
        given:
        file("build.gradle") << """
            task foo
            task someTask(type: SomeTask)
            task someTask2(type: SomeTask)

            ${taskWithMultipleOptions()}
        """

        when:
        runAndFail 'someTask', '--second', 'foo', 'someTask2', '--secon', 'bar'

        then:
        failure.assertHasDescription("Problem configuring task :someTask2 from command line.")
        failure.assertHasCause("Unknown command-line option '--secon'.")

        //TODO it's not fixable easily we would need to change some stuff in options parsing. See also ignored test method below.
//        when:
//        runAndFail 'someTask', '-second', 'foo'
//
//        then:
//        failure.assertHasDescription("Problem configuring task :someTask from command line. Unknown command-line option '-second'.")

        when:
        runAndFail 'someTask', '--second'

        then:
        failure.assertHasDescription("Problem configuring task :someTask from command line.")
        failure.assertHasCause("No argument was provided for command-line option '--second'.")

        when:
        runAndFail 'someTask', '--second', 'hey', '--second', 'buddy'

        then:
        failure.assertHasDescription("Problem configuring task :someTask from command line.")
        failure.assertHasCause("Multiple arguments were provided for command-line option '--second'.")
    }

    def "single dash user error yields decent error message"() {
        when:
        runAndFail 'help', '-tsk'

        then:
        failure.assertHasDescription("Problem configuring task :help from command line.")
        failure.assertHasCause("Unknown command-line option '-k'.")
    }

    @Ignore
    //more work & design decisions needed
    def "single dash error is detected in the subsequent option"() {
        given:
        file("build.gradle") << """
            task someTask(type: SomeTask)

            ${taskWithMultipleOptions()}
        """

        when:
        runAndFail 'someTask', '--first', '-second', 'foo'

        then:
        failure.assertHasDescription("Incorrect command line arguments: [-l, -l]. Task options require double dash, for example: 'gradle tasks --all'.")
    }

    def "decent error for invalid enum value"() {
        given:
        file("build.gradle") << """
            task someTask(type: SomeTask)

            ${taskWithMultipleOptions()}
        """

        when:
        runAndFail 'someTask', '--third', 'unsupportedValue'

        then:
        failure.assertHasDescription("Problem configuring option 'third' on task ':someTask' from command line.")
        failure.assertHasCause("Cannot convert string value 'unsupportedValue' to an enum value of type 'SomeTask\$TestEnum' (valid case insensitive values: valid1, valid2, valid3)")
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
