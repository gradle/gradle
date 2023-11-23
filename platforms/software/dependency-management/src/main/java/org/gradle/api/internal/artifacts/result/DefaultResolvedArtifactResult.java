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

import com.google.common.collect.ImmutableSet;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.artifacts.result.ResolvedVariantResult;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.component.Artifact;
import org.gradle.internal.DisplayName;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class DefaultResolvedArtifactResult implements ResolvedArtifactResult {
    private final ComponentArtifactIdentifier identifier;
    private final ResolvedVariantResult variant;
    private final Class<? extends Artifact> type;
    private final File file;
    private final Set<ResolvedVariantResult> variants;

    public DefaultResolvedArtifactResult(ComponentArtifactIdentifier identifier,
                                         AttributeContainer variantAttributes,
                                         List<? extends Capability> capabilities,
                                         DisplayName variantDisplayName,
                                         Class<? extends Artifact> type,
                                         File file) {
        this.identifier = identifier;
        this.variant = new DefaultResolvedVariantResult(identifier.getComponentIdentifier(), variantDisplayName, variantAttributes, capabilities, null);
        this.variants = Collections.singleton(variant);
        this.type = type;
        this.file = file;
    }

    private DefaultResolvedArtifactResult(DefaultResolvedArtifactResult result, DefaultResolvedVariantResult additionalVariantResult) {
        this.identifier = result.identifier;
        this.variant = result.variant;
        this.variants = ImmutableSet.<ResolvedVariantResult>builder()
            .addAll(result.variants)
            .add(additionalVariantResult)
            .build();
        this.type = result.type;
        this.file = result.file;
    }

    @Override
    public String toString() {
        return identifier.getDisplayName();
    }

    @Override
    public ComponentArtifactIdentifier getId() {
        return identifier;
    }

    @Override
    public Class<? extends Artifact> getType() {
        return type;
    }

    @Override
    public File getFile() {
        return file;
    }

    @Override
    public ResolvedVariantResult getVariant() {
        return variant;
    }

    @Override
    public Set<ResolvedVariantResult> getVariants() {
        return variants;
    }

    public DefaultResolvedArtifactResult withAddedVariant(AttributeContainer variantAttributes, List<? extends Capability> capabilities, DisplayName variantName) {
        return new DefaultResolvedArtifactResult(this, new DefaultResolvedVariantResult(identifier.getComponentIdentifier(), variantName, variantAttributes, capabilities, null));
    }
}
