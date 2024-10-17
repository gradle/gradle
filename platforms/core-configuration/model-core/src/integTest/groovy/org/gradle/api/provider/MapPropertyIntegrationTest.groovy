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

package org.gradle.api.provider

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import spock.lang.Issue

class MapPropertyIntegrationTest extends AbstractIntegrationSpec {
    def setup() {
        buildFile '''
            abstract class AbstractVerificationTask<K, V> extends DefaultTask {

                @Optional
                @Input
                final MapProperty<K, V> prop
                @Internal
                Map<K, V> expected = [:]
                @Internal
                final Class<K> keyType
                @Internal
                final Class<V> valueType

                AbstractVerificationTask(Class<K> keyType, Class<V> valueType) {
                    this.keyType = keyType
                    this.valueType = valueType
                    prop = project.objects.mapProperty(keyType, valueType)
                }

                @TaskAction
                void validate() {
                    def actual = prop.getOrNull()
                    println "Actual: $actual"
                    println "Expected: $expected"
                    assert expected == actual
                    actual.each {
                        assert keyType.isInstance(it.key)
                        assert valueType.isInstance(it.value)
                    }
                }
            }

            class StringVerificationTask extends AbstractVerificationTask<String, String> {
                StringVerificationTask() { super(String, String) }
            }

            class IntegerVerificationTask extends AbstractVerificationTask<String, String> {
                IntegerVerificationTask() { super(Integer, Integer) }
            }

            task verify(type: StringVerificationTask)
            task verifyInt(type: IntegerVerificationTask)
            '''
    }

    def "can define task with abstract MapProperty<#keyType, #valueType> getter"() {
        given:
        buildFile << """
            class Param<T> {
                T display
                String toString() { display.toString() }
            }

            abstract class MyTask extends DefaultTask {
                @Input
                abstract MapProperty<$keyType, $valueType> getProp()

                @TaskAction
                void go() {
                    println("prop = \${prop.get()}")
                }
            }

            def key = new Param<String>(display: 'a')
            def map = [:]
            map[key] = new Param<Number>(display: 12)

            tasks.create("thing", MyTask) {
                prop = $value
            }
        """

        when:
        succeeds("thing")

        then:
        outputContains("prop = $display")

        where:
        keyType         | valueType       | value           | display
        "String"        | "Number"        | "[a: 12, b: 4]" | "[a:12, b:4]"
        "Param<String>" | "Param<Number>" | "map"           | "[a:12]"
    }

    def "can finalize the value of a property using API"() {
        given:
        buildFile << '''
            int counter = 0
            def provider = providers.provider { [(++counter): ++counter] }

            def property = objects.mapProperty(Integer, Integer)
            property.set(provider)

            assert property.get() == [1: 2]
            assert property.get() == [3: 4]
            property.finalizeValue()
            assert property.get() == [5: 6]
            assert property.get() == [5: 6]

            property.set([1: 2])
            '''.stripIndent()

        when:
        fails()

        then:
        failure.assertHasCause('The value for this property is final and cannot be changed any further.')
    }

    def "can disallow changes to a property using API without finalizing the value"() {
        given:
        buildFile << '''
            int counter = 0
            def provider = providers.provider { [(++counter): ++counter] }

            def property = objects.mapProperty(Integer, Integer)
            property.set(provider)

            assert property.get() == [1: 2]
            assert property.get() == [3: 4]
            property.disallowChanges()
            assert property.get() == [5: 6]
            assert property.get() == [7: 8]

            property.set([1: 2])
            '''.stripIndent()

        when:
        fails()

        then:
        failure.assertHasCause('The value for this property cannot be changed any further.')
    }

    def "task @Input property is implicitly finalized when task starts execution"() {
        given:
        buildFile << '''
            class SomeTask extends DefaultTask {
                @Input
                final MapProperty<String, String> prop = project.objects.mapProperty(String, String)

                @OutputFile
                final Property<RegularFile> outputFile = project.objects.fileProperty()

                @TaskAction
                void go() {
                    outputFile.get().asFile.text = prop.get()
                }
            }

            task thing(type: SomeTask) {
                prop = ['key1': 'value1']
                outputFile = layout.buildDirectory.file('out.txt')
                doFirst {
                    prop.set(['ignoredKey': 'ignoredValue'])
                }
            }

            afterEvaluate {
                thing.prop = ['key2': 'value2']
            }

            '''.stripIndent()

        when:
        fails('thing')

        then:
        failure.assertHasDescription("Execution failed for task ':thing'.")
        failure.assertHasCause("The value for task ':thing' property 'prop' is final and cannot be changed any further.")
    }

