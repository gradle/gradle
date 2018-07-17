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

import static PropertyStateProjectUnderTest.Language
import static org.gradle.util.TextUtil.normaliseFileSeparators

class PropertyIntegrationTest extends AbstractIntegrationSpec {

    private final PropertyStateProjectUnderTest projectUnderTest = new PropertyStateProjectUnderTest(testDirectory)

    @Unroll
    def "receives deprecation warning when using #expr"() {
        given:
        buildFile << """
PropertyState<String> p = $expr
p.set("123")
p.get()
"""

        when:
        executer.expectDeprecationWarning()
        run()

        then:
        output.contains("The property(Class) method has been deprecated. This is scheduled to be removed in Gradle 5.0. Please use the ObjectFactory.property(Class) method instead.")

        where:
        expr                         | _
        "providers.property(String)" | _
        "project.property(String)"   | _
        "property(String)"           | _
    }

    @Unroll
    def "can create and use property state by custom task written as #language class"() {
        given:
        projectUnderTest.writeCustomTaskTypeToBuildSrc(language)
        buildFile << """
            task myTask(type: MyTask)
        """

        when:
        succeeds('myTask')

        then:
        projectUnderTest.assertDefaultOutputFileDoesNotExist()

        when:
        buildFile << """
             myTask {
                enabled = true
                outputFiles = files("${normaliseFileSeparators(projectUnderTest.customOutputFile.canonicalPath)}")
            }
        """
        succeeds('myTask')

        then:
        projectUnderTest.assertDefaultOutputFileDoesNotExist()
        projectUnderTest.assertCustomOutputFileContent()

        where:
        language << [Language.GROOVY, Language.JAVA]
    }

    def "can lazily map extension property state to task property with convention mapping"() {
        given:
        projectUnderTest.writeCustomGroovyBasedTaskTypeToBuildSrc()
        projectUnderTest.writePluginWithExtensionMappingUsingConventionMapping()

        when:
        succeeds('myTask')

        then:
        projectUnderTest.assertDefaultOutputFileDoesNotExist()
        projectUnderTest.assertCustomOutputFileContent()
    }

    def "can lazily map extension property state to task property with property state"() {
        given:
        projectUnderTest.writeCustomGroovyBasedTaskTypeToBuildSrc()
        projectUnderTest.writePluginWithExtensionMappingUsingPropertyState()

        when:
        succeeds('myTask')

        then:
        projectUnderTest.assertDefaultOutputFileDoesNotExist()
        projectUnderTest.assertCustomOutputFileContent()
    }

    def "can use property state as task input"() {
        given:
        buildFile << """
class SomeTask extends DefaultTask {
    @Input
    final Property<String> prop = project.objects.property(String)
    
    @OutputFile
    final Property<RegularFile> outputFile = newOutputFile()
    
    @TaskAction
    void go() { 
        outputFile.get().asFile.text = prop.get()
    }
}

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

extensions.create('custom', SomeExtension, objects)
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

extensions.create('custom', SomeExtension, objects)

task wrongValueType {
    doLast {
        custom.prop = 123
    }
}

task wrongPropertyType {
    doLast {
        custom.prop = objects.property(Integer)
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
        fails("wrongValueType")

        then:
        failure.assertHasDescription("Execution failed for task ':wrongValueType'.")
        failure.assertHasCause("Cannot set the value of a property of type java.lang.String using an instance of type java.lang.Integer.")

        when:
        fails("wrongPropertyType")

        then:
        failure.assertHasDescription("Execution failed for task ':wrongPropertyType'.")
        failure.assertHasCause("Cannot set the value of a property of type java.lang.String using a provider of type java.lang.Integer.")

        when:
        fails("wrongRuntimeType")

        then:
        failure.assertHasDescription("Execution failed for task ':wrongRuntimeType'.")
        failure.assertHasCause("Cannot get the value of a property of type java.lang.String as the provider associated with this property returned a value of type java.lang.Integer.")
    }
}
