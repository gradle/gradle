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

class ObjectFactoryExtensionsIntegrationTest extends AbstractIntegrationSpec {
    def "extension container of created DSL object can create type with non-annotated constructor"() {
        given:
        buildFile """
        class Thing {
            String value
            Thing(String value) {
                this.value = value
            }
        }

        class MyExtensible {}

        def myExtensible = project.objects.newInstance(MyExtensible)
        assert myExtensible instanceof ExtensionAware

        myExtensible.extensions.create("thing", Thing, "bar")
        assert myExtensible.extensions.thing.value == "bar"
"""

        expect:
        succeeds "help"
    }

    def "extension container of created DSL object can create type with multiple constructors not annotated"() {
        given:
        buildFile """
        class Thing {
            Thing() {}
            Thing(String foo) {}
        }

        class MyExtensible {}

        task createExtension {
            def objects = project.objects
            doLast {
                def myExtensible = objects.newInstance(MyExtensible)
                assert myExtensible instanceof ExtensionAware

                myExtensible.extensions.create("thing", Thing)
            }
        }
"""

        expect:
        succeeds "createExtension"
    }

    def "creation via extension is lenient where injected ObjectFactory is not"() {
        buildFile """
            class Thing {
                String name

                Thing(String name) {
                    this.name = name
                }
            }

            class Outer {
                Thing thing

                @javax.inject.Inject
                ObjectFactory getObjects() { null }
            }

            def outer = project.objects.newInstance(Outer)
            task createExtensionThing {
                doLast {
                    outer.extensions.create("extensionThing", Thing, "foo")
                    assert outer.extensions.extensionThing.name == "foo"
                }
            }

            task createNestedThing {
                doLast {
                    outer.objects.newInstance(Thing, "bar")
                }
            }
"""

        expect:
        succeeds "createExtensionThing"

        and:
        fails "createNestedThing"
    }

}
