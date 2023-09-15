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

import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.StartParameterInternal;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.file.temp.TemporaryFileProvider;
import org.gradle.caching.configuration.internal.BuildCacheConfigurationInternal;
import org.gradle.caching.configuration.internal.BuildCacheServiceRegistration;
import org.gradle.caching.configuration.internal.DefaultBuildCacheConfiguration;
import org.gradle.caching.configuration.internal.DefaultBuildCacheServiceRegistration;
import org.gradle.caching.internal.controller.BuildCacheController;
import org.gradle.caching.internal.controller.RootBuildCacheControllerRef;
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
import org.gradle.caching.local.internal.DirectoryBuildCacheFileStoreFactory;
import org.gradle.caching.local.internal.DirectoryBuildCacheServiceFactory;
import org.gradle.internal.SystemProperties;
import org.gradle.internal.file.BufferProvider;
import org.gradle.internal.file.Deleter;
import org.gradle.internal.file.FileException;
import org.gradle.internal.file.ThreadLocalBufferProvider;
import org.gradle.internal.hash.ChecksumService;
import org.gradle.internal.hash.StreamHasher;
import org.gradle.internal.instantiation.InstantiatorFactory;
import org.gradle.internal.nativeintegration.filesystem.FileSystem;
import org.gradle.internal.nativeintegration.network.HostnameLookup;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.resource.local.DefaultPathKeyFileStore;
import org.gradle.internal.resource.local.PathKeyFileStore;
import org.gradle.internal.scopeids.id.BuildInvocationScopeId;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.service.scopes.AbstractPluginServiceRegistry;
import org.gradle.internal.vfs.FileSystemAccess;
import org.gradle.util.GradleVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;

/**
 * Build scoped services for build cache usage.
 */
public final class BuildCacheServices extends AbstractPluginServiceRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(BuildCacheServices.class);

    @Override
    public void registerGlobalServices(ServiceRegistration registration) {
        registration.addProvider(new Object() {
            BufferProvider createBufferProvider() {
                // TODO Make buffer size configurable
                return new ThreadLocalBufferProvider(64 * 1024);
            }
        });
    }

    @Override
    public void registerBuildTreeServices(ServiceRegistration registration) {
        registration.addProvider(new Object() {
            RootBuildCacheControllerRef createRootBuildCacheControllerRef() {
                return new RootBuildCacheControllerRef();
            }
        });
    }

    @Override
    public void registerBuildServices(ServiceRegistration registration) {
        registration.addProvider(new Object() {

            BuildCacheConfigurationInternal createBuildCacheConfiguration(
                Instantiator instantiator,
                List<BuildCacheServiceRegistration> allBuildCacheServiceFactories
            ) {
                return instantiator.newInstance(DefaultBuildCacheConfiguration.class, instantiator, allBuildCacheServiceFactories);
            }

            DirectoryBuildCacheFileStoreFactory createDirectoryBuildCacheFileStoreFactory(ChecksumService checksumService) {
                return new DirectoryBuildCacheFileStoreFactory() {
                    @Override
                    public PathKeyFileStore createFileStore(File baseDir) {
                        return new DefaultPathKeyFileStore(checksumService, baseDir);
                    }
                };
            }

            BuildCacheServiceRegistration createDirectoryBuildCacheServiceRegistration() {
                return new DefaultBuildCacheServiceRegistration(DirectoryBuildCache.class, DirectoryBuildCacheServiceFactory.class);
            }
        });
    }

    @Override
    public void registerGradleServices(ServiceRegistration registration) {
        // Not build scoped because of dependency on GradleInternal for build path
        registration.addProvider(new Object() {
            private static final String GRADLE_VERSION_KEY = "gradleVersion";

            TarPackerFileSystemSupport createPackerFileSystemSupport(Deleter deleter) {
                return new DefaultTarPackerFileSystemSupport(deleter);
            }

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

            OriginMetadataFactory createOriginMetadataFactory(
                BuildInvocationScopeId buildInvocationScopeId,
                GradleInternal gradleInternal,
                HostnameLookup hostnameLookup
            ) {
                return new OriginMetadataFactory(
                    SystemProperties.getInstance().getUserName(),
                    OperatingSystem.current().getName(),
                    buildInvocationScopeId.getId().asString(),
                    properties -> properties.setProperty(GRADLE_VERSION_KEY, GradleVersion.current().getVersion()),
                    hostnameLookup::getHostname
                );
            }

            BuildCacheController createBuildCacheController(
                ServiceRegistry serviceRegistry,
                InstantiatorFactory instantiatorFactory,
                GradleInternal gradle,
                BuildCacheConfigurationInternal buildCacheConfiguration,
                RootBuildCacheControllerRef rootControllerRef,
                BuildCacheControllerFactory buildCacheControllerFactory
            ) {
                if (isRoot(gradle) || isGradleBuildTaskRoot(rootControllerRef)) {
                    LOGGER.warn("[MASTER] Creating build cache controller");
                    return buildCacheControllerFactory.createController(gradle.getIdentityPath(), buildCacheConfiguration, instantiatorFactory.inject(serviceRegistry));
                } else {
                    // must be an included build or buildSrc
                    LOGGER.warn("[MASTER] Reusing root build cache controller in non-root build");
                    return rootControllerRef.getForNonRootBuild();
                }
            }

            private boolean isGradleBuildTaskRoot(RootBuildCacheControllerRef rootControllerRef) {
                // GradleBuild tasks operate with their own build session and tree scope.
                // Therefore, they have their own RootBuildCacheControllerRef.
                // This prevents them from reusing the build cache configuration defined by the root.
                // There is no way to detect that a Gradle instance represents a GradleBuild invocation.
                // If there were, that would be a better heuristic than this.
                return !rootControllerRef.isSet();
            }

            private boolean isRoot(GradleInternal gradle) {
                return gradle.isRootBuild();
            }

            BuildCacheControllerFactory createBuildCacheControllerFactory(
                StartParameterInternal startParameter,
                BuildOperationExecutor buildOperationExecutor,
                TemporaryFileProvider temporaryFileProvider,
                FileSystemAccess fileSystemAccess,
                BuildCacheEntryPacker packer,
                OriginMetadataFactory originMetadataFactory,
                StringInterner stringInterner
            ) {
                return new DefaultBuildCacheControllerFactory(
                    startParameter,
                    buildOperationExecutor,
                    originMetadataFactory,
                    fileSystemAccess,
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
