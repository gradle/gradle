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
import org.gradle.model.internal.type.ModelType
import spock.lang.Specification

class InstanceBackedModelRegistrationTest extends Specification {

    def registry = new DefaultModelRegistry(null)

    def "action is called"() {
        when:
        def foo = ModelReference.of("foo", List)
        def bar = ModelReference.of("bar", List)

        def descriptor = new SimpleModelRuleDescriptor("foo")

        def fooList = []
        def fooRegistration = ModelRegistrations.bridgedInstance(foo, fooList).descriptor(descriptor).build()
        registry.register(fooRegistration)

        def barList = []
        def factory = Mock(org.gradle.internal.Factory) {
            1 * create() >> barList
        }
        def barRegistration = ModelRegistrations.unmanagedInstance(bar, factory).descriptor(descriptor).build()
        registry.register(barRegistration)

        then:
        !fooRegistration.promise.canBeViewedAsImmutable(ModelType.of(String))
        !fooRegistration.promise.canBeViewedAsMutable(ModelType.of(String))
        fooRegistration.promise.canBeViewedAsImmutable(ModelType.of(List))
        fooRegistration.promise.canBeViewedAsMutable(ModelType.of(List))

        registry.realize(foo.path, foo.type).is(fooList)
        registry.realize(bar.path, bar.type).is(barList)
    }

}
