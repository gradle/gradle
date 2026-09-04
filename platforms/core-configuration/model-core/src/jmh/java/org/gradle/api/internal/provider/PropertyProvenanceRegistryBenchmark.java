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

import org.gradle.api.internal.provider.provenance.PropertyProvenanceKind;
import org.gradle.api.internal.provider.provenance.PropertyProvenanceRegistry;
import org.gradle.internal.Describables;
import org.gradle.internal.code.UserCodeSource;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.TimeUnit;

/**
 * First registration of application sources, which the steady-state property benchmarks exclude.
 * Includes an amortized registry/map setup cost, but excludes source construction and property state.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
public class PropertyProvenanceRegistryBenchmark {
    private static final int SOURCE_COUNT = 64;
    private final UserCodeSource[] sources = new UserCodeSource[SOURCE_COUNT];

    @Setup
    public void setup() {
        for (int i = 0; i < SOURCE_COUNT; i++) {
            // Equal plugin metadata must still preserve distinct application sources.
            sources[i] = new UserCodeSource.Binary(Describables.of("plugin 'benchmark'"), "BenchmarkPlugin", "benchmark");
        }
    }

    @Benchmark
    @OperationsPerInvocation(SOURCE_COUNT)
    public PropertyProvenanceRegistry registerOrigins() {
        PropertyProvenanceRegistry registry = new PropertyProvenanceRegistry(true);
        for (UserCodeSource source : sources) {
            registry.recordFor(source, PropertyProvenanceKind.EXPLICIT_SOURCE, null);
            registry.recordFor(source, PropertyProvenanceKind.CONVENTION, null);
        }
        return registry;
    }
}
