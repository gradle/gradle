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

import org.gradle.StartParameter;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.file.temp.TemporaryFileProvider;
import org.gradle.api.logging.configuration.ShowStacktrace;
import org.gradle.caching.configuration.internal.BuildCacheConfigurationInternal;
import org.gradle.caching.configuration.internal.BuildCacheServiceRegistration;
import org.gradle.caching.configuration.internal.DefaultBuildCacheConfiguration;
import org.gradle.caching.configuration.internal.DefaultBuildCacheServiceRegistration;
import org.gradle.caching.internal.controller.BuildCacheCommandFactory;
import org.gradle.caching.internal.controller.BuildCacheController;
import org.gradle.caching.internal.controller.RootBuildCacheControllerRef;
import org.gradle.caching.internal.controller.impl.DefaultBuildCacheCommandFactory;
import org.gradle.caching.internal.origin.OriginMetadataFactory;
import org.gradle.caching.internal.packaging.BuildCacheEntryPacker;
import org.gradle.caching.internal.packaging.impl.DefaultTarPackerFileSystemSupport;
import org.gradle.caching.internal.packaging.impl.FilePermissionAccess;
import org.gradle.caching.internal.packaging.impl.GZipBuildCacheEntryPacker;
import org.gradle.caching.internal.packaging.impl.TarBuildCacheEntryPacker;
import org.gradle.caching.internal.packaging.impl.TarPackerFileSystemSupport;
import org.gradle.caching.internal.services.BuildCacheControllerFactory;
import org.gradle.caching.local.DirectoryBuildCache;
import org.gradle.caching.local.internal.DirectoryBuildCacheFileStoreFactory;
import org.gradle.caching.local.internal.DirectoryBuildCacheServiceFactory;
import org.gradle.internal.SystemProperties;
import org.gradle.internal.file.Deleter;
import org.gradle.internal.file.FileException;
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
import org.gradle.util.Path;

import java.io.File;
import java.util.List;

/**
 * Build scoped services for build cache usage.
 */
public final class BuildCacheServices extends AbstractPluginServiceRegistry {

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
                StringInterner stringInterner
            ) {
                return new GZipBuildCacheEntryPacker(
                    new TarBuildCacheEntryPacker(fileSystemSupport, new FilePermissionsAccessAdapter(fileSystem), fileHasher, stringInterner));
            }

            OriginMetadataFactory createOriginMetadataFactory(
                BuildInvocationScopeId buildInvocationScopeId,
                GradleInternal gradleInternal,
                HostnameLookup hostnameLookup
            ) {
                File rootDir = gradleInternal.getRootProject().getRootDir();
                return new OriginMetadataFactory(
                    rootDir,
                    SystemProperties.getInstance().getUserName(),
                    OperatingSystem.current().getName(),
                    buildInvocationScopeId.getId().asString(),
                    properties -> properties.setProperty(GRADLE_VERSION_KEY, GradleVersion.current().getVersion()),
                    hostnameLookup::getHostname
                );
            }

            BuildCacheCommandFactory createBuildCacheCommandFactory(
                BuildCacheEntryPacker packer,
                OriginMetadataFactory originMetadataFactory,
                FileSystemAccess fileSystemAccess,
                StringInterner stringInterner
            ) {
                return new DefaultBuildCacheCommandFactory(packer, originMetadataFactory, fileSystemAccess, stringInterner);
            }

            BuildCacheController createBuildCacheController(
                ServiceRegistry serviceRegistry,
                BuildCacheConfigurationInternal buildCacheConfiguration,
                BuildOperationExecutor buildOperationExecutor,
                InstantiatorFactory instantiatorFactory,
                GradleInternal gradle,
                RootBuildCacheControllerRef rootControllerRef,
                TemporaryFileProvider temporaryFileProvider
            ) {
                if (isRoot(gradle) || isGradleBuildTaskRoot(rootControllerRef)) {
                    return doCreateBuildCacheController(serviceRegistry, buildCacheConfiguration, buildOperationExecutor, instantiatorFactory, gradle, temporaryFileProvider);
                } else {
                    // must be an included build or buildSrc
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

            private BuildCacheController doCreateBuildCacheController(ServiceRegistry serviceRegistry, BuildCacheConfigurationInternal buildCacheConfiguration, BuildOperationExecutor buildOperationExecutor, InstantiatorFactory instantiatorFactory, GradleInternal gradle, TemporaryFileProvider temporaryFileProvider) {
                StartParameter startParameter = gradle.getStartParameter();
                Path buildIdentityPath = gradle.getIdentityPath();
                BuildCacheControllerFactory.BuildCacheMode buildCacheMode = startParameter.isBuildCacheEnabled() ? BuildCacheControllerFactory.BuildCacheMode.ENABLED : BuildCacheControllerFactory.BuildCacheMode.DISABLED;
                BuildCacheControllerFactory.RemoteAccessMode remoteAccessMode = startParameter.isOffline() ? BuildCacheControllerFactory.RemoteAccessMode.OFFLINE : BuildCacheControllerFactory.RemoteAccessMode.ONLINE;
                boolean logStackTraces = startParameter.getShowStacktrace() != ShowStacktrace.INTERNAL_EXCEPTIONS;
                boolean emitDebugLogging = startParameter.isBuildCacheDebugLogging();

                return BuildCacheControllerFactory.create(
                    buildOperationExecutor,
                    buildIdentityPath,
                    temporaryFileProvider,
                    buildCacheConfiguration,
                    buildCacheMode,
                    remoteAccessMode,
                    logStackTraces,
                    emitDebugLogging,
                    instantiatorFactory.inject(serviceRegistry)
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
