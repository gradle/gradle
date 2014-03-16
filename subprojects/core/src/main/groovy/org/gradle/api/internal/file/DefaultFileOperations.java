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

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.PathValidation;
import org.gradle.api.file.*;
import org.gradle.api.internal.ClosureBackedAction;
import org.gradle.api.internal.ProcessOperations;
import org.gradle.api.internal.file.archive.TarFileTree;
import org.gradle.api.internal.file.archive.ZipFileTree;
import org.gradle.api.internal.file.collections.DefaultConfigurableFileCollection;
import org.gradle.api.internal.file.collections.DefaultConfigurableFileTree;
import org.gradle.api.internal.file.collections.FileTreeAdapter;
import org.gradle.api.internal.file.copy.DefaultCopySpec;
import org.gradle.api.internal.file.copy.DeleteActionImpl;
import org.gradle.api.internal.file.copy.FileCopier;
import org.gradle.api.internal.resources.DefaultResourceHandler;
import org.gradle.api.internal.tasks.TaskResolver;
import org.gradle.api.resources.ReadableResource;
import org.gradle.api.tasks.WorkResult;
import org.gradle.internal.nativeplatform.filesystem.FileSystem;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.process.ExecResult;
import org.gradle.process.internal.*;
import org.gradle.util.ConfigureUtil;
import org.gradle.util.GFileUtils;

import java.io.File;
import java.net.URI;
import java.util.Collections;
import java.util.Map;

import static org.gradle.util.ConfigureUtil.configure;

public class DefaultFileOperations implements FileOperations, ProcessOperations, ExecActionFactory {
    private final FileResolver fileResolver;
    private final TaskResolver taskResolver;
    private final TemporaryFileProvider temporaryFileProvider;
    private final Instantiator instantiator;
    private final DeleteAction deleteAction;
    private final DefaultResourceHandler resourceHandler;
    private final FileCopier fileCopier;
    private final FileSystem fileSystem;

    public DefaultFileOperations(FileResolver fileResolver, TaskResolver taskResolver, TemporaryFileProvider temporaryFileProvider, Instantiator instantiator, FileLookup fileLookup) {
        this.fileResolver = fileResolver;
        this.taskResolver = taskResolver;
        this.temporaryFileProvider = temporaryFileProvider;
        this.instantiator = instantiator;
        this.deleteAction = new DeleteActionImpl(fileResolver);
        this.resourceHandler = new DefaultResourceHandler(fileResolver);
        fileCopier = new FileCopier(this.instantiator, this.fileResolver, fileLookup);
        fileSystem = fileLookup.getFileSystem();
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

    public ConfigurableFileCollection files(Object paths, Closure configureClosure) {
        return configure(configureClosure, files(paths));
    }

    public ConfigurableFileTree fileTree(Object baseDir) {
        return new DefaultConfigurableFileTree(baseDir, fileResolver, taskResolver, fileCopier);
    }

    public ConfigurableFileTree fileTree(Object baseDir, Closure closure) {
        return ConfigureUtil.configure(closure, fileTree(baseDir));
    }

    public ConfigurableFileTree fileTree(Map<String, ?> args) {
        return new DefaultConfigurableFileTree(args, fileResolver, taskResolver, fileCopier);
    }

    @Deprecated
    public ConfigurableFileTree fileTree(Closure closure) {
        // This method is deprecated, but the deprecation warning is added on public classes that delegate to this. 
        return configure(closure, new DefaultConfigurableFileTree(Collections.emptyMap(), fileResolver, taskResolver, fileCopier));
    }

    public FileTree zipTree(Object zipPath) {
        return new FileTreeAdapter(new ZipFileTree(file(zipPath), getExpandDir(), fileSystem));
    }

    public FileTree tarTree(Object tarPath) {
        ReadableResource res = getResources().maybeCompressed(tarPath);

        TarFileTree tarTree = new TarFileTree(res, getExpandDir(), fileSystem);
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
        return deleteAction.delete(paths);
    }

    public WorkResult copy(Closure closure) {
        return fileCopier.copy(new ClosureBackedAction<CopySpec>(closure));
    }

    public WorkResult sync(Action<? super CopySpec> action) {
        return fileCopier.sync(action);
    }

    public CopySpec copySpec(Closure closure) {
        return copySpec(new ClosureBackedAction<CopySpec>(closure));
    }

    public CopySpec copySpec(Action<? super CopySpec> action) {
        DefaultCopySpec copySpec = instantiator.newInstance(DefaultCopySpec.class, fileResolver, instantiator);
        action.execute(copySpec);
        return copySpec;
    }

    public FileResolver getFileResolver() {
        return fileResolver;
    }

    public ExecResult javaexec(Closure cl) {
        JavaExecAction javaExecAction = ConfigureUtil.configure(cl, instantiator.newInstance(DefaultJavaExecAction.class, fileResolver));
        return javaExecAction.execute();
    }

    public ExecResult exec(Closure cl) {
        ExecAction execAction = ConfigureUtil.configure(cl, instantiator.newInstance(DefaultExecAction.class, fileResolver));
        return execAction.execute();
    }

    public ExecAction newExecAction() {
        return new DefaultExecAction(fileResolver);
    }

    public DefaultResourceHandler getResources() {
        return resourceHandler;
    }
}
