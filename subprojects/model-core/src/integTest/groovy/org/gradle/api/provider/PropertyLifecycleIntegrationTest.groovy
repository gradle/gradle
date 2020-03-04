/*
 * Copyright 2020 the original author or authors.
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

class PropertyLifecycleIntegrationTest extends AbstractIntegrationSpec {
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

assert counter == 3 // is eager
assert property.get() == 3

counter = 45
assert property.get() == 3

property.set(12)
"""

        when:
        fails()

        then:
        failure.assertHasCause("The value for this property is final and cannot be changed any further.")
    }

    def "can finalize the value of a property on next read using API"() {
        given:
        buildFile << """
Integer counter = 0
def provider = providers.provider { ++counter }

def property = objects.property(Integer)
property.set(provider)

assert property.get() == 1
assert property.get() == 2

property.finalizeValueOnRead()

assert counter == 2 // is lazy
assert property.get() == 3

counter = 45
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

    def "can write but cannot read strict project property instance before afterEvaluate starts"() {
        given:
        settingsFile << 'rootProject.name = "broken"'
        buildFile << """
interface ProjectModel {
    Property<String> getProp()
}

project.extensions.create('thing', ProjectModel.class)
thing.prop.disallowUnsafeRead()
thing.prop.set("abc")

try {
    thing.prop.get()
} catch(IllegalStateException e) {
    println("get failed with: \${e.message}")
}

thing.prop.set("123")

afterEvaluate {
    println("value = \${thing.prop.get()}")
    try {
        thing.prop.set("ignore me")
    } catch(IllegalStateException e) {
        println("set after read failed with: \${e.message}")
    }
}

task show {
    doLast {
        println("value = \${thing.prop.get()}")
    }
}
        """

        when:
        run("show")

        then:
        outputContains("get failed with: Cannot query the value of extension 'thing' property 'prop' because configuration of root project 'broken' has not finished yet.")
        outputContains("set after read failed with: The value for extension 'thing' property 'prop' is final and cannot be changed any further.")
        output.count("value = 123") == 2
    }

    def "can change value of strict property after afterEvaluate starts and before the value has been read"() {
        given:
        settingsFile << 'rootProject.name = "broken"'
        buildFile << """
interface ProjectModel {
    Property<String> getProp()
}

project.extensions.create('thing', ProjectModel.class)
thing.prop.disallowUnsafeRead()

afterEvaluate {
    thing.prop.set("123")
    println("value = \${thing.prop.get()}")
    try {
        thing.prop.set("ignore me")
    } catch(IllegalStateException e) {
        println("set failed with: \${e.message}")
    }
}

task show {
    doLast {
        println("value = \${thing.prop.get()}")
    }
}
        """

        when:
        run("show")

        then:
        outputContains("set failed with: The value for extension 'thing' property 'prop' is final and cannot be changed any further.")
        output.count("value = 123") == 2
    }
}
