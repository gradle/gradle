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

import org.gradle.api.internal.provider.CircularEvaluationSpec.CircularFunctionEvaluationSpec
import org.gradle.api.internal.provider.CircularEvaluationSpec.UsesStringProperty
import spock.lang.Specification

import java.util.function.BiFunction
import java.util.function.Consumer

import static org.gradle.api.internal.provider.CircularEvaluationSpec.ProviderConsumer.GET_PRODUCER
import static org.gradle.api.internal.provider.CircularEvaluationSpec.ProviderConsumer.TO_STRING

class BiProviderTest {
    static class BiProviderCircularFunctionEvaluationTest extends CircularFunctionEvaluationSpec<String> {
        @Override
        ProviderInternal<String> providerWithSelfReference() {
            def transform = new BiFunction<String, String, String>() {
                ProviderInternal<String> provider

                @Override
                String apply(String a, String b) {
                    return provider.get()
                }

                @Override
                String toString() {
                    return "BiFunction with $provider"
                }
            }
            transform.provider = new BiProvider(String, Providers.of("A"), Providers.of("B"), transform)
            return transform.provider
        }

        @Override
        List<Consumer<ProviderInternal<?>>> safeConsumers() {
            return [TO_STRING, GET_PRODUCER]
        }
    }

    static class BiProviderLeftCircularChainEvaluationTest extends Specification implements UsesStringProperty {
        def "setting property to a zipped version of itself (left) uses original value"() {
            given:
            def property = property().value("hello")

            when:
            property.set(property.zip(Providers.of("B")) { a, b -> a + b })

            then:
            property.get() == "helloB"
        }
    }

    static class BiProviderRightCircularChainEvaluationTest extends Specification implements UsesStringProperty {
        def "setting property to a zipped version of itself (right) uses original value"() {
            given:
            def property = property().value("hello")

            when:
            property.set(Providers.of("A").zip(property, { a, b -> a + b }))

            then:
            property.get() == "Ahello"
        }
    }
}
