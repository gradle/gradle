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

import groovy.transform.SelfType
import spock.lang.Issue

@SelfType(AbstractDomainObjectContainerIntegrationTest)
trait AbstractNamedDomainObjectContainerIntegrationTest {
    String getContainerStringRepresentation() {
        return "SomeType container"
    }

    String makeContainer() {
        return "project.objects.domainObjectContainer(SomeType)"
    }

    static String getContainerType() {
        return "NamedDomainObjectContainer"
    }

    def setup() {
        settingsFile << """
            class SomeType implements Named {
                final String name

                SomeType(String name) {
                    this.name = name
                }
            }
        """
    }
}


class NamedDomainObjectContainerIntegrationTest extends AbstractDomainObjectContainerIntegrationTest implements AbstractNamedDomainObjectContainerIntegrationTest {
    def "can mutate the task container from named container"() {
        buildFile """
            testContainer.configureEach {
                tasks.create(it.name)
            }
            toBeRealized.get()

            task verify {
                def realizedPresent = provider { tasks.findByName("realized") != null }
                def toBeRealizedPresent = provider { tasks.findByName("toBeRealized") != null }
                doLast {
                    assert realizedPresent.get()
                    assert toBeRealizedPresent.get()
                }
            }
        """

        expect:
        succeeds "verify"
    }

    def "chained lookup of testContainer.withType.matching"() {
        buildFile << """
            testContainer.withType(testContainer.type).matching({ it.name.endsWith("foo") }).all { element ->
                assert element.name in ['foo', 'barfoo']
            }

            testContainer.register("foo")
            testContainer.register("bar")
            testContainer.register("foobar")
            testContainer.register("barfoo")
        """
        expect:
        succeeds "help"
    }

    @Issue("https://github.com/gradle/gradle/issues/9446")
    def "chained lookup of testContainer.matching.withType"() {
        buildFile << """
            testContainer.matching({ it.name.endsWith("foo") }).withType(testContainer.type).all { element ->
                assert element.name in ['foo', 'barfoo']
            }

            testContainer.register("foo")
            testContainer.register("bar")
            testContainer.register("foobar")
            testContainer.register("barfoo")
        """
        expect:
        succeeds "help"
    }

    def "name based filtering"() {
        buildFile << """
            def onlyFoo = testContainer.named({ it.endsWith("foo") })

            assert onlyFoo.isEmpty()

            testContainer.register("foo")
            testContainer.register("bar")
            testContainer.register("foobar")
            testContainer.register("barfoo")

            assert onlyFoo*.name == ["barfoo", "foo"]
        """
        expect:
        succeeds "help"
    }

    def "failing to create a domain object produces a nice message"() {
        buildFile << """
            testContainer.register("foo") {
                throw new RuntimeException("fail creation")
            }.get()
        """

        when:
        fails "help"

        then:

        failure.assertHasFailure("A problem occurred evaluating root project 'root'.") {
            it.assertHasFirstCause("Could not create domain object 'foo' (SomeType) in SomeType container")
            it.assertHasCause("fail creation")
        }
    }
}
