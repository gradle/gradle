/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.api.internal.file;

import org.gradle.api.Action;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.PathValidation;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.ConfigurableFileTree;
import org.gradle.api.file.CopySpec;
import org.gradle.api.file.DeleteSpec;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.SyncSpec;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.internal.file.archive.TarFileTree;
import org.gradle.api.internal.file.archive.ZipFileTree;
import org.gradle.api.internal.file.collections.DirectoryFileTreeFactory;
import org.gradle.api.internal.file.collections.FileTreeAdapter;
import org.gradle.api.internal.file.copy.DefaultCopySpec;
import org.gradle.api.internal.file.copy.FileCopier;
import org.gradle.api.internal.file.delete.DefaultDeleteSpec;
import org.gradle.api.internal.file.delete.DeleteSpecInternal;
import org.gradle.api.internal.file.temp.TemporaryFileProvider;
import org.gradle.api.internal.provider.ProviderInternal;
import org.gradle.api.internal.resources.ApiTextResourceAdapter;
import org.gradle.api.internal.resources.DefaultResourceHandler;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.resources.ReadableResource;
import org.gradle.api.resources.ResourceHandler;
import org.gradle.api.resources.internal.LocalResourceAdapter;
import org.gradle.api.resources.internal.ReadableResourceInternal;
import org.gradle.api.tasks.WorkResult;
import org.gradle.api.tasks.WorkResults;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.internal.Cast;
import org.gradle.internal.Factory;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.file.Deleter;
import org.gradle.internal.hash.FileHasher;
import org.gradle.internal.hash.StreamHasher;
import org.gradle.internal.nativeintegration.filesystem.FileSystem;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.resource.local.LocalFileStandInExternalResource;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.util.internal.ConfigureUtil;
import org.gradle.util.internal.GFileUtils;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.Map;

import static org.gradle.api.internal.lambdas.SerializableLambdas.transformer;

// Suppress warnings that could lead the code to be refactored in a configuration cache incompatible way.
@SuppressWarnings({"Convert2Lambda", "Anonymous2MethodRef"})
public class DefaultFileOperations implements FileOperations {
    private final FileResolver fileResolver;
    private final TemporaryFileProvider temporaryFileProvider;
    private final Instantiator instantiator;
    private final Deleter deleter;
    private final ResourceHandler resourceHandler;
    private final StreamHasher streamHasher;
    private final FileHasher fileHasher;
    private final Factory<PatternSet> patternSetFactory;
    private final FileCopier fileCopier;
    private final FileSystem fileSystem;
    private final DirectoryFileTreeFactory directoryFileTreeFactory;
    private final FileCollectionFactory fileCollectionFactory;
    private final ProviderFactory providers;

    public DefaultFileOperations(
        FileResolver fileResolver,
        TemporaryFileProvider temporaryFileProvider,
        Instantiator instantiator,
        DirectoryFileTreeFactory directoryFileTreeFactory,
        StreamHasher streamHasher,
        FileHasher fileHasher,
        DefaultResourceHandler.Factory resourceHandlerFactory,
        FileCollectionFactory fileCollectionFactory,
        ObjectFactory objectFactory,
        FileSystem fileSystem,
        Factory<PatternSet> patternSetFactory,
        Deleter deleter,
        DocumentationRegistry documentationRegistry,
        ProviderFactory providers
    ) {
        this.fileCollectionFactory = fileCollectionFactory;
        this.fileResolver = fileResolver;
        this.temporaryFileProvider = temporaryFileProvider;
        this.instantiator = instantiator;
        this.directoryFileTreeFactory = directoryFileTreeFactory;
        this.resourceHandler = resourceHandlerFactory.create(this);
        this.streamHasher = streamHasher;
        this.fileHasher = fileHasher;
        this.patternSetFactory = patternSetFactory;
        this.providers = providers;
        this.fileCopier = new FileCopier(
            deleter,
            directoryFileTreeFactory,
            fileCollectionFactory,
            fileResolver,
            patternSetFactory,
            objectFactory,
            fileSystem,
            instantiator,
            documentationRegistry
        );
        this.fileSystem = fileSystem;
        this.deleter = deleter;
    }

    @Override
    public File file(Object path) {
        return fileResolver.resolve(path);
    }

    @Override
    public File file(Object path, PathValidation validation) {
        return fileResolver.resolve(path, validation);
    }

    @Override
    public URI uri(Object path) {
        return fileResolver.resolveUri(path);
    }

    @Override
    public ConfigurableFileCollection configurableFiles(Object... paths) {
        return fileCollectionFactory.configurableFiles().from(paths);
    }

    @Override
    public FileCollection immutableFiles(Object... paths) {
        return fileCollectionFactory.resolving(paths);
    }

    @Override
    public PatternSet patternSet() {
        return patternSetFactory.create();
    }

    @Override
    public ConfigurableFileTree fileTree(Object baseDir) {
        ConfigurableFileTree fileTree = fileCollectionFactory.fileTree();
        fileTree.from(baseDir);
        return fileTree;
    }

    @Override
    public ConfigurableFileTree fileTree(Map<String, ?> args) {
        ConfigurableFileTree fileTree = fileCollectionFactory.fileTree();
        ConfigureUtil.configureByMap(args, fileTree);
        return fileTree;
    }

    @Override
    public FileTreeInternal zipTree(Object zipPath) {
        Provider<File> fileProvider = asFileProvider(zipPath);
        return new FileTreeAdapter(new ZipFileTree(fileProvider, getExpandDir(), fileSystem, directoryFileTreeFactory, fileHasher), patternSetFactory);
    }

