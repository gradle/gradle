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

package org.gradle.api.internal.tasks;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.CompositeFileCollection;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSetOutput;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.internal.logging.text.TreeFormatter;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.File;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

public abstract class DefaultSourceSetOutput extends CompositeFileCollection implements SourceSetOutput {
    private final ConfigurableFileCollection outputDirectories;
    private Object resourcesDir;

    private final ConfigurableFileCollection classesDirs;
    private final ConfigurableFileCollection dirs;
    private final ConfigurableFileCollection generatedSourcesDirs;
    private final FileResolver fileResolver;

    private DirectoryContribution resourcesContributor;

    @Inject
    public DefaultSourceSetOutput(String sourceSetDisplayName, TaskDependencyFactory taskDependencyFactory, FileResolver fileResolver, FileCollectionFactory fileCollectionFactory) {
        super(taskDependencyFactory);
        this.fileResolver = fileResolver;

        this.classesDirs = fileCollectionFactory.configurableFiles(sourceSetDisplayName + " classesDirs");

        this.outputDirectories = fileCollectionFactory.configurableFiles(sourceSetDisplayName + " classes");
        outputDirectories.from(classesDirs, (Callable<File>) this::getResourcesDir);

        this.dirs = fileCollectionFactory.configurableFiles(sourceSetDisplayName + " dirs");

        this.generatedSourcesDirs = fileCollectionFactory.configurableFiles(sourceSetDisplayName + " generatedSourcesDirs");
    }

    @Override
    protected void visitChildren(Consumer<FileCollectionInternal> visitor) {
        visitor.accept((FileCollectionInternal) outputDirectories);
    }

    @Override
    public String getDisplayName() {
        return outputDirectories.toString();
    }

    @Override
    protected void appendContents(TreeFormatter formatter) {
        formatter.node("source set: " + outputDirectories.toString());
        formatter.node("output directories");
        formatter.startChildren();
        ((FileCollectionInternal) outputDirectories).describeContents(formatter);
        formatter.endChildren();
    }

    @Override
    public ConfigurableFileCollection getClassesDirs() {
        return classesDirs;
    }

    /**
     * Set the task contributor to the provided resources directory. The provided resources
     * directory provider should resolve to the same directory set by {@link #setResourcesDir}.
     *
     * @param directory The resources directory provider.
     * @param task The task which generates {@code directory}.
     */
    public void setResourcesContributor(Provider<File> directory, TaskProvider<?> task) {
        this.resourcesContributor = new DirectoryContribution(directory, task);
    }

    @Override
    @Nullable
    public File getResourcesDir() {
        if (resourcesDir == null) {
            return null;
        }
        return fileResolver.resolve(resourcesDir);
    }

    @Override
    public void setResourcesDir(File resourcesDir) {
        this.resourcesDir = resourcesDir;
    }

    @Override
    public void setResourcesDir(Object resourcesDir) {
        this.resourcesDir = resourcesDir;
    }

    public void builtBy(Object... taskPaths) {
        outputDirectories.builtBy(taskPaths);
    }

    @Override
    public void dir(Object dir) {
        this.dir(Collections.emptyMap(), dir);
    }

    @Override
    public void dir(Map<String, Object> options, Object dir) {
        this.dirs.from(dir);
        this.outputDirectories.from(dir);

        Object builtBy = options.get("builtBy");
        if (builtBy != null) {
            this.builtBy(builtBy);
            this.dirs.builtBy(builtBy);
        }
    }

    @Override
    public FileCollection getDirs() {
        return dirs;
    }

    @Override
    public ConfigurableFileCollection getGeneratedSourcesDirs() {
        return generatedSourcesDirs;
    }

    @Nullable
    public DirectoryContribution getResourcesContribution() {
        return resourcesContributor;
    }

    /**
     * A mapping from a directory to the task which provides that directory.
     */
    public static class DirectoryContribution {
        private final Provider<File> directory;
        private final TaskProvider<?> task;

        public DirectoryContribution(Provider<File> directory, TaskProvider<?> task) {
            this.directory = directory;
            this.task = task;
        }

        public Provider<File> getDirectory() {
            return directory;
        }

        public TaskProvider<?> getTask() {
            return task;
        }
    }
}
