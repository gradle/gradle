/*
 * Copyright 2017 the original author or authors.
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
import spock.lang.Unroll

class PropertyIntegrationTest extends AbstractIntegrationSpec {
    def "can use property as task input"() {
        given:
        taskTypeWritesPropertyValueToFile()
        buildFile << """

task thing(type: SomeTask) {
    prop = System.getProperty('prop')
    outputFile = layout.buildDirectory.file("out.txt")
}

"""

        when:
        executer.withArgument("-Dprop=123")
        run("thing")

        then:
        executedAndNotSkipped(":thing")

        when:
        executer.withArgument("-Dprop=123")
        run("thing")

        then:
        skipped(":thing")

        when:
        executer.withArgument("-Dprop=abc")
        run("thing")

        then:
        executedAndNotSkipped(":thing")

        when:
        executer.withArgument("-Dprop=abc")
        run("thing")

        then:
        skipped(":thing")
    }

    @Unroll
    def "can define task with abstract Property<#type> getter"() {
        given:
        buildFile << """
            class Param<T> {
                T display
                String toString() { display.toString() }
            }

            abstract class MyTask extends DefaultTask {
                @Input
                abstract Property<$type> getProp()
                
                @TaskAction
                void go() {
                    println("prop = \${prop.get()}")
                }
            }
            
            tasks.create("thing", MyTask) {
                prop = $value
            }
        """

        when:
        succeeds("thing")

        then:
        outputContains("prop = $display")

        where:
        type            | value                               | display
        "String"        | "'abc'"                             | "abc"
        "Param<String>" | "new Param<String>(display: 'abc')" | "abc"
    }

    def "can define task with abstract nested property"() {
        given:
        buildFile << """
            interface NestedType {
                @Input
                Property<String> getProp()
            }

            abstract class MyTask extends DefaultTask {
                @Nested
                abstract NestedType getNested()
                
                void nested(Action<NestedType> action) {
                    action.execute(nested)
                }
                
                @TaskAction
                void go() {
                    println("prop = \${nested.prop.get()}")
                }
            }
            
            tasks.create("thing", MyTask) {
                nested {
                    prop = "value"
                }
            }
        """

        when:
        succeeds("thing")

        then:
        outputContains("prop = value")
    }

    def "can finalize the value of a property using API"() {
        given:
        buildFile << """
Integer counter = 0
def provider = providers.provider { ++counter }

def property = objects.property(Integer)
property.set(provider)

assert property.get() == 1 
assert property.get() == 2 
property.finalizeValue()
assert property.get() == 3 
assert property.get() == 3 

property.set(12)
"""

        when:
        fails()

        then:
        failure.assertHasCause("The value for this property is final and cannot be changed any further.")
    }

    def "can disallow changes to a property using API without finalizing the value"() {
        given:
        buildFile << """
Integer counter = 0
def provider = providers.provider { ++counter }

def property = objects.property(Integer)
property.set(provider)

assert property.get() == 1 
assert property.get() == 2 
property.disallowChanges()
assert property.get() == 3
assert property.get() == 4 

property.set(12)
"""

        when:
        fails()

        then:
        failure.assertHasCause("The value for this property cannot be changed any further.")
    }

    def "task @Input property is implicitly finalized when task starts execution"() {
        given:
        buildFile << """
class SomeTask extends DefaultTask {
    @Input
    final Property<String> prop = project.objects.property(String)
    
    @OutputFile
    final Property<RegularFile> outputFile = project.objects.fileProperty()
    
    @TaskAction
    void go() {
        outputFile.get().asFile.text = prop.get()
    }
}

task thing(type: SomeTask) {
    prop = "value 1"
    outputFile = layout.buildDirectory.file("out.txt")
    doFirst {
        prop.set("broken")
    }
}

afterEvaluate {
    thing.prop = "value 2"
}

task before {
    doLast {
        thing.prop = providers.provider { "value 3" }
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
        buildFile << """

def prop = project.objects.property(String)

task thing {
    inputs.property("prop", prop)
    prop.set("value 1")
    doFirst {
        prop.set("broken")
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
        buildFile << """

def prop = project.objects.property(String)

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

    def "reports failure due to broken @Input task property"() {
        taskTypeWritesPropertyValueToFile()
        buildFile << """

task thing(type: SomeTask) {
    prop = providers.provider { throw new RuntimeException("broken") }
    outputFile = layout.buildDirectory.file("out.txt")
}
            
        """

        when:
        fails("thing")

        then:
        failure.assertHasDescription("Execution failed for task ':thing'.")
        failure.assertHasCause("broken")
    }

    def "task @Input property calculation is called once only when task executes"() {
        taskTypeWritesPropertyValueToFile()
        buildFile << """

task thing(type: SomeTask) {
    prop = providers.provider { 
        println("calculating value")
        return "value"
    }
    outputFile = layout.buildDirectory.file("out.txt")
}
            
        """

        when:
        run("thing")

        then:
        output.count("calculating value") == 1

        when:
        run("thing")

        then:
        result.assertTaskSkipped(":thing")
        output.count("calculating value") == 1

        when:
        run("help")

        then:
        output.count("calculating value") == 0
    }

    def "does not calculate task @Input property value when task is skipped due to @SkipWhenEmpty on another property"() {
        buildFile << """

class SomeTask extends DefaultTask {
    @Input
    final Property<String> prop = project.objects.property(String)
    
    @InputFiles @SkipWhenEmpty
    final SetProperty<RegularFile> outputFile = project.objects.setProperty(RegularFile)
    
    @TaskAction
    void go() {
    }
}

task thing(type: SomeTask) {
    prop = providers.provider { 
        throw new RuntimeException("should not be called")
    }
}
            
        """

        when:
        run("thing")

        then:
        result.assertTaskSkipped(":thing")
    }

    def "can set property value from DSL using a value or a provider"() {
        given:
        buildFile << """
class SomeExtension {
    final Property<String> prop
    
    @javax.inject.Inject
    SomeExtension(ObjectFactory objects) {
        prop = objects.property(String)
    }
}

class SomeTask extends DefaultTask {
    final Property<String> prop = project.objects.property(String)
}

extensions.create('custom', SomeExtension)
custom.prop = "value"
assert custom.prop.get() == "value"

custom.prop = providers.provider { "new value" }
assert custom.prop.get() == "new value"

tasks.create('t', SomeTask)
tasks.t.prop = custom.prop
assert tasks.t.prop.get() == "new value"

custom.prop = "changed"
assert custom.prop.get() == "changed"
assert tasks.t.prop.get() == "changed"

"""

        expect:
        succeeds()
    }

    def "can set String property value using a GString"() {
        given:
        buildFile << """
class SomeExtension {
    final Property<String> prop
    
    @javax.inject.Inject
    SomeExtension(ObjectFactory objects) {
        prop = objects.property(String)
    }
}

extensions.create('custom', SomeExtension)
custom.prop = "\${'some value 1'.substring(5)}"
assert custom.prop.get() == "value 1"

custom.prop = providers.provider { "\${'some value 2'.substring(5)}" }
assert custom.prop.get() == "value 2"

custom.prop = null
custom.prop.convention("\${'some value 3'.substring(5)}")
assert custom.prop.get() == "value 3"

custom.prop.convention(providers.provider { "\${'some value 4'.substring(5)}" })
assert custom.prop.get() == "value 4"
"""

        expect:
        succeeds()
    }

    def "reports failure to set property value using incompatible type"() {
        given:
        buildFile << """
class SomeExtension {
    final Property<String> prop
    
    @javax.inject.Inject
    SomeExtension(ObjectFactory objects) {
        prop = objects.property(String)
    }
}

extensions.create('custom', SomeExtension)

task wrongValueTypeDsl {
    doLast {
        custom.prop = 123
    }
}

task wrongValueTypeApi {
    doLast {
        custom.prop.set(123)
    }
}

task wrongPropertyTypeDsl {
    doLast {
        custom.prop = objects.property(Integer)
    }
}

task wrongPropertyTypeApi {
    doLast {
        custom.prop.set(objects.property(Integer))
    }
}

task wrongRuntimeType {
    doLast {
        custom.prop = providers.provider { 123 }
        custom.prop.get()
    }
}

task wrongConventionValueType {
    doLast {
        custom.prop.convention(123)
    }
}

task wrongConventionPropertyType {
    doLast {
        custom.prop.convention(objects.property(Integer))
    }
}

task wrongConventionRuntimeValueType {
    doLast {
        custom.prop.convention(providers.provider { 123 })
        custom.prop.get()
    }
}
"""

        when:
        fails("wrongValueTypeDsl")

        then:
        failure.assertHasDescription("Execution failed for task ':wrongValueTypeDsl'.")
        failure.assertHasCause("Cannot set the value of extension 'custom' property 'prop' of type java.lang.String using an instance of type java.lang.Integer.")

        when:
        fails("wrongValueTypeApi")

        then:
        failure.assertHasDescription("Execution failed for task ':wrongValueTypeApi'.")
        failure.assertHasCause("Cannot set the value of extension 'custom' property 'prop' of type java.lang.String using an instance of type java.lang.Integer.")

        when:
        fails("wrongPropertyTypeDsl")

        then:
        failure.assertHasDescription("Execution failed for task ':wrongPropertyTypeDsl'.")
        failure.assertHasCause("Cannot set the value of extension 'custom' property 'prop' of type java.lang.String using a provider of type java.lang.Integer.")

        when:
        fails("wrongPropertyTypeApi")

        then:
        failure.assertHasDescription("Execution failed for task ':wrongPropertyTypeApi'.")
        failure.assertHasCause("Cannot set the value of extension 'custom' property 'prop' of type java.lang.String using a provider of type java.lang.Integer.")

        when:
        fails("wrongRuntimeType")

        then:
        failure.assertHasDescription("Execution failed for task ':wrongRuntimeType'.")
        failure.assertHasCause("Cannot get the value of extension 'custom' property 'prop' of type java.lang.String as the provider associated with this property returned a value of type java.lang.Integer.")

        when:
        fails("wrongConventionValueType")

        then:
        failure.assertHasDescription("Execution failed for task ':wrongConventionValueType'.")
        failure.assertHasCause("Cannot set the value of extension 'custom' property 'prop' of type java.lang.String using an instance of type java.lang.Integer.")

        when:
        fails("wrongConventionPropertyType")

        then:
        failure.assertHasDescription("Execution failed for task ':wrongConventionPropertyType'.")
        failure.assertHasCause("Cannot set the value of extension 'custom' property 'prop' of type java.lang.String using a provider of type java.lang.Integer.")

        when:
        fails("wrongConventionRuntimeValueType")

        then:
        failure.assertHasDescription("Execution failed for task ':wrongConventionRuntimeValueType'.")
        failure.assertHasCause("Cannot get the value of extension 'custom' property 'prop' of type java.lang.String as the provider associated with this property returned a value of type java.lang.Integer.")
    }

    def "fails when specialized factory method is not used"() {
        buildFile << """
class SomeExtension {
    final Property<List<String>> prop1
    final Property<Set<String>> prop2
    final Property<Directory> prop3
    final Property<RegularFile> prop4
    final Property<Map<String, String>> prop5

    @javax.inject.Inject
    SomeExtension(ObjectFactory objects) {
        $prop = objects.property($type)
    }
}
 
project.extensions.create("some", SomeExtension)            
        """

        when:
        fails()

        then:
        failure.assertHasCause("Please use the ObjectFactory.$method method to create a property of type $type$typeParam.")

        where:
        prop    | method                | type          | typeParam
        'prop1' | 'listProperty()'      | 'List'        | '<T>'
        'prop2' | 'setProperty()'       | 'Set'         | '<T>'
        'prop3' | 'mapProperty()'       | 'Map'         | '<K, V>'
        'prop4' | 'directoryProperty()' | 'Directory'   | ''
        'prop5' | 'fileProperty()'      | 'RegularFile' | ''
    }

    def taskTypeWritesPropertyValueToFile() {
        buildFile << """
            class SomeTask extends DefaultTask {
                @Input
                final Property<String> prop = project.objects.property(String)
                
                @OutputFile
                final Property<RegularFile> outputFile = project.objects.fileProperty()
                
                @TaskAction
                void go() {
                    outputFile.get().asFile.text = prop.get()
                }
            }
        """
    }
}
