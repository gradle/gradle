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

package org.gradle.api.internal.provider

import org.gradle.api.Transformer
import org.gradle.api.provider.Provider
import spock.lang.Specification

class PropertyUpdateClassifierTest extends Specification {
    def "recognizes one mutation containing #maps maps without calling providers or transformations"() {
        def previous = Mock(ProviderInternal)
        def transform = Mock(Transformer)
        def candidate = previous
        maps.times { candidate = new TransformBackedProvider(null, candidate, transform) }

        when:
        def update = PropertyUpdateClassifier.isMapUpdate(candidate, previous)

        then:
        update == expected
        0 * _

        where:
        maps | expected
        0    | false
        1    | true
        2    | true
        128  | true
        129  | false
    }

    def "an opaque provider is unclassified without invoking any of its methods"() {
        def candidate = Mock(Provider)
        def previous = Mock(Provider)

        when:
        def update = PropertyUpdateClassifier.isMapUpdate(candidate, previous)

        then:
        !update
        0 * _
    }

    def "a map of another copy is not a proven update of the supplied previous plan"() {
        def property = new DefaultProperty<String>(PropertyHost.NO_OP, String)
        def previous = property.shallowCopy()
        def earlierCopy = property.shallowCopy()

        expect:
        !PropertyUpdateClassifier.isMapUpdate(earlierCopy.map { it }, previous)
        !PropertyUpdateClassifier.isMapUpdate(property.map { it }, previous)
    }

    def "unsupported structural nodes and custom map subclasses remain unclassified"() {
        def previous = Providers.of("value")

        expect:
        !PropertyUpdateClassifier.isMapUpdate(candidate(previous), previous)

        where:
        candidate << [
            { it.flatMap { Providers.of(it) } },
            { it.filter { true } },
            { it.zip(Providers.of("other")) { a, b -> a + b } },
            { it.orElse("fallback") },
            { new MappingProvider(String, it, { it }) },
            { new TransformBackedProvider(String, it, { it }) {} }
        ]
    }
}
