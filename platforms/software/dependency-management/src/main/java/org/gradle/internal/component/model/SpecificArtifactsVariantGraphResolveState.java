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
 * <p>
 * This is modeled as a separate variant instead of adding additional artifacts to the
 * original variant during artifact selection, since the user-specified artifacts are
 * _not_ part of the original variant. By requesting specific artifacts, the user has
 * explicitly requested to create/select a new variant that only contains those artifacts.
 */
public class SpecificArtifactsVariantGraphResolveState implements VariantGraphResolveState {

    private final long instanceId;
    private final ComponentIdentifier componentIdentifier;
    private final VariantGraphResolveState delegate;
    private final List<IvyArtifactName> artifactNames;
    private final ImmutableAttributes componentAttributes;
    private final VariantGraphResolveMetadata metadata;

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
        this.metadata = new SpecificArtifactsVariantGraphResolveMetadata(delegate.getMetadata(), artifactNames, componentAttributes);
    }

    @Override
    public long getInstanceId() {
        return instanceId;
    }

    @Override
    public String getName() {
        return metadata.getName();
    }

    @Override
    public ImmutableAttributes getAttributes() {
        return metadata.getAttributes();
    }

    @Override
    public ImmutableCapabilities getCapabilities() {
        return metadata.getCapabilities();
    }

    @Override
    public VariantGraphResolveMetadata getMetadata() {
        return metadata;
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

    private static class SpecificArtifactsVariantGraphResolveMetadata implements VariantGraphResolveMetadata {

        private final VariantGraphResolveMetadata delegate;
        private final List<IvyArtifactName> artifactNames;
        private final ImmutableAttributes componentAttributes;

        public SpecificArtifactsVariantGraphResolveMetadata(
            VariantGraphResolveMetadata delegate,
            List<IvyArtifactName> artifactNames,
            ImmutableAttributes componentAttributes
        ) {
            this.delegate = delegate;
            this.artifactNames = artifactNames;
            this.componentAttributes = componentAttributes;
        }

        @Override
        public String getName() {
            return delegate.getName() + " with artifacts " + artifactNames;
        }

        @Override
        public VariantIdentifier getId() {
            return new VariantWithSpecificArtifactsIdentifier(delegate.getId(), artifactNames);
        }

        @Override
        public ImmutableAttributes getAttributes() {
            // We do not know the attributes of the specific artifacts, so we fall back the component attributes.
            return componentAttributes;
        }

        @Override
        public ImmutableCapabilities getCapabilities() {
            // We do not know the capabilities of the specific artifacts, so we do not declare any capabilities.
            // This variant should not participate in capability conflict detection.
            return ImmutableCapabilities.EMPTY;
        }

        @Override
        public boolean isTransitive() {
            return delegate.isTransitive();
        }

        @Override
        public boolean isExternalVariant() {
            return delegate.isExternalVariant();
        }
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

            ImmutableList.Builder<ComponentArtifactIdentifier> allArtifactIds = ImmutableList.builderWithExpectedSize(artifacts.size());
            for (ComponentArtifactMetadata artifact : artifacts) {
                allArtifactIds.add(artifact.getId());
            }

            VariantResolveMetadata.Identifier identifier =
                new SpecificArtifactsArtifactSetId(allArtifactIds.build());

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
     * Identifies an adhoc variant of a component that is derived from another variant, but
     * instead provides the artifacts corresponding to the given artifact names.
     */
    public static class VariantWithSpecificArtifactsIdentifier implements VariantIdentifier {

        private final VariantIdentifier delegate;
        private final List<IvyArtifactName> artifactNames;
        private final int hashCode;

        public VariantWithSpecificArtifactsIdentifier(VariantIdentifier delegate, List<IvyArtifactName> artifactNames) {
            this.delegate = delegate;
            this.artifactNames = artifactNames;
            this.hashCode = computeHashCode(artifactNames, delegate);
        }

        @Override
        public ComponentIdentifier getComponentId() {
            return delegate.getComponentId();
        }

        public VariantIdentifier getDelegate() {
            return delegate;
        }

        public List<IvyArtifactName> getArtifactNames() {
            return artifactNames;
        }

        @Override
        public String getDisplayName() {
            return delegate.getDisplayName() + " with artifacts " + artifactNames;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            VariantWithSpecificArtifactsIdentifier that = (VariantWithSpecificArtifactsIdentifier) o;
            return delegate.equals(that.delegate) && artifactNames.equals(that.artifactNames);
        }

        @Override
        public int hashCode() {
            return hashCode;
        }

        private static int computeHashCode(List<IvyArtifactName> artifactNames, VariantIdentifier delegate) {
            int result = delegate.hashCode();
            result = 31 * result + artifactNames.hashCode();
            return result;
        }

    }

    /**
     * Identifier for an adhoc variant artifact set with attributes identified by the given identifiers.
     */
    private static class SpecificArtifactsArtifactSetId implements VariantResolveMetadata.Identifier {

        private final ImmutableList<ComponentArtifactIdentifier> artifactIds;
        private final int hashCode;

        public SpecificArtifactsArtifactSetId(ImmutableList<ComponentArtifactIdentifier> artifactIds) {
            this.artifactIds = artifactIds;
            this.hashCode = artifactIds.hashCode();
        }

        @Override
        public int hashCode() {
            return hashCode;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != getClass()) {
                return false;
            }
            SpecificArtifactsArtifactSetId other = (SpecificArtifactsArtifactSetId) obj;
            return artifactIds.equals(other.artifactIds);
        }

    }

}
