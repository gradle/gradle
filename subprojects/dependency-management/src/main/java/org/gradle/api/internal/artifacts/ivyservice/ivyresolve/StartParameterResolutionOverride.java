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
import org.gradle.api.GradleException;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.verification.DependencyVerificationMode;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.internal.artifacts.configurations.dynamicversion.CachePolicy;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.verification.ChecksumAndSignatureVerificationOverride;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.verification.DependencyVerificationOverride;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.verification.writer.WriteDependencyVerificationFile;
import org.gradle.api.internal.artifacts.ivyservice.resolutionstrategy.ExternalResourceCachePolicy;
import org.gradle.api.internal.artifacts.repositories.resolver.MetadataFetchingCost;
import org.gradle.api.internal.artifacts.verification.signatures.SignatureVerificationServiceFactory;
import org.gradle.api.internal.component.ArtifactType;
import org.gradle.api.resources.ResourceException;
import org.gradle.internal.component.external.model.ModuleDependencyMetadata;
import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.internal.component.model.ComponentOverrideMetadata;
import org.gradle.internal.component.model.ComponentResolveMetadata;
import org.gradle.internal.component.model.ConfigurationMetadata;
import org.gradle.internal.component.model.ModuleSources;
import org.gradle.internal.hash.ChecksumService;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.resolve.ArtifactResolveException;
import org.gradle.internal.resolve.ModuleVersionResolveException;
import org.gradle.internal.resolve.result.BuildableArtifactResolveResult;
import org.gradle.internal.resolve.result.BuildableArtifactSetResolveResult;
import org.gradle.internal.resolve.result.BuildableComponentArtifactsResolveResult;
import org.gradle.internal.resolve.result.BuildableModuleComponentMetaDataResolveResult;
import org.gradle.internal.resolve.result.BuildableModuleVersionListingResolveResult;
import org.gradle.internal.resource.ReadableContent;
import org.gradle.internal.resource.metadata.ExternalResourceMetaData;
import org.gradle.internal.resource.transfer.ExternalResourceConnector;
import org.gradle.internal.resource.transfer.ExternalResourceReadResponse;
import org.gradle.util.SingleMessageLogger;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.List;

public class StartParameterResolutionOverride {
    private final StartParameter startParameter;

    public StartParameterResolutionOverride(StartParameter startParameter) {
        this.startParameter = startParameter;
    }

    public void applyToCachePolicy(CachePolicy cachePolicy) {
        if (startParameter.isOffline()) {
            cachePolicy.setOffline();
        } else if (startParameter.isRefreshDependencies()) {
            cachePolicy.setRefreshDependencies();
        }
    }

    public ModuleComponentRepository overrideModuleVersionRepository(ModuleComponentRepository original) {
        if (startParameter.isOffline()) {
            return new OfflineModuleComponentRepository(original);
        }
        return original;
    }

    public DependencyVerificationOverride dependencyVerificationOverride(BuildOperationExecutor buildOperationExecutor,
                                                                         ChecksumService checksumService,
                                                                         SignatureVerificationServiceFactory signatureVerificationServiceFactory,
                                                                         DocumentationRegistry documentationRegistry) {
        File currentDir = startParameter.getCurrentDir();
        List<String> checksums = startParameter.getWriteDependencyVerifications();
        if (!checksums.isEmpty()) {
            SingleMessageLogger.incubatingFeatureUsed("Dependency verification");
            return new WriteDependencyVerificationFile(currentDir, buildOperationExecutor, checksums, checksumService, signatureVerificationServiceFactory, startParameter.isDryRun(), startParameter.isExportKeys());
        } else {
            File verificationsFile = DependencyVerificationOverride.dependencyVerificationsFile(currentDir);
            File keyringsFile = DependencyVerificationOverride.keyringsFile(currentDir);
            if (verificationsFile.exists()) {
                if (startParameter.getDependencyVerificationMode() == DependencyVerificationMode.OFF) {
                    return DependencyVerificationOverride.NO_VERIFICATION;
                }
                SingleMessageLogger.incubatingFeatureUsed("Dependency verification");
                try {
                    return new ChecksumAndSignatureVerificationOverride(buildOperationExecutor, startParameter.getGradleUserHomeDir(), verificationsFile, keyringsFile, checksumService, signatureVerificationServiceFactory, startParameter.getDependencyVerificationMode(), documentationRegistry);
                } catch (Exception e) {
                    return new FailureVerificationOverride(e, verificationsFile);
                }
            }
        }
        return DependencyVerificationOverride.NO_VERIFICATION;
    }

