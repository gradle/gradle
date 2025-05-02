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


import org.gradle.internal.evaluation.CircularEvaluationException
import org.gradle.util.TestUtil
import spock.lang.Specification

import javax.annotation.Nullable
import java.util.function.Consumer

import static org.gradle.api.internal.provider.CircularEvaluationSpec.ProviderConsumer.GET_PRODUCER
import static org.gradle.api.internal.provider.CircularEvaluationSpec.ProviderConsumer.TO_STRING
import static org.gradle.api.internal.provider.ValueSupplier.ValueConsumer.IgnoreUnsafeRead

abstract class CircularEvaluationSpec<T> extends Specification {
    List<Consumer<ProviderInternal<?>>> throwingConsumers() {
        return ProviderConsumer.values().findAll { it !in safeConsumers() }.toList()
    }

    List<Consumer<ProviderInternal<?>>> safeConsumers() {
        return [TO_STRING]
    }

    void setup() {
        assert ((throwingConsumers() + safeConsumers()) as Set).containsAll(ProviderConsumer.values())
    }

    /**
     * Base class for testing providers that evaluate user-provided code to determine the provider value.
     * The tests simulate this user code referencing the provider itself.
     *
     * @param <T> the provider value type
     */
    static abstract class CircularFunctionEvaluationSpec<T> extends CircularEvaluationSpec<T> {
        abstract ProviderInternal<T> providerWithSelfReference()

        def "calling #consumer throws exception if user code causes circular evaluation"(
            Consumer<ProviderInternal<?>> consumer
        ) {
            given:
            def provider = providerWithSelfReference()

            when:
            consumer.accept(provider)

            then:
            CircularEvaluationException ex = thrown()
            ex.evaluationCycle == [provider, provider]

            where:
            consumer << throwingConsumers()
        }

        def "calling #consumer is safe even if user code causes circular evaluation"(
            Consumer<ProviderInternal<?>> consumer
        ) {
            given:
            def provider = providerWithSelfReference()

            when:
            consumer.accept(provider)

            then:
            noExceptionThrown()

            where:
            consumer << safeConsumers()
        }
    }

    /**
     * Base class for testing providers that produce the value based on some other providers, including properties.
     * The tests simulate circular evaluation chain, when the provider is based on a property that is set to the provider itself.
     *
     * @param <T> the provider value type
     */
    static abstract class CircularChainEvaluationSpec<T> extends CircularEvaluationSpec<T> {
        /**
         * Returns a provider-under-test that uses baseProvider for its computation
         * @param baseProvider the provider to wrap
         * @return the provider-under-test
         */
        abstract ProviderInternal<T> wrapProviderWithProviderUnderTest(ProviderInternal<T> baseProvider)

        /**
         * Creates an appropriate property instance of the same type
         * @return the property instance
         */
        abstract PropertyInternal<T> property()

        def "calling #consumer throws exception with proper chain if wrapped provider forms a cycle"(
            Consumer<ProviderInternal<?>> consumer
        ) {
            given:
            def property = property()
            def provider = wrapProviderWithProviderUnderTest(property)
            property.set(provider)

            when:
            consumer.accept(provider)

            then:
            CircularEvaluationException ex = thrown()
            assertExceptionHasExpectedCycle(ex, provider, property)

            where:
            consumer << throwingConsumers()
        }

        def "calling #consumer throws exception with proper chain if wrapped provider forms a cycle and discards producer"(
            Consumer<ProviderInternal<?>> consumer
        ) {
            given:
            def property = property()
            def provider = wrapProviderWithProviderUnderTest(new ProducerDiscardingProvider(property))
            property.set(provider)

            when:
            consumer.accept(provider)

            then:
            CircularEvaluationException ex = thrown()
            assertExceptionHasExpectedCycle(ex, provider, property)

            where:
            consumer << throwingConsumers() - [GET_PRODUCER]
        }

        def "calling #consumer is safe even if wrapped provider forms a cycle"(
            Consumer<ProviderInternal<?>> consumer
        ) {
            given:
            def property = property()
            def provider = wrapProviderWithProviderUnderTest(property)
            property.set(provider)

            when:
            consumer.accept(provider)

            then:
            noExceptionThrown()

            where:
            consumer << safeConsumers()
        }

        void assertExceptionHasExpectedCycle(CircularEvaluationException ex, def provider, def property) {
            // A Property may wrap the provider in the type-adapting one, adding an extra item to the evaluation chain.
            // To account for this, we only check the beginning of the chain, and its last element.
            assert ex.evaluationCycle[0..1] == [provider, property]
            assert ex.evaluationCycle.last() == provider
        }
    }

    /**
     * A mixin for CircularChainEvaluationSpec<String> that provides implementation of the property() method.
     * Intended for tests of providers which aren't properties.
     */
    trait UsesStringProperty {
        DefaultProperty<String> property() {
            return TestUtil.objectFactory().property(String) as DefaultProperty<String>
        }
    }

    static class ProducerDiscardingProvider<T> extends AbstractMinimalProvider<T> {
        private final ProviderInternal<T> provider

        ProducerDiscardingProvider(ProviderInternal<T> provider) {
            this.provider = provider
        }

        @Override
        protected Value<? extends T> calculateOwnValue(ValueConsumer consumer) {
            return provider.calculateValue(consumer)
        }

        @Override
        ExecutionTimeValue<? extends T> calculateExecutionTimeValue() {
            return provider.calculateExecutionTimeValue()
        }

        @Nullable
        @Override
        Class<T> getType() {
            return provider.getType()
        }
    }

    enum ProviderConsumer implements Consumer<ProviderInternal<?>> {
        GET("get", { it.get() }),
        CALCULATE_VALUE("calculateValue", { it.calculateValue(IgnoreUnsafeRead) }),
        CALCULATE_PRESENCE("calculatePresence", { it.calculatePresence(IgnoreUnsafeRead) }),
        CALCULATE_EXECUTION_TIME_VALUE("calculateExecutionTimeValue", { it.calculateExecutionTimeValue() }),
        WITH_FINAL_VALUE("withFinalValue", { it.withFinalValue(IgnoreUnsafeRead) }),
        GET_PRODUCER("getProducer", { it.getProducer() }),
        TO_STRING("toString", { it.toString() })

        private final String stringValue
        private final Consumer<ProviderInternal<?>> impl

        ProviderConsumer(String stringValue, Consumer<ProviderInternal<?>> impl) {
            this.impl = impl
            this.stringValue = stringValue
        }

        @Override
        String toString() {
            return stringValue
        }

        @Override
        void accept(ProviderInternal<?> providerInternal) {
            impl.accept(providerInternal)
        }
    }
}
