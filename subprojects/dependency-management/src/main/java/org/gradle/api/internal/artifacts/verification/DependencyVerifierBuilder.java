/*
 * Copyright 2019 the original author or authors.
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
package org.gradle.api.internal.artifacts.verification;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.artifacts.verification.model.ArtifactVerificationMetadata;
import org.gradle.api.internal.artifacts.verification.model.ChecksumKind;
import org.gradle.api.internal.artifacts.verification.model.ComponentVerificationMetadata;
import org.gradle.api.internal.artifacts.verification.model.ImmutableArtifactVerificationMetadata;
import org.gradle.api.internal.artifacts.verification.model.ImmutableComponentVerificationMetadata;
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class DependencyVerifierBuilder {
    private final Map<ModuleComponentIdentifier, ComponentVerificationsBuilder> byComponent = Maps.newLinkedHashMap();

    public void addChecksum(ModuleComponentArtifactIdentifier artifact, ChecksumKind kind, String value) {
        addChecksum(artifact, kind, () -> value);
    }

    public synchronized void addChecksum(ModuleComponentArtifactIdentifier artifact, ChecksumKind kind, Supplier<String> generator) {
        ModuleComponentIdentifier componentIdentifier = artifact.getComponentIdentifier();
        byComponent.computeIfAbsent(componentIdentifier, id -> new ComponentVerificationsBuilder(id))
            .addChecksum(artifact, kind, generator);
    }

    public DependencyVerifier build() {
        ImmutableMap.Builder<ComponentIdentifier, ComponentVerificationMetadata> builder = ImmutableMap.builderWithExpectedSize(byComponent.size());
        for (Map.Entry<ModuleComponentIdentifier, ComponentVerificationsBuilder> entry : byComponent.entrySet()) {
            builder.put(entry.getKey(), entry.getValue().build());
        }
        return new DependencyVerifier(builder.build());
    }

    private static class ComponentVerificationsBuilder {
        private final ModuleComponentIdentifier component;
        private final Map<ModuleComponentArtifactIdentifier, EnumMap<ChecksumKind, String>> checksums = Maps.newLinkedHashMap();

        private ComponentVerificationsBuilder(ModuleComponentIdentifier component) {
            this.component = component;
        }

        void addChecksum(ModuleComponentArtifactIdentifier artifact, ChecksumKind kind, Supplier<String> value) {
            checksums.computeIfAbsent(artifact, id -> Maps.newEnumMap(ChecksumKind.class)).put(kind, value.get());
        }

        private static ArtifactVerificationMetadata toArtifactVerification(Map.Entry<ModuleComponentArtifactIdentifier, EnumMap<ChecksumKind, String>> entry) {
            return new ImmutableArtifactVerificationMetadata(entry.getKey(), entry.getValue());
        }

        ComponentVerificationMetadata build() {
            return new ImmutableComponentVerificationMetadata(component,
                checksums.entrySet()
                    .stream()
                    .map(ComponentVerificationsBuilder::toArtifactVerification)
                    .collect(Collectors.toList())
                );
        }
    }
}
