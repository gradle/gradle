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

package org.gradle.api.internal.file.collections;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.CompositeFileCollection;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.api.internal.file.FileCollectionStructureVisitor;
import org.gradle.api.internal.file.FileTreeInternal;
import org.gradle.api.internal.file.UnionFileCollection;
import org.gradle.api.internal.provider.HasConfigurableValueInternal;
import org.gradle.api.internal.provider.PropertyHost;
import org.gradle.api.internal.provider.support.LazyGroovySupport;
import org.gradle.api.internal.tasks.DefaultTaskDependency;
import org.gradle.api.internal.tasks.TaskDependencyFactory;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.internal.Cast;
import org.gradle.internal.Factory;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.file.PathToFileResolver;
import org.gradle.internal.logging.text.TreeFormatter;
import org.gradle.internal.state.Managed;

import javax.annotation.Nullable;
import java.io.File;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A {@link org.gradle.api.file.FileCollection} which resolves a set of paths relative to a {@link org.gradle.api.internal.file.FileResolver}.
 */
public class DefaultConfigurableFileCollection extends CompositeFileCollection implements ConfigurableFileCollection, Managed, HasConfigurableValueInternal, LazyGroovySupport {
    public static final EmptyCollector EMPTY_COLLECTOR = new EmptyCollector();

    private enum State {
        Mutable, ImplicitFinalizeNextQuery, FinalizeNextQuery, Final
    }

    private final PathSet filesWrapper;
    private final String displayName;
    private final PathToFileResolver resolver;
    private final TaskDependencyFactory dependencyFactory;
    private final PropertyHost host;
    private final DefaultTaskDependency buildDependency;
    private State state = State.Mutable;
    private boolean disallowChanges;
    private boolean disallowUnsafeRead;
    private ValueCollector value = EMPTY_COLLECTOR;

    public DefaultConfigurableFileCollection(@Nullable String displayName, PathToFileResolver fileResolver, TaskDependencyFactory dependencyFactory, Factory<PatternSet> patternSetFactory, PropertyHost host) {
        super(dependencyFactory, patternSetFactory);
        this.displayName = displayName;
        this.resolver = fileResolver;
        this.dependencyFactory = dependencyFactory;
        this.host = host;
        filesWrapper = new PathSet();
        buildDependency = dependencyFactory.configurableDependency();
    }

    @Override
    public boolean isImmutable() {
        return false;
    }

    @Override
    public Class<?> publicType() {
        return ConfigurableFileCollection.class;
    }

    @Override
    public Object unpackState() {
        return getFiles();
    }

    @Override
    public void finalizeValue() {
        if (state == State.Final) {
            return;
        }
        if (disallowUnsafeRead) {
            String reason = host.beforeRead(null);
            if (reason != null) {
                throw new IllegalStateException("Cannot finalize the value for " + displayNameForThisCollection() + " because " + reason + ".");
            }
        }
        calculateFinalizedValue();
        state = State.Final;
        disallowChanges = true;
    }

    public boolean isFinalizing() {
        return state != State.Mutable;
    }

    @Override
    public void disallowChanges() {
        disallowChanges = true;
    }

    @Override
    public void finalizeValueOnRead() {
        if (state == State.Mutable || state == State.ImplicitFinalizeNextQuery) {
            state = State.FinalizeNextQuery;
        }
    }

    @Override
    public void implicitFinalizeValue() {
        if (state == State.Mutable) {
            state = State.ImplicitFinalizeNextQuery;
        }
    }

    public void disallowUnsafeRead() {
        disallowUnsafeRead = true;
        finalizeValueOnRead();
    }

    public int getFactoryId() {
        return ManagedFactories.ConfigurableFileCollectionManagedFactory.FACTORY_ID;
    }

    @Override
    public String getDisplayName() {
        return displayName == null ? "file collection" : displayName;
    }

    @Override
    protected void appendContents(TreeFormatter formatter) {
        if (displayName != null) {
            formatter.node("display name: " + displayName);
        }
        List<Object> paths = new ArrayList<>();
        value.collectSource(paths);
        if (!paths.isEmpty()) {
            formatter.node("contents");
            formatter.startChildren();
            for (Object path : paths) {
                if (path instanceof FileCollectionInternal) {
                    ((FileCollectionInternal) path).describeContents(formatter);
                } else {
                    formatter.node(path.toString());
                }
            }
            formatter.endChildren();
        }
    }

