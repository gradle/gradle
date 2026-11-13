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


import org.gradle.integtests.fixtures.modes.UnsupportedWithConfigurationCache
import org.gradle.integtests.fixtures.polyglot.PolyglotDslTest
import org.gradle.integtests.fixtures.polyglot.SkipDsl
import org.gradle.test.fixtures.dsl.GradleDsl
import spock.lang.Issue

@PolyglotDslTest
@SkipDsl(dsl = GradleDsl.DECLARATIVE, because = "Declarative DSL cannot declare task types")
class TaskOptionIntegrationTest extends AbstractOptionIntegrationSpec {

    def "can evaluate option value of type #optionType when #description for Java task on command line"() {
        given:
        file('buildSrc/src/main/java/SampleTask.java') << taskWithSingleOption(optionType)
        buildFile() << sampleTask()

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
        'Double'         | ['--myProp=123']                     | '123'               | 'provided'
        'Double'         | ['--myProp=12.3']                    | '12.3'              | 'provided'
        'Double'         | []                                   | 'null'              | 'not provided'
        'Integer'        | ['--myProp=123']                     | '123'               | 'provided'
        'Integer'        | []                                   | 'null'              | 'not provided'
        'Long'           | ['--myProp=123']                     | '123'               | 'provided'
        'Long'           | []                                   | 'null'              | 'not provided'
        'TestEnum'       | ['--myProp=OPT_2']                   | 'OPT_2'             | 'provided with upper case'
        'TestEnum'       | ['--myProp=opt_2']                   | 'OPT_2'             | 'provided with lower case'
        'TestEnum'       | []                                   | 'null'              | 'not provided'
        'Object'         | ['--myProp=test']                    | 'test'              | 'provided'
        'Object'         | []                                   | 'null'              | 'not provided'
        'List<String>'   | ['--myProp=a', '--myProp=b']         | '[a, b]'            | 'provided'
        'List<String>'   | []                                   | 'null'              | 'not provided'
        'List<String>'   | ['--myProp=a,b']                     | '[a,b]'             | 'provided with incorrect syntax'
        'List<Double>'   | ['--myProp=12.3', '--myProp=45.6']   | '[12.3, 45.6]'      | 'provided'
        'List<Double>'   | []                                   | 'null'              | 'not provided'
        'List<Integer>'  | ['--myProp=123', '--myProp=456']     | '[123, 456]'        | 'provided'
        'List<Integer>'  | []                                   | 'null'              | 'not provided'
        'List<Long>'     | ['--myProp=123', '--myProp=456']     | '[123, 456]'        | 'provided'
        'List<Long>'     | []                                   | 'null'              | 'not provided'
        'List<TestEnum>' | ['--myProp=OPT_2', '--myProp=OPT_3'] | '[OPT_2, OPT_3]'    | 'provided with upper case'
        'List<TestEnum>' | ['--myProp=opt_2', '--myProp=opt_3'] | '[OPT_2, OPT_3]'    | 'provided with lower case'
        'List<TestEnum>' | []                                   | 'null'              | 'not provided'
    }

    def "can evaluate option value of type #optionType when #description for task in build script on command line"() {
        given:
        buildFile() << buildScriptTaskWithSingleOption(optionType)
        buildFile() << sampleTask()

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
        'Double'         | ['--myProp=123']                     | '123'               | 'provided'
        'Double'         | ['--myProp=12.3']                    | '12.3'              | 'provided'
        'Double'         | []                                   | 'null'              | 'not provided'
        'Integer'        | ['--myProp=123']                     | '123'               | 'provided'
        'Integer'        | []                                   | 'null'              | 'not provided'
        'Long'           | ['--myProp=123']                     | '123'               | 'provided'
        'Long'           | []                                   | 'null'              | 'not provided'
        'TestEnum'       | ['--myProp=OPT_2']                   | 'OPT_2'             | 'provided with upper case'
        'TestEnum'       | ['--myProp=opt_2']                   | 'OPT_2'             | 'provided with lower case'
        'TestEnum'       | []                                   | 'null'              | 'not provided'
        'Object'         | ['--myProp=test']                    | 'test'              | 'provided'
        'Object'         | []                                   | 'null'              | 'not provided'
        'List<String>'   | ['--myProp=a', '--myProp=b']         | '[a, b]'            | 'provided'
        'List<String>'   | []                                   | 'null'              | 'not provided'
        'List<String>'   | ['--myProp=a,b']                     | '[a,b]'             | 'provided with incorrect syntax'
        'List<Double>'   | ['--myProp=12.3', '--myProp=45.6']   | '[12.3, 45.6]'      | 'provided'
        'List<Double>'   | []                                   | 'null'              | 'not provided'
        'List<Integer>'  | ['--myProp=123', '--myProp=456']     | '[123, 456]'        | 'provided'
        'List<Integer>'  | []                                   | 'null'              | 'not provided'
        'List<Long>'     | ['--myProp=123', '--myProp=456']     | '[123, 456]'        | 'provided'
        'List<Long>'     | []                                   | 'null'              | 'not provided'
        'List<TestEnum>' | ['--myProp=OPT_2', '--myProp=OPT_3'] | '[OPT_2, OPT_3]'    | 'provided with upper case'
        'List<TestEnum>' | ['--myProp=opt_2', '--myProp=opt_3'] | '[OPT_2, OPT_3]'    | 'provided with lower case'
        'List<TestEnum>' | []                                   | 'null'              | 'not provided'
    }

