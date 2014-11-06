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

import org.gradle.model.internal.core.ModelCreators
import org.gradle.model.internal.core.ModelPath
import org.gradle.model.internal.core.ModelReference
import org.gradle.model.internal.core.ModelType
import spock.lang.Specification

class DefaultModelRegistryTest extends Specification {

    def registry = new DefaultModelRegistry()

    def "can register creator that is bound immediately"() {
        def foo = ModelCreators.of(ModelReference.of("foo", String), "foo").simpleDescriptor("foo").build()
        def bar = ModelCreators.of(ModelReference.of("bar", Integer), 1).simpleDescriptor("bar").build()

        // importantly, this creator has a type only input reference to something that is already bindable
        def other = ModelCreators.of(ModelReference.of("other", String), "other")
                .simpleDescriptor("other")
                .inputs([ModelReference.of(String)])
                .build()

        when:
        registry.create(foo)
        registry.create(bar)
        registry.create(other)

        then:
        noExceptionThrown()
    }

    def "can maybe get non existing"() {
        when:
        registry.get(ModelPath.path("foo"), ModelType.untyped())

        then:
        thrown IllegalStateException
        registry.find(ModelPath.path("foo"), ModelType.untyped()) == null
    }
}
