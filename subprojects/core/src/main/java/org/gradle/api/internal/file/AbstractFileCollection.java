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
import com.google.common.collect.Iterators;
import groovy.lang.Closure;
import org.apache.commons.lang.StringUtils;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.internal.file.collections.DirectoryFileTree;
import org.gradle.api.internal.file.collections.FileBackedDirectoryFileTree;
import org.gradle.api.internal.file.collections.FileCollectionResolveContext;
import org.gradle.api.internal.file.collections.ResolvableFileCollectionResolveContext;
import org.gradle.api.internal.tasks.TaskDependencies;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.util.CollectionUtils;
import org.gradle.util.DeprecationLogger;
import org.gradle.util.GUtil;

import javax.annotation.Nullable;
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
    public FileCollection minus(final FileCollection collection) {
        return new AbstractFileCollection() {
            @Override
            public String getDisplayName() {
                return AbstractFileCollection.this.getDisplayName();
            }

            @Override
            public TaskDependency getBuildDependencies() {
                return AbstractFileCollection.this.getBuildDependencies();
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
    public FileCollection add(FileCollection collection) throws UnsupportedOperationException {
        throw new UnsupportedOperationException(String.format("%s does not allow modification.", getCapDisplayName()));
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

    @Deprecated
    @Override
    public Object asType(Class<?> type) throws UnsupportedOperationException {
        if (type.isAssignableFrom(Object[].class)) {
            return getFiles().toArray();
        }
        if (type.isAssignableFrom(File[].class)) {
            DeprecationLogger.nagUserOfDeprecatedThing("Do not cast FileCollection to File[].");
            Set<File> files = getFiles();
            return files.toArray(new File[0]);
        }
        if (type.isAssignableFrom(File.class)) {
            DeprecationLogger.nagUserOfDeprecatedThing("Do not cast FileCollection to File.", "Call getSingleFile() instead.");
            return getSingleFile();
        }
        if (type.isAssignableFrom(FileCollection.class)) {
            return this;
        }
        if (type.isAssignableFrom(FileTree.class)) {
            DeprecationLogger.nagUserOfDeprecatedThing("Do not cast FileCollection to FileTree.", "Call getAsFileTree() instead.");
            return getAsFileTree();
        }
        return DefaultGroovyMethods.asType(this, type);
    }

    @Override
    public TaskDependency getBuildDependencies() {
        DeprecationLogger.nagUserOfDiscontinuedMethod("AbstractFileCollection.getBuildDependencies()", "Do not extend AbstractFileCollection. Use Project.files() instead.", getClass().getName() + " extends AbstractFileCollection.");
        return TaskDependencies.EMPTY;
    }

    @Override
    public FileTree getAsFileTree() {
        return new CompositeFileTree() {
            @Override
            public void visitContents(FileCollectionResolveContext context) {
                ResolvableFileCollectionResolveContext nested = context.newContext();
                nested.add(AbstractFileCollection.this);
                context.add(nested.resolveAsFileTrees());
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
        final Predicate<File> predicate = new Predicate<File>() {
            @Override
            public boolean apply(@Nullable File input) {
                return filterSpec.isSatisfiedBy(input);
            }
        };
        return new AbstractFileCollection() {
            @Override
            public String getDisplayName() {
                return AbstractFileCollection.this.getDisplayName();
            }

            @Override
            public TaskDependency getBuildDependencies() {
                return AbstractFileCollection.this.getBuildDependencies();
            }

            @Override
            public Set<File> getFiles() {
                return CollectionUtils.filter(AbstractFileCollection.this, new LinkedHashSet<File>(), filterSpec);
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

    protected String getCapDisplayName() {
        return StringUtils.capitalize(getDisplayName());
    }

    @Override
    public void visitRootElements(FileCollectionVisitor visitor) {
        visitor.visitCollection(this);
    }

    @Override
    public void registerWatchPoints(FileSystemSubset.Builder builder) {
        for (File file : this) {
            builder.add(file);
        }
    }
}
