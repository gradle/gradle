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
import com.google.common.collect.Sets;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.artifacts.verification.model.ArtifactVerificationMetadata;
import org.gradle.api.internal.artifacts.verification.model.Checksum;
import org.gradle.api.internal.artifacts.verification.model.ChecksumKind;
import org.gradle.api.internal.artifacts.verification.model.ComponentVerificationMetadata;
import org.gradle.api.internal.artifacts.verification.model.ImmutableArtifactVerificationMetadata;
import org.gradle.api.internal.artifacts.verification.model.ImmutableComponentVerificationMetadata;
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class DependencyVerifierBuilder {
    private final Map<ModuleComponentIdentifier, ComponentVerificationsBuilder> byComponent = Maps.newLinkedHashMap();

    public void addChecksum(ModuleComponentArtifactIdentifier artifact, ChecksumKind kind, String value, @Nullable String origin) {
        ModuleComponentIdentifier componentIdentifier = artifact.getComponentIdentifier();
        byComponent.computeIfAbsent(componentIdentifier, id -> new ComponentVerificationsBuilder(id))
            .addChecksum(artifact, kind, value, origin);
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
        private final Map<String, ChecksumsBuilder> checksums = Maps.newLinkedHashMap();

        private ComponentVerificationsBuilder(ModuleComponentIdentifier component) {
            this.component = component;
        }

        void addChecksum(ModuleComponentArtifactIdentifier artifact, ChecksumKind kind, String value, @Nullable String origin) {
            checksums.computeIfAbsent(artifact.getFileName(), id -> new ChecksumsBuilder()).addChecksum(kind, value, origin);
        }

        private static ArtifactVerificationMetadata toArtifactVerification(Map.Entry<String, ChecksumsBuilder> entry) {
            return new ImmutableArtifactVerificationMetadata(entry.getKey(), entry.getValue().build());
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

    private static class ChecksumsBuilder {
        private final Map<ChecksumKind, ChecksumBuilder> builder = Maps.newEnumMap(ChecksumKind.class);

        void addChecksum(ChecksumKind kind, String value, @Nullable String origin) {
            ChecksumBuilder builder = this.builder.computeIfAbsent(kind, ChecksumBuilder::new);
            builder.addChecksum(value);
            if (origin != null) {
                builder.withOrigin(origin);
            }
        }

        List<Checksum> build() {
            return builder.values()
                .stream()
                .map(ChecksumBuilder::build)
                .collect(Collectors.toList());
        }
    }

    private static class ChecksumBuilder {
        private final ChecksumKind kind;
        private String value;
        private String origin;
        private Set<String> alternatives;

        private ChecksumBuilder(ChecksumKind kind) {
            this.kind = kind;
        }

        /**
         * Sets the origin, if not set already. This is
         * mostly used for automatic generation of checksums
         */
        void withOrigin(String origin) {
            if (this.origin == null) {
                this.origin = origin;
            }
        }

        void addChecksum(String checksum) {
            if (value == null) {
                value = checksum;
            } else if (!value.equals(checksum)) {
                if (alternatives == null) {
                    alternatives = Sets.newLinkedHashSet();
                }
                alternatives.add(checksum);
            }
        }

        Checksum build() {
            return new Checksum(
                kind,
                value,
                alternatives,
                origin
            );
        }
    }
}
