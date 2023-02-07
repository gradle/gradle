/*
 * Copyright 2023 the original author or authors.
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

import org.gradle.api.Named
import org.gradle.api.NamedDomainObjectFactory
import org.gradle.api.reflect.HasPublicType
import org.gradle.api.reflect.PublicType
import org.gradle.api.reflect.TypeOf
import org.gradle.configuration.internal.DefaultUserCodeApplicationContext
import org.gradle.configuration.internal.UserCodeApplicationContext
import org.gradle.internal.operations.TestBuildOperationExecutor
import org.gradle.util.TestUtil
import spock.lang.Specification

class DefaultPolymorphicDomainObjectContainerSchemaTest extends Specification {

    def container = createContainer()

    private DefaultPolymorphicDomainObjectContainer<Person> createContainer() {
        TestBuildOperationExecutor buildOperationExecutor = new TestBuildOperationExecutor()
        UserCodeApplicationContext userCodeApplicationContext = new DefaultUserCodeApplicationContext()
        CollectionCallbackActionDecorator callbackActionDecorator = new DefaultCollectionCallbackActionDecorator(buildOperationExecutor, userCodeApplicationContext)
        new DefaultPolymorphicDomainObjectContainer<Person>(Person, TestUtil.instantiatorFactory().decorateLenient(), TestUtil.instantiatorFactory().decorateLenient(), callbackActionDecorator)
    }

    interface Person extends Named {}

    static abstract class AbstractPerson implements Person {
        String name

        String toString() { name }


        boolean equals(DefaultPerson other) {
            name == other.name
        }

        int hashCode() {
            name.hashCode()
        }
    }

    static class DefaultPerson extends AbstractPerson {
    }

    @PublicType(MarkedPerson)
    static abstract class AbstractMarkedIntrovert extends AbstractPerson {}

    static class SecretlyMarkedIntrovert extends AbstractMarkedIntrovert {
        SecretlyMarkedIntrovert(String name) {
            this.name = name
        }
    }

    @PublicType(MarkedPerson)
    static class MarkedIntrovert extends AbstractPerson {
        MarkedIntrovert(String name) {
            this.name = name
        }
    }

    static class OfficialIntrovert extends AbstractPerson implements HasPublicType {
        OfficialIntrovert(String name) {
            this.name = name
        }

        @Override
        TypeOf<?> getPublicType() {
            return TypeOf.typeOf(MarkedPerson.class)
        }
    }

    interface MarkedPerson extends Person {}

    @SuppressWarnings('ConfigurationAvoidance')
    def "can extract schema from container that mixes register, create and add"() {
        given:
        container.registerFactory(Person, { new DefaultPerson(name: it) } as NamedDomainObjectFactory)
        container.registerFactory(MarkedIntrovert, { new MarkedIntrovert(it) } as NamedDomainObjectFactory)
        container.registerFactory(OfficialIntrovert, { new OfficialIntrovert(it) } as NamedDomainObjectFactory)
        container.registerFactory(SecretlyMarkedIntrovert, { new SecretlyMarkedIntrovert(it) } as NamedDomainObjectFactory)

        def expectedSchema = [
            mike: Person,
            fred: Person,
            edward: MarkedPerson,
            thomas: MarkedPerson,
            jameson: Person,
            kate: Person,
            bob: Person,
            jill: Person,
            alice: MarkedPerson,
            mary: MarkedPerson,
            john: Person,
            kevin: Person,
            skippy: MarkedPerson,
            shaun: MarkedPerson
        ]

        when:
        container.register("mike")
        container.register("fred", Person)
        container.register("edward", MarkedIntrovert)
        container.register("thomas", SecretlyMarkedIntrovert)
        container.register("jameson", OfficialIntrovert)
        container.create("kate")
        container.create("bob", Person)
        container.create("jill", OfficialIntrovert)
        container.create("alice", MarkedIntrovert)
        container.create("mary", SecretlyMarkedIntrovert)
        container.add(new DefaultPerson(name: "john"))
        container.add(new OfficialIntrovert("kevin"))
        container.add(new MarkedIntrovert("skippy"))
        container.add(new SecretlyMarkedIntrovert("shaun"))

        then:
        assertSchemaIs(expectedSchema)

        when: "realizing pending elements"
        container.getByName("mike")
        container.getByName("fred")
        container.getByName("edward")
        container.getByName("jameson")

        then: "schema is (almost) the same"
        assertSchemaIs(expectedSchema)
    }

    private void assertSchemaIs(Map<String, Class<Person>> expectedSchema) {
        def actualSchema = container.collectionSchema
        Map<String, Class<Person>> actualSchemaMap = actualSchema.elements.collectEntries { schema ->
            [schema.name, schema.publicType.getConcreteClass()]
        }.sort()
        def expectedSchemaMap = expectedSchema.sort()
        assert expectedSchemaMap == actualSchemaMap
    }
}
