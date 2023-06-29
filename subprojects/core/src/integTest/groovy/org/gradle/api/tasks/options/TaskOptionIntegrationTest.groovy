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

import spock.lang.Issue

class TaskOptionIntegrationTest extends AbstractOptionIntegrationSpec {

    def "can evaluate option value of type #optionType when #description for Java task on command line"() {
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
        'String'         | []                                   | 'null'              | 'not provided'
        'Boolean'        | ['--myProp']                         | 'true'              | 'provided'
        'Boolean'        | []                                   | 'null'              | 'not provided'
        'boolean'        | ['--myProp']                         | 'true'              | 'provided'
        'boolean'        | []                                   | 'false'             | 'not provided'
        'TestEnum'       | ['--myProp=OPT_2']                   | 'OPT_2'             | 'provided with upper case'
        'TestEnum'       | ['--myProp=opt_2']                   | 'OPT_2'             | 'provided with lower case'
        'TestEnum'       | []                                   | 'null'              | 'not provided'
        'Object'         | ['--myProp=test']                    | 'test'              | 'provided'
        'Object'         | []                                   | 'null'              | 'not provided'
        'List<String>'   | ['--myProp=a', '--myProp=b']         | '[a, b]'            | 'provided'
        'List<String>'   | []                                   | 'null'              | 'not provided'
        'List<String>'   | ['--myProp=a,b']                     | '[a,b]'             | 'provided with incorrect syntax'
        'List<TestEnum>' | ['--myProp=OPT_2', '--myProp=OPT_3'] | '[OPT_2, OPT_3]'    | 'provided with upper case'
        'List<TestEnum>' | ['--myProp=opt_2', '--myProp=opt_3'] | '[OPT_2, OPT_3]'    | 'provided with lower case'
        'List<TestEnum>' | []                                   | 'null'              | 'not provided'
    }

    def "can evaluate option value of type #optionType when #description for Groovy task on command line"() {
        given:
        buildFile << groovyTaskWithSingleOption(optionType)
        buildFile << sampleTask()

        when:
        run(['sample'] + options as String[])

        then:
        outputContains("Value of myProp: $optionValue")

        where:
        optionType       | options                              | optionValue         | description
        'String'         | ['--myProp=test']                    | 'test'              | 'provided'
        'String'         | ['--myProp=ab\'c=123:x\\yz45']       | 'ab\'c=123:x\\yz45' | 'provided with special characters'
        'String'         | []                                   | 'null'              | 'not provided'
        'Boolean'        | ['--myProp']                         | 'true'              | 'provided'
        'Boolean'        | []                                   | 'null'              | 'not provided'
        'boolean'        | ['--myProp']                         | 'true'              | 'provided'
        'boolean'        | []                                   | 'false'             | 'not provided'
        'TestEnum'       | ['--myProp=OPT_2']                   | 'OPT_2'             | 'provided with upper case'
        'TestEnum'       | ['--myProp=opt_2']                   | 'OPT_2'             | 'provided with lower case'
        'TestEnum'       | []                                   | 'null'              | 'not provided'
        'Object'         | ['--myProp=test']                    | 'test'              | 'provided'
        'Object'         | []                                   | 'null'              | 'not provided'
        'List<String>'   | ['--myProp=a', '--myProp=b']         | '[a, b]'            | 'provided'
        'List<String>'   | []                                   | 'null'              | 'not provided'
        'List<String>'   | ['--myProp=a,b']                     | '[a,b]'             | 'provided with incorrect syntax'
        'List<TestEnum>' | ['--myProp=OPT_2', '--myProp=OPT_3'] | '[OPT_2, OPT_3]'    | 'provided with upper case'
        'List<TestEnum>' | ['--myProp=opt_2', '--myProp=opt_3'] | '[OPT_2, OPT_3]'    | 'provided with lower case'
        'List<TestEnum>' | []                                   | 'null'              | 'not provided'
    }

    def "can set boolean option using no-args method when #description for Java task on command line"() {
        given:
        file('buildSrc/src/main/java/SampleTask.java') << taskWithFlagMethod()
        buildFile << sampleTask()

        when:
        run(['sample'] + options as String[])

        then:
        outputContains("Value of myProp: $optionValue")

        where:
        options      | optionValue | description
        ['--myProp'] | 'true'      | 'provided'
        []           | 'false'     | 'not provided'
    }

