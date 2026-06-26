/*
 * Copyright 2011 the original author or authors.
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

import groovy.test.NotYetImplemented
import org.gradle.util.TestUtil
import spock.lang.Issue

class DefaultDomainObjectSetTest extends AbstractDomainObjectCollectionSpec<CharSequence> {
    DefaultDomainObjectSet<CharSequence> set = new DefaultDomainObjectSet<CharSequence>(CharSequence, callbackActionDecorator)
    DefaultDomainObjectSet<CharSequence> container = set
    StringBuffer a = new StringBuffer("a")
    StringBuffer b = new StringBuffer("b")
    StringBuffer c = new StringBuffer("c")
    StringBuilder d = new StringBuilder("d")
    boolean externalProviderAllowed = true
    boolean directElementAdditionAllowed = true
    boolean elementRemovalAllowed = true
    boolean supportsBuildOperations = true

    def "Set semantics preserved if backing collection is a filtered composite set"() {
        def c1 = new DefaultDomainObjectSet<String>(String, callbackActionDecorator)
        def c2 = new DefaultDomainObjectSet<String>(String, callbackActionDecorator)
        given:
        def composite = CompositeDomainObjectSet.<String>create(String, c1, c2)
        def set = new DefaultDomainObjectSet<String>(String, composite.getStore(), callbackActionDecorator)

        when:
        c1.add("a")
        c1.add("b")
        c1.add("c")
        c1.add("d")
        c2.add("a")
        c2.add("c")

        then:
        set.size() == 4
        set.iterator().collect { it } == ["a", "b", "c", "d"]
    }

    def "withType works with addLater"() {
        given:
        def value = Mock(Subtype)

        when:
        container.addLater(TestUtil.providerFactory().provider { value })
        def result = collect(container.withType(Subtype))

        then:
        result == [value]
    }

    @NotYetImplemented
    def "withType works with addAllLater for list"() {
        given:
        def value = Mock(Subtype)

        when:
        container.addAllLater(TestUtil.providerFactory().provider { [value] })
        def result = collect(container.withType(Subtype))

        then:
        result == [value]
    }

    @NotYetImplemented
    def "withType works for addAllLater and set property"() {
        def value = Mock(Subtype)
        def property = TestUtil.objectFactory().setProperty(CharSequence)
        property.add(value)

        when:
        container.addAllLater(property)
        def result = collect(container.withType(Subtype))

        then:
        result == [value]
    }

    @Issue("https://github.com/gradle/gradle/issues/36951")
    def "configureEach does not realize pending elements added via addAllLater when an eager element is present"() {
        given:
        def pending = TestUtil.objectFactory().listProperty(CharSequence)
        pending.value(TestUtil.providerFactory().provider { throw new RuntimeException("pending provider realized too early") })
        set.addAllLater(pending)
        // configureEach is documented as lazy: registering it must not realize pending providers.
        set.configureEach { } // first call stays lazy because no eager element exists yet
        set.add(a) // mix in an eager element

        when:
        set.configureEach { } // second call must remain lazy and not realize the pending provider

        then:
        noExceptionThrown()
    }

    interface Subtype extends CharSequence {}

    static <T> Collection<T> collect(Iterable<T> iterable) {
        def result = []
        Iterator it = iterable.iterator()
        while (it.hasNext()) {
            result.add(it.next())
        }
        result
    }

}
