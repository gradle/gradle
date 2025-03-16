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

package org.gradle.vcs.internal.resolver;

import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.internal.BuildDefinition;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ComponentResolvers;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelector;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.LocalComponentRegistry;
import org.gradle.api.internal.component.ArtifactType;
import org.gradle.initialization.definition.DefaultInjectedPluginDependency;
import org.gradle.internal.Actions;
import org.gradle.internal.Pair;
import org.gradle.internal.build.BuildStateRegistry;
import org.gradle.internal.build.IncludedBuildState;
import org.gradle.internal.build.PublicBuildPath;
import org.gradle.internal.component.local.model.LocalComponentGraphResolveState;
import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.internal.component.model.ComponentArtifactResolveMetadata;
import org.gradle.internal.component.model.ComponentGraphSpecificResolveState;
import org.gradle.internal.component.model.ComponentOverrideMetadata;
import org.gradle.internal.resolve.ModuleVersionNotFoundException;
import org.gradle.internal.resolve.ModuleVersionResolveException;
import org.gradle.internal.resolve.resolver.ArtifactResolver;
import org.gradle.internal.resolve.resolver.ComponentMetaDataResolver;
import org.gradle.internal.resolve.resolver.DependencyToComponentIdResolver;
import org.gradle.internal.resolve.result.BuildableArtifactResolveResult;
import org.gradle.internal.resolve.result.BuildableArtifactSetResolveResult;
import org.gradle.internal.resolve.result.BuildableComponentIdResolveResult;
import org.gradle.internal.resolve.result.BuildableComponentResolveResult;
import org.gradle.plugin.management.internal.DefaultPluginRequest;
import org.gradle.plugin.management.internal.PluginRequestInternal;
import org.gradle.plugin.management.internal.PluginRequests;
import org.gradle.plugin.use.internal.DefaultPluginId;
import org.gradle.util.internal.CollectionUtils;
import org.gradle.vcs.VersionControlSpec;
import org.gradle.vcs.internal.VcsResolver;
import org.gradle.vcs.internal.VersionControlRepositoryConnection;
import org.gradle.vcs.internal.VersionControlRepositoryConnectionFactory;
import org.gradle.vcs.internal.spec.AbstractVersionControlSpec;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class VcsDependencyResolver implements DependencyToComponentIdResolver, ComponentResolvers, ComponentMetaDataResolver, ArtifactResolver {
    private final LocalComponentRegistry localComponentRegistry;
    private final VcsResolver vcsResolver;
    private final VersionControlRepositoryConnectionFactory versionControlSystemFactory;
    private final VcsVersionWorkingDirResolver workingDirResolver;
    private final PublicBuildPath publicBuildPath;
    private final BuildStateRegistry buildRegistry;

    private final Set<String> names = new HashSet<>();

    public VcsDependencyResolver(LocalComponentRegistry localComponentRegistry, VcsResolver vcsResolver, VersionControlRepositoryConnectionFactory versionControlSystemFactory, BuildStateRegistry buildRegistry, VcsVersionWorkingDirResolver workingDirResolver, PublicBuildPath publicBuildPath) {
        this.localComponentRegistry = localComponentRegistry;
        this.vcsResolver = vcsResolver;
        this.versionControlSystemFactory = versionControlSystemFactory;
        this.buildRegistry = buildRegistry;
        this.workingDirResolver = workingDirResolver;
        this.publicBuildPath = publicBuildPath;
    }

    @Override
    public void resolve(ComponentSelector selector, ComponentOverrideMetadata overrideMetadata, VersionSelector acceptor, VersionSelector rejector, BuildableComponentIdResolveResult result) {
        if (selector instanceof ModuleComponentSelector) {
            final ModuleComponentSelector depSelector = (ModuleComponentSelector) selector;
            VersionControlSpec spec = vcsResolver.locateVcsFor(depSelector);
            // TODO: Need failure handling, e.g., cannot clone repository
            if (spec != null) {
                VersionControlRepositoryConnection repository = versionControlSystemFactory.create(spec);

                File dependencyWorkingDir;
                try {
                    dependencyWorkingDir = workingDirResolver.selectVersion(depSelector, repository);
                } catch (ModuleVersionResolveException e) {
                    result.failed(e);
                    return;
                }
                if (dependencyWorkingDir == null) {
                    result.failed(new ModuleVersionNotFoundException(depSelector, Collections.singleton(spec.getDisplayName())));
                    return;
                }

                File buildRootDir = new File(dependencyWorkingDir, spec.getRootDir());
                BuildDefinition buildDefinition = toBuildDefinition((AbstractVersionControlSpec) spec, buildRootDir);
                IncludedBuildState includedBuild = buildRegistry.addImplicitIncludedBuild(buildDefinition);

                Collection<Pair<ModuleVersionIdentifier, ProjectComponentIdentifier>> moduleToProject = includedBuild.getAvailableModules();
                Pair<ModuleVersionIdentifier, ProjectComponentIdentifier> entry = CollectionUtils.findFirst(moduleToProject, e -> {
                    ModuleVersionIdentifier possibleMatch = e.left;
                    return depSelector.getGroup().equals(possibleMatch.getGroup())
                        && depSelector.getModule().equals(possibleMatch.getName());
                });
                if (entry == null) {
                    result.failed(new ModuleVersionResolveException(depSelector, () -> spec.getDisplayName() + " did not contain a project publishing the specified dependency."));
                } else {
                    LocalComponentGraphResolveState component = localComponentRegistry.getComponent(entry.right);
                    result.resolved(component, ComponentGraphSpecificResolveState.EMPTY_STATE);
                }
            }
        }
    }

    private BuildDefinition toBuildDefinition(AbstractVersionControlSpec spec, File buildDirectory) {
        return BuildDefinition.fromStartParameterForBuild(
            buildRegistry.getRootBuild().getStartParameter(),
            assignBuildName(buildDirectory.getName()),
            buildDirectory,
            getPluginRequests(spec),
            Actions.doNothing(),
            publicBuildPath,
            false
        );
    }

    private static PluginRequests getPluginRequests(AbstractVersionControlSpec spec) {
        List<DefaultInjectedPluginDependency> requests = spec.getInjectedPlugins();
        if (requests.isEmpty()) {
            return PluginRequests.EMPTY;
        }

        return PluginRequests.of(
            requests.stream()
                .map(original -> new DefaultPluginRequest(DefaultPluginId.of(original.getId()), true, PluginRequestInternal.Origin.AUTO_APPLIED, null, null, null, null, null, null))
                .collect(Collectors.toList())
        );
    }

    private String assignBuildName(String name) {
        int counter = 0;
        while (!names.add(name)) {
            name = name + ++counter;
        }
        return name;
    }

    @Override
    public DependencyToComponentIdResolver getComponentIdResolver() {
        return this;
    }

    @Override
    public ComponentMetaDataResolver getComponentResolver() {
        return this;
    }

    @Override
    public void resolve(ComponentIdentifier identifier, ComponentOverrideMetadata componentOverrideMetadata, BuildableComponentResolveResult result) {
    }

    @Override
    public boolean isFetchingMetadataCheap(ComponentIdentifier identifier) {
        return false;
    }

    @Override
    public ArtifactResolver getArtifactResolver() {
        return this;
    }

    @Override
    public void resolveArtifactsWithType(ComponentArtifactResolveMetadata component, ArtifactType artifactType, BuildableArtifactSetResolveResult result) {
    }

    @Override
    public void resolveArtifact(ComponentArtifactResolveMetadata component, ComponentArtifactMetadata artifact, BuildableArtifactResolveResult result) {
    }
}
