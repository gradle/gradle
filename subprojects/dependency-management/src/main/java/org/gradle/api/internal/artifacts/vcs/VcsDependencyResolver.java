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
import org.gradle.api.artifacts.VersionConstraint;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.internal.artifacts.ResolvedVersionConstraint;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ComponentResolvers;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.LatestVersionSelector;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.Version;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionComparator;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionParser;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelector;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelectorScheme;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.LocalComponentRegistry;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectDependencyResolver;
import org.gradle.api.specs.Spec;
import org.gradle.initialization.NestedBuildFactory;
import org.gradle.internal.Pair;
import org.gradle.internal.build.BuildStateRegistry;
import org.gradle.internal.build.IncludedBuildState;
import org.gradle.internal.component.local.model.DefaultProjectComponentSelector;
import org.gradle.internal.component.local.model.LocalComponentMetadata;
import org.gradle.internal.component.model.DependencyMetadata;
import org.gradle.internal.hash.HashUtil;
import org.gradle.internal.resolve.ModuleVersionNotFoundException;
import org.gradle.internal.resolve.ModuleVersionResolveException;
import org.gradle.internal.resolve.resolver.ArtifactResolver;
import org.gradle.internal.resolve.resolver.ComponentMetaDataResolver;
import org.gradle.internal.resolve.resolver.DependencyToComponentIdResolver;
import org.gradle.internal.resolve.resolver.OriginArtifactSelector;
import org.gradle.internal.resolve.result.BuildableComponentIdResolveResult;
import org.gradle.util.CollectionUtils;
import org.gradle.vcs.VersionControlSpec;
import org.gradle.vcs.internal.VcsResolver;
import org.gradle.vcs.internal.VcsWorkingDirectoryRoot;
import org.gradle.vcs.internal.VersionControlSystem;
import org.gradle.vcs.internal.VersionControlSystemFactory;
import org.gradle.vcs.internal.VersionRef;
import org.gradle.vcs.internal.spec.AbstractVersionControlSpec;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class VcsDependencyResolver implements DependencyToComponentIdResolver, ComponentResolvers {
    private final ProjectDependencyResolver projectDependencyResolver;
    private final LocalComponentRegistry localComponentRegistry;
    private final VcsResolver vcsResolver;
    private final VersionControlSystemFactory versionControlSystemFactory;
    private final VersionSelectorScheme versionSelectorScheme;
    private final VersionComparator versionComparator;
    private final BuildStateRegistry buildRegistry;
    private final NestedBuildFactory nestedBuildFactory;
    private final File baseWorkingDir;
    private final Map<String, VersionRef> selectedVersionCache = new HashMap<String, VersionRef>();

    public VcsDependencyResolver(VcsWorkingDirectoryRoot vcsWorkingDirRoot, ProjectDependencyResolver projectDependencyResolver, LocalComponentRegistry localComponentRegistry, VcsResolver vcsResolver, VersionControlSystemFactory versionControlSystemFactory, VersionSelectorScheme versionSelectorScheme, VersionComparator versionComparator, BuildStateRegistry buildRegistry, NestedBuildFactory nestedBuildFactory) {
        this.baseWorkingDir = vcsWorkingDirRoot.getDir();
        this.projectDependencyResolver = projectDependencyResolver;
        this.localComponentRegistry = localComponentRegistry;
        this.vcsResolver = vcsResolver;
        this.versionControlSystemFactory = versionControlSystemFactory;
        this.versionSelectorScheme = versionSelectorScheme;
        this.versionComparator = versionComparator;
        this.buildRegistry = buildRegistry;
        this.nestedBuildFactory = nestedBuildFactory;
    }

    @Override
    public void resolve(DependencyMetadata dependency, ResolvedVersionConstraint versionConstraint, BuildableComponentIdResolveResult result) {
        if (dependency.getSelector() instanceof ModuleComponentSelector) {
            final ModuleComponentSelector depSelector = (ModuleComponentSelector) dependency.getSelector();
            VersionControlSpec spec = vcsResolver.locateVcsFor(depSelector);

            // TODO: Need failure handling, e.g., cannot clone repository
            if (spec != null) {
                VersionControlSystem versionControlSystem = versionControlSystemFactory.create(spec);

                VersionRef selectedVersion = selectVersion(depSelector, spec, versionControlSystem);
                if (selectedVersion == null) {
                    result.failed(new ModuleVersionNotFoundException(depSelector, Collections.singleton(spec.getDisplayName())));
                    return;
                }

                File dependencyWorkingDir = new File(populateWorkingDirectory(baseWorkingDir, spec, versionControlSystem, selectedVersion), spec.getRootDir());

                IncludedBuildState includedBuild = buildRegistry.addImplicitBuild(((AbstractVersionControlSpec)spec).getBuildDefinition(dependencyWorkingDir), nestedBuildFactory);

                Collection<Pair<ModuleVersionIdentifier, ProjectComponentIdentifier>> moduleToProject = includedBuild.getAvailableModules();
                Pair<ModuleVersionIdentifier, ProjectComponentIdentifier> entry = CollectionUtils.findFirst(moduleToProject, new Spec<Pair<ModuleVersionIdentifier, ProjectComponentIdentifier>>() {
                    @Override
                    public boolean isSatisfiedBy(Pair<ModuleVersionIdentifier, ProjectComponentIdentifier> entry) {
                        ModuleVersionIdentifier possibleMatch = entry.left;
                        return depSelector.getGroup().equals(possibleMatch.getGroup())
                            && depSelector.getModule().equals(possibleMatch.getName());
                    }
                });
                if (entry == null) {
                    result.failed(new ModuleVersionResolveException(depSelector, spec.getDisplayName() + " did not contain a project publishing the specified dependency."));
                } else {
                    LocalComponentMetadata componentMetaData = localComponentRegistry.getComponent(entry.right);

                    if (componentMetaData == null) {
                        result.failed(new ModuleVersionResolveException(DefaultProjectComponentSelector.newSelector(includedBuild.getBuildIdentifier(), entry.right.getProjectPath()), spec.getDisplayName() + " could not be resolved into a usable project."));
                    } else {
                        result.resolved(componentMetaData);
                    }
                    return;
                }
            }
        }

        projectDependencyResolver.resolve(dependency, versionConstraint, result);
    }

    private VersionRef selectVersion(ModuleComponentSelector depSelector, VersionControlSpec spec, VersionControlSystem versionControlSystem) {
        String cacheKey = cacheKey(spec, depSelector.getVersionConstraint());
        if (selectedVersionCache.containsKey(cacheKey)) {
            return selectedVersionCache.get(cacheKey);
        } else {
            VersionRef selectedVersion = selectVersionFromRepository(spec, versionControlSystem, depSelector.getVersionConstraint());
            selectedVersionCache.put(cacheKey, selectedVersion);
            return selectedVersion;
        }
    }

    private File populateWorkingDirectory(File baseWorkingDir, VersionControlSpec spec, VersionControlSystem versionControlSystem, VersionRef selectedVersion) {
        String repositoryId = HashUtil.createCompactMD5(spec.getUniqueId());
        File versionDirectory = new File(baseWorkingDir, repositoryId + "-" + selectedVersion.getCanonicalId());
        return versionControlSystem.populate(versionDirectory, selectedVersion, spec);
    }

    private VersionRef selectVersionFromRepository(VersionControlSpec spec, VersionControlSystem versionControlSystem, VersionConstraint constraint) {
        // TODO: match with status, order versions correctly

        if (constraint.getBranch() != null) {
            return versionControlSystem.getBranch(spec, constraint.getBranch());
        }

        String version = constraint.getPreferredVersion();
        VersionSelector versionSelector = versionSelectorScheme.parseSelector(version);
        if (versionSelector instanceof LatestVersionSelector && ((LatestVersionSelector)versionSelector).getSelectorStatus().equals("integration")) {
            return versionControlSystem.getDefaultBranch(spec);
        }

        if (versionSelector.requiresMetadata()) {
            // TODO - implement this by moving this resolver to live alongside the external resolvers
            return null;
        }

        Set<VersionRef> versions = versionControlSystem.getAvailableVersions(spec);
        Version bestVersion = null;
        VersionRef bestCandidate = null;
        for (VersionRef candidate : versions) {
            Version candidateVersion = VersionParser.INSTANCE.transform(candidate.getVersion());
            if (versionSelector.accept(candidateVersion)) {
                if (bestCandidate == null || versionComparator.asVersionComparator().compare(candidateVersion, bestVersion) > 0) {
                    bestVersion = candidateVersion;
                    bestCandidate = candidate;
                }
            }
        }

        return bestCandidate;
    }

    private String cacheKey(VersionControlSpec spec, VersionConstraint constraint) {
        if (constraint.getBranch() != null) {
            return spec.getUniqueId() + ":b:" + constraint.getBranch();
        }
        return spec.getUniqueId() + ":v:" + constraint.getPreferredVersion();
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
