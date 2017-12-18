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

package org.gradle.api.internal.artifacts.vcs;

import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.initialization.IncludedBuild;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ComponentResolvers;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.LocalComponentRegistry;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectDependencyResolver;
import org.gradle.api.specs.Spec;
import org.gradle.composite.internal.IncludedBuildRegistry;
import org.gradle.initialization.NestedBuildFactory;
import org.gradle.internal.Pair;
import org.gradle.internal.component.local.model.DefaultProjectComponentSelector;
import org.gradle.internal.component.local.model.LocalComponentMetadata;
import org.gradle.internal.component.model.DependencyMetadata;
import org.gradle.internal.hash.HashUtil;
import org.gradle.internal.resolve.ModuleVersionResolveException;
import org.gradle.internal.resolve.resolver.ArtifactResolver;
import org.gradle.internal.resolve.resolver.ComponentMetaDataResolver;
import org.gradle.internal.resolve.resolver.DependencyToComponentIdResolver;
import org.gradle.internal.resolve.resolver.OriginArtifactSelector;
import org.gradle.internal.resolve.result.BuildableComponentIdResolveResult;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.util.CollectionUtils;
import org.gradle.vcs.VersionControlSpec;
import org.gradle.vcs.VersionControlSystem;
import org.gradle.vcs.VersionRef;
import org.gradle.vcs.internal.VcsMappingFactory;
import org.gradle.vcs.internal.VcsMappingInternal;
import org.gradle.vcs.internal.VcsMappingsStore;
import org.gradle.vcs.internal.VcsWorkingDirectoryRoot;
import org.gradle.vcs.internal.VersionControlSystemFactory;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class VcsDependencyResolver implements DependencyToComponentIdResolver, ComponentResolvers {
    private final ProjectDependencyResolver projectDependencyResolver;
    private final ServiceRegistry serviceRegistry;
    private final LocalComponentRegistry localComponentRegistry;
    private final VcsMappingsStore vcsMappingsStore;
    private final VcsMappingFactory vcsMappingFactory;
    private final VersionControlSystemFactory versionControlSystemFactory;
    private final File baseWorkingDir;
    private final Map<String, VersionRef> selectedVersionCache = new HashMap<String, VersionRef>();

    public VcsDependencyResolver(VcsWorkingDirectoryRoot vcsWorkingDirRoot, ProjectDependencyResolver projectDependencyResolver, ServiceRegistry serviceRegistry, LocalComponentRegistry localComponentRegistry, VcsMappingsStore vcsMappingsStore, VcsMappingFactory vcsMappingFactory, VersionControlSystemFactory versionControlSystemFactory) {
        this.baseWorkingDir = vcsWorkingDirRoot.getDir();
        this.projectDependencyResolver = projectDependencyResolver;
        this.serviceRegistry = serviceRegistry;
        this.localComponentRegistry = localComponentRegistry;
        this.vcsMappingsStore = vcsMappingsStore;
        this.vcsMappingFactory = vcsMappingFactory;
        this.versionControlSystemFactory = versionControlSystemFactory;
    }

    @Override
    public void resolve(DependencyMetadata dependency, BuildableComponentIdResolveResult result) {
        VcsMappingInternal vcsMappingInternal = getVcsMapping(dependency);

        if (vcsMappingInternal != null) {
            // Safe to cast because if it weren't a ModuleComponentSelector, vcsMappingInternal would be null.
            final ModuleComponentSelector depSelector = (ModuleComponentSelector) dependency.getSelector();
            vcsMappingsStore.getVcsMappingRule().execute(vcsMappingInternal);

            // TODO: Need failure handling, e.g., cannot clone repository
            if (vcsMappingInternal.hasRepository()) {
                VersionControlSpec spec = vcsMappingInternal.getRepository();
                VersionControlSystem versionControlSystem = versionControlSystemFactory.create(spec);
                VersionRef selectedVersion = selectVersionFromCache(spec, depSelector.getVersion());
                if (selectedVersion == null) {
                    selectedVersion = selectVersionFromRepository(spec, versionControlSystem, depSelector.getVersion());
                }
                if (selectedVersion == null) {
                    result.failed(new ModuleVersionResolveException(depSelector,
                        "Could not resolve " + depSelector.toString() + ". " + spec.getDisplayName() + " does not contain a version matching " + depSelector.getVersion()));
                    return;
                }

                //TODO: Allow user to provide settings script in VcsMapping
                File dependencyWorkingDir = new File(populateWorkingDirectory(baseWorkingDir, spec, versionControlSystem, selectedVersion), spec.getRootDir());

                // TODO: This shouldn't rely on the service registry to find NestedBuildFactory
                IncludedBuildRegistry includedBuildRegistry = serviceRegistry.get(IncludedBuildRegistry.class);
                NestedBuildFactory nestedBuildFactory = serviceRegistry.get(NestedBuildFactory.class);
                IncludedBuild includedBuild = includedBuildRegistry.addImplicitBuild(dependencyWorkingDir, nestedBuildFactory);

                Collection<Pair<ModuleVersionIdentifier, ProjectComponentIdentifier>> moduleToProject = includedBuildRegistry.getModuleToProjectMapping(includedBuild);
                Pair<ModuleVersionIdentifier, ProjectComponentIdentifier> entry = CollectionUtils.findFirst(moduleToProject, new Spec<Pair<ModuleVersionIdentifier, ProjectComponentIdentifier>>() {
                        @Override
                        public boolean isSatisfiedBy(Pair<ModuleVersionIdentifier, ProjectComponentIdentifier> entry) {
                            ModuleVersionIdentifier possibleMatch = entry.left;
                            return depSelector.getGroup().equals(possibleMatch.getGroup())
                                && depSelector.getModule().equals(possibleMatch.getName());
                        }
                });
                if (entry == null) {
                    result.failed(new ModuleVersionResolveException(vcsMappingInternal.getRequested(), spec.getDisplayName() + " did not contain a project publishing the specified dependency."));
                } else {
                    LocalComponentMetadata componentMetaData = localComponentRegistry.getComponent(entry.right);

                    if (componentMetaData == null) {
                        result.failed(new ModuleVersionResolveException(DefaultProjectComponentSelector.newSelector(includedBuild, entry.right.getProjectPath()), vcsMappingInternal.getRepository().getDisplayName() + " could not be resolved into a usable project."));
                    } else {
                        result.resolved(componentMetaData);
                    }
                    return;
                }
            }
        }

        projectDependencyResolver.resolve(dependency, result);
    }

    private File populateWorkingDirectory(File baseWorkingDir, VersionControlSpec spec, VersionControlSystem versionControlSystem, VersionRef selectedVersion) {
        String repositoryId = HashUtil.createCompactMD5(spec.getUniqueId());
        File versionDirectory = new File(baseWorkingDir, repositoryId + "-" + selectedVersion.getCanonicalId());
        return versionControlSystem.populate(versionDirectory, selectedVersion, spec);
    }

    private VersionRef selectVersionFromCache(VersionControlSpec spec, String version) {
        return selectedVersionCache.get(cacheKey(spec, version));
    }

    private VersionRef selectVersionFromRepository(VersionControlSpec spec, VersionControlSystem versionControlSystem, String version) {
        // TODO: Select version based on requested version and tags
        Set<VersionRef> versions = versionControlSystem.getAvailableVersions(spec);
        for (VersionRef candidate : versions) {
            if (candidate.getVersion().equals(version)) {
                selectedVersionCache.put(cacheKey(spec, version), candidate);
                return candidate;
            }
        }
        return null;
    }

    private String cacheKey(VersionControlSpec spec, String version) {
        return spec.getUniqueId() + version;
    }

    private VcsMappingInternal getVcsMapping(DependencyMetadata dependency) {
        if (vcsMappingsStore.hasRules()
                && dependency.getSelector() instanceof ModuleComponentSelector) {
            return vcsMappingFactory.create(dependency.getSelector());
        }
        return null;
    }

    @Override
    public DependencyToComponentIdResolver getComponentIdResolver() {
        return this;
    }

    @Override
    public ComponentMetaDataResolver getComponentResolver() {
        return projectDependencyResolver;
    }

    @Override
    public OriginArtifactSelector getArtifactSelector() {
        return projectDependencyResolver;
    }

    @Override
    public ArtifactResolver getArtifactResolver() {
        return projectDependencyResolver;
    }
}
