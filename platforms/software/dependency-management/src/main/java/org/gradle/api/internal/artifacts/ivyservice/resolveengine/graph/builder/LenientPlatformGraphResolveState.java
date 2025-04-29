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
import org.gradle.api.artifacts.result.ResolvedVariantResult;
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
import org.gradle.internal.component.model.ComponentGraphResolveState;
import org.gradle.internal.component.model.ComponentIdGenerator;
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
import org.gradle.internal.component.model.VariantResolveMetadata;
import org.gradle.internal.lazy.Lazy;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class LenientPlatformGraphResolveState extends AbstractComponentGraphResolveState<LenientPlatformResolveMetadata> {

    private final ComponentIdGenerator idGenerator;
    private final NodeState platformNode;
    private final ResolveState resolveState;
    private final VirtualPlatformState virtualPlatformState;

    public static LenientPlatformGraphResolveState of(
        ComponentIdGenerator componentIdGenerator,
        ModuleComponentIdentifier moduleComponentIdentifier,
        ModuleVersionIdentifier moduleVersionIdentifier,
        VirtualPlatformState virtualPlatformState,
        NodeState platformNode,
        ResolveState resolveState
    ) {
        LenientPlatformResolveMetadata metadata = new LenientPlatformResolveMetadata(moduleComponentIdentifier, moduleVersionIdentifier);
        return new LenientPlatformGraphResolveState(componentIdGenerator.nextComponentId(), metadata, componentIdGenerator, virtualPlatformState, platformNode, resolveState);
    }

    private LenientPlatformGraphResolveState(
        long instanceId,
        LenientPlatformResolveMetadata metadata,
        ComponentIdGenerator idGenerator,
        VirtualPlatformState virtualPlatformState,
        NodeState platformNode,
        ResolveState resolveState
    ) {
        super(instanceId, metadata, resolveState.getAttributeDesugaring());
        this.idGenerator = idGenerator;
        this.platformNode = platformNode;
        this.resolveState = resolveState;
        this.virtualPlatformState = virtualPlatformState;
    }

    /**
     * Create a copy of this component with the given ids.
     */
    public ComponentGraphResolveState copyWithIds(ModuleComponentIdentifier componentIdentifier, ModuleVersionIdentifier moduleVersionIdentifier) {
        return new LenientPlatformGraphResolveState(
            idGenerator.nextComponentId(),
            getMetadata().copyWithIds(componentIdentifier, moduleVersionIdentifier),
            idGenerator,
            virtualPlatformState,
            platformNode,
            resolveState
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
    public List<ResolvedVariantResult> getAllSelectableVariantResults() {
        // Variants are not selected from a lenient platform in the conventional manner.
        return Collections.emptyList();
    }

    @Override
    public LenientPlatformGraphSelectionCandidates getCandidatesForGraphVariantSelection() {
        return new LenientPlatformGraphSelectionCandidates(this);
    }

    public static class LenientPlatformGraphSelectionCandidates implements GraphSelectionCandidates {

        private final LenientPlatformGraphResolveState component;
        private final Lazy<LenientPlatformVariantGraphResolveState> implicitVariantState;

        public LenientPlatformGraphSelectionCandidates(LenientPlatformGraphResolveState component) {
            this.component = component;
            this.implicitVariantState = Lazy.locking().of(() -> createImplicitVariant(component));
        }

        @Override
        public List<? extends VariantGraphResolveState> getVariantsForAttributeMatching() {
            // Variants are not selected from a lenient platform in the conventional manner.
            return Collections.emptyList();
        }

        @Override
        public VariantGraphResolveState getLegacyVariant() {
            return implicitVariantState.get();
        }

        /**
         * The variant that is selected when a normal dependency targets this component.
         */
        private static LenientPlatformVariantGraphResolveState createImplicitVariant(LenientPlatformGraphResolveState component) {
            VariantDependencyFactory dependencyFactory = new ImplicitVariantDependencyFactory(
                component.virtualPlatformState,
                component.resolveState,
                component.platformNode,
                component.getMetadata().getModuleVersionId()
            );

            return new LenientPlatformVariantGraphResolveState(
                component.idGenerator.nextVariantId(),
                component.getMetadata().getId(),
                new LenientPlatformVariantGraphResolveMetadata(Dependency.DEFAULT_CONFIGURATION, false, dependencyFactory)
            );
        }

        /**
         * The variant that is selected when another lenient platform targets this component.
         *
         * @param platformId The consuming platform.
         */
        public VariantGraphResolveState getVariantForSourceNode(
            NodeState from,
            @Nullable ComponentIdentifier platformId
        ) {
            VariantDependencyFactory dependencyFactory = new SourceAwareVariantDependencyFactory(
                component.virtualPlatformState,
                component.resolveState,
                from,
                platformId
            );

            return new LenientPlatformVariantGraphResolveState(
                component.idGenerator.nextVariantId(),
                component.getMetadata().getId(),
                new LenientPlatformVariantGraphResolveMetadata(Dependency.DEFAULT_CONFIGURATION, true, dependencyFactory)
            );
        }

    }

    /**
     * Metadata for a variant of a lenient platform.
     */
    private static class LenientPlatformVariantGraphResolveMetadata implements VariantGraphResolveMetadata {

        private final String name;
        private final boolean transitive;
        private final VariantDependencyFactory dependencyFactory;

        public LenientPlatformVariantGraphResolveMetadata(
            String name,
            boolean transitive,
            VariantDependencyFactory dependencyFactory
        ) {
            this.name = name;
            this.transitive = transitive;
            this.dependencyFactory = dependencyFactory;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public ImmutableAttributes getAttributes() {
            return ImmutableAttributes.EMPTY;
        }

        @Override
        public ImmutableCapabilities getCapabilities() {
            return ImmutableCapabilities.EMPTY;
        }

        public List<? extends ModuleDependencyMetadata> getDependencies() {
            return dependencyFactory.getDependencies();
        }

        @Override
        public boolean isTransitive() {
            return transitive;
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
        private final LenientPlatformVariantGraphResolveMetadata metadata;
        private final LenientPlatformVariantArtifactResolveState artifactResolveState;

        public LenientPlatformVariantGraphResolveState(
            long instanceId,
            ModuleComponentIdentifier componentId,
            LenientPlatformVariantGraphResolveMetadata metadata
        ) {
            this.instanceId = instanceId;
            this.metadata = metadata;
            this.artifactResolveState = new LenientPlatformVariantArtifactResolveState(componentId, metadata);
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
            return metadata.getDependencies();
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
                Describables.of(componentId, "variant", name),
                variant.getAttributes(),
                ImmutableList.of(),
                variant.getCapabilities()
            ));
        }

    }

    /**
     * Factory for dependencies of a variant.
     * <p>
     * Lenient platforms have both an implicit variant and a source-aware variant.
     * The implicit variant is selected when a normal dependency targets the platform,
     * while the source-aware variant is selected when another lenient platform targets the platform.
     * <p>
     * These two schemes have different approaches to selecting dependencies.
     */
    interface VariantDependencyFactory {

        List<? extends ModuleDependencyMetadata> getDependencies();

    }

    /**
     * Dependencies for the implicit variant of a lenient platform.
     */
    private static class ImplicitVariantDependencyFactory implements VariantDependencyFactory {

        private final VirtualPlatformState virtualPlatformState;
        private final ResolveState resolveState;
        private final NodeState platformNode;
        private final ModuleVersionIdentifier moduleVersionIdentifier;

        public ImplicitVariantDependencyFactory(
            VirtualPlatformState virtualPlatformState,
            ResolveState resolveState,
            NodeState platformNode,
            ModuleVersionIdentifier moduleVersionIdentifier
        ) {
            this.virtualPlatformState = virtualPlatformState;
            this.resolveState = resolveState;
            this.platformNode = platformNode;
            this.moduleVersionIdentifier = moduleVersionIdentifier;
        }

        @Override
        public List<? extends ModuleDependencyMetadata> getDependencies() {
            ImmutableList.Builder<ModuleDependencyMetadata> dependencies = new ImmutableList.Builder<>();
            Set<ModuleResolveState> participatingModules = virtualPlatformState.getParticipatingModules();
            for (ModuleResolveState module : participatingModules) {
                dependencies.add(new LenientPlatformDependencyMetadata(
                    resolveState,
                    platformNode,
                    DefaultModuleComponentSelector.newSelector(module.getId(), moduleVersionIdentifier.getVersion()),
                    DefaultModuleComponentIdentifier.newId(module.getId(), moduleVersionIdentifier.getVersion()),
                    virtualPlatformState.getSelectedPlatformId(),
                    virtualPlatformState.isForced(),
                    true
                ));
            }
            return dependencies.build();
        }
    }

    /**
     * Dependencies for the source-aware variant of a lenient platform.
     */
    private static class SourceAwareVariantDependencyFactory implements VariantDependencyFactory {

        private final VirtualPlatformState virtualPlatformState;
        private final ResolveState resolveState;
        private final NodeState from;
        private final ComponentIdentifier platformId;

        public SourceAwareVariantDependencyFactory(
            VirtualPlatformState virtualPlatformState,
            ResolveState resolveState,
            NodeState from,
            @Nullable ComponentIdentifier platformId
        ) {
            this.virtualPlatformState = virtualPlatformState;
            this.resolveState = resolveState;
            this.from = from;
            this.platformId = platformId;
        }

        @Override
        public List<? extends ModuleDependencyMetadata> getDependencies() {
            List<ModuleDependencyMetadata> result = null;
            List<String> candidateVersions = virtualPlatformState.getCandidateVersions();
            Set<ModuleResolveState> modules = virtualPlatformState.getParticipatingModules();
            for (ModuleResolveState module : modules) {
                ComponentState selected = module.getSelected();
                if (selected != null) {
                    String componentVersion = selected.getId().getVersion();
                    for (String target : candidateVersions) {
                        ModuleComponentIdentifier leafId = DefaultModuleComponentIdentifier.newId(module.getId(), target);
                        ModuleComponentSelector leafSelector = DefaultModuleComponentSelector.newSelector(module.getId(), target);
                        ComponentIdentifier platformId = virtualPlatformState.getSelectedPlatformId();
                        if (platformId == null) {
                            // Not sure this can happen, unless in error state
                            platformId = this.platformId;
                        }
                        if (!componentVersion.equals(target)) {
                            // We will only add dependencies to the leaves if there is such a published module
                            PotentialEdge potentialEdge = PotentialEdge.of(resolveState, from, leafId, leafSelector, platformId, virtualPlatformState.isForced(), false);
                            if (potentialEdge.state != null) {
                                result = registerPlatformEdge(result, modules, leafId, leafSelector, platformId, virtualPlatformState.isForced());
                                break;
                            }
                        } else {
                            // at this point we know the component exists
                            result = registerPlatformEdge(result, modules, leafId, leafSelector, platformId, virtualPlatformState.isForced());
                            break;
                        }
                    }
                    virtualPlatformState.attachOrphanEdges();
                }
            }
            return result == null ? Collections.emptyList() : result;
        }

        private List<ModuleDependencyMetadata> registerPlatformEdge(
            @Nullable List<ModuleDependencyMetadata> result,
            Set<ModuleResolveState> modules,
            ModuleComponentIdentifier leafId,
            ModuleComponentSelector leafSelector,
            ComponentIdentifier platformId,
            boolean force
        ) {
            if (result == null) {
                result = new ArrayList<>(modules.size());
            }
            result.add(new LenientPlatformDependencyMetadata(
                resolveState,
                from,
                leafSelector,
                leafId,
                platformId,
                force,
                false
            ));
            return result;
        }
    }

}
