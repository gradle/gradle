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

import groovy.util.ObservableSet;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.internal.file.CompositeFileCollection;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.api.internal.tasks.DefaultTaskDependency;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.api.internal.tasks.TaskResolver;
import org.gradle.internal.file.PathToFileResolver;
import org.gradle.internal.state.Managed;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A {@link org.gradle.api.file.FileCollection} which resolves a set of paths relative to a {@link org.gradle.api.internal.file.FileResolver}.
 */
public class DefaultConfigurableFileCollection extends CompositeFileCollection implements ConfigurableFileCollection, Managed {
    private enum State {
        Mutable, Finalized
    }

    private final Set<Object> files;
    private final ObservableSet<Object> filesWrapper;
    private final String displayName;
    private final PathToFileResolver resolver;
    private final DefaultTaskDependency buildDependency;
    private State state = State.Mutable;

    public DefaultConfigurableFileCollection(PathToFileResolver fileResolver, @Nullable TaskResolver taskResolver) {
        this("file collection", fileResolver, taskResolver, null);
    }

    public DefaultConfigurableFileCollection(PathToFileResolver fileResolver, @Nullable TaskResolver taskResolver, Collection<?> files) {
        this("file collection", fileResolver, taskResolver, files);
    }

    public DefaultConfigurableFileCollection(PathToFileResolver fileResolver, @Nullable TaskResolver taskResolver, Object[] files) {
        this("file collection", fileResolver, taskResolver, Arrays.asList(files));
    }

    public DefaultConfigurableFileCollection(String displayName, PathToFileResolver fileResolver, @Nullable TaskResolver taskResolver) {
        this(displayName, fileResolver, taskResolver, null);
    }

    public DefaultConfigurableFileCollection(String displayName, PathToFileResolver fileResolver, @Nullable TaskResolver taskResolver, @Nullable Collection<?> files) {
        this.displayName = displayName;
        this.resolver = fileResolver;
        this.files = new LinkedHashSet<Object>();
        if (files != null) {
            this.files.addAll(files);
        }
        this.filesWrapper = new ObservableSet<Object>(this.files);
        this.filesWrapper.addPropertyChangeListener(evt -> {
            assertMutable();
        });
        buildDependency = new DefaultTaskDependency(taskResolver);
    }

    @Override
    public boolean immutable() {
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
    public Factory managedFactory() {
        return new Factory() {
            @Nullable
            @Override
            public <T> T fromState(Class<T> type, Object state) {
                if (!type.isAssignableFrom(ConfigurableFileCollection.class)) {
                    return null;
                }
                return type.cast(new DefaultConfigurableFileCollection(resolver, null, (Set<File>) state));
            }
        };
    }

    @Override
    public void finalizeValue() {
        if (state == State.Mutable) {
            List<? extends FileCollectionInternal> resolved = getSourceCollections();
            files.clear();
            files.addAll(resolved);
            state = State.Finalized;
        }
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public Set<Object> getFrom() {
        return filesWrapper;
    }

    @Override
    public void setFrom(Iterable<?> path) {
        assertMutable();
        files.clear();
        files.add(path);
    }

    @Override
    public void setFrom(Object... paths) {
        assertMutable();
        files.clear();
        Collections.addAll(files, paths);
    }

    @Override
    public ConfigurableFileCollection from(Object... paths) {
        assertMutable();
        Collections.addAll(files, paths);
        return this;
    }

    private void assertMutable() {
        if (state == State.Finalized) {
            throw new IllegalStateException("The value for " + displayName + " is final and cannot be changed.");
        }
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

    @Override
    public void visitContents(FileCollectionResolveContext context) {
        UnpackingVisitor nested = new UnpackingVisitor(context, resolver);
        nested.add(files);
    }

    @Override
    public void visitDependencies(TaskDependencyResolveContext context) {
        context.add(buildDependency);
        super.visitDependencies(context);
    }
}
