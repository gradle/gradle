/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.caching.example;

import com.google.common.collect.Interner;
import com.google.common.collect.Interners;
import com.google.inject.AbstractModule;
import com.google.inject.Exposed;
import com.google.inject.PrivateModule;
import com.google.inject.Provides;
import org.apache.commons.io.FileUtils;
import org.gradle.api.internal.file.temp.DefaultTemporaryFileProvider;
import org.gradle.api.internal.file.temp.TemporaryFileProvider;
import org.gradle.cache.CacheCleanupStrategy;
import org.gradle.cache.CacheCleanupStrategyFactory;
import org.gradle.cache.FileLockManager;
import org.gradle.cache.PersistentCache;
import org.gradle.cache.internal.CacheFactory;
import org.gradle.cache.internal.DefaultCacheBuilder;
import org.gradle.cache.internal.DefaultCacheCleanupStrategyFactory;
import org.gradle.cache.internal.DefaultCacheFactory;
import org.gradle.cache.internal.DefaultFileLockManager;
import org.gradle.cache.internal.LeastRecentlyUsedCacheCleanup;
import org.gradle.cache.internal.ProcessMetaDataProvider;
import org.gradle.cache.internal.SingleDepthFilesFinder;
import org.gradle.cache.internal.locklistener.DefaultFileLockContentionHandler;
import org.gradle.cache.internal.locklistener.FileLockContentionHandler;
import org.gradle.cache.internal.locklistener.InetAddressProvider;
import org.gradle.caching.internal.controller.BuildCacheController;
import org.gradle.caching.internal.controller.DefaultBuildCacheController;
import org.gradle.caching.internal.controller.service.BuildCacheServicesConfiguration;
import org.gradle.caching.internal.origin.OriginMetadataFactory;
import org.gradle.caching.internal.packaging.BuildCacheEntryPacker;
import org.gradle.caching.internal.packaging.impl.FilePermissionAccess;
import org.gradle.caching.internal.packaging.impl.GZipBuildCacheEntryPacker;
import org.gradle.caching.internal.packaging.impl.TarBuildCacheEntryPacker;
import org.gradle.caching.internal.packaging.impl.TarPackerFileSystemSupport;
import org.gradle.caching.local.internal.DirectoryBuildCacheService;
import org.gradle.caching.local.internal.LocalBuildCacheService;
import org.gradle.caching.local.internal.TemporaryFileFactory;
import org.gradle.internal.concurrent.DefaultExecutorFactory;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.file.FileAccessTimeJournal;
import org.gradle.internal.file.FileAccessTracker;
import org.gradle.internal.file.FileMetadataAccessor;
import org.gradle.internal.file.FilePermissionHandler;
import org.gradle.internal.file.StatStatistics;
import org.gradle.internal.file.TreeType;
import org.gradle.internal.file.impl.SingleDepthFileAccessTracker;
import org.gradle.internal.file.nio.ModificationTimeFileAccessTimeJournal;
import org.gradle.internal.file.nio.NioFileMetadataAccessor;
import org.gradle.internal.file.nio.NioFilePermissions;
import org.gradle.internal.hash.DefaultFileHasher;
import org.gradle.internal.hash.DefaultStreamHasher;
import org.gradle.internal.hash.FileHasher;
import org.gradle.internal.hash.StreamHasher;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationIdFactory;
import org.gradle.internal.operations.BuildOperationListener;
import org.gradle.internal.operations.BuildOperationProgressEventEmitter;
import org.gradle.internal.operations.BuildOperationRunner;
import org.gradle.internal.operations.BuildOperationState;
import org.gradle.internal.operations.BuildOperationTimeSupplier;
import org.gradle.internal.operations.CurrentBuildOperationRef;
import org.gradle.internal.operations.DefaultBuildOperationIdFactory;
import org.gradle.internal.operations.DefaultBuildOperationProgressEventEmitter;
import org.gradle.internal.operations.DefaultBuildOperationRunner;
import org.gradle.internal.operations.OperationFinishEvent;
import org.gradle.internal.operations.OperationIdentifier;
import org.gradle.internal.operations.OperationProgressEvent;
import org.gradle.internal.operations.OperationStartEvent;
import org.gradle.internal.snapshot.SnapshotHierarchy;
import org.gradle.internal.snapshot.impl.DirectorySnapshotterStatistics;
import org.gradle.internal.time.TimestampSuppliers;
import org.gradle.internal.vfs.FileSystemAccess;
import org.gradle.internal.vfs.VirtualFileSystem;
import org.gradle.internal.vfs.impl.AbstractVirtualFileSystem;
import org.gradle.internal.vfs.impl.DefaultFileSystemAccess;
import org.gradle.internal.vfs.impl.DefaultSnapshotHierarchy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.function.Supplier;

