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

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterators;
import groovy.lang.Closure;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.file.FileTree;
import org.gradle.api.internal.file.collections.DirectoryFileTree;
import org.gradle.api.internal.file.collections.FileBackedDirectoryFileTree;
import org.gradle.api.internal.file.collections.FileCollectionResolveContext;
import org.gradle.api.internal.file.collections.ResolvableFileCollectionResolveContext;
import org.gradle.api.internal.provider.AbstractProviderWithValue;
import org.gradle.api.internal.tasks.AbstractTaskDependency;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.api.provider.Provider;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.internal.Cast;
import org.gradle.util.CollectionUtils;
import org.gradle.util.GUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public abstract class AbstractFileCollection implements FileCollectionInternal {
    /**
     * Returns the display name of this file collection. Used in log and error messages.
     *
     * @return the display name
     */
    public abstract String getDisplayName();

    @Override
    public String toString() {
        return getDisplayName();
    }

    // This is final - use {@link TaskDependencyContainer#visitDependencies} to provide the dependencies instead.
    @Override
    public final TaskDependency getBuildDependencies() {
        return new AbstractTaskDependency() {
            @Override
            public String toString() {
                return "Dependencies of " + getDisplayName();
            }

            @Override
            public void visitDependencies(TaskDependencyResolveContext context) {
                context.add(AbstractFileCollection.this);
            }
        };
    }

    @Override
    public void visitDependencies(TaskDependencyResolveContext context) {
        // Assume no dependencies
    }

    @Override
    public File getSingleFile() throws IllegalStateException {
        Iterator<File> iterator = iterator();
        if (!iterator.hasNext()) {
            throw new IllegalStateException(String.format("Expected %s to contain exactly one file, however, it contains no files.", getDisplayName()));
        }
        File singleFile = iterator.next();
        if (iterator.hasNext()) {
            throw new IllegalStateException(String.format("Expected %s to contain exactly one file, however, it contains more than one file.", getDisplayName()));
        }
        return singleFile;
    }

    @Override
    public Iterator<File> iterator() {
        return getFiles().iterator();
    }

    @Override
    public String getAsPath() {
        return GUtil.asPath(this);
    }

    @Override
    public boolean contains(File file) {
        return getFiles().contains(file);
    }

    @Override
    public FileCollection plus(FileCollection collection) {
        return new UnionFileCollection(this, collection);
    }

    @Override
    public Provider<Set<FileSystemLocation>> getElements() {
        return new AbstractProviderWithValue<Set<FileSystemLocation>>() {
            @Override
            public Class<Set<FileSystemLocation>> getType() {
                return Cast.uncheckedCast(Set.class);
            }

            @Override
            public boolean maybeVisitBuildDependencies(TaskDependencyResolveContext context) {
                // TODO - visit dependencies of this collection instead
                context.add(AbstractFileCollection.this.getBuildDependencies());
                return true;
            }

            @Override
            public Set<FileSystemLocation> get() {
                // TODO - visit the contents of this collection instead.
                // This is just a super simple implementation for now
                Set<File> files = getFiles();
                ImmutableSet.Builder<FileSystemLocation> builder = ImmutableSet.builderWithExpectedSize(files.size());
                for (File file : files) {
                    builder.add(new DefaultFileSystemLocation(file));
                }
                return builder.build();
            }
        };
    }

    @Override
    public FileCollection minus(final FileCollection collection) {
        return new AbstractFileCollection() {
            @Override
            public String getDisplayName() {
                return AbstractFileCollection.this.getDisplayName();
            }

            @Override
            public void visitDependencies(TaskDependencyResolveContext context) {
                AbstractFileCollection.this.visitDependencies(context);
            }

            @Override
            public Set<File> getFiles() {
                Set<File> files = new LinkedHashSet<File>(AbstractFileCollection.this.getFiles());
                files.removeAll(collection.getFiles());
                return files;
            }

            @Override
            public boolean contains(File file) {
                return AbstractFileCollection.this.contains(file) && !collection.contains(file);
            }
        };
    }

    @Override
    public void addToAntBuilder(Object builder, String nodeName, AntType type) {
        if (type == AntType.ResourceCollection) {
            addAsResourceCollection(builder, nodeName);
        } else if (type == AntType.FileSet) {
            addAsFileSet(builder, nodeName);
        } else {
            addAsMatchingTask(builder, nodeName);
        }
    }

    protected void addAsMatchingTask(Object builder, String nodeName) {
        new AntFileCollectionMatchingTaskBuilder(getAsFileTrees()).addToAntBuilder(builder, nodeName);
    }

    protected void addAsFileSet(Object builder, String nodeName) {
        new AntFileSetBuilder(getAsFileTrees()).addToAntBuilder(builder, nodeName);
    }

    protected void addAsResourceCollection(Object builder, String nodeName) {
        new AntFileCollectionBuilder(this).addToAntBuilder(builder, nodeName);
    }

    /**
     * Returns this collection as a set of {@link DirectoryFileTree} instances.
     */
    protected Collection<DirectoryFileTree> getAsFileTrees() {
        List<DirectoryFileTree> fileTrees = new ArrayList<DirectoryFileTree>();
        for (File file : this) {
            if (file.isFile()) {
                fileTrees.add(new FileBackedDirectoryFileTree(file));
            }
        }
        return fileTrees;
    }

    @Override
    public Object addToAntBuilder(Object node, String childNodeName) {
        addToAntBuilder(node, childNodeName, AntType.ResourceCollection);
        return this;
    }

    @Override
    public boolean isEmpty() {
        return getFiles().isEmpty();
    }

    @Override
    public FileTree getAsFileTree() {
        return new CompositeFileTree() {
            @Override
            public void visitContents(FileCollectionResolveContext context) {
                ResolvableFileCollectionResolveContext nested = context.newContext();
                nested.add(AbstractFileCollection.this);
                context.addAll(nested.resolveAsFileTrees());
            }

            @Override
            public void visitDependencies(TaskDependencyResolveContext context) {
                context.add(AbstractFileCollection.this);
            }

            @Override
            public String getDisplayName() {
                return AbstractFileCollection.this.getDisplayName();
            }
        };
    }

    @Override
    public FileCollection filter(Closure filterClosure) {
        return filter(Specs.convertClosureToSpec(filterClosure));
    }

    @Override
    public FileCollection filter(final Spec<? super File> filterSpec) {
        final Predicate<File> predicate = filterSpec::isSatisfiedBy;
        return new AbstractFileCollection() {
            @Override
            public String getDisplayName() {
                return AbstractFileCollection.this.getDisplayName();
            }

            @Override
            public void visitDependencies(TaskDependencyResolveContext context) {
                AbstractFileCollection.this.visitDependencies(context);
            }

            @Override
            public Set<File> getFiles() {
                return CollectionUtils.filter(AbstractFileCollection.this, new LinkedHashSet<>(), filterSpec);
            }

            @Override
            public boolean contains(File file) {
                return AbstractFileCollection.this.contains(file) && predicate.apply(file);
            }

            @Override
            public Iterator<File> iterator() {
                return Iterators.filter(AbstractFileCollection.this.iterator(), predicate);
            }
        };
    }

    @Override
    public void visitStructure(FileCollectionStructureVisitor visitor) {
        if (visitor.prepareForVisit(OTHER) != FileCollectionStructureVisitor.VisitType.NoContents) {
            visitor.visitCollection(OTHER, this);
        }
    }
}
