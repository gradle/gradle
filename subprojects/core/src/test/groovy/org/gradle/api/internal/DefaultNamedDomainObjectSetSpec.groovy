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
import org.gradle.api.UnknownDomainObjectException
import org.gradle.api.specs.Spec
import org.gradle.internal.reflect.Instantiator
import org.gradle.util.internal.ConfigureUtil
import org.gradle.util.TestUtil

class DefaultNamedDomainObjectSetSpec extends AbstractNamedDomainObjectCollectionSpec<Bean> {
    private final Instantiator instantiator = TestUtil.instantiatorFactory().decorateLenient()
    private final Namer<Bean> namer = new Namer<Bean>() {
        String determineName(Bean bean) {
            return bean.name
        }
    };
    DefaultNamedDomainObjectSet<Bean> container = instantiator.newInstance(DefaultNamedDomainObjectSet.class, Bean.class, instantiator, namer, callbackActionDecorator)
    Bean a = new BeanSub1("a")
    Bean b = new BeanSub1("b")
    Bean c = new BeanSub1("c")
    Bean d = new BeanSub2("d")
    boolean externalProviderAllowed = true
    boolean directElementAdditionAllowed = true
    boolean elementRemovalAllowed = true
    boolean supportsBuildOperations = true

    @Override
    List<Bean> iterationOrder(Bean... elements) {
        return elements.sort { it.name }
    }

    def usesTypeNameToGenerateDisplayName() {
        expect:
        container.getTypeDisplayName() == "Bean"
        container.getDisplayName() == "Bean set"
    }

    def "can get empty collection as map"() {
        expect:
        container.asMap == [:]
    }

    def "can get collection as map"() {
        when:
        container.add(a)
        container.add(b)
        container.add(c)

        then:
        container.asMap == ["a":a, "b":b, "c":c]
    }

    def canGetAllMatchingDomainObjectsOrderedByName() {
        Bean bean1 = new Bean("a");
        Bean bean2 = new Bean("b");
        Bean bean3 = new Bean("c");

        Spec<Bean> spec = {it == bean2} as Spec<Bean>

        when:
        container.add(bean1);
        container.add(bean2);
        container.add(bean3);

        then:
        container.matching(spec) as List == [bean2]
    }

    def getAllMatchingDomainObjectsReturnsEmptySetWhenNoMatches() {
        Spec<Bean> spec = {false} as Spec<Bean>

        when:
        container.add(new Bean("a"));

        then:
        container.matching(spec).empty
    }

    def canGetFilteredCollectionContainingAllObjectsWhichMeetSpec() {
        Bean bean1 = new Bean("a");
        Bean bean2 = new Bean("b");
        Bean bean3 = new Bean("c");

        when:
        Spec<Bean> spec = { it != bean1 } as Spec<Bean>

        container.add(bean1);
        container.add(bean2);
        container.add(bean3);

        then:
        container.matching(spec) as List == [bean2, bean3]
        container.matching(spec).findByName("a") == null
        container.matching(spec).findByName("b") == bean2
    }

    def canGetFilteredCollectionContainingAllObjectsWhichHaveType() {
        Bean bean1 = new Bean("a");
        BeanSub1 bean2 = new BeanSub1("b");
        Bean bean3 = new Bean("c");

        when:
        container.add(bean1);
        container.add(bean2);
        container.add(bean3);

        then:
        container.withType(Bean) as List == [bean1, bean2, bean3]
        container.withType(BeanSub1) as List == [bean2]
    }

    def canChainFilteredCollections() {
        final Bean bean = new Bean("b1");
        final Bean bean2 = new Bean("b2");
        final Bean bean3 = new Bean("b3");

        when:
        Spec<Bean> spec = { it != bean } as Spec<Bean>
        Spec<Bean> spec2 = {it != bean2 } as Spec<Bean>

        container.add(bean);
        container.add(bean2);
        container.add(bean3);

        then:
        container.matching(spec).matching(spec2) as List == [bean3]
    }

