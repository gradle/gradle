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
package org.gradle.api.internal.artifacts.ivyservice.ivyresolve;

import org.gradle.StartParameter;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.verification.DependencyVerificationMode;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.internal.artifacts.configurations.dynamicversion.CachePolicy;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.verification.ChecksumAndSignatureVerificationOverride;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.verification.DependencyVerificationOverride;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.verification.writer.WriteDependencyVerificationFile;
import org.gradle.api.internal.artifacts.ivyservice.resolutionstrategy.ExternalResourceCachePolicy;
import org.gradle.api.internal.artifacts.repositories.resolver.MetadataFetchingCost;
import org.gradle.api.internal.artifacts.verification.exceptions.DependencyVerificationException;
import org.gradle.api.internal.artifacts.verification.signatures.SignatureVerificationServiceFactory;
import org.gradle.api.internal.component.ArtifactType;
import org.gradle.api.internal.properties.GradleProperties;
import org.gradle.api.resources.ResourceException;
import org.gradle.internal.Factory;
import org.gradle.internal.component.external.model.ModuleComponentGraphResolveState;
import org.gradle.internal.component.external.model.ModuleComponentResolveMetadata;
import org.gradle.internal.component.external.model.ModuleDependencyMetadata;
import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.internal.component.model.ComponentOverrideMetadata;
import org.gradle.internal.component.model.ComponentResolveMetadata;
import org.gradle.internal.component.model.ModuleSources;
import org.gradle.internal.hash.ChecksumService;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.resolve.ArtifactResolveException;
import org.gradle.internal.resolve.ModuleVersionResolveException;
import org.gradle.internal.resolve.result.BuildableArtifactFileResolveResult;
import org.gradle.internal.resolve.result.BuildableArtifactSetResolveResult;
import org.gradle.internal.resolve.result.BuildableModuleComponentMetaDataResolveResult;
import org.gradle.internal.resolve.result.BuildableModuleVersionListingResolveResult;
import org.gradle.internal.resource.ExternalResource;
import org.gradle.internal.resource.ExternalResourceName;
import org.gradle.internal.resource.ReadableContent;
import org.gradle.internal.resource.local.FileResourceListener;
import org.gradle.internal.resource.metadata.ExternalResourceMetaData;
import org.gradle.internal.resource.transfer.ExternalResourceConnector;
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.util.internal.BuildCommencedTimeProvider;

import javax.annotation.Nullable;
import java.io.File;
import java.util.List;

@ServiceScope(Scopes.BuildTree.class)
public class StartParameterResolutionOverride {
    private final StartParameter startParameter;
    private final File gradleDir;

    public StartParameterResolutionOverride(StartParameter startParameter, File gradleDir) {
        this.startParameter = startParameter;
        this.gradleDir = gradleDir;
    }

    public void applyToCachePolicy(CachePolicy cachePolicy) {
        if (startParameter.isOffline()) {
            cachePolicy.setOffline();
        } else if (startParameter.isRefreshDependencies()) {
            cachePolicy.setRefreshDependencies();
        }
    }

    public ModuleComponentRepository<ModuleComponentResolveMetadata> overrideModuleVersionRepository(ModuleComponentRepository<ModuleComponentResolveMetadata> original) {
        if (startParameter.isOffline()) {
            return new OfflineModuleComponentRepository(original);
        }
        return original;
    }

    public DependencyVerificationOverride dependencyVerificationOverride(
        BuildOperationExecutor buildOperationExecutor,
        ChecksumService checksumService,
        SignatureVerificationServiceFactory signatureVerificationServiceFactory,
        DocumentationRegistry documentationRegistry,
        BuildCommencedTimeProvider timeProvider,
        Factory<GradleProperties> gradlePropertiesFactory,
        FileResourceListener fileResourceListener
    ) {
        List<String> checksums = startParameter.getWriteDependencyVerifications();
        File verificationsFile = DependencyVerificationOverride.dependencyVerificationsFile(gradleDir);
        fileResourceListener.fileObserved(verificationsFile);

        if (!checksums.isEmpty() || startParameter.isExportKeys()) {
            return new WriteDependencyVerificationFile(verificationsFile, buildOperationExecutor, checksums, checksumService, signatureVerificationServiceFactory, startParameter.isDryRun(), startParameter.isExportKeys());
        }

        if (!verificationsFile.exists() ||
            startParameter.getDependencyVerificationMode() == DependencyVerificationMode.OFF
        ) {
            return DependencyVerificationOverride.NO_VERIFICATION;
        }

        try {
            File sessionReportDir = computeReportDirectory(timeProvider);
            return new ChecksumAndSignatureVerificationOverride(buildOperationExecutor, startParameter.getGradleUserHomeDir(), verificationsFile, checksumService, signatureVerificationServiceFactory, startParameter.getDependencyVerificationMode(), documentationRegistry, sessionReportDir, gradlePropertiesFactory, fileResourceListener);
        } catch (Exception e) {
            return new FailureVerificationOverride(e);
        }
    }

