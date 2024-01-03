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

import org.gradle.api.internal.provider.CircularEvaluationSpec.CircularChainEvaluationSpec
import org.gradle.api.internal.provider.CircularEvaluationSpec.UsesStringProperty

import java.util.function.Consumer

class OrElseFixedValueProviderTest {
    static class OrElseFixedValueProviderCircularChainEvaluationTest extends CircularChainEvaluationSpec<String> implements UsesStringProperty {
        @Override
        ProviderInternal<String> wrapProviderWithProviderUnderTest(ProviderInternal<String> baseProvider) {
            return new OrElseFixedValueProvider<String>(baseProvider, "B")
        }

        @Override
        List<Consumer<ProviderInternal<?>>> safeConsumers() {
            return super.safeConsumers() + [ProviderConsumer.CALCULATE_PRESENCE] as List<Consumer<ProviderInternal<?>>>
        }
    }
}
