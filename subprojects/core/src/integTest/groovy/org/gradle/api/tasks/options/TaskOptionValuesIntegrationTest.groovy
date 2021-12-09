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
        file('buildSrc/src/main/java/SampleTask.java') << taskWithSingleOptionAndPredefinedValues()
        buildFile << sampleTask()

        when:
        run('sample', "--myProp=$optionValue")

        then:
        outputContains("Value of myProp: $optionValue")

        where:
        optionValue << ['test', 'hello', 'world']
    }

    def "can accept invalid option value"() {
        given:
        file('buildSrc/src/main/java/SampleTask.java') << taskWithSingleOptionAndPredefinedValues()
        buildFile << sampleTask()

        when:
        run('sample', '--myProp=unknown')

        then:
        outputContains("Value of myProp: unknown")
    }

    def "can render option values with help task"() {
        given:
        file('buildSrc/src/main/java/SampleTask.java') << taskWithSingleOptionAndPredefinedValues()
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
    }

    static String sampleTask() {
        """
            task sample(type: SampleTask)
        """
    }

    static String taskWithSingleOptionAndPredefinedValues() {
        """
            import org.gradle.api.DefaultTask;
            import org.gradle.api.tasks.TaskAction;
            import org.gradle.api.tasks.options.Option;
            import org.gradle.api.tasks.options.OptionValues;

            import java.util.List;
            import java.util.ArrayList;
            
            public class SampleTask extends DefaultTask {
                private final static List<String> MY_PROP_VALUES = new ArrayList<String>();
                private String myProp;
                
                public SampleTask() {
                    MY_PROP_VALUES.add("test");
                    MY_PROP_VALUES.add("hello");
                    MY_PROP_VALUES.add("world");
                }
                
                @Option(option = "myProp", description = "Configures command line option 'myProp'")
                public void setMyProp(String myProp) {
                    this.myProp = myProp;
                }
                
                @OptionValues("myProp")
                public List<String> getAvailableMyPropValues() {
                    return MY_PROP_VALUES;
                }
                
                @TaskAction
                public void renderOptionValue() {
                    System.out.println("Value of myProp: " + myProp);
                }
            }
        """
    }
}