    private File computeReportDirectory(BuildCommencedTimeProvider timeProvider) {
        // TODO: This is not quite correct: we're using the "root project" build directory
        // but technically speaking, this can be changed _after_ this service is created.
        // There's currently no good way to figure that out.
        File buildDir = new File(gradleDir.getParentFile(), "build");
        File reportsDirectory = new File(buildDir, "reports");
        File verifyReportsDirectory = new File(reportsDirectory, "dependency-verification");
        return new File(verifyReportsDirectory, "at-" + timeProvider.getCurrentTime());
    }

    private static class OfflineModuleComponentRepository extends BaseModuleComponentRepository<ModuleComponentResolveMetadata> {

        private final FailedRemoteAccess failedRemoteAccess = new FailedRemoteAccess();

        public OfflineModuleComponentRepository(ModuleComponentRepository<ModuleComponentResolveMetadata> original) {
            super(original);
        }

        @Override
        public ModuleComponentRepositoryAccess<ModuleComponentResolveMetadata> getRemoteAccess() {
            return failedRemoteAccess;
        }
    }

    private static class FailedRemoteAccess implements ModuleComponentRepositoryAccess<ModuleComponentResolveMetadata> {
        @Override
        public String toString() {
            return "offline remote";
        }

        @Override
        public void listModuleVersions(ModuleDependencyMetadata dependency, BuildableModuleVersionListingResolveResult result) {
            result.failed(new ModuleVersionResolveException(dependency.getSelector(), () -> String.format("No cached version listing for %s available for offline mode.", dependency.getSelector())));
        }

        @Override
        public void resolveComponentMetaData(ModuleComponentIdentifier moduleComponentIdentifier, ComponentOverrideMetadata requestMetaData, BuildableModuleComponentMetaDataResolveResult<ModuleComponentResolveMetadata> result) {
            result.failed(new ModuleVersionResolveException(moduleComponentIdentifier, () -> String.format("No cached version of %s available for offline mode.", moduleComponentIdentifier.getDisplayName())));
        }

        @Override
        public void resolveArtifactsWithType(ComponentResolveMetadata component, ArtifactType artifactType, BuildableArtifactSetResolveResult result) {
            result.failed(new ArtifactResolveException(component.getId(), "No cached version available for offline mode"));
        }

        @Override
        public void resolveArtifact(ComponentArtifactMetadata artifact, ModuleSources moduleSources, BuildableArtifactFileResolveResult result) {
            result.failed(new ArtifactResolveException(artifact.getId(), "No cached version available for offline mode"));
        }

        @Override
        public MetadataFetchingCost estimateMetadataFetchingCost(ModuleComponentIdentifier moduleComponentIdentifier) {
            return MetadataFetchingCost.CHEAP;
        }
    }

    public ExternalResourceCachePolicy overrideExternalResourceCachePolicy(ExternalResourceCachePolicy original) {
        if (startParameter.isOffline()) {
            return ageMillis -> false;
        }
        return original;
    }

    public ExternalResourceConnector overrideExternalResourceConnector(ExternalResourceConnector original) {
        if (startParameter.isOffline()) {
            return new OfflineExternalResourceConnector();
        }
        return original;
    }

    private static class OfflineExternalResourceConnector implements ExternalResourceConnector {
        @Nullable
        @Override
        public <T> T withContent(ExternalResourceName location, boolean revalidate, ExternalResource.ContentAndMetadataAction<T> action) throws ResourceException {
            throw offlineResource(location);
        }

        @Nullable
        @Override
        public ExternalResourceMetaData getMetaData(ExternalResourceName location, boolean revalidate) throws ResourceException {
            throw offlineResource(location);
        }

        @Nullable
        @Override
        public List<String> list(ExternalResourceName parent) throws ResourceException {
            throw offlineResource(parent);
        }

        @Override
        public void upload(ReadableContent resource, ExternalResourceName destination) {
            throw new ResourceException(destination.getUri(), String.format("Cannot upload to '%s' in offline mode.", destination.getUri()));
        }

        private ResourceException offlineResource(ExternalResourceName source) {
            return new ResourceException(source.getUri(), String.format("No cached resource '%s' available for offline mode.", source.getUri()));
        }
    }

    private static class FailureVerificationOverride implements DependencyVerificationOverride {
        private final Exception error;

        private FailureVerificationOverride(Exception error) {
            this.error = error;
        }

        @Override
        public ModuleComponentRepository<ModuleComponentGraphResolveState> overrideDependencyVerification(ModuleComponentRepository<ModuleComponentGraphResolveState> original) {
            throw new DependencyVerificationException("Dependency verification cannot be performed", error);
        }
    }
}
