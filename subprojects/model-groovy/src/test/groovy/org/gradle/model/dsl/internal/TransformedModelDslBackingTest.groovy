/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.model.dsl.internal

import org.gradle.api.Transformer
import org.gradle.model.InvalidModelRuleDeclarationException
import org.gradle.model.Managed
import org.gradle.model.dsl.internal.inputs.RuleInputAccessBacking
import org.gradle.model.dsl.internal.transform.InputReferences
import org.gradle.model.dsl.internal.transform.SourceLocation
import org.gradle.model.internal.core.DefaultNodeInitializerRegistry
import org.gradle.model.internal.core.ModelCreators
import org.gradle.model.internal.core.ModelPath
import org.gradle.model.internal.core.ModelReference
import org.gradle.model.internal.core.rule.describe.SimpleModelRuleDescriptor
import org.gradle.model.internal.manage.schema.extract.DefaultConstructableTypesRegistry
import org.gradle.model.internal.manage.schema.extract.DefaultModelSchemaStore
import org.gradle.model.internal.registry.DefaultModelRegistry
import org.gradle.model.internal.type.ModelType
import spock.lang.Specification

class TransformedModelDslBackingTest extends Specification {

    def modelRegistry = new DefaultModelRegistry(null)
    def referenceExtractor = Mock(Transformer)
    def locationExtractor = Mock(Transformer)
    def schemaStore = DefaultModelSchemaStore.instance
    def nodeInitializerRegistry = new DefaultNodeInitializerRegistry(DefaultModelSchemaStore.instance, new DefaultConstructableTypesRegistry())
    def modelDsl = new TransformedModelDslBacking(getModelRegistry(), schemaStore, nodeInitializerRegistry, referenceExtractor, locationExtractor)

    void register(String pathString, Object element) {
        modelRegistry.create(ModelCreators.bridgedInstance(ModelReference.of(pathString, element.class), element).descriptor("register").build())
    }

    def "can add rules via dsl"() {
        given:
        register("foo", [])
        referenceExtractor.transform(_) >> new InputReferences()
        locationExtractor.transform(_) >> Mock(SourceLocation) {
            asDescriptor(_) >> new SimpleModelRuleDescriptor("foo")
        }

        when:
        modelDsl.configure("foo") {
            add 1
        }

        then:
        modelRegistry.realize(ModelPath.path("foo"), ModelType.of(List)) == [1]
    }

    @Managed
    static abstract class Thing {
        abstract String getValue()

        abstract void setValue(String value)
    }

    def "can add creator via dsl"() {
        given:
        referenceExtractor.transform(_) >> new InputReferences()
        locationExtractor.transform(_) >> Mock(SourceLocation) {
            asDescriptor(_) >> new SimpleModelRuleDescriptor("foo")
        }

        when:
        modelDsl.create("foo", Thing) {
            value = "set"
        }

        then:
        modelRegistry.realize(ModelPath.path("foo"), ModelType.of(Thing)).value == "set"
    }

    def "can only create top level"() {
        given:
        referenceExtractor.transform(_) >> new InputReferences()
        locationExtractor.transform(_) >> Mock(SourceLocation) {
            asDescriptor(_) >> new SimpleModelRuleDescriptor("foo")
        }

        when:
        modelDsl.create("foo.bar", Thing) {
            value = "set"
        }

        then:
        thrown InvalidModelRuleDeclarationException
    }

    def "can registers extracted references"() {
        given:
        def inputs = new InputReferences()
        inputs.absolutePath("value", 123)
        register("foo", [])
        register("value", "123")
        referenceExtractor.transform(_) >> inputs
        locationExtractor.transform(_) >> Mock(SourceLocation) {
            asDescriptor(_) >> new SimpleModelRuleDescriptor("foo")
        }

        when:
        modelDsl.with {
            configure("foo") {
                // this is effectively what it gets transformed to
                add RuleInputAccessBacking.access.input("value")
            }
        }

        then:
        modelRegistry.realize(ModelPath.path("foo"), ModelType.of(List)) == ["123"]
    }
}

