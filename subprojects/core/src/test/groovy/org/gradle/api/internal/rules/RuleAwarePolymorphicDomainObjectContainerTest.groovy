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

package org.gradle.api.internal.rules

import org.gradle.api.GradleException
import org.gradle.api.Named
import org.gradle.internal.reflect.DirectInstantiator
import org.gradle.model.internal.core.rule.describe.SimpleModelRuleDescriptor
import spock.lang.Specification

class RuleAwarePolymorphicDomainObjectContainerTest extends Specification {

    def container = new DummyContainer()

    def "reports duplicate type registration that was created without rule context"() {
        given:
        container.registerFactory(Dummy, { new Dummy("foo") })

        when:
        container.registerFactory(Dummy, { new Dummy("other") })

        then:
        def t = thrown GradleException
        t.message == "Cannot register a factory for type Dummy because a factory for this type is already registered."
    }

    def "reports duplicate type registration that was created with rule context"() {
        given:
        container.registerFactory(Dummy, { new Dummy(it) }, new SimpleModelRuleDescriptor("<model-rule>"))

        when:
        container.registerFactory(Dummy, { new Dummy(it) }, new SimpleModelRuleDescriptor("<other-rule>"))

        then:
        def t = thrown GradleException
        t.message == "Cannot register a factory for type Dummy because a factory for this type was already registered by <model-rule>."
    }

    class DummyContainer extends RuleAwarePolymorphicDomainObjectContainer<Object> {
        DummyContainer() {
            super(Dummy.class, DirectInstantiator.INSTANCE)
        }
    }

    class Dummy implements Named {
        String name

        Dummy(String name) {
            this.name = name
        }
    }
}
