/*
 * Copyright 2014 the original author or authors.
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
package org.gradle.api.internal.artifacts.result;

import com.google.common.collect.ImmutableList;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.artifacts.result.ResolvedVariantResult;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.component.Artifact;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.DisplayName;
import org.gradle.internal.component.external.model.ImmutableCapabilities;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.util.List;
import java.util.Optional;

public class DefaultResolvedArtifactResult implements ResolvedArtifactResult {

    private final ComponentArtifactIdentifier identifier;
    private final ImmutableAttributes attributes;
    private final ImmutableCapabilities capabilities;
    private final DisplayName displayName;
    private final Class<? extends Artifact> type;
    private final File file;

    private final ResolvedVariantResult variantView;

    public DefaultResolvedArtifactResult(
        ComponentArtifactIdentifier identifier,
        ImmutableAttributes attributes,
        ImmutableCapabilities capabilities,
        DisplayName displayName,
        Class<? extends Artifact> type,
        File file
    ) {
        this.identifier = identifier;
        this.attributes = attributes;
        this.capabilities = capabilities;
        this.displayName = displayName;
        this.type = type;
        this.file = file;
        this.variantView = new ArtifactVariantView();
    }

    @Override
    public ComponentArtifactIdentifier getId() {
        return identifier;
    }

    @Override
    public File getFile() {
        return file;
    }

    @Override
    public ImmutableAttributes getAttributes() {
        return attributes;
    }

    @Override
    public ImmutableCapabilities getCapabilities() {
        return capabilities;
    }

    @Override
    public String toString() {
        return identifier.getDisplayName();
    }

    // Below methods to be deprecated in 10.x

    @Override
    public Class<? extends Artifact> getType() {
        return type;
    }

    @Override
    public ResolvedVariantResult getVariant() {
        return variantView;
    }

    private class ArtifactVariantView implements ResolvedVariantResult {

        private volatile @Nullable ImmutableList<Capability> capabilitiesList = null;

        @Override
        public ComponentIdentifier getOwner() {
            return identifier.getComponentIdentifier();
        }

        @Override
        public AttributeContainer getAttributes() {
            return attributes;
        }

        @Override
        public String getDisplayName() {
            return displayName.getDisplayName();
        }

        @Override
        public List<Capability> getCapabilities() {
            ImmutableList<Capability> capabilities = capabilitiesList;
            if (capabilities == null) {
                ImmutableList<Capability> copy = ImmutableList.copyOf(DefaultResolvedArtifactResult.this.capabilities);
                this.capabilitiesList = copy;
                return copy;
            }
            return capabilities;
        }

        @Override
        public Optional<ResolvedVariantResult> getExternalVariant() {
            return Optional.empty();
        }

    }

}
