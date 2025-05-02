/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.api.internal.model

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache

import static org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache.Skip.INVESTIGATE
import static org.hamcrest.CoreMatchers.allOf
import static org.hamcrest.CoreMatchers.containsString
import static org.hamcrest.CoreMatchers.startsWith

class ObjectFactoryNamedTypeIntegrationTest extends AbstractIntegrationSpec {
    def "plugin can create named instances of interface using injected factory"() {
        buildFile """
            interface Thing extends Named { }

            class CustomPlugin implements Plugin<Project> {
                ObjectFactory objects

                @javax.inject.Inject
                CustomPlugin(ObjectFactory objects) {
                    this.objects = objects
                }

                void apply(Project project) {
                    project.tasks.create('thing1', CustomTask) {
                        thing = objects.named(Thing, 'thing1')
                    }
                    project.tasks.create('thing2', CustomTask) {
                        thing = project.objects.named(Thing, 'thing2')
                    }
                }
            }

            class CustomTask extends DefaultTask {
                @Internal
                Thing thing

                @javax.inject.Inject
                ObjectFactory getObjects() { null }

                @TaskAction
                void run() {
                    println thing.toString() + ": " + objects.named(Thing, thing.name)
                }
            }

            apply plugin: CustomPlugin
"""

        when:
        run "thing1", "thing2"

        then:
        outputContains("thing1: thing1")
        outputContains("thing2: thing2")
    }

    def "plugin can create named instances of abstract class"() {
        buildFile << """
            abstract class Thing implements Named { }

            class CustomPlugin implements Plugin<Project> {
                void apply(Project project) {
                    project.tasks.create('thing1', CustomTask) {
                        thing = project.objects.named(Thing, 'thing1')
                    }
                }
            }

            class CustomTask extends DefaultTask {
                @Internal
                Thing thing

                @TaskAction
                void run() {
                    println thing.toString() + ": " + thing.name
                }
            }

            apply plugin: CustomPlugin
"""

        when:
        run "thing1"

        then:
        outputContains("thing1: thing1")
    }

    @ToBeFixedForConfigurationCache(skip = INVESTIGATE, because = "Flaky ClassNotFoundException: Thing\$Impl")
    def "named instance can be used as task input property"() {
        buildFile << """
            interface Thing extends Named { }

            class ThingTask extends DefaultTask {
                @Input
                Thing thing

                @OutputFile
                RegularFile outputFile

                @TaskAction def go() {
                    outputFile.asFile.text = thing.name
                }
            }

            task a(type: ThingTask) {
                thing = objects.named(Thing, providers.systemProperty('name').getOrElse('a'))
                outputFile = layout.projectDirectory.file("out.txt")
            }
"""

        when:
        run("a")

        then:
        file("out.txt").text == "a"

        when:
        run("a")

        then:
        result.assertTaskSkipped(":a")

        when:
        executer.withArgument("-Dname=b")
        run("a")

        then:
        result.assertTaskNotSkipped(":a")
        file("out.txt").text == "b"

        when:
        executer.withArgument("-Dname=b")
        run("a")

        then:
        result.assertTaskSkipped(":a")
    }

    @ToBeFixedForConfigurationCache(skip = INVESTIGATE, because = "Flaky")
    def "cannot mutate named instance from groovy"() {
        buildFile << """
            interface Thing extends Named { }

            def t1 = objects.named(Thing, "t1")
            task changeProp {
                doLast {
                    t1.name = "123"
                }
            }
            task changeDynProp {
                doLast {
                    t1.setProperty("name", "123")
                }
            }
            task changeField {
                doLast {
                    t1.@name = "123"
                }
            }
"""

        when:
        fails("changeProp")

        then:
        failure.assertHasCause("Cannot set readonly property: name for class: Thing\$Impl")

        when:
        fails("changeDynProp")

        then:
        failure.assertThatCause(allOf(
            startsWith("No signature of method: Thing\$Impl"),
            containsString(".setProperty() is applicable for argument types: (String, String) values: [name, 123]")
        ))

        when:
        fails("changeField")

        then:
        failure.assertHasCause("No such field: name for class: Thing\$Impl")
    }

    def "cannot create named instance with fields"() {
        buildFile << """
            class Thing implements Named {
                String name
            }

            objects.named(Thing, "t1")
"""

        when:
        fails("help")

        then:
        failure.assertHasCause("Could not create an instance of type Thing.")
        failure.assertHasCause("""Type Thing is not a valid Named implementation class:
- Field name is not valid: A Named implementation class must not define any instance fields.""")
    }
}
