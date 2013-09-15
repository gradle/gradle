/*
 * Copyright 2013 the original author or authors.
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

import org.gradle.StartParameter;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.internal.ClassPathRegistry;
import org.gradle.api.internal.artifacts.dependencies.DefaultExternalModuleDependency;
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyFactory;
import org.gradle.api.internal.artifacts.ivyservice.ArtifactCacheMetaData;
import org.gradle.api.internal.artifacts.ivyservice.CacheLockingManager;
import org.gradle.api.internal.artifacts.ivyservice.DefaultCacheLockingManager;
import org.gradle.api.internal.artifacts.ivyservice.dynamicversions.ModuleResolutionCache;
import org.gradle.api.internal.artifacts.ivyservice.dynamicversions.SingleFileBackedModuleResolutionCache;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.memcache.InMemoryDependencyMetadataCache;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.DefaultModuleMetaDataCache;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.ModuleMetaDataCache;
import org.gradle.api.internal.externalresource.cached.ByUrlCachedExternalResourceIndex;
import org.gradle.api.internal.externalresource.ivy.ArtifactAtRepositoryCachedArtifactIndex;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.TmpDirTemporaryFileProvider;
import org.gradle.api.internal.filestore.PathKeyFileStore;
import org.gradle.api.internal.filestore.UniquePathKeyFileStore;
import org.gradle.api.internal.filestore.ivy.ArtifactRevisionIdFileStore;
import org.gradle.api.internal.notations.*;
import org.gradle.api.internal.notations.api.NotationParser;
import org.gradle.cache.CacheRepository;
import org.gradle.initialization.ProjectAccessListener;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.util.BuildCommencedTimeProvider;

import java.io.File;
import java.util.Arrays;
import java.util.List;

class DependencyManagementBuildScopeServices {
    InMemoryDependencyMetadataCache createInMemoryDependencyMetadataCache() {
        return new InMemoryDependencyMetadataCache();
    }

    DependencyManagementServices createDependencyManagementServices(ServiceRegistry parent) {
        return new DefaultDependencyManagementServices(parent);
    }

    DependencyFactory createDependencyFactory(Instantiator instantiator,
                                              ProjectAccessListener projectAccessListener,
                                              StartParameter startParameter,
                                              ClassPathRegistry classPathRegistry,
                                              FileResolver fileResolver) {
        DefaultProjectDependencyFactory factory = new DefaultProjectDependencyFactory(
                projectAccessListener, instantiator, startParameter.isBuildProjectDependencies());

        ProjectDependencyFactory projectDependencyFactory = new ProjectDependencyFactory(factory);
        DependencyProjectNotationParser projParser = new DependencyProjectNotationParser(factory);

        NotationParser<? extends Dependency> moduleMapParser = new DependencyMapNotationParser<DefaultExternalModuleDependency>(instantiator, DefaultExternalModuleDependency.class);
        NotationParser<? extends Dependency> moduleStringParser = new DependencyStringNotationParser<DefaultExternalModuleDependency>(instantiator, DefaultExternalModuleDependency.class);
        NotationParser<? extends Dependency> selfResolvingDependencyFactory = new DependencyFilesNotationParser(instantiator);

        List<NotationParser<? extends Dependency>> notationParsers = Arrays.asList(
                moduleStringParser,
                moduleMapParser,
                selfResolvingDependencyFactory,
                projParser,
                new DependencyClassPathNotationParser(instantiator, classPathRegistry, fileResolver.withNoBaseDir()));

        return new DefaultDependencyFactory(
                new DependencyNotationParser(notationParsers),
                new ClientModuleNotationParserFactory(instantiator).create(),
                projectDependencyFactory);
    }

    CacheLockingManager createCacheLockingManager(CacheRepository cacheRepository) {
        return new DefaultCacheLockingManager(cacheRepository);
    }

    BuildCommencedTimeProvider createBuildTimeProvider() {
        return new BuildCommencedTimeProvider();
    }

    ModuleResolutionCache createModuleResolutionCache(ArtifactCacheMetaData artifactCacheMetaData, BuildCommencedTimeProvider timeProvider, CacheLockingManager cacheLockingManager) {
        return new SingleFileBackedModuleResolutionCache(
                artifactCacheMetaData,
                timeProvider,
                cacheLockingManager
        );
    }

    ModuleMetaDataCache createModuleDescriptorCache(ArtifactCacheMetaData artifactCacheMetaData, BuildCommencedTimeProvider timeProvider, CacheLockingManager cacheLockingManager) {
        return new DefaultModuleMetaDataCache(
                artifactCacheMetaData,
                timeProvider,
                cacheLockingManager
        );
    }

    ArtifactAtRepositoryCachedArtifactIndex createArtifactAtRepositoryCachedResolutionIndex(ArtifactCacheMetaData artifactCacheMetaData, BuildCommencedTimeProvider timeProvider, CacheLockingManager cacheLockingManager) {
        return new ArtifactAtRepositoryCachedArtifactIndex(
                new File(artifactCacheMetaData.getCacheDir(), "artifact-at-repository.bin"),
                timeProvider,
                cacheLockingManager
        );
    }

    ByUrlCachedExternalResourceIndex createArtifactUrlCachedResolutionIndex(ArtifactCacheMetaData artifactCacheMetaData, BuildCommencedTimeProvider timeProvider, CacheLockingManager cacheLockingManager) {
        return new ByUrlCachedExternalResourceIndex(
                new File(artifactCacheMetaData.getCacheDir(), "artifact-at-url.bin"),
                timeProvider,
                cacheLockingManager
        );
    }

    PathKeyFileStore createUniquePathFileStore(ArtifactCacheMetaData artifactCacheMetaData) {
        return new UniquePathKeyFileStore(new File(artifactCacheMetaData.getCacheDir(), "filestore"));
    }

    ArtifactRevisionIdFileStore createArtifactRevisionIdFileStore(PathKeyFileStore pathKeyFileStore) {
        return new ArtifactRevisionIdFileStore(pathKeyFileStore, new TmpDirTemporaryFileProvider());
    }

}