    @Override
    public Set<Object> getFrom() {
        return filesWrapper;
    }

    @Override
    public void setFromAnyValue(Object object) {
        // Currently we support just FileCollection for Groovy assign, so first try to cast to FileCollection
        FileCollectionInternal fileCollection = Cast.castNullable(FileCollectionInternal.class, Cast.castNullable(FileCollection.class, object));

        // Don't allow a += b or a = (a + b), this is not support
        fileCollection.visitStructure(new FileCollectionStructureVisitor() {
            @Override
            public boolean startVisit(FileCollectionInternal.Source source, FileCollectionInternal fileCollection) {
                if (DefaultConfigurableFileCollection.this == fileCollection) {
                    throw new UnsupportedOperationException("Self-referencing ConfigurableFileCollections are not supported. Use the from() method to add to a ConfigurableFileCollection.");
                }
                return true;
            }

            @Override
            public VisitType prepareForVisit(Source source) {
                return VisitType.NoContents;
            }

            @Override
            public void visitCollection(Source source, Iterable<File> contents) {
            }

            @Override
            public void visitFileTree(File root, PatternSet patterns, FileTreeInternal fileTree) {
            }

            @Override
            public void visitFileTreeBackedByFile(File file, FileTreeInternal fileTree, FileSystemMirroringFileTree sourceTree) {
            }
        });

        setFrom(Cast.castNullable(FileCollection.class, object));
    }

    @Override
    public FileCollection plus(FileCollection collection) {
        return new UnionFileCollection(taskDependencyFactory, this, (FileCollectionInternal) collection);
    }

    @Override
    public void setFrom(Iterable<?> path) {
        assertMutable();
        value = value.setFrom(this, resolver, patternSetFactory, dependencyFactory, host, path);
    }

    @Override
    public void setFrom(Object... paths) {
        assertMutable();
        value = paths.length > 0
            ? value.setFrom(this, resolver, patternSetFactory, dependencyFactory, host, paths)
            : EMPTY_COLLECTOR;
    }

    @Override
    public ConfigurableFileCollection from(Object... paths) {
        assertMutable();
        if (paths.length > 0) {
            value = value.plus(this, resolver, patternSetFactory, dependencyFactory, host, paths);
        }
        return this;
    }

    @Override
    public FileCollectionInternal replace(FileCollectionInternal original, Supplier<FileCollectionInternal> supplier) {
        if (original == this) {
            return supplier.get();
        }
        List<Object> newItems = value.replace(original, supplier);
        if (newItems == null) {
            return this;
        }
        DefaultConfigurableFileCollection newFiles = new DefaultConfigurableFileCollection(null, resolver, dependencyFactory, patternSetFactory, host);
        newFiles.from(newItems);
        return newFiles;
    }

    private void assertMutable() {
        if (state == State.Final && disallowChanges) {
            throw new IllegalStateException("The value for " + displayNameForThisCollection() + " is final and cannot be changed.");
        } else if (disallowChanges) {
            throw new IllegalStateException("The value for " + displayNameForThisCollection() + " cannot be changed.");
        } else if (state == State.Final) {
            throw new IllegalStateException("The value for " + displayNameForThisCollection() + " is final and cannot be changed.");
        }
    }

    private String displayNameForThisCollection() {
        return displayName == null ? "this file collection" : displayName;
    }

    @Override
    public ConfigurableFileCollection builtBy(Object... tasks) {
        buildDependency.add(tasks);
        return this;
    }

    @Override
    public Set<Object> getBuiltBy() {
        return buildDependency.getMutableValues();
    }

    @Override
    public ConfigurableFileCollection setBuiltBy(Iterable<?> tasks) {
        buildDependency.setValues(tasks);
        return this;
    }