    @Override
    public FileTreeInternal tarTree(Object tarPath) {
        Provider<File> fileProvider = asFileProvider(tarPath);
        Provider<ReadableResourceInternal> resource = providers.provider(() -> {
            if (tarPath instanceof ReadableResourceInternal) {
                return (ReadableResourceInternal) tarPath;
            } else if (tarPath instanceof ReadableResource) {
                // custom type
                return new UnknownBackingFileReadableResource((ReadableResource) tarPath);
            } else {
                File tarFile = file(tarPath);
                return new LocalResourceAdapter(new LocalFileStandInExternalResource(tarFile, fileSystem));
            }
        });

        if (tarPath instanceof ReadableResource) {
            boolean hasBackingFile = tarPath instanceof ReadableResourceInternal
                && ((ReadableResourceInternal) tarPath).getBackingFile() != null;
            if (!hasBackingFile) {
                DeprecationLogger.deprecateAction("Using tarTree() on a resource without a backing file")
                    .withAdvice("Convert the resource to a file and then pass this file to tarTree(). For converting the resource to a file you can use a custom task or declare a dependency.")
                    .willBecomeAnErrorInGradle8()
                    .withUpgradeGuideSection(7, "tar_tree_no_backing_file")
                    .nagUser();
            }
        }

        TarFileTree tarTree = new TarFileTree(fileProvider, resource.map(MaybeCompressedFileResource::new), getExpandDir(), fileSystem, directoryFileTreeFactory, streamHasher, fileHasher);
        return new FileTreeAdapter(tarTree, patternSetFactory);
    }

    private Provider<File> asFileProvider(Object path) {
        if (path instanceof ReadableResource) {
            return providers.provider(() -> null);
        }
        if (path instanceof Provider) {
            ProviderInternal<?> provider = (ProviderInternal<?>) path;
            Class<?> type = provider.getType();
            if (type != null) {
                if (File.class.isAssignableFrom(type)) {
                    return Cast.uncheckedCast(path);
                }
                if (RegularFile.class.isAssignableFrom(type)) {
                    Provider<RegularFile> regularFileProvider = Cast.uncheckedCast(provider);
                    return regularFileProvider.map(transformer(RegularFile::getAsFile));
                }
            }
            return provider.map(transformer(this::file));
        }
        return providers.provider(() -> file(path));
    }

    private File getExpandDir() {
        return temporaryFileProvider.newTemporaryFile("expandedArchives");
    }

    @Override
    public String relativePath(Object path) {
        return fileResolver.resolveAsRelativePath(path);
    }

    @Override
    public File mkdir(Object path) {
        File dir = fileResolver.resolve(path);
        if (dir.isFile()) {
            throw new InvalidUserDataException(String.format("Can't create directory. The path=%s points to an existing file.", path));
        }
        GFileUtils.mkdirs(dir);
        return dir;
    }

    @Override
    public boolean delete(Object... paths) {
        return delete(deleteSpec -> deleteSpec.delete(paths).setFollowSymlinks(false)).getDidWork();
    }

    @Override
    public WorkResult delete(Action<? super DeleteSpec> action) {
        DeleteSpecInternal deleteSpec = new DefaultDeleteSpec();
        action.execute(deleteSpec);
        FileCollectionInternal roots = fileCollectionFactory.resolving(deleteSpec.getPaths());
        boolean didWork = false;
        for (File root : roots) {
            try {
                didWork |= deleter.deleteRecursively(root, deleteSpec.isFollowSymlinks());
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        }
        return WorkResults.didWork(didWork);
    }

    @Override
    public WorkResult copy(Action<? super CopySpec> action) {
        return fileCopier.copy(action);
    }

    @Override
    public WorkResult sync(Action<? super SyncSpec> action) {
        return fileCopier.sync(action);
    }

    public CopySpec copySpec(Action<? super CopySpec> action) {
        CopySpec copySpec = copySpec();
        action.execute(copySpec);
        return copySpec;
    }

    @Override
    public CopySpec copySpec() {
        return instantiator.newInstance(DefaultCopySpec.class, fileCollectionFactory, instantiator, patternSetFactory);
    }

    @Override
    public FileResolver getFileResolver() {
        return fileResolver;
    }

    @Override
    public ResourceHandler getResources() {
        return resourceHandler;
    }

    public static DefaultFileOperations createSimple(FileResolver fileResolver, FileCollectionFactory fileTreeFactory, ServiceRegistry services) {
        Instantiator instantiator = services.get(Instantiator.class);
        ObjectFactory objectFactory = services.get(ObjectFactory.class);
        FileSystem fileSystem = services.get(FileSystem.class);
        DirectoryFileTreeFactory directoryFileTreeFactory = services.get(DirectoryFileTreeFactory.class);
        StreamHasher streamHasher = services.get(StreamHasher.class);
        FileHasher fileHasher = services.get(FileHasher.class);
        ApiTextResourceAdapter.Factory textResourceAdapterFactory = services.get(ApiTextResourceAdapter.Factory.class);
        Factory<PatternSet> patternSetFactory = services.getFactory(PatternSet.class);
        Deleter deleter = services.get(Deleter.class);
        DocumentationRegistry documentationRegistry = services.get(DocumentationRegistry.class);
        ProviderFactory providers = services.get(ProviderFactory.class);

        DefaultResourceHandler.Factory resourceHandlerFactory = DefaultResourceHandler.Factory.from(
            fileResolver,
            fileSystem,
            null,
            textResourceAdapterFactory
        );

        return new DefaultFileOperations(
            fileResolver,
            null,
            instantiator,
            directoryFileTreeFactory,
            streamHasher,
            fileHasher,
            resourceHandlerFactory,
            fileTreeFactory,
            objectFactory,
            fileSystem,
            patternSetFactory,
            deleter,
            documentationRegistry,
            providers);
    }
}
