/*
 * Copyright 2019 the original author or authors.
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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.ConfigurableFileTree;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.internal.file.collections.DefaultConfigurableFileCollection;
import org.gradle.api.internal.file.collections.DefaultConfigurableFileTree;
import org.gradle.api.internal.file.collections.DirectoryFileTreeFactory;
import org.gradle.api.internal.file.collections.FileCollectionAdapter;
import org.gradle.api.internal.file.collections.FileCollectionResolveContext;
import org.gradle.api.internal.file.collections.MinimalFileSet;
import org.gradle.api.internal.file.collections.UnpackingVisitor;
import org.gradle.api.internal.tasks.TaskDependencyFactory;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.internal.Factory;
import org.gradle.internal.file.PathToFileResolver;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class DefaultFileCollectionFactory implements FileCollectionFactory {
    public static final String DEFAULT_COLLECTION_DISPLAY_NAME = "file collection";
    public static final String DEFAULT_TREE_DISPLAY_NAME = "file tree";
    private static final EmptyFileCollection EMPTY = new EmptyFileCollection(DEFAULT_COLLECTION_DISPLAY_NAME);
    private final PathToFileResolver fileResolver;
    private final TaskDependencyFactory taskDependencyFactory;
    private final DirectoryFileTreeFactory directoryFileTreeFactory;
    private final Factory<PatternSet> patternSetFactory;

    public DefaultFileCollectionFactory(PathToFileResolver fileResolver, TaskDependencyFactory taskDependencyFactory, DirectoryFileTreeFactory directoryFileTreeFactory, Factory<PatternSet> patternSetFactory) {
        this.fileResolver = fileResolver;
        this.taskDependencyFactory = taskDependencyFactory;
        this.directoryFileTreeFactory = directoryFileTreeFactory;
        this.patternSetFactory = patternSetFactory;
    }

    @Override
    public FileCollectionFactory withResolver(PathToFileResolver fileResolver) {
        if (fileResolver == this.fileResolver) {
            return this;
        }
        return new DefaultFileCollectionFactory(fileResolver, taskDependencyFactory, directoryFileTreeFactory, patternSetFactory);
    }

    @Override
    public ConfigurableFileCollection configurableFiles() {
        return new DefaultConfigurableFileCollection(null, fileResolver, taskDependencyFactory, Collections.emptyList());
    }

    @Override
    public ConfigurableFileCollection configurableFiles(String displayName) {
        return new DefaultConfigurableFileCollection(displayName, fileResolver, taskDependencyFactory, Collections.emptyList());
    }

    @Override
    public ConfigurableFileTree fileTree() {
        return new DefaultConfigurableFileTree(fileResolver, patternSetFactory, taskDependencyFactory, directoryFileTreeFactory);
    }

    @Override
    public FileCollectionInternal create(final TaskDependency builtBy, MinimalFileSet contents) {
        return new FileCollectionAdapter(contents) {
            @Override
            public void visitDependencies(TaskDependencyResolveContext context) {
                super.visitDependencies(context);
                context.add(builtBy);
            }
        };
    }

    @Override
    public FileCollectionInternal create(MinimalFileSet contents) {
        return new FileCollectionAdapter(contents);
    }

    @Override
    public FileCollectionInternal resolving(String displayName, List<?> sources) {
        return new ResolvingFileCollection(displayName, fileResolver, sources);
    }

    @Override
    public FileCollectionInternal resolving(String displayName, Object... sources) {
        return resolving(displayName, ImmutableList.copyOf(sources));
    }

    @Override
    public FileCollectionInternal resolving(Object... sources) {
        if (sources.length == 0) {
            return empty();
        }
        if (sources.length == 1 && sources[0] instanceof FileCollectionInternal) {
            return (FileCollectionInternal) sources[0];
        }
        return resolving(DEFAULT_COLLECTION_DISPLAY_NAME, sources);
    }

    @Override
    public FileCollectionInternal empty(String displayName) {
        return new EmptyFileCollection(displayName);
    }

    @Override
    public FileCollectionInternal empty() {
        return EMPTY;
    }

    @Override
    public FileCollectionInternal fixed(File... files) {
        if (files.length == 0) {
            return empty();
        }
        return fixed(DEFAULT_COLLECTION_DISPLAY_NAME, files);
    }

    @Override
    public FileCollectionInternal fixed(String displayName, File... files) {
        if (files.length == 0) {
            return new EmptyFileCollection(displayName);
        }
        return new FixedFileCollection(displayName, ImmutableSet.copyOf(files));
    }

    @Override
    public FileCollectionInternal fixed(Collection<File> files) {
        if (files.isEmpty()) {
            return empty();
        }
        return fixed(DEFAULT_COLLECTION_DISPLAY_NAME, files);
    }

    @Override
    public FileCollectionInternal fixed(final String displayName, Collection<File> files) {
        if (files.isEmpty()) {
            return new EmptyFileCollection(displayName);
        }
        return new FixedFileCollection(displayName, ImmutableSet.copyOf(files));
    }

    private static final class EmptyFileCollection extends AbstractFileCollection {
        private final String displayName;

        public EmptyFileCollection(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String getDisplayName() {
            return displayName;
        }

        @Override
        public Set<File> getFiles() {
            return ImmutableSet.of();
        }

        @Override
        public void visitStructure(FileCollectionStructureVisitor visitor) {
        }

        @Override
        public FileTree getAsFileTree() {
            return new EmptyFileTree();
        }
    }

    private static final class EmptyFileTree extends AbstractFileTree {
        @Override
        public String getDisplayName() {
            return DEFAULT_TREE_DISPLAY_NAME;
        }

        @Override
        public FileTree visit(FileVisitor visitor) {
            return this;
        }

        @Override
        public void visitStructure(FileCollectionStructureVisitor visitor) {
        }
    }

    private static final class FixedFileCollection extends AbstractFileCollection {
        private final String displayName;
        private final ImmutableSet<File> files;

        public FixedFileCollection(String displayName, ImmutableSet<File> files) {
            this.displayName = displayName;
            this.files = files;
        }

        @Override
        public String getDisplayName() {
            return displayName;
        }

        @Override
        public Set<File> getFiles() {
            return files;
        }
    }

    private static final class ResolvingFileCollection extends CompositeFileCollection {
        private final String displayName;
        private final PathToFileResolver resolver;
        private final List<?> paths;

        public ResolvingFileCollection(String displayName, PathToFileResolver resolver, List<?> paths) {
            this.displayName = displayName;
            this.resolver = resolver;
            this.paths = paths;
        }

        @Override
        public String getDisplayName() {
            return displayName;
        }

        @Override
        public void visitContents(FileCollectionResolveContext context) {
            UnpackingVisitor nested = new UnpackingVisitor(context, resolver);
            nested.add(paths);
        }
    }
}
