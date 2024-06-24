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
import org.gradle.api.Action;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.ConfigurableFileTree;
import org.gradle.api.internal.file.collections.DefaultConfigurableFileCollection;
import org.gradle.api.internal.file.collections.DefaultConfigurableFileTree;
import org.gradle.api.internal.file.collections.DirectoryFileTreeFactory;
import org.gradle.api.internal.file.collections.FileCollectionAdapter;
import org.gradle.api.internal.file.collections.FileCollectionObservationListener;
import org.gradle.api.internal.file.collections.FileTreeAdapter;
import org.gradle.api.internal.file.collections.GeneratedSingletonFileTree;
import org.gradle.api.internal.file.collections.MinimalFileSet;
import org.gradle.api.internal.file.collections.MinimalFileTree;
import org.gradle.api.internal.file.collections.UnpackingVisitor;
import org.gradle.api.internal.provider.PropertyHost;
import org.gradle.api.internal.provider.ProviderResolutionStrategy;
import org.gradle.api.internal.tasks.TaskDependencyFactory;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.internal.Factory;
import org.gradle.internal.file.PathToFileResolver;
import org.gradle.internal.logging.text.TreeFormatter;
import org.gradle.internal.nativeintegration.filesystem.FileSystem;

import java.io.File;
import java.io.OutputStream;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

public class DefaultFileCollectionFactory implements FileCollectionFactory {
    private final PathToFileResolver fileResolver;
    private final TaskDependencyFactory taskDependencyFactory;
    private final DirectoryFileTreeFactory directoryFileTreeFactory;
    private final Factory<PatternSet> patternSetFactory;
    private final PropertyHost propertyHost;
    private final FileSystem fileSystem;
    private final FileCollectionObservationListener listener;

    public DefaultFileCollectionFactory(
        PathToFileResolver fileResolver,
        TaskDependencyFactory taskDependencyFactory,
        DirectoryFileTreeFactory directoryFileTreeFactory,
        Factory<PatternSet> patternSetFactory,
        PropertyHost propertyHost,
        FileSystem fileSystem
    ) {
        this(fileResolver, taskDependencyFactory, directoryFileTreeFactory, patternSetFactory, propertyHost, fileSystem, fileCollection -> {});
    }

    private DefaultFileCollectionFactory(
        PathToFileResolver fileResolver,
        TaskDependencyFactory taskDependencyFactory,
        DirectoryFileTreeFactory directoryFileTreeFactory,
        Factory<PatternSet> patternSetFactory,
        PropertyHost propertyHost,
        FileSystem fileSystem,
        FileCollectionObservationListener listener
    ) {
        this.fileResolver = fileResolver;
        this.taskDependencyFactory = taskDependencyFactory;
        this.directoryFileTreeFactory = directoryFileTreeFactory;
        this.patternSetFactory = patternSetFactory;
        this.propertyHost = propertyHost;
        this.fileSystem = fileSystem;
        this.listener = listener;
    }

    @Override
    public FileCollectionFactory withResolver(PathToFileResolver fileResolver) {
        if (fileResolver == this.fileResolver) {
            return this;
        }
        return new DefaultFileCollectionFactory(fileResolver, taskDependencyFactory, directoryFileTreeFactory, patternSetFactory, propertyHost, fileSystem, listener);
    }

    @Override
    public FileCollectionFactory forChildScope(FileCollectionObservationListener listener) {
        return new DefaultFileCollectionFactory(fileResolver, taskDependencyFactory, directoryFileTreeFactory, patternSetFactory, propertyHost, fileSystem, listener);
    }

    @Override
    public FileCollectionFactory forChildScope(PathToFileResolver fileResolver, TaskDependencyFactory taskDependencyFactory, PropertyHost propertyHost) {
        return new DefaultFileCollectionFactory(fileResolver, taskDependencyFactory, directoryFileTreeFactory, patternSetFactory, propertyHost, fileSystem, listener);
    }

    @Override
    public ConfigurableFileCollection configurableFiles() {
        return new DefaultConfigurableFileCollection(null, fileResolver, taskDependencyFactory, patternSetFactory, propertyHost);
    }

