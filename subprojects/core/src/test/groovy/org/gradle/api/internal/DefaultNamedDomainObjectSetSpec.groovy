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
import org.junit.Test
import spock.lang.Specification

import static org.gradle.util.TestUtil.call
import static org.gradle.util.TestUtil.call
import static org.gradle.util.TestUtil.toClosure
import static org.hamcrest.Matchers.equalTo
import static org.hamcrest.Matchers.equalTo
import static org.hamcrest.Matchers.equalTo
import static org.junit.Assert.assertThat
import static org.junit.Assert.assertThat
import static org.junit.Assert.assertThat
import static org.junit.Assert.assertTrue

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
        container.properties.child == bean
    }

    def cannotGetOrSetUnknownProperty() {
        expect:
        !container.hasProperty("unknown")

        when:
        container.unknown

        then:
        def e = thrown(MissingPropertyException)
        e.message == '??'

        when:
        container.unknown = "123"

        then:
        e = thrown(MissingPropertyException)
        e.message == '??'
    }

    def dynamicPropertyAccessInvokesRulesForUnknownDomainObject() {
        def bean = new DefaultNamedDomainObjectSetTest.Bean();

        given:
        container.addRule("rule", { s -> if (s == bean.name) { container.add(bean) }})

        expect:
        container.hasProperty("bean")
        container.bean == bean
    }

    @Test
    public void eachObjectIsAvailableAsConfigureMethod() {
        DefaultNamedDomainObjectSetTest.Bean bean = new DefaultNamedDomainObjectSetTest.Bean("child");
        container.add(bean);

        Closure closure = toClosure("{ beanProperty = 'value' }");
        assertTrue(container.getAsDynamicObject().hasMethod("child", closure));
        container.getAsDynamicObject().invokeMethod("child", closure);
        assertThat(bean.getBeanProperty(), equalTo("value"));

        call("{ it.child { beanProperty = 'value 2' } }", container);
        assertThat(bean.getBeanProperty(), equalTo("value 2"));

        call("{ it.invokeMethod('child') { beanProperty = 'value 3' } }", container);
        assertThat(bean.getBeanProperty(), equalTo("value 3"));
    }

    @Test
    public void cannotInvokeUnknownMethod() {
        container.add(new DefaultNamedDomainObjectSetTest.Bean("child"));

        assertMethodUnknown("unknown");
        assertMethodUnknown("unknown", toClosure("{ }"));
        assertMethodUnknown("child");
        assertMethodUnknown("child", "not a closure");
        assertMethodUnknown("child", toClosure("{ }"), "something else");
    }

    @Test
    public void canUseDynamicPropertiesAndMethodsInsideConfigureClosures() {
        DefaultNamedDomainObjectSetTest.Bean bean = new DefaultNamedDomainObjectSetTest.Bean("child");
        container.add(bean);
        container.add(bean);
        container.add(bean);
        container.add(bean);
        container.add(bean);

        ConfigureUtil.configure(toClosure("{ child.beanProperty = 'value 1' }"), container);
        assertThat(bean.getBeanProperty(), equalTo("value 1"));

        ConfigureUtil.configure(toClosure("{ child { beanProperty = 'value 2' } }"), container);
        assertThat(bean.getBeanProperty(), equalTo("value 2"));

        ConfigureUtil.configure(toClosure("{ child.beanProperty = 'value 3' }"), container);
        assertThat(bean.getBeanProperty(), equalTo("value 3"));

        ConfigureUtil.configure(toClosure("{ child.beanProperty = 'value 4' }"), container);
        assertThat(bean.getBeanProperty(), equalTo("value 4"));

        DefaultNamedDomainObjectSetTest.Bean withType = new DefaultNamedDomainObjectSetTest.Bean("withType");
        container.add(withType);

        // Try with an element with the same name as a method
        ConfigureUtil.configure(toClosure("{ withType.beanProperty = 'value 6' }"), container);
        assertThat(withType.getBeanProperty(), equalTo("value 6"));

        ConfigureUtil.configure(toClosure("{ withType { beanProperty = 'value 6' } }"), container);
        assertThat(withType.getBeanProperty(), equalTo("value 6"));
    }

    @Test
    public void configureMethodInvokesRuleForUnknownDomainObject() {
        DefaultNamedDomainObjectSetTest.Bean bean = new DefaultNamedDomainObjectSetTest.Bean();
        addRuleFor(bean);

        assertTrue(container.getAsDynamicObject().hasMethod("bean", toClosure("{ }")));
    }

}
