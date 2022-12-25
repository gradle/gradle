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

class CollectionPropertyIntegrationTest extends AbstractIntegrationSpec {
    def setup() {
        buildFile """
            class MyTask extends DefaultTask {
                @Internal
                final ListProperty<String> prop = project.objects.listProperty(String)
                @Internal
                List<String> expected = []

                @TaskAction
                void validate() {
                    def actual = prop.getOrNull()
                    println 'Actual: ' + actual
                    println 'Expected: ' + expected
                    assert expected == actual
                    actual.each { assert it instanceof String }
                }
            }

            task verify(type: MyTask)
        """
    }

    def "can define task with abstract ListProperty<#type> getter"() {
        given:
        buildFile << """
            class Param<T> {
                T display
                String toString() { display.toString() }
            }

            abstract class ATask extends DefaultTask {
                @Input
                abstract ListProperty<$type> getProp()

                @TaskAction
                void go() {
                    println("prop = \${prop.get()}")
                }
            }

            tasks.create("thing", ATask) {
                prop = $value
            }
        """

        when:
        succeeds("thing")

        then:
        outputContains("prop = $display")

        where:
        type            | value                                                                | display
        "String"        | '["a", "b", "c"]'                                                    | '[a, b, c]'
        "Param<String>" | '[new Param<String>(display: "a"), new Param<String>(display: "b")]' | '[a, b]'
    }

    def "can finalize the value of a property using API"() {
        given:
        buildFile """
Integer counter = 0
def provider = providers.provider { [++counter, ++counter] }

def property = objects.listProperty(Integer)
property.set(provider)

assert property.get() == [1, 2]
assert property.get() == [3, 4]
property.finalizeValue()
assert property.get() == [5, 6]
assert property.get() == [5, 6]

property.set([1])
"""

        when:
        fails()

        then:
        failure.assertHasCause("The value for this property is final and cannot be changed any further.")
    }

    def "can disallow changes to a property using API without finalizing value"() {
        given:
        buildFile """
Integer counter = 0
def provider = providers.provider { [++counter, ++counter] }

def property = objects.listProperty(Integer)
property.set(provider)

assert property.get() == [1, 2]
assert property.get() == [3, 4]
property.disallowChanges()
assert property.get() == [5, 6]
assert property.get() == [7, 8]

property.set([1])
"""

        when:
        fails()

        then:
        failure.assertHasCause("The value for this property cannot be changed any further.")
    }

    def "task @Input property is implicitly finalized when task starts execution"() {
        given:
        buildFile """
class SomeTask extends DefaultTask {
    @Input
    final ListProperty<String> prop = project.objects.listProperty(String)

    @OutputFile
    final Property<RegularFile> outputFile = project.objects.fileProperty()

    @TaskAction
    void go() {
        outputFile.get().asFile.text = prop.get()
    }
}

task thing(type: SomeTask) {
    prop = ["value 1"]
    outputFile = layout.buildDirectory.file("out.txt")
    doFirst {
        prop.set(["broken"])
    }
}

afterEvaluate {
    thing.prop = ["value 2"]
}

task before {
    doLast {
        thing.prop = providers.provider { ["value 3"] }
    }
}
thing.dependsOn before
"""

        when:
        fails("thing")

        then:
        failure.assertHasDescription("Execution failed for task ':thing'.")
        failure.assertHasCause("The value for task ':thing' property 'prop' is final and cannot be changed any further.")
    }

    def "task ad hoc input property is implicitly finalized when task starts execution"() {
        given:
        buildFile """

def prop = project.objects.listProperty(String)

task thing {
    inputs.property("prop", prop)
    prop.set(["value 1"])
    doLast {
        prop.set(["ignored"])
        println "prop = " + prop.get()
    }
}
"""

        when:
        fails("thing")

        then:
        failure.assertHasDescription("Execution failed for task ':thing'.")
        failure.assertHasCause("The value for this property is final and cannot be changed any further.")
    }

