/*
 * Copyright 2018 the original author or authors.
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

import javax.inject.Inject


class ObjectExtensionServiceInjectionIntegrationTest extends AbstractIntegrationSpec {
    // Document current behaviour
    def "can create instance of extension with multiple constructors without @Inject annotation"() {
        buildFile << """
            class Thing {
                String a
                String b
                Thing(String a, String b = a) {
                    this.a = a
                    this.b = b
                }
            }
            
            def one = extensions.create("one", Thing, "a")
            def two = extensions.create("two", Thing, "a", "b")
            
            assert one.a == "a"
            assert one.b == "a"

            assert two.a == "a"
            assert two.b == "b"
        """

        expect:
        succeeds()
    }

    def "fails when too few construction parameters provided"() {
        buildFile << """
            class Thing {
                Thing(String a, String b) {
                }
            }
            
            extensions.create("one", Thing, "a")
        """

        expect:
        fails()
        failure.assertHasCause("Could not create an instance of type Thing.")
        failure.assertHasCause("Unable to determine constructor argument #2: missing parameter of class java.lang.String, or no service of type class java.lang.String")
    }

    def "fails when interface provided"() {
        buildFile << """
            interface Thing {
            }
            
            extensions.create("one", Thing, "a")
        """

        expect:
        fails()
        failure.assertHasCause("Could not create an instance of type Thing.")
        failure.assertHasCause("Interface Thing is not a class.")
    }

    def "fails when non-static inner class provided"() {
        buildFile << """
            class Things {
                class Thing { }
            }
            
            extensions.create("one", Things.Thing, "a")
        """

        expect:
        fails()
        failure.assertHasCause("Could not create an instance of type Things\$Thing.")
        failure.assertHasCause("Class Things\$Thing is a non-static inner class.")
    }

    def "fails when mismatched construction parameters provided"() {
        buildFile << """
            class Thing {
                Thing(String a, String b) {
                }
            }
            
            extensions.create("one", Thing, "a", 12)
        """

        expect:
        fails()
        failure.assertHasCause("Could not create an instance of type Thing.")
        failure.assertHasCause("Unable to determine constructor argument #2: value 12 not assignable to class java.lang.String")
    }

    def "fails when mismatched construction parameters provided when there are multiple constructors"() {
        buildFile << """
            class Thing {
                Thing(String a, String b) {
                }
                Thing(String a, boolean b) {
                }
            }
            
            extensions.create("one", Thing, "a", 12)
        """

        expect:
        fails()
        failure.assertHasCause("Could not create an instance of type Thing.")
        failure.assertHasCause("No constructors of class Thing match parameters: [a, 12]")
    }

    def "fails when constructor is ambiguous"() {
        buildFile << """
            class Thing {
                Thing(String a, String b, ObjectFactory f) {
                }
                Thing(String a, String b, ProjectLayout p) {
                }
            }
            
            extensions.create("one", Thing, "a", "b")
        """

        expect:
        fails()
        failure.assertHasCause("Could not create an instance of type Thing.")
        failure.assertHasCause("Multiple constructors of class Thing match parameters: [a, b]")
    }

    def "fails when too many construction parameters provided"() {
        buildFile << """
            class Thing {
                Thing(String a, String b) {
                }
            }
            
            extensions.create("one", Thing, "a", "b", "c")
        """

        expect:
        fails()
        failure.assertHasCause("Could not create an instance of type Thing.")
        failure.assertHasCause("Too many parameters provided for constructor for class Thing. Expected 2, received 3.")
    }

    // Document current behaviour
    def "can inject service and configuration as constructor args when constructor not annotated with @Inject"() {
        buildFile << """
            class Thing {
                Thing(String a, ObjectFactory objects, int b) {
                    assert a == "a"
                    assert b == 12
                    assert objects != null
                }
            }
            
            extensions.create("one", Thing, "a", 12)
        """

        expect:
        succeeds()
    }

    def "can inject service using getter"() {
        buildFile << """
            import ${Inject.name}

            class Thing {
                Thing(String a) {
                }

                @Inject
                ObjectFactory getObjects() { }
            }
            
            def e = extensions.create("one", Thing, "a")
            assert e.objects != null
        """

        expect:
        succeeds()
    }

    def "can inject service using abstract getter"() {
        buildFile << """
            import ${Inject.name}

            abstract class Thing {
                Thing(String a) {
                }

                @Inject
                abstract ObjectFactory getObjects()
            }
            
            def e = extensions.create("one", Thing, "a")
            assert e.objects != null
        """

        expect:
        succeeds()
    }

    def "can use getter injected services from constructor"() {
        buildFile << """
            import ${Inject.name}

            class Thing {
                Thing(String a) {
                    objects.property(String).set(a)
                }

                @Inject
                ObjectFactory getObjects() { }
            }
            
            def e = extensions.create("one", Thing, "a")
            assert e.objects != null
        """

        expect:
        succeeds()
    }
}
