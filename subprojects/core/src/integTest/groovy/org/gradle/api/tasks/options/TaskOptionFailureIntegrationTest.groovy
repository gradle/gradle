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

import org.gradle.integtests.fixtures.ToBeFixedForIsolatedProjects
import spock.lang.Ignore

import static org.gradle.api.tasks.options.TaskOptionFixture.taskWithMultipleOptions

class TaskOptionFailureIntegrationTest extends AbstractOptionIntegrationSpec {

    def "annotation cannot be assigned to a setter method multiple times"() {
        given:
        buildFile << """
            task sample(type: SampleTask)

            ${taskWithMultipleOptionsForSingleProperty('String', 'hello', 'Some description')}
        """

        when:
        runAndFail 'sample, --hello', 'test'

        then:
        failureCauseContains('Cannot specify duplicate annotation on the same member : org.gradle.api.tasks.options.Option')
    }

    @ToBeFixedForIsolatedProjects(because = "Configuring projects from root")
    def "different tasks match name but only one accepts the option"() {
        given:
        createDirs("other")
        settingsFile << "include 'other'"
        buildFile << """
            task someTask(type: SomeTask)
            project(":other") {
              task someTask
            }

            ${TaskOptionFixture.taskWithMultipleOptions()}
        """

        when:
        runAndFail 'someTask', '--first'

        then:
        failure.assertHasDescription("Problem configuring task :other:someTask from command line.")
        failure.assertHasCause("Unknown command-line option '--first'.")
    }

    def "using an unknown option yields decent error message"() {
        given:
        buildFile << """
            task foo
            task someTask(type: SomeTask)
            task someTask2(type: SomeTask)

            ${TaskOptionFixture.taskWithMultipleOptions()}
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
        failure.assertHasCause("No argument was provided for command-line option '--second' with description: 'configures 'second' field'")

        when:
        runAndFail 'someTask', '--second', 'hey', '--second', 'buddy'

        then:
        failure.assertHasDescription("Problem configuring task :someTask from command line.")
        failure.assertHasCause("Multiple arguments were provided for command-line option '--second'.")
    }

    def "single dash user error yields decent error message"() {
        when:
        runAndFail 'help', '-isk'

        then:
        failure.assertHasDescription("Problem configuring task :help from command line.")
        failure.assertHasCause("Unknown command-line option '-k'.")
    }

    @Ignore
    //more work & design decisions needed
    def "single dash error is detected in the subsequent option"() {
        given:
        buildFile << """
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
        buildFile << """
            task someTask(type: SampleTask)

            ${taskWithSingleOption('TestEnum')}
        """

        when:
        runAndFail 'someTask', '--myProp', 'unsupportedValue'

        then:
        failure.assertHasDescription("Problem configuring option 'myProp' on task ':someTask' from command line.")
        failure.assertHasCause("Cannot convert string value 'unsupportedValue' to an enum value of type 'SampleTask\$TestEnum' (valid case insensitive values: OPT_1, OPT_2, OPT_3)")
    }

    def "decent error for invalid enum list value"() {
        given:
        buildFile << """
            task someTask(type: SampleTask)

            ${taskWithSingleOption('List<TestEnum>')}
        """

        when:
        runAndFail 'someTask', '--myProp', 'unsupportedValue'

        then:
        failure.assertHasDescription("Problem configuring option 'myProp' on task ':someTask' from command line.")
        failure.assertHasCause("Cannot convert string value 'unsupportedValue' to an enum value of type 'SampleTask\$TestEnum' (valid case insensitive values: OPT_1, OPT_2, OPT_3)")

        when:
        runAndFail 'someTask', '--myProp', 'OPT_1,OPT_2'

        then:
        failure.assertHasDescription("Problem configuring option 'myProp' on task ':someTask' from command line.")
        failure.assertHasCause("Cannot convert string value 'OPT_1,OPT_2' to an enum value of type 'SampleTask\$TestEnum' (valid case insensitive values: OPT_1, OPT_2, OPT_3)")
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
        run 'tasks', '-refresh-dependencies'

        then:
        "should fail in a consistent way as with '--refresh-dependencies'"
    }

    def "cannot declare option for task dependency of another task"() {
        given:
        buildFile << """
            apply plugin: 'java'
        """

        when:
        runAndFail 'check', '--tests', 'abc'

        then:
        failure.assertHasDescription('Problem configuring task :check from command line.')
        failure.assertHasCause("Unknown command-line option '--tests'")
    }

    String taskWithMultipleOptionsForSingleProperty(String optionType, String optionName, String optionDescription) {
        """
            import org.gradle.api.DefaultTask;
            import org.gradle.api.tasks.TaskAction;
            import org.gradle.api.tasks.options.Option;

            import java.util.List;

            public class SampleTask extends DefaultTask {
                private $optionType myProp;

                @Option(option = "$optionName", description = "$optionDescription")
                @Option(option = "myProp", description = "Configures command line option 'myProp'.")
                public void setMyProp($optionType myProp) {
                    this.myProp = myProp;
                }

                @TaskAction
                public void renderOptionValue() {
                    System.out.println("Value of myProp: " + myProp);
                }

                private static enum TestEnum {
                    OPT_1, OPT_2, OPT_3
                }
            }
        """
    }
}
