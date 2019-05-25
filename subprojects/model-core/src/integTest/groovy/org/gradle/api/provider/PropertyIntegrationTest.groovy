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

    def "can define task with abstract Property getter"() {
        given:
        buildFile << """
            abstract class MyTask extends DefaultTask {
                @Input
                abstract Property<String> getProp()
                
                @TaskAction
                void go() {
                    println("prop = \${prop.get()}")
                }
            }
            
            tasks.create("thing", MyTask) {
                prop = "abc"
            }
        """

        when:
        succeeds("thing")

        then:
        outputContains("prop = abc")
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

    def "task @Input property is implicitly finalized and changes ignored when task starts execution"() {
        given:
        buildFile << """
class SomeTask extends DefaultTask {
    @Input
    final Property<String> prop = project.objects.property(String)
    
    @OutputFile
    final Property<RegularFile> outputFile = project.objects.fileProperty()
    
    @TaskAction
    void go() {
        prop.set("ignored")
        outputFile.get().asFile.text = prop.get()
    }
}

task thing(type: SomeTask) {
    prop = "value 1"
    outputFile = layout.buildDirectory.file("out.txt")
    doLast {
        prop.set("ignored")
    }
}

afterEvaluate {
    thing.prop = "value 2"
}

task before {
    doLast {
        thing.prop = providers.provider { "final value" }
    }
}
thing.dependsOn before

task after {
    dependsOn thing
    doLast {
        thing.prop = "ignore"
        assert thing.prop.get() == "final value"
    }
}
"""

        when:
        executer.expectDeprecationWarning()
        run("after")

        then:
        file("build/out.txt").text == "final value"
    }

    def "task ad hoc input property is implicitly finalized and changes ignored when task starts execution"() {
        given:
        buildFile << """

def prop = project.objects.property(String)

task thing {
    inputs.property("prop", prop)
    prop.set("value 1")
    doLast {
        prop.set("ignored")
        println "prop = " + prop.get()
    }
}
"""

        when:
        executer.expectDeprecationWarning()
        run("thing")

        then:
        output.contains("prop = value 1")
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

    def "can set String property value from DSL using a GString"() {
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
custom.prop = "\${'some value'.substring(5)}"
assert custom.prop.get() == "value"

custom.prop = providers.provider { "\${'some new value'.substring(5)}" }
assert custom.prop.get() == "new value"

custom.prop.set("\${'some other value'.substring(5)}")
assert custom.prop.get() == "other value"
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
"""

        when:
        fails("wrongValueTypeDsl")

        then:
        failure.assertHasDescription("Execution failed for task ':wrongValueTypeDsl'.")
        failure.assertHasCause("Cannot set the value of a property of type java.lang.String using an instance of type java.lang.Integer.")

        when:
        fails("wrongValueTypeApi")

        then:
        failure.assertHasDescription("Execution failed for task ':wrongValueTypeApi'.")
        failure.assertHasCause("Cannot set the value of a property of type java.lang.String using an instance of type java.lang.Integer.")

        when:
        fails("wrongPropertyTypeDsl")

        then:
        failure.assertHasDescription("Execution failed for task ':wrongPropertyTypeDsl'.")
        failure.assertHasCause("Cannot set the value of a property of type java.lang.String using a provider of type java.lang.Integer.")

        when:
        fails("wrongPropertyTypeApi")

        then:
        failure.assertHasDescription("Execution failed for task ':wrongPropertyTypeApi'.")
        failure.assertHasCause("Cannot set the value of a property of type java.lang.String using a provider of type java.lang.Integer.")

        when:
        fails("wrongRuntimeType")

        then:
        failure.assertHasDescription("Execution failed for task ':wrongRuntimeType'.")
        failure.assertHasCause("Cannot get the value of a property of type java.lang.String as the provider associated with this property returned a value of type java.lang.Integer.")
    }

    def "emits deprecation warning when specialized factory method is not used"() {
        buildFile << """
class SomeExtension {
    final Property<List<String>> prop1
    final Property<Set<String>> prop2
    final Property<Directory> prop3
    final Property<RegularFile> prop4
    final Property<Map<String, String>> prop5

    @javax.inject.Inject
    SomeExtension(ObjectFactory objects) {
        prop1 = objects.property(List)
        prop2 = objects.property(Set)
        prop3 = objects.property(Directory)
        prop4 = objects.property(RegularFile)
        prop5 = objects.property(Map)
    }
}
 
project.extensions.create("some", SomeExtension)            
        """

        when:
        executer.expectDeprecationWarnings(5)
        succeeds()

        then:
        outputContains("Using method ObjectFactory.property() to create a property of type List<T> has been deprecated. This will fail with an error in Gradle 6.0. Please use the ObjectFactory.listProperty() method instead.")
        outputContains("Using method ObjectFactory.property() method to create a property of type Set<T> has been deprecated. This will fail with an error in Gradle 6.0. Please use the ObjectFactory.setProperty() method instead.")
        outputContains("Using method ObjectFactory.property() method to create a property of type Directory has been deprecated. This will fail with an error in Gradle 6.0. Please use the ObjectFactory.directoryProperty() method instead.")
        outputContains("Using method ObjectFactory.property() method to create a property of type RegularFile has been deprecated. This will fail with an error in Gradle 6.0. Please use the ObjectFactory.fileProperty() method instead.")
        outputContains("Using method ObjectFactory.property() method to create a property of type Map<K, V> has been deprecated. This will fail with an error in Gradle 6.0. Please use the ObjectFactory.mapProperty() method instead.")
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
