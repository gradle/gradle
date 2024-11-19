/*
 * Copyright 2024 the original author or authors.
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
package org.gradle.internal.evaluation;

import org.gradle.api.internal.provider.DefaultProperty;
import org.gradle.api.internal.provider.DefaultProvider;
import org.gradle.api.internal.provider.PropertyHost;
import org.gradle.api.internal.provider.TransformBackedProvider;
import org.gradle.api.provider.Property;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

@State(Scope.Thread)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Fork(2)
public class EvaluationContextPerfTest {
    private final PropertyHost host = producer -> null;

    @SuppressWarnings("FieldMayBeFinal")
    private String value = "value";

    private Property<String> property;

    @Setup
    public void setUp() {
        property = new DefaultProperty<>(host, String.class);
        property.set(new TransformBackedProvider<>(String.class, new DefaultProvider<>(() -> value), v -> v + v));
    }

    @Benchmark
    public void getPropertyValue(Blackhole bh) {
        bh.consume(property.get());
    }
}