    private void calculateFinalizedValue() {
        ImmutableList.Builder<FileCollectionInternal> builder = ImmutableList.builder();
        value.visitContents(child -> child.visitStructure(new FileCollectionStructureVisitor() {
            @Override
            public void visitCollection(Source source, Iterable<File> contents) {
                ImmutableSet<File> files = ImmutableSet.copyOf(contents);
                if (!files.isEmpty()) {
                    builder.add(new FileCollectionAdapter(new ListBackedFileSet(files), taskDependencyFactory, patternSetFactory));
                }
            }

            @Override
            public void visitFileTree(File root, PatternSet patterns, FileTreeInternal fileTree) {
                builder.add(fileTree);
            }

            @Override
            public void visitFileTreeBackedByFile(File file, FileTreeInternal fileTree, FileSystemMirroringFileTree sourceTree) {
                builder.add(fileTree);
            }
        }));
        value = new ResolvedItemsCollector(builder.build());
    }

    @Override
    protected void visitChildren(Consumer<FileCollectionInternal> visitor) {
        if (disallowUnsafeRead && state != State.Final) {
            String reason = host.beforeRead(null);
            if (reason != null) {
                throw new IllegalStateException("Cannot query the value for " + displayNameForThisCollection() + " because " + reason + ".");
            }
        }
        if (state == State.ImplicitFinalizeNextQuery) {
            calculateFinalizedValue();
            state = State.Final;
        } else if (state == State.FinalizeNextQuery) {
            calculateFinalizedValue();
            state = State.Final;
            disallowChanges = true;
        }
        value.visitContents(visitor);
    }

    @Override
    public void visitDependencies(TaskDependencyResolveContext context) {
        context.add(buildDependency);
        super.visitDependencies(context);
    }

    private interface ValueCollector {
        void collectSource(Collection<Object> dest);

        void visitContents(Consumer<FileCollectionInternal> visitor);

        boolean remove(Object source);

        ValueCollector setFrom(DefaultConfigurableFileCollection owner, PathToFileResolver resolver, Factory<PatternSet> patternSetFactory, TaskDependencyFactory taskDependencyFactory, PropertyHost propertyHost, Iterable<?> path);

        ValueCollector setFrom(DefaultConfigurableFileCollection owner, PathToFileResolver resolver, Factory<PatternSet> patternSetFactory, TaskDependencyFactory taskDependencyFactory, PropertyHost propertyHost, Object[] paths);

        ValueCollector plus(DefaultConfigurableFileCollection owner, PathToFileResolver resolver, Factory<PatternSet> patternSetFactory, TaskDependencyFactory taskDependencyFactory, PropertyHost propertyHost, Object... paths);

        @Nullable
        List<Object> replace(FileCollectionInternal original, Supplier<FileCollectionInternal> supplier);
    }

    private static class EmptyCollector implements ValueCollector {
        @Override
        public void collectSource(Collection<Object> dest) {
        }

        @Override
        public void visitContents(Consumer<FileCollectionInternal> visitor) {
        }

        @Override
        public boolean remove(Object source) {
            return false;
        }

        @Override
        public ValueCollector setFrom(DefaultConfigurableFileCollection owner, PathToFileResolver resolver, Factory<PatternSet> patternSetFactory, TaskDependencyFactory taskDependencyFactory, PropertyHost propertyHost, Iterable<?> path) {
            return new UnresolvedItemsCollector(owner, resolver, patternSetFactory, taskDependencyFactory, propertyHost, path);
        }

        @Override
        public ValueCollector setFrom(DefaultConfigurableFileCollection owner, PathToFileResolver resolver, Factory<PatternSet> patternSetFactory, TaskDependencyFactory taskDependencyFactory, PropertyHost propertyHost, Object[] paths) {
            return new UnresolvedItemsCollector(resolver, taskDependencyFactory, patternSetFactory, paths);
        }

        @Override
        public ValueCollector plus(DefaultConfigurableFileCollection owner, PathToFileResolver resolver, Factory<PatternSet> patternSetFactory, TaskDependencyFactory taskDependencyFactory, PropertyHost propertyHost, Object... paths) {
            return setFrom(owner, resolver, patternSetFactory, taskDependencyFactory, propertyHost, paths);
        }

        @Nullable
        @Override
        public List<Object> replace(FileCollectionInternal original, Supplier<FileCollectionInternal> supplier) {
            return null;
        }
    }

