/*
 * Copyright 2009 the original author or authors.
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
import org.gradle.api.Rule
import org.gradle.api.internal.collections.IterationOrderRetainingSetElementSource
import org.gradle.internal.reflect.DirectInstantiator
import org.gradle.internal.reflect.Instantiator

class DefaultNamedDomainObjectCollectionTest extends AbstractNamedDomainObjectCollectionSpec<Bean> {

    private final Namer<Bean> namer = new Namer<Bean>() {
        String determineName(Bean bean) { return bean.name }
    };

    Instantiator instantiator = DirectInstantiator.INSTANCE
    Set<Bean> store

    final DefaultNamedDomainObjectCollection<Bean> container = new DefaultNamedDomainObjectCollection<Bean>(Bean, new IterationOrderRetainingSetElementSource<Bean>(), instantiator, namer)
    final Bean a = new BeanSub1("a")
    final Bean b = new BeanSub1("b")
    final Bean c = new BeanSub1("c")
    final Bean d = new BeanSub2("d")
    boolean insertionOrderExpected = true

    def setup() {
        container.clear()
    }

    def "getNames"() {
        expect:
        container.getNames().isEmpty()

        when:
        container.add(new Bean("bean1"))
        container.add(new Bean("bean2"))
        container.add(new Bean("bean3"))
        then:
        container.names == ["bean1", "bean2", "bean3"] as SortedSet
    }

    def "returns null element with name is not present and there are no rules to create it"() {
        expect:
        container.findByName("bean") == null
    }

    def "invokes rule to create element when element with name cannot be located"() {
        def rule = Mock(Rule)
        def bean = new Bean("bean")

        given:
        container.addRule(rule)

        when:
        def result = container.findByName("bean")

        then:
        result == bean

        and:
        1 * rule.apply("bean") >> { container.add(bean) }
        0 * rule._
    }

    def "invokes rule once only when element cannot be located"() {
        def rule = Mock(Rule)

        given:
        container.addRule(rule)

        when:
        def result = container.findByName("bean")

        then:
        result == null

        and:
        1 * rule.apply("bean")
        0 * rule._
    }

    def "does not invoke rule when element with name is available"() {
        def rule = Mock(Rule)
        def bean = new Bean("bean")

        given:
        container.addRule(rule)
        container.add(bean)

        when:
        def result = container.findByName("bean")

        then:
        result == bean

        and:
        0 * rule._
    }

    static class Bean {
        public final String name

        Bean(String name) {
            this.name = name
        }

        @Override
        String toString() {
            return name
        }
    }

    static class BeanSub1 extends Bean {
        BeanSub1(String name) {
            super(name)
        }
    }

    static class BeanSub2 extends Bean {
        BeanSub2(String name) {
            super(name)
        }
    }
}
