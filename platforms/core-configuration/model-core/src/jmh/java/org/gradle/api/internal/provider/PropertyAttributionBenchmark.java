/*
 * Copyright 2026 Gradle and contributors.
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

import org.gradle.api.internal.provider.provenance.Attribution;
import org.gradle.api.internal.provider.provenance.ContributorKey;
import org.gradle.api.internal.provider.provenance.DiagnosticOrigin;
import org.gradle.api.internal.provider.provenance.PropertyProvenanceHost;
import org.gradle.api.internal.provider.provenance.ScopeIdentity;
import org.gradle.internal.state.ModelObject;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

/** Checks mutable/finalized read dispatch with optional attribution subclasses at the same call site. */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 3, time = 1)
@Fork(2)
public class PropertyAttributionBenchmark {
    @Param({"disabled", "enabled", "mixed"})
    public String tracking;

    private final DefaultProperty<?>[] properties = new DefaultProperty<?>[4];

    @Setup
    public void setup() {
        ScopeIdentity owner = new ScopeIdentity("build", ":app");
        Attribution attribution = new Attribution(new ContributorKey("domain", ContributorKey.Kind.PLUGIN_ID, "plugin"),
            new DiagnosticOrigin(DiagnosticOrigin.Kind.PLUGIN_ID, "plugin", "plugin"), owner, null);
        PropertyProvenanceHost enabled = new PropertyProvenanceHost() {
            private long nextProperty;

            @Override
            public ScopeIdentity getOwnerScope() {
                return owner;
            }

            @Override
            public String newOccurrenceScope() {
                return "property/" + nextProperty++;
            }

            @Override
            public Attribution currentAttribution() {
                return attribution;
            }

            @Override
            public String beforeRead(ModelObject producer) {
                return null;
            }
        };
        for (int i = 0; i < properties.length; i++) {
            boolean tracked = tracking.equals("enabled") || tracking.equals("mixed") && i < 2;
            DefaultProperty<String> property = new DefaultPropertyFactory(tracked ? enabled : PropertyHost.NO_OP).property(String.class);
            property.set("value");
            if (i % 2 == 0) {
                property.finalizeValue();
            }
            properties[i] = property;
        }
    }

    @Benchmark
    public void reads(Blackhole blackhole) {
        for (DefaultProperty<?> property : properties) {
            blackhole.consume(property.get());
        }
    }
}
