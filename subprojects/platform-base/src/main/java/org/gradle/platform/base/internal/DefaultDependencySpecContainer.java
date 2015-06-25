/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.platform.base.internal;

import com.google.common.collect.ImmutableSet;
import org.gradle.api.Action;
import org.gradle.platform.base.DependencySpec;
import org.gradle.platform.base.DependencySpecBuilder;
import org.gradle.platform.base.DependencySpecContainer;

import java.util.*;

public class DefaultDependencySpecContainer implements DependencySpecContainer {

    private final List<DefaultDependencySpec.Builder> builders = new LinkedList<DefaultDependencySpec.Builder>();

    @Override
    public DependencySpecBuilder project(final String path) {
        return doCreate(new Action<DefaultDependencySpec.Builder>() {
            @Override
            public void execute(DefaultDependencySpec.Builder builder) {
                builder.project(path);
            }
        });
    }

    @Override
    public DependencySpecBuilder library(final String name) {
        return doCreate(new Action<DefaultDependencySpec.Builder>() {
            @Override
            public void execute(DefaultDependencySpec.Builder builder) {
                builder.library(name);
            }
        });
    }

    private Collection<DependencySpec> getDependencies() {
        if (builders.isEmpty()) {
            return Collections.emptySet();
        }
        ArrayList<DependencySpec> specs = new ArrayList<DependencySpec>(builders.size());
        for (DefaultDependencySpec.Builder builder : builders) {
            specs.add(builder.build());
        }
        return ImmutableSet.copyOf(specs);
    }

    private DefaultDependencySpec.Builder doCreate(Action<? super DefaultDependencySpec.Builder> action) {
        DefaultDependencySpec.Builder builder = new DefaultDependencySpec.Builder();
        action.execute(builder);
        builders.add(builder);
        return builder;
    }

    @Override
    public boolean add(DependencySpec dependencySpec) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int size() {
        return getDependencies().size();
    }

    @Override
    public boolean isEmpty() {
        return builders.isEmpty();
    }

    @Override
    public boolean contains(Object o) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Iterator<DependencySpec> iterator() {
        return getDependencies().iterator();
    }

    @Override
    public Object[] toArray() {
        return getDependencies().toArray();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T[] toArray(T[] a) {
        return (T[]) getDependencies().toArray((DefaultDependencySpec[])a);
    }

    @Override
    public boolean remove(Object o) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean addAll(Collection<? extends DependencySpec> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException();
    }
}
