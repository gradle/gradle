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

package org.gradle.caching.internal;

import org.gradle.api.internal.StartParameterInternal;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.file.temp.TemporaryFileProvider;
import org.gradle.caching.configuration.internal.BuildCacheConfigurationInternal;
import org.gradle.caching.configuration.internal.BuildCacheServiceRegistration;
import org.gradle.caching.configuration.internal.DefaultBuildCacheConfiguration;
import org.gradle.caching.configuration.internal.DefaultBuildCacheServiceRegistration;
import org.gradle.caching.internal.controller.impl.LifecycleAwareBuildCacheController;
import org.gradle.caching.internal.controller.impl.LifecycleAwareBuildCacheControllerFactory;
import org.gradle.caching.internal.origin.OriginMetadataFactory;
import org.gradle.caching.internal.packaging.BuildCacheEntryPacker;
import org.gradle.caching.internal.packaging.impl.DefaultTarPackerFileSystemSupport;
import org.gradle.caching.internal.packaging.impl.FilePermissionAccess;
import org.gradle.caching.internal.packaging.impl.GZipBuildCacheEntryPacker;
import org.gradle.caching.internal.packaging.impl.TarBuildCacheEntryPacker;
import org.gradle.caching.internal.packaging.impl.TarPackerFileSystemSupport;
import org.gradle.caching.internal.services.BuildCacheControllerFactory;
import org.gradle.caching.internal.services.DefaultBuildCacheControllerFactory;
import org.gradle.caching.local.DirectoryBuildCache;
import org.gradle.caching.local.internal.DirectoryBuildCacheServiceFactory;
import org.gradle.internal.build.BuildState;
import org.gradle.internal.build.RootBuildState;
import org.gradle.internal.file.BufferProvider;
import org.gradle.internal.file.Deleter;
import org.gradle.internal.file.FileException;
import org.gradle.internal.file.ThreadLocalBufferProvider;
import org.gradle.internal.hash.StreamHasher;
import org.gradle.internal.instantiation.InstanceGenerator;
import org.gradle.internal.instantiation.InstantiatorFactory;
import org.gradle.internal.nativeintegration.filesystem.FileSystem;
import org.gradle.internal.operations.BuildOperationProgressEventEmitter;
import org.gradle.internal.operations.BuildOperationRunner;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.scopeids.id.BuildInvocationScopeId;
import org.gradle.internal.service.Provides;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.ServiceRegistrationProvider;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.service.scopes.AbstractGradleModuleServices;
import org.gradle.util.GradleVersion;

import java.io.File;
import java.util.List;

/**
 * Build scoped services for build cache usage.
 */
public final class BuildCacheServices extends AbstractGradleModuleServices {
    @Override
    public void registerGlobalServices(ServiceRegistration registration) {
        registration.addProvider(new ServiceRegistrationProvider() {
            @Provides
            BufferProvider createBufferProvider() {
                // TODO Make buffer size configurable
                return new ThreadLocalBufferProvider(64 * 1024);
            }
        });
    }

    @Override
    public void registerBuildTreeServices(ServiceRegistration registration) {
        registration.addProvider(new ServiceRegistrationProvider() {
            private static final String GRADLE_VERSION_KEY = "gradleVersion";

            @Provides
            LifecycleAwareBuildCacheControllerFactory createRootBuildCacheControllerRef() {
                return new LifecycleAwareBuildCacheControllerFactory();
            }

            @Provides
            OriginMetadataFactory createOriginMetadataFactory(
                BuildInvocationScopeId buildInvocationScopeId
            ) {
                return new OriginMetadataFactory(
                    buildInvocationScopeId.getId().asString(),
                    properties -> properties.setProperty(GRADLE_VERSION_KEY, GradleVersion.current().getVersion())
                );
            }
        });
    }

    @Override
    public void registerBuildServices(ServiceRegistration registration) {
        registration.addProvider(new ServiceRegistrationProvider() {

            @Provides
            BuildCacheConfigurationInternal createBuildCacheConfiguration(
                Instantiator instantiator,
                InstantiatorFactory instantiatorFactory,
                ServiceRegistry services,
                List<BuildCacheServiceRegistration> allBuildCacheServiceFactories
            ) {
                // We need to create an instantiator that has access to ObjectFactory
                Instantiator buildScopedInstantiator = instantiatorFactory.decorate(services);
                return instantiator.newInstance(DefaultBuildCacheConfiguration.class, buildScopedInstantiator, allBuildCacheServiceFactories);
            }

            @Provides
            BuildCacheServiceRegistration createDirectoryBuildCacheServiceRegistration() {
                return new DefaultBuildCacheServiceRegistration(DirectoryBuildCache.class, DirectoryBuildCacheServiceFactory.class);
            }

            @Provides
            TarPackerFileSystemSupport createPackerFileSystemSupport(Deleter deleter) {
                return new DefaultTarPackerFileSystemSupport(deleter);
            }

            @Provides
            BuildCacheEntryPacker createResultPacker(
                TarPackerFileSystemSupport fileSystemSupport,
                FileSystem fileSystem,
                StreamHasher fileHasher,
                StringInterner stringInterner,
                BufferProvider bufferProvider
            ) {
                return new GZipBuildCacheEntryPacker(
                    new TarBuildCacheEntryPacker(fileSystemSupport, new FilePermissionsAccessAdapter(fileSystem), fileHasher, stringInterner, bufferProvider));
            }

            @Provides
            LifecycleAwareBuildCacheController createBuildCacheController(
                BuildState build,
                LifecycleAwareBuildCacheControllerFactory rootControllerRef,
                BuildCacheControllerFactory buildCacheControllerFactory,
                InstantiatorFactory instantiatorFactory,
                ServiceRegistry services
            ) {
                InstanceGenerator injectingGenerator = instantiatorFactory.inject(services);
                if (build instanceof RootBuildState) {
                    return rootControllerRef.createForRootBuild(build.getIdentityPath(), buildCacheControllerFactory, injectingGenerator);
                } else {
                    return rootControllerRef.createForNonRootBuild(build.getIdentityPath(), buildCacheControllerFactory, injectingGenerator);
                }
            }

            @Provides
            BuildCacheControllerFactory createBuildCacheControllerFactory(
                StartParameterInternal startParameter,
                BuildOperationRunner buildOperationRunner,
                BuildOperationProgressEventEmitter buildOperationProgressEventEmitter,
                TemporaryFileProvider temporaryFileProvider,
                BuildCacheEntryPacker packer,
                OriginMetadataFactory originMetadataFactory,
                StringInterner stringInterner
            ) {
                return new DefaultBuildCacheControllerFactory(
                    startParameter,
                    buildOperationRunner,
                    buildOperationProgressEventEmitter,
                    originMetadataFactory,
                    stringInterner,
                    temporaryFileProvider,
                    packer
                );
            }
        });
    }

    private static final class FilePermissionsAccessAdapter implements FilePermissionAccess {

        private final FileSystem fileSystem;

        public FilePermissionsAccessAdapter(FileSystem fileSystem) {
            this.fileSystem = fileSystem;
        }

        @Override
        public int getUnixMode(File f) throws FileException {
            return fileSystem.getUnixMode(f);
        }

        @Override
        public void chmod(File file, int mode) throws FileException {
            fileSystem.chmod(file, mode);
        }
    }
}
