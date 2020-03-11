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
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.internal.file.CompositeFileCollection;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.api.internal.provider.HasConfigurableValueInternal;
import org.gradle.api.internal.provider.PropertyHost;
import org.gradle.api.internal.tasks.DefaultTaskDependency;
import org.gradle.api.internal.tasks.TaskDependencyFactory;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.internal.Factory;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.file.PathToFileResolver;
import org.gradle.internal.state.Managed;

import javax.annotation.Nullable;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A {@link org.gradle.api.file.FileCollection} which resolves a set of paths relative to a {@link org.gradle.api.internal.file.FileResolver}.
 */
public class DefaultConfigurableFileCollection extends CompositeFileCollection implements ConfigurableFileCollection, Managed, HasConfigurableValueInternal {
    public static final EmptyCollector EMPTY_COLLECTOR = new EmptyCollector();

    private enum State {
        Mutable, ImplicitFinalizeNextQuery, FinalizeNextQuery, Final
    }

    private final PathSet filesWrapper;
    private final String displayName;
    private final PathToFileResolver resolver;
    private final PropertyHost host;
    private final DefaultTaskDependency buildDependency;
    private State state = State.Mutable;
    private boolean disallowChanges;
    private boolean disallowUnsafeRead;
    private ValueCollector value = EMPTY_COLLECTOR;

    public DefaultConfigurableFileCollection(@Nullable String displayName, PathToFileResolver fileResolver, TaskDependencyFactory dependencyFactory, Factory<PatternSet> patternSetFactory, PropertyHost host) {
        super(patternSetFactory);
        this.displayName = displayName;
        this.resolver = fileResolver;
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
        if (state != State.Final) {
            calculateFinalizedValue();
        }
        state = State.Final;
        disallowChanges = true;
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

    // Should be on the public API. Was not made public for the 6.3 release
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
    public Set<Object> getFrom() {
        return filesWrapper;
    }

    @Override
    public void setFrom(Iterable<?> path) {
        if (assertMutable()) {
            value = value.setFrom(resolver, path);
        }
    }

    @Override
    public void setFrom(Object... paths) {
        if (assertMutable()) {
            value = value.setFrom(resolver, paths);
        }
    }

    @Override
    public ConfigurableFileCollection from(Object... paths) {
        if (assertMutable()) {
            value = value.plus(resolver, paths);
        }
        return this;
    }

    private boolean assertMutable() {
        if (state == State.Final && disallowChanges) {
            throw new IllegalStateException("The value for " + displayNameForThisCollection() + " is final and cannot be changed.");
        } else if (disallowChanges) {
            throw new IllegalStateException("The value for " + displayNameForThisCollection() + " cannot be changed.");
        } else if (state == State.Final) {
            DeprecationLogger.deprecateAction("Changing the value for a FileCollection with a final value")
                .willBecomeAnErrorInGradle7()
                .withUserManual("lazy_configuration", "unmodifiable_property")
                .nagUser();
            return false;
        } else {
            return true;
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
        DefaultFileCollectionResolveContext context = new DefaultFileCollectionResolveContext(patternSetFactory);
        value.visitContents(context);
        value = new ResolvedItemsCollector(context.resolveAsFileCollections());
    }

    @Override
    public void visitContents(FileCollectionResolveContext context) {
        if (disallowUnsafeRead && state != State.Final) {
            String reason = host.beforeRead();
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
        value.visitContents(context);
    }

    @Override
    public void visitDependencies(TaskDependencyResolveContext context) {
        context.add(buildDependency);
        super.visitDependencies(context);
    }

    private interface ValueCollector {
        void collectSource(Collection<Object> dest);

        void visitContents(FileCollectionResolveContext context);

        boolean remove(Object source);

        ValueCollector setFrom(PathToFileResolver resolver, Iterable<?> path);

        ValueCollector setFrom(PathToFileResolver resolver, Object[] paths);

        ValueCollector plus(PathToFileResolver resolver, Object... paths);
    }

    private static class EmptyCollector implements ValueCollector {
        @Override
        public void collectSource(Collection<Object> dest) {
        }

        @Override
        public void visitContents(FileCollectionResolveContext context) {
        }

        @Override
        public boolean remove(Object source) {
            return false;
        }

        @Override
        public ValueCollector setFrom(PathToFileResolver resolver, Iterable<?> path) {
            return new UnresolvedItemsCollector(resolver, path);
        }

        @Override
        public ValueCollector setFrom(PathToFileResolver resolver, Object[] paths) {
            return new UnresolvedItemsCollector(resolver, paths);
        }

        @Override
        public ValueCollector plus(PathToFileResolver resolver, Object[] paths) {
            return setFrom(resolver, paths);
        }
    }

    private static class UnresolvedItemsCollector implements ValueCollector {
        private final PathToFileResolver resolver;
        private final Set<Object> items = new LinkedHashSet<>();

        public UnresolvedItemsCollector(PathToFileResolver resolver, Iterable<?> item) {
            this.resolver = resolver;
            items.add(item);
        }

        public UnresolvedItemsCollector(PathToFileResolver resolver, Object[] item) {
            this.resolver = resolver;
            Collections.addAll(items, item);
        }

        @Override
        public void collectSource(Collection<Object> dest) {
            dest.addAll(items);
        }

        @Override
        public void visitContents(FileCollectionResolveContext context) {
            UnpackingVisitor nested = new UnpackingVisitor(context, resolver);
            for (Object item : items) {
                nested.add(item);
            }
        }

        @Override
        public boolean remove(Object source) {
            return items.remove(source);
        }

        @Override
        public ValueCollector setFrom(PathToFileResolver resolver, Iterable<?> path) {
            items.clear();
            items.add(path);
            return this;
        }

        @Override
        public ValueCollector setFrom(PathToFileResolver resolver, Object[] paths) {
            items.clear();
            Collections.addAll(items, paths);
            return this;
        }

        @Override
        public ValueCollector plus(PathToFileResolver resolver, Object[] paths) {
            Collections.addAll(items, paths);
            return this;
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
        public void visitContents(FileCollectionResolveContext context) {
            context.addAll(fileCollections);
        }

        @Override
        public ValueCollector setFrom(PathToFileResolver resolver, Iterable<?> path) {
            throw new UnsupportedOperationException("Should not be called");
        }

        @Override
        public ValueCollector setFrom(PathToFileResolver resolver, Object[] paths) {
            throw new UnsupportedOperationException("Should not be called");
        }

        @Override
        public ValueCollector plus(PathToFileResolver resolver, Object[] paths) {
            throw new UnsupportedOperationException("Should not be called");
        }

        @Override
        public boolean remove(Object source) {
            throw new UnsupportedOperationException("Should not be called");
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
                    if (assertMutable()) {
                        iterator.remove();
                    }
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
            if (assertMutable() && !delegate().contains(o)) {
                value = value.plus(resolver, o);
                return true;
            } else {
                return false;
            }
        }

        @Override
        public boolean remove(Object o) {
            if (assertMutable()) {
                return value.remove(o);
            } else {
                return false;
            }
        }

        @Override
        public void clear() {
            if (assertMutable()) {
                value = EMPTY_COLLECTOR;
            }
        }
    }
}
