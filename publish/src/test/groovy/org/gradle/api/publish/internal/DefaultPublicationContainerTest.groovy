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

import org.gradle.api.InvalidUserDataException
import org.gradle.api.NamedDomainObjectFactory
import org.gradle.api.UnknownDomainObjectException
import org.gradle.api.internal.CollectionCallbackActionDecorator
import org.gradle.api.publish.Publication
import org.gradle.internal.reflect.Instantiator
import org.gradle.util.TestUtil
import spock.lang.Specification

class DefaultPublicationContainerTest extends Specification {

    Instantiator instantiator = TestUtil.instantiatorFactory().decorateLenient()
    DefaultPublicationContainer container = instantiator.newInstance(DefaultPublicationContainer, instantiator, CollectionCallbackActionDecorator.NOOP)

    def "exception is thrown on unknown access"() {
        given:
        container.add(publication("foo"))

        expect:
        container.foo instanceof Publication

        when:
        container.getByName("notHere")

        then:
        def e = thrown(UnknownDomainObjectException)
        e.message == "Publication with name 'notHere' not found."
    }

    def "can add and configure publication with API"() {
        given:
        Publication pub = publication("test")
        NamedDomainObjectFactory<Publication> factory = Mock()
        container.registerFactory(Publication, factory)

        when:
        container.create("name", Publication) {
            value = 2
        }

        then:
        1 * factory.create("name") >> pub

        and:
        container.getByName("test") == pub
        pub.value == 2
    }

    def "cannot add multiple publications with same name"() {
        given:
        NamedDomainObjectFactory<TestPublication> factory = Mock()
        container.registerFactory(TestPublication, factory)

        when:
        container.create("publication_name", TestPublication)

        then:
        1 * factory.create("publication_name") >> publication("test")

        when:
        container.create("publication_name", TestPublication)

        then:
        1 * factory.create("publication_name") >> publication("test")

        and:
        def t = thrown InvalidUserDataException
        t.message == "Publication with name 'test' added multiple times"
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

        @Override
        void withoutBuildIdentifier() {
            // No-op
        }

        @Override
        void withBuildIdentifier() {
            // No-op
        }
    }
}