    private static class UnresolvedItemsCollector implements ValueCollector {
        private final PathToFileResolver resolver;
        private final Factory<PatternSet> patternSetFactory;
        private final TaskDependencyFactory taskDependencyFactory;
        private final Set<Object> items = new LinkedHashSet<>();

        public UnresolvedItemsCollector(DefaultConfigurableFileCollection owner, PathToFileResolver resolver, Factory<PatternSet> patternSetFactory, TaskDependencyFactory taskDependencyFactory, PropertyHost propertyHost, Iterable<?> item) {
            this.resolver = resolver;
            this.patternSetFactory = patternSetFactory;
            this.taskDependencyFactory = taskDependencyFactory;
            setFrom(owner, resolver, patternSetFactory, taskDependencyFactory, propertyHost, item);
        }

        public UnresolvedItemsCollector(PathToFileResolver resolver, TaskDependencyFactory taskDependencyFactory, Factory<PatternSet> patternSetFactory, Object[] item) {
            this.resolver = resolver;
            this.taskDependencyFactory = taskDependencyFactory;
            this.patternSetFactory = patternSetFactory;
            Collections.addAll(items, item);
        }

        @Override
        public void collectSource(Collection<Object> dest) {
            dest.addAll(items);
        }

        @Override
        public void visitContents(Consumer<FileCollectionInternal> visitor) {
            UnpackingVisitor nested = new UnpackingVisitor(visitor, resolver, taskDependencyFactory, patternSetFactory);
            for (Object item : items) {
                nested.add(item);
            }
        }

        @Override
        public boolean remove(Object source) {
            return items.remove(source);
        }

        @Override
        public ValueCollector setFrom(DefaultConfigurableFileCollection owner, PathToFileResolver resolver, Factory<PatternSet> patternSetFactory, TaskDependencyFactory taskDependencyFactory, PropertyHost propertyHost, Iterable<?> path) {
            ImmutableList<Object> oldItems = ImmutableList.copyOf(items);
            items.clear();
            addItem(owner, resolver, patternSetFactory, taskDependencyFactory, propertyHost, path, oldItems);
            return this;
        }

        @Override
        public ValueCollector setFrom(DefaultConfigurableFileCollection owner, PathToFileResolver resolver, Factory<PatternSet> patternSetFactory, TaskDependencyFactory taskDependencyFactory, PropertyHost propertyHost, Object[] paths) {
            ImmutableList<Object> oldItems = ImmutableList.copyOf(items);
            items.clear();
            for (Object path : paths) {
                addItem(owner, resolver, patternSetFactory, taskDependencyFactory, propertyHost, path, oldItems);
            }
            return this;
        }

        @Override
        public ValueCollector plus(DefaultConfigurableFileCollection owner, PathToFileResolver resolver, Factory<PatternSet> patternSetFactory, TaskDependencyFactory taskDependencyFactory, PropertyHost propertyHost, Object... paths) {
            ImmutableList<Object> oldItems = ImmutableList.copyOf(items);
            for (Object path : paths) {
                addItem(owner, resolver, patternSetFactory, taskDependencyFactory, propertyHost, path, oldItems);
            }
            return this;
        }

        private void addItem(DefaultConfigurableFileCollection owner, PathToFileResolver resolver, Factory<PatternSet> patternSetFactory, TaskDependencyFactory taskDependencyFactory, PropertyHost propertyHost, Object path, ImmutableList<Object> oldItems) {
            // Unpack to deal with DSL syntax: collection += someFiles
            if (path instanceof FileCollectionInternal) {
                FileCollectionInternal newPath = ((FileCollectionInternal) path).replace(owner, () -> {
                    // Should use FileCollectionFactory here, and it can take care of simplifying the tree. For example, ths returned collection does not need to be mutable
                    if (oldItems.size() == 1 && oldItems.get(0) instanceof FileCollectionInternal) {
                        return (FileCollectionInternal) oldItems.get(0);
                    }
                    DefaultConfigurableFileCollection oldFiles = new DefaultConfigurableFileCollection(null, resolver, taskDependencyFactory, patternSetFactory, propertyHost);
                    oldFiles.from(oldItems);
                    return oldFiles;
                });
                if (newPath != path) {
                    DeprecationLogger.deprecateBehaviour("A ConfigurableFileCollection is used as its own from argument, potentially indirectly.")
                        .willBecomeAnErrorInGradle9()
                        .undocumented()
                        .nagUser();

                    path = newPath;
                }
            }
            items.add(path);
        }

