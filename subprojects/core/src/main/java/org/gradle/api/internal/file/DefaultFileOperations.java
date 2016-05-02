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
import org.gradle.api.file.*;
import org.gradle.api.internal.ProcessOperations;
import org.gradle.api.internal.file.archive.TarFileTree;
import org.gradle.api.internal.file.archive.ZipFileTree;
import org.gradle.api.internal.file.collections.DefaultConfigurableFileCollection;
import org.gradle.api.internal.file.collections.DefaultConfigurableFileTree;
import org.gradle.api.internal.file.collections.DirectoryFileTreeFactory;
import org.gradle.api.internal.file.collections.FileTreeAdapter;
import org.gradle.api.internal.file.copy.DefaultCopySpec;
import org.gradle.api.internal.file.delete.Deleter;
import org.gradle.api.internal.file.copy.FileCopier;
import org.gradle.api.internal.resources.DefaultResourceHandler;
import org.gradle.api.internal.tasks.TaskResolver;
import org.gradle.api.resources.ReadableResource;
import org.gradle.api.resources.internal.ReadableResourceInternal;
import org.gradle.api.tasks.WorkResult;
import org.gradle.internal.nativeintegration.filesystem.FileSystem;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.process.ExecResult;
import org.gradle.process.ExecSpec;
import org.gradle.process.JavaExecSpec;
import org.gradle.process.internal.DefaultExecAction;
import org.gradle.process.internal.DefaultJavaExecAction;
import org.gradle.process.internal.ExecAction;
import org.gradle.process.internal.JavaExecAction;
import org.gradle.util.GFileUtils;

import java.io.File;
import java.net.URI;
import java.util.Map;

public class DefaultFileOperations implements FileOperations, ProcessOperations {
    private final FileResolver fileResolver;
    private final TaskResolver taskResolver;
    private final TemporaryFileProvider temporaryFileProvider;
    private final Instantiator instantiator;
    private final Deleter deleter;
    private final DefaultResourceHandler resourceHandler;
    private final FileCopier fileCopier;
    private final FileSystem fileSystem;
    private final DirectoryFileTreeFactory directoryFileTreeFactory;

    public DefaultFileOperations(FileResolver fileResolver, TaskResolver taskResolver, TemporaryFileProvider temporaryFileProvider, Instantiator instantiator, FileLookup fileLookup, DirectoryFileTreeFactory directoryFileTreeFactory) {
        this.fileResolver = fileResolver;
        this.taskResolver = taskResolver;
        this.temporaryFileProvider = temporaryFileProvider;
        this.instantiator = instantiator;
        this.directoryFileTreeFactory = directoryFileTreeFactory;
        this.resourceHandler = new DefaultResourceHandler(this, temporaryFileProvider);
        this.fileCopier = new FileCopier(this.instantiator, this.fileResolver, fileLookup);
        this.fileSystem = fileLookup.getFileSystem();
        this.deleter = new Deleter(fileResolver, fileSystem);
    }

    public File file(Object path) {
        return fileResolver.resolve(path);
    }

    public File file(Object path, PathValidation validation) {
        return fileResolver.resolve(path, validation);
    }

    public URI uri(Object path) {
        return fileResolver.resolveUri(path);
    }

    public ConfigurableFileCollection files(Object... paths) {
        return new DefaultConfigurableFileCollection(fileResolver, taskResolver, paths);
    }

    public ConfigurableFileTree fileTree(Object baseDir) {
        return new DefaultConfigurableFileTree(baseDir, fileResolver, taskResolver, fileCopier, directoryFileTreeFactory);
    }

    public ConfigurableFileTree fileTree(Map<String, ?> args) {
        return new DefaultConfigurableFileTree(args, fileResolver, taskResolver, fileCopier, directoryFileTreeFactory);
    }

    public FileTree zipTree(Object zipPath) {
        return new FileTreeAdapter(new ZipFileTree(file(zipPath), getExpandDir(), fileSystem, directoryFileTreeFactory));
    }

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
            resource = new FileResource(tarFile);
        }
        TarFileTree tarTree = new TarFileTree(tarFile, new MaybeCompressedFileResource(resource), getExpandDir(), fileSystem, fileSystem, directoryFileTreeFactory);
        return new FileTreeAdapter(tarTree);
    }

    private File getExpandDir() {
        return temporaryFileProvider.newTemporaryFile("expandedArchives");
    }

    public String relativePath(Object path) {
        return fileResolver.resolveAsRelativePath(path);
    }

    public File mkdir(Object path) {
        File dir = fileResolver.resolve(path);
        if (dir.isFile()) {
            throw new InvalidUserDataException(String.format("Can't create directory. The path=%s points to an existing file.", path));
        }
        GFileUtils.mkdirs(dir);
        return dir;
    }

    public boolean delete(Object... paths) {
        return deleter.delete(paths);
    }

    public WorkResult delete(Action<? super DeleteSpec> action) {
        return deleter.delete(action);
    }

    public WorkResult copy(Action<? super CopySpec> action) {
        return fileCopier.copy(action);
    }

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

    public FileResolver getFileResolver() {
        return fileResolver;
    }

    public ExecResult javaexec(Action<? super JavaExecSpec> action) {
        JavaExecAction javaExecAction = instantiator.newInstance(DefaultJavaExecAction.class, fileResolver);
        action.execute(javaExecAction);
        return javaExecAction.execute();
    }

    public ExecResult exec(Action<? super ExecSpec> action) {
        ExecAction execAction = instantiator.newInstance(DefaultExecAction.class, fileResolver);
        action.execute(execAction);
        return execAction.execute();
    }

    public DefaultResourceHandler getResources() {
        return resourceHandler;
    }
}
