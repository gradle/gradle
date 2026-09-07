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
import org.gradle.api.internal.provider.DefaultPropertyFactory;
import org.gradle.api.internal.provider.PropertyHost;

/** Matched factory construction and mixed read control, compilable against the unmodified baseline. */
public class AttributionBaselineProbe {
    public static void main(String[] args) throws Exception {
        ProvenanceAllocationProbe.main(args);
        DefaultPropertyFactory factory = new DefaultPropertyFactory(PropertyHost.NO_OP);
        ProvenanceAllocationProbe.measure("factory construct", 100000, () -> ProvenanceAllocationProbe.consume(factory.property(String.class)));
        DefaultProperty<?>[] properties = new DefaultProperty<?>[4];
        for (int i = 0; i < properties.length; i++) {
            DefaultProperty<String> property = factory.property(String.class);
            property.set("value");
            if (i % 2 == 0) {
                property.finalizeValue();
            }
            properties[i] = property;
        }
        measureReads(properties);
    }

    static void measureReads(DefaultProperty<?>[] properties) {
        ProvenanceAllocationProbe.measure("mixed reads (4)", 100000, () -> {
            for (DefaultProperty<?> property : properties) {
                ProvenanceAllocationProbe.consume(property.get());
            }
        });
    }
}
