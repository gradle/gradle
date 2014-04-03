/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.artifacts.repositories.legacy;

import com.google.common.collect.ImmutableSet;
import org.apache.ivy.core.IvyContext;
import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.ivy.core.report.ArtifactDownloadReport;
import org.apache.ivy.core.report.DownloadStatus;
import org.apache.ivy.core.resolve.DownloadOptions;
import org.apache.ivy.core.resolve.ResolveData;
import org.apache.ivy.core.resolve.ResolvedModuleRevision;
import org.apache.ivy.core.settings.IvySettings;
import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.gradle.api.artifacts.resolution.JvmLibraryJavadocArtifact;
import org.gradle.api.artifacts.resolution.JvmLibrarySourcesArtifact;
import org.gradle.api.artifacts.resolution.SoftwareArtifact;
import org.gradle.api.internal.artifacts.ivyservice.*;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.*;
import org.gradle.api.internal.artifacts.metadata.*;
import org.gradle.api.internal.artifacts.resolution.IvyDescriptorArtifact;
import org.gradle.internal.UncheckedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.text.ParseException;
import java.util.Collections;
import java.util.Set;

/**
 * A {@link org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleVersionRepository} wrapper around an Ivy {@link DependencyResolver}.
 */
public class IvyDependencyResolverAdapter implements ConfiguredModuleVersionRepository, IvyAwareModuleVersionRepository, LocalArtifactsModuleVersionRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger(IvyDependencyResolverAdapter.class);
    private final DownloadOptions downloadOptions = new DownloadOptions();
    private final String identifier;
    private final DependencyResolver resolver;
    private ResolveData resolveData;

    public IvyDependencyResolverAdapter(DependencyResolver resolver) {
        this.resolver = resolver;
        identifier = DependencyResolverIdentifier.forIvyResolver(resolver);
    }

    public String getId() {
        return identifier;
    }

    public String getName() {
        return resolver.getName();
    }

    @Override
    public String toString() {
        return String.format("Repository '%s'", resolver.getName());
    }

    public boolean isLocal() {
        return resolver.getRepositoryCacheManager() instanceof LocalFileRepositoryCacheManager;
    }

    public void setSettings(IvySettings settings) {
        settings.addResolver(resolver);
    }

    public void setResolveData(ResolveData resolveData) {
        this.resolveData = resolveData;
    }

    public boolean isDynamicResolveMode() {
        return false;
    }

    public void listModuleVersions(DependencyMetaData dependency, BuildableModuleVersionSelectionResolveResult result) {
        IvyContext.getContext().setResolveData(resolveData);
        try {
            ResolvedModuleRevision revision = resolver.getDependency(dependency.getDescriptor(), resolveData);
            if (revision == null) {
                result.listed(new DefaultModuleVersionListing());
            } else {
                result.listed(new DefaultModuleVersionListing(revision.getId().getRevision()));
            }
        } catch (ParseException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    public void getDependency(DependencyMetaData dependency, BuildableModuleVersionMetaDataResolveResult result) {
        IvyContext.getContext().setResolveData(resolveData);
        try {
            ResolvedModuleRevision revision = resolver.getDependency(dependency.getDescriptor(), resolveData);
            if (revision == null) {
                LOGGER.debug("Performed resolved of module '{}' in repository '{}': not found", dependency.getRequested(), getName());
                result.missing();
            } else {
                LOGGER.debug("Performed resolved of module '{}' in repository '{}': found", dependency.getRequested(), getName());
                ModuleDescriptorAdapter metaData = new ModuleDescriptorAdapter(revision.getDescriptor());
                metaData.setChanging(isChanging(revision));
                result.resolved(metaData, null);
            }
        } catch (ParseException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    public void resolveArtifact(ComponentArtifactMetaData artifact, ModuleSource moduleSource, BuildableArtifactResolveResult result) {
        Artifact ivyArtifact = ((ModuleVersionArtifactMetaData) artifact).toIvyArtifact();
        ArtifactDownloadReport artifactDownloadReport = resolver.download(new Artifact[]{ivyArtifact}, downloadOptions).getArtifactReport(ivyArtifact);
        if (downloadFailed(artifactDownloadReport)) {
            if (artifactDownloadReport instanceof EnhancedArtifactDownloadReport) {
                EnhancedArtifactDownloadReport enhancedReport = (EnhancedArtifactDownloadReport) artifactDownloadReport;
                result.failed(new ArtifactResolveException(artifact.getId(), enhancedReport.getFailure()));
            } else {
                result.failed(new ArtifactResolveException(artifact.getId(), artifactDownloadReport.getDownloadDetails()));
            }
            return;
        }

        File localFile = artifactDownloadReport.getLocalFile();
        if (localFile != null) {
            result.resolved(localFile);
        } else {
            result.notFound(artifact.getId());
        }
    }

    public void localResolveModuleArtifacts(ComponentMetaData component, ArtifactResolveContext context, BuildableArtifactSetResolveResult result) {
        doResolveModuleArtifacts(component, context, result, true);
    }

    public void resolveModuleArtifacts(ComponentMetaData component, ArtifactResolveContext context, BuildableArtifactSetResolveResult result) {
        doResolveModuleArtifacts(component, context, result, false);
    }

    // TODO:DAZ This "local-only" pattern is quite ugly: improve it.
    private void doResolveModuleArtifacts(ComponentMetaData component, ArtifactResolveContext context, BuildableArtifactSetResolveResult result, boolean localOnly) {
        ModuleVersionMetaData moduleVersion = (ModuleVersionMetaData) component;
        if (context instanceof ConfigurationResolveContext) {
            String configurationName = ((ConfigurationResolveContext) context).getConfigurationName();
            result.resolved(component.getConfiguration(configurationName).getArtifacts());
            return;
        }

        if (!localOnly && context instanceof ArtifactTypeResolveContext) {
            Class<? extends SoftwareArtifact> artifactType = ((ArtifactTypeResolveContext) context).getArtifactType();
            try {
                result.resolved(doGetCandidateArtifacts(moduleVersion, artifactType));
            } catch (Exception e) {
                result.failed(new ArtifactResolveException(component.getComponentId(), e));
            }
        }
    }

    private Set<ModuleVersionArtifactMetaData> doGetCandidateArtifacts(ModuleVersionMetaData module, Class<? extends SoftwareArtifact> artifactType) {
        if (artifactType == IvyDescriptorArtifact.class) {
            Artifact metadataArtifact = module.getDescriptor().getMetadataArtifact();
            return ImmutableSet.of(module.artifact(metadataArtifact));
        }

        if (artifactType == JvmLibraryJavadocArtifact.class) {
            return createArtifactMetaData(module, "javadoc", "javadoc");
        }

        if (artifactType == JvmLibrarySourcesArtifact.class) {
            return createArtifactMetaData(module, "source", "sources");
        }

        throw new IllegalArgumentException(String.format("Cannot find artifacts of type %s in %s", artifactType.getName(), module));
    }

    private Set<ModuleVersionArtifactMetaData> createArtifactMetaData(ModuleVersionMetaData module, String type, String classifier) {
        ModuleVersionArtifactMetaData artifact = module.artifact(type, "jar", classifier);
        if (resolver.exists(artifact.toIvyArtifact())) {
            return ImmutableSet.of(artifact);
        }
        return Collections.emptySet();
    }

    private boolean downloadFailed(ArtifactDownloadReport artifactReport) {
        // Ivy reports FAILED with MISSING_ARTIFACT message when the artifact doesn't exist.
        return artifactReport.getDownloadStatus() == DownloadStatus.FAILED
                && !artifactReport.getDownloadDetails().equals(ArtifactDownloadReport.MISSING_ARTIFACT);
    }

    private boolean isChanging(ResolvedModuleRevision resolvedModuleRevision) {
        return new ChangingModuleDetector(resolver).isChangingModule(resolvedModuleRevision.getDescriptor());
    }
}
