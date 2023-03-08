/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.testing

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.UnexpectedBuildFailure

class TaskBooleanOptionIntegrationTest extends AbstractIntegrationSpec {

    def "if not passed, boolean options have default value"() {
        given:
        file('buildSrc/src/main/java/SampleTask.java') << taskWithBooleanOptions()
        buildFile << sampleTask()

        when:
        run('sample')

        then:
        outputContains("Value of $optionName: $value")

        where:
        optionName                 | value
        'myBooleanPrimitiveOption' | 'false'
        'myBooleanObjectOption'    | 'null'
        'myBooleanPropertyOption'  | 'property(java.lang.Boolean, undefined)'
        'myFieldOption'            | 'null'
    }

    def "can pass boolean option"() {
        given:
        file('buildSrc/src/main/java/SampleTask.java') << taskWithBooleanOptions()
        buildFile << sampleTask()

        when:
        run('sample', "--$option")

        then:
        outputContains("Value of $option: $value")

        where:
        option                     | value
        'myBooleanPrimitiveOption' | 'true'
        'myBooleanObjectOption'    | 'true'
        'myBooleanPropertyOption'  | 'property(java.lang.Boolean, fixed(class java.lang.Boolean, true))'
        'myFieldOption'            | 'true'
    }

    def "can pass boolean disable option"() {
        given:
        file('buildSrc/src/main/java/SampleTask.java') << taskWithBooleanOptions()
        buildFile << sampleTask()

        when:
        run('sample', "--no-$option")

        then:
        outputContains("Value of $option: $value")

        where:
        option                        | value
        'myBooleanPrimitiveOption' | 'false'
        'myBooleanObjectOption'    | 'false'
        'myBooleanPropertyOption'  | 'property(java.lang.Boolean, fixed(class java.lang.Boolean, false))'
        'myFieldOption'            | 'false'
    }

    def "cannot pass boolean option value"() {
        given:
        file('buildSrc/src/main/java/SampleTask.java') << taskWithBooleanOptions()
        buildFile << sampleTask()

        when:
        run('sample', "$option=false")

        then:
        UnexpectedBuildFailure buildFailure = thrown(UnexpectedBuildFailure)
        Throwable exception = buildFailure.cause.cause.cause
        exception.message.startsWith("Command-line option '$option' does not take an argument.")

        where:
        option                          | _
        '--myBooleanPrimitiveOption'    | _
        '--myBooleanObjectOption'       | _
        '--myBooleanPropertyOption'     | _
        '--no-myBooleanPrimitiveOption' | _
        '--no-myBooleanObjectOption'    | _
        '--no-myBooleanPropertyOption'  | _
        '--no-myFieldOption'            | _
    }

    def "if option and disable option are passed multiple times, last one wins"() {
        given:
        file('buildSrc/src/main/java/SampleTask.java') << taskWithBooleanOptions()
        buildFile << sampleTask()

        when:
        run('sample', "--$option", "--no-$option")

        then:
        outputContains("Value of $option: $value1")

        when:
        run('sample', "--no-$option", "--$option")

        then:
        outputContains("Value of $option: $value2")

        where:
        option                     | value1                                                               | value2
        'myBooleanPrimitiveOption' | 'false'                                                              | 'true'
        'myBooleanObjectOption'    | 'false'                                                              | 'true'
        'myBooleanPropertyOption'  | 'property(java.lang.Boolean, fixed(class java.lang.Boolean, false))' | 'property(java.lang.Boolean, fixed(class java.lang.Boolean, true))'
        'myFieldOption'            | 'false'                                                              | 'true'
    }

    def "can render boolean options with help task"() {
        given:
        file('buildSrc/src/main/java/SampleTask.java') << taskWithBooleanOptions()
        buildFile << sampleTask()

        when:
        succeeds('help', '--task', 'sample')

        then:
        outputContains("""
Detailed task information for sample

Path
     :sample

Type
     SampleTask (SampleTask)

Options
     --myBooleanObjectOption     Configures boolean option 'myBooleanObjectOption'

     --myBooleanPrimitiveOption     Configures boolean option 'myBooleanPrimitiveOption'

     --myBooleanPropertyOption     Configures Property<Boolean> option 'myBooleanPropertyOption'

     --myFieldOption     Configures boolean option 'myFieldOption'

     --no-myBooleanObjectOption     Disables option --myBooleanObjectOption

     --no-myBooleanPrimitiveOption     Disables option --myBooleanPrimitiveOption

     --no-myBooleanPropertyOption     Disables option --myBooleanPropertyOption

     --no-myFieldOption     Disables option --myFieldOption

     --rerun     Causes the task to be re-run even if up-to-date.

Description
     -

Group
     -""")
    }

    static String sampleTask() {
        """
            task sample(type: SampleTask)
        """
    }

    static String taskWithBooleanOptions() {
        """
            import org.gradle.api.DefaultTask;
            import org.gradle.api.provider.Property;
            import org.gradle.api.tasks.TaskAction;
            import org.gradle.api.tasks.options.Option;
            import org.gradle.api.tasks.options.OptionValues;

            public class SampleTask extends DefaultTask {
                private boolean myBooleanPrimitiveOption;
                private Boolean myBooleanObjectOption;
                private Property<Boolean> myBooleanPropertyOption = getProject().getObjects().property(Boolean.class).convention((Boolean)null);

                @Option(description = "Configures boolean option 'myFieldOption'")
                private Boolean myFieldOption;

                public SampleTask() {}

                @Option(option = "myBooleanPrimitiveOption", description = "Configures boolean option 'myBooleanPrimitiveOption'")
                public void setMyBooleanPrimitiveOption(boolean myBooleanPrimitiveOption) {
                    this.myBooleanPrimitiveOption = myBooleanPrimitiveOption;
                }

                @Option(option = "myBooleanObjectOption", description = "Configures boolean option 'myBooleanObjectOption'")
                public void setMyBooleanObjectOption(Boolean myBooleanObjectOption) {
                    this.myBooleanObjectOption = myBooleanObjectOption;
                }

                @Option(option = "myBooleanPropertyOption", description = "Configures Property<Boolean> option 'myBooleanPropertyOption'")
                public void setMyBooleanPropertyOption(Boolean myBooleanPropertyOption) {
                    this.myBooleanPropertyOption.set(myBooleanPropertyOption);
                }

                public void setMyFieldOption(Boolean myFieldOption) {
                    this.myFieldOption = myFieldOption;
                }

                @TaskAction
                public void renderOptionValue() {
                    System.out.println("Value of myBooleanPrimitiveOption: " + myBooleanPrimitiveOption);
                    System.out.println("Value of myBooleanObjectOption: " + myBooleanObjectOption);
                    System.out.println("Value of myBooleanPropertyOption: " + myBooleanPropertyOption);
                    System.out.println("Value of myFieldOption: " + myFieldOption);
                }
            }
        """
    }
}
