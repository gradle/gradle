/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.internal.component.model;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.Describables;
import org.gradle.internal.component.external.model.ImmutableCapabilities;

import java.util.List;
import java.util.Set;

/**
 * A variant in the graph derived from another variant, which exposes specific artifacts
 * during artifact resolution instead of the original variant's artifacts.
 */
public class SpecificArtifactsVariantGraphResolveState implements VariantGraphResolveState {

    private final long instanceId;
    private final ComponentIdentifier componentIdentifier;
    private final VariantGraphResolveState delegate;
    private final List<IvyArtifactName> artifactNames;
    private final ImmutableAttributes componentAttributes;

    public SpecificArtifactsVariantGraphResolveState(
        long instanceId,
        ComponentIdentifier componentIdentifier,
        VariantGraphResolveState delegate,
        List<IvyArtifactName> artifactNames,
        ImmutableAttributes componentAttributes
    ) {
        this.instanceId = instanceId;
        this.componentIdentifier = componentIdentifier;
        this.delegate = delegate;
        this.artifactNames = artifactNames;
        this.componentAttributes = componentAttributes;
    }

    @Override
    public long getInstanceId() {
        return instanceId;
    }

    @Override
    public String getName() {
        return delegate.getName() + " artifacts " + artifactNames;
    }

    @Override
    public ImmutableAttributes getAttributes() {
        return delegate.getAttributes();
    }

    @Override
    public ImmutableCapabilities getCapabilities() {
        return delegate.getCapabilities();
    }

    @Override
    public VariantGraphResolveMetadata getMetadata() {
        return delegate.getMetadata();
    }

    @Override
    public List<? extends DependencyMetadata> getDependencies() {
        return delegate.getDependencies();
    }

    @Override
    public List<? extends ExcludeMetadata> getExcludes() {
        return delegate.getExcludes();
    }

    @Override
    public VariantArtifactResolveState prepareForArtifactResolution() {
        return new SpecificArtifactsVariantArtifactResolveState();
    }

    /**
     * Exposes the specific artifacts of this variant to artifact selection as a single adhoc artifact set.
     */
    private class SpecificArtifactsVariantArtifactResolveState implements VariantArtifactResolveState {

        @Override
        public ImmutableList<ComponentArtifactMetadata> getAdhocArtifacts(List<IvyArtifactName> dependencyArtifacts) {
            return delegate.prepareForArtifactResolution().getAdhocArtifacts(dependencyArtifacts);
        }

        @Override
        public Set<? extends VariantResolveMetadata> getArtifactVariants() {
            ImmutableList<ComponentArtifactMetadata> artifacts = getAdhocArtifacts(artifactNames);

            VariantResolveMetadata.Identifier identifier = artifacts.size() == 1
                ? new SingleArtifactVariantIdentifier(artifacts.iterator().next().getId())
                : null;

            return ImmutableSet.of(new DefaultVariantMetadata(
                "adhoc",
                identifier,
                Describables.of("adhoc variant for", componentIdentifier),
                componentAttributes,
                artifacts,
                ImmutableCapabilities.EMPTY
            ));
        }

    }

    /**
     * Identifier for adhoc variants with a single artifact
     */
    private static class SingleArtifactVariantIdentifier implements VariantResolveMetadata.Identifier {

        private final ComponentArtifactIdentifier artifactIdentifier;

        public SingleArtifactVariantIdentifier(ComponentArtifactIdentifier artifactIdentifier) {
            this.artifactIdentifier = artifactIdentifier;
        }

        @Override
        public int hashCode() {
            return artifactIdentifier.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != getClass()) {
                return false;
            }
            SingleArtifactVariantIdentifier other = (SingleArtifactVariantIdentifier) obj;
            return artifactIdentifier.equals(other.artifactIdentifier);
        }

    }

}
