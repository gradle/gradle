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

package org.gradle.model.internal.core

import org.gradle.model.internal.core.rule.describe.SimpleModelRuleDescriptor
import org.gradle.model.internal.registry.DefaultModelRegistry
import spock.lang.Specification

class ActionBackedModelMutatorTest extends Specification {

    def registry = new DefaultModelRegistry()

    def "action is called"() {
        when:
        def foo = ModelReference.of("foo", List)
        def bar = ModelReference.of("bar", List)

        def descriptor = new SimpleModelRuleDescriptor("foo")

        def fooList = []
        registry.create(ModelCreators.of(foo, fooList).descriptor(descriptor).build())

        def barList = []
        registry.create(ModelCreators.of(bar, barList).descriptor(descriptor).build())

        registry.mutate(ActionBackedModelMutator.of(bar, [], descriptor) {
            it.add("bar")
        })

        registry.mutate(ActionBackedModelMutator.of(foo, [bar], descriptor) {
            it.add("foo")
        })

        then:
        registry.get(foo.path, foo.type) == ["foo"]
        barList == ["bar"] // foo rule depended on bar rule
    }

}
