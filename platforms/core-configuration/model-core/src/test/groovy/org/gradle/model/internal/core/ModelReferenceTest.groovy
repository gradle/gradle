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

package org.gradle.model.internal.core

import org.gradle.model.internal.type.ModelType
import org.gradle.util.Matchers
import spock.lang.Specification

class ModelReferenceTest extends Specification {
    def "any"() {
        expect:
        def reference = ModelReference.any()
        reference.scope == null
        reference.path == null
        reference.type == ModelType.of(Object)
        reference.untyped
        reference.state == ModelNode.State.GraphClosed
    }

    def "path only"() {
        expect:
        def reference = ModelReference.of("some.path")
        reference.scope == null
        reference.path == ModelPath.path("some.path")
        reference.type == ModelType.of(Object)
        reference.untyped
        reference.state == ModelNode.State.GraphClosed
    }

    def "type only"() {
        expect:
        def reference = ModelReference.of(String)
        reference.scope == null
        reference.path == null
        reference.type == ModelType.of(String)
        !reference.untyped
        reference.state == ModelNode.State.GraphClosed
    }

    def "can replace state"() {
        expect:
        def reference = ModelReference.of("some.path", String).atState(ModelNode.State.Mutated)
        reference.scope == null
        reference.path == ModelPath.path("some.path")
        reference.type == ModelType.of(String)
        !reference.untyped
        reference.state == ModelNode.State.Mutated

        def original = ModelReference.of(String)
        original.atState(original.state).is(original)
    }

    def "can replace path"() {
        expect:
        def reference = ModelReference.of(ModelPath.path("some.path"), ModelType.of(String), ModelNode.State.Mutated).withPath(ModelPath.path("other.path"))
        reference.scope == null
        reference.path == ModelPath.path("other.path")
        reference.type == ModelType.of(String)
        !reference.untyped
        reference.state == ModelNode.State.Mutated
    }

    def "can attach a scope"() {
        expect:
        def reference = ModelReference.of(String).inScope(ModelPath.path("some.scope"))
        reference.scope == ModelPath.path("some.scope")
        reference.path == null
        reference.type == ModelType.of(String)
        !reference.untyped
        reference.state == ModelNode.State.GraphClosed
    }

    def "equals"() {
        expect:
        def path = ModelReference.of("path")
        def type = ModelReference.of(String)
        def pathAndType = ModelReference.of("path", String)
        def scopeAndType = ModelReference.of(String).inScope(ModelPath.path("scope"))

        Matchers.strictlyEquals(path, ModelReference.of("path"))
        Matchers.strictlyEquals(type, ModelReference.of(String))
        Matchers.strictlyEquals(pathAndType, ModelReference.of("path", String))
        Matchers.strictlyEquals(scopeAndType, type.inScope(ModelPath.path("scope")))

        path != type
        path != pathAndType
        path != scopeAndType
        type != pathAndType
        type != scopeAndType

        path != ModelReference.of("other")
        type != ModelReference.of(Long)
        pathAndType != pathAndType.withPath(ModelPath.path("other"))
        pathAndType != ModelReference.of("path", Long)
        scopeAndType != ModelReference.of(Long).inScope(ModelPath.path("scope"))
        scopeAndType != scopeAndType.inScope(ModelPath.path("other"))
    }
}