    @Override
    public ConfigurableFileCollection configurableFiles(String displayName) {
        return new DefaultConfigurableFileCollection(displayName, fileResolver, taskDependencyFactory, patternSetFactory, propertyHost);
    }

    @Override
    public ConfigurableFileTree fileTree() {
        return new DefaultConfigurableFileTree(fileResolver, listener, patternSetFactory, taskDependencyFactory, directoryFileTreeFactory);
    }

    @Override
    public FileTreeInternal treeOf(List<? extends FileTreeInternal> fileTrees) {
        if (fileTrees.isEmpty()) {
            return FileCollectionFactory.emptyTree();
        } else if (fileTrees.size() == 1) {
            return fileTrees.get(0);
        } else {
            return new DefaultCompositeFileTree(taskDependencyFactory, patternSetFactory, ImmutableList.copyOf(fileTrees));
        }
    }

    @Override
    public FileTreeInternal treeOf(MinimalFileTree tree) {
        return new FileTreeAdapter(tree, listener, taskDependencyFactory, patternSetFactory);
    }

    @Override
    public FileCollectionInternal create(TaskDependency builtBy, MinimalFileSet contents) {
        return new FileCollectionAdapter(contents, taskDependencyFactory, patternSetFactory) {
            @Override
            public void visitDependencies(TaskDependencyResolveContext context) {
                super.visitDependencies(context);
                context.add(builtBy);
            }
        };
    }

    @Override
    public FileCollectionInternal create(MinimalFileSet contents, Consumer<? super TaskDependencyResolveContext> visitTaskDependencies) {
        return new FileCollectionAdapter(contents, taskDependencyFactory, patternSetFactory) {
            @Override
            public void visitDependencies(TaskDependencyResolveContext context) {
                super.visitDependencies(context);
                visitTaskDependencies.accept(context);
            }
        };
    }

    @Override
    public FileCollectionInternal create(MinimalFileSet contents) {
        return new FileCollectionAdapter(contents, taskDependencyFactory, patternSetFactory);
    }

    @Override
    public FileCollectionInternal resolving(String displayName, Object sources) {
        return resolving(displayName, ProviderResolutionStrategy.REQUIRE_PRESENT, sources);
    }

    @Override
    public FileCollectionInternal resolvingLeniently(String displayName, Object sources) {
        return resolving(displayName, ProviderResolutionStrategy.ALLOW_ABSENT, sources);
    }

    private FileCollectionInternal resolving(String displayName, ProviderResolutionStrategy providerResolutionStrategy, Object sources) {
        if (isEmptyArray(sources)) {
            return FileCollectionFactory.empty(displayName);
        }
        return new ResolvingFileCollection(displayName, fileResolver, taskDependencyFactory, patternSetFactory, providerResolutionStrategy, sources);
    }

    @Override
    public FileCollectionInternal resolving(Object sources) {
        return resolving(ProviderResolutionStrategy.REQUIRE_PRESENT, sources);
    }

    @Override
    public FileCollectionInternal resolvingLeniently(Object sources) {
        return resolving(ProviderResolutionStrategy.ALLOW_ABSENT, sources);
    }

    private FileCollectionInternal resolving(ProviderResolutionStrategy providerResolutionStrategy, Object sources) {
        if (sources instanceof FileCollectionInternal) {
            return (FileCollectionInternal) sources;
        }
        if (isEmptyArray(sources)) {
            return FileCollectionFactory.empty();
        }
        return resolving(FileCollectionInternal.DEFAULT_COLLECTION_DISPLAY_NAME, providerResolutionStrategy, sources);
    }

    @Override
    public FileCollectionInternal fixed(File... files) {
        if (files.length == 0) {
            return FileCollectionFactory.empty();
        }
        return fixed(FileCollectionInternal.DEFAULT_COLLECTION_DISPLAY_NAME, files);
    }

    @Override
    public FileCollectionInternal fixed(String displayName, File... files) {
        if (files.length == 0) {
            return new EmptyFileCollection(displayName);
        }
        return new FixedFileCollection(displayName, taskDependencyFactory, patternSetFactory, ImmutableSet.copyOf(files));
    }

