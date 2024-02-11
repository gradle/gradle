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

class TaskBooleanOptionIntegrationTest extends AbstractIntegrationSpec {

    def "if not passed, boolean options have default value with #optionName=#value"() {
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
        'myBooleanPropertyOption'  | 'null'
        'myFieldOption'            | 'null'
        'feature'                  | 'false'
        'propertyFeature'          | 'null'
    }

    def "can pass boolean option with #option=#value"() {
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
        'myBooleanPropertyOption'  | 'true'
        'myFieldOption'            | 'true'
        'feature'                  | 'false'
        'propertyFeature'          | 'false'
    }

    def "can pass boolean disable option #option=#value"() {
        given:
        file('buildSrc/src/main/java/SampleTask.java') << taskWithBooleanOptions()
        buildFile << sampleTask()

        when:
        run('sample', "--no-$option")

        then:
        outputContains("Value of $option: $value")

        where:
        option                     | value
        'myBooleanPrimitiveOption' | 'false'
        'myBooleanObjectOption'    | 'false'
        'myBooleanPropertyOption'  | 'false'
        'myFieldOption'            | 'false'
        'feature'                  | 'true'
        'propertyFeature'          | 'true'
    }

    def "cannot pass boolean option value with #option"() {
        given:
        file('buildSrc/src/main/java/SampleTask.java') << taskWithBooleanOptions()
        buildFile << sampleTask()

        expect:
        def failure = fails('sample', "$option=false")
        failure.assertHasCause("Command-line option '$option' does not take an argument.")

        where:
        option << ['--myBooleanPrimitiveOption',
                   '--myBooleanObjectOption',
                   '--myBooleanPropertyOption',
                   '--myFieldOption',
                   '--feature',
                   '--propertyFeature',
                   '--no-myBooleanPrimitiveOption',
                   '--no-myBooleanObjectOption',
                   '--no-myBooleanPropertyOption',
                   '--no-myFieldOption',
                   '--no-feature',
                   '--no-propertyFeature'
        ]
    }

    def "if option and disable option are passed multiple times, last one wins with #option value1=#value1 value2=#value2"() {
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
        option                     | value1  | value2
        'myBooleanPrimitiveOption' | 'false' | 'true'
        'myBooleanObjectOption'    | 'false' | 'true'
        'myBooleanPropertyOption'  | 'false' | 'true'
        'myFieldOption'            | 'false' | 'true'
        'feature'                  | 'true'  | 'false'
        'propertyFeature'          | 'true'  | 'false'
    }