    def canGetDomainObjectByName() {
        when:
        container.add(a);

        then:
        container.getByName("a") == a
        container.getAt("a") == a
        container.findByName("a") == a

        container.findByName("unknown") == null
    }

    def getDomainObjectByNameFailsForUnknownDomainObject() {
        when:
        container.getByName("unknown")

        then:
        def t = thrown(UnknownDomainObjectException)
        t.message == "Bean with name 'unknown' not found."
    }

    def getByNameInvokesRuleForUnknownDomainObject() {
        Bean bean = new Bean("bean");
        when:
        container.addRule("rule", { s -> if (s == bean.name) { container.add(bean) }})

        then:
        container.getByName("bean") == bean

        when:
        container.getByName("other")

        then:
        thrown(UnknownDomainObjectException)
    }

    def findByNameInvokesRuleForUnknownDomainObject() {
        Bean bean = new Bean("bean");
        when:
        container.addRule("rule", { s -> if (s == bean.name) { container.add(bean) }})

        then:
        container.findByName("other") == null
        container.findByName("bean") == bean
    }

    def configureByNameInvokesRuleForUnknownDomainObject() {
        Bean bean = new Bean("bean");
        when:
        container.addRule("rule", { s -> if (s == bean.name) { container.add(bean) }})

        then:
        container.getByName("bean", {it.beanProperty = 'hi'}) == bean
        bean.getBeanProperty() == 'hi'
    }

    def findDomainObjectByNameInvokesNestedRulesOnlyOnceForUnknownDomainObject() {
        final Bean bean1 = new Bean("bean1");
        final Bean bean2 = new Bean("bean2");

        container.addRule("rule1", { s -> if (s == "bean1") { container.add(bean1)} })
        container.addRule("rule2", { s ->
            if (s == "bean2") {
                container.findByName("bean1");
                container.findByName("bean2");
                container.add(bean2);
            }
        })

        expect:
        container.findByName("bean2");
        container as List == [bean1, bean2]
    }

    def eachObjectIsAvailableAsADynamicProperty() {
        def bean = new Bean("child");

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
        def bean = new Bean("bean")

        given:
        container.addRule("rule", { s -> if (s == bean.name) { container.add(bean) }})

        expect:
        container.bean == bean
        container.hasProperty("bean")
    }

    def eachObjectIsAvailableAsConfigureMethod() {
        def bean = new Bean("child");
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
        container.add(new Bean("child"));

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
        def bean = new Bean("child");
        container.add(bean);

        when:
        ConfigureUtil.configure({ child.beanProperty = 'value 1' }, container)

        then:
        bean.beanProperty == 'value 1'

        when:
        ConfigureUtil.configure({ child { beanProperty = 'value 2' } }, container)

        then:
        bean.beanProperty == 'value 2'

        def withType = new Bean("withType");
        container.add(withType);

        // Try with an element with the same name as a method
        when:
        ConfigureUtil.configure({ withType.beanProperty = 'value 3' }, container)

        then:
        withType.beanProperty == 'value 3'
    }

    def configureMethodInvokesRuleForUnknownDomainObject() {
        def b = new Bean("bean")

        given:
        container.addRule("rule", { s -> if (s == b.name) { container.add(b) }})

        expect:
        container.bean {
            assert it == b
        }
    }

    def "name based filtering does not realize pending"() {
        given:
        container.add(new Bean("realized1"))
        container.addLater(new TestNamedProvider("unrealized1", new Bean("unrealized1")))
        container.add(new Bean("realized2"))
        container.addLater(new TestNamedProvider("unrealized2", new Bean("unrealized2")))

        expect: "unrealized elements remain as such"
        container.index.asMap().size() == 2
        container.index.pendingAsMap.size() == 2

        when: "filter the list via the `named` method"
        def filtered = container.named { it.contains("2") }

        then: "unrealized elements remain as such"
        container.index.asMap().size() == 2
        container.index.pendingAsMap.size() == 2

        filtered.index.asMap().size() == 1
        filtered.index.pendingAsMap.size() == 1
    }

    static class Bean {
        public final String name
        String beanProperty

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
