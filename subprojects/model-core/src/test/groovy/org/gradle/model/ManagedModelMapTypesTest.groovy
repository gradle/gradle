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
import org.gradle.model.internal.manage.schema.extract.DefaultModelSchemaStore
import org.gradle.model.internal.manage.schema.extract.InvalidManagedModelElementTypeException
import org.gradle.model.internal.type.ModelType
import org.gradle.model.internal.type.ModelTypes
import spock.lang.Specification

class ManagedModelMapTypesTest extends Specification {

    def schemaStore = DefaultModelSchemaStore.instance

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
        e.message == "Invalid managed model type $ModelMap.name: type parameter of $ModelMap.name has to be specified."
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
        e.message.startsWith "Invalid managed model type $ModelMap.name<?>: type parameter of $ModelMap.name cannot be a wildcard."
    }

    def "cannot have map of map"() {
        when:
        schemaStore.getSchema(ModelTypes.modelMap(ModelTypes.modelMap(NamedThingInterface)))

        then:
        def e = thrown InvalidManagedModelElementTypeException
        e.message.endsWith "org.gradle.model.ModelMap cannot be used as type parameter of org.gradle.model.ModelMap."
    }

    @Managed
    abstract static class MutableName implements Named {
        abstract void setName(String name)
    }

    def "element cannot have setName"() {
        when:
        schemaStore.getSchema(ModelTypes.modelMap(MutableName))

        then:
        def e = thrown InvalidManagedModelElementTypeException
        e.message.startsWith "Invalid managed model type $MutableName.name: @Managed types implementing $Named.name must not declare a setter for the name property"
    }

    @Managed
    static abstract class WritableMapProperty {
        abstract void setMap(ModelMap<NamedThingInterface> map)

        abstract ModelMap<NamedThingInterface> getMap()
    }

    @Managed
    static abstract class WritableSetProperty {
        abstract void setSet(ModelSet<NamedThingInterface> set)

        abstract ModelSet<NamedThingInterface> getSet()
    }

    def "map cannot be writable"() {
        when:
        schemaStore.getSchema(ModelType.of(WritableMapProperty))

        then:
        def e = thrown InvalidManagedModelElementTypeException
        e.message == "Invalid managed model type org.gradle.model.ManagedModelMapTypesTest\$WritableMapProperty: property 'map' cannot have a setter (org.gradle.model.ModelMap<org.gradle.model.NamedThingInterface> properties must be read only)."


    }

    def "set cannot be writable"() {
        when:
        schemaStore.getSchema(ModelType.of(WritableSetProperty))

        then:
        def e = thrown InvalidManagedModelElementTypeException
        e.message == "Invalid managed model type org.gradle.model.ManagedModelMapTypesTest\$WritableSetProperty: property 'set' cannot have a setter (org.gradle.model.ModelSet<org.gradle.model.NamedThingInterface> properties must be read only)."
    }
}
