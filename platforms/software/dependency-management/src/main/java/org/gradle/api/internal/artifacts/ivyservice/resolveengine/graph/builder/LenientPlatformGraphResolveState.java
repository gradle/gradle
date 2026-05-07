/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier;
import org.gradle.api.internal.artifacts.NamedVariantIdentifier;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.attributes.immutable.ImmutableAttributesSchema;
import org.gradle.internal.Describables;
import org.gradle.internal.component.external.model.DefaultModuleComponentArtifactMetadata;
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier;
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector;
import org.gradle.internal.component.external.model.ImmutableCapabilities;
import org.gradle.internal.component.external.model.ModuleDependencyMetadata;
import org.gradle.internal.component.model.AbstractComponentGraphResolveState;
import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.internal.component.model.ComponentArtifactResolveMetadata;
import org.gradle.internal.component.model.ComponentConfigurationIdentifier;
import org.gradle.internal.component.model.DefaultVariantMetadata;
import org.gradle.internal.component.model.DependencyMetadata;
import org.gradle.internal.component.model.ExcludeMetadata;
import org.gradle.internal.component.model.GraphSelectionCandidates;
import org.gradle.internal.component.model.ImmutableModuleSources;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.component.model.ModuleSources;
import org.gradle.internal.component.model.VariantArtifactResolveState;
import org.gradle.internal.component.model.VariantGraphResolveMetadata;
import org.gradle.internal.component.model.VariantGraphResolveState;
import org.gradle.internal.component.model.VariantIdentifier;
import org.gradle.internal.component.model.VariantResolveMetadata;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class LenientPlatformGraphResolveState extends AbstractComponentGraphResolveState<LenientPlatformResolveMetadata> {

    private final LenientPlatformVariantGraphResolveState variant;

    public LenientPlatformGraphResolveState(
        long componentId,
        long variantId,
        LenientPlatformResolveMetadata metadata,
        VirtualPlatformState virtualPlatformState,
        ResolveState resolveState
    ) {
        super(componentId, metadata);

        this.variant = createVariant(variantId, virtualPlatformState, resolveState, metadata.getId());
    }

    private static LenientPlatformVariantGraphResolveState createVariant(
        long instanceId,
        VirtualPlatformState virtualPlatformState,
        ResolveState resolveState,
        ModuleComponentIdentifier platformId
    ) {
        String name = Dependency.DEFAULT_CONFIGURATION;
        NamedVariantIdentifier variantId = new NamedVariantIdentifier(platformId, name);
        return new LenientPlatformVariantGraphResolveState(
            instanceId,
            platformId,
            virtualPlatformState,
            resolveState,
            new LenientPlatformVariantGraphResolveMetadata(variantId, name)
        );
    }

    @Override
    public ComponentArtifactResolveMetadata getArtifactMetadata() {
        return new LenientPlatformArtifactResolveMetadata(getMetadata());
    }

    /**
     * Artifact metadata for a lenient platform.
     */
    private static class LenientPlatformArtifactResolveMetadata implements ComponentArtifactResolveMetadata {

        private final LenientPlatformResolveMetadata metadata;

        LenientPlatformArtifactResolveMetadata(LenientPlatformResolveMetadata metadata) {
            this.metadata = metadata;
        }

        @Override
        public ComponentIdentifier getId() {
            return metadata.getId();
        }

        @Override
        public ModuleVersionIdentifier getModuleVersionId() {
            return metadata.getModuleVersionId();
        }

        @Override
        public ModuleSources getSources() {
            return ImmutableModuleSources.of();
        }

        @Override
        public ImmutableAttributes getAttributes() {
            return ImmutableAttributes.EMPTY;
        }

        @Override
        public ImmutableAttributesSchema getAttributesSchema() {
            return metadata.getAttributesSchema();
        }

    }

    @Override
    public GraphSelectionCandidates getCandidatesForGraphVariantSelection() {
        return new LenientPlatformGraphSelectionCandidates(variant);
    }

    private record LenientPlatformGraphSelectionCandidates(VariantGraphResolveState variant) implements GraphSelectionCandidates {

        @Override
        public List<? extends VariantGraphResolveState> getVariantsForAttributeMatching() {
            // Variants are not selected from a lenient platform in the conventional manner.
            return Collections.emptyList();
        }

        @Override
        public VariantGraphResolveState getLegacyVariant() {
            return variant;
        }

    }

    /**
     * Metadata for a variant of a lenient platform.
     */
    private static class LenientPlatformVariantGraphResolveMetadata implements VariantGraphResolveMetadata {

        private final VariantIdentifier id;
        private final String name;

        public LenientPlatformVariantGraphResolveMetadata(
            VariantIdentifier id,
            String name
        ) {
            this.id = id;
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public VariantIdentifier getId() {
            return id;
        }

        @Override
        public ImmutableAttributes getAttributes() {
            return ImmutableAttributes.EMPTY;
        }

        @Override
        public ImmutableCapabilities getCapabilities() {
            return ImmutableCapabilities.EMPTY;
        }

        @Override
        public boolean isTransitive() {
            return true;
        }

        @Override
        public boolean isExternalVariant() {
            return false;
        }

    }

    /**
     * State for a variant of a lenient platform.
     */
    private static class LenientPlatformVariantGraphResolveState implements VariantGraphResolveState {

        private final long instanceId;
        private final ModuleComponentIdentifier platformId;
        private final VirtualPlatformState virtualPlatformState;
        private final ResolveState resolveState;
        private final LenientPlatformVariantGraphResolveMetadata metadata;

        private final LenientPlatformVariantArtifactResolveState artifactResolveState;

        public LenientPlatformVariantGraphResolveState(
            long instanceId,
            ModuleComponentIdentifier platformId,
            VirtualPlatformState virtualPlatformState,
            ResolveState resolveState,
            LenientPlatformVariantGraphResolveMetadata metadata
        ) {
            this.instanceId = instanceId;
            this.platformId = platformId;
            this.virtualPlatformState = virtualPlatformState;
            this.resolveState = resolveState;
            this.metadata = metadata;

            this.artifactResolveState = new LenientPlatformVariantArtifactResolveState(platformId, metadata);
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
        public List<? extends DependencyMetadata> getDependencies() {
            List<ModuleDependencyMetadata> result = null;
            List<String> candidateVersions = virtualPlatformState.getCandidateVersions();
            Set<ModuleResolveState> modules = virtualPlatformState.getParticipatingModules();
            boolean forced = virtualPlatformState.isForced();

            for (ModuleResolveState module : modules) {
                ComponentState selected = module.getSelected();
                if (selected != null) {
                    ModuleDependencyMetadata resultForSelected = getDependencyForParticipatingComponent(module, selected, candidateVersions, forced);
                    if (resultForSelected != null) {
                        if (result == null) {
                            result = new ArrayList<>(modules.size());
                        }
                        result.add(resultForSelected);
                    }

                    virtualPlatformState.attachOrphanEdges();
                }
            }

            return result == null ? Collections.emptyList() : result;
        }

        private @Nullable ModuleDependencyMetadata getDependencyForParticipatingComponent(
            ModuleResolveState module,
            ComponentState selectedComponent,
            List<String> candidateVersions,
            boolean forced
        ) {
            for (String target : candidateVersions) {
                ModuleComponentIdentifier targetComponentId = DefaultModuleComponentIdentifier.newId(module.getId(), target);

                // We will only add dependencies to the leaves if there is such a published module.
                // To do this, we either check module has already selected the target version
                // or we need to resolve the potential target version to see if it exists.
                if (selectedComponent.getComponentId().equals(targetComponentId) ||
                    componentVersionExists(targetComponentId)
                ) {
                    return createConstraint(targetComponentId, forced);
                }
            }

            return null;
        }

        /**
         * Determine if the given component version exists, by resolving it.
         */
        private boolean componentVersionExists(ModuleComponentIdentifier componentId) {
            ModuleVersionIdentifier moduleVersionId =
                DefaultModuleVersionIdentifier.newId(componentId.getModuleIdentifier(), componentId.getVersion());
            return resolveState.getModule(componentId.getModuleIdentifier())
                .getVersion(moduleVersionId, componentId)
                .getResolveStateOrNull() != null;
        }

        private LenientPlatformDependencyMetadata createConstraint(
            ModuleComponentIdentifier targetComponentId,
            boolean forced
        ) {
            ModuleComponentSelector selector =
                DefaultModuleComponentSelector.newSelector(targetComponentId.getModuleIdentifier(), targetComponentId.getVersion());
            return new LenientPlatformDependencyMetadata(
                selector,
                platformId,
                forced,
                true
            );
        }

        @Override
        public List<? extends ExcludeMetadata> getExcludes() {
            return Collections.emptyList();
        }

        @Override
        public VariantGraphResolveMetadata getMetadata() {
            return metadata;
        }

        @Override
        public VariantArtifactResolveState prepareForArtifactResolution() {
            return artifactResolveState;
        }

    }

    /**
     * Artifact state for a variant of a lenient platform.
     */
    private static class LenientPlatformVariantArtifactResolveState implements VariantArtifactResolveState {

        private final ModuleComponentIdentifier componentId;
        private final VariantGraphResolveMetadata variant;

        public LenientPlatformVariantArtifactResolveState(ModuleComponentIdentifier componentId, VariantGraphResolveMetadata variant) {
            this.componentId = componentId;
            this.variant = variant;
        }

        @Override
        public ImmutableList<ComponentArtifactMetadata> getAdhocArtifacts(List<IvyArtifactName> dependencyArtifacts) {
            ImmutableList.Builder<ComponentArtifactMetadata> artifacts = ImmutableList.builderWithExpectedSize(dependencyArtifacts.size());
            for (IvyArtifactName dependencyArtifact : dependencyArtifacts) {
                artifacts.add(new DefaultModuleComponentArtifactMetadata(componentId, dependencyArtifact));
            }
            return artifacts.build();
        }

        @Override
        public Set<? extends VariantResolveMetadata> getArtifactVariants() {
            String name = variant.getName();
            return ImmutableSet.of(new DefaultVariantMetadata(
                name,
                new ComponentConfigurationIdentifier(componentId, name),
                Describables.of(componentId, "variant", variant.getDisplayName()),
                variant.getAttributes(),
                ImmutableList.of(),
                variant.getCapabilities()
            ));
        }

    }

}
