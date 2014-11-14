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

package org.gradle.model.internal.core

import org.gradle.model.internal.registry.DefaultModelRegistry
import org.gradle.model.internal.type.ModelType
import spock.lang.Specification

class InstanceModelCreatorTest extends Specification {

    def "can retrieve instance via exact type"() {
        when:
        def registry = new DefaultModelRegistry()
        def string = "foo"
        def path = ModelPath.path("s")
        registry.create(ModelCreators.bridgedInstance(ModelReference.of(path, String), string).simpleDescriptor("string").build())
        def node = registry.node(path)
        def adapter = node.adapter

        then:
        adapter.asReadOnly(ModelType.of(List), node) == null
        adapter.asReadOnly(ModelType.of(String), node).instance.is(string)
        adapter.asReadOnly(ModelType.of(CharSequence), node).instance.is(string)
        asWritable(node, List) == null
        asWritable(node, String).instance.is(string)
        asWritable(node, CharSequence).instance.is(string)
    }

    private static <T> ModelView<T> asWritable(ModelNode node, Class<T> type) {
        node.adapter.asWritable(ModelType.of(type), null, null, node)
    }
}