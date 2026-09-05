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

import org.gradle.api.internal.provider.provenance.PropertyCallSites;
import org.gradle.api.internal.provider.provenance.PropertyProvenanceKind;
import org.gradle.api.internal.provider.provenance.PropertyProvenanceRecord;
import org.gradle.api.internal.provider.provenance.PropertyProvenanceRegistry;
import org.gradle.internal.Describables;
import org.gradle.internal.code.DefaultUserCodeApplicationContext;
import org.gradle.internal.code.UserCodeApplicationContext;
import org.gradle.internal.code.UserCodeSource;
import org.gradle.internal.state.ModelObject;
import org.jspecify.annotations.Nullable;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.TimeUnit;

/**
 * Successful property operations, including the existing instrumented-call bridge.
 * This is not a whole-build benchmark or a baseline with provenance fields removed.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(3)
public class PropertyProvenanceBenchmark {
    @Param({"disabled", "origins", "locations"})
    public String mode;

    private PropertyHost host;
    private DefaultProperty<Object> property;
    private DefaultProperty<?>[] mixedProperties;
    private final DefaultUserCodeApplicationContext context = new DefaultUserCodeApplicationContext();
    private UserCodeApplicationContext.Application application;
    private final ProviderInternal<Object> supplier = Providers.of("value");

    @Setup
    public void setup() {
        PropertyProvenanceRegistry registry = new PropertyProvenanceRegistry(!mode.equals("disabled"), mode.equals("locations"));
        UserCodeSource source = new UserCodeSource.Binary(Describables.of("plugin 'benchmark'"), "BenchmarkPlugin", "benchmark");
        context.apply(source, ignored -> application = context.current());
        host = new PropertyHost() {
            @Override
            public @Nullable String beforeRead(@Nullable ModelObject producer) {
                return null;
            }

            @Override
            public boolean tracksPropertyProvenance() {
                return registry.isEnabled();
            }

            @Override
            public boolean capturesPropertyCallSites() {
                return registry.capturesLocations();
            }

            @Override
            public PropertyProvenanceRecord currentPropertyBinding(PropertyProvenanceKind kind) {
                UserCodeApplicationContext.Application current = context.current();
                return registry.recordFor(current == null ? null : current.getSource(), kind,
                    registry.capturesLocations() ? PropertyCallSites.current() : null);
            }
        };
        property = new DefaultProperty<>(host, Object.class);
        application.reapply(() -> property.set(supplier));
        mixedProperties = new DefaultProperty<?>[4];
        for (int i = 0; i < mixedProperties.length; i++) {
            DefaultProperty<Object> mixed = new DefaultProperty<>(i < 2 ? host : PropertyHost.NO_OP, Object.class);
            application.reapply(() -> mixed.set(supplier));
            if (i % 2 == 0) {
                mixed.finalizeValue();
            }
            mixedProperties[i] = mixed;
        }
    }

    @Benchmark
    public DefaultProperty<Object> createOnly() {
        return new DefaultProperty<>(host, Object.class);
    }

    @Benchmark
    public ProviderInternal<Object> shallowCopy() {
        return property.shallowCopy();
    }

    @Benchmark
    @OperationsPerInvocation(4)
    public int mixedReads() {
        int result = 0;
        // Exercise mutable/finalized and tracked/untracked states at the same read call site.
        for (DefaultProperty<?> mixed : mixedProperties) {
            result += ((String) mixed.get()).length();
        }
        return result;
    }

    @Benchmark
    public DefaultProperty<Object> bindAndFinalizeFixed() {
        DefaultProperty<Object> created = createAndBind();
        created.finalizeValue();
        return created;
    }

    @Benchmark
    public DefaultProperty<Object> createAndBind() {
        return application.reapply(() -> {
            DefaultProperty<Object> created = new DefaultProperty<>(host, Object.class);
            PropertyCallSites.set(created, supplier, "Plugin.java:12");
            return created;
        });
    }

    @Benchmark
    public DefaultProperty<Object> replaceBinding() {
        application.reapply(() -> PropertyCallSites.set(property, supplier, "Plugin.java:12"));
        return property;
    }

    @Benchmark
    public DefaultProperty<Object> conventionAndExplicit() {
        return application.reapply(() -> {
            DefaultProperty<Object> created = new DefaultProperty<>(host, Object.class);
            PropertyCallSites.convention(created, supplier, "Plugin.java:10");
            PropertyCallSites.set(created, supplier, "Plugin.java:12");
            return created;
        });
    }

    @Benchmark
    public DefaultProperty<Object> bindAndFinalizeChain() {
        return application.reapply(() -> {
            DefaultProperty<Object> source = new DefaultProperty<>(host, Object.class);
            PropertyCallSites.set(source, supplier, "SourcePlugin.java:12");
            DefaultProperty<Object> middle = new DefaultProperty<>(host, Object.class);
            PropertyCallSites.set(middle, source, "MiddlePlugin.java:12");
            DefaultProperty<Object> target = new DefaultProperty<>(host, Object.class);
            PropertyCallSites.set(target, middle, "TargetPlugin.java:12");
            target.finalizeValue();
            return target;
        });
    }
}
