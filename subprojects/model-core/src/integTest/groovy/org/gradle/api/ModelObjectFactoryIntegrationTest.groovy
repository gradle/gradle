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

package org.gradle.api

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class ModelObjectFactoryIntegrationTest extends AbstractIntegrationSpec {
    def "plugin can create named instances of interface using injected factory"() {
        buildFile << """
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
        failure.assertHasCause("No signature of method: Thing\$Impl.setProperty() is applicable for argument types: (java.lang.String, java.lang.String) values: [name, 123]")

        when:
        fails("changeField")

        then:
        failure.assertHasCause("No such field: name for class: Thing\$Impl")
    }

    def "cannot create instance with fields"() {
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
