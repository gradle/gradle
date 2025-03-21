/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.composite.internal;

import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.BuildIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory;
import org.gradle.api.internal.artifacts.Module;
import org.gradle.api.internal.artifacts.configurations.ConfigurationsProvider;
import org.gradle.api.internal.artifacts.configurations.DefaultConfigurationContainer;
import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.BuildTreeLocalComponentProvider;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.LocalComponentCache;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.internal.attributes.immutable.ImmutableAttributesSchema;
import org.gradle.api.internal.attributes.immutable.ImmutableAttributesSchemaFactory;
import org.gradle.api.internal.project.HoldsProjectState;
import org.gradle.api.internal.project.ProjectIdentity;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ProjectState;
import org.gradle.api.internal.project.ProjectStateRegistry;
import org.gradle.internal.Describables;
import org.gradle.internal.build.BuildState;
import org.gradle.internal.component.local.model.LocalComponentGraphResolveMetadata;
import org.gradle.internal.component.local.model.LocalComponentGraphResolveState;
import org.gradle.internal.component.local.model.LocalComponentGraphResolveStateFactory;
import org.gradle.internal.model.InMemoryCacheFactory;
import org.gradle.internal.model.InMemoryLoadingCache;
import org.gradle.util.Path;

import javax.inject.Inject;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default implementation of {@link BuildTreeLocalComponentProvider}.
 */
public class DefaultBuildTreeLocalComponentProvider implements BuildTreeLocalComponentProvider, HoldsProjectState {

    private final LocalComponentCache localComponentCache;
    private final ProjectStateRegistry projectStateRegistry;
    private final ImmutableAttributesSchemaFactory attributesSchemaFactory;
    private final ImmutableModuleIdentifierFactory moduleIdentifierFactory;
    private final LocalComponentGraphResolveStateFactory resolveStateFactory;

    /**
     * Caches the component state instances for each project.
     */
    private final InMemoryLoadingCache<Path, LocalComponentGraphResolveState> components;

    /**
     * All builds which are known to be configured already.
     */
    private final Set<BuildIdentifier> configuredBuilds = ConcurrentHashMap.newKeySet();

    @Inject
    public DefaultBuildTreeLocalComponentProvider(
        InMemoryCacheFactory cacheFactory,
        LocalComponentCache localComponentCache,
        ProjectStateRegistry projectStateRegistry,
        ImmutableAttributesSchemaFactory attributesSchemaFactory,
        ImmutableModuleIdentifierFactory moduleIdentifierFactory,
        LocalComponentGraphResolveStateFactory resolveStateFactory
    ) {
        this.localComponentCache = localComponentCache;
        this.projectStateRegistry = projectStateRegistry;
        this.attributesSchemaFactory = attributesSchemaFactory;
        this.moduleIdentifierFactory = moduleIdentifierFactory;
        this.resolveStateFactory = resolveStateFactory;

        this.components = cacheFactory.createCalculatedValueCache(Describables.of("project components"), this::loadOrCreateLocalComponentState);
    }

    @Override
    public LocalComponentGraphResolveState getComponent(ProjectIdentity targetProject, Path sourceBuild) {
        Path projectIdentityPath = targetProject.getBuildTreePath();

        if (!configuredBuilds.contains(targetProject.getBuildIdentifier())) {
            // The target build _may_ have not yet been configured. Configure it now.
            if (!sourceBuild.equals(targetProject.getBuildPath())) {
                // Only configure the target build if it is not the same as the source build.
                // Otherwise, we are in the process of configuring the source build right now.
                // TODO: This source-build-check should not be necessary. `ensureProjectsConfigured` should
                //       be able to handle the case where the source build ensures that itself is configured, but
                //       at the moment it deadlocks in that case.
                ProjectState projectState = projectStateRegistry.stateFor(projectIdentityPath);
                BuildState buildState = projectState.getOwner();
                buildState.ensureProjectsConfigured();
            }
            configuredBuilds.add(targetProject.getBuildIdentifier());
        }

        return components.get(projectIdentityPath);
    }

    private LocalComponentGraphResolveState loadOrCreateLocalComponentState(Path projectIdentityPath) {
        return localComponentCache.computeIfAbsent(projectIdentityPath, path -> {
            ProjectState projectState = projectStateRegistry.stateFor(path);

            // The project may not have been configured yet if configure-on-demand is enabled.
            projectState.ensureConfigured();

            return projectState.fromMutableState(p -> createLocalComponentState(projectState, p));
        });
    }

    private LocalComponentGraphResolveState createLocalComponentState(ProjectState projectState, ProjectInternal project) {
        Module module = project.getServices().get(DependencyMetaDataProvider.class).getModule();
        ModuleVersionIdentifier moduleVersionIdentifier = moduleIdentifierFactory.moduleWithVersion(module.getGroup(), module.getName(), module.getVersion());
        ProjectComponentIdentifier componentIdentifier = projectState.getComponentIdentifier();
        AttributesSchemaInternal mutableSchema = (AttributesSchemaInternal) project.getDependencies().getAttributesSchema();
        ImmutableAttributesSchema schema = attributesSchemaFactory.create(mutableSchema);

        LocalComponentGraphResolveMetadata metadata = new LocalComponentGraphResolveMetadata(
            moduleVersionIdentifier,
            componentIdentifier,
            module.getStatus(),
            schema
        );

        ConfigurationsProvider configurations = (DefaultConfigurationContainer) project.getConfigurations();
        return resolveStateFactory.stateFor(projectState, metadata, configurations);
    }

    @Override
    public void discardAll() {
        components.invalidate();
    }

}
