/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.internal.provider

import org.gradle.util.TestUtil
import spock.lang.Specification

class MergeProviderTest extends Specification {

    def objects = TestUtil.objectFactory()
    def providers = TestUtil.providerFactory()

    def "provides all values"() {
        given:
        def provider1 = new MergeProvider([providers.provider { "a" }])
        def provider2 = new MergeProvider([providers.provider { "a" }, providers.provider { "b" }])

        expect:
        provider1.get() == ["a"]
        provider2.get() == ["a", "b"]
    }

    def "isPresent is true if all sources are present"() {
        given:
        def provider1 = new MergeProvider([providers.provider { new Object() }])
        def provider2 = new MergeProvider([providers.provider { new Object() }, providers.provider { new Object() }])

        expect:
        provider1.isPresent()
        provider2.isPresent()
    }

    def "isPresent is false if any source is not present"() {
        given:
        def provider1 = new MergeProvider([providers.provider { null }])
        def provider2 = new MergeProvider([providers.provider { null }, providers.provider { new Object() }])

        expect:
        !provider1.isPresent()
        !provider2.isPresent()
    }

    def "runs side effects"() {
        given:
        def leftSideEffect = Mock(ValueSupplier.SideEffect)
        def rightSideEffect = Mock(ValueSupplier.SideEffect)
        def mergedSideEffect = Mock(ValueSupplier.SideEffect)

        def left = Providers.of("left").withSideEffect(leftSideEffect)
        def right = Providers.of("right").withSideEffect(rightSideEffect)

        def provider = new MergeProvider([left, right]).withSideEffect(mergedSideEffect)

        when:
        provider.calculateValue(ValueSupplier.ValueConsumer.IgnoreUnsafeRead)
        provider.calculateExecutionTimeValue()

        then:
        0 * _ // no side effects when values are not unpacked

        when:
        def result = provider.getOrNull()

        then:
        result == ["left", "right"]
        1 * leftSideEffect.execute("left")

        then: // ensure ordering
        1 * rightSideEffect.execute("right")

        then: // ensure ordering
        1 * mergedSideEffect.execute(["left", "right"])
    }

    def "provider is live"() {
        given:
        def first = objects.property(String)
        def second = objects.property(String)

        def provider = new MergeProvider([first, second])

        when:
        first.set("1")
        second.set("2")

        then:
        provider.get() == ["1", "2"]

        when:
        first.set("3")
        second.set("4")

        then:
        provider.get() == ["3", "4"]

    }
}
