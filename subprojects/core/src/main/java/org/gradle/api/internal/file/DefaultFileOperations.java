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
import org.gradle.api.internal.file.archive.TarFileTree;
import org.gradle.api.internal.file.archive.ZipFileTree;
import org.gradle.api.internal.file.collections.DirectoryFileTreeFactory;
import org.gradle.api.internal.file.collections.FileTreeAdapter;
import org.gradle.api.internal.file.copy.DefaultCopySpec;
import org.gradle.api.internal.file.copy.FileCopier;
import org.gradle.api.internal.file.delete.DefaultDeleteSpec;
import org.gradle.api.internal.file.delete.DeleteSpecInternal;
import org.gradle.api.internal.resources.ApiTextResourceAdapter;
import org.gradle.api.internal.resources.DefaultResourceHandler;
import org.gradle.api.resources.ReadableResource;
import org.gradle.api.resources.ResourceHandler;
import org.gradle.api.resources.internal.LocalResourceAdapter;
import org.gradle.api.resources.internal.ReadableResourceInternal;
import org.gradle.api.tasks.WorkResult;
import org.gradle.api.tasks.WorkResults;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.internal.Factory;
import org.gradle.internal.file.Deleter;
import org.gradle.internal.hash.FileHasher;
import org.gradle.internal.hash.StreamHasher;
import org.gradle.internal.nativeintegration.filesystem.FileSystem;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.resource.local.LocalFileStandInExternalResource;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.util.ConfigureUtil;
import org.gradle.util.GFileUtils;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.Map;

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

    public DefaultFileOperations(
        FileResolver fileResolver,
        TemporaryFileProvider temporaryFileProvider,
        Instantiator instantiator,
        DirectoryFileTreeFactory directoryFileTreeFactory,
        StreamHasher streamHasher,
        FileHasher fileHasher,
        DefaultResourceHandler.Factory resourceHandlerFactory,
        FileCollectionFactory fileCollectionFactory,
        FileSystem fileSystem,
        Factory<PatternSet> patternSetFactory,
        Deleter deleter
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
        this.fileCopier = new FileCopier(
            deleter,
            directoryFileTreeFactory,
            fileCollectionFactory,
            fileResolver,
            patternSetFactory,
            fileSystem,
            instantiator
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
        return new FileTreeAdapter(new ZipFileTree(file(zipPath), getExpandDir(), fileSystem, directoryFileTreeFactory, fileHasher), patternSetFactory);
    }

    @Override
    public FileTreeInternal tarTree(Object tarPath) {
        File tarFile = null;
        ReadableResourceInternal resource;
        if (tarPath instanceof ReadableResourceInternal) {
            resource = (ReadableResourceInternal) tarPath;
        } else if (tarPath instanceof ReadableResource) {
            // custom type
            resource = new UnknownBackingFileReadableResource((ReadableResource) tarPath);
        } else {
            tarFile = file(tarPath);
            resource = new LocalResourceAdapter(new LocalFileStandInExternalResource(tarFile, fileSystem));
        }
        TarFileTree tarTree = new TarFileTree(tarFile, new MaybeCompressedFileResource(resource), getExpandDir(), fileSystem, directoryFileTreeFactory, streamHasher, fileHasher);
        return new FileTreeAdapter(tarTree, patternSetFactory);
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
    public WorkResult sync(Action<? super CopySpec> action) {
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
        FileSystem fileSystem = services.get(FileSystem.class);
        DirectoryFileTreeFactory directoryFileTreeFactory = services.get(DirectoryFileTreeFactory.class);
        StreamHasher streamHasher = services.get(StreamHasher.class);
        FileHasher fileHasher = services.get(FileHasher.class);
        ApiTextResourceAdapter.Factory textResourceAdapterFactory = services.get(ApiTextResourceAdapter.Factory.class);
        Factory<PatternSet> patternSetFactory = services.getFactory(PatternSet.class);
        Deleter deleter = services.get(Deleter.class);

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
            fileSystem,
            patternSetFactory,
            deleter
        );
    }
}
