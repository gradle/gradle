/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.component.resolution.failure.transform

import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.internal.Describables
import spock.lang.Issue
import spock.lang.Specification

@Issue("https://github.com/gradle/gradle/issues/31503")
class SourceVariantDataTest extends Specification {

    def "formats with an owner display name by composing the parts"() {
        given:
        def owner = Describables.of("configuration ':lib:compile'")
        def data = new SourceVariantData(owner, "variant1", ImmutableAttributes.EMPTY)

        expect:
        data.formattedVariantName == "configuration ':lib:compile' variant 'variant1'"
    }

    def "falls back to the bare variant name when owner display is null"() {
        given:
        def data = new SourceVariantData(null, "variant 'red'", ImmutableAttributes.EMPTY)

        expect:
        data.formattedVariantName == "variant 'red'"
    }

    def "exposes constructor inputs via accessors"() {
        given:
        def data = new SourceVariantData(Describables.of("owner"), "variant1", ImmutableAttributes.EMPTY)

        expect:
        data.variantName == "variant1"
        data.attributes == ImmutableAttributes.EMPTY
    }

    def "two source variants sharing a bare name across different owners remain distinct identities"() {
        given:
        def fromComponentA = new SourceVariantData(Describables.of("component A"), "runtime", ImmutableAttributes.EMPTY)
        def fromComponentB = new SourceVariantData(Describables.of("component B"), "runtime", ImmutableAttributes.EMPTY)

        expect:
        fromComponentA != fromComponentB
        fromComponentA.hashCode() != fromComponentB.hashCode()
    }

    def "equality covers owner, variant name, and attributes"() {
        given:
        def a = new SourceVariantData(Describables.of("owner-1"), "variant1", ImmutableAttributes.EMPTY)
        def aPrime = new SourceVariantData(Describables.of("owner-1"), "variant1", ImmutableAttributes.EMPTY)
        def differentOwner = new SourceVariantData(Describables.of("owner-2"), "variant1", ImmutableAttributes.EMPTY)
        def differentName = new SourceVariantData(Describables.of("owner-1"), "variant2", ImmutableAttributes.EMPTY)
        def nullOwner = new SourceVariantData(null, "variant1", ImmutableAttributes.EMPTY)
        def nullOwnerPrime = new SourceVariantData(null, "variant1", ImmutableAttributes.EMPTY)

        expect:
        a == aPrime
        a.hashCode() == aPrime.hashCode()
        a != differentOwner
        a != differentName
        a != nullOwner
        nullOwner == nullOwnerPrime
        nullOwner.hashCode() == nullOwnerPrime.hashCode()
    }
}
