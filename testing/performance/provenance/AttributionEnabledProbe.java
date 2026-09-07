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
import org.gradle.api.internal.provider.provenance.AttributedProperty;
import org.gradle.api.internal.provider.provenance.Attribution;
import org.gradle.api.internal.provider.provenance.PropertyProvenanceHost;
import org.gradle.api.internal.provider.provenance.ScopeIdentity;
import org.gradle.configuration.ConfigurationTargetIdentifier;
import org.gradle.configuration.PropertyProvenanceRegistry;
import org.gradle.api.internal.project.ProjectIdentity;
import org.gradle.internal.Describables;
import org.gradle.internal.code.DefaultUserCodeApplicationContext;
import org.gradle.internal.code.UserCodeSource;
import org.gradle.internal.state.ModelObject;
import org.gradle.util.Path;

/** Real S2 registry/factory capture with first-application and steady-state allocation controls. */
public class AttributionEnabledProbe {
    public static void main(String[] args) {
        DefaultUserCodeApplicationContext context = new DefaultUserCodeApplicationContext();
        PropertyProvenanceRegistry registry = new PropertyProvenanceRegistry(true, context);
        ConfigurationTargetIdentifier target = ConfigurationTargetIdentifier.of(ProjectIdentity.forSubproject(Path.ROOT, Path.path(":source")));
        ScopeIdentity owner = new ScopeIdentity(":", ":target");
        PropertyProvenanceHost host = new PropertyProvenanceHost() {
            @Override
            public ScopeIdentity getOwnerScope() { return owner; }
            @Override
            public String newOccurrenceScope() { return registry.newOccurrenceScope(); }
            @Override
            public Attribution currentAttribution() { return registry.currentAttribution(); }
            @Override
            public String beforeRead(ModelObject producer) { return null; }
        };
        DefaultPropertyFactory factory = new DefaultPropertyFactory(host);
        DefaultProperty<String> property = factory.property(String.class);
        ProvenanceAllocationProbe.layout("AttributedProperty", property);
        ProvenanceAllocationProbe.measure("enabled factory construct", 10000, () -> ProvenanceAllocationProbe.consume(factory.property(String.class)));
        UserCodeSource source = registry.binarySource(Describables.of("plugin"), "Plugin", "plugin", target);
        context.apply(source, id -> {
            property.set("warm capture");
            ProvenanceAllocationProbe.measure("steady accepted binding", 100000, () -> property.set("value"));
            ProvenanceAllocationProbe.measure("steady attribution lookup", 100000, () -> ProvenanceAllocationProbe.consume(registry.currentAttribution()));
        });
        // Each first-application sample includes matching context registration/restoration and a new scoped source.
        ProvenanceAllocationProbe.measure("application registration control", 1000, () -> context.apply(
            registry.binarySource(Describables.of("plugin"), "Plugin", "plugin", target), id -> ProvenanceAllocationProbe.consume(id)
        ));
        ProvenanceAllocationProbe.measure("first accepted application", 1000, () -> context.apply(
            registry.binarySource(Describables.of("plugin"), "Plugin", "plugin", target), id -> property.set("value")
        ));
        DefaultProperty<?>[] properties = new DefaultProperty<?>[4];
        for (int i = 0; i < properties.length; i++) {
            DefaultProperty<String> value = i < 2 ? factory.property(String.class) : new DefaultPropertyFactory(PropertyHost.NO_OP).property(String.class);
            value.set("value");
            if (i % 2 == 0) {
                value.finalizeValue();
            }
            properties[i] = value;
        }
        AttributionBaselineProbe.measureReads(properties);
        // Check only the latest immutable record is retained by the property; no update/history list exists in S2.
        ProvenanceAllocationProbe.layout("latest occurrence", ((AttributedProperty<?>) property).getLastAcceptedMutation());
    }
}