    def "can render option with help for Java task"() {
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

    def "can render option with help for Groovy task"() {
        given:
        buildFile << groovyTaskWithSingleOption('String')
        buildFile << sampleTask()

        when:
        succeeds('help', '--task', 'sample')

        then:
        outputContains("""
Options
     --myProp     Configures command line option 'myProp'.""")
    }

    def "can render option with help for Java task with property"() {
        given:
        file('buildSrc/src/main/java/SampleTask.java') << taskWithSinglePropertyOption('String')
        buildFile << sampleTask()

        when:
        succeeds('help', '--task', 'sample')

        then:
        outputContains("""
Options
     --myProp     Configures command line option 'myProp'.""")
    }

    def "can render option with help for Groovy task with property"() {
        given:
        buildFile << groovyTaskWithSinglePropertyOption('String')
        buildFile << sampleTask()

        when:
        succeeds('help', '--task', 'sample')

        then:
        outputContains("""
Options
     --myProp     Configures command line option 'myProp'.""")
    }

    def "can render ordered option with help for Java task"() {
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

     --no-prop2     Disables option --prop2.

     --prop3     Configures command line option 'prop3'.""")
    }

    def "can override option with configure task"() {
        given:
        file('buildSrc/src/main/java/SampleTask.java') << taskWithSingleOption("String")
        buildFile << sampleTask()
        buildFile << """
            task configureTask {
                doLast {
                    sample.myProp = "fromConfigureTask"
                }
            }

            sample.dependsOn(configureTask)
        """

        when:
        succeeds('sample', "--myProp=fromCommandLine")

        then:
        outputContains("Value of myProp: fromConfigureTask")
    }

    def "set value of property of type Property of type #optionType when #description for Java task"() {
        given:
        file('buildSrc/src/main/java/SampleTask.java') << taskWithSinglePropertyOption(optionType)
        buildFile << sampleTask()

        when:
        run(['sample'] + options as String[])

        then:
        outputContains("Value of myProp: $optionValue")

        where:
        optionType | options                        | optionValue         | description
        'String'   | ['--myProp=test']              | 'test'              | 'provided'
        'String'   | ['--myProp=ab\'c=123:x\\yz45'] | 'ab\'c=123:x\\yz45' | 'provided with special characters'
        'String'   | []                             | 'null '             | 'not provided'
        'Boolean'  | ['--myProp']                   | 'true'              | 'provided'
        'Boolean'  | []                             | 'null '             | 'not provided'
        'TestEnum' | ['--myProp=OPT_2']             | 'OPT_2'             | 'provided with upper case'
        'TestEnum' | ['--myProp=opt_2']             | 'OPT_2'             | 'provided with lower case'
        'TestEnum' | []                             | 'null'              | 'not provided'
    }

    def "set value of property of type Property of type #optionType when #description for Groovy task"() {
        given:
        buildFile << groovyTaskWithSinglePropertyOption(optionType)
        buildFile << sampleTask()

        when:
        run(['sample'] + options as String[])

        then:
        outputContains("Value of myProp: $optionValue")

        where:
        optionType | options                        | optionValue         | description
        'String'   | ['--myProp=test']              | 'test'              | 'provided'
        'String'   | ['--myProp=ab\'c=123:x\\yz45'] | 'ab\'c=123:x\\yz45' | 'provided with special characters'
        'String'   | []                             | 'null '             | 'not provided'
        'Boolean'  | ['--myProp']                   | 'true'              | 'provided'
        'Boolean'  | []                             | 'null '             | 'not provided'
        'TestEnum' | ['--myProp=OPT_2']             | 'OPT_2'             | 'provided with upper case'
        'TestEnum' | ['--myProp=opt_2']             | 'OPT_2'             | 'provided with lower case'
        'TestEnum' | []                             | 'null'              | 'not provided'
    }

    def "set value of property of type ListProperty of type #optionType when #description for Java task"() {
        given:
        file('buildSrc/src/main/java/SampleTask.java') << taskWithListPropertyOption(optionType)
        buildFile << sampleTask()

        when:
        run(['sample'] + options as String[])

        then:
        outputContains("Value of myProp: $optionValue")

        where:
        optionType | options                              | optionValue        | description
        'String'   | ['--myProp=a', '--myProp=b']         | '[a, b]'           | 'provided'
        'String'   | []                                   | '[]'               | 'not provided'
        'TestEnum' | ['--myProp=OPT_1', '--myProp=OPT_2'] | '[OPT_1, OPT_2]'   | 'provided with upper case'
        'TestEnum' | ['--myProp=opt_1', '--myProp=opt_2'] | '[OPT_1, OPT_2]'   | 'provided with lower case'
        'TestEnum' | []                                   | '[]'               | 'not provided'
    }

    def "set value of property of type ListProperty of type #optionType when #description for Groovy task"() {
        given:
        buildFile << groovyTaskWithListPropertyOption(optionType)
        buildFile << sampleTask()

        when:
        run(['sample'] + options as String[])

        then:
        outputContains("Value of myProp: $optionValue")

        where:
        optionType | options                              | optionValue      | description
        'String'   | ['--myProp=a', '--myProp=b']         | '[a, b]'         | 'provided'
        'String'   | []                                   | '[]'             | 'not provided'
        'TestEnum' | ['--myProp=OPT_1', '--myProp=OPT_2'] | '[OPT_1, OPT_2]' | 'provided with upper case'
        'TestEnum' | ['--myProp=opt_1', '--myProp=opt_2'] | '[OPT_1, OPT_2]' | 'provided with lower case'
        'TestEnum' | []                                   | '[]'             | 'not provided'
    }

    def "set value of property of type SetProperty of type #optionType when #description for Java task"() {
        given:
        file('buildSrc/src/main/java/SampleTask.java') << taskWithSetPropertyOption(optionType)
        buildFile << sampleTask()

        when:
        run(['sample'] + options as String[])

        then:
        outputContains("Value of myProp: $optionValue")

        where:
        optionType | options                              | optionValue        | description
        'String'   | ['--myProp=a', '--myProp=b']         | '[a, b]'           | 'provided'
        'String'   | []                                   | '[]'               | 'not provided'
        'TestEnum' | ['--myProp=OPT_1', '--myProp=OPT_2'] | '[OPT_1, OPT_2]'   | 'provided with upper case'
        'TestEnum' | ['--myProp=opt_1', '--myProp=opt_2'] | '[OPT_1, OPT_2]'   | 'provided with lower case'
        'TestEnum' | []                                   | '[]'               | 'not provided'
    }

    def "set value of property of type SetProperty of type #optionType when #description for Groovy task"() {
        given:
        buildFile << groovyTaskWithSetPropertyOption(optionType)
        buildFile << sampleTask()

        when:
        run(['sample'] + options as String[])

        then:
        outputContains("Value of myProp: $optionValue")

        where:
        optionType | options                              | optionValue      | description
        'String'   | ['--myProp=a', '--myProp=b']         | '[a, b]'         | 'provided'
        'String'   | []                                   | '[]'             | 'not provided'
        'TestEnum' | ['--myProp=OPT_1', '--myProp=OPT_2'] | '[OPT_1, OPT_2]' | 'provided with upper case'
        'TestEnum' | ['--myProp=opt_1', '--myProp=opt_2'] | '[OPT_1, OPT_2]' | 'provided with lower case'
        'TestEnum' | []                                   | '[]'             | 'not provided'
    }

    static String sampleTask() {
        """
            task sample(type: SampleTask)
        """
    }

    @Issue("https://github.com/gradle/gradle/issues/18496")
    def "considers options from interfaces"() {
        given:
        buildFile << '''
            interface MyInterface {
              @Option(
                option = 'serial',
                description = 'Target the device with given serial'
              )
              @Optional
              @Input
              Property<String> getSerial()
            }

            abstract class MyTask extends DefaultTask implements MyInterface{
              @TaskAction
              void action() {
                println "Serial: ${serial.getOrElse('-')}"
              }
            }

            tasks.register("myTask", MyTask.class)
        '''

        when:
        succeeds('myTask', '--serial=1234')

        then:
        result.assertTaskExecuted(':myTask').assertOutputContains('Serial: 1234')
    }

    @Issue("https://github.com/gradle/gradle/issues/18496")
    def "options from interfaces with same method defined twice should use last defined value"() {
        given:
        buildFile << '''
            interface MyInterface {
              @Option(
                option = 'serial',
                description = 'Target the device with given serial'
              )
              @Optional
              @Input
              Property<String> getSerial()
            }

            interface MyInterface1 {
              @Option(
                option = 'serialNumber',
                description = 'Target the device with given serial'
              )
              @Optional
              @Input
              Property<String> getSerial()
            }

            abstract class MyTask extends DefaultTask implements MyInterface, MyInterface1{
              @TaskAction
              void action() {
                println "Serial: ${serial.getOrElse('-')}"
              }
            }

            tasks.register("myTask", MyTask.class)
        '''

        when:
        succeeds('myTask', '--serial=1234', '--serialNumber=4321')

        then:
        result.assertTaskExecuted(':myTask').assertOutputContains('Serial: 4321')
    }

    def "options from interfaces with same method defined in class should use overridden value"() {
        given:
        buildFile << '''
            interface MyInterface {
              @Option(
                option = 'serial',
                description = 'Target the device with given serial'
              )
              @Optional
              @Input
              Property<String> getSerial()
            }

            abstract class MyTask extends DefaultTask implements MyInterface {
              @Option(
                option = 'serialNumber',
                description = 'Target the device with given serial'
              )
              @Optional
              @Input
              abstract Property<String> getSerial()

              @TaskAction
              void action() {
                println "Serial: ${serial.getOrElse('-')}"
              }
            }

            tasks.register("myTask", MyTask.class)
        '''

        when:
        succeeds('myTask', '--serial=1234', '--serialNumber=4321')

        then:
        result.assertTaskExecuted(':myTask').assertOutputContains('Serial: 4321')

        when:
        succeeds('myTask', '--serialNumber=4321', '--serial=1234')

        then:
        result.assertTaskExecuted(':myTask').assertOutputContains('Serial: 4321')
    }

    def "options from interfaces with same method defined twice with same name should work"() {
        given:
        buildFile << '''
            interface MyInterface {
              @Option(
                option = 'serial',
                description = 'Target the device with given serial (this is the first)'
              )
              @Optional
              @Input
              Property<String> getSerial()
            }

            interface MyInterface1 {
              @Option(
                option = 'serial',
                description = 'Target the device with given serial (this is the second)'
              )
              @Optional
              @Input
              Property<String> getSerial()
            }

            abstract class MyTask extends DefaultTask implements MyInterface, MyInterface1{
              @TaskAction
              void action() {
                println "Serial: ${serial.getOrElse('-')}"
              }
            }

            tasks.register("myTask", MyTask.class)
        '''

        when:
        succeeds('myTask', '--serial=1234')

        then:
        result.assertTaskExecuted(':myTask').assertOutputContains('Serial: 1234')

        when:
        succeeds('help', '--task', 'myTask')

        then:
        outputContains("this is the first")
        outputDoesNotContain("this is the second")
    }

    @Issue("https://github.com/gradle/gradle/issues/19868")
    def "options from interfaces with same method defined in class with same name should work"() {
        given:
        buildFile << '''
            interface MyInterface {
              @Option(
                option = 'serial',
                description = 'Target the device with given serial'
              )
              @Optional
              @Input
              Property<String> getSerial()
            }

            abstract class MyTask extends DefaultTask implements MyInterface {
              @Option(
                option = 'serial',
                description = 'Target the device with given serial'
              )
              @Optional
              @Input
              abstract Property<String> getSerial()

              @TaskAction
              void action() {
                println "Serial: ${serial.getOrElse('-')}"
              }
            }

            tasks.register("myTask", MyTask.class)
        '''

        when:
        succeeds('myTask', '--serial=1234')

        then:
        result.assertTaskExecuted(':myTask').assertOutputContains('Serial: 1234')
    }
}
