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

class TaskOptionValuesIntegrationTest extends AbstractIntegrationSpec {

    def "can accept valid option value '#optionValue'"() {
        given:
        file('buildSrc/src/main/java/SampleTask.java') << taskWithSingleOptionAndPredefinedValues(optionValuesType)
        buildFile << sampleTask()

        when:
        run('sample', "--myProp=$optionValue")

        then:
        outputContains("Value of myProp: $optionValue")

        where:
        optionValuesType              | optionValue
        TestOptionValuesType.LIST     | 'test'
        TestOptionValuesType.LIST     | 'hello'
        TestOptionValuesType.LIST     | 'world'
        TestOptionValuesType.PROVIDER | 'test'
        TestOptionValuesType.PROVIDER | 'hello'
        TestOptionValuesType.PROVIDER | 'world'
    }

    def "can accept invalid option value"() {
        given:
        file('buildSrc/src/main/java/SampleTask.java') << taskWithSingleOptionAndPredefinedValues((TestOptionValuesType) optionValuesType)
        buildFile << sampleTask()

        when:
        run('sample', '--myProp=unknown')

        then:
        outputContains("Value of myProp: unknown")

        where:
        optionValuesType << TestOptionValuesType.values()
    }

    def "can render option values with help task"() {
        given:
        file('buildSrc/src/main/java/SampleTask.java') << taskWithSingleOptionAndPredefinedValues((TestOptionValuesType) optionValuesType)
        buildFile << sampleTask()

        when:
        succeeds('help', '--task', 'sample')

        then:
        outputContains("""
Options
     --myProp     Configures command line option 'myProp'
                  Available values are:
                       hello
                       test
                       world""")

        where:
        optionValuesType << TestOptionValuesType.values()
    }

    def "fails with descriptive message for invalid @OptionValues Provider return type"() {
        given:
        buildFile << """
            abstract class SampleTask extends DefaultTask {
                @Internal
                @Option(option = "prop", description = "Configures command line option 'prop'")
                abstract Property<String> getProp()
                @OptionValues(value = "prop")
                $optionValuesReturnType getPropValues() {
                    return null;
                }
            }

            tasks.register("sample", SampleTask)
        """

        when:
        fails('sample', '--prop=unknown')

        then:
        failureDescriptionContains("@OptionValues annotation not supported on method 'getPropValues' in class 'SampleTask'. Supported method must be non-static, return a Collection<String> or Provider<Collection<String>> and take no parameters.")

        where:
        optionValuesReturnType   | _
        "Property<List<String>>" | _
        "Provider<String>"       | _
    }

    static String sampleTask() {
        """
            task sample(type: SampleTask)
        """
    }

    static String taskWithSingleOptionAndPredefinedValues(TestOptionValuesType optionValuesType) {
        String returnType
        String value
        if (optionValuesType == TestOptionValuesType.LIST) {
            returnType = "List<String>"
            value = "myPropValues.get()"
        } else {
            returnType = "Provider<List<String>>"
            value = "myPropValues"
        }
        """
            import org.gradle.api.DefaultTask;
            import org.gradle.api.tasks.TaskAction;
            import org.gradle.api.tasks.options.Option;
            import org.gradle.api.tasks.options.OptionValues;
            import org.gradle.api.provider.Provider;
            import org.gradle.api.provider.ProviderFactory;

            import java.util.List;
            import java.util.Arrays;
            import java.util.ArrayList;
            import javax.inject.Inject;

            public class SampleTask extends DefaultTask {
                private final Provider<List<String>> myPropValues;
                private String myProp;

                @Inject
                public SampleTask(ProviderFactory providers) {
                    this.myPropValues = providers.provider(() -> Arrays.asList("test", "hello", "world"));
                }

                @Option(option = "myProp", description = "Configures command line option 'myProp'")
                public void setMyProp(String myProp) {
                    this.myProp = myProp;
                }

                @OptionValues("myProp")
                public $returnType getAvailableMyPropValues() {
                    return $value;
                }

                @TaskAction
                public void renderOptionValue() {
                    System.out.println("Value of myProp: " + myProp);
                }
            }
        """
    }

    private enum TestOptionValuesType {
        LIST,
        PROVIDER
    }
}
