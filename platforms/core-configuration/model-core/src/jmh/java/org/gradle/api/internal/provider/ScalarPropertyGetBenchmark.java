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
package org.gradle.api.internal.provider;

import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.internal.evaluation.EvaluationContext;
import org.gradle.internal.evaluation.EvaluationOwner;
import org.gradle.internal.evaluation.EvaluationScopeContext;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

/**
 * Decomposes the cost of a scalar {@code Property.get()} into its parts, so the remaining ~5ns can be
 * attributed rather than guessed at.
 *
 * <p>The layers, cheapest first: a raw ThreadLocal lookup, the whole evaluation scope, a bare
 * provider read with no property around it, and finally the property read itself.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(3)
public class ScalarPropertyGetBenchmark {

    private final PropertyHost host = producer -> null;

    private final ThreadLocal<Object> threadLocal = ThreadLocal.withInitial(Object::new);

    private Property<String> property;
    private Property<String> finalizedProperty;
    private Property<String> conventionProperty;
    private ProviderInternal<String> bareFixedProvider;
    private EvaluationOwner scopeOwner;

    @Setup(Level.Trial)
    public void setup() {
        property = new DefaultProperty<>(host, String.class);
        property.set("explicit-value");

        finalizedProperty = new DefaultProperty<>(host, String.class);
        finalizedProperty.set("explicit-value");
        finalizedProperty.finalizeValue();

        conventionProperty = new DefaultProperty<>(host, String.class);
        conventionProperty.convention("convention-value");

        bareFixedProvider = Providers.of("explicit-value");
        scopeOwner = (EvaluationOwner) property;
    }

    // ---- layer 0: the primitives the scope is built from ----

    @Benchmark
    public void threadLocalGet(Blackhole bh) {
        bh.consume(threadLocal.get());
    }

    @Benchmark
    public void evaluationScopeOpenClose(Blackhole bh) {
        try (EvaluationScopeContext context = EvaluationContext.current().open(scopeOwner)) {
            bh.consume(context);
        }
    }

    // ---- layer 1: a provider with no property wrapper and no scope ----

    @Benchmark
    public void bareFixedProviderGet(Blackhole bh) {
        bh.consume(bareFixedProvider.get());
    }

    /**
     * Same as above but through {@code calculateValue}, which adds the
     * {@code pushWhenMissing(getDeclaredDisplayName())} pair that does nothing for a present value.
     */
    @Benchmark
    public void bareFixedProviderCalculateValue(Blackhole bh) {
        bh.consume(bareFixedProvider.calculateValue(ValueSupplier.ValueConsumer.IgnoreUnsafeRead));
    }

    // ---- layer 2: the full property read ----

    @Benchmark
    public void propertyGet(Blackhole bh) {
        bh.consume(property.get());
    }

    @Benchmark
    public void finalizedPropertyGet(Blackhole bh) {
        bh.consume(finalizedProperty.get());
    }

    @Benchmark
    public void conventionPropertyGet(Blackhole bh) {
        bh.consume(conventionProperty.get());
    }

    @Benchmark
    public void propertyGetOrNull(Blackhole bh) {
        bh.consume(property.getOrNull());
    }

    @Benchmark
    public void propertyIsPresent(Blackhole bh) {
        bh.consume(property.isPresent());
    }
}