        @Nullable
        @Override
        public List<Object> replace(FileCollectionInternal original, Supplier<FileCollectionInternal> supplier) {
            ImmutableList.Builder<Object> builder = ImmutableList.builderWithExpectedSize(items.size());
            boolean hasChanges = false;
            for (Object candidate : items) {
                if (candidate instanceof FileCollectionInternal) {
                    FileCollectionInternal newCollection = ((FileCollectionInternal) candidate).replace(original, supplier);
                    hasChanges |= newCollection != candidate;
                    builder.add(newCollection);
                } else {
                    builder.add(candidate);
                }
            }
            if (hasChanges) {
                return builder.build();
            } else {
                return null;
            }
        }
    }

    private static class ResolvedItemsCollector implements ValueCollector {
        private final ImmutableList<FileCollectionInternal> fileCollections;

        public ResolvedItemsCollector(ImmutableList<FileCollectionInternal> fileCollections) {
            this.fileCollections = fileCollections;
        }

        @Override
        public void collectSource(Collection<Object> dest) {
            dest.addAll(fileCollections);
        }

        @Override
        public void visitContents(Consumer<FileCollectionInternal> visitor) {
            for (FileCollectionInternal fileCollection : fileCollections) {
                visitor.accept(fileCollection);
            }
        }

        @Override
        public ValueCollector setFrom(DefaultConfigurableFileCollection owner, PathToFileResolver resolver, Factory<PatternSet> patternSetFactory, TaskDependencyFactory taskDependencyFactory, PropertyHost propertyHost, Iterable<?> path) {
            throw new UnsupportedOperationException("Should not be called");
        }

        @Override
        public ValueCollector setFrom(DefaultConfigurableFileCollection owner, PathToFileResolver resolver, Factory<PatternSet> patternSetFactory, TaskDependencyFactory taskDependencyFactory, PropertyHost propertyHost, Object[] paths) {
            throw new UnsupportedOperationException("Should not be called");
        }

        @Override
        public ValueCollector plus(DefaultConfigurableFileCollection owner, PathToFileResolver resolver, Factory<PatternSet> patternSetFactory, TaskDependencyFactory taskDependencyFactory, PropertyHost propertyHost, Object... paths) {
            throw new UnsupportedOperationException("Should not be called");
        }

        @Override
        public boolean remove(Object source) {
            throw new UnsupportedOperationException("Should not be called");
        }

        @Nullable
        @Override
        public List<Object> replace(FileCollectionInternal original, Supplier<FileCollectionInternal> supplier) {
            return null;
        }
    }

    private class PathSet extends AbstractSet<Object> {
        private Set<Object> delegate() {
            Set<Object> sources = new LinkedHashSet<>();
            value.collectSource(sources);
            return sources;
        }

        @Override
        public Iterator<Object> iterator() {
            Iterator<Object> iterator = delegate().iterator();
            return new Iterator<Object>() {
                @Override
                public boolean hasNext() {
                    return iterator.hasNext();
                }

                @Override
                public Object next() {
                    return iterator.next();
                }

                @Override
                public void remove() {
                    assertMutable();
                    iterator.remove();
                }
            };
        }

        @Override
        public int size() {
            return delegate().size();
        }

        @Override
        public boolean contains(Object o) {
            return delegate().contains(o);
        }

        @Override
        public boolean add(Object o) {
            assertMutable();
            if (!delegate().contains(o)) {
                value = value.plus(DefaultConfigurableFileCollection.this, resolver, patternSetFactory, dependencyFactory, host, o);
                return true;
            } else {
                return false;
            }
        }

        @Override
        public boolean remove(Object o) {
            assertMutable();
            return value.remove(o);
        }

        @Override
        public void clear() {
            assertMutable();
            value = EMPTY_COLLECTOR;
        }
    }
}
