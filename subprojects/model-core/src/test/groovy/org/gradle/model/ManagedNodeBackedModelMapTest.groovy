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
import org.gradle.model.internal.manage.binding.InvalidManagedTypeException

import static org.gradle.model.ModelTypeTesting.fullyQualifiedNameOf
import static org.gradle.model.internal.core.NodeInitializerContext.forType

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
        e.cause instanceof InvalidManagedTypeException
        e.cause.message == """Type ${fullyQualifiedNameOf(Invalid)} is not a valid managed type:
- Cannot be a parameterized type."""
    }
}

