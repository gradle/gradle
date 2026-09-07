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

import org.gradle.api.internal.provider.DefaultProperty;
import org.gradle.api.internal.provider.PropertyHost;

/** Matched engine work for bounded S3 allocation comparisons. Long chains are constructed, not evaluated. */
public class EffectiveBaselineProbe {
    protected DefaultProperty<String> property() {
        return new DefaultProperty<>(PropertyHost.NO_OP, String.class);
    }

    public static void main(String[] args) {
        new EffectiveBaselineProbe().run();
    }

    protected void run() {
        DefaultProperty<String> value = property();
        ProvenanceAllocationProbe.layout("property", value);
        value.set("root");
        ProvenanceAllocationProbe.measure("binding", 10000, () -> value.set("root"));
        ProvenanceAllocationProbe.measure("copy", 10000, () -> ProvenanceAllocationProbe.consume(value.shallowCopy()));
        ProvenanceAllocationProbe.measure("construct bind finalize", 10000, () -> {
            DefaultProperty<String> next = property();
            next.set("root");
            next.finalizeValue();
            ProvenanceAllocationProbe.consume(next);
        });
        ProvenanceAllocationProbe.measure("replace and discard", 10000, () -> {
            value.replace(previous -> previous.map(input -> input));
            value.set("root");
        });
        for (int count : new int[]{0, 1, 8, 128, 4096}) {
            ProvenanceAllocationProbe.measure("construct chain " + count, Math.max(10, 10000 / Math.max(1, count)), () -> {
                DefaultProperty<String> next = property();
                next.set("root");
                for (int i = 0; i < count; i++) {
                    next.replace(previous -> previous.map(input -> input));
                }
                ProvenanceAllocationProbe.consume(next);
            });
        }
        DefaultProperty<String> missing = property();
        ProvenanceAllocationProbe.measure("missing query failure", 1000, () -> {
            try {
                missing.get();
                throw new AssertionError("Expected a missing value");
            } catch (IllegalStateException failure) {
                ProvenanceAllocationProbe.consume(failure);
            }
        });
        DefaultProperty<String> locked = property();
        locked.set("root");
        locked.finalizeValue();
        ProvenanceAllocationProbe.measure("rejected mutation failure", 1000, () -> {
            try {
                locked.set("rejected");
                throw new AssertionError("Expected a rejected mutation");
            } catch (IllegalStateException failure) {
                ProvenanceAllocationProbe.consume(failure);
            }
        });
    }
}