    def "can use property with no value as optional ad hoc task input property"() {
        given:
        buildFile """

def prop = project.objects.listProperty(String)
prop.set((List)null)

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

    def "can set value for list property from DSL"() {
        buildFile << """
            verify {
                prop = ${value}
                expected = [ 'a', 'b', 'c' ]
            }
        """
        expect:
        succeeds("verify")

        where:
        value                                      | _
        "[ 'a', 'b', 'c' ]"                        | _
        "new LinkedHashSet([ 'a', 'b', 'c' ])"     | _
        "providers.provider { [ 'a', 'b', 'c' ] }" | _
    }

    def "can set value for string list property using GString values"() {
        buildFile << """
            def str = "aBc"
            verify {
                prop = ${value}
                expected = [ 'a', 'b' ]
            }
        """
        expect:
        succeeds("verify")

        where:
        value                                                                                         | _
        '[ "${str.substring(0, 1)}", "${str.toLowerCase().substring(1, 2)}" ]'                        | _
        'providers.provider { [ "${str.substring(0, 1)}", "${str.toLowerCase().substring(1, 2)}" ] }' | _
    }

    def "can add elements to default value"() {
        buildFile << """
            verify {
                prop = [ 'a' ]
                prop.add('b')
                prop.add(project.provider { 'c' })
                prop.addAll('d', 'e')
                prop.addAll(['f', 'g'])
                prop.addAll(project.provider { [ 'h', 'i' ] })
                expected = [ 'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i' ]
            }
        """
        expect:
        succeeds("verify")
    }

    def "can add element to string list property using GString value"() {
        buildFile << """
            def str = "aBc"
            verify {
                prop = [ 'a' ]
                prop.add($value)
                expected = [ 'a', 'b' ]
            }
        """
        expect:
        succeeds("verify")

        where:
        value                                                           | _
        '"${str.toLowerCase().substring(1, 2)}"'                        | _
        'providers.provider { "${str.toLowerCase().substring(1, 2)}" }' | _
    }

    def "can add elements to string list property using GString value"() {
        buildFile << """
            def str = "aBc"
            verify {
                prop = [ 'a' ]
                prop.addAll($value)
                expected = [ 'a', 'b', 'c' ]
            }
        """
        expect:
        succeeds("verify")

        where:
        value                                                                                         | _
        '"${str.toLowerCase().substring(1, 2)}", "${str.substring(2, 3)}"'                            | _
        '[ "${str.toLowerCase().substring(1, 2)}", "${str.substring(2, 3)}" ]'                        | _
        'providers.provider { [ "${str.toLowerCase().substring(1, 2)}", "${str.substring(2, 3)}" ] }' | _
    }

    def "reports failure to set property value using incompatible type"() {
        given:
        buildFile << """
task wrongValueTypeDsl {
    doLast {
        verify.prop = 123
    }
}

task wrongRuntimeElementType {
    doLast {
        verify.prop = [123]
        verify.prop.get()
    }
}

task wrongPropertyTypeDsl {
    doLast {
        verify.prop = objects.property(Integer)
    }
}

task wrongPropertyTypeApi {
    doLast {
        verify.prop.set(objects.property(Integer))
    }
}

task wrongPropertyElementTypeDsl {
    doLast {
        verify.prop = objects.listProperty(Integer)
    }
}

task wrongPropertyElementTypeApi {
    doLast {
        verify.prop.set(objects.listProperty(Integer))
    }
}
"""

        when:
        fails("wrongValueTypeDsl")

        then:
        failure.assertHasDescription("Execution failed for task ':wrongValueTypeDsl'.")
        failure.assertHasCause("Cannot set the value of a property of type java.util.List using an instance of type java.lang.Integer.")

        when:
        fails("wrongRuntimeElementType")

        then:
        failure.assertHasDescription("Execution failed for task ':wrongRuntimeElementType'.")
        failure.assertHasCause("Cannot get the value of a property of type java.util.List with element type java.lang.String as the source value contains an element of type java.lang.Integer.")

        when:
        fails("wrongPropertyTypeDsl")

        then:
        failure.assertHasDescription("Execution failed for task ':wrongPropertyTypeDsl'.")
        failure.assertHasCause("Cannot set the value of a property of type java.util.List using a provider of type java.lang.Integer.")

        when:
        fails("wrongPropertyTypeApi")

        then:
        failure.assertHasDescription("Execution failed for task ':wrongPropertyTypeApi'.")
        failure.assertHasCause("Cannot set the value of a property of type java.util.List using a provider of type java.lang.Integer.")

        when:
        fails("wrongPropertyElementTypeDsl")

        then:
        failure.assertHasDescription("Execution failed for task ':wrongPropertyElementTypeDsl'.")
        failure.assertHasCause("Cannot set the value of a property of type java.util.List with element type java.lang.String using a provider with element type java.lang.Integer.")

        when:
        fails("wrongPropertyElementTypeApi")

        then:
        failure.assertHasDescription("Execution failed for task ':wrongPropertyElementTypeApi'.")
        failure.assertHasCause("Cannot set the value of a property of type java.util.List with element type java.lang.String using a provider with element type java.lang.Integer.")
    }

    def "can add elements to empty list property"() {
        buildFile << """
            verify {
                prop.empty()
                prop.add('a')
                prop.add(project.provider { 'b' })
                prop.addAll(project.provider { [ 'c', 'd' ] })
                expected = [ 'a', 'b', 'c', 'd' ]
            }
        """
        expect:
        succeeds("verify")
    }

    def "adds to non-defined property does nothing"() {
        buildFile << """
            verify {
                prop = null
                prop.add('b')
                prop.add(project.provider { 'c' })
                prop.addAll(project.provider { [ 'd', 'e' ] })
                expected = null
            }
        """
        expect:
        succeeds("verify")
    }

    def "reasonable message when trying to add a null to a list property"() {
        buildFile << """
            verify {
                prop.add(null)
            }
        """
        expect:
        def failure = fails("verify")
        failure.assertHasCause("Cannot add a null element to a property of type List.")
    }

    def "has no value when providing null to a list property"() {
        buildFile << """
            verify {
                prop.add(project.provider { null })
                expected = null
            }
        """
        expect:
        succeeds("verify")
    }

    def "has no value when providing null list to a list property"() {
        buildFile << """
            verify {
                prop.addAll(project.provider { null })
                expected = null
            }
        """
        expect:
        succeeds("verify")
    }

    def "fails when property with no value is queried"() {
        given:
        buildFile << """
            abstract class SomeTask extends DefaultTask {
                @Internal
                abstract ListProperty<String> getProp()

                @TaskAction
                def go() {
                    prop.set((Iterable<String>)null)
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

}