    def "UPGRADED task @Input property is LENIENTLY implicitly finalized when task starts execution UNTIL NEXT MAJOR"() {
        given:
        buildFile << '''
            import org.gradle.internal.instrumentation.api.annotations.ReplacesEagerProperty

            abstract class SomeTask extends DefaultTask {
                @ReplacesEagerProperty
                @Input
                abstract MapProperty<String, String> getProp()

                @OutputFile
                final Property<RegularFile> outputFile = project.objects.fileProperty()

                @TaskAction
                void go() {
                    println("value: " + prop.get().sort())
                    outputFile.get().asFile.text = prop.get()
                }
            }

            task thing(type: SomeTask) {
                prop = ['key1': 'value1']
                outputFile = layout.buildDirectory.file('out.txt')
                doFirst {
                    prop.put('key3', 'value3')
                }
            }

            afterEvaluate {
                thing.prop.putAll(['key2': 'value2'])
            }

            '''.stripIndent()

        expect:
        executer.expectDeprecationWarningWithPattern("Changing property value of task ':thing' property 'prop' at execution time. This behavior has been deprecated.*")
        succeeds('thing')
        outputContains("value: [key1:value1, key2:value2, key3:value3]")
    }

    @Requires(value = IntegTestPreconditions.NotConfigCached, reason = "https://github.com/gradle/gradle/issues/25516")
    def "task ad hoc input property is implicitly finalized and changes ignored when task starts execution"() {
        given:
        buildFile << '''
            def prop = project.objects.mapProperty(String, String)

            task thing {
                inputs.property('prop', prop)
                prop.set(['key1': 'value1'])
                doLast {
                    prop.set(['ignoredKey': 'ignoredValue'])
                    println "prop = ${prop.get()}"
                }
            }
            '''.stripIndent()

        when:
        fails('thing')

        then:
        failure.assertHasDescription("Execution failed for task ':thing'.")
        failure.assertHasCause("The value for this property is final and cannot be changed any further.")
    }

    def "can use property with no value as optional ad hoc task input property"() {
        given:
        buildFile << """

def prop = project.objects.mapProperty(String, String)
prop.set((Map)null)

task thing {
    inputs.property("prop", prop).optional(true)
    doLast {
        println "prop = " + prop.getOrNull()
    }
}
"""

        when:
        run("thing")

        then:
        output.contains("prop = null")
    }

    def "can set value for map property from DSL"() {
        given:
        buildFile << """
            verify {
                prop = ${value}
                expected = ['key1': 'value1', 'key2': 'value2']
            }
            """.stripIndent()

        expect:
        succeeds('verify')

        where:
        value                                                         | _
        "['key1': 'value1', 'key2': 'value2']"                        | _
        "new LinkedHashMap(['key1': 'value1', 'key2': 'value2'])"     | _
        "providers.provider { ['key1': 'value1', 'key2': 'value2'] }" | _
    }

    def "can add entries to map property with string keys using index notation"() {
        given:
        buildFile << '''
            verify {
                prop.empty()
                prop['key1'] = 'value1'
                prop['key2'] = provider { 'value2' }
                expected = ['key1': 'value1', 'key2': 'value2']
            }
            '''.stripIndent()

        expect:
        succeeds('verify')
    }

    def "can add entries to map property with non-string keys using index notation"() {
        given:
        buildFile << '''
            verifyInt {
                prop.empty()
                prop[1] = 111
                prop[2] = project.provider { 222 }
                expected = [1: 111, 2: 222]
            }
            '''.stripIndent()

        expect:
        succeeds('verifyInt')
    }

    def "can set value for string map property using GString keys and values"() {
        given:
        buildFile << """
            def str = "aBc"
            verify {
                prop = ${value}
                expected = ['a': 'b']
            }
            """.stripIndent()

        expect:
        succeeds('verify')

        where:
        value                                                                                         | _
        '[ "${str.substring(0, 1)}": "${str.toLowerCase().substring(1, 2)}" ]'                        | _
        'providers.provider { [ "${str.substring(0, 1)}": "${str.toLowerCase().substring(1, 2)}" ] }' | _
    }

