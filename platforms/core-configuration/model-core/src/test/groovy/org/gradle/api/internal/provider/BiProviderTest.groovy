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

import org.gradle.api.internal.provider.CircularEvaluationSpec.CircularChainEvaluationSpec
import org.gradle.api.internal.provider.CircularEvaluationSpec.CircularFunctionEvaluationSpec
import org.gradle.api.internal.provider.CircularEvaluationSpec.UsesStringProperty

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

    static class BiProviderLeftCircularChainEvaluationTest extends CircularChainEvaluationSpec<String> implements UsesStringProperty {
        @Override
        ProviderInternal<String> wrapProviderWithProviderUnderTest(ProviderInternal<String> baseProvider) {
            return new BiProvider(String, baseProvider, Providers.of("B"), { a, b -> a + b })
        }
    }

    static class BiProviderRightCircularChainEvaluationTest extends CircularChainEvaluationSpec<String> implements UsesStringProperty {
        ProviderInternal<String> wrapProviderWithProviderUnderTest(ProviderInternal<String> baseProvider) {
            return new BiProvider(String, Providers.of("A"), baseProvider, { a, b -> a + b })
        }
    }
}
