/*
 * Copyright 2013 the original author or authors.
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

import groovy.lang.Closure;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.StopExecutionException;
import org.gradle.api.tasks.TaskDependency;

import java.io.File;
import java.util.Iterator;
import java.util.Set;

/**
 * A file collection that delegates each method call to the
 * file collection returned by {@link #getDelegate()}.
 */
public abstract class DelegatingFileCollection implements FileCollection, MinimalFileSet {
    public abstract FileCollection getDelegate();

    public File getSingleFile() throws IllegalStateException {
        return getDelegate().getSingleFile();
    }

    public Set<File> getFiles() {
        return getDelegate().getFiles();
    }

    public boolean contains(File file) {
        return getDelegate().contains(file);
    }

    public String getAsPath() {
        return getDelegate().getAsPath();
    }

    public FileCollection plus(FileCollection collection) {
        return getDelegate().plus(collection);
    }

    public FileCollection minus(FileCollection collection) {
        return getDelegate().minus(collection);
    }

    public FileCollection filter(Closure filterClosure) {
        return getDelegate().filter(filterClosure);
    }

    public FileCollection filter(Spec<? super File> filterSpec) {
        return getDelegate().filter(filterSpec);
    }

    public Object asType(Class<?> type) throws UnsupportedOperationException {
        return getDelegate().asType(type);
    }

    public FileCollection add(FileCollection collection) throws UnsupportedOperationException {
        return getDelegate().add(collection);
    }

    public boolean isEmpty() {
        return getDelegate().isEmpty();
    }

    public FileCollection stopExecutionIfEmpty() throws StopExecutionException {
        return getDelegate().stopExecutionIfEmpty();
    }

    public FileTree getAsFileTree() {
        return getDelegate().getAsFileTree();
    }

    public void addToAntBuilder(Object builder, String nodeName, AntType type) {
        getDelegate().addToAntBuilder(builder, nodeName, type);
    }

    public Object addToAntBuilder(Object builder, String nodeName) {
        return getDelegate().addToAntBuilder(builder, nodeName);
    }

    public TaskDependency getBuildDependencies() {
        return getDelegate().getBuildDependencies();
    }

    public Iterator<File> iterator() {
        return getDelegate().iterator();
    }

    public String getDisplayName() {
        FileCollection delegate = getDelegate();
        if (delegate instanceof MinimalFileSet) {
            return ((MinimalFileSet) delegate).getDisplayName();
        }
        return getDelegate().toString();
    }
}