    def "can add entries to default value"() {
        given:
        buildFile << '''
            verify {
                prop = ['key1': 'value1']
                prop.put('key2', 'value2')
                prop.put('key3', project.provider { 'value3' })
                prop.putAll(['key4': 'value4'])
                prop.putAll(project.provider { ['key5': 'value5'] })
                expected = ['key1': 'value1', 'key2': 'value2', 'key3': 'value3', 'key4': 'value4', 'key5': 'value5']
            }
            '''.stripIndent()
    }

    def "can add entries to empty map property"() {
        given:
        buildFile << '''
            verify {
                prop.empty()
                prop.put('key1', 'value1')
                prop.put('key2', project.provider { 'value2' })
                prop.putAll(['key3': 'value3'])
                prop.putAll(project.provider { ['key4': 'value4'] })
                expected = ['key1': 'value1', 'key2': 'value2', 'key3': 'value3', 'key4': 'value4']
            }
            '''.stripIndent()

        expect:
        succeeds('verify')
    }

    def "can add entry to string map property using GString key and value"() {
        given:
        buildFile << """
            def str = "aBc"
            verify {
                prop = ['key': 'value']
                prop.putAll(${value})
                expected = ['key': 'value', 'b': 'c']
            }
            """.stripIndent()

        expect:
        succeeds('verify')

        where:
        value                                                                                         | _
        '["${str.toLowerCase().substring(1, 2)}": "${str.substring(2, 3)}"]'                          | _
        'providers.provider { [ "${str.toLowerCase().substring(1, 2)}": "${str.substring(2, 3)}" ] }' | _
    }

    def "can add entries to string map property using GString values"() {
        given:
        buildFile << """
            def str = "aBc"
            verify {
                prop = ['key': 'value']
                prop.put(${key}, ${value})
                expected = ['key': 'value', 'a': 'b']
            }
            """.stripIndent()

        expect:
        succeeds('verify')

        where:
        key                        | value
        '"${str.substring(0, 1)}"' | '"${str.toLowerCase().substring(1, 2)}"'
        '"${str.substring(0, 1)}"' | 'project.provider { "${str.toLowerCase().substring(1, 2)}" }'
    }

