/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal

import org.gradle.api.Namer
import org.gradle.internal.reflect.DirectInstantiator
import org.gradle.internal.reflect.Instantiator
import org.gradle.util.ConfigureUtil
import spock.lang.Specification

class DefaultNamedDomainObjectSetSpec extends Specification {
    private final Instantiator instantiator = new ClassGeneratorBackedInstantiator(new AsmBackedClassGenerator(), DirectInstantiator.INSTANCE)
    private final Namer<DefaultNamedDomainObjectSetTest.Bean> namer = new Namer<DefaultNamedDomainObjectSetTest.Bean>() {
        String determineName(DefaultNamedDomainObjectSetTest.Bean bean) {
            return bean.name
        }
    };
    private final def container = instantiator.newInstance(DefaultNamedDomainObjectSet.class, DefaultNamedDomainObjectSetTest.Bean.class, instantiator, namer)

    def eachObjectIsAvailableAsADynamicProperty() {
        def bean = new DefaultNamedDomainObjectSetTest.Bean("child");

        given:
        container.add(bean)

        expect:
        container.hasProperty("child")
        container.child == bean
    }

    def cannotGetOrSetUnknownProperty() {
        expect:
        !container.hasProperty("unknown")

        when:
        container.unknown

        then:
        def e = thrown(MissingPropertyException)
        e.message == "Could not get unknown property 'unknown' for Bean set of type $DefaultNamedDomainObjectSet.name."

        when:
        container.unknown = "123"

        then:
        e = thrown(MissingPropertyException)
        e.message == "Could not set unknown property 'unknown' for Bean set of type $DefaultNamedDomainObjectSet.name."
    }

    def dynamicPropertyAccessInvokesRulesForUnknownDomainObject() {
        def bean = new DefaultNamedDomainObjectSetTest.Bean();

        given:
        container.addRule("rule", { s -> if (s == bean.name) { container.add(bean) }})

        expect:
        container.bean == bean
        container.hasProperty("bean")
    }

    def eachObjectIsAvailableAsConfigureMethod() {
        def bean = new DefaultNamedDomainObjectSetTest.Bean("child");
        container.add(bean);

        when:
        container.child {
            beanProperty = 'value'
        }

        then:
        bean.beanProperty == 'value'

        when:
        container.invokeMethod("child") {
            beanProperty = 'value 2'
        }

        then:
        bean.beanProperty == 'value 2'
    }

    def cannotInvokeUnknownMethod() {
        container.add(new DefaultNamedDomainObjectSetTest.Bean("child"));

        when:
        container.unknown()

        then:
        def e = thrown(MissingMethodException)
        e.message == "Could not find method unknown() for arguments [] on Bean set of type $DefaultNamedDomainObjectSet.name."

        when:
        container.unknown { }

        then:
        e = thrown(MissingMethodException)
        e.message.startsWith("Could not find method unknown() for arguments [")

        when:
        container.child()

        then:
        e = thrown(MissingMethodException)
        e.message == "Could not find method child() for arguments [] on Bean set of type $DefaultNamedDomainObjectSet.name."

        when:
        container.child("not a closure")

        then:
        e = thrown(MissingMethodException)
        e.message == "Could not find method child() for arguments [not a closure] on Bean set of type $DefaultNamedDomainObjectSet.name."

        when:
        container.child({}, "not a closure")

        then:
        e = thrown(MissingMethodException)
        e.message.startsWith("Could not find method child() for arguments [")
    }

    def canUseDynamicPropertiesAndMethodsInsideConfigureClosures() {
        def bean = new DefaultNamedDomainObjectSetTest.Bean("child");
        container.add(bean);

        when:
        ConfigureUtil.configure({ child.beanProperty = 'value 1' }, container)

        then:
        bean.beanProperty == 'value 1'

        when:
        ConfigureUtil.configure({ child { beanProperty = 'value 2' } }, container)

        then:
        bean.beanProperty == 'value 2'

        def withType = new DefaultNamedDomainObjectSetTest.Bean("withType");
        container.add(withType);

        // Try with an element with the same name as a method
        when:
        ConfigureUtil.configure({ withType.beanProperty = 'value 3' }, container)

        then:
        withType.beanProperty == 'value 3'
    }

    def configureMethodInvokesRuleForUnknownDomainObject() {
        def b = new DefaultNamedDomainObjectSetTest.Bean()

        given:
        container.addRule("rule", { s -> if (s == b.name) { container.add(b) }})

        expect:
        container.bean {
            assert it == b
        }
    }

}
