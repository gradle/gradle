/*
 * Copyright 2020 the original author or authors.
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

import org.gradle.api.internal.provider.CircularEvaluationSpec.UsesStringProperty
import spock.lang.Specification

class OrElseFixedValueProviderTest {
    static class OrElseFixedValueProviderCircularChainEvaluationTest extends Specification implements UsesStringProperty {
        def "setting property to an orElse fixed-value version of itself uses original value when present"() {
            given:
            def property = property().value("hello")

            when:
            property.set(new OrElseFixedValueProvider<String>(property, "fallback"))

            then:
            property.get() == "hello"
        }

        def "setting property to an orElse fixed-value version of itself falls back when absent"() {
            given:
            def property = property()

            when:
            property.set(new OrElseFixedValueProvider<String>(property, "fallback"))

            then:
            property.get() == "fallback"
        }
    }
}
