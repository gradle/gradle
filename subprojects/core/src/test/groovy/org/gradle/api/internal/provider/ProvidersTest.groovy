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

package org.gradle.api.internal.provider

import org.gradle.api.Transformer
import spock.lang.Specification

class ProvidersTest extends Specification {

    def "can create a null provider"() {
        expect:
        def provider = Providers.notDefined()
        !provider.present
        provider.getOrNull() == null
        provider.getOrElse(null) == null
        provider.getOrElse(12) == 12
        provider.map(Stub(Transformer)) == provider
    }

    def "can create a provider with fixed value"() {
        expect:
        def provider = Providers.of(123)
        provider.present
        provider.get() == 123
        provider.getOrNull() == 123
        provider.getOrElse(null) == 123
        provider.getOrElse(456) == 123
    }

    def "mapped fixed value provider calculates transformed value lazily and caches the result"() {
        given:
        def transform = Mock(Transformer)
        def provider = Providers.of(123)

        when:
        def mapped = provider.map(transform)

        then:
        mapped.present
        0 * transform._

        when:
        def result = mapped.get()

        then:
        result == 321
        1 * transform.transform(123) >> 321
        0 * transform._

        when:
        mapped.get()
        mapped.getOrNull()
        mapped.getOrElse(12)

        then:
        0 * transform._
    }

    def "can chain mapped providers"() {
        given:
        def transform1 = Mock(Transformer)
        def transform2 = Mock(Transformer)
        def transform3 = Mock(Transformer)
        def provider = Providers.of(123)

        when:
        def mapped1 = provider.map(transform1)
        def mapped2 = mapped1.map(transform2)
        def mapped3 = mapped2.map(transform3)

        then:
        mapped1.present
        mapped2.present
        mapped3.present
        0 * _

        when:
        def result = mapped2.get()

        then:
        result == "321"
        1 * transform1.transform(123) >> 321
        1 * transform2.transform(321) >> "321"
        0 * _

        when:
        mapped2.get()
        mapped2.getOrNull()
        mapped2.getOrElse(12)

        then:
        0 * _

        when:
        def result2 = mapped3.get()

        then:
        result2 == "last result"
        1 * transform3.transform("321") >> "last result"
        0 * _

        when:
        mapped3.get()
        mapped3.getOrNull()
        mapped3.getOrElse(12)

        then:
        0 * _
    }

    def "mapped provider fails when transformer returns null"() {
        given:
        def transform = Mock(Transformer)
        def provider = Providers.of(123)

        when:
        def mapped = provider.map(transform)

        then:
        mapped.present
        0 * transform._

        when:
        mapped.get()

        then:
        def e = thrown(IllegalStateException)
        e.message == 'Transformer for this provider returned a null value.'
        1 * transform.transform(123) >> null
        0 * transform._

        when:
        mapped.get()

        then:
        1 * transform.transform(123) >> 12
        0 * transform._
    }

}
