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

import org.gradle.model.internal.core.ModelRegistrations
import org.gradle.model.internal.core.ModelRuleExecutionException
import org.gradle.model.internal.core.NodeInitializer
import org.gradle.model.internal.core.NodeInitializerRegistry
import org.gradle.model.internal.manage.schema.extract.DefaultConstructibleTypesRegistry
import org.gradle.model.internal.manage.schema.extract.InvalidManagedModelElementTypeException
import org.gradle.model.internal.type.ModelType

import static org.gradle.model.internal.core.NodeInitializerContext.forType
import static org.gradle.util.TextUtil.normaliseLineSeparators

class ManagedNodeBackedModelMapTest extends NodeBackedModelMapSpec<NamedThingInterface, SpecialNamedThingInterface> {

    Class<NamedThingInterface> itemClass = NamedThingInterface
    Class<SpecialNamedThingInterface> specialItemClass = SpecialNamedThingInterface.class

    def setup() {
        registry.register(ModelRegistrations.of(path, nodeInitializerRegistry.getNodeInitializer(forType(modelMapType))).descriptor("creator").build())
    }

    def "cannot read child in mutative method"() {
        when:
        mutate {
            create('foo')
        }
        mutate {
            get('foo')
        }
        realizeAsModelMap()

        then:
        def e = thrown ModelRuleExecutionException
        e.cause instanceof WriteOnlyModelViewException
    }

    def "cannot read size in mutative method"() {
        when:
        mutate {
            size()
        }
        realizeAsModelMap()

        then:
        def e = thrown ModelRuleExecutionException
        e.cause instanceof WriteOnlyModelViewException
    }

    def "can put existing unmanaged instance"() {
        when:
        mutate {
            put("foo", "bar")
        }

        then:
        registry.realize("map.foo", String) == "bar"
    }

    @Managed
    abstract static class Invalid<T> implements SpecialNamedThingInterface {}

    def "cannot create invalid subtype"() {
        when:
        mutate {
            create("foo", Invalid)
        }
        realizeAsModelMap()

        then:
        def e = thrown ModelRuleExecutionException
        e.cause instanceof InvalidManagedModelElementTypeException
        e.cause.message == "Invalid managed model type $Invalid.name: cannot be a parameterized type."
    }

    abstract class ConstructibleNamedThing implements NamedThingInterface {}
    abstract class NonConstructibleNamedThing implements NamedThingInterface {}

    def "reasonable error message when creating a non-constructible type"() {
        registry.mutate(NodeInitializerRegistry) { nodeInitializerRegistry ->
            def constructibleTypesRegistry = new DefaultConstructibleTypesRegistry()
            // This should not show up in the error message on account of not being a subtype of NamedThingInterface
            constructibleTypesRegistry.registerConstructibleType(ModelType.of(Serializable), Mock(NodeInitializer))
            // This should show up in the error message
            constructibleTypesRegistry.registerConstructibleType(ModelType.of(ConstructibleNamedThing), Mock(NodeInitializer))
            nodeInitializerRegistry.registerStrategy(constructibleTypesRegistry)
        }
        when:
        mutate {
            create("foo", NonConstructibleNamedThing)
        }
        realize()

        then:
        ModelRuleExecutionException e = thrown()
        normaliseLineSeparators(e.cause.message) == """A model element of type: '$NonConstructibleNamedThing.name' can not be constructed.
It must be one of:
    - A managed type (annotated with @Managed)
    - or a type which Gradle is capable of constructing:
        - $ConstructibleNamedThing.name"""
    }

}
