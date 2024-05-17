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

import org.gradle.model.internal.fixture.ProjectRegistrySpec
import org.gradle.model.internal.manage.schema.ModelMapSchema
import org.gradle.model.internal.manage.schema.extract.InvalidManagedModelElementTypeException
import org.gradle.model.internal.type.ModelType
import org.gradle.model.internal.type.ModelTypes

class ManagedModelMapTypesTest extends ProjectRegistrySpec {

    @Managed
    abstract static class ManagedThing {}

    def "type doesn't need to implement named"() {
        when:
        schemaStore.getSchema(ModelTypes.modelMap(ManagedThing))

        then:
        noExceptionThrown()
    }

    def "must have type param"() {
        when:
        schemaStore.getSchema(ModelType.of(ModelMap))

        then:
        def e = thrown InvalidManagedModelElementTypeException
        e.message == """Type $ModelMap.name is not a valid model element type:
- type parameter of $ModelMap.name has to be specified."""
    }

    @Managed
    abstract static class WildModelMap {
        abstract ModelMap<?> getMap()
    }

    def "must have concrete param"() {
        when:
        schemaStore.getSchema(ModelType.of(WildModelMap))

        then:
        def e = thrown InvalidManagedModelElementTypeException
        e.message.startsWith("""Type $ModelMap.name<?> is not a valid model element type:
- type parameter of $ModelMap.name cannot be a wildcard.""")
    }

    def "can have map of map"() {
        def type = ModelTypes.modelMap(ModelTypes.modelMap(NamedThingInterface))

        expect:
        schemaStore.getSchema(type) instanceof ModelMapSchema
    }
}
