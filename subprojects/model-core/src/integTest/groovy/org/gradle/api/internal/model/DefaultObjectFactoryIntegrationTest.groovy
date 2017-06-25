/*
 * Copyright 2016 the original author or authors.
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
    def "plugin can create unnamed instances of class using injected factory"() {
        buildFile << """
            @groovy.transform.Canonical
            class Thing {
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

    def "plugin application fails with ObjectInstantiationException given invalid factory constructor"() {
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
        failure.assertHasCause('Could not create an instance of type Thing')
    }
}
