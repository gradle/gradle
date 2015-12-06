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

    def "can query collection size"() {
        when:
        mutate {
            create("a")
            create("b")
        }

        then:
        realizeAsModelMap().size() == 2
        !realizeAsModelMap().isEmpty()
    }

    def "can query filtered collection size"() {
        when:
        mutate {
            create("a")
            create("b", SpecialNamedThingInterface)
        }

        then:
        with(realizeAsModelMap()) {
            assert withType(SpecialNamedThingInterface).size() == 1
            assert withType(Special).size() == 1
            assert withType(NamedThingInterface).size() == 2
            assert withType(String).size() == 0

            assert !withType(SpecialNamedThingInterface).isEmpty()
            assert withType(String).isEmpty()
        }
    }

    def "can query collection membership"() {
        when:
        mutate {
            create("a")
            create("b")
        }

        then:
        realizeAsModelMap().containsKey("a")
        realizeAsModelMap().containsKey("b")
        !realizeAsModelMap().containsKey("c")
    }

    def "can query filtered collection membership"() {
        when:
        mutate {
            create("a")
            create("b", SpecialNamedThingInterface)
        }

        then:
        with(realizeAsModelMap()) {
            withType(SpecialNamedThingInterface).containsKey("b")
            withType(Object).containsKey("a")
            withType(NamedThingInterface).containsKey("a")
            !withType(SpecialNamedThingInterface).containsKey("a")
            !withType(Special).containsKey("a")
            !withType(String).containsKey("a")

            withType(Object).containsKey("b")
            withType(NamedThingInterface).containsKey("b")
            withType(SpecialNamedThingInterface).containsKey("b")
            withType(Special).containsKey("b")
            !withType(String).containsKey("b")
        }
    }

    def "can query collection keys"() {
        when:
        mutate {
            create("a")
            create("b")
        }

        then:
        realizeAsModelMap().keySet() as List == ["a", "b"]
    }

    def "can query filtered collection keys"() {
        when:
        mutate {
            create("b", SpecialNamedThingInterface)
            create("a")
        }

        then:
        with(realizeAsModelMap()) {
            assert withType(Special).keySet() as List == ["b"]
            assert withType(NamedThingInterface).keySet() as List == ["a", "b"]
            assert withType(SpecialNamedThingInterface).keySet() as List == ["b"]
            assert withType(Special).keySet() as List == ["b"]
            assert withType(String).keySet().isEmpty()
        }
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
