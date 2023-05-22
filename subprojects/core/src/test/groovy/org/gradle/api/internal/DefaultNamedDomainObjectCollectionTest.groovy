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

import org.gradle.api.InvalidUserDataException
import org.gradle.api.Namer
import org.gradle.api.Rule
import org.gradle.api.internal.collections.IterationOrderRetainingSetElementSource
import org.gradle.api.internal.provider.Providers
import org.gradle.internal.reflect.Instantiator
import org.gradle.util.TestUtil

class DefaultNamedDomainObjectCollectionTest extends AbstractNamedDomainObjectCollectionSpec<Bean> {

    private final Namer<Bean> namer = new Namer<Bean>() {
        String determineName(Bean bean) { return bean.name }
    };

    Instantiator instantiator = TestUtil.instantiatorFactory().decorateLenient()

    DefaultNamedDomainObjectCollection<Bean> container = new DefaultNamedDomainObjectCollection<Bean>(Bean, new IterationOrderRetainingSetElementSource<Bean>(), instantiator, namer, callbackActionDecorator)
    Bean a = new BeanSub1("a")
    Bean b = new BeanSub1("b")
    Bean c = new BeanSub1("c")
    Bean d = new BeanSub2("d")
    boolean externalProviderAllowed = true
    boolean directElementAdditionAllowed = true
    boolean elementRemovalAllowed = true
    boolean supportsBuildOperations = true

    def setup() {
        container.clear()
    }

    def "named finds objects created by rules"() {
        def rule = Mock(Rule)
        def bean = new Bean("bean")

        given:
        container.addRule(rule)

        when:
        def result = container.named("bean")

        then:
        result.present
        result.get() == bean

        and:
        1 * rule.apply("bean") >> {
            container.add(bean)
        }
        0 * rule._
    }

    def "named finds element added by addAllLater"() {
//        container.addAllLater(Providers.of(([a])))
        container.addLater(Providers.of(a))

        when:
        def result = container.named("a")

        then:
        result.present
        result.get() == a
    }

    def "named finds objects added to container"() {
        container.add(a)
        when:
        def result = container.named("a")
        then:
        result.present
        result.get() == a
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

    def "can configure domain objects through provider"() {
        container.add(a)
        when:
        def result = container.named("a")
        result.configure {
            it.value = "changed"
        }
        then:
        result.get().value == "changed"
    }

    def "can remove element using named provider"() {
        def bean = new Bean("bean")

        given:
        container.add(bean)

        when:
        def provider = container.named('bean')

        then:
        provider.present
        provider.orNull == bean

        when:
        container.remove(provider)

        then:
        container.names.toList() == []

        and:
        !provider.present
        provider.orNull == null

        when:
        provider.get()

        then:
        def ex = thrown(IllegalStateException)
        ex.message == "The domain object 'bean' (Bean) for this provider is no longer present in its container."
    }

    def "can find object by name and type"() {
        def bean = new Bean("bean")

        given:
        container.add(bean)

        when:
        def provider = container.named('bean', Bean)
        then:
        provider.present
        provider.orNull == bean
    }

    def "can configure object by name and type"() {
        def bean = new Bean("bean")

        given:
        container.add(bean)

        when:
        def provider = container.named('bean', Bean) {
            it.value = "changed"
        }
        then:
        provider.present
        provider.orNull == bean
        provider.get().value == "changed"
    }

    def "can configure object by name"() {
        def bean = new Bean("bean")

        given:
        container.add(bean)

        when:
        def provider = container.named('bean') {
            it.value = "changed"
        }
        then:
        provider.present
        provider.orNull == bean
        provider.get().value == "changed"
    }

    def "gets useful error when trying to find object by name and improper type"() {
        def bean = new Bean("bean")

        given:
        container.add(bean)

        when:
        container.named('bean', String)
        then:
        def e = thrown(InvalidUserDataException)
        e.message == "The domain object 'bean' (${Bean.class.canonicalName}) is not a subclass of the given type (java.lang.String)."
    }

    def "can extract schema from collection with domain objects"() {
        container.add(a)
        expect:
        assertSchemaIs(
            a: "DefaultNamedDomainObjectCollectionTest.Bean"
        )
        // schema isn't cached
        container.add(b)
        container.add(d)
        // TODO maybe should be based on the type of the add?
        assertSchemaIs(
            a: "DefaultNamedDomainObjectCollectionTest.Bean",
            b: "DefaultNamedDomainObjectCollectionTest.Bean",
            d: "DefaultNamedDomainObjectCollectionTest.Bean"
        )
    }

    def "can extract schema from empty collection"() {
        expect:
        assertSchemaIs([:])
    }

    protected void assertSchemaIs(Map<String, String> expectedSchema) {
        def actualSchema = container.collectionSchema
        Map<String, String> actualSchemaMap = actualSchema.elements.collectEntries { schema ->
            [ schema.name, schema.publicType.simpleName ]
        }.sort()
        def expectedSchemaMap = expectedSchema.sort()
        assert expectedSchemaMap == actualSchemaMap
    }

    static class Bean {
        public final String name

        String value = "original"

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