    def "options of a task shadow clash with generated opposite options with #options"() {
        given:
        file('buildSrc/src/main/java/SampleTask.java') << taskWithBooleanOppositeOptionNameClashing()
        buildFile << sampleTask()

        when:
        run('sample', *options)

        then:
        outputContains("Value of my-option: $myOptionValue")
        outputContains("Value of no-my-option: $noMyOptionValue")
        outputContains("Opposite option 'my-option' in task task ':sample' was disabled for clashing with another option of same name")
        outputContains("Opposite option 'no-my-option' in task task ':sample' was disabled for clashing with another option of same name")

        where:
        options                           | myOptionValue | noMyOptionValue
        ["--my-option"]                   | true          | null
        ["--no-my-option"]                | null          | true
        ["--my-option", "--no-my-option"] | true          | true
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
     --feature     Opposite option of --no-feature.

     --no-feature     Configures boolean option 'feature' that is only negated.

     --myBooleanObjectOption     Configures boolean option 'myBooleanObjectOption'.

     --no-myBooleanObjectOption     Disables option --myBooleanObjectOption.

     --myBooleanPrimitiveOption     Configures boolean option 'myBooleanPrimitiveOption'.

     --no-myBooleanPrimitiveOption     Disables option --myBooleanPrimitiveOption.

     --myBooleanPropertyOption     Configures Property<Boolean> option 'myBooleanPropertyOption'.

     --no-myBooleanPropertyOption     Disables option --myBooleanPropertyOption.

     --myFieldOption     Configures boolean option 'myFieldOption'.

     --no-myFieldOption     Disables option --myFieldOption.

     --propertyFeature     Opposite option of --no-propertyFeature.

     --no-propertyFeature     Configures boolean option 'propertyFeature' that is only negated.

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

            public class SampleTask extends DefaultTask {
                private boolean myBooleanPrimitiveOption;
                private Boolean myBooleanObjectOption;
                private Property<Boolean> myBooleanPropertyOption = getProject().getObjects().property(Boolean.class).convention((Boolean)null);

                @Option(description = "Configures boolean option 'myFieldOption'.")
                private Boolean myFieldOption;

                @Option(option = "no-feature", description = "Configures boolean option 'feature' that is only negated.")
                private Boolean feature = false;

                private Property<Boolean> propertyFeature = getProject().getObjects().property(Boolean.class).convention((Boolean)null);

                public SampleTask() {}

                @Option(option = "myBooleanPrimitiveOption", description = "Configures boolean option 'myBooleanPrimitiveOption'.")
                public void setMyBooleanPrimitiveOption(boolean myBooleanPrimitiveOption) {
                    this.myBooleanPrimitiveOption = myBooleanPrimitiveOption;
                }

                @Option(option = "myBooleanObjectOption", description = "Configures boolean option 'myBooleanObjectOption'.")
                public void setMyBooleanObjectOption(Boolean myBooleanObjectOption) {
                    this.myBooleanObjectOption = myBooleanObjectOption;
                }

                @Option(option = "myBooleanPropertyOption", description = "Configures Property<Boolean> option 'myBooleanPropertyOption'.")
                public void setMyBooleanPropertyOption(Boolean myBooleanPropertyOption) {
                    this.myBooleanPropertyOption.set(myBooleanPropertyOption);
                }

                public void setMyFieldOption(Boolean myFieldOption) {
                    this.myFieldOption = myFieldOption;
                }

                public void setFeature(Boolean feature) {
                    this.feature = feature;
                }

                @Option(option = "no-propertyFeature", description = "Configures boolean option 'propertyFeature' that is only negated.")
                public void setPropertyFeature(Boolean propertyFeature) {
                    this.propertyFeature.set(propertyFeature);
                }


                @TaskAction
                public void renderOptionValue() {
                    System.out.println("Value of myBooleanPrimitiveOption: " + myBooleanPrimitiveOption);
                    System.out.println("Value of myBooleanObjectOption: " + myBooleanObjectOption);
                    System.out.println("Value of myBooleanPropertyOption: " + myBooleanPropertyOption.getOrNull());
                    System.out.println("Value of myFieldOption: " + myFieldOption);
                    System.out.println("Value of feature: " + feature);
                    System.out.println("Value of propertyFeature: " + propertyFeature.getOrNull());
                }
            }
        """
    }

    static String taskWithBooleanOppositeOptionNameClashing() {
        """
            import org.gradle.api.DefaultTask;
            import org.gradle.api.provider.Property;
            import org.gradle.api.tasks.TaskAction;
            import org.gradle.api.tasks.options.Option;

            public class SampleTask extends DefaultTask {

                @Option(option = "my-option", description = "Option to trigger generation of opposite option.")
                private Boolean myOption;

                @Option(option = "no-my-option", description = "Option clashing with generated opposite option.")
                private Boolean noMyOption;

                public void setMyOption(Boolean myOption) {
                    this.myOption = myOption;
                }

                public void setNoMyOption(Boolean noMyOption) {
                    this.noMyOption = noMyOption;
                }

                public SampleTask() {}

                @TaskAction
                public void renderOptionValue() {
                    System.out.println("Value of my-option: " + myOption);
                    System.out.println("Value of no-my-option: " + noMyOption);
                }
            }
        """
    }
}
