/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.internal.extensibility


import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class CallablePropertyIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        settingsFile << "rootProject.name='callable-property-test'"
    }

    def "can call a property on a NDOC"() {
        given:
        buildFile << """
            class CallableItem {
                def counter = 0
                void call() {
                    counter++
                }
            }

            abstract class NamedThing implements Named {
                private final CallableItem prop = new CallableItem()

                CallableItem getProp() {
                    return prop
                }
            }

            def container = objects.domainObjectContainer(NamedThing)
            container.register("foo")

            container.foo.prop()
            configure(container.foo) {
                prop()
            }
            container.configure {
                foo {
                    prop()
                }
            }

            println("The property was called " + container.foo.prop.counter + " times")
        """

        when:
        succeeds 'help'

        then:
        outputContains("The property was called 3 times")
    }
}