import static org.gradle.cache.FileLockManager.LockMode.OnDemand;
import static org.gradle.internal.snapshot.CaseSensitivity.CASE_SENSITIVE;

@SuppressWarnings("CloseableProvides")
class BuildCacheClientModule extends AbstractModule {
    private static final Logger LOGGER = LoggerFactory.getLogger(BuildCacheClientModule.class);

    private final String buildInvocationId;

    public BuildCacheClientModule(String buildInvocationId) {
        this.buildInvocationId = buildInvocationId;
    }

    @Override
    protected void configure() {
        install(new LocalBuildCacheModule());
    }

    @Provides
    BuildCacheController createBuildCacheController(
        BuildCacheServicesConfiguration buildCacheServicesConfig,
        BuildOperationRunner buildOperationRunner,
        BuildOperationProgressEventEmitter eventEmitter,
        TemporaryFileFactory temporaryFileProvider,
        BuildCacheEntryPacker buildCacheEntryPacker,
        OriginMetadataFactory originMetadataFactory,
        Interner<String> stringInterner
    ) {
        return new DefaultBuildCacheController(
            buildCacheServicesConfig,
            buildOperationRunner,
            eventEmitter,
            temporaryFileProvider,
            false,
            true,
            buildCacheEntryPacker,
            originMetadataFactory,
            stringInterner
        );
    }

    @Provides
    FileAccessTimeJournal createFileAccessTimeJournal() {
        return new ModificationTimeFileAccessTimeJournal();
    }

    @Provides
    CacheCleanupStrategyFactory createCacheCleanupStrategyFactory(BuildOperationRunner buildOperationRunner) {
        return new DefaultCacheCleanupStrategyFactory(buildOperationRunner);
    }

    @Provides
    ExecutorFactory createExecutorFactory() {
        return new DefaultExecutorFactory();
    }

