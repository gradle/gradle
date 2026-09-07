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
import org.gradle.api.internal.provider.DefaultProvider;
import org.gradle.api.internal.provider.DiagnosticProperty;
import org.gradle.api.internal.provenance.EffectiveProvenanceView;
import org.gradle.api.internal.provenance.ProvenanceRenderer;
import org.gradle.api.internal.provider.AttributedProperty;
import org.gradle.api.internal.provenance.Attribution;
import org.gradle.api.internal.provenance.ContributorKey;
import org.gradle.api.internal.provenance.DiagnosticOrigin;
import org.gradle.api.internal.provider.PropertyProvenanceHost;
import org.gradle.api.internal.provenance.ScopeIdentity;
import org.gradle.internal.state.ModelObject;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.List;

/** S3 metadata allocation and controlled retained-reference probes, with shared prebuilt attribution. */
public class EffectiveEnabledProbe extends EffectiveBaselineProbe {
    private static final ScopeIdentity OWNER = new ScopeIdentity("build", ":project");
    private static final Attribution ATTRIBUTION = new Attribution(new ContributorKey("domain", ContributorKey.Kind.PLUGIN_ID, "plugin"),
        new DiagnosticOrigin(DiagnosticOrigin.Kind.PLUGIN_ID, "plugin", "plugin"), OWNER, null);
    private final Host host = new Host();

    private static final class Host implements PropertyProvenanceHost {
        private long sequence;
        public ScopeIdentity getOwnerScope() { return OWNER; }
        public String newOccurrenceScope() { return "property/" + sequence++; }
        public Attribution currentAttribution() { return ATTRIBUTION; }
        public String beforeRead(ModelObject producer) { return null; }
    }

    @Override
    protected DefaultProperty<String> property() {
        return new DiagnosticProperty<>(host, String.class);
    }

    public static void main(String[] args) {
        EffectiveEnabledProbe probe = new EffectiveEnabledProbe();
        probe.run();
        try {
            Field state = AttributedProperty.class.getDeclaredField("provenance");
            state.setAccessible(true);
            ProvenanceAllocationProbe.layout("ordinary provenance state", state.get(probe.property()));
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
        for (int count : new int[]{0, 8, 4096}) {
            AttributedProperty<String> value = (AttributedProperty<String>) probe.property();
            value.set("root");
            for (int i = 0; i < count; i++) {
                value.replace(previous -> previous.map(input -> input));
            }
            EffectiveProvenanceView view = value.getEffectiveProvenance();
            ProvenanceAllocationProbe.measure("requested formatting " + count + " updates", 1000,
                () -> ProvenanceAllocationProbe.consume(ProvenanceRenderer.configuration(view)));
        }
        for (String scenario : new String[]{"metadata", "copy", "finalization"}) {
            List<WeakReference<?>> references = retainedReferences(scenario);
            for (int i = 0; i < 5; i++) {
                System.gc();
            }
            long retained = references.stream().filter(reference -> reference.get() != null).count();
            System.out.println("{\"retention\":\"" + scenario + "\",\"unwantedReferences\":" + retained + "}");
            if (retained != 0) {
                throw new AssertionError("Unexpected retention in " + scenario);
            }
        }
    }

    private static List<WeakReference<?>> retainedReferences(String scenario) {
        Host host = new Host();
        AttributedProperty<String> property = new DiagnosticProperty<>(host, String.class);
        DefaultProvider<String> supplier = new DefaultProvider<>(() -> "root");
        property.set(supplier);
        property.replace(previous -> previous.map(input -> input));
        if (scenario.equals("metadata")) {
            ProvenanceAllocationProbe.consume(property.getEffectiveProvenance());
            return List.of(new WeakReference<>(property), new WeakReference<>(supplier), new WeakReference<>(host));
        }
        if (scenario.equals("copy")) {
            Object copy = property.shallowCopy();
            DefaultProvider<String> displaced = new DefaultProvider<>(() -> "later");
            property.set(displaced);
            ProvenanceAllocationProbe.consume(copy);
            return List.of(new WeakReference<>(property), new WeakReference<>(displaced), new WeakReference<>(host));
        }
        property.finalizeValue();
        ProvenanceAllocationProbe.consume(property);
        return List.of(new WeakReference<>(supplier), new WeakReference<>(host));
    }
}
