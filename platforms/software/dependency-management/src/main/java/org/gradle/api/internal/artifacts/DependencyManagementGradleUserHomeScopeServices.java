/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.artifacts;

import org.gradle.BuildAdapter;
import org.gradle.BuildResult;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.internal.artifacts.ivyservice.ArtifactCachesProvider;
import org.gradle.api.internal.artifacts.ivyservice.CacheLayout;
import org.gradle.api.internal.artifacts.ivyservice.DefaultArtifactCaches;
import org.gradle.api.internal.artifacts.transform.ImmutableTransformWorkspaceServices;
import org.gradle.api.internal.artifacts.transform.ToPlannedTransformStepConverter;
import org.gradle.api.internal.artifacts.transform.TransformExecutionResult;
import org.gradle.api.internal.cache.CacheConfigurationsInternal;
import org.gradle.cache.Cache;
import org.gradle.cache.CacheBuilder;
import org.gradle.cache.UnscopedCacheBuilderFactory;
import org.gradle.cache.internal.CrossBuildInMemoryCache;
import org.gradle.cache.internal.CrossBuildInMemoryCacheFactory;
import org.gradle.cache.internal.UsedGradleVersions;
import org.gradle.cache.scopes.GlobalScopedCacheBuilderFactory;
import org.gradle.execution.plan.ToPlannedNodeConverter;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.execution.ExecutionEngine;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.execution.workspace.ImmutableWorkspaceProvider;
import org.gradle.internal.execution.workspace.impl.CacheBasedImmutableWorkspaceProvider;
import org.gradle.internal.file.FileAccessTimeJournal;
import org.gradle.internal.service.Provides;
import org.gradle.internal.service.ServiceRegistrationProvider;

public class DependencyManagementGradleUserHomeScopeServices implements ServiceRegistrationProvider {

    @Provides
    ToPlannedNodeConverter createToPlannedTransformStepConverter() {
        return new ToPlannedTransformStepConverter();
    }

    @Provides
    DefaultArtifactCaches.WritableArtifactCacheLockingParameters createWritableArtifactCacheLockingParameters(FileAccessTimeJournal fileAccessTimeJournal, UsedGradleVersions usedGradleVersions) {
        return new DefaultArtifactCaches.WritableArtifactCacheLockingParameters() {
            @Override
            public FileAccessTimeJournal getFileAccessTimeJournal() {
                return fileAccessTimeJournal;
            }

            @Override
            public UsedGradleVersions getUsedGradleVersions() {
                return usedGradleVersions;
            }
        };
    }

    @Provides
    ArtifactCachesProvider createArtifactCaches(
        GlobalScopedCacheBuilderFactory cacheBuilderFactory,
        UnscopedCacheBuilderFactory unscopedCacheBuilderFactory,
        DefaultArtifactCaches.WritableArtifactCacheLockingParameters parameters,
        ListenerManager listenerManager,
        DocumentationRegistry documentationRegistry,
        CacheConfigurationsInternal cacheConfigurations
    ) {
        DefaultArtifactCaches artifactCachesProvider = new DefaultArtifactCaches(cacheBuilderFactory, unscopedCacheBuilderFactory, parameters, documentationRegistry, cacheConfigurations);
        listenerManager.addListener(new BuildAdapter() {
            @SuppressWarnings("deprecation")
            @Override
            public void buildFinished(BuildResult result) {
                artifactCachesProvider.getWritableCacheAccessCoordinator().useCache(() -> {
                    // forces cleanup even if cache wasn't used
                });
            }
        });
        return artifactCachesProvider;
    }

    @Provides
    ImmutableTransformWorkspaceServices createTransformWorkspaceServices(
        GlobalScopedCacheBuilderFactory cacheBuilderFactory,
        CrossBuildInMemoryCacheFactory crossBuildInMemoryCacheFactory,
        FileAccessTimeJournal fileAccessTimeJournal,
        CacheConfigurationsInternal cacheConfigurations
    ) {
        CacheBuilder cacheBuilder = cacheBuilderFactory
            .createCacheBuilder(CacheLayout.TRANSFORMS.getName())
            .withDisplayName("Artifact transforms cache");
        CrossBuildInMemoryCache<UnitOfWork.Identity, ExecutionEngine.IdentityCacheResult<TransformExecutionResult.TransformWorkspaceResult>> identityCache = crossBuildInMemoryCacheFactory.newCacheRetainingDataFromPreviousBuild(result -> result.getResult().isSuccessful());
        CacheBasedImmutableWorkspaceProvider workspaceProvider = CacheBasedImmutableWorkspaceProvider.createWorkspaceProvider(cacheBuilder, fileAccessTimeJournal, cacheConfigurations);
        return new ImmutableTransformWorkspaceServices() {
            @Override
            public ImmutableWorkspaceProvider getWorkspaceProvider() {
                return workspaceProvider;
            }

            @Override
            public Cache<UnitOfWork.Identity, ExecutionEngine.IdentityCacheResult<TransformExecutionResult.TransformWorkspaceResult>> getIdentityCache() {
                return identityCache;
            }

            @Override
            public void close() {
                workspaceProvider.close();
            }
        };
    }
}
