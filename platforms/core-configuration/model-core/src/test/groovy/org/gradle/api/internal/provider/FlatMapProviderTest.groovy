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

import org.gradle.api.Transformer
import org.gradle.api.internal.provider.CircularEvaluationSpec.CircularChainEvaluationSpec
import org.gradle.api.internal.provider.CircularEvaluationSpec.CircularFunctionEvaluationSpec
import org.gradle.api.internal.provider.CircularEvaluationSpec.UsesStringProperty
import org.gradle.api.provider.Provider

class FlatMapProviderTest {
    static class FlatMapProviderCircularFunctionEvaluationTest extends CircularFunctionEvaluationSpec<String> {
        @Override
        ProviderInternal<String> providerWithSelfReference() {
            def transform = new Transformer<Provider<String>, String>() {
                ProviderInternal<String> provider

                @Override
                Provider<String> transform(String s) {
                    return Providers.of(provider.get())
                }

                @Override
                String toString() {
                    return "Transformer with $provider"
                }
            }
            transform.provider = new FlatMapProvider(Providers.of("value"), transform)
            return transform.provider
        }
    }

    static class FlatMapProviderCircularChainEvaluationTest extends CircularChainEvaluationSpec<String> implements UsesStringProperty {
        @Override
        ProviderInternal<String> wrapProviderWithProviderUnderTest(ProviderInternal<String> baseProvider) {
            return new FlatMapProvider(baseProvider, { baseProvider })
        }
    }
}