    @Provides
    FileLockContentionHandler createFileLockContentionHandler(ExecutorFactory executorFactory) {
        return new DefaultFileLockContentionHandler(executorFactory, new InetAddressProvider() {
            @Override
            public InetAddress getWildcardBindingAddress() {
                return new InetSocketAddress(0).getAddress();
            }

            @Override
            public Iterable<InetAddress> getCommunicationAddresses() {
                try {
                    return Collections.singleton(InetAddress.getByName(null));
                } catch (UnknownHostException e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }

    @Provides
    FileLockManager createFileLockManager(FileLockContentionHandler fileLockContentionHandler) {
        ProcessMetaDataProvider metaDataProvider = new ProcessMetaDataProvider() {
            @Override
            public String getProcessIdentifier() {
                // TODO Inject actual PID
                return "PID";
            }

            @Override
            public String getProcessDisplayName() {
                return "build-cache-example-client";
            }
        };
        return new DefaultFileLockManager(metaDataProvider, fileLockContentionHandler);
    }

    @Provides
    CacheFactory createCacheFactory(FileLockManager fileLockManager, ExecutorFactory executorFactory) {
        return new DefaultCacheFactory(fileLockManager, executorFactory.create("Cache workers"));
    }

    private static class LocalBuildCacheModule extends PrivateModule {
        @Override
        protected void configure() {
        }

        @Provides
        @Exposed
        LocalBuildCacheService createLocalBuildCacheService(FileAccessTracker fileAccessTracker, PersistentCache persistentCache) {
            return new DirectoryBuildCacheService(
                persistentCache,
                fileAccessTracker,
                ".failed"
            );
        }

        @Provides
        PersistentCache createPersistentCache(File buildCacheDir, CacheCleanupStrategy cacheCleanupStrategy, CacheFactory cacheFactory) {
            return new DefaultCacheBuilder(cacheFactory, buildCacheDir)
                .withCleanupStrategy(cacheCleanupStrategy)
                .withDisplayName("Build cache")
                .withInitialLockMode(OnDemand)
                .open();
        }

        @Provides
        FileAccessTracker createFileAccessTracker(File buildCacheDir, FileAccessTimeJournal fileAccessTimeJournal) {
            return new SingleDepthFileAccessTracker(fileAccessTimeJournal, buildCacheDir, 1);
        }

        @Provides
        File createBuildCacheDir() throws IOException {
            File target = Files.createTempDirectory("build-cache").toFile();
            FileUtils.forceMkdir(target);
            return target;
        }
    }

    @Provides
    CacheCleanupStrategy createCacheCleanupStrategy(FileAccessTimeJournal fileAccessTimeJournal, CacheCleanupStrategyFactory factory) {
        SingleDepthFilesFinder filesFinder = new SingleDepthFilesFinder(1);
        Supplier<Long> removeUnusedEntriesOlderThan = TimestampSuppliers.daysAgo(1);
        LeastRecentlyUsedCacheCleanup cleanupAction = new LeastRecentlyUsedCacheCleanup(filesFinder, fileAccessTimeJournal, removeUnusedEntriesOlderThan);
        return factory.daily(cleanupAction);
    }

    @Provides
    BuildCacheServicesConfiguration createBuildCacheServicesConfig(
        LocalBuildCacheService localBuildCacheService
    ) {
        return new BuildCacheServicesConfiguration(
            ":",
            localBuildCacheService,
            true,
            // TODO Add remote cache capability
            null,
            false
        );
    }

    @Provides
    BuildOperationRunner createBuildOperationRunner(
        CurrentBuildOperationRef currentBuildOperationRef,
        BuildOperationTimeSupplier timeSupplier,
        BuildOperationIdFactory buildOperationIdFactory,
        DefaultBuildOperationRunner.BuildOperationExecutionListenerFactory buildOperationExecutionListenerFactory
    ) {
        return new DefaultBuildOperationRunner(
            currentBuildOperationRef,
            timeSupplier,
            buildOperationIdFactory,
            buildOperationExecutionListenerFactory
        );
    }

    @Provides
    BuildOperationIdFactory createBuildOperationIdFactory() {
        return new DefaultBuildOperationIdFactory();
    }

    @Provides
    DefaultBuildOperationRunner.BuildOperationExecutionListenerFactory createBuildOperationExecutionListenerFactory() {
        return () -> new DefaultBuildOperationRunner.BuildOperationExecutionListener() {
            @Override
            public void start(BuildOperationDescriptor descriptor, BuildOperationState operationState) {
                LOGGER.info("Start: {}", descriptor.getDisplayName());
            }

            @Override
            public void progress(BuildOperationDescriptor descriptor, String status) {
                LOGGER.info("Progress: {}", descriptor.getDisplayName());
            }

            @Override
            public void progress(BuildOperationDescriptor descriptor, long progress, long total, String units, String status) {
                LOGGER.info("Progress: {} ({} / {} {} - {})", descriptor.getDisplayName(), progress, total, units, status);
            }

            @Override
            public void stop(BuildOperationDescriptor descriptor, BuildOperationState operationState, @Nullable BuildOperationState parent, DefaultBuildOperationRunner.ReadableBuildOperationContext context) {
                LOGGER.info("Stop: {}", descriptor.getDisplayName());
            }

            @Override
            public void close(BuildOperationDescriptor descriptor, BuildOperationState operationState) {
                LOGGER.info("Close: {}", descriptor.getDisplayName());
            }
        };
    }

    @Provides
    BuildOperationProgressEventEmitter createBuildOperationProgressEventEmitter(
        BuildOperationTimeSupplier timeSupplier,
        CurrentBuildOperationRef currentBuildOperationRef,
        BuildOperationListener buildOperationListener
    ) {
        return new DefaultBuildOperationProgressEventEmitter(
            timeSupplier,
            currentBuildOperationRef,
            buildOperationListener
        );
    }

    @Provides
    CurrentBuildOperationRef createCurrentBuildOperationRef() {
        return new CurrentBuildOperationRef();
    }

    // TODO What's the difference between BuildOperationExecutionListener
    //      (which seems to be called) and BuildOperationListener (which
    //      seems not to be called)?
    @Provides
    BuildOperationListener createBuildOperationListener() {
        return new BuildOperationListener() {
            @Override
            public void started(BuildOperationDescriptor buildOperation, OperationStartEvent startEvent) {
                LOGGER.info("Started: {}", buildOperation.getDisplayName());
            }

            @Override
            public void progress(OperationIdentifier operationIdentifier, OperationProgressEvent progressEvent) {
                LOGGER.info("Progress: {}", operationIdentifier);
            }

            @Override
            public void finished(BuildOperationDescriptor buildOperation, OperationFinishEvent finishEvent) {
                LOGGER.info("Finished: {}", buildOperation.getDisplayName());
            }
        };
    }

    @Provides
    BuildOperationTimeSupplier createTimeSupplier() {
        return System::currentTimeMillis;
    }

    @Provides
    BuildCacheEntryPacker createBuildCacheEntryPacker(
        TarPackerFileSystemSupport fileSystemSupport,
        StreamHasher streamHasher,
        Interner<String> stringInterner
    ) {
        FilePermissionHandler permissionHandler = NioFilePermissions.createFilePermissionHandler();
        FilePermissionAccess filePermissionAccess = new FilePermissionAccess() {
            @Override
            public int getUnixMode(File f) {
                try {
                    return permissionHandler.getUnixMode(f);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }

            @Override
            public void chmod(File file, int mode) {
                try {
                    permissionHandler.chmod(file, mode);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        };
        return new GZipBuildCacheEntryPacker(
            new TarBuildCacheEntryPacker(
                fileSystemSupport,
                filePermissionAccess,
                streamHasher,
                stringInterner,
                () -> new byte[4096]
            )
        );
    }

    @Provides
    TarPackerFileSystemSupport createTarPackerFileSystemSupport() {
        return new TarPackerFileSystemSupport() {
            @Override
            public void ensureFileIsMissing(File entry) throws IOException {
                FileUtils.forceDelete(entry);
            }

            @Override
            public void ensureDirectoryForTree(TreeType type, File root) throws IOException {
                switch (type) {
                    case DIRECTORY:
                        FileUtils.forceMkdir(root.getParentFile());
                        FileUtils.cleanDirectory(root);
                        break;
                    case FILE:
                        FileUtils.forceMkdir(root.getParentFile());
                        if (root.exists()) {
                            FileUtils.forceDelete(root);
                        }
                        break;
                    default:
                        throw new AssertionError();
                }
            }
        };
    }

    @Provides
    OriginMetadataFactory createOriginMetadataFactory() {
        return new OriginMetadataFactory(
            buildInvocationId,
            properties -> properties.put("client", "example-client")
        );
    }

    @Provides
    StreamHasher createStreamHasher() {
        return new DefaultStreamHasher();
    }

    @Provides
    FileHasher createFileHasher(StreamHasher streamHasher) {
        return new DefaultFileHasher(streamHasher);
    }

    @Provides
    TemporaryFileProvider createTemporaryFileProvider() throws IOException {
        File tempDir = Files.createTempDirectory("cache-client-example-temp").toFile();
        return new DefaultTemporaryFileProvider(() -> tempDir);
    }

    @Provides
    TemporaryFileFactory createTemporaryFileFactory(TemporaryFileProvider temporaryFileProvider) {
        return temporaryFileProvider::createTemporaryFile;
    }

    @Provides
    StatStatistics.Collector createStatStatisticsCollector() {
        return new StatStatistics.Collector();
    }

    @Provides
    VirtualFileSystem createVirtualFileSystem() {
        // TODO Figure out case sensitivity properly
        SnapshotHierarchy root = DefaultSnapshotHierarchy.empty(CASE_SENSITIVE);
        return new CustomVirtualFileSystem(root);
    }

    // TODO Make AbstractVirtualFileSystem into DefaultVirtualFileSystem, and wrap it with the
    //      watching/non-watching implementations so DefaultVirtualFileSystem can be reused here
    private static class CustomVirtualFileSystem extends AbstractVirtualFileSystem {
        protected CustomVirtualFileSystem(SnapshotHierarchy root) {
            super(root);
        }

        @Override
        protected SnapshotHierarchy updateNotifyingListeners(UpdateFunction updateFunction) {
            return updateFunction.update(SnapshotHierarchy.NodeDiffListener.NOOP);
        }
    }

    @Provides
    Interner<String> createStringInterner() {
        return Interners.newWeakInterner();
    }

    @Provides
    DirectorySnapshotterStatistics.Collector creaetDirectorySnapshotterStatisticsCollector() {
        return new DirectorySnapshotterStatistics.Collector();
    }

    @Provides
    FileMetadataAccessor createFileMetadataAccessor() {
        return new NioFileMetadataAccessor();
    }

    @Provides
    FileSystemAccess createFileSystemAccess(
        FileHasher fileHasher,
        Interner<String> stringInterner,
        FileMetadataAccessor stat,
        VirtualFileSystem virtualFileSystem,
        DirectorySnapshotterStatistics.Collector statisticsCollector
    ) {
        return new DefaultFileSystemAccess(
            fileHasher,
            stringInterner,
            stat,
            virtualFileSystem,
            locations -> locations.forEach(System.out::println),
            statisticsCollector
        );
    }
}
