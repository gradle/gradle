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

package org.gradle.api.internal.tasks.execution

import org.gradle.api.internal.provider.Providers
import org.gradle.api.provider.Provider
import spock.lang.Specification

/**
 * Unit tests for {@link SelfDescribingSpec}.
 */
class SelfDescribingSpecTest extends Specification {
    def "toString reports description as <UNEVALUATED> before getDisplayName is called"() {
        given:
        def spec = new SelfDescribingSpec<Object>({ true }, Providers.of("the reason"))

        expect:
        spec.toString() == "SelfDescribingSpec{description=<UNEVALUATED>}"
    }

    def "toString reports the resolved description after getDisplayName is called"() {
        given:
        def spec = new SelfDescribingSpec<Object>({ true }, Providers.of("the reason"))

        when:
        spec.getDisplayName()

        then:
        spec.toString() == "SelfDescribingSpec{description='the reason'}"
    }

    def "String-argument constructor eagerly considers the description evaluated for toString"() {
        given:
        def spec = new SelfDescribingSpec<Object>({ true }, "the reason")

        when:
        spec.getDisplayName()

        then:
        spec.toString() == "SelfDescribingSpec{description='the reason'}"
    }

    def "descriptionProvider is queried at most once across repeated getDisplayName calls"() {
        given:
        def provider = Mock(Provider)
        def spec = new SelfDescribingSpec<Object>({ true }, provider)

        when:
        3.times { spec.getDisplayName() }

        then:
        1 * provider.get() >> "the reason"
        0 * provider._
    }

    def "toString does not invoke the descriptionProvider"() {
        given:
        def provider = Mock(Provider)
        def spec = new SelfDescribingSpec<Object>({ true }, provider)

        when:
        def result = spec.toString()

        then:
        result == "SelfDescribingSpec{description=<UNEVALUATED>}"
        0 * provider._
    }
}
