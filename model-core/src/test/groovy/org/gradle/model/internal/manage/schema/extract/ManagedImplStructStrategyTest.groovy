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

package org.gradle.model.internal.manage.schema.extract

import org.gradle.model.Managed
import org.gradle.model.internal.manage.schema.CompositeSchema
import org.gradle.model.internal.manage.schema.ManagedImplSchema
import org.gradle.model.internal.manage.schema.ManagedImplStructSchema
import org.gradle.model.internal.manage.schema.StructSchema
import org.gradle.model.internal.type.ModelType
import org.gradle.model.internal.type.ModelTypes
import spock.lang.Specification

class ManagedImplStructStrategyTest extends Specification {
    def store = new DefaultModelSchemaStore(DefaultModelSchemaExtractor.withDefaultStrategies())

    def "assembles schema for @Managed type"() {
        expect:
        def schema = store.getSchema(ModelType.of(SomeType))
        schema instanceof ManagedImplStructSchema
        schema instanceof ManagedImplSchema
        schema instanceof CompositeSchema
        schema instanceof StructSchema
        schema.propertyNames == ['readOnlyString', 'strings'] as SortedSet
        schema.properties*.name == ['readOnlyString', 'strings']
        schema.getProperty('readOnlyString').type == ModelType.of(String)
        schema.getProperty('readOnlyString').schema == store.getSchema(ModelType.of(String))
        schema.getProperty('strings').type == ModelTypes.list(ModelType.of(String))
        schema.getProperty('strings').schema == store.getSchema(ModelTypes.list(ModelType.of(String)))
    }

    def "assembles schema for @Managed type that references itself"() {
        expect:
        def schema = store.getSchema(ModelType.of(Person))
        schema instanceof ManagedImplStructSchema
        schema.propertyNames == ['parent'] as SortedSet
        schema.properties*.name == ['parent']
        schema.getProperty('parent').type == schema.type
        schema.getProperty('parent').schema == schema
    }

    @Managed
    interface SomeType {
        String getReadOnlyString()

        List<String> getStrings()
        void setStrings(List<String> strings)
    }

    @Managed
    interface Person {
        Person getParent()
    }
}
