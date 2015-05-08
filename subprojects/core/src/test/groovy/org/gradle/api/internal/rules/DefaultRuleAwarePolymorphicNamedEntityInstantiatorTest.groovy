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

package org.gradle.api.internal.rules

import org.gradle.api.NamedDomainObjectFactory
import org.gradle.api.internal.PolymorphicNamedEntityInstantiator
import org.gradle.model.internal.core.rule.describe.SimpleModelRuleDescriptor
import spock.lang.Specification

class DefaultRuleAwarePolymorphicNamedEntityInstantiatorTest extends Specification {

    def delegate = Mock(PolymorphicNamedEntityInstantiator)
    def registry = Mock(RuleAwareNamedDomainObjectFactoryRegistry)
    def instantiator = new DefaultRuleAwarePolymorphicNamedEntityInstantiator(delegate, registry)

    def "uses delegate instantiator to create objects"() {
        given:
        delegate.create("foo", String) >> "bar"

        expect:
        instantiator.create("foo", String) == "bar"
    }

    def "uses delegate registry to register factories"() {
        given:
        def descriptor = new SimpleModelRuleDescriptor("test")
        def factory = Mock(NamedDomainObjectFactory)

        when:
        instantiator.registerFactory(String, factory, descriptor)

        then:
        1 * registry.registerFactory(String, factory, descriptor)
    }
}
