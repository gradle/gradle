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
import spock.lang.Issue

class CallablePropertyIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        settingsFile << "rootProject.name='callable-property-test'"
    }

    def "can call a property on a NDOC"() {
        when:
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

            assert container.foo.prop.counter == 3
        """

        then:
        succeeds 'help'
    }

    def "cannot call a property on a NDOC with no call method (in #context)"() {
        given:
        buildFile << """
            class NonCallableItem {
            }

            abstract class NamedThing implements Named {
                private final NonCallableItem prop = new NonCallableItem()

                NonCallableItem getProp() {
                    return prop
                }
            }

            def container = objects.domainObjectContainer(NamedThing)
            container.register("foo")

            ${code}
        """

        when:
        fails 'help'

        then:
        failureHasCause("Could not find method prop() for arguments [] on object of type NamedThing.")

        where:
        context | code
        "Top-level call" | "container.foo.prop()"
        "Inside Project.configure" | "configure(container.foo) { prop() }"
        "Inside NDOC.configure" | "container.configure { foo { prop() } }"
    }

    @Issue('https://github.com/gradle/gradle/issues/23111')
    def "can configure dynamic property without call method"() {
        buildFile << """
            task test {
                doLast {
                    ant { echo(message: 'hello world!') }
                }
            }
        """

        expect:
        args('--stacktrace')
        succeeds("test")
    }
}
