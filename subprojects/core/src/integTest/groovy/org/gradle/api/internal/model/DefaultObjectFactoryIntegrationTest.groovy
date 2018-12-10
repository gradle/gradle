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
package org.gradle.api.internal.model

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class DefaultObjectFactoryIntegrationTest extends AbstractIntegrationSpec {
    def "plugin can create instances of class using injected factory"() {
        buildFile << """
            @groovy.transform.ToString
            class Thing {
                @javax.inject.Inject
                Thing(String name) { this.name = name }
                
                String name
            }
            
            class CustomPlugin implements Plugin<Project> {
                ObjectFactory objects
                
                @javax.inject.Inject
                CustomPlugin(ObjectFactory objects) {
                    this.objects = objects
                }
                
                void apply(Project project) {
                    project.tasks.create('thing1', CustomTask) { 
                        thing = objects.newInstance(Thing, 'thing1')
                    }
                    project.tasks.create('thing2', CustomTask) { 
                        thing = project.objects.newInstance(Thing, 'thing2')
                    }
                }
            }
            
            class CustomTask extends DefaultTask {
                Thing thing
                
                @javax.inject.Inject
                ObjectFactory getObjects() { null }
                
                @TaskAction
                void run() {
                    println thing.toString() + ": " + objects.newInstance(Thing, thing.name)    
                }
            }
            
            apply plugin: CustomPlugin
"""

        when:
        run "thing1", "thing2"

        then:
        outputContains("Thing(thing1): Thing(thing1)")
        outputContains("Thing(thing2): Thing(thing2)")
    }

    def "services are injected into instances using constructor or getter"() {
        buildFile << """
            class Thing1 {
                final Property<String> name
                
                @javax.inject.Inject
                Thing1(ObjectFactory objects) { this.name = objects.property(String) }
            }
            
            class Thing2 {
                @javax.inject.Inject
                ObjectFactory getObjects() { null }
                
                String getName() {
                    def t = objects.newInstance(Thing1)
                    t.name.set("name")
                    t.name.get()
                }
            }
            
            assert objects.newInstance(Thing2).name == "name"
"""

        expect:
        succeeds()
    }

    def "can create nested DSL elements using injected ObjectFactory"() {
        buildFile << """
            class Thing {
                String name   
            }
            
            class Thing2 {
                Thing thing
                
                @javax.inject.Inject
                Thing2(ObjectFactory factory) {
                    thing = factory.newInstance(Thing)
                }
                
                void thing(Action<? super Thing> action) { action.execute(thing) }
            }
            
            class Thing3 {
                Thing2 thing
                
                Thing3(ObjectFactory factory) {
                    thing = factory.newInstance(Thing2)
                }
                
                void thing(Action<? super Thing2> action) { action.execute(thing) }
            }
            
            project.extensions.create('thing', Thing3, project.objects)
            
            thing {
                thing {
                    thing {
                        name = 'thing'
                    }
                }
            }
            assert thing.thing.thing.name == 'thing'
"""

        expect:
        succeeds()
    }

    def "object creation fails with ObjectInstantiationException given invalid factory constructor"() {
        given:
        buildFile << """
        class Thing {}
        
        task fail {
            doLast {
                project.objects.newInstance(Thing, 'bogus') 
            }
        }
"""

        when:
        fails "fail"

        then:
        failure.assertHasCause('Could not create an instance of type Thing.')
        failure.assertHasCause('Too many parameters provided for constructor for class Thing. Expected 0, received 1.')
    }

    def "object creation fails with ObjectInstantiationException given unknown service injected"() {
        given:
        buildFile << """
        interface Unknown { }
        
        class Thing {
            @javax.inject.Inject
            Thing(Unknown u) { }
        }
        
        task fail {
            doLast {
                project.objects.newInstance(Thing) 
            }
        }
"""

        when:
        fails "fail"

        then:
        failure.assertHasCause('Could not create an instance of type Thing.')
        failure.assertHasCause('Unable to determine Thing argument #1: missing parameter value of type interface Unknown, or no service of type interface Unknown')
    }

    def "object creation fails with ObjectInstantiationException when constructor throws an exception"() {
        given:
        buildFile << """
        class Thing {
            Thing() { throw new GradleException("broken") }
        }
        
        task fail {
            doLast {
                project.objects.newInstance(Thing) 
            }
        }
"""

        when:
        fails "fail"

        then:
        failure.assertHasCause('Could not create an instance of type Thing.')
        failure.assertHasCause('broken')
    }

    def "object creation fails with ObjectInstantiationException when constructor takes parameters but is not annotated"() {
        given:
        buildFile << """
        class Thing {
            Thing(ObjectFactory factory) { }
        }
        
        task fail {
            doLast {
                project.objects.newInstance(Thing) 
            }
        }
"""

        when:
        fails "fail"

        then:
        failure.assertHasCause('Could not create an instance of type Thing.')
        failure.assertHasCause('The constructor for class Thing should be annotated with @Inject.')
    }

    def "plugin can create SourceDirectorySet instances"() {
        given:
        buildFile << """
            def dirSet = project.objects.sourceDirectorySet("sources", "some source files")
            assert dirSet != null
        """

        expect:
        succeeds()
    }
}
