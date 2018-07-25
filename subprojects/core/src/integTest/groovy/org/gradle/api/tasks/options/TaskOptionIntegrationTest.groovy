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
import spock.lang.Unroll

class TaskOptionIntegrationTest extends AbstractIntegrationSpec {

    @Unroll
    def "can evaluate option value of type #optionType when #description on command line"() {
        given:
        file('buildSrc/src/main/java/SampleTask.java') << taskWithSingleOption(optionType)
        buildFile << sampleTask()

        when:
        run(['sample'] + options as String[])

        then:
        outputContains("Value of myProp: $optionValue")

        where:
        optionType       | options                              | optionValue         | description
        'String'         | ['--myProp=test']                    | 'test'              | 'provided'
        'String'         | ['--myProp=ab\'c=123:x\\yz45']       | 'ab\'c=123:x\\yz45' | 'provided with special characters'
        'String'         | []                                   | 'null '             | 'not provided'
        'Boolean'        | ['--myProp']                         | 'true'              | 'provided'
        'Boolean'        | []                                   | 'null '             | 'not provided'
        'boolean'        | ['--myProp']                         | 'true'              | 'provided'
        'boolean'        | []                                   | 'false'             | 'not provided'
        'TestEnum'       | ['--myProp=OPT_2']                   | 'OPT_2'             | 'provided with upper case'
        'TestEnum'       | ['--myProp=opt_2']                   | 'OPT_2'             | 'provided with lower case'
        'TestEnum'       | []                                   | 'null'              | 'not provided'
        'List<String>'   | ['--myProp=a', '--myProp=b']         | '[a, b]'            | 'provided'
        'List<String>'   | []                                   | 'null '             | 'not provided'
        'List<String>'   | ['--myProp=a,b']                     | '[a,b]'             | 'provided with incorrect syntax'
        'List<TestEnum>' | ['--myProp=OPT_2', '--myProp=OPT_3'] | '[OPT_2, OPT_3]'    | 'provided with upper case'
        'List<TestEnum>' | []                                   | 'null '             | 'not provided'
        'List<TestEnum>' | ['--myProp=OPT_2,OPT_3']             | '[OPT_2,OPT_3]'     | 'provided with incorrect syntax'
    }

    def "can render option with help task"() {
        given:
        file('buildSrc/src/main/java/SampleTask.java') << taskWithSingleOption('String')
        buildFile << sampleTask()

        when:
        succeeds('help', '--task', 'sample')

        then:
        outputContains("""
Options
     --myProp     Configures command line option 'myProp'.""")
    }

    def "can render ordered option with help task"() {
        given:
        file('buildSrc/src/main/java/SampleTask.java') << taskWithMultipleOptions()
        buildFile << sampleTask()

        when:
        succeeds('help', '--task', 'sample')

        then:
        outputContains("""
Options
     --prop1     Configures command line option 'prop1'.

     --prop2     Configures command line option 'prop2'.

     --prop3     Configures command line option 'prop3'.""")
    }

    static String sampleTask() {
        """
            task sample(type: SampleTask)
        """
    }

    static String taskWithSingleOption(String optionType) {
        """
            import org.gradle.api.DefaultTask;
            import org.gradle.api.tasks.TaskAction;
            import org.gradle.api.tasks.options.Option;

            import java.util.List;
            
            public class SampleTask extends DefaultTask {
                private $optionType myProp;
                
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

    static String taskWithMultipleOptions() {
        """
            import org.gradle.api.DefaultTask;
            import org.gradle.api.tasks.TaskAction;
            import org.gradle.api.tasks.options.Option;

            public class SampleTask extends DefaultTask {
                private String prop1;
                private Boolean prop2;
                private String prop3;
                
                @Option(option = "prop1", description = "Configures command line option 'prop1'.")
                public void setProp1(String prop1) {
                    this.prop1 = prop1;
                }
                
                @Option(option = "prop2", description = "Configures command line option 'prop2'.")
                public void setProp2(Boolean prop2) {
                    this.prop2 = prop2;
                }
                
                @Option(option = "prop3", description = "Configures command line option 'prop3'.")
                public void setProp3(String prop3) {
                    this.prop3 = prop3;
                }
                
                @TaskAction
                public void renderOptionValue() {
                    System.out.println("Value of prop1: " + prop1);
                    System.out.println("Value of prop2: " + prop2);
                    System.out.println("Value of prop3: " + prop3);
                }
            }
        """
    }
}
