package org.gradle.caching.example;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Interner;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Provides;
import org.apache.commons.io.FileUtils;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.file.temp.DefaultTemporaryFileProvider;
import org.gradle.api.internal.file.temp.TemporaryFileProvider;
import org.gradle.caching.BuildCacheKey;
import org.gradle.caching.internal.BuildCacheKeyInternal;
import org.gradle.caching.internal.CacheableEntity;
import org.gradle.caching.internal.controller.BuildCacheController;
import org.gradle.caching.internal.controller.DefaultBuildCacheController;
import org.gradle.caching.internal.controller.service.BuildCacheLoadResult;
import org.gradle.caching.internal.controller.service.BuildCacheServicesConfiguration;
import org.gradle.caching.internal.origin.OriginMetadataFactory;
import org.gradle.caching.internal.packaging.BuildCacheEntryPacker;
import org.gradle.caching.internal.packaging.impl.FilePermissionAccess;
import org.gradle.caching.internal.packaging.impl.GZipBuildCacheEntryPacker;
import org.gradle.caching.internal.packaging.impl.TarBuildCacheEntryPacker;
import org.gradle.caching.internal.packaging.impl.TarPackerFileSystemSupport;
import org.gradle.caching.local.internal.DirectoryBuildCache;
import org.gradle.caching.local.internal.LocalBuildCacheService;
import org.gradle.caching.local.internal.TemporaryFileFactory;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.file.FileException;
import org.gradle.internal.file.FileMetadata;
import org.gradle.internal.file.StatStatistics;
import org.gradle.internal.file.TreeType;
import org.gradle.internal.file.impl.DefaultFileMetadata;
import org.gradle.internal.hash.DefaultFileHasher;
import org.gradle.internal.hash.DefaultStreamHasher;
import org.gradle.internal.hash.FileHasher;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.hash.StreamHasher;
import org.gradle.internal.nativeintegration.filesystem.FileMetadataAccessor;
import org.gradle.internal.nativeintegration.filesystem.FileSystem;
import org.gradle.internal.nativeintegration.filesystem.Symlink;
import org.gradle.internal.nativeintegration.filesystem.jdk7.Jdk7Symlink;
import org.gradle.internal.nativeintegration.filesystem.jdk7.PosixJdk7FilePermissionHandler;
import org.gradle.internal.nativeintegration.filesystem.jdk7.WindowsJdk7Symlink;
import org.gradle.internal.nativeintegration.filesystem.services.EmptyChmod;
import org.gradle.internal.nativeintegration.filesystem.services.FallbackStat;
import org.gradle.internal.nativeintegration.filesystem.services.GenericFileSystem;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationIdFactory;
import org.gradle.internal.operations.BuildOperationListener;
import org.gradle.internal.operations.BuildOperationProgressEventEmitter;
import org.gradle.internal.operations.BuildOperationRunner;
import org.gradle.internal.operations.CurrentBuildOperationRef;
import org.gradle.internal.operations.DefaultBuildOperationIdFactory;
import org.gradle.internal.operations.DefaultBuildOperationProgressEventEmitter;
import org.gradle.internal.operations.DefaultBuildOperationRunner;
import org.gradle.internal.operations.DefaultBuildOperationRunner.BuildOperationExecutionListener;
import org.gradle.internal.operations.DefaultBuildOperationRunner.BuildOperationExecutionListenerFactory;
import org.gradle.internal.operations.OperationFinishEvent;
import org.gradle.internal.operations.OperationIdentifier;
import org.gradle.internal.operations.OperationProgressEvent;
import org.gradle.internal.operations.OperationStartEvent;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.internal.snapshot.CaseSensitivity;
import org.gradle.internal.snapshot.FileSystemLocationSnapshot;
import org.gradle.internal.snapshot.FileSystemSnapshot;
import org.gradle.internal.snapshot.SnapshotHierarchy;
import org.gradle.internal.snapshot.impl.DirectorySnapshotterStatistics;
import org.gradle.internal.time.Clock;
import org.gradle.internal.vfs.FileSystemAccess;
import org.gradle.internal.vfs.VirtualFileSystem;
import org.gradle.internal.vfs.impl.AbstractVirtualFileSystem;
import org.gradle.internal.vfs.impl.DefaultFileSystemAccess;
import org.gradle.internal.vfs.impl.DefaultSnapshotHierarchy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.function.Consumer;

import static org.gradle.internal.snapshot.CaseSensitivity.CASE_INSENSITIVE;
import static org.gradle.internal.snapshot.CaseSensitivity.CASE_SENSITIVE;

