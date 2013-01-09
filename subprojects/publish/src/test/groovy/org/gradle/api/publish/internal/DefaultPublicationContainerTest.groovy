/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.publish.internal
import org.gradle.api.internal.ThreadGlobalInstantiator
import org.gradle.api.publish.Publication
import org.gradle.api.publish.UnknownPublicationException
import org.gradle.internal.reflect.Instantiator
import spock.lang.Specification

class DefaultPublicationContainerTest extends Specification {

    Instantiator instantiator = ThreadGlobalInstantiator.getOrCreate()
    GroovyPublicationContainer container = instantiator.newInstance(GroovyPublicationContainer, instantiator)

    def "right exception is thrown on unknown access"() {
        given:
        container.add(publication("foo"))

        expect:
        container.foo instanceof Publication

        when:
        container.getByName("notHere")

        then:
        def e = thrown(UnknownPublicationException)
        e.message == "Publication with name 'notHere' not found"
    }

    def "can add and configure publication with API"() {
        given:
        Publication pub = publication("test")
        PublicationFactory factory = Mock()
        container.registerFactory(Publication, factory)

        when:
        container.add("name", Publication) {
            value = 2
        }

        then:
        1 * factory.create("name") >> pub

        and:
        container.getByName("test") == pub
        pub.value == 2
    }

    def "can add publication with DSL"() {
        given:
        Publication testPub = publication("test")
        PublicationFactory factory = Mock()
        container.registerFactory(Publication, factory)

        when:
        container.publication_name(Publication)

        then:
        1 * factory.create("publication_name") >> testPub

        and:
        container.getByName("test") == testPub
    }

    def "can add and configure publication with DSL"() {
        given:
        TestPublication testPub = publication("test")
        PublicationFactory factory = Mock()
        container.registerFactory(TestPublication, factory)

        when:
        container.publication_name(TestPublication) {
            value = 2
        }

        then:
        1 * factory.create("publication_name") >> testPub

        and:
        container.getByName("test") == testPub
        testPub.value == 2
    }

    TestPublication publication(String name) {
        new TestPublication(name)
    }

    class TestPublication implements Publication {
        def value = 0
        String name

        TestPublication(name) {
            this.name = name
        }
    }
}
