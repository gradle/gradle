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

package org.gradle.model.internal.registry

import org.gradle.model.internal.core.InstanceModelView
import org.gradle.model.internal.core.ModelBinding
import org.gradle.model.internal.core.ModelReference
import org.gradle.model.internal.core.ModelRuleInput
import org.gradle.model.internal.core.ModelType
import spock.lang.Specification

class DefaultInputsTest extends Specification {

    def foo = "foo"
    def bar = 1
    def fooRef = ModelReference.of("foo", String)
    def fooBinding = ModelBinding.of(fooRef)
    def barRef = ModelReference.of("bar", Integer)
    def barBinding = ModelBinding.of(barRef)

    def inputs = new DefaultInputs([
            ModelRuleInput.of(fooBinding, InstanceModelView.of(fooRef.type, foo)),
            ModelRuleInput.of(barBinding, InstanceModelView.of(barRef.type, bar))
    ])

    def "size"() {
        expect:
        inputs.size() == 2
    }

    def "as references"() {
        expect:
        inputs.references == [fooRef, barRef]
    }

    def "retrieve view"() {
        expect:
        inputs.get(0, fooRef.type).instance.is(foo)
        inputs.get(0, ModelType.of(CharSequence)).instance.is(foo)
        inputs.get(1, barRef.type).instance.is(bar)
    }

    def "out of bounds"() {
        when:
        inputs.get(inputs.size(), ModelType.of(Object))

        then:
        thrown IndexOutOfBoundsException

        when:
        inputs.get(-1, ModelType.of(Object))

        then:
        thrown IndexOutOfBoundsException
    }

    def "incompatible type"() {
        when:
        inputs.get(0, ModelType.of(List))

        then:
        thrown IllegalArgumentException
    }

}