    def "can set boolean option using no-args method when #description for Java task on command line"() {
        given:
        file('buildSrc/src/main/java/SampleTask.java') << taskWithFlagMethod()
        buildFile() << sampleTask()

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
        buildFile() << sampleTask()

        when:
        succeeds('help', '--task', 'sample')

        then:
        outputContains("""
Options
     --myProp     Configures command line option 'myProp'.""")
    }

    def "can render option with help for task in build script"() {
        given:
        buildFile() << buildScriptTaskWithSingleOption('String')
        buildFile() << sampleTask()

        when:
        succeeds('help', '--task', 'sample')

        then:
        outputContains("""
Options
     --myProp     Configures command line option 'myProp'.""")
    }

    def "can render option with help for Java task with property"() {
        given:
        file('buildSrc/src/main/java/SampleTask.java') << taskWithSinglePropertyOption('Property', 'String')
        buildFile() << sampleTask()

        when:
        succeeds('help', '--task', 'sample')

        then:
        outputContains("""
Options
     --myProp     Configures command line option 'myProp'.""")
    }

    def "can render option with help for task in build script with property"() {
        given:
        buildFile() << buildScriptTaskWithSinglePropertyOption('Property', 'String')
        buildFile() << sampleTask()

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
        buildFile() << sampleTask()

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

    @UnsupportedWithConfigurationCache(because = "Cross-task configuration at execution time")
    def "can override option with configure task"() {
        given:
        file('buildSrc/src/main/java/SampleTask.java') << taskWithSingleOption("String")
        buildFile() << sampleTask()
        buildFile() << (currentDsl() == GradleDsl.KOTLIN ? """
            val sample = tasks.named<SampleTask>("sample")
            val configureTask = tasks.register("configureTask") {
                doLast {
                    sample.get().setMyProp("fromConfigureTask")
                }
            }
            sample.configure { dependsOn(configureTask) }
        """ : """
            task configureTask {
                doLast {
                    sample.myProp = "fromConfigureTask"
                }
            }

            sample.dependsOn(configureTask)
        """)

        when:
        succeeds('sample', "--myProp=fromCommandLine")

        then:
        outputContains("Value of myProp: fromConfigureTask")
    }

    def "set value of property of type #propertyType of type #optionType when #description for Java task"() {
        given:
        file('buildSrc/src/main/java/SampleTask.java') << taskWithSinglePropertyOption(propertyType, optionType)
        buildFile() << sampleTask()

        when:
        run(['sample'] + options as String[])

        then:
        outputContains("Value of myProp: $optionValue")

        where:
        propertyType   | optionType | options                              | optionValue         | description
        'Property'     | 'String'   | ['--myProp=test']                    | 'test'              | 'provided'
        'Property'     | 'String'   | ['--myProp=ab\'c=123:x\\yz45']       | 'ab\'c=123:x\\yz45' | 'provided with special characters'
        'Property'     | 'String'   | []                                   | 'null '             | 'not provided'
        'Property'     | 'Boolean'  | ['--myProp']                         | 'true'              | 'provided'
        'Property'     | 'Boolean'  | []                                   | 'null '             | 'not provided'
        'Property'     | 'Double'   | ['--myProp=12.3']                    | '12.3'              | 'provided'
        'Property'     | 'Double'   | []                                   | 'null '             | 'not provided'
        'Property'     | 'Integer'  | ['--myProp=123']                     | '123'               | 'provided'
        'Property'     | 'Integer'  | []                                   | 'null '             | 'not provided'
        'Property'     | 'Long'     | ['--myProp=123']                     | '123'               | 'provided'
        'Property'     | 'Long'     | []                                   | 'null '             | 'not provided'
        'Property'     | 'TestEnum' | ['--myProp=OPT_2']                   | 'OPT_2'             | 'provided with upper case'
        'Property'     | 'TestEnum' | ['--myProp=opt_2']                   | 'OPT_2'             | 'provided with lower case'
        'Property'     | 'TestEnum' | []                                   | 'null'              | 'not provided'
        'ListProperty' | 'String'   | ['--myProp=a', '--myProp=b']         | '[a, b]'            | 'provided'
        'ListProperty' | 'String'   | []                                   | '[]'                | 'not provided'
        'ListProperty' | 'Double'   | ['--myProp=12.3', '--myProp=45.6']   | '[12.3, 45.6]'      | 'provided'
        'ListProperty' | 'Double'   | []                                   | '[]'                | 'not provided'
        'ListProperty' | 'Integer'  | ['--myProp=123', '--myProp=456']     | '[123, 456]'        | 'provided'
        'ListProperty' | 'Integer'  | []                                   | '[]'                | 'not provided'
        'ListProperty' | 'Long'     | ['--myProp=123', '--myProp=456']     | '[123, 456]'        | 'provided'
        'ListProperty' | 'Long'     | []                                   | '[]'                | 'not provided'
        'ListProperty' | 'TestEnum' | ['--myProp=OPT_1', '--myProp=OPT_2'] | '[OPT_1, OPT_2]'    | 'provided with upper case'
        'ListProperty' | 'TestEnum' | ['--myProp=opt_1', '--myProp=opt_2'] | '[OPT_1, OPT_2]'    | 'provided with lower case'
        'ListProperty' | 'TestEnum' | []                                   | '[]'                | 'not provided'
        'SetProperty'  | 'String'   | ['--myProp=a', '--myProp=b']         | '[a, b]'            | 'provided'
        'SetProperty'  | 'String'   | []                                   | '[]'                | 'not provided'
        'SetProperty'  | 'Double'   | ['--myProp=12.3', '--myProp=45.6']   | '[12.3, 45.6]'      | 'provided'
        'SetProperty'  | 'Double'   | []                                   | '[]'                | 'not provided'
        'SetProperty'  | 'Integer'  | ['--myProp=123', '--myProp=456']     | '[123, 456]'        | 'provided'
        'SetProperty'  | 'Integer'  | []                                   | '[]'                | 'not provided'
        'SetProperty'  | 'Long'     | ['--myProp=123', '--myProp=456']     | '[123, 456]'        | 'provided'
        'SetProperty'  | 'Long'     | []                                   | '[]'                | 'not provided'
        'SetProperty'  | 'TestEnum' | ['--myProp=OPT_1', '--myProp=OPT_2'] | '[OPT_1, OPT_2]'    | 'provided with upper case'
        'SetProperty'  | 'TestEnum' | ['--myProp=opt_1', '--myProp=opt_2'] | '[OPT_1, OPT_2]'    | 'provided with lower case'
        'SetProperty'  | 'TestEnum' | []                                   | '[]'                | 'not provided'
    }

    def "set value of property of type #propertyType of type #optionType when #description for task in build script"() {
        given:
        buildFile() << buildScriptTaskWithSinglePropertyOption(propertyType, optionType)
        buildFile() << sampleTask()

        when:
        run(['sample'] + options as String[])

        then:
        outputContains("Value of myProp: $optionValue")

        where:
        propertyType   | optionType  | options                              | optionValue         | description
        'Property'     | 'String'    | ['--myProp=test']                    | 'test'              | 'provided'
        'Property'     | 'String'    | ['--myProp=ab\'c=123:x\\yz45']       | 'ab\'c=123:x\\yz45' | 'provided with special characters'
        'Property'     | 'String'    | []                                   | 'null '             | 'not provided'
        'Property'     | 'Boolean'   | ['--myProp']                         | 'true'              | 'provided'
        'Property'     | 'Boolean'   | []                                   | 'null '             | 'not provided'
        'Property'     | 'Double'    | ['--myProp=12.3']                    | '12.3'              | 'provided'
        'Property'     | 'Double'    | []                                   | 'null '             | 'not provided'
        'Property'     | 'Integer'   | ['--myProp=123']                     | '123'               | 'provided'
        'Property'     | 'Integer'   | []                                   | 'null '             | 'not provided'
        'Property'     | 'Long'      | ['--myProp=123']                     | '123'               | 'provided'
        'Property'     | 'Long'      | []                                   | 'null '             | 'not provided'
        'Property'     | 'TestEnum'  | ['--myProp=OPT_2']                   | 'OPT_2'             | 'provided with upper case'
        'Property'     | 'TestEnum'  | ['--myProp=opt_2']                   | 'OPT_2'             | 'provided with lower case'
        'Property'     | 'TestEnum'  | []                                   | 'null'              | 'not provided'
        'ListProperty' | 'String'    | ['--myProp=a', '--myProp=b']         | '[a, b]'            | 'provided'
        'ListProperty' | 'String'    | []                                   | '[]'                | 'not provided'
        'ListProperty' | 'Double'    | ['--myProp=12.3', '--myProp=45.6']   | '[12.3, 45.6]'      | 'provided'
        'ListProperty' | 'Double'    | []                                   | '[]'                | 'not provided'
        'ListProperty' | 'Integer'   | ['--myProp=123', '--myProp=456']     | '[123, 456]'        | 'provided'
        'ListProperty' | 'Integer'   | []                                   | '[]'                | 'not provided'
        'ListProperty' | 'Long'      | ['--myProp=123', '--myProp=456']     | '[123, 456]'        | 'provided'
        'ListProperty' | 'Long'      | []                                   | '[]'                | 'not provided'
        'ListProperty' | 'TestEnum'  | ['--myProp=OPT_1', '--myProp=OPT_2'] | '[OPT_1, OPT_2]'    | 'provided with upper case'
        'ListProperty' | 'TestEnum'  | ['--myProp=opt_1', '--myProp=opt_2'] | '[OPT_1, OPT_2]'    | 'provided with lower case'
        'ListProperty' | 'TestEnum'  | []                                   | '[]'                | 'not provided'
        'SetProperty'  | 'String'    | ['--myProp=a', '--myProp=b']         | '[a, b]'            | 'provided'
        'SetProperty'  | 'String'    | []                                   | '[]'                | 'not provided'
        'SetProperty'  | 'Double'    | ['--myProp=12.3', '--myProp=45.6']   | '[12.3, 45.6]'      | 'provided'
        'SetProperty'  | 'Double'    | []                                   | '[]'                | 'not provided'
        'SetProperty'  | 'Integer'   | ['--myProp=123', '--myProp=456']     | '[123, 456]'        | 'provided'
        'SetProperty'  | 'Integer'   | []                                   | '[]'                | 'not provided'
        'SetProperty'  | 'Long'      | ['--myProp=123', '--myProp=456']     | '[123, 456]'        | 'provided'
        'SetProperty'  | 'Long'      | []                                   | '[]'                | 'not provided'
        'SetProperty'  | 'TestEnum'  | ['--myProp=OPT_1', '--myProp=OPT_2'] | '[OPT_1, OPT_2]'    | 'provided with upper case'
        'SetProperty'  | 'TestEnum'  | ['--myProp=opt_1', '--myProp=opt_2'] | '[OPT_1, OPT_2]'    | 'provided with lower case'
        'SetProperty'  | 'TestEnum'  | []                                   | '[]'                | 'not provided'
    }

    def "set value of property of type #propertyType when #description for Java task"() {
        given:
        file('buildSrc/src/main/java/SampleTask.java') << taskWithUnparameterizedPropertyOption(propertyType, methodName)
        buildFile() << sampleTask()

        when:
        run(['sample'] + options as String[])

        then:
        def value = optionValue == 'null' ? optionValue : "${testDirectory.file(optionValue)}"
        outputContains("Value of myProp: $value")

        where:
        propertyType          | options               | optionValue | methodName          | description
        'RegularFileProperty' | ['--myProp=test.txt'] | 'test.txt'  | 'fileProperty'      | 'provided'
        'RegularFileProperty' | []                    | 'null'      | 'fileProperty'      | 'not provided'
        'DirectoryProperty'   | ['--myProp=testDir']  | 'testDir'   | 'directoryProperty' | 'provided'
        'DirectoryProperty'   | []                    | 'null'      | 'directoryProperty' | 'not provided'
    }

    def "set value of property of type #propertyType when #description for task in build script"() {
        given:
        buildFile() << buildScriptTaskWithUnparameterizedPropertyOption(propertyType, methodName)
        buildFile() << sampleTask()

        when:
        run(['sample'] + options as String[])

        then:
        def value = optionValue == 'null' ? optionValue : "${testDirectory.file(optionValue)}"
        outputContains("Value of myProp: $value")

        where:
        propertyType          | options               | optionValue | methodName          | description
        'RegularFileProperty' | ['--myProp=test.txt'] | 'test.txt'  | 'fileProperty'      | 'provided'
        'RegularFileProperty' | []                    | 'null'      | 'fileProperty'      | 'not provided'
        'DirectoryProperty'   | ['--myProp=testDir']  | 'testDir'   | 'directoryProperty' | 'provided'
        'DirectoryProperty'   | []                    | 'null'      | 'directoryProperty' | 'not provided'
    }

    String sampleTask() {
        currentDsl() == GradleDsl.KOTLIN ? """
            tasks.register<SampleTask>("sample")
        """ : """
            task sample(type: SampleTask)
        """
    }

    @Issue("https://github.com/gradle/gradle/issues/18496")
    def "considers options from interfaces"() {
        given:
        buildFile() << optionInterface('MyInterface', 'serial', 'Target the device with given serial')
        buildFile() << taskImplementing('MyInterface')

        when:
        succeeds('myTask', '--serial=1234')

        then:
        result.assertTaskScheduled(':myTask').assertOutputContains('Serial: 1234')
    }

    @Issue("https://github.com/gradle/gradle/issues/18496")
    def "options from interfaces with same method defined twice should use last defined value"() {
        given:
        buildFile() << optionInterface('MyInterface', 'serial', 'Target the device with given serial')
        buildFile() << optionInterface('MyInterface1', 'serialNumber', 'Target the device with given serial')
        buildFile() << taskImplementing('MyInterface, MyInterface1')

        when:
        succeeds('myTask', '--serial=1234', '--serialNumber=4321')

        then:
        result.assertTaskScheduled(':myTask').assertOutputContains('Serial: 4321')
    }

    def "options from interfaces with same method defined in class should use overridden value"() {
        given:
        buildFile() << optionInterface('MyInterface', 'serial', 'Target the device with given serial')
        buildFile() << taskImplementing('MyInterface', 'serialNumber', 'Target the device with given serial')

        when:
        succeeds('myTask', '--serial=1234', '--serialNumber=4321')

        then:
        result.assertTaskScheduled(':myTask').assertOutputContains('Serial: 4321')

        when:
        succeeds('myTask', '--serialNumber=4321', '--serial=1234')

        then:
        result.assertTaskScheduled(':myTask').assertOutputContains('Serial: 4321')
    }

    def "options from interfaces with same method defined twice with same name should work"() {
        given:
        buildFile() << optionInterface('MyInterface', 'serial', 'Target the device with given serial (this is the first)')
        buildFile() << optionInterface('MyInterface1', 'serial', 'Target the device with given serial (this is the second)')
        buildFile() << taskImplementing('MyInterface, MyInterface1')

        when:
        succeeds('myTask', '--serial=1234')

        then:
        result.assertTaskScheduled(':myTask').assertOutputContains('Serial: 1234')

        when:
        succeeds('help', '--task', 'myTask')

        then:
        outputContains("this is the first")
        outputDoesNotContain("this is the second")
    }

    @Issue("https://github.com/gradle/gradle/issues/19868")
    def "options from interfaces with same method defined in class with same name should work"() {
        given:
        buildFile() << optionInterface('MyInterface', 'serial', 'Target the device with given serial')
        buildFile() << taskImplementing('MyInterface', 'serial', 'Target the device with given serial')

        when:
        succeeds('myTask', '--serial=1234')

        then:
        result.assertTaskScheduled(':myTask').assertOutputContains('Serial: 1234')
    }

    private String optionInterface(String name, String option, String description) {
        currentDsl() == GradleDsl.KOTLIN ? """
            interface $name {
                @get:Option("$option", "$description")
                @get:Optional
                @get:Input
                val serial: Property<String>
            }
        """ : """
            interface $name {
                @Option(option = '$option', description = '$description')
                @Optional
                @Input
                Property<String> getSerial()
            }
        """
    }

    private String taskImplementing(String interfaces, String overridingOption = null, String overridingDescription = null) {
        if (currentDsl() == GradleDsl.KOTLIN) {
            String override = overridingOption == null ? '' : """
                @get:Option("$overridingOption", "$overridingDescription")
                @get:Optional
                @get:Input
                abstract override val serial: Property<String>
            """
            return """
                abstract class MyTask : DefaultTask(), $interfaces {
                    $override
                    @TaskAction
                    fun action() {
                        println("Serial: " + serial.getOrElse("-"))
                    }
                }

                tasks.register<MyTask>("myTask")
            """
        }
        String override = overridingOption == null ? '' : """
            @Option(option = '$overridingOption', description = '$overridingDescription')
            @Optional
            @Input
            abstract Property<String> getSerial()
        """
        """
            abstract class MyTask extends DefaultTask implements $interfaces {
                $override
                @TaskAction
                void action() {
                    println "Serial: \${serial.getOrElse('-')}"
                }
            }

            tasks.register("myTask", MyTask.class)
        """
    }
}
