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

import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.internal.file.collections.BuildDependenciesOnlyFileCollectionResolveContext;
import org.gradle.api.internal.file.collections.DefaultFileCollectionResolveContext;
import org.gradle.api.internal.file.collections.DirectoryFileTree;
import org.gradle.api.internal.file.collections.FileCollectionContainer;
import org.gradle.api.internal.file.collections.FileCollectionResolveContext;
import org.gradle.api.internal.file.collections.ResolvableFileCollectionResolveContext;
import org.gradle.api.internal.tasks.AbstractTaskDependency;
import org.gradle.api.internal.tasks.TaskDependencyContainer;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.TaskDependency;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * A {@link org.gradle.api.file.FileCollection} that contains the union of zero or more file collections. Maintains file ordering.
 *
 * <p>The source file collections are calculated from the result of calling {@link #visitContents(FileCollectionResolveContext)}, and may be lazily created.
 * This also means that the source collections can be created using any representation supported by {@link FileCollectionResolveContext}.
 * </p>
 *
 * <p>The dependencies of this collection are calculated from the result of calling {@link #visitDependencies(TaskDependencyResolveContext)}.</p>
 */
public abstract class CompositeFileCollection extends AbstractFileCollection implements FileCollectionContainer, TaskDependencyContainer {
    public Set<File> getFiles() {
        // Gather each of the backing Sets first, so we can set the initial capacity of the LinkedHashSet
        List<Set<File>> fileSets = new LinkedList<Set<File>>();
        int fileCount = 0;
        for (FileCollection collection : getSourceCollections()) {
            Set<File> files = collection.getFiles();
            fileCount += files.size();
            fileSets.add(files);
        }
        Set<File> allFiles = new LinkedHashSet<File>(fileCount);
        for (Set<File> fileSet : fileSets) {
            allFiles.addAll(fileSet);
        }
        return allFiles;
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
            public void visitContents(FileCollectionResolveContext context) {
                ResolvableFileCollectionResolveContext nested = context.newContext();
                CompositeFileCollection.this.visitContents(nested);
                context.add(nested.resolveAsFileTrees());
            }

            @Override
            public void visitDependencies(TaskDependencyResolveContext context) {
                CompositeFileCollection.this.visitDependencies(context);
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
            public void visitContents(FileCollectionResolveContext context) {
                for (FileCollection collection : CompositeFileCollection.this.getSourceCollections()) {
                    context.add(collection.filter(filterSpec));
                }
            }

            @Override
            public void visitDependencies(TaskDependencyResolveContext context) {
                CompositeFileCollection.this.visitDependencies(context);
            }

            @Override
            public String getDisplayName() {
                return CompositeFileCollection.this.getDisplayName();
            }
        };
    }

    // This is final - use {@link TaskDependencyContainer#resolve} to provide the dependencies instead.
    @Override
    public final TaskDependency getBuildDependencies() {
        return new AbstractTaskDependency() {
            @Override
            public String toString() {
                return CompositeFileCollection.this.toString() + " dependencies";
            }

            public void visitDependencies(TaskDependencyResolveContext context) {
                CompositeFileCollection.this.visitDependencies(context);
            }
        };
    }

    @Override
    public void visitDependencies(TaskDependencyResolveContext context) {
        BuildDependenciesOnlyFileCollectionResolveContext fileContext = new BuildDependenciesOnlyFileCollectionResolveContext(context);
        visitContents(fileContext);
    }

    protected Collection<? extends FileCollectionInternal> getSourceCollections() {
        DefaultFileCollectionResolveContext context = new DefaultFileCollectionResolveContext(new IdentityFileResolver());
        visitContents(context);
        return context.resolveAsFileCollections();
    }

    @Override
    public void registerWatchPoints(FileSystemSubset.Builder builder) {
        for (FileCollectionInternal files : getSourceCollections()) {
            files.registerWatchPoints(builder);
        }
    }

    @Override
    public void visitRootElements(FileCollectionVisitor visitor) {
        for (FileCollectionInternal element : getSourceCollections()) {
            element.visitRootElements(visitor);
        }
    }
}