    @Override
    public FileCollectionInternal fixed(Collection<File> files) {
        if (files.isEmpty()) {
            return FileCollectionFactory.empty();
        }
        return fixed(FileCollectionInternal.DEFAULT_COLLECTION_DISPLAY_NAME, files);
    }

    @Override
    public FileCollectionInternal fixed(final String displayName, Collection<File> files) {
        if (files.isEmpty()) {
            return new EmptyFileCollection(displayName);
        }
        return new FixedFileCollection(displayName, taskDependencyFactory, patternSetFactory, ImmutableSet.copyOf(files));
    }

    @Override
    public FileTreeInternal generated(Factory<File> tmpDir, String fileName, Action<File> fileGenerationListener, Action<OutputStream> contentWriter) {
        return new FileTreeAdapter(new GeneratedSingletonFileTree(tmpDir, fileName, fileGenerationListener, contentWriter, fileSystem), listener, taskDependencyFactory, patternSetFactory);
    }

    private static final class FixedFileCollection extends AbstractOpaqueFileCollection {
        private final String displayName;
        private final ImmutableSet<File> files;

        public FixedFileCollection(String displayName, TaskDependencyFactory taskDependencyFactory, Factory<PatternSet> patternSetFactory, ImmutableSet<File> files) {
            super(taskDependencyFactory, patternSetFactory);
            this.displayName = displayName;
            this.files = files;
        }

        @Override
        public String getDisplayName() {
            return displayName;
        }

        @Override
        protected Set<File> getIntrinsicFiles() {
            return files;
        }

        @Override
        public Optional<FileCollectionExecutionTimeValue> calculateExecutionTimeValue() {
            return Optional.of(new FixedExecutionTimeValue(displayName, files));
        }

        private static class FixedExecutionTimeValue implements FileCollectionExecutionTimeValue {
            private final String displayName;
            private final ImmutableSet<File> files;

            public FixedExecutionTimeValue(String displayName, ImmutableSet<File> files) {
                this.displayName = displayName;
                this.files = files;
            }

            @Override
            public FileCollectionInternal toFileCollection(FileCollectionFactory fileCollectionFactory) {
                return fileCollectionFactory.fixed(displayName, files);
            }
        }
    }

    private static class ResolvingFileCollection extends CompositeFileCollection {
        private final String displayName;
        private final PathToFileResolver resolver;
        private final Object source;
        private final ProviderResolutionStrategy providerResolutionStrategy;

        public ResolvingFileCollection(String displayName, PathToFileResolver resolver, TaskDependencyFactory taskDependencyFactory, Factory<PatternSet> patternSetFactory, ProviderResolutionStrategy providerResolutionStrategy, Object source) {
            super(taskDependencyFactory, patternSetFactory);
            this.displayName = displayName;
            this.resolver = resolver;
            this.source = source;
            this.providerResolutionStrategy = providerResolutionStrategy;
        }

        @Override
        public String getDisplayName() {
            return displayName;
        }

        @Override
        protected void visitChildren(Consumer<FileCollectionInternal> visitor) {
            UnpackingVisitor nested = new UnpackingVisitor(visitor, resolver, taskDependencyFactory, patternSetFactory, providerResolutionStrategy, true);
            nested.add(source);
        }

        @Override
        protected void appendContents(TreeFormatter formatter) {
            formatter.node("source");
            formatter.startChildren();
            appendItem(formatter, source);
            formatter.endChildren();
        }

        private void appendItem(TreeFormatter formatter, Object item) {
            if (item instanceof FileCollectionInternal) {
                ((FileCollectionInternal) item).describeContents(formatter);
            } else if (item instanceof ArrayList) {
                for (Object child : (List) item) {
                    appendItem(formatter, child);
                }
            } else {
                formatter.node(item + " (class: " + item.getClass().getName() + ")");
            }
        }
    }

    private boolean isEmptyArray(Object sources) {
        return sources.getClass().isArray() && Array.getLength(sources) == 0;
    }
}
