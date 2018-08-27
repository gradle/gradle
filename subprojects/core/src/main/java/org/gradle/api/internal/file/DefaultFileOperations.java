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
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.ConfigurableFileTree;
import org.gradle.api.file.CopySpec;
import org.gradle.api.file.DeleteSpec;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.internal.ProcessOperations;
import org.gradle.api.internal.file.archive.TarFileTree;
import org.gradle.api.internal.file.archive.ZipFileTree;
import org.gradle.api.internal.file.collections.DefaultConfigurableFileCollection;
import org.gradle.api.internal.file.collections.DefaultConfigurableFileTree;
import org.gradle.api.internal.file.collections.DirectoryFileTreeFactory;
import org.gradle.api.internal.file.collections.FileTreeAdapter;
import org.gradle.api.internal.file.collections.ImmutableFileCollection;
import org.gradle.api.internal.file.copy.DefaultCopySpec;
import org.gradle.api.internal.file.copy.FileCopier;
import org.gradle.api.internal.file.delete.Deleter;
import org.gradle.api.internal.resources.DefaultResourceHandler;
import org.gradle.api.internal.resources.DefaultResourceResolver;
import org.gradle.api.internal.tasks.TaskResolver;
import org.gradle.api.resources.ReadableResource;
import org.gradle.api.resources.internal.LocalResourceAdapter;
import org.gradle.api.resources.internal.ReadableResourceInternal;
import org.gradle.api.tasks.WorkResult;
import org.gradle.internal.hash.FileHasher;
import org.gradle.internal.hash.StreamHasher;
import org.gradle.internal.nativeintegration.filesystem.FileSystem;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.resource.TextResourceLoader;
import org.gradle.internal.resource.local.LocalFileStandInExternalResource;
import org.gradle.process.ExecResult;
import org.gradle.process.ExecSpec;
import org.gradle.process.JavaExecSpec;
import org.gradle.process.internal.ExecAction;
import org.gradle.process.internal.ExecFactory;
import org.gradle.process.internal.JavaExecAction;
import org.gradle.util.GFileUtils;

import javax.annotation.Nullable;
import java.io.File;
import java.net.URI;
import java.util.Map;

public class DefaultFileOperations implements FileOperations, ProcessOperations {
    private final FileResolver fileResolver;
    @Nullable
    private final TaskResolver taskResolver;
    @Nullable
    private final TemporaryFileProvider temporaryFileProvider;
    private final Instantiator instantiator;
    private final Deleter deleter;
    private final DefaultResourceHandler resourceHandler;
    private final StreamHasher streamHasher;
    private final FileHasher fileHasher;
    private final ExecFactory execFactory;
    private final FileCopier fileCopier;
    private final FileSystem fileSystem;
    private final DirectoryFileTreeFactory directoryFileTreeFactory;

    @Deprecated //used by the Kotlin DSL
    public DefaultFileOperations(FileResolver fileResolver, @Nullable TaskResolver taskResolver, @Nullable TemporaryFileProvider temporaryFileProvider, Instantiator instantiator, FileLookup fileLookup, DirectoryFileTreeFactory directoryFileTreeFactory, StreamHasher streamHasher, FileHasher fileHasher, ExecFactory execFactory) {
        this(fileResolver, taskResolver, temporaryFileProvider, instantiator, fileLookup, directoryFileTreeFactory, streamHasher, fileHasher, execFactory, null);
    }

