/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.internal.resolve.resolver;

import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeSpec;
import org.gradle.api.internal.artifacts.type.ArtifactTypeRegistry;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.component.model.ComponentResolveMetadata;
import org.gradle.internal.component.model.ModuleSources;
import org.gradle.internal.component.model.VariantResolveMetadata;

import javax.annotation.Nullable;
import java.util.Set;

public interface OriginArtifactSelector {
    /**
     * Creates a set that will resolve the artifacts of the given configuration, minus those artifacts that are excluded.
     */
    @Nullable
    ArtifactSet resolveArtifacts(ComponentResolveMetadataForArtifactSelection component, ArtifactTypeRegistry artifactTypeRegistry, ExcludeSpec exclusions, ImmutableAttributes overriddenAttributes);

    interface ComponentResolveMetadataForArtifactSelection {

        ComponentIdentifier getId();

        ModuleVersionIdentifier getModuleVersionId();

        ModuleSources getSources();

        Set<? extends VariantResolveMetadata> getVariantsForArtifactSelection();

        AttributesSchemaInternal getAttributesSchema();
    }
    class DefaultComponentResolveMetadataForArtifactSelection implements OriginArtifactSelector.ComponentResolveMetadataForArtifactSelection {
        private final ComponentResolveMetadata delegate;
        private final Set<? extends VariantResolveMetadata> variants;

        public DefaultComponentResolveMetadataForArtifactSelection(ComponentResolveMetadata delegate, Set<? extends VariantResolveMetadata> variants) {
            this.delegate = delegate;
            this.variants = variants;
        }

        @Override
        public ComponentIdentifier getId() {
            return delegate.getId();
        }

        @Override
        public ModuleVersionIdentifier getModuleVersionId() {
            return delegate.getModuleVersionId();
        }

        @Override
        public ModuleSources getSources() {
            return delegate.getSources();
        }

        @Override
        public AttributesSchemaInternal getAttributesSchema() {
            return delegate.getAttributesSchema();
        }

        @Override
        public Set<? extends VariantResolveMetadata> getVariantsForArtifactSelection() {
            return variants;
        }
    }
}
