/*
 * Copyright 2025 the original author or authors.
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
 * Benchmarks for reading the value of a {@link Property} under different
 * value-source configurations.
 *
 * <p>Designed for high sample counts so the JFR profiler can capture
 * meaningful CPU and allocation data.
 *
 * <p>Run with: {@code ./gradlew :model-core:jmh -Pjmh.include=PropertyGetBenchmark}
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 10, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(2)
public class PropertyGetBenchmark {

    private final PropertyHost host = producer -> null;

    private Property<String> setProperty;
    private Property<String> conventionProperty;
    private Property<String> setOverConventionProperty;
    private Property<String> providerProperty;
    private Property<String> conventionProviderProperty;
    private Property<String> finalizedSetProperty;
    private Property<String> finalizedConventionProperty;
    private Property<String> finalizedProviderProperty;

    @Setup(Level.Trial)
    public void setup() {
        // Property with explicit value via set()
        setProperty = new DefaultProperty<>(host, String.class);
        setProperty.set("explicit-value");

        // Property with convention value only
        conventionProperty = new DefaultProperty<>(host, String.class);
        conventionProperty.convention("convention-value");

        // Property with convention AND explicit set (set wins)
        setOverConventionProperty = new DefaultProperty<>(host, String.class);
        setOverConventionProperty.convention("convention-value");
        setOverConventionProperty.set("explicit-value");

        // Property backed by a Provider
        providerProperty = new DefaultProperty<>(host, String.class);
        providerProperty.set(new DefaultProvider<>(() -> "provider-value"));

        // Convention backed by a Provider
        conventionProviderProperty = new DefaultProperty<>(host, String.class);
        conventionProviderProperty.convention(new DefaultProvider<>(() -> "convention-provider-value"));

        // Finalized properties
        finalizedSetProperty = new DefaultProperty<>(host, String.class);
        finalizedSetProperty.set("finalized-explicit");
        finalizedSetProperty.finalizeValue();

        finalizedConventionProperty = new DefaultProperty<>(host, String.class);
        finalizedConventionProperty.convention("finalized-convention");
        finalizedConventionProperty.finalizeValue();

        finalizedProviderProperty = new DefaultProperty<>(host, String.class);
        finalizedProviderProperty.set(new DefaultProvider<>(() -> "finalized-provider"));
        finalizedProviderProperty.finalizeValue();
    }

    @Benchmark
    public void getExplicitValue(Blackhole bh) {
        bh.consume(setProperty.get());
    }

    @Benchmark
    public void getConventionValue(Blackhole bh) {
        bh.consume(conventionProperty.get());
    }

    @Benchmark
    public void getSetOverConventionValue(Blackhole bh) {
        bh.consume(setOverConventionProperty.get());
    }

    @Benchmark
    public void getProviderValue(Blackhole bh) {
        bh.consume(providerProperty.get());
    }

    @Benchmark
    public void getConventionProviderValue(Blackhole bh) {
        bh.consume(conventionProviderProperty.get());
    }

    @Benchmark
    public void getFinalizedSetValue(Blackhole bh) {
        bh.consume(finalizedSetProperty.get());
    }

    @Benchmark
    public void getFinalizedConventionValue(Blackhole bh) {
        bh.consume(finalizedConventionProperty.get());
    }

    @Benchmark
    public void getFinalizedProviderValue(Blackhole bh) {
        bh.consume(finalizedProviderProperty.get());
    }
}
