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

import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
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

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Benchmarks the query hot paths of properties whose values are constants.
 *
 * <p>These are the shapes that dominate a real build: a property assigned a constant in a build
 * script, and the same property read through a user {@code map {}} chain. Run with {@code -prof gc}
 * to see the per-read allocation, which is what most of these cases are really measuring.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
public class ProviderHotPathBenchmark {

    private static final int LIST_SIZE = 10;

    private final PropertyHost host = producer -> null;

    private Property<String> fixedProperty;
    private Provider<String> mappedFixedProperty;
    private ListProperty<String> listProperty;
    private Provider<Integer> mappedListProperty;
    private MapProperty<String, String> mapProperty;
    private EvaluationOwner scopeOwner;

    @Setup(Level.Trial)
    public void setup() {
        fixedProperty = new DefaultProperty<>(host, String.class);
        fixedProperty.set("explicit-value");
        mappedFixedProperty = fixedProperty.map(v -> v);

        listProperty = new DefaultListProperty<>(host, String.class);
        for (int i = 0; i < LIST_SIZE; i++) {
            listProperty.add("element-" + i);
        }
        mappedListProperty = listProperty.map(List::size);

        scopeOwner = (EvaluationOwner) fixedProperty;

        mapProperty = new DefaultMapProperty<>(host, String.class, String.class);
        for (int i = 0; i < LIST_SIZE; i++) {
            mapProperty.put("key-" + i, "value-" + i);
        }
    }

    /**
     * The simplest possible read: one {@code EvaluationContext} scope, one {@code Value} allocation.
     */
    @Benchmark
    public void getFixedProperty(Blackhole bh) {
        bh.consume(fixedProperty.get());
    }

    /**
     * Adds {@code TransformBackedProvider.beforeRead}, which walks the upstream producer graph.
     */
    @Benchmark
    public void getMappedFixedProperty(Blackhole bh) {
        bh.consume(mappedFixedProperty.get());
    }

    @Benchmark
    public void isPresentFixedProperty(Blackhole bh) {
        bh.consume(fixedProperty.isPresent());
    }

    @Benchmark
    public void getListProperty(Blackhole bh) {
        bh.consume(listProperty.get());
    }

    /**
     * The expensive shape: {@code beforeRead} rebuilds a {@code ValueProducer} per collector on
     * every single read.
     */
    @Benchmark
    public void getMappedListProperty(Blackhole bh) {
        bh.consume(mappedListProperty.get());
    }

    @Benchmark
    public void isPresentListProperty(Blackhole bh) {
        bh.consume(listProperty.isPresent());
    }

    @Benchmark
    public void getMapProperty(Blackhole bh) {
        bh.consume(mapProperty.get());
    }

    /**
     * Isolates the cycle-detection scope that {@code AbstractProperty} opens on every read. This is
     * the ceiling on what skipping the scope for constant-valued properties can save.
     */
    @Benchmark
    public void evaluationScopeOpenClose(Blackhole bh) {
        try (EvaluationScopeContext context = EvaluationContext.current().open(scopeOwner)) {
            bh.consume(context);
        }
    }
}
