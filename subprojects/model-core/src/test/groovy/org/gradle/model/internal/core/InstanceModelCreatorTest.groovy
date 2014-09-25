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

import org.gradle.model.internal.registry.DefaultInputs
import spock.lang.Specification

class InstanceModelCreatorTest extends Specification {

    def "can retrieve instance via exact type"() {
        when:
        def string = "foo"
        def creator = ModelCreators.of(ModelReference.of(String), string).simpleDescriptor("string").build()
        def adapter = creator.create(new DefaultInputs([]))

        then:
        adapter.asReadOnly(ModelType.of(List)) == null
        adapter.asReadOnly(ModelType.of(String)).instance.is(string)
        adapter.asReadOnly(ModelType.of(CharSequence)).instance.is(string)
        asWritable(adapter, List) == null
        asWritable(adapter, String).instance.is(string)
        asWritable(adapter, CharSequence).instance.is(string)
    }

    private static <T> ModelView<T> asWritable(ModelAdapter adapter, Class<T> type) {
        def reference = ModelReference.of("foo", type)
        adapter.asWritable(new ModelBinding(reference, reference.path), null, null, null)  // can null these because we know they aren't used
    }
}