/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.model

import org.gradle.api.Named
import org.gradle.model.internal.core.DefaultNodeInitializerRegistry
import org.gradle.model.internal.core.ModelCreators
import org.gradle.model.internal.fixture.ModelRegistryHelper
import org.gradle.model.internal.manage.schema.extract.ConstructableTypesRegistry
import org.gradle.model.internal.manage.schema.extract.DefaultModelSchemaStore
import org.gradle.model.internal.manage.schema.extract.InvalidManagedModelElementTypeException
import spock.lang.Specification

class ManagedNamedTest extends Specification {

    ConstructableTypesRegistry constructableTypesRegistry = Mock()
    def r = new ModelRegistryHelper()
    def schemaStore = DefaultModelSchemaStore.getInstance()
    def nodeInitializerRegistry = new DefaultNodeInitializerRegistry(schemaStore, constructableTypesRegistry)

    def "named struct has name name property populated"() {
        when:
        r.create(ModelCreators.of(r.path("foo"), nodeInitializerRegistry.getNodeInitializer(schemaStore.getSchema(NamedThingInterface))).descriptor(r.desc("foo")).build())

        then:
        r.realize("foo", NamedThingInterface).name == "foo"

        when:
        r.create(ModelCreators.of(r.path("bar"), nodeInitializerRegistry.getNodeInitializer(schemaStore.getSchema(NamedThingInterface))).descriptor(r.desc("bar")).build())

        then:
        r.realize("bar", NamedThingInterface).name == "bar"
    }

    @Managed
    static abstract class NonNamedThing {
        abstract String getName()

        abstract void setName(String name)
    }

    def "named struct does not have name populated if does not implement named"() {
        when:
        r.create(ModelCreators.of(r.path("foo"), nodeInitializerRegistry.getNodeInitializer(schemaStore.getSchema(NonNamedThing))).descriptor(r.desc("foo")).build())

        then:
        r.realize("foo", NonNamedThing).name == null
    }

    @Managed
    static abstract class NonNamedThingNoSetter {
        abstract String getName()
    }

    def "name requires setter if not named"() {
        when:
        schemaStore.getSchema(NonNamedThingNoSetter)

        then:
        thrown InvalidManagedModelElementTypeException
    }

    @Managed
    static abstract class NamedThingWithSetter implements Named {
        abstract String getName()

        abstract void setName(String name)
    }

    def "named cannot have setter"() {
        when:
        schemaStore.getSchema(NamedThingWithSetter)

        then:
        thrown InvalidManagedModelElementTypeException
    }

}
