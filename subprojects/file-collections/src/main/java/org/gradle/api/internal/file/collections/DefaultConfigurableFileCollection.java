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

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.internal.file.CompositeFileCollection;
import org.gradle.api.internal.provider.HasConfigurableValueInternal;
import org.gradle.api.internal.tasks.DefaultTaskDependency;
import org.gradle.api.internal.tasks.TaskDependencyFactory;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.api.tasks.util.internal.PatternSets;
import org.gradle.internal.file.PathToFileResolver;
import org.gradle.internal.state.Managed;
import org.gradle.util.DeprecationLogger;

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
    private enum State {
        Mutable, ImplicitFinalizeNextQuery, FinalizeNextQuery, Final
    }

    private final Set<Object> files;
    private final PathSet filesWrapper;
    private final String displayName;
    private final PathToFileResolver resolver;
    private final DefaultTaskDependency buildDependency;
    private State state = State.Mutable;
    private boolean disallowChanges;

    public DefaultConfigurableFileCollection(@Nullable String displayName, PathToFileResolver fileResolver, TaskDependencyFactory dependencyFactory, Collection<?> files) {
        this.displayName = displayName;
        this.resolver = fileResolver;
        this.files = new LinkedHashSet<>();
        this.files.addAll(files);
        filesWrapper = new PathSet(this.files);
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
            files.clear();
            files.add(path);
        }
    }

    @Override
    public void setFrom(Object... paths) {
        if (assertMutable()) {
            files.clear();
            Collections.addAll(files, paths);
        }
    }

    @Override
    public ConfigurableFileCollection from(Object... paths) {
        if (assertMutable()) {
            Collections.addAll(files, paths);
        }
        return this;
    }

    private boolean assertMutable() {
        if (state == State.Final && disallowChanges) {
            throw new IllegalStateException("The value for " + displayNameForThisCollection() + " is final and cannot be changed.");
        } else if (disallowChanges) {
            throw new IllegalStateException("The value for " + displayNameForThisCollection() + " cannot be changed.");
        } else if (state == State.Final) {
            DeprecationLogger.nagUserOfDiscontinuedInvocation("Changing the value for a FileCollection with a final value");
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
        DefaultFileCollectionResolveContext context = new DefaultFileCollectionResolveContext(PatternSets.getNonCachingPatternSetFactory());
        UnpackingVisitor nested = new UnpackingVisitor(context, resolver);
        nested.add(files);
        files.clear();
        files.addAll(context.resolveAsFileCollections());
    }

    @Override
    public void visitContents(FileCollectionResolveContext context) {
        if (state == State.ImplicitFinalizeNextQuery) {
            calculateFinalizedValue();
            state = State.Final;
        } else if (state == State.FinalizeNextQuery) {
            calculateFinalizedValue();
            state = State.Final;
            disallowChanges = true;
        }
        if (state == State.Final) {
            context.addAll(files);
        } else {
            UnpackingVisitor nested = new UnpackingVisitor(context, resolver);
            nested.add(files);
        }
    }

    @Override
    public void visitDependencies(TaskDependencyResolveContext context) {
        context.add(buildDependency);
        super.visitDependencies(context);
    }

    private class PathSet extends AbstractSet<Object> {
        private final Set<Object> delegate;

        public PathSet(Set<Object> delegate) {
            this.delegate = delegate;
        }

        @Override
        public Iterator<Object> iterator() {
            Iterator<Object> iterator = delegate.iterator();
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
            return delegate.size();
        }

        @Override
        public boolean contains(Object o) {
            return delegate.contains(o);
        }

        @Override
        public boolean add(Object o) {
            if (assertMutable()) {
                return delegate.add(o);
            } else {
                return false;
            }
        }

        @Override
        public boolean remove(Object o) {
            if (assertMutable()) {
                return delegate.remove(o);
            } else {
                return false;
            }
        }

        @Override
        public void clear() {
            if (assertMutable()) {
                delegate.clear();
            }
        }
    }
}
