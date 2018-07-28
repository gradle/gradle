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
import org.gradle.api.artifacts.VersionConstraint;
import org.gradle.api.artifacts.component.ComponentIdentifier;
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
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.ModuleExclusion;
import org.gradle.api.internal.artifacts.type.ArtifactTypeRegistry;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.component.ArtifactType;
import org.gradle.api.specs.Spec;
import org.gradle.internal.Pair;
import org.gradle.internal.build.BuildStateRegistry;
import org.gradle.internal.build.IncludedBuildState;
import org.gradle.internal.component.local.model.LocalComponentMetadata;
import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.internal.component.model.ComponentOverrideMetadata;
import org.gradle.internal.component.model.ComponentResolveMetadata;
import org.gradle.internal.component.model.ConfigurationMetadata;
import org.gradle.internal.component.model.DependencyMetadata;
import org.gradle.internal.component.model.ModuleSource;
import org.gradle.internal.hash.HashUtil;
import org.gradle.internal.resolve.ModuleVersionNotFoundException;
import org.gradle.internal.resolve.ModuleVersionResolveException;
import org.gradle.internal.resolve.resolver.ArtifactResolver;
import org.gradle.internal.resolve.resolver.ComponentMetaDataResolver;
import org.gradle.internal.resolve.resolver.DependencyToComponentIdResolver;
import org.gradle.internal.resolve.resolver.OriginArtifactSelector;
import org.gradle.internal.resolve.result.BuildableArtifactResolveResult;
import org.gradle.internal.resolve.result.BuildableArtifactSetResolveResult;
import org.gradle.internal.resolve.result.BuildableComponentIdResolveResult;
import org.gradle.internal.resolve.result.BuildableComponentResolveResult;
import org.gradle.util.CollectionUtils;
import org.gradle.vcs.VersionControlSpec;
import org.gradle.vcs.internal.VcsResolver;
import org.gradle.vcs.internal.VcsWorkingDirectoryRoot;
import org.gradle.vcs.internal.VersionControlSystem;
import org.gradle.vcs.internal.VersionControlSystemFactory;
import org.gradle.vcs.internal.VersionRef;
import org.gradle.vcs.internal.spec.AbstractVersionControlSpec;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

public class VcsDependencyResolver implements DependencyToComponentIdResolver, ComponentResolvers, ComponentMetaDataResolver, OriginArtifactSelector, ArtifactResolver {
    private final LocalComponentRegistry localComponentRegistry;
    private final VcsResolver vcsResolver;
    private final VersionControlSystemFactory versionControlSystemFactory;
    private final VersionSelectorScheme versionSelectorScheme;
    private final VersionComparator versionComparator;
    private final BuildStateRegistry buildRegistry;
    private final File baseWorkingDir;
    private final VcsVersionSelectionCache versionSelectionCache;
    private final VersionParser versionParser;

    public VcsDependencyResolver(VcsWorkingDirectoryRoot vcsWorkingDirRoot, LocalComponentRegistry localComponentRegistry, VcsResolver vcsResolver, VersionControlSystemFactory versionControlSystemFactory, VersionSelectorScheme versionSelectorScheme, VersionComparator versionComparator, BuildStateRegistry buildRegistry, VersionParser versionParser, VcsVersionSelectionCache versionSelectionCache) {
        this.baseWorkingDir = vcsWorkingDirRoot.getDir();
        this.localComponentRegistry = localComponentRegistry;
        this.vcsResolver = vcsResolver;
        this.versionControlSystemFactory = versionControlSystemFactory;
        this.versionSelectorScheme = versionSelectorScheme;
        this.versionComparator = versionComparator;
        this.buildRegistry = buildRegistry;
        this.versionParser = versionParser;
        this.versionSelectionCache = versionSelectionCache;
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

                IncludedBuildState includedBuild = buildRegistry.addImplicitIncludedBuild(((AbstractVersionControlSpec)spec).getBuildDefinition(dependencyWorkingDir));

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
                    result.resolved(componentMetaData);
                }
            }
        }
    }

    private VersionRef selectVersion(ModuleComponentSelector depSelector, VersionControlSpec spec, VersionControlSystem versionControlSystem) {
        VersionRef selectedVersion = versionSelectionCache.getResolvedVersion(spec, depSelector.getVersionConstraint());
        if (selectedVersion == null) {
            // TODO - prevent multiple threads from performing the same selection
            selectedVersion = selectVersionFromRepository(spec, versionControlSystem, depSelector.getVersionConstraint());
            if (selectedVersion != null) {
                versionSelectionCache.putResolvedVersion(spec, depSelector.getVersionConstraint(), selectedVersion);
            }
        }
        return selectedVersion;
    }

    private File populateWorkingDirectory(File baseWorkingDir, VersionControlSpec spec, VersionControlSystem versionControlSystem, VersionRef selectedVersion) {
        File checkoutDir = versionSelectionCache.getCheckoutDir(spec, selectedVersion);
        if (checkoutDir == null) {
            String repositoryId = HashUtil.createCompactMD5(spec.getUniqueId());
            File versionDirectory = new File(baseWorkingDir, repositoryId + "-" + selectedVersion.getCanonicalId());
            checkoutDir = versionControlSystem.populate(versionDirectory, selectedVersion, spec);
            versionSelectionCache.putCheckoutDir(spec, selectedVersion, checkoutDir);
        }
        return checkoutDir;
    }

    private VersionRef selectVersionFromRepository(VersionControlSpec spec, VersionControlSystem versionControlSystem, VersionConstraint constraint) {
        // TODO: match with status, order versions correctly

        if (constraint.getBranch() != null) {
            return versionControlSystem.getBranch(spec, constraint.getBranch());
        }

        String version = constraint.getRequiredVersion();
        VersionSelector versionSelector = versionSelectorScheme.parseSelector(version);
        if (versionSelector instanceof LatestVersionSelector && ((LatestVersionSelector)versionSelector).getSelectorStatus().equals("integration")) {
            return versionControlSystem.getDefaultBranch(spec);
        }

        if (versionSelector.requiresMetadata()) {
            // TODO - implement this by moving this resolver to live alongside the external resolvers
            return null;
        }

        Set<VersionRef> versions = versionSelectionCache.getVersions(spec);
        if (versions == null) {
            versions = versionControlSystem.getAvailableVersions(spec);
            versionSelectionCache.putVersions(spec, versions);
        }
        Version bestVersion = null;
        VersionRef bestCandidate = null;
        for (VersionRef candidate : versions) {
            Version candidateVersion = versionParser.transform(candidate.getVersion());
            if (versionSelector.accept(candidateVersion)) {
                if (bestCandidate == null || versionComparator.asVersionComparator().compare(candidateVersion, bestVersion) > 0) {
                    bestVersion = candidateVersion;
                    bestCandidate = candidate;
                }
            }
        }

        return bestCandidate;
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
    public OriginArtifactSelector getArtifactSelector() {
        return this;
    }

    @Nullable
    @Override
    public ArtifactSet resolveArtifacts(ComponentResolveMetadata component, ConfigurationMetadata configuration, ArtifactTypeRegistry artifactTypeRegistry, ModuleExclusion exclusions, ImmutableAttributes overriddenAttributes) {
        return null;
    }

    @Override
    public ArtifactResolver getArtifactResolver() {
        return this;
    }

    @Override
    public void resolveArtifactsWithType(ComponentResolveMetadata component, ArtifactType artifactType, BuildableArtifactSetResolveResult result) {
    }

    @Override
    public void resolveArtifact(ComponentArtifactMetadata artifact, ModuleSource moduleSource, BuildableArtifactResolveResult result) {
    }
}