    def "reports failure to set property value using incompatible type"() {
        given:
        buildFile << '''
        interface MyExtension {
            MapProperty<String, String> getProp()
        }

        project.extensions.create('myExt', MyExtension)

        task wrongValueTypeDsl {
            def myExt = project.extensions.getByType(MyExtension)
            doLast {
                myExt.prop = 123
            }
        }

        task wrongRuntimeKeyType {
            def myExt = project.extensions.getByType(MyExtension)
            doLast {
                myExt.prop = [123: 'value']
                myExt.prop.get()
            }
        }

        task wrongRuntimeValueType {
            def myExt = project.extensions.getByType(MyExtension)
            doLast {
                myExt.prop = ['key': 123]
                myExt.prop.get()
            }
        }

        task wrongPropertyTypeDsl {
            def myExt = project.extensions.getByType(MyExtension)
            def objects = objects
            doLast {
                myExt.prop = objects.property(Integer)
            }
        }

        task wrongPropertyTypeApi {
            def myExt = project.extensions.getByType(MyExtension)
            def objects = objects
            doLast {
                myExt.prop.set(objects.property(Integer))
            }
        }

        task wrongRuntimeKeyTypeDsl {
            def myExt = project.extensions.getByType(MyExtension)
            def objects = objects
            doLast {
                myExt.prop = objects.mapProperty(Integer, String)
            }
        }

        task wrongRuntimeValueTypeDsl {
            def myExt = project.extensions.getByType(MyExtension)
            def objects = objects
            doLast {
                myExt.prop = objects.mapProperty(String, Integer)
            }
        }

        task wrongRuntimeKeyTypeApi {
            def myExt = project.extensions.getByType(MyExtension)
            def objects = objects
            doLast {
                myExt.prop.set(objects.mapProperty(Integer, String))
            }
        }

        task wrongRuntimeValueTypeApi {
            def myExt = project.extensions.getByType(MyExtension)
            def objects = objects
            doLast {
                myExt.prop.set(objects.mapProperty(String, Integer))
            }
        }
        '''.stripIndent()

        when:
        fails('wrongValueTypeDsl')
        then:
        failure.assertHasDescription("Execution failed for task ':wrongValueTypeDsl'.")
        failure.assertHasCause('Cannot set the value of a property of type java.util.Map using an instance of type java.lang.Integer.')

        when:
        fails('wrongRuntimeKeyType')
        then:
        failure.assertHasDescription("Execution failed for task ':wrongRuntimeKeyType'.")
        failure.assertHasCause('Cannot get the value of a property of type java.util.Map with key type java.lang.String as the source contains a key of type java.lang.Integer.')
        when:
        fails('wrongRuntimeValueType')
        then:
        failure.assertHasDescription("Execution failed for task ':wrongRuntimeValueType'.")
        failure.assertHasCause('Cannot get the value of a property of type java.util.Map with value type java.lang.String as the source contains a value of type java.lang.Integer.')

        when:
        fails('wrongPropertyTypeDsl')
        then:
        failure.assertHasDescription("Execution failed for task ':wrongPropertyTypeDsl'.")
        failure.assertHasCause('Cannot set the value of a property of type java.util.Map using a provider of type java.lang.Integer.')

        when:
        fails('wrongPropertyTypeApi')
        then:
        failure.assertHasDescription("Execution failed for task ':wrongPropertyTypeApi'.")
        failure.assertHasCause('Cannot set the value of a property of type java.util.Map using a provider of type java.lang.Integer.')

        when:
        fails('wrongRuntimeKeyTypeDsl')
        then:
        failure.assertHasDescription("Execution failed for task ':wrongRuntimeKeyTypeDsl'.")
        failure.assertHasCause('Cannot set the value of a property of type java.util.Map with key type java.lang.String and value type java.lang.String using a provider with key type java.lang.Integer and value type java.lang.String.')

        when:
        fails('wrongRuntimeValueTypeDsl')
        then:
        failure.assertHasDescription("Execution failed for task ':wrongRuntimeValueTypeDsl'.")
        failure.assertHasCause('Cannot set the value of a property of type java.util.Map with key type java.lang.String and value type java.lang.String using a provider with key type java.lang.String and value type java.lang.Integer.')

        when:
        fails('wrongRuntimeKeyTypeApi')
        then:
        failure.assertHasDescription("Execution failed for task ':wrongRuntimeKeyTypeApi'.")
        failure.assertHasCause('Cannot set the value of a property of type java.util.Map with key type java.lang.String and value type java.lang.String using a provider with key type java.lang.Integer and value type java.lang.String.')

        when:
        fails('wrongRuntimeValueTypeApi')
        then:
        failure.assertHasDescription("Execution failed for task ':wrongRuntimeValueTypeApi'.")
        failure.assertHasCause('Cannot set the value of a property of type java.util.Map with key type java.lang.String and value type java.lang.String using a provider with key type java.lang.String and value type java.lang.Integer.')
    }

    @Requires(
        value = IntegTestPreconditions.NotConfigCached,
        reason = "Test relies on modifying properties at execution time, but CC finalizes them before execution"
    )
    def "later entries replace earlier entries"() {
        given:
        buildFile << '''
            verify.prop = ['key': 'value']

            task replacingPut {
                doLast {
                    verify.prop.put('key', 'newValue')
                    verify.expected = ['key': 'newValue']
                }
            }

            task replacingPutWithProvider {
                doLast {
                    verify.prop.put('key', provider { 'newValue' })
                    verify.expected = ['key': 'newValue']
                }
            }

            task replacingPutAll {
                doLast {
                    verify.prop.putAll(['key': 'newValue', 'otherKey': 'otherValue'])
                    verify.expected = ['key': 'newValue', 'otherKey': 'otherValue']
                }
            }

            task replacingPutAllWithProvider {
                doLast {
                    verify.prop.putAll(provider { ['key': 'newValue', 'otherKey': 'otherValue'] })
                    verify.expected = ['key': 'newValue', 'otherKey': 'otherValue']
                }
            }
        '''.stripIndent()

        expect:
        succeeds('replacingPut', 'verify')
        and:
        succeeds('replacingPutWithProvider', 'verify')
        and:
        succeeds('replacingPutAll', 'verify')
        and:
        succeeds('replacingPutAllWithProvider', 'verify')
    }