    private static class OfflineModuleComponentRepository extends BaseModuleComponentRepository {

        private final FailedRemoteAccess failedRemoteAccess = new FailedRemoteAccess();

        public OfflineModuleComponentRepository(ModuleComponentRepository original) {
            super(original);
        }

        @Override
        public ModuleComponentRepositoryAccess getRemoteAccess() {
            return failedRemoteAccess;
        }
    }

    private static class FailedRemoteAccess implements ModuleComponentRepositoryAccess {
        @Override
        public String toString() {
            return "offline remote";
        }

        @Override
        public void listModuleVersions(ModuleDependencyMetadata dependency, BuildableModuleVersionListingResolveResult result) {
            result.failed(new ModuleVersionResolveException(dependency.getSelector(), () -> String.format("No cached version listing for %s available for offline mode.", dependency.getSelector())));
        }

        @Override
        public void resolveComponentMetaData(ModuleComponentIdentifier moduleComponentIdentifier, ComponentOverrideMetadata requestMetaData, BuildableModuleComponentMetaDataResolveResult result) {
            result.failed(new ModuleVersionResolveException(moduleComponentIdentifier, () -> String.format("No cached version of %s available for offline mode.", moduleComponentIdentifier.getDisplayName())));
        }

        @Override
        public void resolveArtifactsWithType(ComponentResolveMetadata component, ArtifactType artifactType, BuildableArtifactSetResolveResult result) {
            result.failed(new ArtifactResolveException(component.getId(), "No cached version available for offline mode"));
        }

        @Override
        public void resolveArtifacts(ComponentResolveMetadata component, ConfigurationMetadata variant, BuildableComponentArtifactsResolveResult result) {
            result.failed(new ArtifactResolveException(component.getId(), "No cached version available for offline mode"));
        }

        @Override
        public void resolveArtifact(ComponentArtifactMetadata artifact, ModuleSources moduleSources, BuildableArtifactResolveResult result) {
            result.failed(new ArtifactResolveException(artifact.getId(), "No cached version available for offline mode"));
        }

        @Override
        public MetadataFetchingCost estimateMetadataFetchingCost(ModuleComponentIdentifier moduleComponentIdentifier) {
            return MetadataFetchingCost.CHEAP;
        }
    }

    public ExternalResourceCachePolicy overrideExternalResourceCachePolicy(ExternalResourceCachePolicy original) {
        if (startParameter.isOffline()) {
            return new ExternalResourceCachePolicy() {
                @Override
                public boolean mustRefreshExternalResource(long ageMillis) {
                    return false;
                }
            };
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
        public ExternalResourceReadResponse openResource(URI location, boolean revalidate) throws ResourceException {
            throw offlineResource(location);
        }

        @Nullable
        @Override
        public ExternalResourceMetaData getMetaData(URI location, boolean revalidate) throws ResourceException {
            throw offlineResource(location);
        }

        @Nullable
        @Override
        public List<String> list(URI parent) throws ResourceException {
            throw offlineResource(parent);
        }

        @Override
        public void upload(ReadableContent resource, URI destination) throws IOException {
            throw new ResourceException(destination, String.format("Cannot upload to '%s' in offline mode.", destination));
        }

        private ResourceException offlineResource(URI source) {
            return new ResourceException(source, String.format("No cached resource '%s' available for offline mode.", source));
        }
    }

    private static class FailureVerificationOverride implements DependencyVerificationOverride {
        private final Exception error;
        private final File verificationFile;

        private FailureVerificationOverride(Exception error, File verificationFile) {
            this.error = error;
            this.verificationFile = verificationFile;
        }

        @Override
        public ModuleComponentRepository overrideDependencyVerification(ModuleComponentRepository original) {
            throw new GradleException("Dependency verification cannot be performed because the configuration couldn't be read: "+ verificationFile, error);
        }
    }
}
