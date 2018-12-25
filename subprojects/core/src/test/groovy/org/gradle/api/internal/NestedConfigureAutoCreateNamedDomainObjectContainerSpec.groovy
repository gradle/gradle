/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal


import org.gradle.util.TestUtil
import spock.lang.Specification

class NestedConfigureAutoCreateNamedDomainObjectContainerSpec extends Specification {

    def instantiator = TestUtil.instantiatorFactory().decorateLenient()

    static class Container extends FactoryNamedDomainObjectContainer {
        String parentName
        String name
        Container(String parentName, String name, Closure factory) {
            super(Object, TestUtil.instantiatorFactory().decorateLenient(), new DynamicPropertyNamer(), factory, MutationGuards.identity(), CollectionCallbackActionDecorator.NOOP)
            this.parentName = parentName
            this.name = name
        }
    }

    def "can nest auto creation configure closures"() {
        given:
        def parent = instantiator.newInstance(Container, "top", "parent", { name1 ->
            instantiator.newInstance(Container, "parent", name1, { name2 ->
                instantiator.newInstance(Container, name1, name2, { name3 ->
                    [parentName: name2, name: name3]
                })
            })
        })

        when:
        parent.configure {
            c1 {
                c1c1 {
                    m1 {
                        prop = "c1c1m1"
                    }
                    m2 {
                        prop = "c1c1m2"
                    }
                }
                c1c2 {
                    m1 {
                        prop = "c1c2m1"
                    }
                }
            }
            c2 {
                c2c1 {
                    m1 {
                        prop = "c2c1m1"
                    }
                }
            }
        }

        then:
        parent.c1.c1c1.m1.prop == "c1c1m1"
        parent.c1.c1c1.m2.prop == "c1c1m2"
        parent.c1.c1c2.m1.prop == "c1c2m1"
        parent.c2.c2c1.m1.prop == "c2c1m1"
    }

    def "configure like method for object that doesn't support it produces error"() {
        given:
        def parent = instantiator.newInstance(Container, "top", "parent", { name1 ->
            instantiator.newInstance(Container, "parent", name1, { name2 ->
                [parent: name1, name: name2]
            })
        })

        when:
        parent.configure {
            c1 {
                m1 {
                    prop = "c1c1m1"
                    
                    // Should throw mme because map doesn't have this method
                    somethingThatDoesntExist {

                    }
                }
            }
        }


        then:
        def e = thrown(groovy.lang.MissingMethodException)
        e.method == "somethingThatDoesntExist"
        parent.c1.m1.prop == "c1c1m1"
        
        // make sure the somethingThatDoesntExist() call didn't resolve against any of the root containers, creating an entry
        parent.size() == 1
        parent.c1.size() == 1
    }

}