    def "puts to non-defined property do nothing"() {
        given:
        buildFile << '''
            verify {
                prop = null
                prop.put('key1', 'value1')
                prop.put('key2', project.provider { 'value2' })
                prop.putAll(['key3': 'value3'])
                prop.putAll(project.provider { ['key4': 'value4'] })
                expected = null
            }
            '''.stripIndent()

        expect:
        succeeds('verify')
    }

    def "reasonable message when trying to add an entry with a null key to a map property"() {
        given:
        buildFile << '''
            verify {
                prop.put(null, 'value')
            }
            '''.stripIndent()

        expect:
        def failure = fails('verify')
        failure.assertHasCause('Cannot add an entry with a null key to a property of type Map.')
    }

    def "reasonable message when trying to add an entry with a null value to a map property"() {
        given:
        buildFile << '''
            verify {
                prop.put('key', null)
            }
            '''.stripIndent()

        expect:
        def failure = fails('verify')
        failure.assertHasCause('Cannot add an entry with a null value to a property of type Map.')
    }

    def "has no value when providing a null entry to a map property"() {
        given:
        buildFile << '''
            verify {
                prop.put('key', project.provider { null })
                expected = null
            }
            '''.stripIndent()

        expect:
        succeeds('verify')
    }

    def "has no value when providing a null map to a map property"() {
        given:
        buildFile << '''
            verify {
                prop.putAll(project.provider { null })
                expected = null
            }
            '''.stripIndent()

        expect:
        succeeds('verify')
    }

    @Issue('https://github.com/gradle/gradle/issues/11036')
    def "fails with precise error message when property is a map literal with null values"() {
        given:
        buildFile << """
            verify {
                prop = [${key}: ${value}]
            }
        """

        expect:
        fails('verify')
        failureCauseContains(message)

        where:
        key         | value         || message
        '(null)'    | "'someValue'" || 'Cannot get the value of a property of type java.util.Map with key type java.lang.String as the source contains a null key.'
        "'someKey'" | 'null'        || 'Cannot get the value of a property of type java.util.Map with value type java.lang.String as the source contains a null value for key "someKey".'
        '(null)'    | 'null'        || 'Cannot get the value of a property of type java.util.Map with key type java.lang.String as the source contains a null key.'
    }

    def "fails when property with no value is queried"() {
        given:
        buildFile << """
            abstract class SomeTask extends DefaultTask {
                @Internal
                abstract MapProperty<String, Long> getProp()

                @TaskAction
                def go() {
                    prop.set((Map<String, Long>)null)
                    prop.get()
                }
            }

            tasks.register('thing', SomeTask)
        """

        when:
        fails("thing")

        then:
        failure.assertHasDescription("Execution failed for task ':thing'.")
        failure.assertHasCause("Cannot query the value of task ':thing' property 'prop' because it has no value available.")
    }

    @Issue('https://github.com/gradle/gradle/issues/23014')
    def "can put flatmap of task output into map property"() {
        given:
        buildFile '''
            abstract class PrintTask extends DefaultTask {
                @OutputFile
                abstract RegularFileProperty getOutput()

                @TaskAction
                def action() {
                    output.get().asFile.text = "Hello"
                }
            }

            def printTask = tasks.register('print', PrintTask) {
                output = layout.buildDirectory.file('file.txt')
            }
        '''

        def putValue = "put('key', printTask.$provider)"
        def verifyConfig
        if (configFromExtension) {
            buildFile """
                def extension = objects.mapProperty(String, String)
                extension.$putValue
            """
            verifyConfig = "prop = extension"
        } else {
            verifyConfig = "prop.$putValue"
        }

        buildFile """
            verify {
                $verifyConfig
                expected = [key: "Hello"]
            }
        """

        expect:
        2.times {
            succeeds 'verify'
        }

        where:
        [provider, configFromExtension] << [
            [
                'flatMap { it.output }.map { it.asFile.text }',
                'flatMap { it.output }.map { it.asFile }.map { it.text }',
            ],
            [false, true]
        ].combinations()
    }

    def "circular evaluation of map property is detected"() {
        buildFile """
            def myMap = objects.mapProperty(String, String)
            def myLazyProv = provider {
                myMap.getting("foo").getOrElse("not_there")
            }
            myMap.put("bar", myLazyProv.map { "barbar" })
            myLazyProv.get()

            tasks.register("verify") {}
        """

        when:
        fails "verify"

        then:
        failureCauseContains("Circular evaluation detected")
    }
}
