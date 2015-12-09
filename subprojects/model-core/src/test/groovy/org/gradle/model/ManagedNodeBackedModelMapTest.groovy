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
import org.gradle.model.internal.core.NodeInitializerContext
import org.gradle.model.internal.manage.schema.extract.InvalidManagedModelElementTypeException

import static org.gradle.util.TextUtil.normaliseLineSeparators

class ManagedNodeBackedModelMapTest extends NodeBackedModelMapSpec<NamedThingInterface, SpecialNamedThingInterface> {

    Class<NamedThingInterface> itemClass = NamedThingInterface
    Class<SpecialNamedThingInterface> specialItemClass = SpecialNamedThingInterface.class

    def setup() {
        registry.register(ModelRegistrations.of(path, nodeInitializerRegistry.getNodeInitializer(NodeInitializerContext.forType(modelMapType))).descriptor("creator").build())
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
    abstract static class Invalid<T> implements SpecialNamedThingInterface {

    }

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

    def "reasonable error message when creating a non-constructable type"() {
        when:
        mutate { create("foo", List) }
        realize()

        then:
        ModelRuleExecutionException e = thrown()
        normaliseLineSeparators(e.cause.message).contains('''A model element of type: 'java.util.List' can not be constructed.
It must be one of:
    - A managed type (annotated with @Managed)''')
    }

}
