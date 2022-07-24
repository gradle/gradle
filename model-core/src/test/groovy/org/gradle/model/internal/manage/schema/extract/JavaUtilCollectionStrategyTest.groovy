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

import org.gradle.model.internal.manage.schema.CompositeSchema
import org.gradle.model.internal.manage.schema.ManagedImplSchema
import org.gradle.model.internal.manage.schema.ScalarCollectionSchema
import org.gradle.model.internal.manage.schema.StructSchema
import org.gradle.model.internal.manage.schema.UnmanagedImplStructSchema
import org.gradle.model.internal.type.ModelType
import org.gradle.model.internal.type.ModelTypes
import spock.lang.Specification

class JavaUtilCollectionStrategyTest extends Specification {
    def store = new DefaultModelSchemaStore(DefaultModelSchemaExtractor.withDefaultStrategies())

    def "assembles schema for a Set of scalar type"() {
        expect:
        def schema = store.getSchema(ModelTypes.set(ModelType.of(String)))
        schema instanceof ScalarCollectionSchema
        schema instanceof ManagedImplSchema
        !(schema instanceof CompositeSchema)
        !(schema instanceof StructSchema)
        schema.elementType == ModelType.of(String)
        schema.elementTypeSchema == store.getSchema(ModelType.of(String))
    }

    def "assembles schema for a List of scalar type"() {
        expect:
        def schema = store.getSchema(ModelTypes.list(ModelType.of(String)))
        schema instanceof ScalarCollectionSchema
        schema instanceof ManagedImplSchema
        !(schema instanceof CompositeSchema)
        !(schema instanceof StructSchema)
        schema.elementType == ModelType.of(String)
        schema.elementTypeSchema == store.getSchema(ModelType.of(String))
    }

    def "assembles schema for List of non-scalar type"() {
        expect:
        def schema = store.getSchema(ModelTypes.list(ModelType.of(Runnable)))
        schema instanceof UnmanagedImplStructSchema
        !(schema instanceof ManagedImplSchema)
        !(schema instanceof CompositeSchema)
        schema instanceof StructSchema
        !schema.annotated
        schema.propertyNames.empty
    }

}
