/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.model.internal.registry

import org.gradle.api.Transformer
import org.gradle.internal.Factory
import org.gradle.model.internal.core.ModelNode
import org.gradle.model.internal.core.ModelReference
import org.gradle.model.internal.fixture.ModelRegistryHelper
import spock.lang.Specification

class ModelRegistryEphemeralNodeTest extends Specification {

    def registry = new ModelRegistryHelper()

    def "non-ephemeral model nodes are reused when registry is reset"() {
        when:
        def events = []
        registry.create("foo") { it.unmanaged(List, { [] } as Factory) }
        registry.mutate(List) {
            it.add "1"
            events.add "mutate"
        }

        then:
        registry.get("foo") == ["1"]
        registry.node("foo").state == ModelNode.State.GraphClosed
        events.size() == 1

        when:
        registry.prepareForReuse()

        then:
        registry.node("foo").state == ModelNode.State.GraphClosed
        registry.get("foo") == ["1"]
        events.size() == 1
    }

    def "ephemeral model nodes are discarded when registry is reset"() {
        when:
        def events = []
        registry.create("foo") { it.ephemeral(true).unmanaged(List, { [] } as Factory) }
        registry.mutate(List) {
            it.add "1"
            events.add "mutate"
        }

        then:
        registry.get("foo") == ["1"]
        registry.node("foo").state == ModelNode.State.GraphClosed
        events.size() == 1

        when:
        registry.prepareForReuse()

        then:
        registry.node("foo").state == ModelNode.State.Known
        registry.get("foo") == ["1"]
        events.size() == 2
    }

    def "dependents of ephemeral nodes are reset"() {
        when:
        def events = []
        registry.create("foo") { it.ephemeral(true).unmanaged(List, { [] } as Factory) }
        registry.create("bar") { it.ephemeral(false).unmanaged(Queue, { [] } as Factory) }
        registry.mutate(List) {
            it.add "1"
            events.add "mutate foo"
        }
        registry.mutate {
            it.path("bar").type(Queue).action(List) { bar, foo ->
                bar.add(foo.first())
                events.add "mutate bar"
            }
        }

        then:
        registry.get("bar") == ["1"]
        registry.node("foo").state == ModelNode.State.GraphClosed
        registry.node("bar").state == ModelNode.State.GraphClosed
        events == ["mutate foo", "mutate bar"]

        when:
        registry.prepareForReuse()

        then:
        registry.node("foo").state == ModelNode.State.Known
        registry.node("bar").state == ModelNode.State.Known
        registry.get("foo") == ["1"]
        events.size() == 3
        registry.node("bar").state == ModelNode.State.Known
        registry.get("bar") == ["1"]
        events.size() == 4
    }

    def "creator inputs for replaced ephemeral nodes are bound"() {
        when:
        registry.createOrReplace(registry.creator("foo") { it.ephemeral(true).unmanaged(List, ["old"])})
        registry.createOrReplace(registry.creator("bar") { it.ephemeral(true).unmanaged(StringBuilder, List) { List l -> new StringBuilder(l[0]) }})
        registry.mutate(List) {
            it.add "2"
        }
        registry.mutate {
            it.path("bar").type(StringBuilder).action(List) { bar, foo ->
                bar.append " bar"
            }
        }

        then:
        registry.get("bar").toString() == "old bar"
        registry.get("foo") == ["old", "2"]
        registry.node("foo").state == ModelNode.State.GraphClosed
        registry.node("bar").state == ModelNode.State.GraphClosed

        when:
        registry.prepareForReuse()
        registry.createOrReplace(registry.creator("foo") { it.ephemeral(true).unmanaged(List, ["new"])})
        registry.createOrReplace(registry.creator("bar") { it.ephemeral(true).unmanaged(StringBuilder, List) { List l -> new StringBuilder(l[0]) }})

        then:
        registry.node("foo").state == ModelNode.State.Known
        registry.node("bar").state == ModelNode.State.Known
        registry.get("foo") == ["new", "2"]

        registry.node("bar").state == ModelNode.State.Known
        registry.get("bar").toString() == "new bar"
    }

    static class Thing {
        String name
        String value
    }

    def "children of ephemeral collection nodes are implicitly ephemeral"() {
        when:
        registry
                .create("things") {
            it.ephemeral(true).modelMap(Thing)
        }
        .mutate(ModelRegistryHelper.instantiatorType(Thing)) {
            it.registerFactory(Thing) { new Thing(name: it) }
        }
        registry.mutateModelMap("things", Thing) {
            it.create("foo") {
                it.value = "1"
            }
            it.create("bar")
        }
        registry.mutate(ModelReference.of("things.bar", Thing)) {
            it.value = "2"
        }

        then:
        registry.get("things")
        registry.node("things.foo").state == ModelNode.State.GraphClosed
        registry.node("things.bar").state == ModelNode.State.GraphClosed

        when:
        registry.prepareForReuse()

        then:
        registry.node("things").state == ModelNode.State.Known
        registry.node("things.foo").state == ModelNode.State.Known
        registry.node("things.bar").state == ModelNode.State.Known
    }

    def "nodes with creators dependent on ephemeral nodes are reset"() {
            when:
            def val = "1"
            registry.create("foo") { it.ephemeral(true).unmanaged(List, { [] } as Factory) }
            registry.create("bar") { it.ephemeral(false).unmanaged(Queue, List, { it } as Transformer) }
            registry.mutate(List) {
                it.add val
            }

            then:
            registry.get("foo") == ["1"]
            registry.get("bar") == ["1"]

            when:
            val = "2"
            registry.prepareForReuse()

            then:
            registry.get("foo") == ["2"]
            registry.get("bar") == ["2"]
    }
}