public class ExampleBuildCacheClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(DirectoryBuildCache.class);

    private final BuildCacheController buildCacheController;
    private final FileSystemAccess fileSystemAccess;

    @Inject
    public ExampleBuildCacheClient(BuildCacheController buildCacheController, FileSystemAccess fileSystemAccess) {
        this.buildCacheController = buildCacheController;
        this.fileSystemAccess = fileSystemAccess;
    }

    private void useBuildCache() throws IOException {
        BuildCacheKey cacheKey = new SimpleBuildCacheKey(HashCode.fromString("b9800f9130db9efa58f6ec8c744f1cc7"));

        Path originalOutputDirectory = Files.createTempDirectory("cache-entity-original");
        Path originalOutputTxt = originalOutputDirectory.resolve("output.txt");
        Files.write(originalOutputTxt, Collections.singleton("contents"));

        // TODO Should we switch to using Path instead of File?
        CacheableEntity originalEntity = new ExampleEntity("test-entity", originalOutputDirectory.toFile());

        FileSystemLocationSnapshot outputDirectorySnapshot = fileSystemAccess.read(originalOutputDirectory.toAbsolutePath().toString());
        Map<String, FileSystemSnapshot> outputSnapshots = ImmutableMap.of("output", outputDirectorySnapshot);
        buildCacheController.store(cacheKey, originalEntity, outputSnapshots, Duration.ofSeconds(10));

        Path loadedFromCacheDirectory = Files.createTempDirectory("cache-entity-loaded");
        CacheableEntity loadedEntity = new ExampleEntity("test-entity", loadedFromCacheDirectory.toFile());

        @SuppressWarnings("unused")
        BuildCacheLoadResult loadResult = buildCacheController.load(cacheKey, loadedEntity)
            .orElseThrow(() -> new RuntimeException("Couldn't load from cache"));

        Files.walkFileTree(loadedFromCacheDirectory, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                System.out.println(" - " + file.toAbsolutePath());
                return FileVisitResult.CONTINUE;
            }
        });
    }

    public static void main(String[] args) throws IOException {
        LOGGER.info("Hello world!");
        Guice.createInjector(new ApplicationModule("build-1"))
            .getInstance(ExampleBuildCacheClient.class)
            .useBuildCache();
    }

    // TODO Add a SimpleBuildCacheKey to the library
    private static class SimpleBuildCacheKey implements BuildCacheKeyInternal {
        private final HashCode hashCode;

        public SimpleBuildCacheKey(HashCode hashCode) {
            this.hashCode = hashCode;
        }

        @Override
        public HashCode getHashCodeInternal() {
            return hashCode;
        }

        @Override
        public String getHashCode() {
            return hashCode.toString();
        }

        // TODO Provide default implementation
        // TODO Deprecate and move this to BuildCacheKeyInternal
        @Override
        public byte[] toByteArray() {
            return hashCode.toByteArray();
        }

        // TODO Provide default implementation
        @Override
        @Deprecated
        public String getDisplayName() {
            return getHashCode();
        }
    }

    private static class ExampleEntity implements CacheableEntity {
        private final String identity;
        private final File outputDirectory;

        public ExampleEntity(String identity, File outputDirectory) {
            this.identity = identity;
            this.outputDirectory = outputDirectory;
        }

        @Override
        public String getIdentity() {
            return identity;
        }

        @Override
        public Class<?> getType() {
            return getClass();
        }

        @Override
        public String getDisplayName() {
            return identity;
        }

        @Override
        public void visitOutputTrees(CacheableTreeVisitor visitor) {
            visitor.visitOutputTree("output", TreeType.DIRECTORY, outputDirectory);
        }
    }

    // TODO Supply a simple implementation for this as well? Or make the abstract thing non-abstract?
    static class CustomVirtualFileSystem extends AbstractVirtualFileSystem {
        protected CustomVirtualFileSystem(SnapshotHierarchy root) {
            super(root);
        }

        @Override
        protected SnapshotHierarchy updateNotifyingListeners(UpdateFunction updateFunction) {
            return updateFunction.update(SnapshotHierarchy.NodeDiffListener.NOOP);
        }
    }

    @SuppressWarnings("MethodMayBeStatic")
    static class ApplicationModule extends AbstractModule {
        private final String buildInvocationId;

        public ApplicationModule(String buildInvocationId) {
            this.buildInvocationId = buildInvocationId;
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
        LocalBuildCacheService createLocalBuildCacheService() {
            return new LocalBuildCacheService() {
                @Override
                public void loadLocally(BuildCacheKey key, Consumer<? super File> reader) {

                }

                @Override
                public void storeLocally(BuildCacheKey key, File file) {

                }

                @Override
                public void close() {

                }

                @Override
                public void withTempFile(HashCode key, Consumer<? super File> action) {

                }
            };
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
            DefaultBuildOperationRunner.TimeSupplier timeSupplier,
            BuildOperationIdFactory buildOperationIdFactory,
            BuildOperationExecutionListenerFactory buildOperationExecutionListenerFactory
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
        BuildOperationExecutionListenerFactory createBuildOperationExecutionListenerFactory() {
            return () -> BuildOperationExecutionListener.NO_OP;
        }

        @Provides
        BuildOperationProgressEventEmitter createBuildOperationProgressEventEmitter(
            Clock clock,
            CurrentBuildOperationRef currentBuildOperationRef,
            BuildOperationListener buildOperationListener
        ) {
            return new DefaultBuildOperationProgressEventEmitter(
                clock,
                currentBuildOperationRef,
                buildOperationListener
            );
        }

        @Provides
        CurrentBuildOperationRef createCurrentBuildOperationRef() {
            return new CurrentBuildOperationRef();
        }

        @Provides
        BuildOperationListener createBuildOperationListener() {
            return new BuildOperationListener() {
                @Override
                public void started(BuildOperationDescriptor buildOperation, OperationStartEvent startEvent) {

                }

                @Override
                public void progress(OperationIdentifier operationIdentifier, OperationProgressEvent progressEvent) {

                }

                @Override
                public void finished(BuildOperationDescriptor buildOperation, OperationFinishEvent finishEvent) {

                }
            };
        }

        @Provides
        Clock createClock() {
            // TODO Better clock?
            return System::currentTimeMillis;
        }

        @Provides
        DefaultBuildOperationRunner.TimeSupplier createTimeSupplier(Clock clock) {
            return clock::getCurrentTime;
        }

        @Provides
        BuildCacheEntryPacker createBuildCacheEntryPacker(
            TarPackerFileSystemSupport fileSystemSupport,
            FilePermissionAccess filePermissionAccess,
            StreamHasher streamHasher,
            Interner<String> stringInterner
        ) {
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
        FilePermissionAccess createFilePermissionAccess(FileSystem fileSystem) {
            return new FilePermissionAccess() {
                @Override
                public int getUnixMode(File f) throws FileException {
                    return fileSystem.getUnixMode(f);
                }

                @Override
                public void chmod(File file, int mode) throws FileException {
                    fileSystem.chmod(file, mode);
                }
            };
        }

        @Provides
        OriginMetadataFactory createOriginMetadataFactory() {
            return new OriginMetadataFactory(
                System.getProperty("user.name"),
                System.getProperty("os.name"),
                buildInvocationId,
                properties -> properties.put("client", "example-client"),
                // TODO Properly resolve the host name
                () -> "localhost"
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
        OperatingSystem createOperatingSystem() {
            return OperatingSystem.current();
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
        FileSystem createFileSystem(
            GenericFileSystem.Factory genericFileSystemFactory,
            OperatingSystem operatingSystem,
            TemporaryFileProvider temporaryFileProvider
        ) {
            if (operatingSystem.isWindows()) {
                return genericFileSystemFactory.create(new EmptyChmod(), new FallbackStat(), new WindowsJdk7Symlink());
            }

            PosixJdk7FilePermissionHandler handler = new PosixJdk7FilePermissionHandler();
            Symlink symlink = new Jdk7Symlink(temporaryFileProvider);
            return genericFileSystemFactory.create(handler, handler, symlink);
        }

        @Provides
        VirtualFileSystem createVirtualFileSystem(FileSystem fileSystem) {
            CaseSensitivity caseSensitivity = fileSystem.isCaseSensitive() ? CASE_SENSITIVE : CASE_INSENSITIVE;
            SnapshotHierarchy root = DefaultSnapshotHierarchy.empty(caseSensitivity);
            return new CustomVirtualFileSystem(root);
        }

        @Provides
        Interner<String> createStringInterner() {
            return new StringInterner();
        }

        @Provides
        DirectorySnapshotterStatistics.Collector creaetDirectorySnapshotterStatisticsCollector() {
            return new DirectorySnapshotterStatistics.Collector();
        }

        @Provides
        FileMetadataAccessor createFileMetadataAccessor() {
            return new Jdk7FileMetadataAccessor();
        }

        @Provides
        FileSystemAccess createFileSystemAccess(
            FileHasher fileHasher,
            Interner<String> stringInterner,
            FileSystem fileSystem,
            VirtualFileSystem virtualFileSystem,
            DirectorySnapshotterStatistics.Collector statisticsCollector
        ) {
            return new DefaultFileSystemAccess(
                fileHasher,
                stringInterner,
                fileSystem,
                virtualFileSystem,
                locations -> locations.forEach(System.out::println),
                statisticsCollector
            );
        }
    }

    private static class Jdk7FileMetadataAccessor implements FileMetadataAccessor {
        @Override
        public FileMetadata stat(File f) {
            if (!f.exists()) {
                // This is really not cool, but we cannot rely on `readAttributes` because it will
                // THROW AN EXCEPTION if the file is missing, which is really incredibly slow just
                // to determine if a file exists or not.
                return DefaultFileMetadata.missing(FileMetadata.AccessType.DIRECT);
            }
            try {
                BasicFileAttributes bfa = java.nio.file.Files.readAttributes(f.toPath(), BasicFileAttributes.class);
                if (bfa.isDirectory()) {
                    return DefaultFileMetadata.directory(FileMetadata.AccessType.DIRECT);
                }
                return DefaultFileMetadata.file(bfa.lastModifiedTime().toMillis(), bfa.size(), FileMetadata.AccessType.DIRECT);
            } catch (IOException e) {
                throw UncheckedException.throwAsUncheckedException(e);
            }
        }
    }
}