    public DefaultFileOperations(FileResolver fileResolver, @Nullable TaskResolver taskResolver, @Nullable TemporaryFileProvider temporaryFileProvider, Instantiator instantiator, FileLookup fileLookup, DirectoryFileTreeFactory directoryFileTreeFactory, StreamHasher streamHasher, FileHasher fileHasher, ExecFactory execFactory, @Nullable TextResourceLoader textResourceLoader) {
        FileSystem fileSystem = fileLookup.getFileSystem();
        this.fileResolver = fileResolver;
        this.taskResolver = taskResolver;
        this.temporaryFileProvider = temporaryFileProvider;
        this.instantiator = instantiator;
        this.directoryFileTreeFactory = directoryFileTreeFactory;
        this.resourceHandler = new DefaultResourceHandler(this, new DefaultResourceResolver(fileResolver, fileSystem), temporaryFileProvider, textResourceLoader);
        this.streamHasher = streamHasher;
        this.fileHasher = fileHasher;
        this.execFactory = execFactory;
        this.fileCopier = new FileCopier(this.instantiator, this.fileResolver, fileLookup, directoryFileTreeFactory);
        this.fileSystem = fileSystem;
        this.deleter = new Deleter(fileResolver, fileSystem);
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
    public ConfigurableFileCollection configurableFiles() {
        return new DefaultConfigurableFileCollection(fileResolver, taskResolver);
    }

    @Override
    public ConfigurableFileCollection configurableFiles(Object... paths) {
        return new DefaultConfigurableFileCollection(fileResolver, taskResolver, paths);
    }

    @Override
    public ConfigurableFileCollection files(Object... paths) {
        return new DefaultConfigurableFileCollection(fileResolver, taskResolver, paths);
    }

    @Override
    public FileCollection immutableFiles(Object... paths) {
        return ImmutableFileCollection.usingResolver(fileResolver, paths);
    }

    @Override
    public ConfigurableFileTree fileTree(Object baseDir) {
        return new DefaultConfigurableFileTree(baseDir, fileResolver, taskResolver, directoryFileTreeFactory);
    }

    @Override
    public ConfigurableFileTree fileTree(Map<String, ?> args) {
        return new DefaultConfigurableFileTree(args, fileResolver, taskResolver, directoryFileTreeFactory);
    }

    @Override
    public FileTree zipTree(Object zipPath) {
        return new FileTreeAdapter(new ZipFileTree(file(zipPath), getExpandDir(), fileSystem, directoryFileTreeFactory, fileHasher), fileResolver.getPatternSetFactory());
    }

    @Override
    public FileTree tarTree(Object tarPath) {
        File tarFile = null;
        ReadableResourceInternal resource;
        if (tarPath instanceof ReadableResourceInternal) {
            resource = (ReadableResourceInternal) tarPath;
        } else if (tarPath instanceof ReadableResource) {
            // custom type
            resource = new UnknownBackingFileReadableResource((ReadableResource)tarPath);
        } else {
            tarFile = file(tarPath);
            resource = new LocalResourceAdapter(new LocalFileStandInExternalResource(tarFile, fileSystem));
        }
        TarFileTree tarTree = new TarFileTree(tarFile, new MaybeCompressedFileResource(resource), getExpandDir(), fileSystem, fileSystem, directoryFileTreeFactory, streamHasher, fileHasher);
        return new FileTreeAdapter(tarTree, fileResolver.getPatternSetFactory());
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
        return deleter.delete(paths);
    }

    @Override
    public WorkResult delete(Action<? super DeleteSpec> action) {
        return deleter.delete(action);
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
        return instantiator.newInstance(DefaultCopySpec.class, fileResolver, instantiator);
    }

    @Override
    public FileResolver getFileResolver() {
        return fileResolver;
    }

    @Override
    public ExecResult javaexec(Action<? super JavaExecSpec> action) {
        JavaExecAction javaExecAction = execFactory.forContext(fileResolver, instantiator).newDecoratedJavaExecAction();
        action.execute(javaExecAction);
        return javaExecAction.execute();
    }

    @Override
    public ExecResult exec(Action<? super ExecSpec> action) {
        ExecAction execAction = execFactory.forContext(fileResolver, instantiator).newDecoratedExecAction();
        action.execute(execAction);
        return execAction.execute();
    }

    @Override
    public DefaultResourceHandler getResources() {
        return resourceHandler;
    }
}
