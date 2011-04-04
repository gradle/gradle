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

import org.gradle.api.Buildable;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.internal.file.collections.*;
import org.gradle.api.internal.tasks.AbstractTaskDependency;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.TaskDependency;

import java.io.File;
import java.util.*;

/**
 * A {@link org.gradle.api.file.FileCollection} which contains the union of the given source collections. Maintains file
 * ordering.
 */
public abstract class CompositeFileCollection extends AbstractFileCollection implements FileCollectionContainer {
    public Set<File> getFiles() {
        Set<File> files = new LinkedHashSet<File>();
        for (FileCollection collection : getSourceCollections()) {
            files.addAll(collection.getFiles());
        }
        return files;
    }

    @Override
    public boolean contains(File file) {
        for (FileCollection collection : getSourceCollections()) {
            if (collection.contains(file)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isEmpty() {
        for (FileCollection collection : getSourceCollections()) {
            if (!collection.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected void addAsResourceCollection(Object builder, String nodeName) {
        for (FileCollection fileCollection : getSourceCollections()) {
            fileCollection.addToAntBuilder(builder, nodeName, AntType.ResourceCollection);
        }
    }

    @Override
    protected Collection<DirectoryFileTree> getAsFileTrees() {
        List<DirectoryFileTree> fileTree = new ArrayList<DirectoryFileTree>();
        for (FileCollection source : getSourceCollections()) {
            AbstractFileCollection collection = (AbstractFileCollection) source;
            fileTree.addAll(collection.getAsFileTrees());
        }
        return fileTree;
    }

    @Override
    public FileTree getAsFileTree() {
        return new CompositeFileTree() {
            @Override
            public void resolve(FileCollectionResolveContext context) {
                ResolvableFileCollectionResolveContext nested = context.newContext();
                CompositeFileCollection.this.resolve(nested);
                context.add(nested.resolveAsFileTrees());
            }

            @Override
            public String getDisplayName() {
                return CompositeFileCollection.this.getDisplayName();
            }
        };
    }

    @Override
    public FileCollection filter(final Spec<? super File> filterSpec) {
        return new CompositeFileCollection() {
            @Override
            public void resolve(FileCollectionResolveContext context) {
                for (FileCollection collection : CompositeFileCollection.this.getSourceCollections()) {
                    context.add(collection.filter(filterSpec));
                }
            }

            @Override
            public String getDisplayName() {
                return CompositeFileCollection.this.getDisplayName();
            }

            @Override
            public TaskDependency getBuildDependencies() {
                return CompositeFileCollection.this.getBuildDependencies();
            }
        };
    }

    @Override
    public TaskDependency getBuildDependencies() {
        return new AbstractTaskDependency() {
            public void resolve(TaskDependencyResolveContext context) {
                addDependencies(context);
            }
        };
    }

    /**
     * Allows subclasses to add additional dependencies
     * @param context The context to add dependencies to.
     */
    protected void addDependencies(TaskDependencyResolveContext context) {
        BuildDependenciesOnlyFileCollectionResolveContext fileContext = new BuildDependenciesOnlyFileCollectionResolveContext();
        resolve(fileContext);
        for (Buildable buildable : fileContext.resolveAsBuildables()) {
            context.add(buildable);
        }
    }

    protected List<? extends FileCollection> getSourceCollections() {
        DefaultFileCollectionResolveContext context = new DefaultFileCollectionResolveContext();
        resolve(context);
        return context.resolveAsFileCollections();
    }

    public abstract void resolve(FileCollectionResolveContext context);
}
