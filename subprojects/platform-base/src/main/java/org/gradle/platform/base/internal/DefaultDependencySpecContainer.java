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

import org.gradle.api.Action;
import org.gradle.platform.base.DependencySpec;
import org.gradle.platform.base.DependencySpecContainer;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public class DefaultDependencySpecContainer implements DependencySpecContainer {

    private final List<DependencySpec> dependencies = new LinkedList<DependencySpec>();

    @Override
    public DefaultDependencySpec project(final String path) {
        return doCreate(new Action<DependencySpec>() {
            @Override
            public void execute(DependencySpec dependencySpec) {
                dependencySpec.project(path);
            }
        });
    }

    @Override
    public DefaultDependencySpec library(final String name) {
        return doCreate(new Action<DependencySpec>() {
            @Override
            public void execute(DependencySpec dependencySpec) {
                dependencySpec.library(name);
            }
        });
    }

    @Override
    public void create(Action<? super DependencySpec> action) {
        doCreate(action);
    }

    private DefaultDependencySpec doCreate(Action<? super DependencySpec> action) {
        DefaultDependencySpec spec = new DefaultDependencySpec();
        dependencies.add(spec);
        action.execute(spec);
        return spec;
    }

    @Override
    public void beforeEach(Action<? super DependencySpec> configAction) {

    }

    @Override
    public void afterEach(Action<? super DependencySpec> configAction) {

    }

    @Override
    public int size() {
        return dependencies.size();
    }

    @Override
    public boolean isEmpty() {
        return dependencies.isEmpty();
    }

    @Override
    public boolean contains(Object o) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Iterator<DependencySpec> iterator() {
        return dependencies.iterator();
    }

    @Override
    public Object[] toArray() {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> T[] toArray(T[] a) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean add(DependencySpec dependencySpec) {
        throw new UnsupportedOperationException();
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
