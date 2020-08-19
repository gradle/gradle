/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.model.managed

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.UnsupportedWithConfigurationCache

@UnsupportedWithConfigurationCache(because = "software model")
class CyclicalManagedTypeIntegrationTest extends AbstractIntegrationSpec {

    def "managed types can have cyclical managed type references"() {
        when:
        buildScript '''
            @Managed
            interface Parent {
                String getName()
                void setName(String name)

                Child getChild()
            }

            @Managed
            interface Child {
                Parent getParent()
                void setParent(Parent parent)
            }

            class RulePlugin extends RuleSource {
                @Model
                void parent(Parent parent) {
                    parent.name = "parent"
                    parent.child.parent = parent
                }

                @Mutate
                void addEchoTask(ModelMap<Task> tasks, Parent parent) {
                    tasks.create("echo") {
                        it.doLast {
                            println "name: $parent.child.parent.name"
                        }
                    }
                }
            }

            apply type: RulePlugin
        '''

        then:
        succeeds "echo"

        and:
        output.contains("name: parent")
    }

    def "managed types can have cyclical managed type references where more than two types constitute the cycle"() {
        when:
        buildScript '''
            @Managed
            interface A {
                String getName()
                void setName(String name)

                B getB()
            }

            @Managed
            interface B {
                C getC()
            }

            @Managed
            interface C {
                A getA()
                void setA(A a)
            }

            class RulePlugin extends RuleSource {
                @Model
                void a(A a) {
                    a.name = "a"
                    a.b.c.a = a
                }

                @Mutate
                void addEchoTask(ModelMap<Task> tasks, A a) {
                    tasks.create("echo") {
                        it.doLast {
                            println "name: $a.b.c.a.name"
                        }
                    }
                }
            }

            apply type: RulePlugin
        '''

        then:
        succeeds "echo"

        and:
        output.contains("name: a")
    }
}
