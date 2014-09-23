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

import org.gradle.api.internal.ModelCreators
import org.gradle.internal.Factories
import org.gradle.model.internal.core.ModelReference
import org.gradle.model.internal.core.rule.describe.SimpleModelRuleDescriptor
import spock.lang.Specification

class DefaultModelRegistryTest extends Specification {

    def registry = new DefaultModelRegistry()

    def "can register creator that is bound immediately"() {
        def foo = ModelCreators.forFactory(ModelReference.of("foo", String), new SimpleModelRuleDescriptor("foo"), [], Factories.constant("foo"))
        def bar = ModelCreators.forFactory(ModelReference.of("bar", Integer), new SimpleModelRuleDescriptor("bar"), [], Factories.constant(1))

        // importantly, this creator has a type only input reference to something that is already bindable
        def other = ModelCreators.forFactory(ModelReference.of("other", String), new SimpleModelRuleDescriptor("other"), [ModelReference.of(String)], Factories.constant("other"))

        when:
        registry.create(foo)
        registry.create(bar)
        registry.create(other)

        then:
        noExceptionThrown()
    }
}
